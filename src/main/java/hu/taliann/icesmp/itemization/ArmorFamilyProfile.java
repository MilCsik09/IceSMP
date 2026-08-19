package hu.taliann.icesmp.itemization;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Validated family-level budget distribution, not an item-level power ladder. */
public record ArmorFamilyProfile(
        ArmorFamily family,
        double baseArmorCoefficient,
        double offensiveShare,
        double defensiveShare,
        double utilityShare,
        Set<String> preferredStats,
        Set<String> disfavoredStats
) {
    private static final double SHARE_TOLERANCE = 0.000_001D;

    public ArmorFamilyProfile {
        Objects.requireNonNull(family, "family");
        if (!Double.isFinite(baseArmorCoefficient) || baseArmorCoefficient <= 0.0D
                || baseArmorCoefficient > 4.0D) {
            throw new IllegalArgumentException("invalid base armor coefficient for " + family);
        }
        requireShare(offensiveShare, "offensive");
        requireShare(defensiveShare, "defensive");
        requireShare(utilityShare, "utility");
        if (Math.abs(offensiveShare + defensiveShare + utilityShare - 1.0D) > SHARE_TOLERANCE) {
            throw new IllegalArgumentException("armor family budget shares must total 1.0: " + family);
        }
        preferredStats = statIds(preferredStats);
        disfavoredStats = statIds(disfavoredStats);
        if (!java.util.Collections.disjoint(preferredStats, disfavoredStats)) {
            throw new IllegalArgumentException("preferred and disfavored stats overlap: " + family);
        }
    }

    public double share(final ItemStatCatalog.Group group) {
        return switch (group) {
            case OFFENSIVE -> offensiveShare;
            case DEFENSIVE -> defensiveShare;
            case UTILITY, CLASS_SPECIFIC -> utilityShare;
        };
    }

    private static Set<String> statIds(final Set<String> raw) {
        if (raw == null || raw.isEmpty()) return Set.of();
        if (raw.size() > 16) throw new IllegalArgumentException("too many family stat tags");
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final String id : raw) result.add(ItemStatCatalog.require(id).id());
        return Set.copyOf(result);
    }

    private static void requireShare(final double value, final String label) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException("invalid " + label + " armor family budget share");
        }
    }
}
