package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ClassHealthService;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * A HP-rendszer esemény-oldala: minden ténylegesen átment sebzés (kapott VAGY
 * adott) frissíti a harc-kontaktus időbélyeget — ettől számol a harcon kívüli
 * regen késleltetése. Belépéskor a kaszt-HP profil és a szív-kijelzés azonnal
 * felkerül (a join a játékos saját régió-szálán fut).
 */
public final class HealthRegenListener implements Listener {

    private final ClassHealthService classHealthService;

    public HealthRegenListener(final ClassHealthService classHealthService) {
        this.classHealthService = classHealthService;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        classHealthService.apply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim) {
            classHealthService.recordCombat(victim.getUniqueId());
        }
    }

    /**
     * A kaszt-sebzés-profil a lövedékekre is jár: a nyíl/szigony sebzését az
     * ATTACK_DAMAGE attribútum nem érinti, ezért a bónusz itt adódik hozzá — a
     * cache-elt értékből (a lövő PDC-je a találat szálán nem érinthető).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRangedBonus(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.AbstractArrow projectile)
                || !(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        final double bonus = classHealthService.cachedDamageBonus(shooter.getUniqueId());
        if (bonus > 0.0D) {
            event.setDamage(event.getDamage() + bonus);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDealtDamage(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            classHealthService.recordCombat(attacker.getUniqueId());
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            classHealthService.recordCombat(shooter.getUniqueId());
        }
    }
}
