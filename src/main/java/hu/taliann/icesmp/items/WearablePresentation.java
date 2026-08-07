package hu.taliann.icesmp.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Central presentation boundary for custom wearable items.
 *
 * <p>{@code ITEM_MODEL} controls the inventory/hand representation, while the asset id inside the
 * existing {@code EQUIPPABLE} component controls the appearance while the item is worn. These are
 * deliberately separate identities even when the current content uses the same IceSMP render id.
 *
 * <p>The helper never synthesizes a new {@link Equippable} component. Armor slot, equip sound,
 * swappability, damage-on-hurt and every other vanilla property are preserved by cloning the
 * component already provided by the item's material and changing only {@code assetId}.
 *
 * <p>Configuration convention: an explicit {@code equipment-asset} always wins. If it is omitted,
 * an already-equippable item may use its {@code icesmp:*} {@code item-model} as a deterministic
 * same-render-identity fallback. Resource-pack validation proves that every such fallback used by
 * checked-in content resolves to an actual equipment asset.
 */
@SuppressWarnings("UnstableApiUsage")
public final class WearablePresentation {

    public enum EquipmentAssetStatus {
        NOT_REQUESTED,
        APPLIED,
        NOT_EQUIPPABLE,
        INVALID_ASSET_ID
    }

    public record Result(String itemModel, String equipmentAsset, EquipmentAssetStatus equipmentStatus) {
        public boolean equipmentApplied() {
            return equipmentStatus == EquipmentAssetStatus.APPLIED;
        }
    }

    private WearablePresentation() {
    }

    /**
     * Applies the inventory model and, when requested/resolved, the worn equipment asset.
     * Data components must still be applied after all ItemMeta round-trips at the caller.
     */
    public static Result applyWearablePresentation(final ItemStack item, final String itemModelId,
                                                    final String equipmentAssetId) {
        final String normalizedModel = normalize(itemModelId);
        if (normalizedModel != null) {
            ItemDataFactory.applyItemModel(item, normalizedModel);
        }

        final String resolvedEquipment = resolveEquipmentAsset(item, normalizedModel, equipmentAssetId);
        if (resolvedEquipment == null) {
            return new Result(normalizedModel, null, EquipmentAssetStatus.NOT_REQUESTED);
        }
        return new Result(normalizedModel, resolvedEquipment, applyEquipmentAsset(item, resolvedEquipment));
    }

    /**
     * Applies only the worn equipment asset. The pre-existing component is copied with toBuilder(),
     * therefore the vanilla equipment slot and all other equip behaviour remain untouched.
     */
    public static EquipmentAssetStatus applyEquipmentAsset(final ItemStack item, final String equipmentAssetId) {
        final String normalizedAsset = normalize(equipmentAssetId);
        if (item == null || normalizedAsset == null) {
            return EquipmentAssetStatus.NOT_REQUESTED;
        }

        final Equippable current = item.getData(DataComponentTypes.EQUIPPABLE);
        if (current == null) {
            return EquipmentAssetStatus.NOT_EQUIPPABLE;
        }

        final Key assetKey;
        try {
            assetKey = Key.key(normalizedAsset);
        } catch (final IllegalArgumentException invalidKey) {
            return EquipmentAssetStatus.INVALID_ASSET_ID;
        }

        item.setData(DataComponentTypes.EQUIPPABLE,
                current.toBuilder()
                        .assetId(assetKey)
                        .build());
        return EquipmentAssetStatus.APPLIED;
    }

    /**
     * Explicit asset ids win. Otherwise only an item that is already equippable may use the
     * documented IceSMP same-id fallback ({@code item-model: icesmp:x -> equipment: icesmp:x}).
     */
    static String resolveEquipmentAsset(final ItemStack item, final String normalizedItemModel,
                                        final String explicitEquipmentAsset) {
        final String explicit = normalize(explicitEquipmentAsset);
        if (explicit != null) {
            return explicit;
        }
        if (item == null || normalizedItemModel == null || !normalizedItemModel.startsWith("icesmp:")) {
            return null;
        }
        return item.getData(DataComponentTypes.EQUIPPABLE) == null ? null : normalizedItemModel;
    }

    static String normalize(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        final String normalized = id.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : "icesmp:" + normalized;
    }
}
