package hu.taliann.icesmp.classrelic;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import hu.taliann.icesmp.relics.RelicWorldStateStore;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Class Relic Framework Paper/Folia homlokzata. A szabályok a pure resolverben élnek;
 * ez az osztály csak a négy nézetet adaptálja: framework-kapu (relics.enabled, use-site
 * élő-config), világ-relic (RelicManager), Profile v2 class/spec (ClassSpecProfileGateway)
 * és a fizikai birtoklás pillanatkép-cache.
 *
 * Birtoklás-konzisztencia: a pillanatkép KIZÁRÓLAG a játékos saját régió-szálán készül
 * (join-kori első szken + másodpercenkénti frissítés + kritikus invalidáció a világ-relic
 * mutációkról és halálról). Az UUID-only olvasók (pl. ResourceManager hot path) sosem
 * dereferálnak Player-t: friss pillanatkép nélkül az eredmény fail-closed "nincs
 * birtoklás". A maximális konzisztencia-ablak a TTL (lásd POSSESSION_TTL_MILLIS);
 * lost/transfer/reclaim explicit azonnali invalidációt kap.
 */
public final class ClassRelicService implements org.bukkit.event.Listener {

    /** Frissítési periódus a játékos saját schedulerén (1 mp). */
    private static final long POSSESSION_REFRESH_TICKS = 20L;
    /**
     * A pillanatkép elévülése: a periodikus frissítés kimaradását (régió-torlódás,
     * scheduler-jitter) fedi le; ennél idősebb "true" nem hihető el (fail-closed).
     */
    private static final long POSSESSION_TTL_MILLIS = 2_500L;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final RelicManager relicManager;
    private final ClassSpecProfileGateway gateway;
    private final ClassRelicActivationResolver resolver;
    private final Map<UUID, PossessionSnapshot> possessionCache = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> refreshTasks = new ConcurrentHashMap<>();
    private final Map<String, ClassRelicResonanceHook> resonanceHooks = new ConcurrentHashMap<>();
    private volatile ClassRelicCatalog catalog = ClassRelicCatalog.empty();

