package hu.taliann.icesmp.trash;

import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

/** Immutable authored identity resolved from the packaged Trash catalog. */
public record TrashDefinition(
        String id,
        String displayName,
        String playerRarity,
        Material material,
        String itemModel,
        String texture,
        int vendorValue,
        List<String> lore,
        TrashSourceBias sourceBias,
        TrashKind internalKind,
        String behavior,
        String successPhase,
        TrashArchaeologyEvidence archaeology
) {
    public TrashDefinition(String id, String displayName, String playerRarity, Material material,
            String itemModel, String texture, int vendorValue, List<String> lore, TrashSourceBias sourceBias,
            TrashKind internalKind, String behavior, String successPhase) {
        this(id, displayName, playerRarity, material, itemModel, texture, vendorValue, lore, sourceBias,
                internalKind, behavior, successPhase, null);
    }

    public TrashDefinition withArchaeology(final TrashArchaeologyEvidence evidence) {
        return new TrashDefinition(id, displayName, playerRarity, material, itemModel, texture, vendorValue,
                lore, sourceBias, internalKind, behavior, successPhase, Objects.requireNonNull(evidence));
    }

    public TrashDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(playerRarity, "playerRarity");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(itemModel, "itemModel");
        Objects.requireNonNull(texture, "texture");
        lore = List.copyOf(lore);
        Objects.requireNonNull(sourceBias, "sourceBias");
        Objects.requireNonNull(internalKind, "internalKind");
        Objects.requireNonNull(behavior, "behavior");
        successPhase = successPhase == null ? "" : successPhase;
    }
}
