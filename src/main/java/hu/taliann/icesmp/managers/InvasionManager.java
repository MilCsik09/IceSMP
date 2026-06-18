package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Invasion events (ROADMAP phase 9): periodically (or on admin command) a wave
 * of scaled monsters spawns around a random player — a dangerous swarm that
 * rewards via the normal scaled-mob XP and soulstone drops. The spawn runs on
 * the target region's thread (Folia-safe).
 */
public final class InvasionManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final MessageManager messageManager;

    private volatile long nextAttemptAt;

    public InvasionManager(final JavaPlugin plugin, final ConfigManager configManager,
                           final MobScalingManager mobScalingManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.messageManager = messageManager;
    }

    /** Periodic attempt on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("world-events.invasion.enabled", true)) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now < nextAttemptAt) {
            return;
        }
        final long intervalMinutes = Math.max(1L, configManager.getLong("world-events.invasion.check-interval-minutes", 75L));
        nextAttemptAt = now + (intervalMinutes * 60_000L);

        final double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("world-events.invasion.chance-percent", 30.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (!online.isEmpty()) {
            triggerNear(online.get(ThreadLocalRandom.current().nextInt(online.size())));
        }
    }

    /**
     * Admin override: launches an invasion near the given anchor (or a random
     * online player if null).
     *
     * @param anchor preferred anchor (issuing admin), may be null
     * @return true if an invasion was launched
     */
    public boolean forceStart(final Player anchor) {
        Player target = anchor;
        if (target == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }
        triggerNear(target);
        return true;
    }

    private void triggerNear(final Player anchor) {
        // Folia: read the anchor's location on its OWN region thread first, then hop to that
        // location's region to spawn (the caller may run on the global or another region thread).
        anchor.getScheduler().run(plugin, task -> {
            final Location center = anchor.getLocation().clone();
            plugin.getServer().getRegionScheduler().run(plugin, center, spawnTask -> spawnWave(center));
        }, null);
    }

    private void spawnWave(final Location center) {
        if (center.getWorld() == null) {
            return;
        }

        final EntityType type = resolveType();
        final Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
            plugin.getLogger().warning("Configured invasion entity-type is not a mob; skipping.");
            return;
        }

        final int count = Math.max(1, configManager.getInt("world-events.invasion.mob-count", 8));
        final int level = Math.max(1, configManager.getInt("world-events.invasion.mob-level", 4));
        final double radius = Math.max(2.0D, configManager.getDouble("world-events.invasion.radius", 8.0D));

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            final double angle = (Math.PI * 2.0D / count) * i;
            final int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            final int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            final int y = center.getWorld().getHighestBlockYAt(x, z) + 1;
            final Location spot = new Location(center.getWorld(), x + 0.5D, y, z + 0.5D);

            final Mob mob = (Mob) center.getWorld().spawn(spot, entityClass.asSubclass(Mob.class));
            mob.setGlowing(true);
            mob.setRemoveWhenFarAway(false);
            mobScalingManager.forceLevel(mob, level);
            spawned++;
        }

        if (spawned > 0) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "invasion-started",
                    "<dark_red>⚔ INVÁZIÓ! Egy szörnyhorda tört be a vidékre — vigyázz!</dark_red>"
            ));
        }
    }

    private EntityType resolveType() {
        try {
            return EntityType.valueOf(configManager.getString("world-events.invasion.entity-type", "ZOMBIE")
                    .toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return EntityType.ZOMBIE;
        }
    }
}
