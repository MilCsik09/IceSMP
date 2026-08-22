package hu.taliann.icesmp.itemization;

public final class ItemStatScaling {

    private ItemStatScaling() {
    }

    public static double abilityPowerMultiplier(final double points, final double percentPerPoint,
                                                final double maxBonusPercent) {
        if (!Double.isFinite(points) || !Double.isFinite(percentPerPoint)
                || !Double.isFinite(maxBonusPercent)) {
            throw new IllegalArgumentException("ability power inputs must be finite");
        }
        final double bonus = Math.min(Math.max(0.0D, maxBonusPercent),
                Math.max(0.0D, points) * Math.max(0.0D, percentPerPoint));
        return 1.0D + (bonus / 100.0D);
    }
}
