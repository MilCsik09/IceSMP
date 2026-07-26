package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.WildHuntManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

/** Wild Hunt combat bridge; only immutable player ids cross the beast region boundary. */
public final class WildHuntListener implements Listener {

    private final WildHuntManager wildHuntManager;

    public WildHuntListener(final WildHuntManager wildHuntManager) {
        this.wildHuntManager = wildHuntManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(final EntityDeathEvent event) {
        final LivingEntity dead = event.getEntity();
        if (!wildHuntManager.isWildHunt(dead.getUniqueId())) {
            return;
        }
        final var killer = dead.getKiller();
        final UUID killerId = killer == null ? null : killer.getUniqueId();
        wildHuntManager.onSlain(killerId, dead.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!wildHuntManager.isWildHunt(event.getEntity().getUniqueId())) {
            return;
        }
        if (event.getDamager() instanceof org.bukkit.entity.Player player) {
            wildHuntManager.recordDamager(player.getUniqueId());
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof org.bukkit.entity.Player shooter) {
            wildHuntManager.recordDamager(shooter.getUniqueId());
        }
    }
}
