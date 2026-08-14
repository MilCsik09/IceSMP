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
 * <p>The helper never synthesizes a new {@link Equippable} component. Armor slot, equip sound,
 * swappability, damage-on-hurt and every other vanilla property are preserved by cloning the
 * component already provided by the item's material and changing only {@code assetId}.
 *
 * <p>Configuration convention: an explicit {@code equipment-asset} always wins. If it is omitted,
 * an already-equippable item may use its {@code icesmp:*} {@code item-model} only when the material
 * is covered by the shared, versioned {@code wearable-fallback-policy.properties}. The same policy
 * is consumed by resource-pack validation, so runtime fallback eligibility cannot drift from CI.
 */
@SuppressWarnings("UnstableApiUsage")
public final class WearablePresentation {

    private static final String FALLBACK_POLICY_RESOURCE = "wearable-fallback-policy.properties";
    private static final FallbackPolicy FALLBACK_POLICY = FallbackPolicy.load();

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
     * Explicit asset ids win. Otherwise only a genuinely equippable item whose Material is covered
     * by the shared 1.21.11 fallback policy may use the same render id for its equipment asset.
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
                                  List<String> materialSuffixes) {

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

            final Set<String> exact = csv(properties.getProperty("exact", "")).stream()
                    .collect(Collectors.toUnmodifiableSet());
            final List<String> suffixes = List.copyOf(csv(properties.getProperty("suffix", "")));
            if (exact.isEmpty() && suffixes.isEmpty()) {
                throw new IllegalStateException("wearable fallback policy contains no material rules");
            }
            return new FallbackPolicy(minecraftVersion, exact, suffixes);
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

        private static List<String> csv(final String raw) {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .toList();
        }
    }
}
