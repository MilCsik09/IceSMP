package hu.taliann.icesmp.itemization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ItemTemplate(
        String templateId,
        int schemaVersion,
        int templateVersion,
        String displayName,
        List<String> lore,
        ItemRarity rarity,
        int itemLevel,
        Family family,
        Slot slot,
        String material,
        String itemModel,
        String equipmentAsset,
        int levelRequirement,
        Set<String> classRestrictions,
        Set<String> specializationRestrictions,
        Set<String> professionRestrictions,
        double baseDamage,
        double baseArmor,
        Map<String, Double> fixedStats,
        Map<String, StatRange> rolledStats,
        int runeSocketCount,
        String signatureEffectId,
        String setId,
        BindPolicy bindPolicy,
        TradePolicy tradePolicy,
        SalvagePolicy salvagePolicy,
        Set<String> sourceTags,
        Set<String> regionTags,
        Set<String> gatheringTags,
        Set<String> craftingTags,
        Map<String, String> encounterMetadata,
        List<String> ascensionPath,
        Map<String, AscensionStage> ascensionStages
) {

    public static final int CURRENT_SCHEMA = 1;

    public enum Family { WEAPON, ARMOR, TOOL, ACCESSORY, CONSUMABLE, MATERIAL, OTHER }
    public enum Slot { HEAD, CHEST, LEGS, FEET, MAIN_HAND, OFF_HAND, TWO_HAND, ACCESSORY, BODY, NONE }
    public enum BindPolicy { NONE, ON_PICKUP, ON_EQUIP, ACCOUNT }
    public enum TradePolicy { TRADEABLE, BOUND_ONLY, MARKET_BLOCKED }
    public enum SalvagePolicy { FORBIDDEN, MATERIALS, SIGNATURE_MATERIALS }

    public record StatRange(double min, double max) {
        private static final double VALUE_TOLERANCE = 0.000_001D;

        public StatRange {
            if (!Double.isFinite(min) || !Double.isFinite(max) || max < min) {
                throw new IllegalArgumentException("invalid item stat range");
            }
        }

        public double valueAt(final double quality) {
            if (!Double.isFinite(quality) || quality < 0.0D || quality > 1.0D) {
                throw new IllegalArgumentException("roll quality must be between 0 and 1");
            }
            return min + ((max - min) * quality);
        }

        public boolean matches(final double value, final double quality) {
            return Double.isFinite(value)
                    && Math.abs(valueAt(quality) - value) <= VALUE_TOLERANCE;
        }
    }

    /** Complete authored state for one ascension step; rolls retain their normalized quality. */
    public record AscensionStage(String stageId, int itemLevel, int levelRequirement,
                                 Map<String, Double> fixedStats,
                                 Map<String, StatRange> rolledStats,
                                 int runeSocketCount, int signatureTier,
                                 String itemModel, String equipmentAsset,
                                 List<String> lore) {
        public AscensionStage {
            stageId = ItemStatCatalog.normalizeId(stageId);
            if (itemLevel < 1 || itemLevel > 1000 || levelRequirement < 0 || levelRequirement > 1000) {
                throw new IllegalArgumentException("invalid ascension item or requirement level");
            }
            fixedStats = ItemTemplate.fixedStats(fixedStats);
            rolledStats = ItemTemplate.rolledStats(rolledStats);
            if (runeSocketCount < 0 || runeSocketCount > 2) {
                throw new IllegalArgumentException("ascension supports 0-2 rune sockets");
            }
            if (signatureTier < 0 || signatureTier > 16) {
                throw new IllegalArgumentException("invalid signature tier");
            }
            itemModel = optionalNamespacedId(itemModel);
            equipmentAsset = optionalNamespacedId(equipmentAsset);
            lore = strings(lore, 24, 240, "ascension lore");
        }
    }

    /** Backward-compatible constructor used by pure-domain callers without ascension stages. */
    public ItemTemplate(final String templateId, final int schemaVersion, final int templateVersion,
                        final String displayName, final List<String> lore, final ItemRarity rarity,
                        final int itemLevel, final Family family, final Slot slot, final String material,
                        final String itemModel, final String equipmentAsset, final int levelRequirement,
                        final Set<String> classRestrictions, final Set<String> specializationRestrictions,
                        final Set<String> professionRestrictions, final double baseDamage,
                        final double baseArmor, final Map<String, Double> fixedStats,
                        final Map<String, StatRange> rolledStats, final int runeSocketCount,
                        final String signatureEffectId, final String setId, final BindPolicy bindPolicy,
                        final TradePolicy tradePolicy, final SalvagePolicy salvagePolicy,
                        final Set<String> sourceTags, final Set<String> regionTags,
                        final Set<String> gatheringTags, final Set<String> craftingTags,
                        final Map<String, String> encounterMetadata, final List<String> ascensionPath) {
        this(templateId, schemaVersion, templateVersion, displayName, lore, rarity, itemLevel,
                family, slot, material, itemModel, equipmentAsset, levelRequirement,
                classRestrictions, specializationRestrictions, professionRestrictions,
                baseDamage, baseArmor, fixedStats, rolledStats, runeSocketCount,
                signatureEffectId, setId, bindPolicy, tradePolicy, salvagePolicy, sourceTags,
                regionTags, gatheringTags, craftingTags, encounterMetadata, ascensionPath, Map.of());
    }

    public ItemTemplate {
        templateId = ItemStatCatalog.normalizeId(templateId);
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("unsupported item template schema: " + schemaVersion);
        }
        if (templateVersion < 1) {
            throw new IllegalArgumentException("item template version must be positive");
        }
        displayName = requireText(displayName, "display name", 160);
        lore = strings(lore, 24, 240, "lore");
        Objects.requireNonNull(rarity, "rarity");
        if (itemLevel < 1 || itemLevel > 1000) {
            throw new IllegalArgumentException("item level is outside the supported range");
        }
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(slot, "slot");
        material = requireText(material, "material", 96).toUpperCase(Locale.ROOT);
        itemModel = optionalNamespacedId(itemModel);
        equipmentAsset = optionalNamespacedId(equipmentAsset);
        if (levelRequirement < 0 || levelRequirement > 1000) {
            throw new IllegalArgumentException("level requirement is outside the supported range");
        }
        classRestrictions = ids(classRestrictions, 13);
        specializationRestrictions = ids(specializationRestrictions, 35);
        professionRestrictions = ids(professionRestrictions, 32);
        if (!Double.isFinite(baseDamage) || baseDamage < 0.0D
                || !Double.isFinite(baseArmor) || baseArmor < 0.0D) {
            throw new IllegalArgumentException("base item stats must be finite and non-negative");
        }
        fixedStats = fixedStats(fixedStats);
        rolledStats = rolledStats(rolledStats);
        if (runeSocketCount < 0 || runeSocketCount > 2) {
            throw new IllegalArgumentException("an item template supports 0-2 rune sockets");
        }
        signatureEffectId = optionalId(signatureEffectId);
        if (!signatureEffectId.isBlank()) SignatureEffectRegistry.require(signatureEffectId);
        setId = optionalId(setId);
        Objects.requireNonNull(bindPolicy, "bindPolicy");
        Objects.requireNonNull(tradePolicy, "tradePolicy");
        Objects.requireNonNull(salvagePolicy, "salvagePolicy");
        sourceTags = ids(sourceTags, 32);
        if (sourceTags.isEmpty()) {
            throw new IllegalArgumentException("authored item requires an explicit source tag");
        }
        regionTags = ids(regionTags, 16);
        gatheringTags = ids(gatheringTags, 32);
        craftingTags = ids(craftingTags, 32);
        encounterMetadata = stringMap(encounterMetadata, 32);
        ascensionPath = idsList(ascensionPath, 4);
        ascensionStages = stages(ascensionStages);
        if (!ascensionStages.keySet().equals(new LinkedHashSet<>(ascensionPath))) {
            if (!ascensionPath.isEmpty() || !ascensionStages.isEmpty()) {
                throw new IllegalArgumentException("ascension path and stage definitions differ");
            }
        }
        Map<String, StatRange> previousRanges = rolledStats;
        int previousSockets = runeSocketCount;
        int previousSignatureTier = signatureEffectId.isBlank() ? 0 : 1;
        for (final String stageId : ascensionPath) {
            final AscensionStage stage = ascensionStages.get(stageId);
            if (!stage.rolledStats().keySet().equals(previousRanges.keySet())) {
                throw new IllegalArgumentException("ascension cannot add or remove rolled stats: " + stageId);
            }
            if (stage.runeSocketCount() < previousSockets) {
                throw new IllegalArgumentException("ascension cannot discard occupied rune sockets: " + stageId);
            }
            if (stage.signatureTier() < previousSignatureTier) {
                throw new IllegalArgumentException("ascension cannot downgrade signature tier: " + stageId);
            }
            previousRanges = stage.rolledStats();
            previousSockets = stage.runeSocketCount();
            previousSignatureTier = stage.signatureTier();
        }
    }

    public AscensionStage stage(final String stageId) {
        if (stageId == null || stageId.isBlank() || "base".equalsIgnoreCase(stageId)) return null;
        return ascensionStages.get(ItemStatCatalog.normalizeId(stageId));
    }

    public boolean matchesAscensionState(final String stageId, final int stageIndex) {
        final String normalized = stageId == null || stageId.isBlank()
                ? "base" : ItemStatCatalog.normalizeId(stageId);
        if (stageIndex == 0) return "base".equals(normalized);
        return stageIndex <= ascensionPath.size()
                && ascensionPath.get(stageIndex - 1).equals(normalized);
    }

    public Map<String, Double> fixedStatsAt(final String stageId) {
        final AscensionStage stage = stage(stageId);
        return stage == null ? fixedStats : stage.fixedStats();
    }

    public Map<String, StatRange> rolledStatsAt(final String stageId) {
        final AscensionStage stage = stage(stageId);
        return stage == null ? rolledStats : stage.rolledStats();
    }

    public int runeSocketCountAt(final String stageId) {
        final AscensionStage stage = stage(stageId);
        return stage == null ? runeSocketCount : stage.runeSocketCount();
    }

    public int signatureTierAt(final String stageId) {
        final AscensionStage stage = stage(stageId);
        return stage == null ? (signatureEffectId.isBlank() ? 0 : 1) : stage.signatureTier();
    }

    public int levelRequirementAt(final String stageId) {
        final AscensionStage stage = stage(stageId);
        return stage == null ? levelRequirement : stage.levelRequirement();
    }

    public String itemModelAt(final String stageId) {
        final AscensionStage stage = stage(stageId);
        return stage == null || stage.itemModel().isBlank() ? itemModel : stage.itemModel();
    }

    public String equipmentAssetAt(final String stageId) {
        final AscensionStage stage = stage(stageId);
        return stage == null || stage.equipmentAsset().isBlank() ? equipmentAsset : stage.equipmentAsset();
    }

    public List<String> loreAt(final String stageId) {
        final AscensionStage stage = stage(stageId);
        return stage == null || stage.lore().isEmpty() ? lore : stage.lore();
    }

    private static Map<String, Double> fixedStats(final Map<String, Double> source) {
        if (source == null || source.isEmpty()) return Map.of();
        if (source.size() > 32) throw new IllegalArgumentException("too many fixed item stats");
        final LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        source.forEach((raw, value) -> {
            final String id = ItemStatCatalog.require(raw).id();
            final double amount = value == null ? Double.NaN : value;
            if (!Double.isFinite(amount)) throw new IllegalArgumentException("invalid fixed stat: " + raw);
            if (result.putIfAbsent(id, amount) != null) throw new IllegalArgumentException("duplicate fixed stat: " + id);
        });
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, StatRange> rolledStats(final Map<String, StatRange> source) {
        if (source == null || source.isEmpty()) return Map.of();
        if (source.size() > 32) throw new IllegalArgumentException("too many rolled item stats");
        final LinkedHashMap<String, StatRange> result = new LinkedHashMap<>();
        source.forEach((raw, range) -> {
            final String id = ItemStatCatalog.require(raw).id();
            if (range == null) throw new IllegalArgumentException("missing rolled stat range: " + raw);
            if (result.putIfAbsent(id, range) != null) throw new IllegalArgumentException("duplicate rolled stat: " + id);
        });
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, AscensionStage> stages(final Map<String, AscensionStage> source) {
        if (source == null || source.isEmpty()) return Map.of();
        if (source.size() > 4) throw new IllegalArgumentException("too many ascension stages");
        final LinkedHashMap<String, AscensionStage> result = new LinkedHashMap<>();
        source.forEach((raw, stage) -> {
            final String id = ItemStatCatalog.normalizeId(raw);
            if (stage == null || !id.equals(stage.stageId())) {
                throw new IllegalArgumentException("invalid ascension stage: " + raw);
            }
            if (result.putIfAbsent(id, stage) != null) {
                throw new IllegalArgumentException("duplicate ascension stage: " + id);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> ids(final Set<String> source, final int max) {
        if (source == null || source.isEmpty()) return Set.of();
        if (source.size() > max) throw new IllegalArgumentException("id set exceeds limit");
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final String value : source) result.add(ItemStatCatalog.normalizeId(value));
        return Collections.unmodifiableSet(result);
    }

    private static List<String> idsList(final List<String> source, final int max) {
        if (source == null || source.isEmpty()) return List.of();
        if (source.size() > max) throw new IllegalArgumentException("id list exceeds limit");
        final ArrayList<String> result = new ArrayList<>();
        for (final String value : source) result.add(ItemStatCatalog.normalizeId(value));
        if (new LinkedHashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException("duplicate id in list");
        }
        return List.copyOf(result);
    }

    private static List<String> strings(final List<String> source, final int max, final int maxLength,
                                        final String name) {
        if (source == null || source.isEmpty()) return List.of();
        if (source.size() > max) throw new IllegalArgumentException(name + " exceeds limit");
        final ArrayList<String> result = new ArrayList<>();
        for (final String value : source) result.add(requireText(value, name, maxLength));
        return List.copyOf(result);
    }

    private static Map<String, String> stringMap(final Map<String, String> source, final int max) {
        if (source == null || source.isEmpty()) return Map.of();
        if (source.size() > max) throw new IllegalArgumentException("metadata exceeds limit");
        final LinkedHashMap<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(ItemStatCatalog.normalizeId(key),
                requireText(value, "metadata value", 160)));
        return Collections.unmodifiableMap(result);
    }

    private static String optionalId(final String raw) {
        return raw == null || raw.isBlank() ? "" : ItemStatCatalog.normalizeId(raw);
    }

    private static String optionalNamespacedId(final String raw) {
        if (raw == null || raw.isBlank()) return "";
        final String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid namespaced id: " + raw);
        }
        return value;
    }

    private static String requireText(final String raw, final String name, final int maxLength) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException(name + " is required");
        final String value = raw.trim();
        if (value.length() > maxLength) throw new IllegalArgumentException(name + " is too long");
        return value;
    }
}
