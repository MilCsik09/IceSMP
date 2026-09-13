package hu.taliann.icesmp.dev.weaver.subject;

public record RadiusArea(double x, double y, double z, double radius) implements AreaShape {
    public RadiusArea {
        Coordinates.position(x, y, z);
        if (!Double.isFinite(radius) || radius <= 0 || radius > 16) throw new IllegalArgumentException("Area radius outside 0..16");
    }
    @Override public AreaBounds bounds() {
        return new AreaBounds((int) Math.floor(x - radius), (int) Math.floor(y - radius), (int) Math.floor(z - radius),
                (int) Math.floor(x + radius), (int) Math.floor(y + radius), (int) Math.floor(z + radius));
    }
    @Override public boolean contains(final int bx, final int by, final int bz) {
        final double dx = bx + 0.5 - x, dy = by + 0.5 - y, dz = bz + 0.5 - z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
