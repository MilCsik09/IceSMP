package hu.taliann.icesmp.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical, Y-independent personal-claim geometry. Both protection lookups and
 * visualisation consume this normalized X-Z rectangle so corner order, negative
 * coordinates and chunk boundaries cannot produce divergent results.
 */
public record ClaimFootprint(int minX, int minZ, int maxX, int maxZ) {

    public ClaimFootprint {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Claim footprint bounds must be normalized");
        }
    }

    public static ClaimFootprint between(final int x1, final int z1, final int x2, final int z2) {
        return new ClaimFootprint(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
    }

    public boolean contains(final int x, final int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(final ClaimFootprint other) {
        return minX <= other.maxX && maxX >= other.minX
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public long columns() {
        return Math.multiplyExact((long) maxX - minX + 1L, (long) maxZ - minZ + 1L);
    }

    public int outerMaxX() {
        return maxX + 1;
    }

    public int outerMaxZ() {
        return maxZ + 1;
    }

    /**
     * Sampled outside-edge points with all four corners present exactly once.
     * The insertion-ordered set also prevents diagonal joins or duplicated corner
     * particles for one-block and one-block-wide rectangles.
     */
    public List<BoundaryPoint> perimeter(final int spacing) {
        final int step = Math.max(1, spacing);
        final Set<BoundaryPoint> points = new LinkedHashSet<>();
        addHorizontal(points, minX, outerMaxX(), minZ, step);
        addVertical(points, minZ, outerMaxZ(), outerMaxX(), step);
        addHorizontal(points, minX, outerMaxX(), outerMaxZ(), step);
        addVertical(points, minZ, outerMaxZ(), minX, step);
        return List.copyOf(points);
    }

    private static void addHorizontal(final Set<BoundaryPoint> points, final int start, final int end,
                                      final int z, final int spacing) {
        for (final int x : sampledRange(start, end, spacing)) {
            points.add(new BoundaryPoint(x, z));
        }
    }

    private static void addVertical(final Set<BoundaryPoint> points, final int start, final int end,
                                    final int x, final int spacing) {
        for (final int z : sampledRange(start, end, spacing)) {
            points.add(new BoundaryPoint(x, z));
        }
    }

    private static List<Integer> sampledRange(final int start, final int end, final int spacing) {
        final List<Integer> values = new ArrayList<>();
        for (int value = start; value <= end; value += spacing) {
            values.add(value);
        }
        if (values.isEmpty() || values.get(values.size() - 1) != end) {
            values.add(end);
        }
        return values;
    }

    public record BoundaryPoint(int x, int z) { }
}
