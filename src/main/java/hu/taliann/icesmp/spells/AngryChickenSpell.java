package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class AngryChickenSpell extends BaseSpell {

    public static final String CHICKEN_TAG = "icesmp_angry_chicken";

    private final JavaPlugin plugin;

    public AngryChickenSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "angry_chicken", "Mergez Csirke", 30, SpellCostType.HUNGER, 5);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        final UUID shooterId = player.getUniqueId();
        final Vector direction = player.getEyeLocation().getDirection().normalize();
        final Chicken chicken = player.getWorld().spawn(player.getEyeLocation().add(direction.multiply(0.8D)), Chicken.class);
        chicken.setAdult();
        chicken.setAI(false);
        chicken.setGravity(false);
        chicken.setCollidable(false);
        chicken.addScoreboardTag(CHICKEN_TAG);
        chicken.addScoreboardTag("icesmp_owner_" + player.getUniqueId());

        // Folia: drive the projectile on the chicken's own entity scheduler.
        chicken.getScheduler().runAtFixedRate(plugin, task -> {
            if (!chicken.isValid() || chicken.isDead()) {
                task.cancel();
                return;
            }

            final Vector step = direction.clone().multiply(balance("speed", 0.4D)); // ~8 blocks/second at 20 TPS
            chicken.teleportAsync(chicken.getLocation().add(step));

            final Player shooter = plugin.getServer().getPlayer(shooterId);
            final boolean shooterInCurrentRegion = shooter != null && Bukkit.isOwnedByCurrentRegion(shooter);

            final double hitRadius = balance("hit-radius", 1.1D);
            for (final Entity nearby : chicken.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                if (!(nearby instanceof LivingEntity living) || living == chicken || living.getUniqueId().equals(shooterId)) {
                    continue;
                }
                // Baráti tűz-védelem, mint a többi irányított lövedéknél (party/frakció-társ nem cél).
                if (shooterInCurrentRegion && SpellTargetingUtil.isAlly(shooter, living)) {
                    continue;
                }

                final double damage = balance("damage", 8.0D);
                if (shooterInCurrentRegion) {
                    hu.taliann.icesmp.utils.SpellDamageUtil.damageBySpell(shooter, living, damage, getId());
                } else {
                    living.damage(damage);
                }
                chicken.remove();
                task.cancel();
                return;
            }
        }, null, 1L, 1L);

        chicken.getScheduler().runDelayed(plugin, task -> {
            if (chicken.isValid()) {
                chicken.remove();
            }
        }, null, balanceInt("duration-ticks", 40));

        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1.0F, 0.8F);
    }
}
