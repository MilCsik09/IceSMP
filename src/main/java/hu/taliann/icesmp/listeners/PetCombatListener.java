package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.PetManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Feeds the companion a combat target so it fights like a real pet regardless of
 * its native AI: when the owner strikes a mob the pet assists, and when the owner
 * is struck the pet defends. {@link PetManager#setCombatTarget} filters out the
 * owner and the owner's own minions, so a pet never turns on its allies.
 */
public final class PetCombatListener implements Listener {

    private final PetManager petManager;

    public PetCombatListener(final PetManager petManager) {
        this.petManager = petManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        // If a companion dies, clear its combat state and notify its owner.
        petManager.handlePetDeath(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        final Entity victim = event.getEntity();

        // Owner attacks a creature → the pet assists on that creature.
        final Player attacker = resolvePlayer(event.getDamager());
        if (attacker != null && victim instanceof LivingEntity living) {
            petManager.setCombatTarget(attacker, living);
        }

        // Owner is attacked → the pet retaliates against the attacker.
        if (victim instanceof Player defender) {
            final LivingEntity foe = resolveAttacker(event.getDamager());
            if (foe != null) {
                petManager.setCombatTarget(defender, foe);
            }
        }
    }

    /** The player behind a hit (direct melee or a fired projectile), else null. */
    private Player resolvePlayer(final Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    /** The living attacker behind a hit (direct or projectile source), else null. */
    private LivingEntity resolveAttacker(final Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile) {
            final ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
