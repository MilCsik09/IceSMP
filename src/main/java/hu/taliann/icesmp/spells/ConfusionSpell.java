package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ConfusionSpell extends BaseSpell {

    public ConfusionSpell(final MessageManager messageManager) {
        super(messageManager, "confusion", "Megzavaras", 20 * 60, SpellCostType.XP, 160);
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        final double radius = balance("radius", 15.0D);
        final int durationTicks = balanceInt("duration-ticks", 20 * 10);
        boolean hit = false;
        for (final LivingEntity target : player.getLocation().getNearbyLivingEntities(radius, radius, radius)) {
            if (target == player || target.getLocation().distanceSquared(player.getLocation()) > (radius * radius)) {
                continue;
            }

            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, durationTicks, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, durationTicks, 0, false, true, true));
            hit = true;
        }
        // No target: fail the cast so the (expensive, long-cooldown) cast is refunded.
        if (!hit) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a közeledben."));
        }
        return hit;
    }
}

