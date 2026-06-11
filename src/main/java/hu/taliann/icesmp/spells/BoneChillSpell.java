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

public final class BoneChillSpell extends BaseSpell {

    private static final double RADIUS = 6.0D;
    private static final int EFFECT_TICKS = 6 * 20;

    public BoneChillSpell(final MessageManager messageManager) {
        super(messageManager, "bone_chill", "Csontfagy", 90, SpellCostType.XP, 25);
    }

    @Override
    public void execute(final Player player) {
        final Location center = player.getLocation();

        for (final Entity entity : player.getWorld().getNearbyEntities(center, RADIUS, RADIUS, RADIUS)) {
            if (!(entity instanceof LivingEntity living) || entity == player) {
                continue;
            }

            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_TICKS, 2, false, true, true));
            living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, EFFECT_TICKS, 1, false, true, true));
        }

        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0.0D, 0.2D, 0.0D), 60, RADIUS / 2.0D, 0.3D, RADIUS / 2.0D, 0.02D);
        player.getWorld().playSound(center, Sound.ENTITY_SKELETON_AMBIENT, 1.0F, 0.6F);
        player.sendMessage(resolveMessage("spell.bone_chill.activated", "<dark_gray>A csontfagy dermesztő hulláma söpör végig a környezeteden.</dark_gray>"));
    }
}
