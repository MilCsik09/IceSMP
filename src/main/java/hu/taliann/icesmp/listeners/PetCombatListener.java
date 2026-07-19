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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * Feeds the companion a combat target so it fights like a real pet regardless of
 * its native AI: when the owner strikes a mob the pet assists, and when the owner
 * is struck the pet defends.
 *
 * <p>Folia: the damage event runs on the VICTIM's region thread. Each side of the
 * check runs on its own entity's thread — the target-side filter
 * ({@link PetManager#isEligibleCombatTarget}) where that entity is local, the
 * owner-side gate ({@link PetManager#canReceiveCombatTarget}, PDC read) on the
 * owner's scheduler — and the final registration is a concurrent-map write.</p>
 */
public final class PetCombatListener implements Listener {

    private final JavaPlugin plugin;
    private final PetManager petManager;

    public PetCombatListener(final JavaPlugin plugin, final PetManager petManager) {
        this.plugin = plugin;
        this.petManager = petManager;
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        petManager.handlePetDeath(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        final Entity victim = event.getEntity();

        // Owner attacks a creature → the pet assists on that creature. The victim is local:
        // validate the target side here, then hop to the attacker for the PDC-backed owner gate.
        final Player attacker = resolvePlayer(event.getDamager());
        if (attacker != null && victim instanceof LivingEntity living
                && petManager.isEligibleCombatTarget(attacker.getUniqueId(), living)) {
            final UUID targetId = living.getUniqueId();
            attacker.getScheduler().run(plugin, task -> {
                if (petManager.canReceiveCombatTarget(attacker)) {
                    petManager.putCombatTarget(attacker.getUniqueId(), targetId);
                }
            }, null);
        }

        // Owner is attacked → the pet retaliates. The defender IS the event entity (local), so
        // the owner gate is safe here; the foe is foreign — validate it on its own scheduler.
        if (victim instanceof Player defender && petManager.canReceiveCombatTarget(defender)) {
            final LivingEntity foe = resolveAttacker(event.getDamager());
            if (foe != null) {
                final UUID defenderId = defender.getUniqueId();
                foe.getScheduler().run(plugin, task -> {
                    if (petManager.isEligibleCombatTarget(defenderId, foe)) {
                        petManager.putCombatTarget(defenderId, foe.getUniqueId());
                    }
                }, null);
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
