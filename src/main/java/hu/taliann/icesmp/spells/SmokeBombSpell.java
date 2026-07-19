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
        final double radius = balance("radius", RADIUS);
        final int blindnessTicks = balanceInt("blindness-duration-ticks", BLINDNESS_TICKS);
        final int slownessAmplifier = balanceInt("slowness-amplifier", 1);
        final int escapeSpeedTicks = balanceInt("escape-speed-duration-ticks", ESCAPE_SPEED_TICKS);
        final int escapeSpeedAmplifier = balanceInt("escape-speed-amplifier", 1);

        for (final Entity entity : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity == player || SpellTargetingUtil.isAlly(player, living)) {
                continue;
            }

            living.addPotionEffect(SpellTargetingUtil.adaptForTarget(living,
                    new PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, false, true, true)));
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, blindnessTicks, slownessAmplifier, false, true, true));
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, escapeSpeedTicks, escapeSpeedAmplifier, false, false, true));
        player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0.0D, 1.0D, 0.0D), 80, 2.0D, 1.0D, 2.0D, 0.01D);
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.6F);
    }
}
