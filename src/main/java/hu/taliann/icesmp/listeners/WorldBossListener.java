package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import hu.taliann.icesmp.utils.MobKillUtil;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Pays out world boss kills: routes the slayer + boss into the WorldBossManager
 * (treasury reward, league points, slayer buff). Also refreshes the shared boss-bar
 * fraction whenever the boss is hit so players see their damage register.
 */
public final class WorldBossListener implements Listener {

    private final WorldBossManager worldBossManager;
    private final ConfigManager configManager;
    private final AfkManager afkManager;

    public WorldBossListener(final WorldBossManager worldBossManager,
                             final ConfigManager configManager,
                             final AfkManager afkManager) {
        this.worldBossManager = worldBossManager;
        this.configManager = configManager;
        this.afkManager = afkManager;
    }

    /** Keeps the shared HUD boss-bar in sync with the boss's health the moment it takes a hit. */
    @EventHandler(ignoreCancelled = true)
    public void onWorldBossDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity boss && worldBossManager.isWorldBoss(boss)) {
            worldBossManager.updateHealthBar(boss, event.getFinalDamage());
        }
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!worldBossManager.isWorldBoss(event.getEntity())) {
            return;
        }

        final Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        final boolean rewardsAllowed = !MobKillUtil.isAfkRewardBlocked(
                killer.getUniqueId(), configManager, afkManager);
        worldBossManager.handleBossDeath(event.getEntity(), killer, rewardsAllowed);
    }
}
