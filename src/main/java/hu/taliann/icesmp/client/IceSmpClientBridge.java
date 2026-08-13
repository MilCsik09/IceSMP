package hu.taliann.icesmp.client;

import hu.taliann.icesmp.client.protocol.ClientHello;
import hu.taliann.icesmp.client.protocol.ClientMessageCodec;
import hu.taliann.icesmp.client.protocol.ClientProtocol;
import hu.taliann.icesmp.client.protocol.ClientProtocolException;
import hu.taliann.icesmp.client.protocol.MessageEnvelope;
import hu.taliann.icesmp.client.protocol.ProtocolReject;
import hu.taliann.icesmp.client.protocol.ServerHello;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Az opcionális IceSMP Client (Fabric kliensmod) szerveroldali hídja — a foundation
 * fázisban kizárólag transport + kézfogás + session-lifecycle, gameplay-integráció nélkül.
 *
 * <p>Architektúra-invariánsok: a kliens sosem authority (minden beérkező üzenet csak
 * kérés/ajánlat); a domain-servicek nem tudnak a hídról (projection/action irány csak a
 * későbbi fázisokban, adapteren át); hibás payload válasz nélkül eldobódik (fail closed);
 * a teljes réteg a {@code client.enabled} élő config-kapcsolóval üzem közben lekapcsolható.</p>
 */
public final class IceSmpClientBridge implements PluginMessageListener, PlayerStateCleanup {

    /** Aggregált híd-diagnosztika a /icesmp client stats felülethez. */
    public record BridgeStats(long received, long droppedDisabled, long droppedOversized,
                              long droppedMalformed, long droppedRateLimited, long droppedStale,
                              long rejectedHandshakes, long acceptedHandshakes, int activeSessions) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ClientSessionRegistry sessions = new ClientSessionRegistry();
    private final ClientRateLimiter rateLimiter = new ClientRateLimiter();

    private final AtomicLong received = new AtomicLong();
    private final AtomicLong droppedDisabled = new AtomicLong();
    private final AtomicLong droppedOversized = new AtomicLong();
    private final AtomicLong droppedMalformed = new AtomicLong();
    private final AtomicLong droppedRateLimited = new AtomicLong();
    private final AtomicLong droppedStale = new AtomicLong();
    private final AtomicLong rejectedHandshakes = new AtomicLong();
    private final AtomicLong acceptedHandshakes = new AtomicLong();

    public IceSmpClientBridge(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * A csatorna-regisztráció feltétel nélküli: a {@code client.enabled} kapcsolót
     * üzenetenként, élő configból olvassuk, így a rollback restart nélkül működik
     * (/icesmp config set client.enabled false).
     */
    public void register() {
        final Messenger messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, ClientProtocol.CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, ClientProtocol.CHANNEL, this);
    }

