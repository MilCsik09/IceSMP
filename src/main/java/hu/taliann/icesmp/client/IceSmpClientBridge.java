package hu.taliann.icesmp.client;

import hu.taliann.icesmp.client.projection.ClientHudProjector;
import hu.taliann.icesmp.client.projection.ClientPartyProjector;
import hu.taliann.icesmp.client.projection.ClientProfessionProjector;
import hu.taliann.icesmp.client.projection.ClientQuestProjector;
import hu.taliann.icesmp.client.projection.ClientRecipeProjector;
import hu.taliann.icesmp.client.protocol.AbilityKitPayload;
import hu.taliann.icesmp.client.protocol.ActionResultPayload;
import hu.taliann.icesmp.client.protocol.BrowseRecipesPayload;
import hu.taliann.icesmp.client.protocol.BossStatePayload;
import hu.taliann.icesmp.client.protocol.CastSlotPayload;
import hu.taliann.icesmp.client.protocol.ClientHello;
import hu.taliann.icesmp.client.protocol.ClientMessageCodec;
import hu.taliann.icesmp.client.protocol.ClientProtocol;
import hu.taliann.icesmp.client.protocol.ClientProtocolException;
import hu.taliann.icesmp.client.protocol.MessageEnvelope;
import hu.taliann.icesmp.client.protocol.ProfessionActionPayload;
import hu.taliann.icesmp.client.protocol.ProfileStatePayload;
import hu.taliann.icesmp.client.protocol.ProtocolReject;
import hu.taliann.icesmp.client.protocol.QuestStatePayload;
import hu.taliann.icesmp.client.protocol.QuestTrackPayload;
import hu.taliann.icesmp.client.protocol.RelicAttachmentPayload;
import hu.taliann.icesmp.client.protocol.RelicStatePayload;
import hu.taliann.icesmp.client.protocol.ServerHello;
import hu.taliann.icesmp.client.protocol.SpellActionPayload;
import hu.taliann.icesmp.client.protocol.SpellbookStatePayload;
import hu.taliann.icesmp.client.protocol.TalentActionPayload;
import hu.taliann.icesmp.client.protocol.TerritoryStatePayload;
import hu.taliann.icesmp.client.protocol.TalentStatePayload;
import hu.taliann.icesmp.client.projection.ClientTalentProjector;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.gui.SpellbookGUI;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.SpellFavoritesManager;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.HudManager;
import hu.taliann.icesmp.managers.PartyManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.managers.QuestManager;
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
    private final ConcurrentHashMap<UUID, String> lastQuestSignature = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, byte[]> lastProfessionState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, byte[]> lastRelicAttachment = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, byte[]> lastPartyState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, byte[]> lastBossState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, byte[]> lastTerritoryState = new ConcurrentHashMap<>();

    /** A core köti be; a slot-cast és a kit-projekció a canonical cast-koordinátort használja. */
    private volatile AbilityCatalystListener abilityCatalyst;
    private volatile SpellRegistry spellRegistry;

    /** A core köti be: a /profile GUI-val azonos tartalmú PROFILE_STATE forrása. */
    private volatile Function<Player, ProfileStatePayload> profileStateSource;

    /** A core köti be: a saját class-relic aktiváció RELIC_STATE forrása (UUID-only, olcsó). */
    private volatile Function<UUID, RelicStatePayload> relicStateSource;

    /** A core köti be; a vásárlás a CAS-védett spendPoint use-case-en fut. */
    private volatile TalentManager talentManager;

    /** A core köti be; a láthatóság egyetlen forrása az isVisible-szűrt read-API. */
    private volatile QuestManager questManager;

    /** A core köti be; a szakma-akciók a meglévő CAS-/kapu-védett use-case-eken futnak. */
    private volatile ProfessionManager professionManager;
    private volatile SpecializationManager specializationManager;
    private volatile ProfessionRecipeCatalog professionRecipeCatalog;
    private volatile ProfessionWeeklyGoalManager professionWeeklyGoal;
    private volatile UniqueMaterialFactory uniqueMaterialFactory;

    /** A core köti be; a party-frame read-only, mutáció a /party parancson marad. */
    private volatile PartyManager partyManager;

    /** A core köti be; a boss-frame a világboss lock-mentes display-tükreit olvassa. */
    private volatile WorldBossManager worldBossManager;

    /** A core köti be; a zóna-lookup lock-mentes chunk-indexből fut a néző szálán. */
    private volatile TerritoryManager territoryManager;
    private volatile RaidManager raidManager;

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
        lastQuestSignature.clear();
        lastProfessionState.clear();
        lastRelicAttachment.clear();
        lastPartyState.clear();
        lastBossState.clear();
        lastTerritoryState.clear();
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
            case ClientProtocol.MSG_TRACK_QUEST -> handleTrackQuest(player, envelope, now);
            case ClientProtocol.MSG_SELECT_PROFESSION -> handleSelectProfession(player, envelope, now);
            case ClientProtocol.MSG_SELECT_PROFESSION_SPEC -> handleSelectProfessionSpec(player, envelope, now);
            case ClientProtocol.MSG_BROWSE_RECIPES -> handleBrowseRecipes(player, envelope, now);
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
        lastQuestSignature.remove(player.getUniqueId());
        lastProfessionState.remove(player.getUniqueId());
        lastRelicAttachment.remove(player.getUniqueId());
        lastPartyState.remove(player.getUniqueId());
        lastBossState.remove(player.getUniqueId());
        lastTerritoryState.remove(player.getUniqueId());
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
            lastQuestSignature.remove(player.getUniqueId());
            lastProfessionState.remove(player.getUniqueId());
            lastRelicAttachment.remove(player.getUniqueId());
            lastPartyState.remove(player.getUniqueId());
            lastBossState.remove(player.getUniqueId());
            lastTerritoryState.remove(player.getUniqueId());
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

    private boolean relicAttachmentActive(final ClientSession session) {
        return configManager.getBoolean("client.features.relic-attachment-v1", false)
                && session.capabilities().contains(ClientCapability.RELIC_ATTACHMENT_V1);
    }

    private boolean partyFrameActive(final ClientSession session) {
        return configManager.getBoolean("client.features.party-frame", false)
                && session.capabilities().contains(ClientCapability.PARTY_FRAME);
    }

    private boolean bossFrameActive(final ClientSession session) {
        return configManager.getBoolean("client.features.boss-frame", false)
                && session.capabilities().contains(ClientCapability.BOSS_FRAME);
    }

    private boolean territoryOverlayActive(final ClientSession session) {
        return configManager.getBoolean("client.features.territory-overlay", false)
                && session.capabilities().contains(ClientCapability.TERRITORY_OVERLAY);
    }

    /** {@link HudManager.ClientHudRoute}: a vanilla világboss-bar suppression-kapuja. */
    @Override
    public boolean bossFrameActive(final UUID playerId) {
        if (!enabled() || !configManager.getBoolean("client.features.boss-frame", false)) {
            return false;
        }
        return sessions.find(playerId)
                .map(session -> session.capabilities().contains(ClientCapability.BOSS_FRAME))
                .orElse(false);
    }

    private boolean nativeTalentsActive(final ClientSession session) {
        return configManager.getBoolean("client.features.native-talents", false)
                && session.capabilities().contains(ClientCapability.NATIVE_TALENTS);
    }

    private boolean questJournalActive(final ClientSession session) {
        return configManager.getBoolean("client.features.quest-journal", false)
                && session.capabilities().contains(ClientCapability.QUEST_JOURNAL);
    }

    private boolean nativeProfessionsActive(final ClientSession session) {
        return configManager.getBoolean("client.features.native-professions", false)
                && session.capabilities().contains(ClientCapability.NATIVE_PROFESSIONS);
    }

    private boolean recipeBrowserActive(final ClientSession session) {
        return configManager.getBoolean("client.features.recipe-browser", false)
                && session.capabilities().contains(ClientCapability.RECIPE_BROWSER);
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
        if (questJournalActive(session)) {
            pushQuestIfChanged(player, session);
        }
        if (nativeProfessionsActive(session)) {
            pushProfessionNow(player, session);
        }
        if (relicAttachmentActive(session)) {
            pushRelicAttachmentsNow(player, session);
        }
        if (partyFrameActive(session)) {
            pushPartyNow(player, session);
        }
        if (bossFrameActive(session)) {
            pushBossNow(player, session);
        }
        if (territoryOverlayActive(session)) {
            pushTerritoryNow(player, session);
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
        if (questJournalActive(session)) {
            pushQuestNow(player, session);
        }
        if (nativeProfessionsActive(session)) {
            pushProfessionNow(player, session);
        }
        if (relicAttachmentActive(session)) {
            pushRelicAttachmentsNow(player, session);
        }
        if (partyFrameActive(session)) {
            pushPartyNow(player, session);
        }
        if (bossFrameActive(session)) {
            pushBossNow(player, session);
        }
        if (territoryOverlayActive(session)) {
            pushTerritoryNow(player, session);
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

    /**
     * RELIC_STATE: a resolve UUID-only és lock-mentes, a tick-cadence olcsón elbírja.
     * A dedupe a normalizált change-signature-ön fut: a fogyó awakening-maradék nem
     * generál forgalmat, csak tényleges állapotváltás (a kliens interpolál).
     */
    private void pushRelicNow(final Player player, final ClientSession session) {
        final Function<UUID, RelicStatePayload> source = relicStateSource;
        if (source == null) {
            return;
        }
        final RelicStatePayload payload = source.apply(player.getUniqueId());
        final byte[] signature = payload.changeSignature().encode();
        final byte[] previous = lastRelicState.put(player.getUniqueId(), signature);
        if (previous != null && Arrays.equals(previous, signature)) {
            return;
        }
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_RELIC_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST,
                payload.encode()));
        debug(() -> "RELIC_STATE sent to " + player.getName());
    }

    public void connectRelicState(final Function<UUID, RelicStatePayload> source) {
        this.relicStateSource = source;
    }

    /**
     * RELIC_ATTACHMENT_STATE: a néző közelében lévő aktív viselők listája. Folia-safe
     * kereszt-régiós út: a pozíciók az owner-thread-frissített PositionCache tükréből,
     * az aktiváció a lock-mentes relic-resolve-ból jön — idegen Player-objektumot a
     * néző szála nem érint. A lista determinisztikusan rendezett, hogy a bájt-dedupe
     * ne churn-öljön a konkurens map iterációs sorrendjén.
     */
    private void pushRelicAttachmentsNow(final Player player, final ClientSession session) {
        final Function<UUID, RelicStatePayload> source = relicStateSource;
        if (source == null) {
            return;
        }
        final double radius = Math.max(0,
                configManager.getInt("client.limits.relic-attachment-radius", 32));
        final List<UUID> nearby = hu.taliann.icesmp.utils.PositionCache
                .nearbyPlayerIds(player.getUniqueId(), radius);
        nearby.sort(java.util.Comparator.naturalOrder());
        final List<RelicAttachmentPayload.Wearer> wearers = new ArrayList<>();
        for (final UUID wearerId : nearby) {
            if (wearers.size() >= ClientProtocol.MAX_LIST_ELEMENTS) {
                break;
            }
            final RelicStatePayload state = source.apply(wearerId);
            if (state.basePowerActive()) {
                wearers.add(new RelicAttachmentPayload.Wearer(wearerId, state.relicId(),
                        state.displayName(), state.resonanceActive()));
            }
        }
        final byte[] payload = new RelicAttachmentPayload(wearers).encode();
        final byte[] previous = lastRelicAttachment.put(player.getUniqueId(), payload);
        if (previous != null && Arrays.equals(previous, payload)) {
            return;
        }
        sendNow(player, new MessageEnvelope(session.protocolVersion(),
                ClientProtocol.MSG_RELIC_ATTACHMENT_STATE, session.generation(),
                session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, payload));
        debug(() -> "RELIC_ATTACHMENT_STATE sent to " + player.getName()
                + " (" + wearers.size() + " wearers)");
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
     * Olcsó quest-változásjel: a teljes (katalógus-bejárós) payload csak akkor épül
     * fel, ha az aktív progressz, a fül-összetétel vagy a követés ténylegesen változott.
     */
    private void pushQuestIfChanged(final Player player, final ClientSession session) {
        final QuestManager quests = questManager;
        if (quests == null) {
            return;
        }
        final StringBuilder signature = new StringBuilder();
        signature.append(quests.getTracked(player)).append('|');
        for (final String questId : quests.getActiveQuests(player)) {
            signature.append(questId).append('=').append(quests.describeProgress(player, questId)).append(';');
        }
        signature.append('|').append(String.join(",", quests.getReadyQuests(player)));
        signature.append('|').append(quests.getCompletedQuests(player).size());
        signature.append('|').append(String.join(",", quests.getVisibleQuestIds(player)));
        final String value = signature.toString();
        if (value.equals(lastQuestSignature.put(player.getUniqueId(), value))) {
            return;
        }
        pushQuestNow(player, session);
    }

    /** Teljes quest-journal state; player-szálon hívandó. */
    private void pushQuestNow(final Player player, final ClientSession session) {
        final QuestManager quests = questManager;
        if (quests == null) {
            return;
        }
        final byte[] payload = ClientQuestProjector.project(player, quests).encode();
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_QUEST_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, payload));
        debug(() -> "QUEST_STATE sent to " + player.getName() + " (" + payload.length + " bytes)");
    }

    public void connectQuests(final QuestManager manager) {
        this.questManager = manager;
    }

    /**
     * PROFESSION_STATE a szakma-áttekintő vanilla felületeivel azonos tartalommal;
     * player-szálon hívandó. Bájt-dedupe a profile mintájára: a tick-enként felépülő
     * payload csak tényleges változásnál megy ki a vezetékre.
     */
    private void pushProfessionNow(final Player player, final ClientSession session) {
        final ProfessionManager professions = professionManager;
        final SpecializationManager specializations = specializationManager;
        final ProfessionRecipeCatalog catalog = professionRecipeCatalog;
        final ProfessionWeeklyGoalManager weeklyGoal = professionWeeklyGoal;
        if (professions == null || specializations == null || catalog == null || weeklyGoal == null) {
            return;
        }
        final byte[] payload = ClientProfessionProjector.project(player, professions,
                specializations, catalog, weeklyGoal).encode();
        final byte[] previous = lastProfessionState.put(player.getUniqueId(), payload);
        if (previous != null && Arrays.equals(previous, payload)) {
            return;
        }
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_PROFESSION_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, payload));
        debug(() -> "PROFESSION_STATE sent to " + player.getName() + " (" + payload.length + " bytes)");
    }

    public void connectProfessions(final ProfessionManager professions,
                                   final SpecializationManager specializations,
                                   final ProfessionRecipeCatalog catalog,
                                   final ProfessionWeeklyGoalManager weeklyGoal,
                                   final UniqueMaterialFactory uniqueMaterials) {
        this.professionManager = professions;
        this.specializationManager = specializations;
        this.professionRecipeCatalog = catalog;
        this.professionWeeklyGoal = weeklyGoal;
        this.uniqueMaterialFactory = uniqueMaterials;
    }

    /**
     * BROWSE_RECIPES: pull-modellű query — a katalógus nem fér a push-protokoll
     * lista-limitjébe, ezért a kliens lapot kér, a válasz requestId-korrelált
     * RECIPE_PAGE. A lap a játékos régió-szálán épül (inventory-olvasás a have/need
     * értékekhez), a lap-méret és a lap-index szerveroldalon clampelt.
     */
    private void handleBrowseRecipes(final Player player, final MessageEnvelope envelope, final long now) {
        final ClientSession session = liveSession(player.getUniqueId(), envelope, now);
        if (session == null) {
            return;
        }
        if (!recipeBrowserActive(session)) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_BROWSE_RECIPES,
                    ClientProtocol.RESULT_NOT_ALLOWED, "CAPABILITY");
            return;
        }
        if (!rateLimiter.tryAcquire(player.getUniqueId(), ClientRateLimiter.Category.UI,
                configManager.getInt("client.limits.ui-actions-per-second", 10), 1000L, now)) {
            droppedRateLimited.incrementAndGet();
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_BROWSE_RECIPES,
                    ClientProtocol.RESULT_RATE_LIMITED, "");
            return;
        }
        final BrowseRecipesPayload request;
        try {
            request = BrowseRecipesPayload.decode(envelope.payload());
        } catch (final ClientProtocolException malformed) {
            droppedMalformed.incrementAndGet();
            debug(() -> "malformed BROWSE_RECIPES from " + player.getName() + ": " + malformed.getMessage());
            return;
        }
        final ProfessionManager professions = professionManager;
        final ProfessionRecipeCatalog catalog = professionRecipeCatalog;
        final UniqueMaterialFactory uniqueMaterials = uniqueMaterialFactory;
        if (professions == null || catalog == null || uniqueMaterials == null) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_BROWSE_RECIPES,
                    ClientProtocol.RESULT_SERVER_ERROR, "UNAVAILABLE");
            return;
        }
        final ProfessionType profession = ProfessionType.fromId(request.professionId());
        if (profession == null) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_BROWSE_RECIPES,
                    ClientProtocol.RESULT_REJECTED, "UNKNOWN_PROFESSION");
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            final byte[] payload = ClientRecipeProjector.page(player, profession, request.page(),
                    configManager.getInt("client.limits.recipe-page-size", 16),
                    professions, catalog, uniqueMaterials).encode();
            sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_RECIPE_PAGE,
                    session.generation(), session.nextOutboundSequence(), envelope.requestId(), payload));
            debug(() -> "RECIPE_PAGE sent to " + player.getName() + " (" + payload.length + " bytes)");
        }, null);
    }

    /**
     * SELECT_PROFESSION: a ProfessionGUI-kattintás párja — a döntést kizárólag a
     * CAS-mutáció hozza (foglalt kategória-slot = unchanged = REJECTED), a kliens csak
     * kér. Szakmaváltás kliensről sem lehetséges: a slot felszabadítása admin-út marad.
     */
    private void handleSelectProfession(final Player player, final MessageEnvelope envelope, final long now) {
        final ClientSession session = liveSession(player.getUniqueId(), envelope, now);
        if (session == null) {
            return;
        }
        if (!nativeProfessionsActive(session)) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_SELECT_PROFESSION,
                    ClientProtocol.RESULT_NOT_ALLOWED, "CAPABILITY");
            return;
        }
        if (!rateLimiter.tryAcquire(player.getUniqueId(), ClientRateLimiter.Category.UI,
                configManager.getInt("client.limits.ui-actions-per-second", 10), 1000L, now)) {
            droppedRateLimited.incrementAndGet();
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_SELECT_PROFESSION,
                    ClientProtocol.RESULT_RATE_LIMITED, "");
            return;
        }
        final ProfessionActionPayload request;
        try {
            request = ProfessionActionPayload.decode(envelope.payload());
        } catch (final ClientProtocolException malformed) {
            droppedMalformed.incrementAndGet();
            debug(() -> "malformed SELECT_PROFESSION from " + player.getName() + ": " + malformed.getMessage());
            return;
        }
        final ProfessionManager professions = professionManager;
        if (professions == null) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_SELECT_PROFESSION,
                    ClientProtocol.RESULT_SERVER_ERROR, "UNAVAILABLE");
            return;
        }
        final ProfessionType profession = ProfessionType.fromId(request.id());
        if (profession == null) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_SELECT_PROFESSION,
                    ClientProtocol.RESULT_REJECTED, "UNKNOWN_PROFESSION");
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            professions.selectProfession(player, profession)
                    .whenComplete((selected, failure) -> player.getScheduler().run(plugin, done -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        final String code;
                        final String reason;
                        if (failure != null) {
                            code = ClientProtocol.RESULT_SERVER_ERROR;
                            reason = "PERSISTENCE";
                        } else if (Boolean.TRUE.equals(selected)) {
                            code = ClientProtocol.RESULT_SUCCESS;
                            reason = "";
                        } else {
                            code = ClientProtocol.RESULT_REJECTED;
                            reason = "SLOT_TAKEN";
                        }
                        sendNow(player, new MessageEnvelope(session.protocolVersion(),
                                ClientProtocol.MSG_ACTION_RESULT, session.generation(),
                                session.nextOutboundSequence(), envelope.requestId(),
                                new ActionResultPayload(ClientProtocol.MSG_SELECT_PROFESSION,
                                        code, reason).encode()));
                        if (nativeProfessionsActive(session)) {
                            pushProfessionNow(player, session);
                        }
                        if (nativeProfileActive(session)) {
                            pushProfileNow(player, session);
                        }
                    }, null));
        }, null);
    }

    /**
     * SELECT_PROFESSION_SPEC: a SpecGUI-kattintás párja — a szakma-, szint- és
     * egyszeri-választás-kapu a meglévő canSelect/select use-case-ben fut. Respec
     * kliens-actionként szándékosan nincs (fizetős SpecGUI-döntés marad).
     */
    private void handleSelectProfessionSpec(final Player player, final MessageEnvelope envelope, final long now) {
        final ClientSession session = liveSession(player.getUniqueId(), envelope, now);
        if (session == null) {
            return;
        }
        if (!nativeProfessionsActive(session)) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_SELECT_PROFESSION_SPEC,
                    ClientProtocol.RESULT_NOT_ALLOWED, "CAPABILITY");
            return;
        }
        if (!rateLimiter.tryAcquire(player.getUniqueId(), ClientRateLimiter.Category.UI,
                configManager.getInt("client.limits.ui-actions-per-second", 10), 1000L, now)) {
            droppedRateLimited.incrementAndGet();
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_SELECT_PROFESSION_SPEC,
                    ClientProtocol.RESULT_RATE_LIMITED, "");
            return;
        }
        final ProfessionActionPayload request;
        try {
            request = ProfessionActionPayload.decode(envelope.payload());
        } catch (final ClientProtocolException malformed) {
            droppedMalformed.incrementAndGet();
            debug(() -> "malformed SELECT_PROFESSION_SPEC from " + player.getName() + ": " + malformed.getMessage());
            return;
        }
        final SpecializationManager specializations = specializationManager;
        if (specializations == null) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_SELECT_PROFESSION_SPEC,
                    ClientProtocol.RESULT_SERVER_ERROR, "UNAVAILABLE");
            return;
        }
        final ProfessionSpecializationType spec = ProfessionSpecializationType.fromId(request.id());
        if (spec == null) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_SELECT_PROFESSION_SPEC,
                    ClientProtocol.RESULT_REJECTED, "UNKNOWN_SPECIALIZATION");
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            final boolean selected = specializations.selectProfessionSpecialization(player, spec);
            sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_ACTION_RESULT,
                    session.generation(), session.nextOutboundSequence(), envelope.requestId(),
                    new ActionResultPayload(ClientProtocol.MSG_SELECT_PROFESSION_SPEC,
                            selected ? ClientProtocol.RESULT_SUCCESS : ClientProtocol.RESULT_REJECTED,
                            selected ? "" : "REQUIREMENTS").encode()));
            if (nativeProfessionsActive(session)) {
                pushProfessionNow(player, session);
            }
            if (nativeProfileActive(session)) {
                pushProfileNow(player, session);
            }
        }, null);
    }

    /**
     * TRACK_QUEST: az egyetlen kliensről engedett quest-mutáció — nincs forrás-kötése,
     * a szerver csak aktív questre engedi (üres id = követés törlése). Accept/turn-in
     * kliens-actionként tilos: azok forrás-authorityját (NPC/hely/esemény) csak a
     * hitelesített játék-esemény válthatja ki.
     */
    private void handleTrackQuest(final Player player, final MessageEnvelope envelope, final long now) {
        final ClientSession session = liveSession(player.getUniqueId(), envelope, now);
        if (session == null) {
            return;
        }
        if (!questJournalActive(session)) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_TRACK_QUEST,
                    ClientProtocol.RESULT_NOT_ALLOWED, "CAPABILITY");
            return;
        }
        if (!rateLimiter.tryAcquire(player.getUniqueId(), ClientRateLimiter.Category.UI,
                configManager.getInt("client.limits.ui-actions-per-second", 10), 1000L, now)) {
            droppedRateLimited.incrementAndGet();
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_TRACK_QUEST,
                    ClientProtocol.RESULT_RATE_LIMITED, "");
            return;
        }
        final QuestTrackPayload request;
        try {
            request = QuestTrackPayload.decode(envelope.payload());
        } catch (final ClientProtocolException malformed) {
            droppedMalformed.incrementAndGet();
            debug(() -> "malformed TRACK_QUEST from " + player.getName() + ": " + malformed.getMessage());
            return;
        }
        final QuestManager quests = questManager;
        if (quests == null) {
            sendActionResult(player, session, envelope.requestId(), ClientProtocol.MSG_TRACK_QUEST,
                    ClientProtocol.RESULT_SERVER_ERROR, "UNAVAILABLE");
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            final boolean accepted = quests.setTracked(player,
                    request.questId().isEmpty() ? null : request.questId());
            sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_ACTION_RESULT,
                    session.generation(), session.nextOutboundSequence(), envelope.requestId(),
                    new ActionResultPayload(ClientProtocol.MSG_TRACK_QUEST,
                            accepted ? ClientProtocol.RESULT_SUCCESS : ClientProtocol.RESULT_REJECTED,
                            accepted ? "" : "NOT_ACTIVE").encode()));
            if (questJournalActive(session)) {
                pushQuestNow(player, session);
            }
        }, null);
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

    /**
     * PARTY_STATE a vanilla HUD party-soraival azonos adatforrásból; player-szálon
     * hívandó. A fél-szív kvantálás miatt a bájt-dedupe regen alatt nem churn-öl.
     */
    private void pushPartyNow(final Player player, final ClientSession session) {
        final PartyManager parties = partyManager;
        if (parties == null) {
            return;
        }
        final byte[] payload = ClientPartyProjector.project(player, parties).encode();
        final byte[] previous = lastPartyState.put(player.getUniqueId(), payload);
        if (previous != null && Arrays.equals(previous, payload)) {
            return;
        }
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_PARTY_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, payload));
        debug(() -> "PARTY_STATE sent to " + player.getName() + " (" + payload.length + " bytes)");
    }

    public void connectParty(final PartyManager manager) {
        this.partyManager = manager;
    }

    /**
     * BOSS_STATE a vanilla világboss-bar adatkörével (+ név/archetípus/dühöngés, amit
     * a szerver szövegként eddig is broadcastolt). Minden mező lock-mentes volatile
     * tükörből jön, a HP egész százalékra kvantált — a dedupe sebzés-tickek alatt
     * nem churn-öl. A kör a vanilla barral egyezően globális.
     */
    private void pushBossNow(final Player player, final ClientSession session) {
        final WorldBossManager bosses = worldBossManager;
        if (bosses == null) {
            return;
        }
        final boolean active = bosses.isBossActive();
        final BossStatePayload payload = new BossStatePayload(active,
                active ? bosses.getActiveBossArchetypeId() : "",
                active ? bosses.getActiveBossName() : "",
                active ? Math.round(Math.max(0.0F, Math.min(1.0F, bosses.getBossHealthFraction())) * 100.0F) : 0,
                bosses.isBossEnraged());
        final byte[] wire = payload.encode();
        final byte[] previous = lastBossState.put(player.getUniqueId(), wire);
        if (previous != null && Arrays.equals(previous, wire)) {
            return;
        }
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_BOSS_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, wire));
        debug(() -> "BOSS_STATE sent to " + player.getName());
    }

    public void connectWorldBoss(final WorldBossManager manager) {
        this.worldBossManager = manager;
    }

    /**
     * TERRITORY_STATE a vanilla határátlépés-actionbar + /territory info adatkörével,
     * tartós overlay-ként; player-szálon hívandó. A zóna-lookup a lock-mentes
     * chunk-indexen fut (tick-enként olcsó), a raid-blokk csak az aktuális zónán futó
     * raidnél töltött — a pontok a capture-tick ütemében változnak, a dedupe nem churn-öl.
     */
    private void pushTerritoryNow(final Player player, final ClientSession session) {
        final TerritoryManager territories = territoryManager;
        if (territories == null) {
            return;
        }
        final hu.taliann.icesmp.data.Territory territory =
                territories.getTerritoryAt(player.getLocation());
        boolean raidActive = false;
        String raidAttackerLabel = "";
        String raidDefenderLabel = "";
        int raidAttackerPoints = 0;
        int raidDefenderPoints = 0;
        final RaidManager raids = raidManager;
        if (territory != null && raids != null) {
            final RaidManager.ActiveRaid raid = raids.getActiveRaid();
            if (raid != null && raids.isRaidActive() && territory.id().equals(raid.territoryId())) {
                raidActive = true;
                raidAttackerLabel = raid.attacker().getDisplayName();
                raidDefenderLabel = raid.defender().getDisplayName();
                raidAttackerPoints = raids.getPoints(raid.attacker());
                raidDefenderPoints = raids.getPoints(raid.defender());
            }
        }
        final TerritoryStatePayload payload = territory == null
                ? new TerritoryStatePayload(false, "", "", "", "", "",
                        false, "", "", 0, 0)
                : new TerritoryStatePayload(true, territory.id(), territory.name(),
                        territory.type().name(),
                        territory.faction() == null ? "" : territory.faction().name(),
                        territory.faction() == null ? "" : territory.faction().getDisplayName(),
                        raidActive, raidAttackerLabel, raidDefenderLabel,
                        raidAttackerPoints, raidDefenderPoints);
        final byte[] wire = payload.encode();
        final byte[] previous = lastTerritoryState.put(player.getUniqueId(), wire);
        if (previous != null && Arrays.equals(previous, wire)) {
            return;
        }
        sendNow(player, new MessageEnvelope(session.protocolVersion(), ClientProtocol.MSG_TERRITORY_STATE,
                session.generation(), session.nextOutboundSequence(), MessageEnvelope.NO_REQUEST, wire));
        debug(() -> "TERRITORY_STATE sent to " + player.getName());
    }

    public void connectTerritory(final TerritoryManager territories, final RaidManager raids) {
        this.territoryManager = territories;
        this.raidManager = raids;
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
        lastQuestSignature.remove(playerId);
        lastProfessionState.remove(playerId);
        lastRelicAttachment.remove(playerId);
        lastPartyState.remove(playerId);
        lastBossState.remove(playerId);
        lastTerritoryState.remove(playerId);
    }
}
