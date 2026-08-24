package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Canonical authored MobTemplate catalog with vanilla-wilderness fallback. */
public final class MobTemplateRegistry {
    private static final Set<String> SPAWN_POLICIES = Set.of(
            "natural", "authored", "natural_or_authored", "event", "boss");

    private final ConfigManager config;
    private final MobAbilityRegistry abilities;
    private volatile Map<String, MobTemplate> templates = Map.of();
    private volatile Map<EntityType, List<MobTemplate>> byEntityType = Map.of();

    public MobTemplateRegistry(final ConfigManager config, final MobAbilityRegistry abilities) {
        this.config = Objects.requireNonNull(config, "config");
        this.abilities = Objects.requireNonNull(abilities, "abilities");
    }

    public synchronized void load() {
        final ConfigurationSection root = config.getConfiguration()
                .getConfigurationSection("mob-templates");
        if (root == null) throw new IllegalStateException("mob-templates config section missing");
        final ConfigurationSection lootRoot = config.getConfiguration()
                .getConfigurationSection("mob-loot-profiles");
        if (lootRoot == null) throw new IllegalStateException("mob-loot-profiles section missing");
        final Set<String> lootProfiles = parseLootProfileReferences(lootRoot);
        final LinkedHashMap<String, MobTemplate> parsed = new LinkedHashMap<>();
        final LinkedHashMap<EntityType, List<MobTemplate>> entityIndex = new LinkedHashMap<>();
        final LinkedHashSet<String> bestiaryIds = new LinkedHashSet<>();
        for (final String rawId : root.getKeys(false)) {
            final String id = normalize(rawId);
            if (parsed.containsKey(id)) throw new IllegalStateException("duplicate MobTemplate: " + id);
            final ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) throw new IllegalStateException("invalid MobTemplate section: " + id);
            final EntityType entityType;
            try {
                entityType = EntityType.valueOf(section.getString("entity-type", "")
                        .trim().toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid MobTemplate entity type: " + id, invalid);
            }
            if (!entityType.isSpawnable() || !entityType.isAlive()) {
                throw new IllegalStateException("MobTemplate entity is not a living spawn type: " + id);
            }
            final ConfigurationSection stats = section.getConfigurationSection("stats");
            if (stats == null) throw new IllegalStateException("MobTemplate stats missing: " + id);
            final List<String> abilityIds = section.getStringList("abilities");
            for (final String abilityId : abilityIds) abilities.require(abilityId);
            final String lootProfile = normalize(section.getString("loot-profile", ""));
            if (!lootProfiles.contains(lootProfile)) {
                throw new IllegalStateException("unknown MobTemplate loot profile: " + id + '/' + lootProfile);
            }
            final String spawnPolicy = normalize(section.getString("spawn-policy", ""));
            if (!SPAWN_POLICIES.contains(spawnPolicy)) {
                throw new IllegalStateException("invalid MobTemplate spawn policy: " + id + '/' + spawnPolicy);
            }
            final ArrayList<EliteAffix> affixes = new ArrayList<>();
            for (final String raw : section.getStringList("affix-pool")) affixes.add(EliteAffix.parse(raw));
            final MobArchetype archetype = MobArchetype.parse(section.getString("archetype", ""));
            final Map<MobRank, List<String>> rankAbilities = rankAbilities(section, id);
            rankAbilities.values().forEach(ids -> ids.forEach(abilities::require));
            final Set<String> sourceTags = normalizedSet(section.getStringList("source-tags"));
            final MobTemplate template = new MobTemplate(id,
                    section.getInt("schema-version", 0), section.getString("display-name", ""),
                    entityType.name(), section.getString("model", ""),
                    section.getInt("level.minimum", 0), section.getInt("level.maximum", 0),
                    MobRank.parse(section.getString("rank", "")),
                    archetype,
                    new MobTemplate.StatProfile(stats.getDouble("health-multiplier", 1.0D),
                            stats.getDouble("damage-multiplier", 1.0D),
                            stats.getDouble("movement-multiplier", 1.0D),
                            stats.getDouble("cc-resistance", 0.0D)),
                    abilityIds, normalizedSet(section.getStringList("resistances")),
                    normalizedSet(section.getStringList("weaknesses")), lootProfile,
                    sourceTags, spawnPolicy,
                    section.getString("bestiary-id", ""), affixes,
                    behavior(section.getConfigurationSection("behavior"), archetype),
                    naturalContext(section.getConfigurationSection("natural-context"), sourceTags),
                    rankAbilities, section.getString("bestiary-summary", ""),
                    section.getString("counterplay-hint", ""));
            if (!bestiaryIds.add(template.bestiaryId())) {
                throw new IllegalStateException("duplicate MobTemplate bestiary id: " + template.bestiaryId());
            }
            parsed.put(id, template);
            entityIndex.computeIfAbsent(entityType, ignored -> new ArrayList<>()).add(template);
        }
        if (parsed.size() < 4 || parsed.size() > 256) {
            throw new IllegalStateException("MobTemplate registry must contain 4-256 entries");
        }
        final LinkedHashMap<EntityType, List<MobTemplate>> immutableIndex = new LinkedHashMap<>();
        entityIndex.forEach((type, values) -> immutableIndex.put(type, List.copyOf(values)));
        templates = Map.copyOf(parsed);
        byEntityType = Map.copyOf(immutableIndex);
    }

