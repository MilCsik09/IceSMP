package hu.taliann.icesmp.dev.weaver.subject;

public record CylinderArea(double x, double z, double radius, int minY, int maxY) implements AreaShape {
    public CylinderArea {
        Coordinates.position(x, minY, z); Coordinates.position(x, maxY, z);
        if (!Double.isFinite(radius) || radius <= 0 || radius > 16 || maxY < minY || (long) maxY - minY >= 32) {
            throw new IllegalArgumentException("Cylinder exceeds radius/height limits");
        }
    }
    @Override public AreaBounds bounds() {
        return new AreaBounds((int) Math.floor(x - radius), minY, (int) Math.floor(z - radius),
                (int) Math.floor(x + radius), maxY, (int) Math.floor(z + radius));
    }
    @Override public boolean contains(final int bx, final int by, final int bz) {
        final double dx = bx + 0.5 - x, dz = bz + 0.5 - z;
        return by >= minY && by <= maxY && dx * dx + dz * dz <= radius * radius;
    }
}
