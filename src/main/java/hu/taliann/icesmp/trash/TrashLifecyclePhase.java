package hu.taliann.icesmp.trash;

import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

/** Player-facing presentation for a non-loot lifecycle phase of a base Trash identity. */
public record TrashLifecyclePhase(
        String id,
        String displayName,
        String playerRarity,
        Material material,
        String itemModel,
        String texture,
        int vendorValue,
        List<String> lore
) {
    public TrashLifecyclePhase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(playerRarity, "playerRarity");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(itemModel, "itemModel");
        Objects.requireNonNull(texture, "texture");
        lore = List.copyOf(lore);
    }
}
