package hu.taliann.icesmp.client;

import hu.taliann.icesmp.client.projection.ClientHudProjector;
import hu.taliann.icesmp.client.projection.ClientTalentProjector;
import hu.taliann.icesmp.client.protocol.AbilityKitPayload;
import hu.taliann.icesmp.client.protocol.ActionResultPayload;
import hu.taliann.icesmp.client.protocol.CastSlotPayload;
import hu.taliann.icesmp.client.protocol.ClientHello;
import hu.taliann.icesmp.client.protocol.ClientMessageCodec;
import hu.taliann.icesmp.client.protocol.ClientProtocol;
import hu.taliann.icesmp.client.protocol.ClientProtocolException;
import hu.taliann.icesmp.client.protocol.MessageEnvelope;
import hu.taliann.icesmp.client.protocol.ProfileStatePayload;
import hu.taliann.icesmp.client.protocol.ProtocolReject;
import hu.taliann.icesmp.client.protocol.RelicStatePayload;
import hu.taliann.icesmp.client.protocol.ServerHello;
import hu.taliann.icesmp.client.protocol.SpellActionPayload;
import hu.taliann.icesmp.client.protocol.SpellbookStatePayload;
import hu.taliann.icesmp.client.protocol.TalentActionPayload;
import hu.taliann.icesmp.client.protocol.TalentStatePayload;
import hu.taliann.icesmp.gui.SpellbookGUI;
import hu.taliann.icesmp.managers.SpellFavoritesManager;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.HudManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.managers.TalentManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Az opcionális IceSMP Client (Fabric kliensmod) szerveroldali hídja — a foundation
 * fázisban kizárólag transport + kézfogás + session-lifecycle, gameplay-integráció nélkül.
 *
 * <p>Architektúra-invariánsok: a kliens sosem authority (minden beérkező üzenet csak
 * kérés/ajánlat); a domain-servicek nem tudnak a hídról (projection/action irány csak a
 * későbbi fázisokban, adapteren át); hibás payload válasz nélkül eldobódik (fail closed);
 * a teljes réteg a {@code client.enabled} élő config-kapcsolóval üzem közben lekapcsolható.</p>
 */
