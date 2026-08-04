#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one occurrence, got {count}: {old[:160]!r}")
    write(path, content.replace(old, new, 1))


write("src/main/java/hu/taliann/icesmp/data/ClaimShape.java", r'''package hu.taliann.icesmp.data;

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
''')

claim_path = "src/main/java/hu/taliann/icesmp/managers/ClaimManager.java"
claim = read(claim_path)
claim = claim.replace(
'''        private boolean overlapsFootprint(final String worldName, final int oMinX, final int oMinZ,
                                          final int oMaxX, final int oMaxZ) {
            return overlapsShape(worldName,
                    ClaimShape.rectangle(ClaimFootprint.between(oMinX, oMinZ, oMaxX, oMaxZ)));
        }''',
'''        private boolean overlapsFootprint(final String worldName, final int oMinX, final int oMinZ,
                                          final int oMaxX, final int oMaxZ) {
            return world.equals(worldName) && shape.overlaps(
                    ClaimFootprint.between(oMinX, oMinZ, oMaxX, oMaxZ));
        }''')
claim = claim.replace(
'''                entries.add(claim.world + " (" + claim.minX + ", " + claim.minZ + ")→(" + claim.maxX + ", "
                        + claim.maxZ + ") — " + (claim.maxX - claim.minX + 1) + "×" + (claim.maxZ - claim.minZ + 1));''',
'''                entries.add(claim.world + " (" + claim.minX + ", " + claim.minZ + ")→(" + claim.maxX + ", "
                        + claim.maxZ + ") — " + (claim.shape.isPolygon()
                        ? "poligon, " + claim.columns() + " oszlop"
                        : (claim.maxX - claim.minX + 1) + "×" + (claim.maxZ - claim.minZ + 1)));''')
claim = claim.replace(
'''    private int quickSize() {
        return Math.max(4, configManager.getInt("claims.quick-size", 16));
    }

''',
'''    private int quickSize() {
        return Math.max(4, configManager.getInt("claims.quick-size", 16));
    }

    private int areaMaxColumns() {
        return Math.max(16, configManager.getInt("claims.area-max-columns", 6400));
    }

    private int polygonMaxPoints() {
        return Math.max(3, configManager.getInt("claims.polygon-max-points", 64));
    }

''')
claim = claim.replace(
'''    private String createClaim(final Player player, final World world,
                               final int minX, final int minZ,
                               final int maxX, final int maxZ) {
        return createClaimShape(player, world,
                ClaimShape.rectangle(ClaimFootprint.between(minX, minZ, maxX, maxZ)));
    }''',
'''    private String createClaim(final Player player, final World world,
                               final int minX, final int minZ,
                               final int maxX, final int maxZ) {
        try {
            return createClaimShape(player, world, ClaimShape.rectangle(
                    ClaimFootprint.between(minX, minZ, maxX, maxZ), areaMaxColumns()));
        } catch (final IllegalArgumentException tooLarge) {
            return "claim-area-too-big";
        }
    }''')
claim = claim.replace(
'''    private Claim findFootprintOverlap(final String worldName, final int minX, final int minZ,
                                       final int maxX, final int maxZ) {
        return findShapeOverlap(worldName,
                ClaimShape.rectangle(ClaimFootprint.between(minX, minZ, maxX, maxZ)));
    }''',
'''    private Claim findFootprintOverlap(final String worldName, final int minX, final int minZ,
                                       final int maxX, final int maxZ) {
        for (final Claim claim : claims.values()) {
            if (claim.overlapsFootprint(worldName, minX, minZ, maxX, maxZ)) return claim;
        }
        return null;
    }''')
claim = claim.replace(
'''            if (selection.points.isEmpty()
                    || !selection.points.get(selection.points.size() - 1).equals(point)) {
                selection.points.add(point);
            }
            return selection.points.size();''',
'''            if (selection.points.isEmpty()
                    || !selection.points.get(selection.points.size() - 1).equals(point)) {
                if (selection.points.size() >= polygonMaxPoints()) {
                    return -selection.points.size();
                }
                selection.points.add(point);
            }
            return selection.points.size();''')
