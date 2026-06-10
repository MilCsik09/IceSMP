package hu.taliann.icesmp.relics;

import org.bukkit.Material;

import java.util.List;

public record SimpleRelicDefinition(
        String id,
        String displayName,
        String displayColor,
        List<String> lore,
        Material material,
        int customModelData
) implements RelicDefinition {
}