public final class IceSmpClientBridge implements PluginMessageListener, PlayerStateCleanup,
        HudManager.ClientHudRoute {

    /** Aggregált híd-diagnosztika a /icesmp client stats felülethez. */
    public record BridgeStats(long received, long droppedDisabled, long droppedOversized,
                              long droppedMalformed, long droppedRateLimited, long droppedStale,
                              long rejectedHandshakes, long acceptedHandshakes, int activeSessions) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ClientSessionRegistry sessions = new ClientSessionRegistry();
    private final ClientRateLimiter rateLimiter = new ClientRateLimiter();

    /** A core köti be (setter-injektálás), mert a HudManager a bridge után épül. */
    private volatile Function<UUID, HudManager.HudSnapshot> hudSnapshotSource;

    /**
     * Az utoljára kiküldött HUD-payload és kit-signature játékosonként: a HUD-tick
     * másodpercenként fut, de a vezetékre csak tényleges változás megy ki (event-driven
     * budget, terv §39). Új kézfogás/resync törli, így a friss session mindig teljes
     * state-tel indul.
     */
    private final ConcurrentHashMap<UUID, byte[]> lastHudState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, byte[]> lastKitSignature = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> lastSpellbookSignature = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, byte[]> lastProfileState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, byte[]> lastRelicState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, byte[]> lastTalentState = new ConcurrentHashMap<>();

    /** A core köti be; a slot-cast és a kit-projekció a canonical cast-koordinátort használja. */
    private volatile AbilityCatalystListener abilityCatalyst;
    private volatile SpellRegistry spellRegistry;

    /** A core köti be: a /profile GUI-val azonos tartalmú PROFILE_STATE forrása. */
    private volatile Function<Player, ProfileStatePayload> profileStateSource;

    /** A core köti be: a saját class-relic aktiváció RELIC_STATE forrása (UUID-only, olcsó). */
    private volatile Function<UUID, RelicStatePayload> relicStateSource;

    /** A core köti be; a vásárlás a CAS-védett spendPoint use-case-en fut. */
    private volatile TalentManager talentManager;

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
        lastHudState.clear();
        lastKitSignature.clear();
        lastSpellbookSignature.clear();
        lastProfileState.clear();
        lastRelicState.clear();
        lastTalentState.clear();
    }

    @Override
    public void onPluginMessageReceived(final @NonNull String channel, final @NonNull Player player,
                                        final byte @NonNull [] message) {
        if (!ClientProtocol.CHANNEL.equals(channel)) {
            return;
        }
        received.incrementAndGet();
        if (!enabled()) {
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
            case ClientProtocol.MSG_CAST_SLOT -> handleCastSlot(player, envelope, now);
            case ClientProtocol.MSG_SELECT_SPELL -> handleSelectSpell(player, envelope, now);
            case ClientProtocol.MSG_TOGGLE_FAVORITE -> handleToggleFavorite(player, envelope, now);
            case ClientProtocol.MSG_PURCHASE_TALENT -> handlePurchaseTalent(player, envelope, now);
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
        // Az új session teljes kezdő state-et kap; a következő HUD-tickre nem várunk.
        // A state-építés player-állapotot olvas, ezért a játékos régió-szálán fut; a
        // generation-ellenőrzés védi ki a közben történt re-handshake-et.
        lastHudState.remove(player.getUniqueId());
        lastKitSignature.remove(player.getUniqueId());
        lastSpellbookSignature.remove(player.getUniqueId());
        lastProfileState.remove(player.getUniqueId());
        lastRelicState.remove(player.getUniqueId());
        lastTalentState.remove(player.getUniqueId());
        final long acceptedGeneration = session.generation();
        player.getScheduler().run(plugin, task -> {
            final ClientSession live = sessions.find(player.getUniqueId()).orElse(null);
            if (live != null && live.generation() == acceptedGeneration && player.isOnline()) {
                pushFullState(player, live);
            }
        }, null);
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

    /**
     * CAST_SLOT: a kliens csak pozíciót kér; a spell-feloldást és a teljes tranzakciós
     * validációt a canonical cast-koordinátor ({@code castActiveKitSlot}) végzi a
     * játékos régió-szálán — pontosan az az útvonal, amin a katalizátor-input is fut.
     * A kliens minden kérésre gépi ACTION_RESULT választ kap (requestId-korrelációval),
     * sikeres cast után azonnali friss kit-state-tel (cooldown-start).
     */
    private void handleCastSlot(final Player player, final MessageEnvelope envelope, final long now) {
        final ClientSession session = liveSession(player.getUniqueId(), envelope, now);
        if (session == null) {
            return;
        }
        if (!configManager.getBoolean("client.features.keybind-cast", false)
                || !session.capabilities().contains(ClientCapability.KEYBIND_CAST)) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_CAST_SLOT,
                    ClientProtocol.RESULT_NOT_ALLOWED, "CAPABILITY");
            return;
        }
        if (!rateLimiter.tryAcquire(player.getUniqueId(), ClientRateLimiter.Category.CAST,
                configManager.getInt("client.limits.cast-messages-per-second", 8), 1000L, now)) {
            droppedRateLimited.incrementAndGet();
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_CAST_SLOT,
                    ClientProtocol.RESULT_RATE_LIMITED, "");
            return;
        }
        final CastSlotPayload request;
        try {
            request = CastSlotPayload.decode(envelope.payload());
        } catch (final ClientProtocolException malformed) {
            droppedMalformed.incrementAndGet();
            debug(() -> "malformed CAST_SLOT from " + player.getName() + ": " + malformed.getMessage());
            return;
        }
        final AbilityCatalystListener catalyst = abilityCatalyst;
        if (catalyst == null) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_CAST_SLOT,
                    ClientProtocol.RESULT_SERVER_ERROR, "UNAVAILABLE");
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            final AbilityCatalystListener.SlotCastOutcome outcome =
                    catalyst.castActiveKitSlot(player, request.slot());
            sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_ACTION_RESULT,
                    session.generation(), session.nextOutboundSequence(), envelope.requestId(),
                    new ActionResultPayload(ClientProtocol.MSG_CAST_SLOT,
                            mapCastResult(outcome.status()), outcome.status().name()).encode()));
            if (abilityBarActive(session)) {
                pushKitNow(player, session, true);
            }
        }, null);
    }

    private static String mapCastResult(final AbilityCatalystListener.SlotCastStatus status) {
        return switch (status) {
            case SUCCESS -> ClientProtocol.RESULT_SUCCESS;
            case NO_CATALYST -> ClientProtocol.RESULT_NOT_ALLOWED;
            case PROFILE_NOT_READY, EMPTY_KIT, INVALID_SLOT -> ClientProtocol.RESULT_INVALID_STATE;
            case DEBOUNCED -> ClientProtocol.RESULT_RATE_LIMITED;
            case ON_COOLDOWN, NOT_READY, CLASS_GATE_BLOCKED -> ClientProtocol.RESULT_NOT_READY;
            case INSUFFICIENT_COST, NOT_COMMITTED -> ClientProtocol.RESULT_REJECTED;
            case EXECUTION_FAILED -> ClientProtocol.RESULT_SERVER_ERROR;
        };
    }

    private void sendActionResult(final Player player, final ClientSession session, final UUID requestId,
                                  final int actionType, final String result, final String reason) {
        send(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_ACTION_RESULT,
                session.generation(), session.nextOutboundSequence(), requestId,
                new ActionResultPayload(actionType, result, reason).encode()));
    }

    /**
     * SELECT_SPELL: a katalizátor-ciklázás párja — a meglévő validált use-case fut
     * (csak aktív-kit-tag választható, azonos játékos-üzenettel), a kliens gépi
     * választ és friss state-et kap.
     */
    private void handleSelectSpell(final Player player, final MessageEnvelope envelope, final long now) {
        handleSpellAction(player, envelope, now, ClientProtocol.MSG_SELECT_SPELL);
    }

    /** TOGGLE_FAVORITE: a vanilla spellbook shift-katt párja (cappelt, durable commit). */
    private void handleToggleFavorite(final Player player, final MessageEnvelope envelope, final long now) {
        handleSpellAction(player, envelope, now, ClientProtocol.MSG_TOGGLE_FAVORITE);
    }

    private void handleSpellAction(final Player player, final MessageEnvelope envelope, final long now,
                                   final int actionType) {
        final ClientSession session = liveSession(player.getUniqueId(), envelope, now);
        if (session == null) {
            return;
        }
        if (!configManager.getBoolean("client.features.native-spellbook", false)
                || !session.capabilities().contains(ClientCapability.NATIVE_SPELLBOOK)) {
            sendActionResult(player, session, envelope.requestId(), actionType,
                    ClientProtocol.RESULT_NOT_ALLOWED, "CAPABILITY");
            return;
        }
        if (!rateLimiter.tryAcquire(player.getUniqueId(), ClientRateLimiter.Category.UI,
                configManager.getInt("client.limits.ui-actions-per-second", 10), 1000L, now)) {
            droppedRateLimited.incrementAndGet();
            sendActionResult(player, session, envelope.requestId(), actionType,
                    ClientProtocol.RESULT_RATE_LIMITED, "");
            return;
        }
        final SpellActionPayload request;
        try {
            request = SpellActionPayload.decode(envelope.payload());
        } catch (final ClientProtocolException malformed) {
            droppedMalformed.incrementAndGet();
            debug(() -> "malformed spell action from " + player.getName() + ": " + malformed.getMessage());
            return;
        }
        final AbilityCatalystListener catalyst = abilityCatalyst;
        if (catalyst == null) {
            sendActionResult(player, session, envelope.requestId(), actionType,
                    ClientProtocol.RESULT_SERVER_ERROR, "UNAVAILABLE");
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            if (actionType == ClientProtocol.MSG_SELECT_SPELL) {
                final boolean selected = catalyst.selectSpell(player, request.spellId());
                sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_ACTION_RESULT,
                        session.generation(), session.nextOutboundSequence(), envelope.requestId(),
                        new ActionResultPayload(actionType,
                                selected ? ClientProtocol.RESULT_SUCCESS : ClientProtocol.RESULT_REJECTED,
                                selected ? "" : "NOT_IN_ACTIVE_KIT").encode()));
                pushSpellbookAndKit(player, session);
            } else {
                catalyst.toggleFavorite(player, request.spellId()).whenComplete((result, failure) ->
                        player.getScheduler().run(plugin, done -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            final String code;
                            final String reason;
                            if (failure != null) {
                                code = ClientProtocol.RESULT_SERVER_ERROR;
                                reason = "PERSISTENCE";
                            } else if (result == SpellFavoritesManager.ToggleResult.LIMIT_REACHED) {
                                code = ClientProtocol.RESULT_REJECTED;
                                reason = "LIMIT_REACHED";
                            } else {
                                code = ClientProtocol.RESULT_SUCCESS;
                                reason = result.name();
                            }
                            sendNow(player, new MessageEnvelope(session.protocolVersion(),
                                    ClientProtocol.MSG_ACTION_RESULT, session.generation(),
                                    session.nextOutboundSequence(), envelope.requestId(),
                                    new ActionResultPayload(actionType, code, reason).encode()));
                            pushSpellbookAndKit(player, session);
                        }, null));
            }
        }, null);
    }

    /** Action után a kedvenc/kiválasztás a kit-összetételt is érintheti — mindkét state frissül. */
    private void pushSpellbookAndKit(final Player player, final ClientSession session) {
        if (nativeSpellbookActive(session)) {
            pushSpellbookNow(player, session);
        }
        if (abilityBarActive(session)) {
            pushKitNow(player, session, true);
        }
    }

    /** Admin-oldali kényszerített resync (/icesmp client resync). */
    public boolean requestResync(final Player target) {
        if (!enabled()) {
            return false;
        }
        final Optional<ClientSession> session = sessions.find(target.getUniqueId());
        if (session.isEmpty()) {
            return false;
        }
        sendResync(target, session.get(), MessageEnvelope.NO_REQUEST);
        return true;
    }

    /**
     * Resync-szerződés: a BEGIN minden kliensoldali cache eldobását jelenti, a két
     * jelzés között a bekötött state-domainek teljes friss state-et küldenek (HUD +
     * ability kit), az END zárja a kört. A teljes sor a játékos régió-szálán, egyetlen
     * taskban megy ki, mert a state-építés player-állapotot olvas, és a BEGIN/state/END
     * sorrend nem eshet szét külön ütemezett küldésekre.
     */
    private void sendResync(final Player player, final ClientSession session, final UUID requestId) {
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_RESYNC_BEGIN,
                    session.generation(), session.nextOutboundSequence(), requestId, new byte[0]));
            lastHudState.remove(player.getUniqueId());
            lastKitSignature.remove(player.getUniqueId());
            lastSpellbookSignature.remove(player.getUniqueId());
            lastProfileState.remove(player.getUniqueId());
            lastRelicState.remove(player.getUniqueId());
            lastTalentState.remove(player.getUniqueId());
            pushFullState(player, session);
            sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_RESYNC_END,
                    session.generation(), session.nextOutboundSequence(), requestId, new byte[0]));
        }, null);
    }

    /** {@link HudManager.ClientHudRoute}: a HUD-tick és a vanilla-suppression kapuja. */
    @Override
    public boolean nativeHudActive(final UUID playerId) {
        if (!enabled() || !configManager.getBoolean("client.features.native-hud", false)) {
            return false;
        }
        return sessions.find(playerId)
                .map(session -> session.capabilities().contains(ClientCapability.NATIVE_HUD))
                .orElse(false);
    }

    private boolean abilityBarActive(final ClientSession session) {
        return configManager.getBoolean("client.features.ability-bar", false)
                && session.capabilities().contains(ClientCapability.ABILITY_BAR);
    }

    private boolean nativeSpellbookActive(final ClientSession session) {
        return configManager.getBoolean("client.features.native-spellbook", false)
                && session.capabilities().contains(ClientCapability.NATIVE_SPELLBOOK);
    }

    private boolean nativeProfileActive(final ClientSession session) {
        return configManager.getBoolean("client.features.native-profile", false)
                && session.capabilities().contains(ClientCapability.NATIVE_PROFILE);
    }

    private boolean relicRenderActive(final ClientSession session) {
        return configManager.getBoolean("client.features.relic-render-v1", false)
                && session.capabilities().contains(ClientCapability.RELIC_RENDER_V1);
    }

    private boolean nativeTalentsActive(final ClientSession session) {
        return configManager.getBoolean("client.features.native-talents", false)
                && session.capabilities().contains(ClientCapability.NATIVE_TALENTS);
    }

    /**
     * {@link HudManager.ClientHudRoute}: a HUD-tick a játékos régió-szálán hívja minden
     * online játékosra; a session/capability kapuzás itt történik, a HudManager a híd
     * belső szabályait nem ismeri.
     */
    @Override
    public void pushClientState(final Player player, final HudManager.HudSnapshot snapshot) {
        final ClientSession session = sessions.find(player.getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        if (snapshot != null && nativeHudActive(player.getUniqueId())) {
            pushHudNow(player, session, snapshot);
        }
        if (abilityBarActive(session)) {
            pushKitNow(player, session, false);
        }
        if (nativeSpellbookActive(session)) {
            pushSpellbookIfChanged(player, session);
        }
        if (nativeProfileActive(session)) {
            pushProfileNow(player, session);
        }
        if (relicRenderActive(session)) {
            pushRelicNow(player, session);
        }
        if (nativeTalentsActive(session)) {
            pushTalentNow(player, session);
        }
    }

    /** Teljes state-küldés (kézfogás után és resync BEGIN/END között); player-szálon hívandó. */
    private void pushFullState(final Player player, final ClientSession session) {
        final Function<UUID, HudManager.HudSnapshot> source = hudSnapshotSource;
        if (source != null && nativeHudActive(player.getUniqueId())) {
            final HudManager.HudSnapshot snapshot = source.apply(player.getUniqueId());
            if (snapshot != null) {
                pushHudNow(player, session, snapshot);
            }
        }
        if (abilityBarActive(session)) {
            pushKitNow(player, session, true);
        }
        if (nativeSpellbookActive(session)) {
            pushSpellbookNow(player, session);
        }
        if (nativeProfileActive(session)) {
            pushProfileNow(player, session);
        }
        if (relicRenderActive(session)) {
            pushRelicNow(player, session);
        }
        if (nativeTalentsActive(session)) {
            pushTalentNow(player, session);
        }
    }

    private void pushHudNow(final Player player, final ClientSession session,
                            final HudManager.HudSnapshot snapshot) {
        final byte[] payload = ClientHudProjector.project(snapshot).encode();
        final byte[] previous = lastHudState.put(player.getUniqueId(), payload);
        if (previous != null && Arrays.equals(previous, payload)) {
            return;
        }
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_HUD_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, payload));
        debug(() -> "HUD_STATE sent to " + player.getName() + " (" + payload.length + " bytes)");
    }

    /**
     * Ability-kit state a bar-hoz; player-szálon hívandó (a kit-feloldás élő
     * player-állapotot olvas). A dedupe a normalizált change-signature-ön fut: a
     * másodpercenként fogyó maradék-cooldown nem generál forgalmat, csak összetétel-,
     * kiválasztás- és cooldown-állapotváltás (a kliens interpolál).
     */
    private void pushKitNow(final Player player, final ClientSession session, final boolean force) {
        final AbilityCatalystListener catalyst = abilityCatalyst;
        final SpellRegistry registry = spellRegistry;
        if (catalyst == null || registry == null) {
            return;
        }
        final List<String> active = catalyst.getActiveSpellIds(player);
        final String selectedId = catalyst.getSelectedSpellId(player);
        final List<AbilityKitPayload.Entry> entries = new ArrayList<>(active.size());
        for (final String spellId : active) {
            final Spell spell = registry.getById(spellId);
            if (spell == null) {
                continue;
            }
            entries.add(new AbilityKitPayload.Entry(spellId, spell.getName(),
                    catalyst.getDisplayedCostText(player, spell),
                    catalyst.getEffectiveCooldownMs(player, spell),
                    Math.max(0L, catalyst.getRemainingCooldownMs(player, spell)),
                    spellId.equals(selectedId)));
        }
        final AbilityKitPayload payload = new AbilityKitPayload(entries);
        final byte[] signature = payload.changeSignature().encode();
        final byte[] previous = lastKitSignature.put(player.getUniqueId(), signature);
        if (!force && previous != null && Arrays.equals(previous, signature)) {
            return;
        }
        final byte[] wirePayload = payload.encode();
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_ABILITY_KIT_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, wirePayload));
        debug(() -> "ABILITY_KIT_STATE sent to " + player.getName() + " (" + entries.size() + " slots)");
    }

    /**
     * Olcsó változás-jel: a teljes (describe-nehéz) spellbook-payload csak akkor épül
     * fel, ha az unlock/kedvenc/kiválasztás/kit-összetétel ténylegesen változott —
     * a tick-cadence így a spellbookra is event-driven marad.
     */
    private void pushSpellbookIfChanged(final Player player, final ClientSession session) {
        final AbilityCatalystListener catalyst = abilityCatalyst;
        if (catalyst == null) {
            return;
        }
        final String signature = String.join("|",
                String.valueOf(catalyst.getSelectedSpellId(player)),
                String.join(",", catalyst.getActiveSpellIds(player)),
                String.join(",", new java.util.TreeSet<>(catalyst.getFavoriteSpellIds(player))),
                String.join(",", catalyst.getUnlockedSpellIds(player)));
        if (signature.equals(lastSpellbookSignature.put(player.getUniqueId(), signature))) {
            return;
        }
        pushSpellbookNow(player, session);
    }

    /** Teljes spellbook-state; player-szálon hívandó (élő player-állapotot olvas). */
    private void pushSpellbookNow(final Player player, final ClientSession session) {
        final AbilityCatalystListener catalyst = abilityCatalyst;
        if (catalyst == null) {
            return;
        }
        final List<SpellbookGUI.Entry> entries = catalyst.spellbookEntries(player);
        final Set<String> favorites = catalyst.getFavoriteSpellIds(player);
        final List<String> active = catalyst.getActiveSpellIds(player);
        final String selectedId = catalyst.getSelectedSpellId(player);
        final List<SpellbookStatePayload.Entry> wire = new ArrayList<>(entries.size());
        for (final SpellbookGUI.Entry entry : entries) {
            if (wire.size() >= ClientProtocol.MAX_LIST_ELEMENTS) {
                // Protokoll-limit: a lista rendezett (feloldottak elöl), a levágott farok
                // a legmagasabb szint-követelményű zárolt spelleket érinti.
                break;
            }
            final Spell spell = entry.spell();
            final String id = spell.getId();
            final List<String> description = spell.describe();
            wire.add(new SpellbookStatePayload.Entry(id, spell.getName(),
                    description.size() <= ClientProtocol.MAX_LIST_ELEMENTS
                            ? description : description.subList(0, ClientProtocol.MAX_LIST_ELEMENTS),
                    entry.requiredLevel(), entry.unlocked(),
                    favorites.contains(id),
                    entry.unlocked() && id.equalsIgnoreCase(selectedId),
                    entry.unlocked() && active.contains(id),
                    catalyst.getMasteryRank(player, id),
                    catalyst.getDisplayedCostText(player, spell),
                    spell.getCooldown()));
        }
        final byte[] payload = new SpellbookStatePayload(wire).encode();
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_SPELLBOOK_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, payload));
        debug(() -> "SPELLBOOK_STATE sent to " + player.getName() + " (" + wire.size() + " entries)");
    }

    /**
     * PROFILE_STATE a /profile GUI tartalmával; player-szálon hívandó. Bájt-dedupe a
     * HUD mintájára: a tick csak tényleges változásnál küld.
     */
    private void pushProfileNow(final Player player, final ClientSession session) {
        final Function<Player, ProfileStatePayload> source = profileStateSource;
        if (source == null) {
            return;
        }
        final byte[] payload = source.apply(player).encode();
        final byte[] previous = lastProfileState.put(player.getUniqueId(), payload);
        if (previous != null && Arrays.equals(previous, payload)) {
            return;
        }
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_PROFILE_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, payload));
        debug(() -> "PROFILE_STATE sent to " + player.getName() + " (" + payload.length + " bytes)");
    }

    public void connectProfile(final Function<Player, ProfileStatePayload> source) {
        this.profileStateSource = source;
    }

    /** RELIC_STATE: a resolve UUID-only és lock-mentes, a tick-cadence olcsón elbírja. */
    private void pushRelicNow(final Player player, final ClientSession session) {
        final Function<UUID, RelicStatePayload> source = relicStateSource;
        if (source == null) {
            return;
        }
        final byte[] payload = source.apply(player.getUniqueId()).encode();
        final byte[] previous = lastRelicState.put(player.getUniqueId(), payload);
        if (previous != null && Arrays.equals(previous, payload)) {
            return;
        }
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_RELIC_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, payload));
        debug(() -> "RELIC_STATE sent to " + player.getName());
    }

    public void connectRelicState(final Function<UUID, RelicStatePayload> source) {
        this.relicStateSource = source;
    }

    /** TALENT_STATE a vanilla GUI-val azonos szűréssel; player-szálon hívandó. */
    private void pushTalentNow(final Player player, final ClientSession session) {
        final TalentManager talents = talentManager;
        if (talents == null) {
            return;
        }
        final byte[] payload = ClientTalentProjector.project(player, talents).encode();
        final byte[] previous = lastTalentState.put(player.getUniqueId(), payload);
        if (previous != null && Arrays.equals(previous, payload)) {
            return;
        }
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_TALENT_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, payload));
        debug(() -> "TALENT_STATE sent to " + player.getName());
    }

    public void connectTalents(final TalentManager manager) {
        this.talentManager = manager;
    }

    /**
     * PURCHASE_TALENT: a meglévő CAS-védett spendPoint use-case-en fut — minden
     * requirement/fa-gate/pont-fedezet a tranzakción belül validálódik, a kliens
     * csak kér. A durable commit aszinkron; a válasz és a friss state a játékos
     * régió-szálára visszahoppolva megy ki.
     */
    private void handlePurchaseTalent(final Player player, final MessageEnvelope envelope, final long now) {
        final ClientSession session = liveSession(player.getUniqueId(), envelope, now);
        if (session == null) {
            return;
        }
        if (!nativeTalentsActive(session)) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_PURCHASE_TALENT,
                    ClientProtocol.RESULT_NOT_ALLOWED, "CAPABILITY");
            return;
        }
        if (!rateLimiter.tryAcquire(player.getUniqueId(), ClientRateLimiter.Category.UI,
                configManager.getInt("client.limits.ui-actions-per-second", 10), 1000L, now)) {
            droppedRateLimited.incrementAndGet();
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_PURCHASE_TALENT,
                    ClientProtocol.RESULT_RATE_LIMITED, "");
            return;
        }
        final TalentActionPayload request;
        try {
            request = TalentActionPayload.decode(envelope.payload());
        } catch (final ClientProtocolException malformed) {
            droppedMalformed.incrementAndGet();
            debug(() -> "malformed PURCHASE_TALENT from " + player.getName() + ": " + malformed.getMessage());
            return;
        }
        final TalentManager talents = talentManager;
        if (talents == null) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_PURCHASE_TALENT,
                    ClientProtocol.RESULT_SERVER_ERROR, "UNAVAILABLE");
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            talents.spendPoint(player, request.classPool(), request.talentId())
                    .whenComplete((spent, failure) -> player.getScheduler().run(plugin, done -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        final String code;
                        final String reason;
                        if (failure != null) {
                            code = ClientProtocol.RESULT_SERVER_ERROR;
                            reason = "PERSISTENCE";
                        } else if (Boolean.TRUE.equals(spent)) {
                            code = ClientProtocol.RESULT_SUCCESS;
                            reason = "";
                        } else {
                            code = ClientProtocol.RESULT_REJECTED;
                            reason = "REQUIREMENTS";
                        }
                        sendNow(player, new MessageEnvelope(session.protocolVersion(),
                                ClientProtocol.MSG_ACTION_RESULT, session.generation(),
                                session.nextOutboundSequence(), envelope.requestId(),
                                new ActionResultPayload(ClientProtocol.MSG_PURCHASE_TALENT,
                                        code, reason).encode()));
                        if (nativeTalentsActive(session)) {
                            pushTalentNow(player, session);
                        }
                        if (nativeProfileActive(session)) {
                            pushProfileNow(player, session);
                        }
                    }, null));
        }, null);
    }

    public void connectHudSnapshots(final Function<UUID, HudManager.HudSnapshot> source) {
        this.hudSnapshotSource = source;
    }

    public void connectAbilityKit(final AbilityCatalystListener catalyst, final SpellRegistry registry) {
        this.abilityCatalyst = catalyst;
        this.spellRegistry = registry;
    }

    /** Session-, generation- és sequence-kapu minden kézfogás utáni üzenetre. */
    private ClientSession liveSession(final UUID playerId, final MessageEnvelope envelope, final long now) {
        final ClientSession session = sessions.find(playerId).orElse(null);
        if (session == null || !session.acceptInbound(envelope.protocolVersion(),
                envelope.sessionGeneration(), envelope.sequence(), now)) {
            droppedStale.incrementAndGet();
            return null;
        }
        return session;
    }

    /**
     * Az élő kill switch minden belépési ponton sessiontelen állapotot kényszerít.
     * Újraengedélyezés után ezért kötelező az új kézfogás.
     */
    private boolean enabled() {
        final boolean enabled = configManager.getBoolean("client.enabled", true);
        if (!enabled) {
            sessions.clear();
            rateLimiter.clear();
        }
        return enabled;
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

    /** Küldés a játékos régió-szálán belülről — a hívó felelőssége a szál-kontextus. */
    private void sendNow(final Player player, final MessageEnvelope envelope) {
        player.sendPluginMessage(plugin, ClientProtocol.CHANNEL, ClientMessageCodec.encodeEnvelope(envelope));
    }

    private void debug(final java.util.function.Supplier<String> message) {
        if (configManager.getBoolean("client.debug", false)) {
            plugin.getLogger().info("[ClientBridge] " + message.get());
        }
    }

    public Optional<ClientSession> sessionOf(final UUID playerId) {
        if (!enabled()) return Optional.empty();
        return sessions.find(playerId);
    }

    public List<ClientSession> activeSessions() {
        if (!enabled()) return List.of();
        return sessions.snapshot();
    }

    public BridgeStats stats() {
        enabled();
        return new BridgeStats(received.get(), droppedDisabled.get(), droppedOversized.get(),
                droppedMalformed.get(), droppedRateLimited.get(), droppedStale.get(),
                rejectedHandshakes.get(), acceptedHandshakes.get(), sessions.size());
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        sessions.invalidate(playerId);
        rateLimiter.clearPlayer(playerId);
        lastHudState.remove(playerId);
        lastKitSignature.remove(playerId);
        lastSpellbookSignature.remove(playerId);
        lastProfileState.remove(playerId);
        lastRelicState.remove(playerId);
        lastTalentState.remove(playerId);
    }
}
