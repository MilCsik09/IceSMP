package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.CombatTagManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * PvP-találatra mindkét fél combat-taget kap. MONITOR prioritás: csak a
 * ténylegesen átment (nem cancel-elt) sebzés jelöl — a zóna-védelem által
 * elnyelt ütés nem.
 */
public final class CombatTagListener implements Listener {

    private final CombatTagManager combatTagManager;

    public CombatTagListener(final CombatTagManager combatTagManager) {
        this.combatTagManager = combatTagManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        final Player attacker;
        if (event.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        } else {
            return;
        }
        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        combatTagManager.tagBoth(victim, attacker);
    }
}
