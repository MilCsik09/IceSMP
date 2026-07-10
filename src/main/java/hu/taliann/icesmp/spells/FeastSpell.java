package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;

public final class FeastSpell extends BaseSpell {

    public FeastSpell(final MessageManager messageManager) {
        super(messageManager, "feast", "Lakoma", 120, SpellCostType.XP, 352);
    }

    @Override
    public void execute(final Player player) {
        player.setFoodLevel(balanceInt("food-level", 20));
        player.setSaturation((float) balance("saturation", 20.0D));
    }
}

