package hu.taliann.icesmp.dev.weaver.subject;

import java.util.List;
import java.util.HashSet;

public record PolygonPrismArea(List<Vertex> vertices, int minY, int maxY) implements AreaShape {
    public record Vertex(int x, int z) {
        public Vertex { Coordinates.block(x, 0, z); }
    }
    public PolygonPrismArea {
        vertices = List.copyOf(vertices);
        if (vertices.size() < 3 || vertices.size() > 256 || new HashSet<>(vertices).size() != vertices.size()
                || maxY < minY || (long) maxY - minY >= 32) throw new IllegalArgumentException("Invalid polygon prism limits");
        Coordinates.block(0, minY, 0); Coordinates.block(0, maxY, 0);
        long area = 0;
        for (int i = 0; i < vertices.size(); i++) {
            final Vertex a = vertices.get(i), b = vertices.get((i + 1) % vertices.size());
            final Vertex previous = vertices.get((i + vertices.size() - 1) % vertices.size());
            if (orientation(previous, a, b) == 0 && ((long) previous.x() - a.x()) * ((long) b.x() - a.x())
                    + ((long) previous.z() - a.z()) * ((long) b.z() - a.z()) > 0) throw new IllegalArgumentException("Overlapping adjacent polygon edges");
            area += (long) a.x() * b.z() - (long) b.x() * a.z();
            for (int j = i + 1; j < vertices.size(); j++) {
                if (j == i + 1 || (i == 0 && j == vertices.size() - 1)) continue;
                if (intersects(a, b, vertices.get(j), vertices.get((j + 1) % vertices.size()))) {
                    throw new IllegalArgumentException("Self-intersecting polygon");
                }
            }
        }
        if (area == 0) throw new IllegalArgumentException("Zero-area polygon");
    }
    private static long orientation(final Vertex a, final Vertex b, final Vertex c) {
        return ((long) b.x() - a.x()) * ((long) c.z() - a.z()) - ((long) b.z() - a.z()) * ((long) c.x() - a.x());
    }
    private static boolean onSegment(final Vertex a, final Vertex b, final Vertex p) {
        return p.x() >= Math.min(a.x(), b.x()) && p.x() <= Math.max(a.x(), b.x())
                && p.z() >= Math.min(a.z(), b.z()) && p.z() <= Math.max(a.z(), b.z());
    }
    private static boolean intersects(final Vertex a, final Vertex b, final Vertex c, final Vertex d) {
        final long first = orientation(a, b, c), second = orientation(a, b, d), third = orientation(c, d, a), fourth = orientation(c, d, b);
        if ((first == 0 && onSegment(a, b, c)) || (second == 0 && onSegment(a, b, d))
                || (third == 0 && onSegment(c, d, a)) || (fourth == 0 && onSegment(c, d, b))) return true;
        return Long.signum(first) != Long.signum(second) && Long.signum(third) != Long.signum(fourth);
    }
    @Override public AreaBounds bounds() {
        return new AreaBounds(vertices.stream().mapToInt(Vertex::x).min().orElseThrow(), minY,
                vertices.stream().mapToInt(Vertex::z).min().orElseThrow(), vertices.stream().mapToInt(Vertex::x).max().orElseThrow(), maxY,
                vertices.stream().mapToInt(Vertex::z).max().orElseThrow());
    }
    @Override public boolean contains(final int x, final int y, final int z) {
        if (y < minY || y > maxY) return false;
        final double px = x + 0.5, pz = z + 0.5;
        boolean inside = false;
        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            final Vertex a = vertices.get(i), b = vertices.get(j);
            if ((a.z() > pz) != (b.z() > pz)
                    && px < (double) (b.x() - a.x()) * (pz - a.z()) / (b.z() - a.z()) + a.x()) inside = !inside;
        }
        return inside;
    }
}
