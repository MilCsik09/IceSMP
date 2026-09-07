package hu.taliann.icesmp.dev.weaver.subject;

public record AreaBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public AreaBounds {
        Coordinates.block(minX, minY, minZ); Coordinates.block(maxX, maxY, maxZ);
        if (maxX < minX || maxY < minY || maxZ < minZ) throw new IllegalArgumentException("Reversed area bounds");
    }
    public long chunkCount() { return ((long) (maxX >> 4) - (minX >> 4) + 1) * ((long) (maxZ >> 4) - (minZ >> 4) + 1); }
    public boolean contains(final int x, final int y, final int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
