package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import hu.taliann.icesmp.pve.EncounterRewardDeliveryService;
import hu.taliann.icesmp.utils.MobKillUtil;
import hu.taliann.icesmp.utils.PositionCache;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Records bounded boss contribution and settles every eligible participant through
 * the shared AFK/reward gate. Also refreshes the boss-bar fraction whenever the boss
 * is hit so players see their damage register.
 */
public final class WorldBossListener implements Listener {

    private final WorldBossManager worldBossManager;
    private final ConfigManager configManager;
    private final AfkManager afkManager;
    private final JavaPlugin plugin = JavaPlugin.getProvidingPlugin(WorldBossListener.class);

    public WorldBossListener(final WorldBossManager worldBossManager,
                             final ConfigManager configManager,
                             final AfkManager afkManager) {
        this.worldBossManager = worldBossManager;
        this.configManager = configManager;
        this.afkManager = afkManager;
    }

    /**
     * Snapshot creation is synchronous with spawn and the world-boss PDC is written afterwards.
     * Validate one owner tick later: an empty-radius fallback must never turn an unrelated online
     * player from another dimension (or outside the authored radius) into encounter power input.
     * Invalid snapshots fail closed by aborting the transient boss.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        // World bosses are authored CUSTOM spawns. Avoid scheduling validation work for natural mobs.
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        final LivingEntity entity = event.getEntity();
        entity.getScheduler().runDelayed(plugin, task -> validateParticipantSnapshot(entity), null, 1L);
    }

    private void validateParticipantSnapshot(final LivingEntity boss) {
        if (!boss.isValid() || !worldBossManager.isWorldBoss(boss)
                || !worldBossManager.isBossActive()) return;
        final var snapshot = worldBossManager.encounterSnapshot();
        if (snapshot == null || snapshot.participants().isEmpty()) {
            worldBossManager.shutdown();
            return;
        }
        final double radius = Math.max(16.0D, Math.min(512.0D, configManager.getDouble(
                "world-events.world-boss.scaling.participant-radius", 128.0D)));
        final double radiusSquared = radius * radius;
        final Location bossLocation = boss.getLocation();
        boolean hasValidAnchor = false;
        for (final java.util.UUID playerId : snapshot.participants()) {
            final Location cached = PositionCache.get(playerId);
            if (cached != null && cached.getWorld() == bossLocation.getWorld()
                    && cached.distanceSquared(bossLocation) <= radiusSquared) {
                hasValidAnchor = true;
                break;
            }
        }
        if (!hasValidAnchor) {
            plugin.getLogger().warning("World boss spawn aborted: encounter snapshot has no "
                    + "same-world participant inside the scaling radius.");
            worldBossManager.shutdown();
        }
    }

    /** Keeps the shared HUD boss-bar in sync with the boss's health the moment it takes a hit. */
    @EventHandler(ignoreCancelled = true)
    public void onWorldBossDamage(final EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity boss && worldBossManager.isWorldBoss(boss)) {
            worldBossManager.updateHealthBar(boss, event.getFinalDamage());
            final Player contributor = playerSource(event.getDamager());
            if (contributor != null) {
                worldBossManager.recordBossDamage(boss, contributor.getUniqueId(),
                        event.getFinalDamage());
            }
        }
        if (event.getDamager() instanceof LivingEntity boss
                && worldBossManager.isWorldBoss(boss)
                && event.getEntity() instanceof Player player) {
            worldBossManager.recordBossTanking(player.getUniqueId(), event.getFinalDamage());
        }
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!worldBossManager.isWorldBoss(event.getEntity())) {
            return;
        }

        worldBossManager.handleBossDeath(event.getEntity(), event.getEntity().getKiller(),
                playerId -> hu.taliann.icesmp.utils.GameModeCache.isKnown(playerId)
                        && hu.taliann.icesmp.utils.GameModeCache.isSurvival(playerId)
                        && !MobKillUtil.isAfkRewardBlocked(playerId, configManager, afkManager));
    }

    /**
     * Paper exposes every non-player removal cause, including plugin despawn, unload and discard.
     * Death has its own settlement path above; every other live world-boss removal is an abort and
     * must execute the same manager cleanup used by shutdown instead of leaving encounter state.
     */
    @EventHandler
    public void onEntityRemoved(final EntityRemoveEvent event) {
        if (event.getCause() == EntityRemoveEvent.Cause.DEATH
                || !(event.getEntity() instanceof LivingEntity boss)
                || !worldBossManager.isWorldBoss(boss)
                || !worldBossManager.isBossActive()) {
            return;
        }
        final var snapshot = worldBossManager.encounterSnapshot();
        if (snapshot != null) {
            EncounterRewardDeliveryService.abortPreparedEncounter(snapshot.encounterId());
        }
        worldBossManager.shutdown();
    }

    private static Player playerSource(final org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            final ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }
}
