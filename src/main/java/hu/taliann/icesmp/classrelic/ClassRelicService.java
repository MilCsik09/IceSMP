package hu.taliann.icesmp.classrelic;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Class Relic Framework Paper/Folia homlokzata. A szabályok a pure resolverben élnek;
 * ez az osztály csak a három authority nézetét adaptálja: világ-relic (RelicManager),
 * Profile v2 class/spec (ClassSpecProfileGateway) és a fizikai birtoklás (inventory a
 * játékos régió-szálán, TTL-es cache-sel — idegen szálról sosem olvasunk inventoryt).
 * A katalógus reloadja atomikus: hibás config a régi pillanatképet hagyja élni.
 */
public final class ClassRelicService implements org.bukkit.event.Listener {

    /** A birtoklás-vizsgálat régió-szálhoz kötött; két szkennelés közt ennyi ideig hihető. */
    private static final long POSSESSION_TTL_MILLIS = 1_000L;

    private record Possession(String relicId, boolean present, long scannedAtMillis) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final RelicManager relicManager;
    private final ClassSpecProfileGateway gateway;
    private final ClassRelicActivationResolver resolver;
    private final Map<UUID, Possession> possessionCache = new ConcurrentHashMap<>();
    private final Map<String, ClassRelicResonanceHook> resonanceHooks = new ConcurrentHashMap<>();
    private volatile ClassRelicCatalog catalog = ClassRelicCatalog.empty();

    public ClassRelicService(final JavaPlugin plugin, final ConfigManager configManager,
                             final RelicManager relicManager,
                             final ClassSpecProfileGateway gateway) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.relicManager = Objects.requireNonNull(relicManager, "relicManager");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.resolver = new ClassRelicActivationResolver(ownershipView(), possessionView(),
                profileView());
    }

    // ---------- katalógus ----------

    /** Fail-fast reload: hibánál a korábbi (utoljára érvényes) katalógus marad publikálva. */
    public void reload() {
        try {
            final ConfigurationSection root = configManager.getConfiguration() == null ? null
                    : configManager.getConfiguration().getConfigurationSection("relics.class-relics");
            final boolean requireComplete = configManager.getBoolean(
                    "relics.require-complete-catalog", false);
            catalog = ClassRelicCatalogLoader.load(toMap(root), requireComplete);
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

    // ---------- szemantikus gameplay-esemény belépő (a class rework szerződése) ----------

    /**
     * A hívó a játékos entity-schedulerén fut (Folia-kontraktus). Csak aktív resonance-szal
     * rendelkező feloldás jut el a hookig; az inert hook a routing bizonyítéka gameplay nélkül.
     */
    public void onGameplayEvent(final Player player, final ClassGameplayEvent event,
                                final Set<AbilityTag> tags) {
        if (player == null || event == null) {
            return;
        }
        final ClassRelicActivation activation = resolver.resolveForClass(catalog,
                player.getUniqueId());
        if (!activation.resonanceActive() || activation.resolvedResonanceId().isEmpty()) {
            return;
        }
        resonanceHooks.getOrDefault(activation.resolvedResonanceId().orElseThrow(),
                        ClassRelicResonanceHook.INERT)
                .onGameplayEvent(activation, event, tags == null ? Set.of() : tags);
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
        ARMED
    }

    /**
     * Az Awakening keret-aktiválása: a durable cooldown a relickel utazik (RelicManager),
     * gazdacsere/restart nem nullázza. Gameplay-hatás itt nincs — a tényleges Awakening
     * a class rework része; a keret csak a jogosultságot és a cooldownt kezeli.
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
        final long now = System.currentTimeMillis();
        if (!AwakeningCooldownPolicy.ready(now,
                relicManager.getAwakeningReadyAt(activation.relicId()))) {
            return AwakeningResult.ON_COOLDOWN;
        }
        relicManager.setAwakeningReadyAt(activation.relicId(),
                AwakeningCooldownPolicy.nextReadyAt(now, binding.awakening().cooldownSeconds()));
        return AwakeningResult.ARMED;
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
        return playerId -> {
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
        };
    }

    /**
     * Inventory-nézet csak a játékos saját régió-szálán frissül; idegen szálról a TTL-es
     * cache utolsó értéke él (fail-safe: ismeretlen állapot = nincs birtoklás).
     */
    private ClassRelicActivationResolver.PossessionView possessionView() {
        return (playerId, relicId) -> {
            final Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return false;
            }
            final long now = System.currentTimeMillis();
            final Possession cached = possessionCache.get(playerId);
            if (Bukkit.isOwnedByCurrentRegion(player)
                    && (cached == null || !cached.relicId().equals(relicId)
                    || now - cached.scannedAtMillis() >= POSSESSION_TTL_MILLIS)) {
                final boolean present = scanInventory(player, relicId);
                possessionCache.put(playerId, new Possession(relicId, present, now));
                return present;
            }
            return cached != null && cached.relicId().equals(relicId) && cached.present();
        };
    }

    private boolean scanInventory(final Player player, final String relicId) {
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || !relicManager.isRelicItem(stack)) {
                continue;
            }
            final var definition = relicManager.identify(stack);
            if (definition != null && definition.id().equalsIgnoreCase(relicId)) {
                return true;
            }
        }
        return false;
    }

    // ---------- lifecycle ----------

    @org.bukkit.event.EventHandler
    public void onJoin(final org.bukkit.event.player.PlayerJoinEvent event) {
        // Első szkennelés a játékos saját schedulerén, hogy a cache ne üresen induljon.
        final Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                final ClassRelicActivation activation = resolve(player.getUniqueId());
                if (!activation.relicId().isEmpty()) {
                    possessionCache.put(player.getUniqueId(), new Possession(
                            activation.relicId(), scanInventory(player, activation.relicId()),
                            System.currentTimeMillis()));
                }
            }
        }, null, 20L);
    }

    @org.bukkit.event.EventHandler
    public void onQuit(final org.bukkit.event.player.PlayerQuitEvent event) {
        possessionCache.remove(event.getPlayer().getUniqueId());
    }
}
