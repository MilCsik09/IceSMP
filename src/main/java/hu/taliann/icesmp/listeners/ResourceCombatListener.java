package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ResourceManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

/**
 * Warrior combat/resource bridge. The event is owned by the damaged entity's region; the shooter
 * may live elsewhere, therefore the damager is reduced to UUID before any gameplay lookup.
 * ResourceManager's UUID-only methods touch concurrent transient state and the loaded Profile v2
 * cache only.
 */
public final class ResourceCombatListener implements Listener {

    private final ResourceManager resourceManager;

    public ResourceCombatListener(final ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    /**
     * HIGH is intentional: the listener changes damage and therefore must not pretend to be a
     * MONITOR observer. No entity from the attacker's region is dereferenced here.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity) || event.getDamage() <= 0.0D) {
            return;
        }

        final UUID damagerId = resolveDamagerId(event);
        final Player victim = event.getEntity() instanceof Player player ? player : null;
        final UUID victimId = victim == null ? null : victim.getUniqueId();
        final boolean selfHit = damagerId != null && damagerId.equals(victimId);
        final boolean pvp = damagerId != null && victimId != null && !selfHit;

        if (damagerId != null && !selfHit) {
            event.setDamage(resourceManager.modifyOutgoingDamage(
                    damagerId, event.getDamage(), pvp));
        }
        if (victimId != null) {
            event.setDamage(resourceManager.modifyIncomingDamage(
                    victimId, event.getDamage(), pvp));
        }

        if (event.getDamage() <= 0.0D) {
            return;
        }
        if (damagerId != null && !selfHit) {
            resourceManager.onDamageDealt(damagerId);
        }
        if (victimId != null) {
            resourceManager.onDamageTaken(victimId);
        }
    }

    private UUID resolveDamagerId(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player.getUniqueId();
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter.getUniqueId();
        }
        return null;
    }
}
