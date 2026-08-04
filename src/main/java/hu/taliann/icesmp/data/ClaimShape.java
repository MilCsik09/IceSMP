package hu.taliann.icesmp.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exact X-Z column shape for personal claims. Rectangles and territory-style
 * polygons share one immutable rasterized representation, so protection, pricing,
 * overlap, persistence and rendering cannot disagree about occupied columns.
 */
public final class ClaimShape {

    public record Point(int x, int z) { }
    public record RowSpan(int z, int minX, int maxX) { }

    private final ClaimFootprint bounds;
    private final List<Point> vertices;
    private final Set<Long> claimed;
    private final List<Point> claimedColumns;
    private final List<Point> boundaryColumns;
    private final boolean polygon;

    private ClaimShape(final ClaimFootprint bounds, final List<Point> vertices,
                       final Set<Long> claimed, final boolean polygon) {
        if (claimed.isEmpty()) {
            throw new IllegalArgumentException("Claim shape contains no block columns");
        }
        this.bounds = bounds;
        this.vertices = List.copyOf(vertices);
        this.claimed = Set.copyOf(claimed);
        this.claimedColumns = sortedPoints(claimed);
        this.polygon = polygon;
        final List<Point> boundary = new ArrayList<>();
        for (final Point point : claimedColumns) {
            if (!containsKey(claimed, point.x() - 1, point.z())
                    || !containsKey(claimed, point.x() + 1, point.z())
                    || !containsKey(claimed, point.x(), point.z() - 1)
                    || !containsKey(claimed, point.x(), point.z() + 1)) {
                boundary.add(point);
            }
        }
        this.boundaryColumns = List.copyOf(boundary);
    }

