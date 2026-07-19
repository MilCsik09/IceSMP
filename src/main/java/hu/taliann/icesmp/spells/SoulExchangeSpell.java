package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * "Lélek Csere" (id marad {@code shear}) — guided projectile (AngryChicken-mintás, ARMOR_STAND
 * alapú). Az első eltalált élőlény a JELENLEGI életének 25%-át veszti el, a kaszter pedig
 * ugyanennyivel (a cél becsapódás előtti élete 25%-ával) gyógyul, max-élet plafonnal.
 */
public final class SoulExchangeSpell extends BaseSpell {

    private static final String PROJECTILE_TAG = "icesmp_soul_exchange";

    private final JavaPlugin plugin;

    public SoulExchangeSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "shear", "Lélek Csere", 50, SpellCostType.HUNGER, 2);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        final UUID shooterId = player.getUniqueId();
        final Vector direction = player.getEyeLocation().getDirection().normalize();
        final ArmorStand projectile = player.getWorld().spawn(
                player.getEyeLocation().add(direction.clone().multiply(0.8D)), ArmorStand.class, as -> {
                    as.setInvisible(true);
                    as.setMarker(true);
                    as.setSmall(true);
                    as.setGravity(false);
                    as.setCollidable(false);
                    as.setInvulnerable(true);
                    as.setPersistent(false);
                    as.addScoreboardTag(PROJECTILE_TAG);
                });

        final double speed = balance("speed", 0.9D);
        final double hitRadius = balance("hit-radius", 1.1D);
        final double exchangePercent = balance("exchange-percent", 0.25D);

        // Folia: drive the projectile on its own entity scheduler.
        projectile.getScheduler().runAtFixedRate(plugin, task -> {
            if (!projectile.isValid() || projectile.isDead()) {
                task.cancel();
                return;
            }

            projectile.teleportAsync(projectile.getLocation().add(direction.clone().multiply(speed)));

            final Player shooter = plugin.getServer().getPlayer(shooterId);
            final boolean shooterInCurrentRegion = shooter != null && Bukkit.isOwnedByCurrentRegion(shooter);

            for (final Entity nearby : projectile.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                if (!(nearby instanceof LivingEntity living) || living instanceof ArmorStand
                        || living.getUniqueId().equals(shooterId)
                        || SpellTargetingUtil.isAlly(shooterId, living)) {
                    continue;
                }

                final double amount = living.getHealth() * exchangePercent;
                if (shooterInCurrentRegion) {
                    living.damage(amount, shooter);
                } else {
                    living.damage(amount);
                }

                if (shooter != null) {
                    shooter.getScheduler().run(plugin, hop -> {
                        if (!shooter.isOnline()) {
                            return;
                        }
                        final AttributeInstance maxHealth = shooter.getAttribute(Attribute.MAX_HEALTH);
                        final double cap = maxHealth != null ? maxHealth.getValue() : 20.0D;
                        shooter.setHealth(Math.min(cap, shooter.getHealth() + amount));
                        shooter.getWorld().spawnParticle(Particle.HEART, shooter.getLocation().add(0.0D, 2.0D, 0.0D), 4, 0.2D, 0.2D, 0.2D);
                    }, null);
                }

                projectile.remove();
                task.cancel();
                return;
            }
        }, null, 1L, 1L);

        projectile.getScheduler().runDelayed(plugin, task -> {
            if (projectile.isValid()) {
                projectile.remove();
            }
        }, null, balanceInt("duration-ticks", 40));

        player.getWorld().spawnParticle(Particle.SOUL, player.getEyeLocation(), 8, 0.1D, 0.1D, 0.1D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.1F);
    }
}
