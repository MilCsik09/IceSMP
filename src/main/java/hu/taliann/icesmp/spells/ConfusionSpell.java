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
        for (final LivingEntity target : player.getLocation().getNearbyLivingEntities(15.0D, 15.0D, 15.0D)) {
            if (target == player || target.getLocation().distanceSquared(player.getLocation()) > (15.0D * 15.0D)) {
                continue;
            }

            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 10, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 10, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 10, 0, false, true, true));
        }
    }
}

