package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;

public final class ItemTemplateRegistry {

    private record Snapshot(Map<String, ItemTemplate> templates,
                            Map<String, ItemSetDefinition> sets,
                            Map<ArmorFamily, ArmorFamilyProfile> familyProfiles) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of());
        }
    }

    private static volatile ItemTemplateRegistry activeInstance;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private volatile Snapshot snapshot = Snapshot.empty();

    public ItemTemplateRegistry(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.configManager = java.util.Objects.requireNonNull(configManager, "configManager");
        activeInstance = this;
    }

    /** Runtime read-only registry seam for owner-thread set-bonus projection. */
    public static ItemTemplateRegistry current() {
        return activeInstance;
    }

    public synchronized void load() {
        final ConfigurationSection root = configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("item-templates");
        if (root == null) {
            throw new IllegalStateException("content/equipment/equipment.yml: hiányzik az item-templates gyökér; "
                    + "az előző immutable Equipment 2.0 snapshot marad aktív");
        }
        final Map<String, ItemSetDefinition> loadedSets = parseSets(
                configManager.getConfiguration().getConfigurationSection("item-sets"));
        final Map<ArmorFamily, ArmorFamilyProfile> loadedProfiles = parseFamilyProfiles(
                configManager.getConfiguration().getConfigurationSection(
                        "itemization.equipment.family-profiles"));
        final LinkedHashMap<String, ItemTemplate> loaded = new LinkedHashMap<>();
        final ArrayList<String> errors = new ArrayList<>();
        for (final String rawId : root.getKeys(false)) {
            final ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                errors.add(rawId + ": a template nem objektum");
                continue;
            }
            try {
                final ItemTemplate template = parse(rawId, section);
                if (loaded.putIfAbsent(template.templateId(), template) != null) {
                    throw new IllegalArgumentException("duplikált template ID");
                }
            } catch (final RuntimeException invalid) {
                errors.add(rawId + ": " + invalid.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Hibás item template katalógus: " + String.join("; ", errors));
        }
        for (final ItemTemplate template : loaded.values()) {
            if (!template.setId().isBlank() && !loadedSets.containsKey(template.setId())) {
                throw new IllegalStateException("Hibás item template katalógus: " + template.templateId()
                        + ": ismeretlen set ID: " + template.setId());
            }
            validateRestrictions(template);
            if (template.isArmorFamilyEquipment()) {
                for (final EquipmentCatalogValidator.Finding finding
                        : EquipmentCatalogValidator.validate(template,
                        loadedProfiles.get(template.armorFamily()))) {
                    if (finding.severity() == EquipmentCatalogValidator.Severity.ERROR) {
                        throw new IllegalStateException(finding.templateId() + ": " + finding.detail());
                    }
                    plugin.getLogger().warning("Equipment 2.0 audit [" + finding.code() + "] "
                            + finding.templateId() + ": " + finding.detail());
                }
            }
        }
        validateSets(loaded, loadedSets);
        snapshot = new Snapshot(Map.copyOf(loaded), loadedSets, loadedProfiles);
        hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager,
                "Itemization 2.0: " + loaded.size() + " authored template és "
                        + loadedSets.size() + " set, valamint " + loadedProfiles.size()
                        + " Equipment 2.0 family profil betöltve.");
    }

    public Optional<ItemTemplate> find(final String templateId) {
        if (templateId == null || templateId.isBlank()) return Optional.empty();
        return Optional.ofNullable(snapshot.templates().get(ItemStatCatalog.normalizeId(templateId)));
    }

    public ItemTemplate require(final String templateId) {
        return find(templateId).orElseThrow(() ->
                new IllegalArgumentException("unknown item template: " + templateId));
    }

    public Map<String, ItemTemplate> snapshot() {
        return snapshot.templates();
    }

    public Optional<ItemSetDefinition> findSet(final String setId) {
        if (setId == null || setId.isBlank()) return Optional.empty();
        return Optional.ofNullable(snapshot.sets().get(ItemStatCatalog.normalizeId(setId)));
    }

    public ItemSetDefinition requireSet(final String setId) {
        return findSet(setId).orElseThrow(() -> new IllegalArgumentException("unknown item set: " + setId));
    }

    public Map<String, ItemSetDefinition> setSnapshot() {
        return snapshot.sets();
    }

    public ArmorFamilyProfile requireFamilyProfile(final ArmorFamily family) {
        final ArmorFamilyProfile profile = snapshot.familyProfiles().get(family);
        if (profile == null) throw new IllegalArgumentException("missing armor family profile: " + family);
        return profile;
    }

    public Map<ArmorFamily, ArmorFamilyProfile> familyProfileSnapshot() {
        return snapshot.familyProfiles();
    }

    private static Map<String, ItemSetDefinition> parseSets(final ConfigurationSection root) {
        if (root == null) return Map.of();
        final LinkedHashMap<String, ItemSetDefinition> result = new LinkedHashMap<>();
        for (final String rawId : root.getKeys(false)) {
            final ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) throw new IllegalArgumentException("hibás item set: " + rawId);
            final ConfigurationSection tiersSection = section.getConfigurationSection("tiers");
            if (tiersSection == null) throw new IllegalArgumentException("hiányzó item set tiers: " + rawId);
            final LinkedHashMap<Integer, Map<String, Double>> tiers = new LinkedHashMap<>();
            for (final String rawPieces : tiersSection.getKeys(false)) {
                final int pieces;
                try {
                    pieces = Integer.parseInt(rawPieces);
                } catch (final NumberFormatException invalid) {
                    throw new IllegalArgumentException("hibás item set darabszám: " + rawId + '/' + rawPieces,
                            invalid);
                }
                final ConfigurationSection stats = tiersSection.getConfigurationSection(rawPieces + ".fixed-stats");
                tiers.put(pieces, doubles(stats));
            }
            final ItemSetDefinition definition = new ItemSetDefinition(rawId,
                    section.getString("display-name", rawId), tiers);
            if (result.putIfAbsent(definition.setId(), definition) != null) {
                throw new IllegalArgumentException("duplikált item set ID: " + definition.setId());
            }
        }
        return Map.copyOf(result);
    }

    private static ItemTemplate parse(final String id, final ConfigurationSection section) {
        final String materialName = section.getString("material", "").toUpperCase(Locale.ROOT);
        final Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("ismeretlen material: " + materialName);
        }
        return new ItemTemplate(
                id,
                section.getInt("schema", ItemTemplate.CURRENT_SCHEMA),
                section.getInt("version", 1),
                section.getString("display-name", id),
                section.getStringList("lore"),
                ItemRarity.parse(section.getString("rarity", "kozonseges")),
                section.getInt("item-level", 1),
                enumValue(ItemTemplate.Family.class, section.getString("family", "other")),
                ArmorFamily.parse(section.getString("armor-family", "")),
                section.getString("family-exception", ""),
                enumValue(ItemTemplate.Slot.class, section.getString("slot", "none")),
                material.name(),
                section.getString("item-model", ""),
                section.getString("equipment-asset", ""),
                section.getInt("level-requirement", 0),
                ids(section.getStringList("class-restrictions")),
                ids(section.getStringList("specialization-restrictions")),
                ids(section.getStringList("profession-restrictions")),
                section.getDouble("base-damage", 0.0D),
                section.getDouble("base-armor", 0.0D),
                doubles(section.getConfigurationSection("fixed-stats")),
                ranges(section.getConfigurationSection("rolled-stats")),
                section.getInt("rune-sockets", 0),
                section.getString("signature-effect", ""),
                section.getString("set-id", ""),
                enumValue(ItemTemplate.BindPolicy.class, section.getString("bind-policy", "none")),
                enumValue(ItemTemplate.TradePolicy.class, section.getString("trade-policy", "tradeable")),
                enumValue(ItemTemplate.SalvagePolicy.class, section.getString("salvage-policy", "materials")),
                ids(section.getStringList("source-tags")),
                ids(section.getStringList("region-tags")),
                ids(section.getStringList("gathering-tags")),
                ids(section.getStringList("crafting-tags")),
                strings(section.getConfigurationSection("encounter-metadata")),
                section.getStringList("ascension-path"),
                ascensionStages(section.getConfigurationSection("ascension-stages")));
    }

    private static Map<ArmorFamily, ArmorFamilyProfile> parseFamilyProfiles(
            final ConfigurationSection root) {
        if (root == null) throw new IllegalArgumentException("missing Equipment 2.0 family profiles");
        final java.util.EnumMap<ArmorFamily, ArmorFamilyProfile> result =
                new java.util.EnumMap<>(ArmorFamily.class);
        for (final String raw : root.getKeys(false)) {
            final ArmorFamily family = ArmorFamily.parse(raw);
            final ConfigurationSection section = root.getConfigurationSection(raw);
            if (section == null) throw new IllegalArgumentException("invalid armor family profile: " + raw);
            final ArmorFamilyProfile profile = new ArmorFamilyProfile(family,
                    section.getDouble("base-armor-coefficient"),
                    section.getDouble("budget.offensive"),
                    section.getDouble("budget.defensive"),
                    section.getDouble("budget.utility"),
                    ids(section.getStringList("preferred-stats")),
                    ids(section.getStringList("disfavored-stats")));
            if (result.putIfAbsent(family, profile) != null) {
                throw new IllegalArgumentException("duplicate armor family profile: " + family);
            }
        }
        if (result.size() != ArmorFamily.values().length) {
            throw new IllegalArgumentException("all four armor family profiles are required");
        }
        return Map.copyOf(result);
    }

    private static void validateRestrictions(final ItemTemplate template) {
        for (final String classId : template.classRestrictions()) {
            final JobType job = JobType.fromId(classId);
            if (job == null) throw new IllegalArgumentException(template.templateId()
                    + ": unknown class restriction: " + classId);
            if (template.isArmorFamilyEquipment()
                    && EquipmentProficiencyPolicy.familyOf(job) != template.armorFamily()) {
                throw new IllegalArgumentException(template.templateId()
                        + ": class restriction conflicts with armor family: " + classId);
            }
        }
        for (final String specId : template.specializationRestrictions()) {
            final SpecializationType specialization = SpecializationType.fromId(specId);
            if (specialization == null) throw new IllegalArgumentException(template.templateId()
                    + ": unknown specialization restriction: " + specId);
            if (!template.classRestrictions().isEmpty()
                    && !template.classRestrictions().contains(specialization.getParentJob().getId())) {
                throw new IllegalArgumentException(template.templateId()
                        + ": specialization restriction is outside the class restriction: " + specId);
            }
            if (template.isArmorFamilyEquipment()
                    && EquipmentProficiencyPolicy.familyOf(specialization.getParentJob())
                    != template.armorFamily()) {
                throw new IllegalArgumentException(template.templateId()
                        + ": specialization restriction conflicts with armor family: " + specId);
            }
        }
    }

    private static void validateSets(final Map<String, ItemTemplate> templates,
                                     final Map<String, ItemSetDefinition> sets) {
        for (final String setId : sets.keySet()) {
            final Set<ArmorFamily> armorFamilies = templates.values().stream()
                    .filter(template -> setId.equals(template.setId()) && template.isArmorFamilyEquipment())
                    .map(ItemTemplate::armorFamily).collect(java.util.stream.Collectors.toSet());
            if (armorFamilies.size() > 1) {
                throw new IllegalArgumentException("mixed armor-family set is forbidden: " + setId);
            }
        }
    }

    private static Map<String, Double> doubles(final ConfigurationSection section) {
        if (section == null) return Map.of();
        final LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (final String key : section.getKeys(false)) result.put(key, section.getDouble(key));
        return result;
    }

    private static Map<String, ItemTemplate.StatRange> ranges(final ConfigurationSection section) {
        if (section == null) return Map.of();
        final LinkedHashMap<String, ItemTemplate.StatRange> result = new LinkedHashMap<>();
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection range = section.getConfigurationSection(key);
            if (range == null) throw new IllegalArgumentException("hibás stat range: " + key);
            result.put(key, new ItemTemplate.StatRange(range.getDouble("min"), range.getDouble("max")));
        }
        return result;
    }

    private static Map<String, ItemTemplate.AscensionStage> ascensionStages(
            final ConfigurationSection section) {
        if (section == null) return Map.of();
        final LinkedHashMap<String, ItemTemplate.AscensionStage> result = new LinkedHashMap<>();
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection stage = section.getConfigurationSection(id);
            if (stage == null) throw new IllegalArgumentException("hibás ascension stage: " + id);
            result.put(id, new ItemTemplate.AscensionStage(id,
                    stage.getInt("item-level"), stage.getInt("level-requirement", 0),
                    doubles(stage.getConfigurationSection("fixed-stats")),
                    ranges(stage.getConfigurationSection("rolled-stats")),
                    stage.getInt("rune-sockets", 0), stage.getInt("signature-tier", 0),
                    stage.getString("item-model", ""), stage.getString("equipment-asset", ""),
                    stage.getStringList("lore")));
        }
        return result;
    }

    private static Map<String, String> strings(final ConfigurationSection section) {
        if (section == null) return Map.of();
        final LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (final String key : section.getKeys(false)) result.put(key, section.getString(key, ""));
        return result;
    }

    private static Set<String> ids(final List<String> values) {
        return values == null || values.isEmpty() ? Set.of() : new LinkedHashSet<>(values);
    }

    private static <T extends Enum<T>> T enumValue(final Class<T> type, final String raw) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (final RuntimeException invalid) {
            throw new IllegalArgumentException("invalid " + type.getSimpleName() + ": " + raw, invalid);
        }
    }
}
