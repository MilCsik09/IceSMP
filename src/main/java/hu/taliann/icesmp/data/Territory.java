package hu.taliann.icesmp.data;

import java.util.List;

/**
 * A faction territory zone. Two shapes are supported:
 * <ul>
 *   <li><b>circular</b> — a disc of {@code radius} around the ({@code x}, {@code z})
 *       centre ({@code polygon} is {@code null}/empty);</li>
 *   <li><b>polygon</b> — an arbitrary boundary given as a ring of {@code {x, z}}
 *       vertices (≥3), e.g. tracing a city wall. Here ({@code x}, {@code z}) is the
 *       centroid and {@code radius} is the bounding-circle radius (max distance
 *       from the centroid to any vertex), used both for a cheap reject test and
 *       for the "most specific claim wins" size comparison.</li>
 * </ul>
 *
 * <p>A zone can optionally be limited to a vertical band [{@code minY}, {@code maxY}];
 * {@link #NO_MIN_Y}/{@link #NO_MAX_Y} mark an unbounded (full-height) column, which
 * is the default for zones defined without an explicit Y-range.
 *
 * @param id unique identifier of the zone
 * @param faction the owning faction (NEUTRAL for ownerless protected cities)
 * @param name display name shown to players
 * @param type the zone kind (build/claim rules — see {@link TerritoryType})
 * @param world the world name
 * @param x centre/centroid block X
 * @param z centre/centroid block Z
 * @param radius circular radius, or the polygon's bounding-circle radius
 * @param polygon polygon vertices as {@code {x, z}} pairs, or {@code null}/empty for a disc
 * @param minY lower Y bound (inclusive), or {@link #NO_MIN_Y} for unbounded
 * @param maxY upper Y bound (inclusive), or {@link #NO_MAX_Y} for unbounded
 */
public record Territory(
        String id,
        FactionType faction,
        String name,
        TerritoryType type,
        String world,
        int x,
        int z,
        int radius,
        List<int[]> polygon,
        int minY,
        int maxY
) {

    public static final int NO_MIN_Y = Integer.MIN_VALUE;
    public static final int NO_MAX_Y = Integer.MAX_VALUE;

    /** Whether this zone is the faction's capital (banking/exchange gate). */
    public boolean capital() {
        return type == TerritoryType.CAPITAL;
    }

    /** Whether this zone is described by a polygon boundary rather than a disc. */
    public boolean isPolygon() {
        return polygon != null && polygon.size() >= 3;
    }

    /** Whether the zone is limited to a vertical band (vs. a full-height column). */
    public boolean hasYBounds() {
        return minY != NO_MIN_Y || maxY != NO_MAX_Y;
    }

    /** 2D (column) containment test — ignores any Y bounds. */
    public boolean contains(final String worldName, final double blockX, final double blockZ) {
        if (!world.equals(worldName)) {
            return false;
        }

        final double deltaX = blockX - x;
        final double deltaZ = blockZ - z;
        // Bounding-circle reject works for both shapes: for a disc it IS the test,
        // for a polygon every vertex lies within it, so anything outside is out.
        if ((deltaX * deltaX) + (deltaZ * deltaZ) > ((double) radius * radius)) {
            return false;
        }
        return !isPolygon() || pointInPolygon(blockX, blockZ);
    }

    /** Full 3D containment test — the column test plus the optional Y band. */
    public boolean contains(final String worldName, final double blockX, final double blockY,
                            final double blockZ) {
        return blockY >= minY && blockY <= maxY && contains(worldName, blockX, blockZ);
    }

    /** Even-odd ray casting against the vertex ring. */
    private boolean pointInPolygon(final double px, final double pz) {
        boolean inside = false;
        final int count = polygon.size();
        for (int i = 0, j = count - 1; i < count; j = i++) {
            final int xi = polygon.get(i)[0];
            final int zi = polygon.get(i)[1];
            final int xj = polygon.get(j)[0];
            final int zj = polygon.get(j)[1];
            final boolean straddles = (zi > pz) != (zj > pz);
            if (straddles && px < (double) (xj - xi) * (pz - zi) / (double) (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }
}