    public ClassRelicService(final JavaPlugin plugin, final ConfigManager configManager,
                             final RelicManager relicManager,
                             final ClassSpecProfileGateway gateway) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.relicManager = Objects.requireNonNull(relicManager, "relicManager");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.resolver = new ClassRelicActivationResolver(this::frameworkEnabled, ownershipView(),
                possessionView(), profileView());
        relicManager.setWorldStateListener(this::invalidatePossession);
    }

    /** Use-site élő-config: reload/override után azonnal konzisztens. */
    private boolean frameworkEnabled() {
        return configManager.getBoolean("relics.enabled", true);
    }

    // ---------- katalógus ----------

    /**
     * Fail-fast reload: hibánál a korábbi (utoljára érvényes) katalógus marad publikálva.
     * A candidate a publish ELŐTT a generikus relic-registryvel is kereszt-validált —
     * nem létező fizikai relicre mutató kötés a TELJES candidate-et elutasítja (a
     * require-complete-catalog kapu így nem PASS-olhat kitalált relic-rosterrel).
     */
    public void reload() {
        try {
            final ConfigurationSection root = configManager.getConfiguration() == null ? null
                    : configManager.getConfiguration().getConfigurationSection("relics.class-relics");
            final boolean requireComplete = configManager.getBoolean(
                    "relics.require-complete-catalog", false);
            final ClassRelicCatalog candidate =
                    ClassRelicCatalogLoader.load(toMap(root), requireComplete);
            if (relicManager.isEnabled()) {
                candidate.requireKnownRelics(relicId ->
                        relicManager.getDefinition(relicId) != null);
            }
            catalog = candidate;
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager,
                    "Loaded " + catalog.size() + " class-relic binding(s).");
        } catch (final RuntimeException invalid) {
            plugin.getLogger().severe("Class-relic catalog reload rejected (previous catalog stays): "
                    + invalid.getMessage());
        }
    }

    public ClassRelicCatalog catalog() {
        return catalog;
    }

    private static Map<String, Object> toMap(final ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        final LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (final String key : section.getKeys(false)) {
            final Object value = section.get(key);
            result.put(key, value instanceof ConfigurationSection nested ? toMap(nested) : value);
        }
        return result;
    }

    // ---------- feloldás és modifier API ----------

    /** A játékos kasztjához kötött Class Relic feloldása (diagnosztika/GUI/jövőbeli rétegek). */
    public ClassRelicActivation resolve(final UUID playerId) {
        return resolver.resolveForClass(catalog, playerId);
    }

    /**
     * Class Power modifier-szorzó (1.0 = nincs bónusz). A fogyasztók csatornán kérdeznek
     * (pl. CLASS_RESOURCE_MAX), relic-id-t és kaszt-vizsgálatot soha nem hordoznak.
     * UUID-only read path: minden nézete pillanatkép/cache-alapú, Player-dereferencia
     * nélkül — idegen régió-szálról is biztonságosan hívható.
     */
    public double modifier(final UUID playerId, final RelicModifier modifier) {
        if (playerId == null || modifier == null) {
            return 1.0D;
        }
        final ClassRelicActivation activation = resolver.resolveForClass(catalog, playerId);
        if (!activation.basePowerActive()) {
            return 1.0D;
        }
        final ClassRelicBinding binding = catalog.byRelic(activation.relicId()).orElse(null);
        if (binding == null) {
            return 1.0D;
        }
        final Double percent = binding.basePower().percentByModifier().get(modifier);
        return percent == null ? 1.0D : 1.0D + Math.max(0.0D, percent) / 100.0D;
    }

    // ---------- szemantikus gameplay-jelzés belépő (a class rework szerződése) ----------

    /**
     * Tipizált Resonance-dispatch. Folia-szerződés KIKÉNYSZERÍTVE: ha a hívó nem az actor
     * régió-szálán fut, a dispatch maga hoppol az actor schedulerére — rossz szálról nem
     * lehet hamisan biztonságos hívást tenni. A hook a régió-helyes actor referenciát a
     * kontextusban kapja; a signal cél-oldala csak identitás (UUID), idegen entity
     * érintése a hookban is csak a cél schedulerére hoppolva megengedett.
     */
    public void onGameplaySignal(final Player actor, final ClassGameplaySignal signal) {
        if (actor == null || signal == null || !frameworkEnabled()) {
            return;
        }
        if (!actor.getUniqueId().equals(signal.actorId())) {
            plugin.getLogger().warning("Class gameplay signal dropped: actor mismatch ("
                    + signal.actorId() + " != " + actor.getUniqueId() + ")");
            return;
        }
        if (!Bukkit.isOwnedByCurrentRegion(actor)) {
            actor.getScheduler().run(plugin, task -> dispatchSignal(actor, signal), null);
            return;
        }
        dispatchSignal(actor, signal);
    }

    private void dispatchSignal(final Player actor, final ClassGameplaySignal signal) {
        final ClassRelicActivation activation = resolver.resolveForClass(catalog,
                actor.getUniqueId());
        if (!activation.resonanceActive() || activation.resolvedResonanceId().isEmpty()) {
            return;
        }
        resonanceHooks.getOrDefault(activation.resolvedResonanceId().orElseThrow(),
                        ClassRelicResonanceHook.INERT)
                .onSignal(new ClassRelicResonanceContext(actor, activation, signal));
    }

    /** A class rework ide regisztrálja a valódi resonance-implementációkat. */
    public void registerResonanceHook(final String resonanceId, final ClassRelicResonanceHook hook) {
        if (resonanceId != null && !resonanceId.isBlank() && hook != null) {
            resonanceHooks.put(resonanceId.toLowerCase(java.util.Locale.ROOT), hook);
        }
    }

    // ---------- Awakening (durable cooldown authority a relic-oldalon) ----------

    public enum AwakeningResult {
        NOT_AVAILABLE,
        DISABLED,
        ON_COOLDOWN,
        PERSISTENCE_FAILED,
        ARMED
    }

    /**
     * Az Awakening keret-aktiválása. A ready-at ellenőrzés + az új érték durable commitja
     * a világ-relic store EGY szerializált műveletében történik (RelicManager.tryArmAwakening):
     * két konkurens hívásból pontosan egy ARMED, sikertelen lemez-írásnál az eredmény
     * PERSISTENCE_FAILED és az állapot változatlan — ARMED siker csak megtörtént commit után.
     */
    public AwakeningResult tryArmAwakening(final Player player) {
        if (player == null) {
            return AwakeningResult.NOT_AVAILABLE;
        }
        final ClassRelicActivation activation = resolver.resolveForClass(catalog,
                player.getUniqueId());
        if (!activation.basePowerActive()) {
            return AwakeningResult.NOT_AVAILABLE;
        }
        final ClassRelicBinding binding = catalog.byRelic(activation.relicId()).orElse(null);
        if (binding == null || !binding.awakening().enabled()) {
            return AwakeningResult.DISABLED;
        }
        return switch (relicManager.tryArmAwakening(activation.relicId(),
                System.currentTimeMillis(), binding.awakening().cooldownSeconds())) {
            case ARMED -> AwakeningResult.ARMED;
            case ON_COOLDOWN -> AwakeningResult.ON_COOLDOWN;
            case PERSISTENCE_FAILED -> AwakeningResult.PERSISTENCE_FAILED;
        };
    }

    // ---------- authority-nézetek ----------

    private ClassRelicActivationResolver.OwnershipView ownershipView() {
        return new ClassRelicActivationResolver.OwnershipView() {
            @Override
            public Optional<UUID> ownerOf(final String relicId) {
                final var ownership = relicManager.getOwnership(relicId);
                return ownership == null ? Optional.empty() : Optional.of(ownership.owner());
            }

            @Override
            public boolean isLost(final String relicId) {
                return relicManager.isLost(relicId);
            }
        };
    }

    private ClassRelicActivationResolver.ProfileView profileView() {
        return this::profileFacts;
    }

    private Optional<ClassRelicActivationResolver.ProfileFacts> profileFacts(final UUID playerId) {
        if (!gateway.isSessionReady(playerId)) {
            return Optional.empty();
        }
        final Optional<ClassSpecSection> profile = gateway.currentProfile(playerId);
        if (profile.isEmpty() || !profile.orElseThrow().isGameplayUsable()) {
            return Optional.empty();
        }
        final ClassSpecSection section = profile.orElseThrow();
        final var slot = section.activeSlot();
        final var loadout = slot == null ? null : section.loadout(slot);
        return Optional.of(new ClassRelicActivationResolver.ProfileFacts(
                section.primaryClassId(),
                loadout == null ? "" : loadout.specializationId(),
                loadout == null ? LoadoutStatus.EMPTY : loadout.status()));
    }

    /**
     * UUID-only birtoklás-nézet: KIZÁRÓLAG a régió-szálon készült pillanatképből olvas,
     * Player-dereferencia nélkül. Ismeretlen vagy TTL-en túli pillanatkép = fail-closed
     * "nincs birtoklás" — korlátlan ideig élő stale "true" nem létezhet.
     */
    private ClassRelicActivationResolver.PossessionView possessionView() {
        return (playerId, relicId) -> {
            final PossessionSnapshot snapshot = possessionCache.get(playerId);
            return snapshot != null && snapshot.usableFor(relicId,
                    System.currentTimeMillis(), POSSESSION_TTL_MILLIS);
        };
    }

    /** Kritikus világ-relic mutáció (transfer/lost/reclaim/give/expiry) → azonnali invalidáció. */
    public void invalidatePossession(final String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return;
        }
        final String normalized = relicId.toLowerCase(java.util.Locale.ROOT);
        possessionCache.entrySet().removeIf(entry -> entry.getValue().relicId().equals(normalized));
    }

    /** A pillanatkép frissítése — CSAK a játékos saját régió-szálán hívható. */
    private void refreshPossession(final Player player) {
        if (!player.isOnline()) {
            return;
        }
        final Optional<ClassRelicActivationResolver.ProfileFacts> facts =
                profileFacts(player.getUniqueId());
        final Optional<ClassRelicBinding> binding = facts.isEmpty() ? Optional.empty()
                : catalog.byClass(facts.orElseThrow().primaryClassId());
        if (binding.isEmpty()) {
            possessionCache.remove(player.getUniqueId());
            return;
        }
        final String relicId = binding.orElseThrow().relicId();
        possessionCache.put(player.getUniqueId(), new PossessionSnapshot(relicId,
                scanUsableInventory(player, relicId), System.currentTimeMillis()));
    }

    /**
     * "Usable physical relic": nem elég az azonos relic-id — a kanonikus
     * RelicManager.canUse validáció is kötelező (item-owner PDC + központi ownership),
     * így stale/duplikált/rossz gazdához kötött példány nem ad Class Powert.
     */
    private boolean scanUsableInventory(final Player player, final String relicId) {
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || !relicManager.isRelicItem(stack)) {
                continue;
            }
            final var definition = relicManager.identify(stack);
            if (definition != null && definition.id().equalsIgnoreCase(relicId)
                    && relicManager.canUse(player, stack)) {
                return true;
            }
        }
        return false;
    }

    // ---------- lifecycle ----------

    @org.bukkit.event.EventHandler
    public void onJoin(final org.bukkit.event.player.PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();
        // Periodikus pillanatkép-frissítés a játékos saját schedulerén; a task a TTL-nél
        // sűrűbben fut, így friss cache-t tart, amíg a játékos online.
        final ScheduledTask task = player.getScheduler().runAtFixedRate(plugin,
                scheduled -> refreshPossession(player), null, 1L, POSSESSION_REFRESH_TICKS);
        if (task != null) {
            final ScheduledTask previous = refreshTasks.put(playerId, task);
            if (previous != null) {
                previous.cancel();
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onDeath(final org.bukkit.event.entity.PlayerDeathEvent event) {
        // Halálkor a tárgyak kieshetnek/megsemmisülhetnek — a pillanatkép azonnal hiteltelen.
        possessionCache.remove(event.getEntity().getUniqueId());
    }

    @org.bukkit.event.EventHandler
    public void onQuit(final org.bukkit.event.player.PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        possessionCache.remove(playerId);
        final ScheduledTask task = refreshTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdown() {
        refreshTasks.values().forEach(ScheduledTask::cancel);
        refreshTasks.clear();
        possessionCache.clear();
    }
}
