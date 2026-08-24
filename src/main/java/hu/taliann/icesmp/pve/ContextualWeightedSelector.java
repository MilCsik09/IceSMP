package hu.taliann.icesmp.pve;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pure bounded stable weighted choice used after natural-template eligibility/scoring. */
public final class ContextualWeightedSelector {
    public record Candidate<T>(String stableId, T value, double weight) {
        public Candidate {
            stableId = Objects.requireNonNull(stableId, "stableId").trim();
            value = Objects.requireNonNull(value, "value");
            if (stableId.isBlank() || !Double.isFinite(weight) || weight <= 0.0D
                    || weight > 10_000.0D) {
                throw new IllegalArgumentException("invalid weighted candidate");
            }
        }
    }

    private ContextualWeightedSelector() { }

    public static <T> T select(final List<Candidate<T>> rawCandidates,
                               final UUID identity, final long contextSeed) {
        if (rawCandidates == null || rawCandidates.isEmpty() || rawCandidates.size() > 256) {
            throw new IllegalArgumentException("weighted selection requires 1-256 candidates");
        }
        final List<Candidate<T>> candidates = rawCandidates.stream()
                .sorted(Comparator.comparing(Candidate::stableId)).toList();
        final HashSet<String> ids = new HashSet<>();
        double total = 0.0D;
        for (final Candidate<T> candidate : candidates) {
            if (!ids.add(candidate.stableId())) {
                throw new IllegalArgumentException("duplicate weighted candidate id");
            }
            total += candidate.weight();
        }
        if (!Double.isFinite(total) || total <= 0.0D) {
            throw new IllegalArgumentException("invalid weighted total");
        }
        final UUID stableIdentity = identity == null ? new UUID(0L, 0L) : identity;
        final long seed = mix(stableIdentity.getMostSignificantBits()
                ^ stableIdentity.getLeastSignificantBits() ^ contextSeed);
        double cursor = ((seed >>> 11) * 0x1.0p-53) * total;
        for (final Candidate<T> candidate : candidates) {
            cursor -= candidate.weight();
            if (cursor < 0.0D) return candidate.value();
        }
        return candidates.getLast().value();
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }
}