    public Optional<MobTemplate> find(final String mobId) {
        if (mobId == null || mobId.isBlank()) return Optional.empty();
        return Optional.ofNullable(templates.get(normalize(mobId)));
    }

    public MobTemplate require(final String mobId) {
        return find(mobId).orElseThrow(() -> new IllegalArgumentException("unknown MobTemplate: " + mobId));
    }

    /** Returns the most specific natural template; empty means canonical vanilla fallback. */
    public Optional<MobTemplate> naturalTemplate(final EntityType type,
                                                 final NamespacedKey biomeKey) {
        return naturalTemplate(type, biomeKey, Set.of());
    }

    /** Context tags make dimension/depth/night/weather profiles data-driven without authored zones. */
    public Optional<MobTemplate> naturalTemplate(final EntityType type,
                                                 final NamespacedKey biomeKey,
                                                 final Set<String> rawContextTags) {
        return naturalTemplate(type, biomeKey, rawContextTags, new UUID(0L, 0L), 1);
    }

    /**
     * Eligibility -> contextual affinity -> stable weighted selection. The entity UUID keeps a
     * natural identity stable across target changes and reloads without persistent world state.
     */
    public Optional<MobTemplate> naturalTemplate(final EntityType type,
                                                 final NamespacedKey biomeKey,
                                                 final Set<String> rawContextTags,
                                                 final UUID entityId,
                                                 final int localLevel) {
        final List<MobTemplate> candidates = byEntityType.getOrDefault(type, List.of()).stream()
                .filter(template -> template.spawnPolicy().equals("natural")
                        || template.spawnPolicy().equals("natural_or_authored"))
                .toList();
        if (candidates.isEmpty()) return Optional.empty();
        final String biomeTag = biomeKey == null ? "" : "biome:" + biomeKey.getKey();
        final LinkedHashSet<String> contextTags = new LinkedHashSet<>();
        if (!biomeTag.isBlank()) contextTags.add(normalize(biomeTag));
        if (rawContextTags != null) rawContextTags.forEach(tag -> contextTags.add(normalize(tag)));
        final List<MobTemplate> eligible = candidates.stream()
                .filter(template -> template.naturalContext().eligible(contextTags))
                .filter(template -> template.levelSuitable(localLevel))
                .sorted(java.util.Comparator.comparing(MobTemplate::mobId))
                .toList();
        if (eligible.isEmpty()) return Optional.empty();
        final List<ContextualWeightedSelector.Candidate<MobTemplate>> weighted = eligible.stream()
                .map(template -> new ContextualWeightedSelector.Candidate<>(template.mobId(), template,
                        template.naturalContext().effectiveWeight(contextTags)))
                .toList();
        return Optional.of(ContextualWeightedSelector.select(weighted, entityId,
                type.name().hashCode() ^ contextTags.hashCode()));
    }

    public Map<String, MobTemplate> all() { return templates; }