    public static ClaimShape rectangle(final ClaimFootprint footprint) {
        if (footprint == null) {
            throw new IllegalArgumentException("Rectangle footprint is required");
        }
        final Set<Long> columns = new LinkedHashSet<>();
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
            for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                columns.add(key(x, z));
            }
        }
        final List<Point> vertices = List.of(
                new Point(footprint.minX(), footprint.minZ()),
                new Point(footprint.maxX(), footprint.minZ()),
                new Point(footprint.maxX(), footprint.maxZ()),
                new Point(footprint.minX(), footprint.maxZ()));
        return new ClaimShape(footprint, vertices, columns, false);
    }

    public static ClaimShape polygon(final List<Point> rawVertices) {
        final List<Point> vertices = normalize(rawVertices);
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("At least three distinct polygon points are required");
        }
        if (signedDoubleArea(vertices) == 0L) {
            throw new IllegalArgumentException("Polygon area must be non-zero");
        }
        if (isSelfIntersecting(vertices)) {
            throw new IllegalArgumentException("Polygon boundary self-intersects");
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (final Point point : vertices) {
            minX = Math.min(minX, point.x());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxZ = Math.max(maxZ, point.z());
        }
        final ClaimFootprint bounds = new ClaimFootprint(minX, minZ, maxX, maxZ);
        final Set<Long> columns = new LinkedHashSet<>();
        for (int index = 0; index < vertices.size(); index++) {
            addLine(columns, vertices.get(index), vertices.get((index + 1) % vertices.size()));
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (pointInPolygon(vertices, x + 0.5D, z + 0.5D)) {
                    columns.add(key(x, z));
                }
            }
        }
        return new ClaimShape(bounds, vertices, columns, true);
    }

    public ClaimFootprint bounds() { return bounds; }
    public boolean isPolygon() { return polygon; }
    public List<Point> vertices() { return vertices; }
    public List<Point> claimedColumns() { return claimedColumns; }
    public List<Point> boundaryColumns() { return boundaryColumns; }
    public int columns() { return claimed.size(); }

    public boolean contains(final int x, final int z) {
        return claimed.contains(key(x, z));
    }

    public boolean overlaps(final ClaimShape other) {
        if (other == null || !bounds.overlaps(other.bounds)) {
            return false;
        }
        final Set<Long> smaller = claimed.size() <= other.claimed.size() ? claimed : other.claimed;
        final Set<Long> larger = smaller == claimed ? other.claimed : claimed;
        for (final long column : smaller) {
            if (larger.contains(column)) {
                return true;
            }
        }
        return false;
    }

    /** Contiguous X spans per Z row for exact, bounded WorldGuard overlap queries. */
    public List<RowSpan> rowSpans() {
        final List<RowSpan> spans = new ArrayList<>();
        int index = 0;
        while (index < claimedColumns.size()) {
            final int z = claimedColumns.get(index).z();
            int minX = claimedColumns.get(index).x();
            int maxX = minX;
            index++;
            while (index < claimedColumns.size() && claimedColumns.get(index).z() == z) {
                final int x = claimedColumns.get(index).x();
                if (x == maxX + 1) {
                    maxX = x;
                } else {
                    spans.add(new RowSpan(z, minX, maxX));
                    minX = x;
                    maxX = x;
                }
                index++;
            }
            spans.add(new RowSpan(z, minX, maxX));
        }
        return List.copyOf(spans);
    }

    private static List<Point> normalize(final List<Point> raw) {
        if (raw == null) return List.of();
        final List<Point> normalized = new ArrayList<>();
        for (final Point point : raw) {
            if (point != null && (normalized.isEmpty()
                    || !normalized.get(normalized.size() - 1).equals(point))) {
                normalized.add(point);
            }
        }
        if (normalized.size() > 1
                && normalized.get(0).equals(normalized.get(normalized.size() - 1))) {
            normalized.remove(normalized.size() - 1);
        }
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new IllegalArgumentException("Polygon contains a repeated non-adjacent point");
        }
        return normalized;
    }

    private static long signedDoubleArea(final List<Point> vertices) {
        long area = 0L;
        for (int i = 0; i < vertices.size(); i++) {
            final Point a = vertices.get(i);
            final Point b = vertices.get((i + 1) % vertices.size());
            area += (long) a.x() * b.z() - (long) b.x() * a.z();
        }
        return area;
    }

    private static boolean pointInPolygon(final List<Point> vertices,
                                          final double px, final double pz) {
        boolean inside = false;
        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            final Point a = vertices.get(i);
            final Point b = vertices.get(j);
            final boolean straddles = (a.z() > pz) != (b.z() > pz);
            if (straddles && px < (double) (b.x() - a.x()) * (pz - a.z())
                    / (double) (b.z() - a.z()) + a.x()) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static boolean isSelfIntersecting(final List<Point> vertices) {
        final int count = vertices.size();
        for (int i = 0; i < count; i++) {
            final Point a1 = vertices.get(i);
            final Point a2 = vertices.get((i + 1) % count);
            for (int j = i + 1; j < count; j++) {
                if (j == (i + 1) % count || (j + 1) % count == i) continue;
                final Point b1 = vertices.get(j);
                final Point b2 = vertices.get((j + 1) % count);
                if (segmentsIntersect(a1, a2, b1, b2)) return true;
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(final Point a, final Point b,
                                             final Point c, final Point d) {
        final long o1 = orientation(a, b, c);
        final long o2 = orientation(a, b, d);
        final long o3 = orientation(c, d, a);
        final long o4 = orientation(c, d, b);
        if ((o1 > 0L && o2 < 0L || o1 < 0L && o2 > 0L)
                && (o3 > 0L && o4 < 0L || o3 < 0L && o4 > 0L)) {
            return true;
        }
        return o1 == 0L && onSegment(a, b, c)
                || o2 == 0L && onSegment(a, b, d)
                || o3 == 0L && onSegment(c, d, a)
                || o4 == 0L && onSegment(c, d, b);
    }

    private static long orientation(final Point a, final Point b, final Point c) {
        return ((long) b.x() - a.x()) * ((long) c.z() - a.z())
                - ((long) b.z() - a.z()) * ((long) c.x() - a.x());
    }

    private static boolean onSegment(final Point a, final Point b, final Point point) {
        return point.x() >= Math.min(a.x(), b.x()) && point.x() <= Math.max(a.x(), b.x())
                && point.z() >= Math.min(a.z(), b.z()) && point.z() <= Math.max(a.z(), b.z());
    }

    private static void addLine(final Set<Long> columns, final Point start, final Point end) {
        int x = start.x();
        int z = start.z();
        final int dx = Math.abs(end.x() - x);
        final int dz = Math.abs(end.z() - z);
        final int stepX = x < end.x() ? 1 : -1;
        final int stepZ = z < end.z() ? 1 : -1;
        int error = dx - dz;
        while (true) {
            columns.add(key(x, z));
            if (x == end.x() && z == end.z()) return;
            final int doubled = error * 2;
            if (doubled > -dz) {
                error -= dz;
                x += stepX;
            }
            if (doubled < dx) {
                error += dx;
                z += stepZ;
            }
        }
    }

    private static List<Point> sortedPoints(final Set<Long> encoded) {
        return encoded.stream().map(ClaimShape::point)
                .sorted(Comparator.comparingInt(Point::z).thenComparingInt(Point::x))
                .toList();
    }

    private static boolean containsKey(final Set<Long> values, final int x, final int z) {
        return values.contains(key(x, z));
    }

    private static long key(final int x, final int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static Point point(final long key) {
        return new Point((int) (key >> 32), (int) key);
    }
}
