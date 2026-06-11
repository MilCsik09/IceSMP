package hu.taliann.icesmp.data;

/**
 * A circular faction territory claim centered on a block column.
 *
 * @param id unique identifier of the claim
 * @param faction the owning faction
 * @param name display name shown to players
 * @param world the world name
 * @param x the center block X
 * @param z the center block Z
 * @param radius the claim radius in blocks
 * @param capital whether this territory is the faction's capital
 */
public record Territory(
        String id,
        FactionType faction,
        String name,
        String world,
        int x,
        int z,
        int radius,
        boolean capital
) {

    public boolean contains(final String worldName, final double blockX, final double blockZ) {
        if (!world.equals(worldName)) {
            return false;
        }

        final double deltaX = blockX - x;
        final double deltaZ = blockZ - z;
        return (deltaX * deltaX) + (deltaZ * deltaZ) <= ((double) radius * radius);
    }
}
