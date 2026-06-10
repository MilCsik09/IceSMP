package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class FeatherfootSpell extends BaseSpell {

    public FeatherfootSpell(final MessageManager messageManager) {
        super(messageManager, "featherfoot", "Pehelykonyu Leptu", 45, SpellCostType.HUNGER, 1);
    }

    @Override
    public void execute(final Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 10, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 10, 0, false, true, true));
    }
}

