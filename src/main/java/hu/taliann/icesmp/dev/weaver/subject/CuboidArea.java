package hu.taliann.icesmp.dev.weaver.subject;

public record CuboidArea(AreaBounds bounds) implements AreaShape {
    public CuboidArea {
        java.util.Objects.requireNonNull(bounds);
        if ((long) bounds.maxX() - bounds.minX() >= 32 || (long) bounds.maxY() - bounds.minY() >= 32
                || (long) bounds.maxZ() - bounds.minZ() >= 32) throw new IllegalArgumentException("Cuboid exceeds 32 per axis");
    }
    @Override public boolean contains(final int x, final int y, final int z) { return bounds.contains(x, y, z); }
}
