package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * "Jeglanc" — ray-traces the main target, then also chills at most one more living entity
 * (the closest) within a short link-range of the main target. Fully synchronous (single
 * region-thread pass), matching {@link LifeDrainSpell} / the ConfiguredSpell TARGET path —
 * no scheduler hop needed.
 */
public final class ChainsOfIceSpell extends BaseSpell {

    private final JavaPlugin plugin;

    public ChainsOfIceSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "chains_of_ice", "Jéglánc", 30, SpellCostType.HUNGER, 3);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        final double range = balance("range", 12.0D);
        final LivingEntity primary = SpellTargetingUtil.rayTraceLivingEntity(player, range);
        if (primary == null) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a látómeződben."));
            return false;
        }

        final int slownessTicks = balanceInt("slowness-duration-ticks", 5 * 20);
        final int slownessAmplifier = balanceInt("slowness-amplifier", 3);
        final int freezeTicks = balanceInt("freeze-ticks", 140);

        chill(primary, slownessTicks, slownessAmplifier, freezeTicks);

        final double linkRange = balance("link-range", 3.0D);
        LivingEntity secondary = null;
        double closestDistanceSq = Double.MAX_VALUE;
        for (final Entity nearby : primary.getNearbyEntities(linkRange, linkRange, linkRange)) {
            if (!(nearby instanceof LivingEntity living) || living == player || living == primary) {
                continue;
            }
            final double distanceSq = living.getLocation().distanceSquared(primary.getLocation());
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                secondary = living;
            }
        }
        if (secondary != null) {
            chill(secondary, slownessTicks, slownessAmplifier, freezeTicks);
        }

        player.getWorld().spawnParticle(Particle.SNOWFLAKE, primary.getLocation().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.5D, 0.3D, 0.02D);
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0F, 0.7F);
        return true;
    }

    private static void chill(final LivingEntity target, final int slownessTicks, final int slownessAmplifier, final int freezeTicks) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessTicks, slownessAmplifier, false, true, true));
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), freezeTicks));
    }
}
