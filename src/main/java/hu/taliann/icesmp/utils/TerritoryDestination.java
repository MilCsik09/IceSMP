package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.data.Territory;
import org.bukkit.World;

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

        long lower = (long) world.getMinHeight() + 1L;
        long upper = (long) world.getMaxHeight() - 2L;
        if (territory.minY() != Territory.NO_MIN_Y) {
            lower = Math.max(lower, territory.minY());
        }
        if (territory.maxY() != Territory.NO_MAX_Y) {
            // Both the feet block and the head block must remain inside.
            upper = Math.min(upper, (long) territory.maxY() - 1L);
        }
        if (lower > upper) {
            return null;
        }

        final int minY = Math.toIntExact(lower);
        final int maxY = Math.toIntExact(upper);
        final long surface = (long) world.getHighestBlockYAt(territory.x(), territory.z()) + 1L;
        final int preferredY = Math.toIntExact(Math.max(lower, Math.min(upper, surface)));
        final int maxDistance = maxY - minY;

        for (int distance = 0; distance <= maxDistance; distance++) {
            final int below = preferredY - distance;
            if (below >= minY && isSafe(world, territory, below)) {
                return below;
            }
            if (distance == 0) {
                continue;
            }
            final int above = preferredY + distance;
            if (above <= maxY && isSafe(world, territory, above)) {
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
