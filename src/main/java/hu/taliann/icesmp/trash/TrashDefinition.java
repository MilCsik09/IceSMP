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
        TrashArchaeologyEvidence archaeology,
        double losingHealthFraction,
        List<String> contextualText
) {
    public TrashDefinition(String id, String displayName, String playerRarity, Material material,
            String itemModel, String texture, int vendorValue, List<String> lore, TrashSourceBias sourceBias,
            TrashKind internalKind, String behavior, String successPhase, TrashArchaeologyEvidence archaeology) {
        this(id, displayName, playerRarity, material, itemModel, texture, vendorValue, lore, sourceBias,
                internalKind, behavior, successPhase, archaeology, 0.0D, List.of());
    }

    public TrashDefinition(String id, String displayName, String playerRarity, Material material,
            String itemModel, String texture, int vendorValue, List<String> lore, TrashSourceBias sourceBias,
            TrashKind internalKind, String behavior, String successPhase) {
        this(id, displayName, playerRarity, material, itemModel, texture, vendorValue, lore, sourceBias,
                internalKind, behavior, successPhase, null);
    }

    public TrashDefinition withArchaeology(final TrashArchaeologyEvidence evidence) {
        return new TrashDefinition(id, displayName, playerRarity, material, itemModel, texture, vendorValue,
                lore, sourceBias, internalKind, behavior, successPhase, Objects.requireNonNull(evidence), losingHealthFraction, contextualText);
    }

    public TrashDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(playerRarity, "playerRarity");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(itemModel, "itemModel");
        Objects.requireNonNull(texture, "texture");
        lore = List.copyOf(lore);
        contextualText = List.copyOf(contextualText);
        if (!Double.isFinite(losingHealthFraction) || losingHealthFraction < 0.0D || losingHealthFraction > 0.5D)
            throw new IllegalArgumentException("invalid losing health fraction");
        if (contextualText.size() > 8 || contextualText.stream().anyMatch(s -> s.isBlank() || s.length() > 180))
            throw new IllegalArgumentException("invalid contextual text");
        Objects.requireNonNull(sourceBias, "sourceBias");
        Objects.requireNonNull(internalKind, "internalKind");
        Objects.requireNonNull(behavior, "behavior");
        successPhase = successPhase == null ? "" : successPhase;
    }
}
