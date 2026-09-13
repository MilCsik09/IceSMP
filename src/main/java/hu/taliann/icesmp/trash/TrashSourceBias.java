package hu.taliann.icesmp.trash;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Parsed, immutable form of the compact authored source-bias notation. */
public record TrashSourceBias(String authored, Set<String> affinities) {

    private static final Set<String> ALLOWED = Set.of(
            "FISH", "MOB", "AMBIENT", "WET", "COLD", "HOT", "DEEP", "UNDERGROUND",
            "NETHER", "OPEN_SKY", "UNDEAD", "HUMANOID", "DARK");

    public TrashSourceBias {
        if (authored == null || authored.isBlank()) {
            throw new IllegalArgumentException("hiányzó source-bias");
        }
        affinities = Set.copyOf(affinities);
    }

    static TrashSourceBias parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("hiányzó source-bias");
        }
        final String authored = raw.trim().toUpperCase(Locale.ROOT);
        final String normalized = authored.replace("↑", "");
        final LinkedHashSet<String> affinities = new LinkedHashSet<>();
        for (final String part : normalized.split("\\+")) {
            final String token = part.trim();
            if (token.isEmpty() || "GLOBAL".equals(token)) continue;
            if (!ALLOWED.contains(token)) {
                throw new IllegalArgumentException("ismeretlen source-bias token: " + token);
            }
            affinities.add(token);
        }
        return new TrashSourceBias(authored, affinities);
    }

    double weight(final TrashLootSource source, final Set<TrashContext> contexts,
                  final boolean displaced, final TrashLootTuning tuning) {
        double weight = 1.0D;
        if (affinities.contains(source.affinityToken())) {
            weight *= tuning.sourceAffinityMultiplier();
        }
        if (!displaced) {
            for (final TrashContext context : contexts) {
                if (affinities.contains(context.name())) {
                    weight *= tuning.contextAffinityMultiplier();
                }
            }
        }
        return weight;
    }

    @Override
    public String toString() {
        return authored;
    }
}
