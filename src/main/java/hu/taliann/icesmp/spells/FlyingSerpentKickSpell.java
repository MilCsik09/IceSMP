package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Repulo Kigyorugas" — the original dash (forward + up) and short speed buff, plus a
 * ~1.5s post-dash collision window: the first living entity to come within 1.5 blocks of
 * the caster during that window takes bonus damage and is knocked outward, then detection
 * stops. Detection is a short-lived, non-persistent tick loop (no cross-cast state), so it
 * does not need a {@link #clearPlayerState} entry — the entity scheduler retires it on logout.
 */
public final class FlyingSerpentKickSpell extends BaseSpell {

    private final JavaPlugin plugin;

    public FlyingSerpentKickSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "flying_serpent_kick", "Repülő Kígyórúgás", 35, SpellCostType.HUNGER, 3);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        final double dashForward = balance("dash-forward", 2.0D);
        final double dashUp = balance("dash-up", 0.4D);
        final int speedDurationTicks = balanceInt("speed-duration-ticks", 3 * 20);

        Vector velocity = player.getLocation().getDirection().setY(0.0D);
        velocity = velocity.lengthSquared() > 1.0E-4D ? velocity.normalize().multiply(dashForward) : new Vector();
        velocity.setY(dashUp);
        player.setVelocity(velocity);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedDurationTicks, 0, false, true, true));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.3D, 0.3D, 0.3D, 0.02D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0F, 1.2F);

        startCollisionDetection(player);
    }

    private void startCollisionDetection(final Player player) {
        final UUID playerId = player.getUniqueId();
        final double hitRadius = balance("hit-radius", 1.5D);
        final double impactDamage = balance("impact-damage", 5.0D);
        final double impactKnockback = balance("impact-knockback", 2.0D);
        final int durationTicks = balanceInt("detection-duration-ticks", 30); // ~1.5 mp

        final AtomicInteger elapsed = new AtomicInteger(0);
        player.getScheduler().runAtFixedRate(plugin, scheduled -> {
            final Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isValid() || online.isDead() || elapsed.incrementAndGet() > durationTicks) {
                scheduled.cancel();
                return;
            }

            final Location center = online.getLocation();
            for (final Entity nearby : online.getWorld().getNearbyEntities(center, hitRadius, hitRadius, hitRadius)) {
                if (!(nearby instanceof LivingEntity living) || living == online || SpellTargetingUtil.isAlly(online, living)) {
                    continue;
                }
                impact(living, online, center, impactDamage, impactKnockback);
                scheduled.cancel();
                return;
            }
        }, null, 1L, 1L);
    }

    /** Folia: the tick that finds the target may be on a different region than the dash — hop if needed. */
    private void impact(final LivingEntity living, final Player caster, final Location casterLocation,
                         final double damage, final double knockback) {
        if (Bukkit.isOwnedByCurrentRegion(living)) {
            applyImpact(living, caster, casterLocation, damage, knockback);
        } else {
            living.getScheduler().run(plugin, task -> applyImpact(living, caster, casterLocation, damage, knockback), null);
        }
    }

    private static void applyImpact(final LivingEntity living, final Player caster, final Location casterLocation,
                                     final double damage, final double knockback) {
        if (Bukkit.isOwnedByCurrentRegion(caster)) {
            hu.taliann.icesmp.utils.SpellDamageUtil.damageBySpell(caster, living, damage, getId());
        } else {
            living.damage(damage);
        }
        final Vector away = living.getLocation().toVector().subtract(casterLocation.toVector()).setY(0.0D);
        if (away.lengthSquared() > 1.0E-4D) {
            living.setVelocity(away.normalize().multiply(knockback).setY(0.35D));
        } else {
            living.setVelocity(new Vector(0.0D, 0.4D, 0.0D));
        }
    }
}
