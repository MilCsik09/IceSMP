package hu.taliann.icesmp.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central presentation boundary for custom wearable items.
 *
 * <p>{@code ITEM_MODEL} controls the inventory/hand representation, while the asset id inside the
 * existing {@code EQUIPPABLE} component controls the appearance while the item is worn. These are
 * deliberately separate identities even when the current content uses the same IceSMP render id.
 *
 * <p>The helper never synthesizes a replacement equipment slot. Custom worn presentation clones the
 * current {@link Equippable} component and changes only {@code assetId}. The RP2-A vanilla reset does
 * the inverse: it restores only the backing Material's vanilla {@code assetId}, preserving slot,
 * equip sound, swappability, damage-on-hurt and every other authored/vanilla equipment property.
 *
 * <p>The shared, versioned {@code wearable-fallback-policy.properties} is consumed by runtime and
 * resource-pack validation. It also declares temporary RP2-A vanilla inventory fallbacks for
 * production ids whose old config points at a non-existent item definition. Those ids are skipped
 * intentionally instead of being sent to the client as broken ITEM_MODEL references.
 */
@SuppressWarnings("UnstableApiUsage")
public final class WearablePresentation {

    private static final String FALLBACK_POLICY_RESOURCE = "wearable-fallback-policy.properties";
    private static final FallbackPolicy FALLBACK_POLICY = FallbackPolicy.load();

    public enum EquipmentAssetStatus {
        NOT_REQUESTED,
        APPLIED,
        VANILLA_FALLBACK_APPLIED,
        NOT_EQUIPPABLE,
        INVALID_ASSET_ID
    }

    public record Result(String itemModel, String equipmentAsset, EquipmentAssetStatus equipmentStatus) {
        public boolean equipmentApplied() {
            return equipmentStatus == EquipmentAssetStatus.APPLIED
                    || equipmentStatus == EquipmentAssetStatus.VANILLA_FALLBACK_APPLIED;
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
        final String requestedModel = normalize(itemModelId);
        final String normalizedModel = requestedModel != null && !forcesVanillaItemModel(requestedModel)
                ? requestedModel : null;
        if (normalizedModel != null) {
            ItemDataFactory.applyItemModel(item, normalizedModel);
        }

        if (item != null && forcesVanillaWornMaterial(item.getType().name())) {
            return new Result(normalizedModel, null, restoreVanillaEquipmentAsset(item));
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
     * RP2-A temporary reset: copy only the backing Material's vanilla asset id onto the current
     * component. This is idempotent and also repairs already-serialized items that still carry a
     * legacy custom asset id when they pass through the normal refresh/presentation boundary.
     */
    static EquipmentAssetStatus restoreVanillaEquipmentAsset(final ItemStack item) {
        if (item == null) {
            return EquipmentAssetStatus.NOT_REQUESTED;
        }
        final Equippable current = item.getData(DataComponentTypes.EQUIPPABLE);
        final Equippable vanilla = new ItemStack(item.getType()).getData(DataComponentTypes.EQUIPPABLE);
        if (current == null || vanilla == null) {
            return EquipmentAssetStatus.NOT_EQUIPPABLE;
        }
        item.setData(DataComponentTypes.EQUIPPABLE,
                current.toBuilder()
                        .assetId(vanilla.assetId())
                        .build());
        return EquipmentAssetStatus.VANILLA_FALLBACK_APPLIED;
    }

    /**
     * Explicit asset ids win for non-reset equipment. Otherwise only a genuinely equippable item
     * whose Material is covered by the shared 1.21.11 fallback policy may use the same render id.
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
        if (item.getData(DataComponentTypes.EQUIPPABLE) == null
                || !allowsImplicitSameIdFallback(item.getType().name())) {
            return null;
        }
        return normalizedItemModel;
    }

    static boolean allowsImplicitSameIdFallback(final String materialName) {
        return FALLBACK_POLICY.allows(materialName);
    }

    static boolean forcesVanillaWornMaterial(final String materialName) {
        return FALLBACK_POLICY.forcesVanillaWorn(materialName);
    }

    static boolean forcesVanillaItemModel(final String itemModelId) {
        return FALLBACK_POLICY.forcesVanillaItemModel(normalize(itemModelId));
    }

    static String fallbackPolicyMinecraftVersion() {
        return FALLBACK_POLICY.minecraftVersion();
    }

    static String normalize(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        final String normalized = id.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : "icesmp:" + normalized;
    }

    private record FallbackPolicy(String minecraftVersion, Set<String> exactMaterials,
                                  List<String> materialSuffixes, List<String> vanillaWornSuffixes,
                                  Set<String> vanillaItemModels) {

        static FallbackPolicy load() {
            final Properties properties = new Properties();
            try (InputStream input = WearablePresentation.class.getClassLoader()
                    .getResourceAsStream(FALLBACK_POLICY_RESOURCE)) {
                if (input == null) {
                    throw new IllegalStateException("Missing " + FALLBACK_POLICY_RESOURCE);
                }
                properties.load(input);
            } catch (final IOException exception) {
                throw new IllegalStateException("Failed to load " + FALLBACK_POLICY_RESOURCE, exception);
            }

            if (!"1".equals(properties.getProperty("schema"))) {
                throw new IllegalStateException("Unsupported wearable fallback policy schema");
            }
            final String minecraftVersion = properties.getProperty("minecraft-version", "").trim();
            if (minecraftVersion.isEmpty()) {
                throw new IllegalStateException("wearable fallback policy is missing minecraft-version");
            }

            final Set<String> exact = csvUpper(properties.getProperty("exact", "")).stream()
                    .collect(Collectors.toUnmodifiableSet());
            final List<String> suffixes = List.copyOf(csvUpper(properties.getProperty("suffix", "")));
            final List<String> vanillaWorn = List.copyOf(csvUpper(
                    properties.getProperty("vanilla-worn-suffix", "")));
            final Set<String> vanillaModels = csvIds(properties.getProperty(
                    "vanilla-item-model", "")).stream().collect(Collectors.toUnmodifiableSet());
            if (exact.isEmpty() && suffixes.isEmpty()) {
                throw new IllegalStateException("wearable fallback policy contains no implicit material rules");
            }
            if (vanillaWorn.isEmpty()) {
                throw new IllegalStateException("wearable fallback policy contains no RP2 vanilla-worn rules");
            }
            return new FallbackPolicy(minecraftVersion, exact, suffixes, vanillaWorn, vanillaModels);
        }

        boolean allows(final String materialName) {
            if (materialName == null || materialName.isBlank()) {
                return false;
            }
            final String normalized = materialName.trim().toUpperCase(Locale.ROOT);
            if (exactMaterials.contains(normalized)) {
                return true;
            }
            return materialSuffixes.stream().anyMatch(normalized::endsWith);
        }

        boolean forcesVanillaWorn(final String materialName) {
            if (materialName == null || materialName.isBlank()) {
                return false;
            }
            final String normalized = materialName.trim().toUpperCase(Locale.ROOT);
            return vanillaWornSuffixes.stream().anyMatch(normalized::endsWith);
        }

        boolean forcesVanillaItemModel(final String itemModelId) {
            return itemModelId != null && vanillaItemModels.contains(itemModelId.toLowerCase(Locale.ROOT));
        }

        private static List<String> csvUpper(final String raw) {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .toList();
        }

        private static List<String> csvIds(final String raw) {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(WearablePresentation::normalize)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
    }
}