    private Map<MobRank, List<String>> rankAbilities(final ConfigurationSection section,
                                                      final String templateId) {
        final ConfigurationSection root = section.getConfigurationSection("rank-abilities");
        if (root == null) return Map.of();
        final java.util.EnumMap<MobRank, List<String>> result = new java.util.EnumMap<>(MobRank.class);
        for (final String rawRank : root.getKeys(false)) {
            final MobRank rank;
            try {
                rank = MobRank.parse(rawRank);
            } catch (final IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid template rank ability: "
                        + templateId + '/' + rawRank, invalid);
            }
            result.put(rank, root.getStringList(rawRank).stream().map(MobTemplateRegistry::normalize).toList());
        }
        return Map.copyOf(result);
    }

    private static MobBehaviorProfile behavior(final ConfigurationSection section,
                                               final MobArchetype archetype) {
        final MobBehaviorProfile fallback = MobBehaviorProfile.defaults(archetype);
        if (section == null) return fallback;
        return new MobBehaviorProfile(
                section.getDouble("preferred-range", fallback.preferredRange()),
                section.getDouble("minimum-comfort-range", fallback.minimumComfortRange()),
                section.getDouble("maximum-pursuit-range", fallback.maximumPursuitRange()),
                section.getDouble("retreat-tendency", fallback.retreatTendency()),
                section.getDouble("reposition-tendency", fallback.repositionTendency()),
                section.getDouble("strafe-tendency", fallback.strafeTendency()),
                section.getDouble("aggression-cadence", fallback.aggressionCadence()),
                section.getDouble("chase-pressure", fallback.chasePressure()));
    }

    private static MobNaturalContext naturalContext(final ConfigurationSection section,
                                                    final Set<String> sourceTags) {
        if (section == null) {
            final Set<String> contextual = sourceTags.stream().filter(tag ->
                    tag.startsWith("biome:") || tag.startsWith("dimension:")
                            || tag.startsWith("depth:") || tag.startsWith("time:")
                            || tag.startsWith("weather:") || tag.startsWith("territory:"))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new MobNaturalContext(1.0D, contextual, Set.of(), Map.of(), 0, false);
        }
        final LinkedHashMap<String, Double> affinities = new LinkedHashMap<>();
        final ConfigurationSection affinitySection = section.getConfigurationSection("affinities");
        if (affinitySection != null) {
            for (final String raw : affinitySection.getKeys(false)) {
                affinities.put(raw, affinitySection.getDouble(raw));
            }
        }
        return new MobNaturalContext(section.getDouble("weight", 1.0D),
                normalizedSet(section.getStringList("required")),
                normalizedSet(section.getStringList("excluded")), affinities,
                section.getInt("level-offset", 0),
                section.getBoolean("no-daylight-burn", false));
    }

    /**
     * `profile-id` is the migration/deprecation marker for the old mob-loot-profiles authoring
     * layer. Bukkit's YAML loader does not preserve null tombstones, so packaged legacy
     * sources/rewards leaves may still be visible after the overlay merge. Once a profile carries
     * the explicit marker those leaves are intentionally ignored and can no longer influence any
     * reward decision; BuildAware loot + rank policy remain the only runtime reward authority.
     * Unmigrated profiles fail closed instead of silently keeping the old duplicate truth source.
     */
    private static Set<String> parseLootProfileReferences(final ConfigurationSection root) {
        final LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (final String rawId : root.getKeys(false)) {
            final String id = normalize(rawId);
            final ConfigurationSection profile = root.getConfigurationSection(rawId);
            if (profile == null) throw new IllegalStateException("invalid mob loot profile reference: " + id);
            if (!profile.isSet("profile-id")) {
                throw new IllegalStateException("mob-loot-profiles entry is not migrated to reference-only authority: " + id);
            }
            final String marker = normalize(profile.getString("profile-id", ""));
            if (!id.equals(marker)) {
                throw new IllegalStateException("mob loot profile marker mismatch: " + id + '/' + marker);
            }
            if (!ids.add(id)) throw new IllegalStateException("duplicate mob loot profile reference: " + id);
        }
        return Set.copyOf(ids);
    }

    private static Set<String> normalizedSet(final List<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final String value : values) result.add(normalize(value));
        return Set.copyOf(result);
    }

    private static String normalize(final String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