    /**
     * Disable-kor kötelező: bent hagyott channel-listener a régi core-példányt tartaná
     * életben a következő enable-ig (hot reload).
     */
    public void unregister() {
        final Messenger messenger = plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, ClientProtocol.CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin, ClientProtocol.CHANNEL);
        sessions.clear();
        rateLimiter.clear();
    }

    @Override
    public void onPluginMessageReceived(final @NonNull String channel, final @NonNull Player player,
                                        final byte @NonNull [] message) {
        if (!ClientProtocol.CHANNEL.equals(channel)) {
            return;
        }
        received.incrementAndGet();
        if (!configManager.getBoolean("client.enabled", true)) {
            droppedDisabled.incrementAndGet();
            return;
        }
        final int maxPayload = Math.min(ClientProtocol.MAX_PACKET_BYTES,
                configManager.getInt("client.limits.max-payload-bytes", ClientProtocol.MAX_PACKET_BYTES));
        if (message.length > maxPayload) {
            droppedOversized.incrementAndGet();
            debug(() -> "oversized packet from " + player.getName() + " (" + message.length + " bytes)");
            return;
        }
        final long now = System.currentTimeMillis();
        if (!rateLimiter.tryAcquire(player.getUniqueId(), ClientRateLimiter.Category.CONTROL,
                configManager.getInt("client.limits.control-messages-per-second", 20), 1000L, now)) {
            droppedRateLimited.incrementAndGet();
            return;
        }
        final MessageEnvelope envelope;
        try {
            envelope = ClientMessageCodec.decodeEnvelope(message);
        } catch (final ClientProtocolException malformed) {
            droppedMalformed.incrementAndGet();
            debug(() -> "malformed packet from " + player.getName() + ": " + malformed.getMessage());
            return;
        }
        switch (envelope.messageType()) {
            case ClientProtocol.MSG_CLIENT_HELLO -> handleHello(player, envelope, now);
            case ClientProtocol.MSG_PING -> handlePing(player, envelope, now);
            case ClientProtocol.MSG_RESYNC_REQUEST -> handleResyncRequest(player, envelope, now);
            default -> {
                droppedMalformed.incrementAndGet();
                debug(() -> "unknown message type 0x" + Integer.toHexString(envelope.messageType())
                        + " from " + player.getName());
            }
        }
    }

    private void handleHello(final Player player, final MessageEnvelope envelope, final long now) {
        final ClientHello hello;
        try {
            hello = ClientHello.decode(envelope.payload());
        } catch (final ClientProtocolException malformed) {
            droppedMalformed.incrementAndGet();
            debug(() -> "malformed CLIENT_HELLO from " + player.getName() + ": " + malformed.getMessage());
            return;
        }
        final ClientHandshake.Result result = ClientHandshake.negotiate(hello, policyFromConfig());
        if (result instanceof ClientHandshake.Result.Rejected rejected) {
            rejectedHandshakes.incrementAndGet();
            sessions.invalidate(player.getUniqueId());
            final ProtocolReject reject = new ProtocolReject(rejected.reasonCode(),
                    ClientProtocol.PROTOCOL_MIN, ClientProtocol.PROTOCOL_MAX);
            send(player, new MessageEnvelope(ClientProtocol.PROTOCOL_MAX, ClientProtocol.MSG_PROTOCOL_REJECT,
                    0L, 0L, envelope.requestId(), reject.encode()));
            debug(() -> "handshake rejected for " + player.getName() + ": " + rejected.reasonCode());
            return;
        }
        final ClientHandshake.Result.Accepted accepted = (ClientHandshake.Result.Accepted) result;
        final ClientSession session = new ClientSession(player.getUniqueId(), hello.clientVersion(),
                accepted.selectedProtocol(), sessions.nextGeneration(), accepted.capabilities(), now);
        sessions.register(session);
        acceptedHandshakes.incrementAndGet();
        final ServerHello serverHello = new ServerHello(
                plugin.getPluginMeta().getVersion(),
                accepted.selectedProtocol(),
                configManager.getInt("client.resource-pack-schema", 1),
                accepted.capabilities().stream().map(Enum::name).sorted().toList());
        send(player, new MessageEnvelope(accepted.selectedProtocol(), ClientProtocol.MSG_SERVER_HELLO,
                session.generation(), session.nextOutboundSequence(), envelope.requestId(), serverHello.encode()));
        debug(() -> "handshake accepted for " + player.getName() + " (client " + hello.clientVersion()
                + ", protocol " + accepted.selectedProtocol() + ", capabilities " + accepted.capabilities() + ")");
    }

    private void handlePing(final Player player, final MessageEnvelope envelope, final long now) {
        final ClientSession session = liveSession(player.getUniqueId(), envelope, now);
        if (session == null) {
            return;
        }
        send(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_PONG,
                session.generation(), session.nextOutboundSequence(), envelope.requestId(), new byte[0]));
    }

    private void handleResyncRequest(final Player player, final MessageEnvelope envelope, final long now) {
        final ClientSession session = liveSession(player.getUniqueId(), envelope, now);
        if (session == null) {
            return;
        }
        final long cooldown = configManager.getLong("client.limits.resync-cooldown-ms", 2000L);
        if (!rateLimiter.tryAcquire(player.getUniqueId(), ClientRateLimiter.Category.RESYNC, 1, cooldown, now)) {
            droppedRateLimited.incrementAndGet();
            return;
        }
        sendResync(player, session, envelope.requestId());
    }

    /** Admin-oldali kényszerített resync (/icesmp client resync). */
    public boolean requestResync(final Player target) {
        final Optional<ClientSession> session = sessions.find(target.getUniqueId());
        if (session.isEmpty()) {
            return false;
        }
        sendResync(target, session.get(), MessageEnvelope.NO_REQUEST);
        return true;
    }

    /**
     * A foundation fázisban nincs még state-domain, ezért a resync egy üres
     * BEGIN/END pár: a kliensnek minden cache-elt state-et el kell dobnia, és a
     * későbbi fázisok state-üzenetei töltik majd fel a két jelzés között.
     */
    private void sendResync(final Player player, final ClientSession session, final UUID requestId) {
        send(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_RESYNC_BEGIN,
                session.generation(), session.nextOutboundSequence(), requestId, new byte[0]));
        send(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_RESYNC_END,
                session.generation(), session.nextOutboundSequence(), requestId, new byte[0]));
    }

    /** Session-, generation- és sequence-kapu minden kézfogás utáni üzenetre. */
    private ClientSession liveSession(final UUID playerId, final MessageEnvelope envelope, final long now) {
        final ClientSession session = sessions.find(playerId).orElse(null);
        if (session == null || session.generation() != envelope.sessionGeneration()
                || !session.acceptInbound(envelope.sequence(), now)) {
            droppedStale.incrementAndGet();
            return null;
        }
        return session;
    }

    private ClientHandshake.ServerPolicy policyFromConfig() {
        final Set<ClientCapability> enabled = EnumSet.noneOf(ClientCapability.class);
        for (final ClientCapability capability : ClientCapability.values()) {
            if (configManager.getBoolean("client.features." + capability.configKey(), false)) {
                enabled.add(capability);
            }
        }
        return new ClientHandshake.ServerPolicy(
                configManager.getInt("client.protocol.min", ClientProtocol.PROTOCOL_MIN),
                configManager.getInt("client.protocol.max", ClientProtocol.PROTOCOL_MAX),
                enabled);
    }

    /**
     * Kimenő küldés mindig a címzett saját ütemezőjén (Folia): a plugin-message
     * callback szál-kontextusa nem garantált, a Player-t csak a saját régió-szálán
     * érintjük. Az érvényesség-ellenőrzés a task-on belül fut.
     */
    private void send(final Player player, final MessageEnvelope envelope) {
        final byte[] wire = ClientMessageCodec.encodeEnvelope(envelope);
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                player.sendPluginMessage(plugin, ClientProtocol.CHANNEL, wire);
            }
        }, null);
    }

    private void debug(final java.util.function.Supplier<String> message) {
        if (configManager.getBoolean("client.debug", false)) {
            plugin.getLogger().info("[ClientBridge] " + message.get());
        }
    }

    public Optional<ClientSession> sessionOf(final UUID playerId) {
        return sessions.find(playerId);
    }

    public List<ClientSession> activeSessions() {
        return sessions.snapshot();
    }

    public BridgeStats stats() {
        return new BridgeStats(received.get(), droppedDisabled.get(), droppedOversized.get(),
                droppedMalformed.get(), droppedRateLimited.get(), droppedStale.get(),
                rejectedHandshakes.get(), acceptedHandshakes.get(), sessions.size());
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        sessions.invalidate(playerId);
        rateLimiter.clearPlayer(playerId);
    }
}
