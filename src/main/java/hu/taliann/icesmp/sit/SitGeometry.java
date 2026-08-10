package hu.taliann.icesmp.sit;

/** Dependency-free seat-anchor model used by the Bukkit adapter and regression suite. */
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

    public enum Facing {
        NORTH,
        SOUTH,
        EAST,
        WEST
    }

    public enum StairShape {
        STRAIGHT,
        INNER_LEFT,
        INNER_RIGHT,
        OUTER_LEFT,
        OUTER_RIGHT
    }

    /** Block-local coordinates of the marker entity carrying the seated player. */
    public record Anchor(double x, double y, double z) {
    }

    private SitGeometry() {
    }

    public static double offset(final Shape shape, final int snowLayers) {
        return switch (shape) {
            case STAIRS_BOTTOM, SLAB_BOTTOM -> 0.50D;
            case STAIRS_TOP, SLAB_TOP_OR_DOUBLE, GENERIC -> 1.00D;
            case CARPET -> 0.0625D;
            case SNOW -> Math.max(0.0625D, validateSnowLayers(snowLayers) / 8.0D);
        };
    }

    public static Anchor centered(final Shape shape, final int snowLayers) {
        return new Anchor(0.50D, offset(shape, snowLayers), 0.50D);
    }

    /**
     * Bottom stairs use the lower tread as the seat and the raised part as the backrest.
     * Inner/outer corners are shifted laterally so the player remains on the matching tread.
     */
    public static Anchor stairAnchor(final boolean topHalf,
                                     final Facing facing,
                                     final StairShape stairShape) {
        if (topHalf) {
            return centered(Shape.STAIRS_TOP, 1);
        }
        final double frontX = switch (facing) {
            case EAST -> -0.25D;
            case WEST -> 0.25D;
            case NORTH, SOUTH -> 0.0D;
        };
        final double frontZ = switch (facing) {
            case NORTH -> 0.25D;
            case SOUTH -> -0.25D;
            case EAST, WEST -> 0.0D;
        };
        final double leftX = switch (facing) {
            case NORTH -> -0.25D;
            case SOUTH -> 0.25D;
            case EAST, WEST -> 0.0D;
        };
        final double leftZ = switch (facing) {
            case EAST -> -0.25D;
            case WEST -> 0.25D;
            case NORTH, SOUTH -> 0.0D;
        };
        final double lateral = switch (stairShape) {
            case STRAIGHT -> 0.0D;
            case INNER_LEFT, OUTER_RIGHT -> -1.0D;
            case INNER_RIGHT, OUTER_LEFT -> 1.0D;
        };
        return new Anchor(0.50D + frontX + leftX * lateral,
                offset(Shape.STAIRS_BOTTOM, 1),
                0.50D + frontZ + leftZ * lateral);
    }

    private static int validateSnowLayers(final int layers) {
        if (layers < 1 || layers > 8) {
            throw new IllegalArgumentException("snow layers must be within 1..8");
        }
        return layers;
    }
}
