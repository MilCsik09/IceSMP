package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.BlockRegenService;
import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Applies the live, admin-tunable debris flight profile to BlockRegeneration falling blocks.
 *
 * <p>The spawn event fires inside {@code World#spawnFallingBlock}, before
 * {@link BlockRegenService#spawnDebris} adds its scoreboard tag and initial velocity. The entity
 * scheduler therefore performs the adjustment immediately after the current spawn stack has
 * completed. Default multipliers preserve the original trajectory exactly.
 */
public final class BlockRegenDebrisMotionListener implements Listener {

    private static final String BASE = "territory.protection.regen.";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public BlockRegenDebrisMotionListener(final JavaPlugin plugin,
                                          final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(final EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof FallingBlock debris)) {
            return;
        }
        debris.getScheduler().run(plugin, task -> tune(debris), null);
    }

    private void tune(final FallingBlock debris) {
        if (!debris.isValid()
                || !debris.getScoreboardTags().contains(BlockRegenService.DEBRIS_TAG)) {
            return;
        }

        final double horizontalMultiplier = bounded(
                BASE + "debris-horizontal-multiplier", 1.0D, 0.0D, 5.0D);
        final double verticalMultiplier = bounded(
                BASE + "debris-vertical-multiplier", 1.0D, 0.0D, 5.0D);
        final double horizontalSpread = bounded(
                BASE + "debris-horizontal-spread", 0.0D, 0.0D, 3.0D);
        final double extraUpwardVelocity = bounded(
                BASE + "debris-extra-upward-velocity", 0.0D, 0.0D, 3.0D);

        final Vector current = debris.getVelocity();
        final double angle = Math.random() * Math.PI * 2.0D;
        final double spreadRadius = Math.sqrt(Math.random()) * horizontalSpread;
        final double spreadX = Math.cos(angle) * spreadRadius;
        final double spreadZ = Math.sin(angle) * spreadRadius;

        debris.setGravity(configManager.getBoolean(BASE + "debris-gravity-enabled", true));
        debris.setVelocity(new Vector(
                current.getX() * horizontalMultiplier + spreadX,
                current.getY() * verticalMultiplier + extraUpwardVelocity,
                current.getZ() * horizontalMultiplier + spreadZ));
    }

    private double bounded(final String key, final double fallback,
                           final double minimum, final double maximum) {
        final double configured = configManager.getDouble(key, fallback);
        if (!Double.isFinite(configured)) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, configured));
    }
}
