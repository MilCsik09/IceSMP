package hu.taliann.icesmp.trash;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

/** Restart-only, Git-authored acquisition rates and bounded ambient timing. */
public record TrashLootTuning(
        Map<TrashLootSource, Double> sourceChances,
        Map<TrashKind, Double> categoryWeights,
        double sourceAffinityMultiplier,
        double contextAffinityMultiplier,
        double displacedChance,
        double recycleSubstitutionChance,
        Ambient ambient
) {
    private static final double EPSILON = 0.000_001D;

    public TrashLootTuning {
        sourceChances = Map.copyOf(sourceChances);
        categoryWeights = Map.copyOf(categoryWeights);
        if (sourceChances.size() != TrashLootSource.values().length) {
            throw new IllegalArgumentException("minden Trash source chance kötelező");
        }
        if (categoryWeights.size() != TrashKind.values().length) {
            throw new IllegalArgumentException("minden Trash category weight kötelező");
        }
        sourceChances.values().forEach(value -> probability(value, "source chance"));
        probability(displacedChance, "displaced chance");
        probability(recycleSubstitutionChance, "recycle substitution chance");
        if (!Double.isFinite(sourceAffinityMultiplier) || sourceAffinityMultiplier < 1.0D) {
            throw new IllegalArgumentException("source affinity multiplier legalább 1 lehet");
        }
        if (!Double.isFinite(contextAffinityMultiplier) || contextAffinityMultiplier < 1.0D) {
            throw new IllegalArgumentException("context affinity multiplier legalább 1 lehet");
        }
        final double total = categoryWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - 100.0D) > EPSILON
                || categoryWeights.values().stream().anyMatch(value -> !Double.isFinite(value) || value <= 0.0D)) {
            throw new IllegalArgumentException("a category weightek pozitívak és összesen pontosan 100 legyenek");
        }
        if (ambient == null) throw new IllegalArgumentException("hiányzó ambient tuning");
    }

    public double chance(final TrashLootSource source) {
        return sourceChances.getOrDefault(source, 0.0D);
    }

    public double categoryWeight(final TrashKind kind) {
        return categoryWeights.getOrDefault(kind, 0.0D);
    }

    static TrashLootTuning parse(final ConfigurationSection section) {
        if (section == null) throw new IllegalArgumentException("hiányzó loot-ecology section");
        final EnumMap<TrashLootSource, Double> sourceChances = new EnumMap<>(TrashLootSource.class);
        for (final TrashLootSource source : TrashLootSource.values()) {
            sourceChances.put(source, requiredDouble(section,
                    "sources." + source.name().toLowerCase(java.util.Locale.ROOT) + ".chance"));
        }
        final EnumMap<TrashKind, Double> categoryWeights = new EnumMap<>(TrashKind.class);
        for (final TrashKind kind : TrashKind.values()) {
            categoryWeights.put(kind, requiredDouble(section,
                    "category-weights." + kind.name().toLowerCase(java.util.Locale.ROOT)));
        }
        final Ambient ambient = new Ambient(
                requiredInt(section, "ambient.attempt-min-seconds"),
                requiredInt(section, "ambient.attempt-max-seconds"),
                requiredInt(section, "ambient.distance-min-blocks"),
                requiredInt(section, "ambient.distance-max-blocks"),
                requiredInt(section, "ambient.ttl-min-seconds"),
                requiredInt(section, "ambient.ttl-max-seconds"),
                requiredInt(section, "ambient.max-per-chunk"),
                requiredInt(section, "ambient.max-per-neighborhood"));
        return new TrashLootTuning(sourceChances, categoryWeights,
                requiredDouble(section, "source-affinity-multiplier"),
                requiredDouble(section, "context-affinity-multiplier"),
                requiredDouble(section, "displaced-chance"),
                requiredDouble(section, "recycle-substitution-chance"), ambient);
    }

    private static double requiredDouble(final ConfigurationSection section, final String path) {
        if (!section.isSet(path)) throw new IllegalArgumentException("hiányzó loot tuning: " + path);
        final double value = section.getDouble(path, Double.NaN);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("hibás loot tuning: " + path);
        return value;
    }

    private static int requiredInt(final ConfigurationSection section, final String path) {
        if (!section.isInt(path)) throw new IllegalArgumentException("hiányzó/hibás loot tuning: " + path);
        return section.getInt(path);
    }

    private static void probability(final double value, final String name) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(name + " csak 0..1 lehet");
        }
    }

    public record Ambient(int attemptMinSeconds, int attemptMaxSeconds,
                          int distanceMinBlocks,
                          int distanceMaxBlocks, int ttlMinSeconds, int ttlMaxSeconds,
                          int maxPerChunk, int maxPerNeighborhood) {
        public Ambient {
            if (attemptMinSeconds < 1 || attemptMaxSeconds < attemptMinSeconds) {
                throw new IllegalArgumentException("hibás ambient attempt intervallum");
            }
            if (distanceMinBlocks < 1 || distanceMaxBlocks < distanceMinBlocks) {
                throw new IllegalArgumentException("hibás ambient distance tartomány");
            }
            if (ttlMinSeconds < 1 || ttlMaxSeconds < ttlMinSeconds) {
                throw new IllegalArgumentException("hibás ambient TTL tartomány");
            }
            if (maxPerChunk < 1 || maxPerChunk > 16
                    || maxPerNeighborhood < maxPerChunk || maxPerNeighborhood > 32) {
                throw new IllegalArgumentException("hibás ambient density cap");
            }
        }
    }
}
