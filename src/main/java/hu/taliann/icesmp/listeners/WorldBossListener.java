package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.WorldBossManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Pays out world boss kills: routes the slayer + boss into the WorldBossManager
 * (treasury reward, league points, slayer buff).
 */
public final class WorldBossListener implements Listener {

    private final WorldBossManager worldBossManager;

    public WorldBossListener(final WorldBossManager worldBossManager) {
        this.worldBossManager = worldBossManager;
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

        worldBossManager.handleBossDeath(event.getEntity(), killer);
    }
}
