package hu.taliann.icesmp.sit;

/** Dependency-free seat-height model used by the Bukkit adapter and regression suite. */
public final class SitGeometry {
    public enum Shape {
        STAIRS_BOTTOM,
        STAIRS_TOP,
        SLAB_BOTTOM,
        SLAB_TOP_OR_DOUBLE,
        CARPET,
        SNOW,
        GENERIC
    }

    private SitGeometry() {
    }

    public static double offset(final Shape shape, final int snowLayers) {
        return switch (shape) {
            case STAIRS_BOTTOM -> 0.30D;
            case STAIRS_TOP -> 0.80D;
            case SLAB_BOTTOM -> 0.50D;
            case SLAB_TOP_OR_DOUBLE -> 1.00D;
            case CARPET -> 0.0625D;
            case SNOW -> Math.max(0.0625D, validateSnowLayers(snowLayers) / 8.0D);
            case GENERIC -> 0.30D;
        };
    }

    private static int validateSnowLayers(final int layers) {
        if (layers < 1 || layers > 8) {
            throw new IllegalArgumentException("snow layers must be within 1..8");
        }
        return layers;
    }
}