claim = claim.replace(
'''            if (selection.points.size() < 3) return null;
            points = List.copyOf(selection.points);''',
'''            if (selection.points.size() < 3 || selection.points.size() > polygonMaxPoints()) return null;
            points = List.copyOf(selection.points);''', 1)
claim = claim.replace(
'''            final ClaimShape shape = ClaimShape.polygon(points);
            return new PolygonSelectionInfo(points.size(), shape.columns(),''',
'''            final ClaimShape shape = ClaimShape.polygon(points, areaMaxColumns());
            return new PolygonSelectionInfo(points.size(), shape.columns(),''')
claim = claim.replace(
'''        if (points.size() > Math.max(3,
                configManager.getInt("claims.polygon-max-points", 64))) {
            return "claim-polygon-too-many";
        }''',
'''        if (points.size() > polygonMaxPoints()) return "claim-polygon-too-many";''')
claim = claim.replace(
'''            shape = ClaimShape.polygon(points);''',
'''            shape = ClaimShape.polygon(points, areaMaxColumns());''', 1)
claim = claim.replace(
'''        if (shape.columns() > Math.max(16,
                configManager.getInt("claims.area-max-columns", 6400))) {
            return "claim-polygon-too-big";
        }
        final String error = createClaimShape''',
'''        final String error = createClaimShape''')
claim = claim.replace(
'''            if (parts.length != 2) continue;''',
'''            if (parts.length != 2) return null;''')
old_load = '''                } else {
                    claim = new Claim(key,
                            section.getString(key + ".world", "world"),
                            section.getInt(key + ".min-x"), section.getInt(key + ".min-y"),
                            section.getInt(key + ".min-z"), section.getInt(key + ".max-x"),
                            section.getInt(key + ".max-y"), section.getInt(key + ".max-z"),
                            readClaimPolygon(section.getStringList(key + ".polygon")),
                            owner, ownerName, claimedAt);
                }'''
new_load = '''                } else {
                    final String polygonPath = key + ".polygon";
                    final boolean polygonStored = section.contains(polygonPath);
                    final List<ClaimShape.Point> polygon = readClaimPolygon(
                            section.getStringList(polygonPath));
                    if (polygonStored && polygon == null) {
                        throw new IllegalArgumentException("Malformed stored claim polygon");
                    }
                    claim = new Claim(key,
                            section.getString(key + ".world", "world"),
                            section.getInt(key + ".min-x"), section.getInt(key + ".min-y"),
                            section.getInt(key + ".min-z"), section.getInt(key + ".max-x"),
                            section.getInt(key + ".max-y"), section.getInt(key + ".max-z"),
                            polygon, owner, ownerName, claimedAt);
                }'''
if claim.count(old_load) != 1:
    raise RuntimeError("ClaimManager load polygon assertion failed")
claim = claim.replace(old_load, new_load, 1)
write(claim_path, claim)

replace_once(
    "src/main/java/hu/taliann/icesmp/commands/ClaimCommand.java",
'''        final int count = claimManager.addPolygonPoint(player);
        player.sendMessage(messageManager.get("claim-polygon-point-added",
                "&aHatárpont hozzáadva (&f%s&a): &f%s, %s",
                count, player.getLocation().getBlockX(), player.getLocation().getBlockZ()));
        sendPolygonPreview(player);''',
'''        final int count = claimManager.addPolygonPoint(player);
        if (count < 0) {
            player.sendMessage(messageManager.get("claim-polygon-point-limit",
                    "&cElérted a poligonpont-limitet: &f%s&c.", -count));
            return;
        }
        player.sendMessage(messageManager.get("claim-polygon-point-added",
                "&aHatárpont hozzáadva (&f%s&a): &f%s, %s",
                count, player.getLocation().getBlockX(), player.getLocation().getBlockZ()));
        sendPolygonPreview(player);'''
)
replace_once(
    "src/main/java/hu/taliann/icesmp/listeners/SelectionWandListener.java",
'''        final int count = claimManager.addPolygonPoint(player, clicked);
        player.sendMessage(messageManager.get("claim-polygon-wand-point",
                "&aClaim-határpont: &f%s, %s &7(összesen: &f%s&7). Jobb: vissza • SNEAK+jobb: foglalás",
                clicked.getBlockX(), clicked.getBlockZ(), count));''',
'''        final int count = claimManager.addPolygonPoint(player, clicked);
        if (count < 0) {
            player.sendMessage(messageManager.get("claim-polygon-point-limit",
                    "&cElérted a poligonpont-limitet: &f%s&c.", -count));
            return;
        }
        player.sendMessage(messageManager.get("claim-polygon-wand-point",
                "&aClaim-határpont: &f%s, %s &7(összesen: &f%s&7). Jobb: vissza • SNEAK+jobb: foglalás",
                clicked.getBlockX(), clicked.getBlockZ(), count));'''
)

