package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native seat system (GSit replacement, core UX): a player "sits" by riding an invisible,
 * gravity-less, marker {@link ArmorStand} placed at a block's surface.
 *
 * <p>Folia note: {@link #sit(Player, Location)} and {@link #standUp(Player)} must only be called
 * from a thread that already owns the player's region — i.e. from inside an event/command that
 * fires on the player (interact, dismount, quit, death, teleport, the /sit command itself). Both
 * the spawn and the removal are then region-local: a passenger and its vehicle are always kept in
 * the same Folia region, so once we're on the player's thread the seat ArmorStand is guaranteed to
 * be reachable on that same thread too — no scheduler hop is needed either way.
 */
public final class SitManager implements PlayerStateCleanup {

    /** Result of a {@link #sit(Player, Location)} attempt, so callers can pick the right Hungarian message. */
    public enum SitResult {
        OK,
        ALREADY_SITTING,
        NOT_ON_GROUND,
        IN_LIQUID,
        IN_VEHICLE,
        OBSTRUCTED
    }

    /** Generic seat height above a block that isn't a stair/slab (e.g. the /sit-anywhere command target). */
    private static final double GENERIC_SEAT_OFFSET = 0.3D;
    /** Stairs: sit a bit lower than a full block top, roughly at step height. */
    private static final double STAIRS_SEAT_OFFSET = 0.3D;
    private static final double SLAB_BOTTOM_SEAT_OFFSET = 0.5D;
    private static final double SLAB_TOP_SEAT_OFFSET = 1.0D;

    /** One seat = the mount ArmorStand plus the exact block it was placed on (for break-detection). */
    private record Seat(ArmorStand stand, Location supportBlock) {
    }

    private final JavaPlugin plugin;
    private final NamespacedKey seatKey;
    private final Map<UUID, Seat> seatedPlayers = new ConcurrentHashMap<>();
    /**
     * IDEAS A49: standing "lay pose" (LibsDisguises-backed, see {@code LayPoseBridge}) — distinct
     * from the ArmorStand seat above: a laying player is still standing on the ground, just visually
     * disguised into the sleeping pose. Boolean value is unused (presence = laying); a
     * {@link java.util.Set} would do too, but a map matches the rest of this class's bookkeeping.
     */
    private final Map<UUID, Boolean> layingPlayers = new ConcurrentHashMap<>();

    public SitManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.seatKey = new NamespacedKey(plugin, "icesmp_seat");
    }

    /**
     * Computes the seat location on top of the given block (stairs sit lower, slabs sit on their
     * own top surface, everything else uses the generic "sitting on the ground" offset).
     */
    public Location computeSeatLocation(final Block block) {
        final BlockData data = block.getBlockData();
        final double offset;
        if (data instanceof Stairs) {
            offset = STAIRS_SEAT_OFFSET;
        } else if (data instanceof Slab slab) {
            offset = slab.getType() == Slab.Type.BOTTOM ? SLAB_BOTTOM_SEAT_OFFSET : SLAB_TOP_SEAT_OFFSET;
        } else {
            offset = GENERIC_SEAT_OFFSET;
        }
        final World world = block.getWorld();
        return new Location(world, block.getX() + 0.5D, block.getY() + offset, block.getZ() + 0.5D);
    }

    /** Whether there is anything worth an air-block check above (used before offering a sit spot). */
    public boolean hasClearanceAbove(final Block block) {
        return block.getRelative(BlockFace.UP).getType().isAir();
    }

    public boolean isSitting(final UUID playerId) {
        return seatedPlayers.containsKey(playerId);
    }

    /** True while at least one seat is active — lets listeners skip work (e.g. block-break checks) cheaply. */
    public boolean hasActiveSits() {
        return !seatedPlayers.isEmpty();
    }

    /**
     * Places the player onto a seat at the given location. Must be called on the player's own
     * region thread (see class javadoc); the spawn is then region-local.
     *
     * @param player the player who wants to sit
     * @param seatLocation where the seat ArmorStand should appear (use {@link #computeSeatLocation})
     */
    public SitResult sit(final Player player, final Location seatLocation) {
        if (isSitting(player.getUniqueId())) {
            return SitResult.ALREADY_SITTING;
        }
        if (player.isInsideVehicle()) {
            return SitResult.IN_VEHICLE;
        }
        if (player.isInWater() || player.getLocation().getBlock().isLiquid()) {
            return SitResult.IN_LIQUID;
        }
        if (!player.isOnGround() && player.getFallDistance() > 0.0F) {
            return SitResult.NOT_ON_GROUND;
        }
        final World world = seatLocation.getWorld();
        if (world == null) {
            return SitResult.OBSTRUCTED;
        }

        final ArmorStand stand = world.spawn(seatLocation, ArmorStand.class, spawned -> {
            spawned.setInvisible(true);
            spawned.setMarker(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setPersistent(false);
            spawned.setSilent(true);
            spawned.setCanPickupItems(false);
            spawned.getPersistentDataContainer().set(seatKey, PersistentDataType.BYTE, (byte) 1);
        });
        stand.addPassenger(player);

        final Location supportBlock = seatLocation.getBlock().getLocation();
        seatedPlayers.put(player.getUniqueId(), new Seat(stand, supportBlock));
        return SitResult.OK;
    }

    /**
     * Stands the player up: dismounts them (no-op if already off the seat, e.g. called from
     * {@code EntityDismountEvent}) and removes the seat ArmorStand. Safe to call redundantly.
     */
    public void standUp(final Player player) {
        final Seat seat = seatedPlayers.remove(player.getUniqueId());
        if (seat == null) {
            return;
        }
        if (player.isInsideVehicle() && player.getVehicle() != null
                && player.getVehicle().getUniqueId().equals(seat.stand().getUniqueId())) {
            player.leaveVehicle();
        }
        if (seat.stand().isValid()) {
            seat.stand().remove();
        }
    }

    /**
     * If someone is currently seated on top of the given block, returns that player — used by the
     * block-break guard so a mined seat block doesn't leave the sitter floating on an orphan stand.
     * Only meaningful when called region-locally (the caller already owns the broken block's region).
     */
    public Player findSitterOnBlock(final Block block) {
        if (!hasActiveSits()) {
            return null;
        }
        final Location target = block.getLocation();
        for (final Map.Entry<UUID, Seat> entry : seatedPlayers.entrySet()) {
            final Location support = entry.getValue().supportBlock();
            if (support.getWorld() == target.getWorld()
                    && support.getBlockX() == target.getBlockX()
                    && support.getBlockY() == target.getBlockY()
                    && support.getBlockZ() == target.getBlockZ()) {
                return Bukkit.getPlayer(entry.getKey());
            }
        }
        return null;
    }

    // ===== IDEAS A49: fekvő póz (nincs ArmorStand, csak reflexiós LibsDisguises-póz) =====

    public boolean isLaying(final UUID playerId) {
        return layingPlayers.containsKey(playerId);
    }

    /** Registers the player as laying — the actual disguise pose is applied by the caller (SitCommand). */
    public void startLaying(final UUID playerId) {
        layingPlayers.put(playerId, Boolean.TRUE);
    }

    /** Only drops the bookkeeping entry — the caller is responsible for clearing the disguise itself. */
    public void stopLaying(final UUID playerId) {
        layingPlayers.remove(playerId);
    }

    /**
     * Only drops the in-memory map entry. The seat ArmorStand itself must NOT be touched here:
     * this is invoked by {@code PlayerSessionCleanupListener} as a uniform cleanup pass and is not
     * guaranteed to run on the seat's own region thread. The actual entity removal on quit happens
     * in {@code SitListener#onPlayerQuit}, which runs on the player's (and therefore the seat's)
     * own region thread and calls {@link #standUp(Player)} directly.
     */
    @Override
    public void clearPlayerState(final UUID playerId) {
        seatedPlayers.remove(playerId);
        layingPlayers.remove(playerId);
    }
}
