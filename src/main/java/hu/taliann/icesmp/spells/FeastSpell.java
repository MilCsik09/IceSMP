package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;

public final class FeastSpell extends BaseSpell {

    public FeastSpell(final MessageManager messageManager) {
        super(messageManager, "feast", "Lakoma", 120, SpellCostType.XP, 352);
    }

    @Override
    public void execute(final Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
    }
}