suite_path = "src/regression/java/hu/taliann/icesmp/runtime/RuntimeHardeningRegressionSuite.java"
suite = read(suite_path)
suite = suite.replace(
'''        check(invalid, "self-intersecting/bow-tie polygon rejected");
    }''',
'''        check(invalid, "self-intersecting/bow-tie polygon rejected");

        boolean oversized = false;
        try {
            ClaimShape.polygon(List.of(
                    new ClaimShape.Point(0, 0),
                    new ClaimShape.Point(5000, 5000),
                    new ClaimShape.Point(5001, 5000),
                    new ClaimShape.Point(1, 0)), 6400);
        } catch (final IllegalArgumentException expected) {
            oversized = true;
        }
        check(oversized, "long thin polygon is rejected before unbounded raster work");
        check(!concave.overlaps(ClaimFootprint.between(3, 3, 4, 4)),
                "footprint overlap does not materialize or claim the concave notch");
    }''')
suite = suite.replace(
'''        check(claim.contains("ClaimShape.polygon(points)")
                        && claim.contains("readClaimPolygon")
                        && claim.contains("shape.rowSpans()"),
                "polygon claims share exact geometry across create, persistence and protection");''',
'''        check(claim.contains("ClaimShape.polygon(points, areaMaxColumns())")
                        && claim.contains("polygonStored && polygon == null")
                        && claim.contains("shape.rowSpans()"),
                "polygon claims are bounded and fail closed across create, persistence and protection");
        check(claim.contains("return -selection.points.size()")
                        && claim.contains("shape.overlaps(ClaimFootprint.between"),
                "polygon point input is capped and footprint conflict checks avoid rectangle materialization");
        final String shapeSource = source("src/main/java/hu/taliann/icesmp/data/ClaimShape.java");
        check(shapeSource.contains("scanlineIntersections")
                        && shapeSource.contains("perimeterColumns > budget")
                        && !shapeSource.contains("for (int x = minX; x <= maxX; x++)"),
                "polygon rasterization is budgeted scanline work, not bounding-box area work");''')
write(suite_path, suite)

replace_once(
    "docs/RUNTIME_HARDENING_AUDIT.md",
'''- **Normal claim geometry:** quick square and two-corner rectangles now coexist with a territory-style multi-point polygon flow.
  One immutable exact-column `ClaimShape` drives concave membership, overlap, column pricing, row-span WorldGuard checks, YAML
  persistence, chunk lookup and both particle/BlockDisplay boundaries. Bounding-box notches remain wilderness.''',
'''- **Normal claim geometry:** quick square and two-corner rectangles now coexist with a territory-style multi-point polygon flow.
  One immutable exact-column `ClaimShape` drives concave membership, overlap, column pricing, row-span WorldGuard checks, YAML
  persistence, chunk lookup and both particle/BlockDisplay boundaries. Polygon input and scanline rasterization are strictly
  point/column-budgeted; malformed stored polygons fail closed instead of expanding to their bounding rectangle.'''
)

print("polygon claim adversarial hardening applied")
