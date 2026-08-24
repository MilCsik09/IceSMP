package hu.taliann.icesmp.pve;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Data-only natural variant eligibility and affinity projection. */
public record MobNaturalContext(double relativeWeight, Set<String> requiredTags,
                                Set<String> excludedTags, Map<String, Double> affinities,
                                int levelOffset, boolean noDaylightBurn) {
    public MobNaturalContext {
        if (!Double.isFinite(relativeWeight) || relativeWeight <= 0.0D || relativeWeight > 100.0D
                || levelOffset < -12 || levelOffset > 12) {
            throw new IllegalArgumentException("invalid natural context bounds");
        }
        requiredTags = normalized(requiredTags, 16);
        excludedTags = normalized(excludedTags, 16);
        if (!java.util.Collections.disjoint(requiredTags, excludedTags)) {
            throw new IllegalArgumentException("natural context required/excluded overlap");
        }
        final LinkedHashMap<String, Double> safeAffinities = new LinkedHashMap<>();
        if (affinities != null) affinities.forEach((raw, value) -> {
            final String tag = normalize(raw);
            if (value == null || !Double.isFinite(value) || value < 0.1D || value > 8.0D) {
                throw new IllegalArgumentException("invalid natural context affinity: " + tag);
            }
            safeAffinities.put(tag, value);
        });
        if (safeAffinities.size() > 24) throw new IllegalArgumentException("too many context affinities");
        affinities = Map.copyOf(safeAffinities);
    }

    public static MobNaturalContext none() {
        return new MobNaturalContext(1.0D, Set.of(), Set.of(), Map.of(), 0, false);
    }

    public boolean eligible(final Set<String> contextTags) {
        return contextTags.containsAll(requiredTags)
                && excludedTags.stream().noneMatch(contextTags::contains);
    }

    public double effectiveWeight(final Set<String> contextTags) {
        double result = relativeWeight;
        for (final var affinity : affinities.entrySet()) {
            if (contextTags.contains(affinity.getKey())) result *= affinity.getValue();
        }
        return Math.min(10_000.0D, result);
    }

    private static Set<String> normalized(final Set<String> source, final int maximum) {
        if (source == null || source.isEmpty()) return Set.of();
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        source.forEach(raw -> result.add(normalize(raw)));
        if (result.size() > maximum) throw new IllegalArgumentException("too many natural context tags");
        return Set.copyOf(result);
    }

    private static String normalize(final String raw) {
        final String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_');
        if (value.isBlank() || value.length() > 96) throw new IllegalArgumentException("invalid natural context tag");
        return value;
    }
}
