package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.data.Territory;
import org.bukkit.World;

import java.util.function.IntPredicate;

/** Safe centre-column destination lookup for territory teleports. */
public final class TerritoryDestination {

    private TerritoryDestination() {
    }

    /**
     * Finds a two-block-high standing position at the territory's operational
     * centre. Call only from the centre chunk's region thread.
     *
     * @return the player's feet Y, or {@code null} when that column has no safe
     *         position fully inside the territory and world bounds
     */
    public static Integer findSafeStandingY(final World world, final Territory territory) {
        if (world == null || territory == null || !world.getName().equals(territory.world())) {
            return null;
        }

        final int surfaceY = Math.addExact(
                world.getHighestBlockYAt(territory.x(), territory.z()), 1);
        return findSafeStandingYWithinBounds(
                world.getMinHeight(), world.getMaxHeight(),
                territory.minY(), territory.maxY(), surfaceY,
                y -> isSafe(world, territory, y));
    }

    /**
     * Pure bounded search used by the Bukkit-facing lookup and dependency-free
     * regression tests. {@code worldMaxHeight} is exclusive.
     */
    public static Integer findSafeStandingYWithinBounds(
            final int worldMinHeight,
            final int worldMaxHeight,
            final int territoryMinY,
            final int territoryMaxY,
            final int preferredSurfaceY,
            final IntPredicate safeAtY) {
        if (safeAtY == null || worldMinHeight >= worldMaxHeight) {
            return null;
        }

        long lower = (long) worldMinHeight + 1L;
        long upper = (long) worldMaxHeight - 2L;
        if (territoryMinY != Territory.NO_MIN_Y) {
            lower = Math.max(lower, territoryMinY);
        }
        if (territoryMaxY != Territory.NO_MAX_Y) {
            // Both the feet block and the head block must remain inside.
            upper = Math.min(upper, (long) territoryMaxY - 1L);
        }
        if (lower > upper) {
            return null;
        }

        final int minY = Math.toIntExact(lower);
        final int maxY = Math.toIntExact(upper);
        final int preferredY = Math.toIntExact(
                Math.max(lower, Math.min(upper, (long) preferredSurfaceY)));
        final int maxDistance = maxY - minY;
        for (int distance = 0; distance <= maxDistance; distance++) {
            final int below = preferredY - distance;
            if (below >= minY && safeAtY.test(below)) {
                return below;
            }
            if (distance == 0) {
                continue;
            }
            final int above = preferredY + distance;
            if (above <= maxY && safeAtY.test(above)) {
                return above;
            }
        }
        return null;
    }

    private static boolean isSafe(final World world, final Territory territory, final int feetY) {
        final int x = territory.x();
        final int z = territory.z();
        return territory.contains(world.getName(), x + 0.5D, feetY + 0.01D, z + 0.5D)
                && territory.contains(world.getName(), x + 0.5D, feetY + 1.01D, z + 0.5D)
                && world.getBlockAt(x, feetY, z).isPassable()
                && world.getBlockAt(x, feetY + 1, z).isPassable()
                && world.getBlockAt(x, feetY - 1, z).getType().isSolid();
    }
}
