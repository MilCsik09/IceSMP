package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ConfigManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Non-persistent Season 0 content ceiling over the existing live configuration tree.
 * Nothing is written to disk; a normal config reload restores the configured Season 1 values.
 */
public final class PrologueRuntimeConfigOverlay {
    private static final List<String> DEFAULT_DISABLED_CONTENT = List.of(
            "world-events.world-boss.enabled", "wild-hunt.enabled", "treasure-events.enabled");
    private static volatile PrologueRuntimeConfigOverlay active;

    private final ConfigManager config;
    private ScheduledTask task;
    private boolean applied;

    private PrologueRuntimeConfigOverlay(final JavaPlugin plugin) {
        this.config = ConfigManager.current();
        if (config == null) throw new IllegalStateException("ConfigManager not installed");
        this.task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin,
                ignored -> reconcile(), 1L, 20L);
    }

    public static synchronized PrologueRuntimeConfigOverlay install(final JavaPlugin plugin) {
        if (active == null) active = new PrologueRuntimeConfigOverlay(plugin);
        return active;
    }

    public static synchronized void shutdown() {
        final PrologueRuntimeConfigOverlay overlay = active;
        active = null;
        if (overlay != null && overlay.task != null) overlay.task.cancel();
    }

    public void reconcile() {
        final PrologueManager manager = PrologueManager.current();
        if (manager == null || !PrologueContentPolicy.enabled(config)) return;
        if (manager.state().completed()) {
            if (applied) {
                config.reload();
                applied = false;
            }
            return;
        }
        final FileConfiguration effective = config.getConfiguration();
        if (effective == null) return;
        if (!PrologueContentPolicy.specializationAvailable(config)) {
            effective.set("classes.specialization.required-level", 51);
            effective.set("classes.specialization.second-slot-level", 51);
        }
        if (!PrologueContentPolicy.blueprintDropsAvailable(config)) {
            effective.set("loot.blueprint-drop.chance", 0.0D);
            effective.set("loot.blueprint-drop.boss-chance", 0.0D);
        }
        effective.set("loot.boss-drop.chance", 0.0D);
        applyRarityCeiling(effective);
        List<String> disabled = config.getStringList(
                "world-events.prologue.progression.disabled-content-paths");
        if (disabled.isEmpty()) disabled = DEFAULT_DISABLED_CONTENT;
        for (final String path : disabled) {
            if (path != null && !path.isBlank()) effective.set(path.trim(), false);
        }

        // Prologue encounters intentionally happen in the visible, builder-curated Doom Gate arena;
        // they still use surface/chunk/water safety but not wilderness-distance/territory avoidance.
        effective.set("world-events.spawn-rules.prologue.territory", false);
        effective.set("world-events.spawn-rules.prologue.claim", false);
        effective.set("world-events.spawn-rules.prologue.region", false);
        effective.set("world-events.spawn-rules.prologue.water", true);
        effective.set("world-events.profiles.prologue.min-horizontal-distance-blocks", 0.0D);
        effective.set("world-events.profiles.prologue.use-dynamic-view-distance", false);
        effective.set("world-events.profiles.prologue.ignore-recent-locations", true);
        effective.set("world-events.profiles.prologue.footprint-radius-blocks", 1);
        effective.set("world-events.profiles.prologue.max-height-delta-blocks", 3);
        effective.set("world-events.profiles.prologue.visibility-cone.enabled", false);

        effective.set("world-events.anchors.prologue-gate.mode", "points");
        effective.set("world-events.anchors.prologue-gathering.mode", "points");
        effective.set("world-events.anchors.prologue-breach.mode", "points");
        effective.set("world-events.anchors.prologue-boss.mode", "points");
        final List<String> majors = new ArrayList<>(config.getStringList(
                "world-events.orchestration.major-events"));
        if (!majors.contains("prologue")) majors.add("prologue");
        effective.set("world-events.orchestration.major-events", majors);
        applied = true;
    }

    private void applyRarityCeiling(final FileConfiguration effective) {
        final ConfigurationSection tiers = effective.getConfigurationSection("item-rarity.tiers");
        if (tiers == null) return;
        for (final String tierId : tiers.getKeys(false)) {
            final ConfigurationSection weights = tiers.getConfigurationSection(tierId + ".weights");
            if (weights == null) continue;
            for (final String rarityId : List.copyOf(weights.getKeys(false))) {
                if (!PrologueContentPolicy.rarityAvailable(config,
                        rarityId.toLowerCase(Locale.ROOT))) weights.set(rarityId, 0);
            }
        }
    }
}
