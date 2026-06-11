package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SmokeBombSpell extends BaseSpell {

    private static final double RADIUS = 5.0D;
    private static final int BLINDNESS_TICKS = 5 * 20;
    private static final int ESCAPE_SPEED_TICKS = 4 * 20;

    public SmokeBombSpell(final MessageManager messageManager) {
        super(messageManager, "smoke_bomb", "Füstbomba", 120, SpellCostType.HUNGER, 6);
    }

    @Override
    public void execute(final Player player) {
        final Location center = player.getLocation();

        for (final Entity entity : player.getWorld().getNearbyEntities(center, RADIUS, RADIUS, RADIUS)) {
            if (!(entity instanceof LivingEntity living) || entity == player) {
                continue;
            }

            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLINDNESS_TICKS, 0, false, true, true));
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, BLINDNESS_TICKS, 1, false, true, true));
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ESCAPE_SPEED_TICKS, 1, false, false, true));
        player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0.0D, 1.0D, 0.0D), 80, 2.0D, 1.0D, 2.0D, 0.01D);
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.6F);
    }
}
