package hu.taliann.icesmp.pve;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Canonical authored mob definition. Vanilla wilderness mobs may still use the fallback path. */
public record MobTemplate(String mobId, int schemaVersion, String displayName, String entityType,
                          String modelId, int minimumLevel, int maximumLevel, MobRank rank,
                          MobArchetype archetype, StatProfile stats, List<String> abilityIds,
                          Set<String> resistances, Set<String> weaknesses, String lootProfile,
                          Set<String> sourceTags, String spawnPolicy, String bestiaryId,
                          List<EliteAffix> affixPool, MobBehaviorProfile behavior,
                          MobNaturalContext naturalContext,
                          Map<MobRank, List<String>> rankAbilities,
                          String bestiarySummary, String counterplayHint) {
    public static final int CURRENT_SCHEMA = 2;

    public record StatProfile(double healthMultiplier, double damageMultiplier,
                              double movementMultiplier, double crowdControlResistance) {
        public StatProfile {
            // Authored bosses can intentionally project a high-vanilla-HP carrier (for example a
            // Warden) down to the encounter baseline before level/rank scaling. This is a bounded
            // normalization layer, not a second raw-attribute owner.
            if (!bounded(healthMultiplier, 0.02D, 20.0D)
                    || !bounded(damageMultiplier, 0.1D, 10.0D)
                    || !bounded(movementMultiplier, 0.25D, 3.0D)
                    || !bounded(crowdControlResistance, 0.0D, 1.0D)) {
                throw new IllegalArgumentException("invalid mob stat profile");
            }
        }

        public static StatProfile baseline() {
            return new StatProfile(1.0D, 1.0D, 1.0D, 0.0D);
        }
    }

    public MobTemplate {
        mobId = MobAbilityDefinition.id(mobId, "mob id");
        if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("unsupported mob schema");
        }
        displayName = requireText(displayName, "display name", 128);
        entityType = requireText(entityType, "entity type", 64).toUpperCase(Locale.ROOT);
        modelId = Objects.requireNonNullElse(modelId, "").trim();
        if (minimumLevel < 1 || maximumLevel < minimumLevel || maximumLevel > 200) {
            throw new IllegalArgumentException("invalid authored mob level range");
        }
        rank = Objects.requireNonNull(rank, "rank");
        archetype = Objects.requireNonNull(archetype, "archetype");
        stats = Objects.requireNonNullElse(stats, StatProfile.baseline());
        abilityIds = ids(abilityIds, 8, "ability");
        resistances = idSet(resistances, 16, "resistance");
        weaknesses = idSet(weaknesses, 16, "weakness");
        if (!java.util.Collections.disjoint(resistances, weaknesses)) {
            throw new IllegalArgumentException("mob resistance and weakness overlap");
        }
        lootProfile = MobAbilityDefinition.id(lootProfile, "loot profile");
        sourceTags = idSet(sourceTags, 16, "source tag");
        spawnPolicy = MobAbilityDefinition.id(spawnPolicy, "spawn policy");
        bestiaryId = MobAbilityDefinition.id(bestiaryId, "bestiary id");
        final LinkedHashSet<EliteAffix> pool = new LinkedHashSet<>(
                affixPool == null ? List.of() : affixPool);
        if (pool.size() != (affixPool == null ? 0 : affixPool.size()) || pool.size() > 7) {
            throw new IllegalArgumentException("invalid elite affix pool");
        }
        affixPool = List.copyOf(pool);
        behavior = Objects.requireNonNullElseGet(behavior,
                () -> MobBehaviorProfile.defaults(archetype));
        naturalContext = Objects.requireNonNullElse(naturalContext, MobNaturalContext.none());
        final java.util.EnumMap<MobRank, List<String>> unlocks =
                new java.util.EnumMap<>(MobRank.class);
        if (rankAbilities != null) rankAbilities.forEach((unlockRank, values) -> {
            if (unlockRank == null) throw new IllegalArgumentException("rank ability rank required");
            unlocks.put(unlockRank, ids(values, 5, "rank ability"));
        });
        rankAbilities = Map.copyOf(unlocks);
        bestiarySummary = requireText(bestiarySummary == null || bestiarySummary.isBlank()
                ? displayName : bestiarySummary, "bestiary summary", 220);
        counterplayHint = requireText(counterplayHint == null || counterplayHint.isBlank()
                ? "Figyeld a támadás előjelét." : counterplayHint, "counterplay hint", 180);
    }

    /** Compatibility constructor retained for focused fixtures and legacy adapters. */
    public MobTemplate(final String mobId, final int schemaVersion, final String displayName,
                       final String entityType, final String modelId, final int minimumLevel,
                       final int maximumLevel, final MobRank rank, final MobArchetype archetype,
                       final StatProfile stats, final List<String> abilityIds,
                       final Set<String> resistances, final Set<String> weaknesses,
                       final String lootProfile, final Set<String> sourceTags,
                       final String spawnPolicy, final String bestiaryId,
                       final List<EliteAffix> affixPool) {
        this(mobId, schemaVersion, displayName, entityType, modelId, minimumLevel,
                maximumLevel, rank, archetype, stats, abilityIds, resistances, weaknesses,
                lootProfile, sourceTags, spawnPolicy, bestiaryId, affixPool,
                MobBehaviorProfile.defaults(archetype), MobNaturalContext.none(), Map.of(),
                displayName, "Figyeld a támadás előjelét.");
    }

    public int levelAt(final double normalizedQuality) {
        final double quality = Math.max(0.0D, Math.min(1.0D,
                Double.isFinite(normalizedQuality) ? normalizedQuality : 0.0D));
        return minimumLevel + (int) Math.round((maximumLevel - minimumLevel) * quality);
    }

    public int levelForBaseline(final int localBaseline) {
        return Math.max(minimumLevel, Math.min(maximumLevel,
                localBaseline + naturalContext.levelOffset()));
    }

    public boolean levelSuitable(final int localLevel) {
        return localLevel >= Math.max(1, minimumLevel - 6)
                && localLevel <= Math.min(200, maximumLevel + 6);
    }

    public List<String> abilityIdsFor(final MobRank effectiveRank) {
        final LinkedHashSet<String> result = new LinkedHashSet<>(abilityIds);
        for (final MobRank unlock : MobRank.values()) {
            if (unlock.ordinal() <= effectiveRank.ordinal()) {
                result.addAll(rankAbilities.getOrDefault(unlock, List.of()));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> ids(final List<String> source, final int maximum,
                                    final String field) {
        if (source == null || source.isEmpty()) return List.of();
        final LinkedHashSet<String> values = new LinkedHashSet<>();
        for (final String value : source) values.add(MobAbilityDefinition.id(value, field));
        if (values.size() != source.size() || values.size() > maximum) {
            throw new IllegalArgumentException("invalid " + field + " list");
        }
        return List.copyOf(values);
    }

    private static Set<String> idSet(final Set<String> source, final int maximum,
                                     final String field) {
        if (source == null || source.isEmpty()) return Set.of();
        final LinkedHashSet<String> values = new LinkedHashSet<>();
        for (final String value : source) values.add(MobAbilityDefinition.id(value, field));
        if (values.size() > maximum) throw new IllegalArgumentException("too many " + field + " values");
        return Set.copyOf(values);
    }

    private static String requireText(final String raw, final String field, final int maximum) {
        final String value = Objects.requireNonNull(raw, field).trim();
        if (value.isBlank() || value.length() > maximum) throw new IllegalArgumentException(field + " invalid");
        return value;
    }

    private static boolean bounded(final double value, final double minimum, final double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }
}
