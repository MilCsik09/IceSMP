package hu.taliann.icesmp.pve;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Canonical authored mob definition. Vanilla wilderness mobs may still use the fallback path. */
public record MobTemplate(String mobId, int schemaVersion, String displayName, String entityType,
                          String modelId, int minimumLevel, int maximumLevel, MobRank rank,
                          MobArchetype archetype, StatProfile stats, List<String> abilityIds,
                          Set<String> resistances, Set<String> weaknesses, String lootProfile,
                          Set<String> sourceTags, String spawnPolicy, String bestiaryId,
                          List<EliteAffix> affixPool) {
    public static final int CURRENT_SCHEMA = 1;

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
        if (schemaVersion != CURRENT_SCHEMA) throw new IllegalArgumentException("unsupported mob schema");
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
    }

    public int levelAt(final double normalizedQuality) {
        final double quality = Math.max(0.0D, Math.min(1.0D,
                Double.isFinite(normalizedQuality) ? normalizedQuality : 0.0D));
        return minimumLevel + (int) Math.round((maximumLevel - minimumLevel) * quality);
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
