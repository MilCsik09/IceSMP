package hu.taliann.icesmp.data;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, block-inclusive 3D selection. Both corners belong to the cuboid.
 *
 * <p>The footprint polygon uses the outer block edges ({@code max + 1}) so the
 * existing even/odd territory containment treats every selected block as inside.
 */
public record BlockCuboid(
        String world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    public BlockCuboid {
        Objects.requireNonNull(world, "world");
        if (world.isBlank()) {
            throw new IllegalArgumentException("world must not be blank");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("cuboid bounds are not normalized");
        }
    }

    public static BlockCuboid between(final String world,
                                      final int x1, final int y1, final int z1,
                                      final int x2, final int y2, final int z2) {
        return new BlockCuboid(world,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public long width() {
        return (long) maxX - minX + 1L;
    }

    public long height() {
        return (long) maxY - minY + 1L;
    }

    public long depth() {
        return (long) maxZ - minZ + 1L;
    }

    public long columns() {
        return Math.multiplyExact(width(), depth());
    }

    public long volume() {
        return Math.multiplyExact(columns(), height());
    }

    public int centerX() {
        return Math.toIntExact((long) minX + (width() - 1L) / 2L);
    }

    public int centerZ() {
        return Math.toIntExact((long) minZ + (depth() - 1L) / 2L);
    }

    public List<int[]> footprintPolygon() {
        final int outerMaxX = Math.toIntExact((long) maxX + 1L);
        final int outerMaxZ = Math.toIntExact((long) maxZ + 1L);
        return List.of(
                new int[] {minX, minZ},
                new int[] {outerMaxX, minZ},
                new int[] {outerMaxX, outerMaxZ},
                new int[] {minX, outerMaxZ}
        );
    }
}
