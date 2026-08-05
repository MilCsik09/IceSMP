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

    /** Defensive load cap; normal gameplay uses the much smaller configured budget. */
    private static final int ABSOLUTE_COLUMN_LIMIT = 1_000_000;
    private static final int MAX_WORLD_COORDINATE = 30_000_000;

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
        return rectangle(footprint, ABSOLUTE_COLUMN_LIMIT);
    }

    public static ClaimShape rectangle(final ClaimFootprint footprint, final int maxColumns) {
        if (footprint == null) {
            throw new IllegalArgumentException("Rectangle footprint is required");
        }
        validateCoordinate(footprint.minX(), footprint.minZ());
        validateCoordinate(footprint.maxX(), footprint.maxZ());
        final int budget = requireBudget(maxColumns);
        final long columnCount = footprint.columns();
        if (columnCount > budget) {
            throw tooLarge(columnCount, budget);
        }
        final Set<Long> columns = new LinkedHashSet<>((int) Math.min(columnCount * 2L, Integer.MAX_VALUE - 8L));
        int z = footprint.minZ();
        while (true) {
            int x = footprint.minX();
            while (true) {
                columns.add(key(x, z));
                if (x == footprint.maxX()) break;
                x++;
            }
            if (z == footprint.maxZ()) break;
            z++;
        }
        final List<Point> vertices = List.of(
                new Point(footprint.minX(), footprint.minZ()),
                new Point(footprint.maxX(), footprint.minZ()),
                new Point(footprint.maxX(), footprint.maxZ()),
                new Point(footprint.minX(), footprint.maxZ()));
        return new ClaimShape(footprint, vertices, columns, false);
    }

    public static ClaimShape polygon(final List<Point> rawVertices) {
        return polygon(rawVertices, ABSOLUTE_COLUMN_LIMIT);
    }

    /**
     * Rasterizes a simple polygon within a strict claimed-column budget. Boundary
     * length and continuous area are rejected before allocation; row scanlines then
     * visit only Z rows and filled spans, never the full bounding rectangle.
     */
    public static ClaimShape polygon(final List<Point> rawVertices, final int maxColumns) {
        final int budget = requireBudget(maxColumns);
        final List<Point> vertices = normalize(rawVertices);
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("At least three distinct polygon points are required");
        }
        for (final Point point : vertices) validateCoordinate(point.x(), point.z());
        final long signedArea = signedDoubleArea(vertices);
        if (signedArea == 0L) {
            throw new IllegalArgumentException("Polygon area must be non-zero");
        }
        if (isSelfIntersecting(vertices)) {
            throw new IllegalArgumentException("Polygon boundary self-intersects");
        }

        long perimeterColumns = 0L;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int index = 0; index < vertices.size(); index++) {
            final Point point = vertices.get(index);
            final Point next = vertices.get((index + 1) % vertices.size());
            perimeterColumns = Math.addExact(perimeterColumns,
                    Math.max(Math.abs((long) next.x() - point.x()),
                            Math.abs((long) next.z() - point.z())));
            if (perimeterColumns > budget) {
                throw tooLarge(perimeterColumns, budget);
            }
            minX = Math.min(minX, point.x());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxZ = Math.max(maxZ, point.z());
        }
        if (Math.abs(signedArea) > 2L * budget) {
            throw tooLarge((Math.abs(signedArea) + 1L) / 2L, budget);
        }

        final ClaimFootprint bounds = new ClaimFootprint(minX, minZ, maxX, maxZ);
        final Set<Long> columns = new LinkedHashSet<>();
        for (int index = 0; index < vertices.size(); index++) {
            addLine(columns, vertices.get(index), vertices.get((index + 1) % vertices.size()), budget);
        }

        int z = minZ;
        while (true) {
            final List<Double> intersections = scanlineIntersections(vertices, z + 0.5D);
            if ((intersections.size() & 1) != 0) {
                throw new IllegalArgumentException("Polygon scanline has unmatched intersections");
            }
            for (int index = 0; index < intersections.size(); index += 2) {
                final double left = intersections.get(index);
                final double right = intersections.get(index + 1);
                int x = (int) Math.ceil(left - 0.5D);
                final int endExclusive = (int) Math.ceil(right - 0.5D);
                while (x < endExclusive) {
                    addBudgeted(columns, x, z, budget);
                    x++;
                }
            }
            if (z == maxZ) break;
            z++;
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
        if (other == null || !bounds.overlaps(other.bounds)) return false;
        final Set<Long> smaller = claimed.size() <= other.claimed.size() ? claimed : other.claimed;
        final Set<Long> larger = smaller == claimed ? other.claimed : claimed;
        for (final long column : smaller) if (larger.contains(column)) return true;
        return false;
    }

    /** Exact overlap without materializing a potentially huge rectangle. */
    public boolean overlaps(final ClaimFootprint footprint) {
        if (footprint == null || !bounds.overlaps(footprint)) return false;
        for (final Point point : claimedColumns) {
            if (footprint.contains(point.x(), point.z())) return true;
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

    private static int requireBudget(final int maxColumns) {
        if (maxColumns < 1) throw new IllegalArgumentException("Column budget must be positive");
        return Math.min(maxColumns, ABSOLUTE_COLUMN_LIMIT);
    }

    private static IllegalArgumentException tooLarge(final long estimated, final int budget) {
        return new IllegalArgumentException("Claim shape exceeds column budget: " + estimated + " > " + budget);
    }

    private static void validateCoordinate(final int x, final int z) {
        if (x < -MAX_WORLD_COORDINATE || x > MAX_WORLD_COORDINATE
                || z < -MAX_WORLD_COORDINATE || z > MAX_WORLD_COORDINATE) {
            throw new IllegalArgumentException("Claim point outside supported world coordinates");
        }
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
            area = Math.addExact(area,
                    Math.subtractExact(Math.multiplyExact((long) a.x(), b.z()),
                            Math.multiplyExact((long) b.x(), a.z())));
        }
        return area;
    }

    private static List<Double> scanlineIntersections(final List<Point> vertices, final double scanZ) {
        final List<Double> intersections = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) {
            final Point a = vertices.get(i);
            final Point b = vertices.get((i + 1) % vertices.size());
            if ((a.z() > scanZ) != (b.z() > scanZ)) {
                intersections.add(a.x() + (scanZ - a.z()) * (b.x() - a.x())
                        / (double) (b.z() - a.z()));
            }
        }
        intersections.sort(Double::compareTo);
        return intersections;
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
                && (o3 > 0L && o4 < 0L || o3 < 0L && o4 > 0L)) return true;
        return o1 == 0L && onSegment(a, b, c)
                || o2 == 0L && onSegment(a, b, d)
                || o3 == 0L && onSegment(c, d, a)
                || o4 == 0L && onSegment(c, d, b);
    }

    private static long orientation(final Point a, final Point b, final Point c) {
        return Math.subtractExact(
                Math.multiplyExact((long) b.x() - a.x(), (long) c.z() - a.z()),
                Math.multiplyExact((long) b.z() - a.z(), (long) c.x() - a.x()));
    }

    private static boolean onSegment(final Point a, final Point b, final Point point) {
        return point.x() >= Math.min(a.x(), b.x()) && point.x() <= Math.max(a.x(), b.x())
                && point.z() >= Math.min(a.z(), b.z()) && point.z() <= Math.max(a.z(), b.z());
    }

    private static void addLine(final Set<Long> columns, final Point start, final Point end,
                                final int budget) {
        int x = start.x();
        int z = start.z();
        final int dx = (int) Math.abs((long) end.x() - x);
        final int dz = (int) Math.abs((long) end.z() - z);
        final int stepX = x < end.x() ? 1 : -1;
        final int stepZ = z < end.z() ? 1 : -1;
        int error = dx - dz;
        while (true) {
            addBudgeted(columns, x, z, budget);
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

    private static void addBudgeted(final Set<Long> columns, final int x, final int z,
                                    final int budget) {
        columns.add(key(x, z));
        if (columns.size() > budget) throw tooLarge(columns.size(), budget);
    }

    private static List<Point> sortedPoints(final Set<Long> encoded) {
        return encoded.stream().map(ClaimShape::point)
                .sorted(Comparator.comparingInt(Point::z).thenComparingInt(Point::x)).toList();
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
