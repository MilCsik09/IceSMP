package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * "Szent Harag" — guided light projectile (AngryChicken-mintás, ARMOR_STAND alapú). Az első
 * eltalált élőlény a JELENLEGI életének 30%-át veszti el. Bossokra (org.bukkit.entity.Boss,
 * Warden) és a world-boss PDC-taggel jelölt mobokra hatástalan.
 */
public final class HolyWrathSpell extends BaseSpell {

    private static final String PROJECTILE_TAG = "icesmp_holy_wrath";

    private final JavaPlugin plugin;
    private final NamespacedKey worldBossKey;

    public HolyWrathSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "holy_wrath", "Szent Harag", 90, SpellCostType.XP, 60);
        this.plugin = plugin;
        this.worldBossKey = new NamespacedKey(plugin, "world_boss");
    }

    private boolean isBossExempt(final LivingEntity living) {
        if (living instanceof Boss || living instanceof Warden) {
            return true;
        }
        return living.getPersistentDataContainer()
                .getOrDefault(worldBossKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
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
        final double damagePercent = balance("damage-percent", 0.30D);

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

                if (isBossExempt(living)) {
                    if (shooter != null) {
                        shooter.getScheduler().run(plugin, hop -> shooter.sendMessage(
                                resolveMessage("spell.holy_wrath.boss-immune",
                                        "<gray>A Szent Harag fénye hatástalanul siklik le róla.</gray>")), null);
                    }
                } else {
                    final double damage = living.getHealth() * damagePercent;
                    if (shooterInCurrentRegion) {
                        living.damage(damage, shooter);
                    } else {
                        living.damage(damage);
                    }
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

        player.getWorld().spawnParticle(Particle.FLASH, player.getEyeLocation(), 2, 0.0D, 0.0D, 0.0D, 0.0D);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7F, 1.4F);
    }
}
