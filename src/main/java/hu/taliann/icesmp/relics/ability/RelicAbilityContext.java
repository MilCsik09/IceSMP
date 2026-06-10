package hu.taliann.icesmp.relics.ability;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.relics.RelicTrigger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record RelicAbilityContext(
        Player player,
        ItemStack itemStack,
        RelicDefinition definition,
        RelicTrigger trigger,
        ConfigManager configManager
) {
}

