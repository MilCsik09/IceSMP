package hu.taliann.icesmp.selection;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared, feature-neutral two-corner 3D selection service.
 *
 * <p>The service owns the player session, world identity validation, inclusive cuboid
 * normalization, overflow-safe dimensions, preview task and lifecycle cleanup. Domain systems
 * apply the metric that belongs to them: AFK zones use the volume-limited overload, while claims
 * use the complete cuboid and enforce their historical XZ footprint cap.</p>
 *
 * <p>Folia ownership: corners and previews are set from the player's entity thread. Preview
 * particles are emitted only from a task scheduled on that same player. The map itself contains
 * immutable scalar snapshots and may be queried safely by command/service code.</p>
 */
public final class CuboidSelectionService implements PlayerStateCleanup {

    public enum Status {
        READY,
        INCOMPLETE,
        TOO_LARGE
    }

    /** Immutable, inclusive block cuboid with stable and human-readable world identity. */
    public record Cuboid(UUID worldId, String worldName,
                         int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ) {

        public Cuboid {
            if (worldId == null || worldName == null || worldName.isBlank()) {
                throw new IllegalArgumentException("A kijelölés világazonosítója hiányzik.");
            }
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("A kijelölés koordinátái nincsenek normalizálva.");
            }
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

        /** Inclusive XZ footprint with the same overflow-safe semantics as volume. */
        public long footprint() {
            return saturatingMultiply(width(), depth());
        }

        /** Saturating multiplication: malicious extreme coordinates can never wrap negative. */
        public long volume() {
            return saturatingMultiply(saturatingMultiply(width(), height()), depth());
        }

        public boolean contains(final Location location) {
            return location != null && location.getWorld() != null
                    && worldId.equals(location.getWorld().getUID())
                    && location.getBlockX() >= minX && location.getBlockX() <= maxX
                    && location.getBlockY() >= minY && location.getBlockY() <= maxY
                    && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
        }

        private static long saturatingMultiply(final long left, final long right) {
            if (left <= 0L || right <= 0L) {
                return 0L;
            }
            if (left > Long.MAX_VALUE / right) {
                return Long.MAX_VALUE;
            }
            return left * right;
        }
    }

    public record Corner(UUID worldId, String worldName, int x, int y, int z) { }

    public record Result(Status status, Cuboid cuboid, long maxVolume) {
        public boolean ready() {
            return status == Status.READY && cuboid != null;
        }
    }

    private static final class MutableSelection {
        private Corner first;
        private Corner second;
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, MutableSelection> selections = new ConcurrentHashMap<>();
    private final IdentityTaskRegistry<UUID, ScheduledTask> previewTasks = new IdentityTaskRegistry<>();

    public CuboidSelectionService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Sets one corner. Switching worlds deliberately clears the opposite corner, so a completed
     * cross-world selection can never be observed by a domain system.
     */
    public Corner setCorner(final Player player, final boolean first, final Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("A kijelöléshez élő játékos és világ kell.");
        }
        final World world = location.getWorld();
        final Corner corner = new Corner(world.getUID(), world.getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        final MutableSelection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new MutableSelection());
        synchronized (selection) {
            final Corner other = first ? selection.second : selection.first;
            if (other != null && !other.worldId().equals(corner.worldId())) {
                selection.first = null;
                selection.second = null;
            }
            if (first) {
                selection.first = corner;
            } else {
                selection.second = corner;
            }
        }
        return corner;
    }

    /**
     * Returns the complete normalized cuboid without imposing a 3D volume policy. This is the
     * compatibility path for footprint-based domains such as claims; dimensions remain saturating
     * and the caller must enforce its own domain cap before any mutation.
     */
    public Result result(final UUID playerId) {
        return resultInternal(playerId, Long.MAX_VALUE);
    }

    /** Domain systems may apply a tighter 3D cap, never a looser one than the shared configured cap. */
    public Result result(final UUID playerId, final long requestedMaxVolume) {
        final long globalMax = Math.max(1L,
                configManager.getLong("selection.max-volume", 1_000_000L));
        final long maxVolume = Math.max(1L, Math.min(globalMax, requestedMaxVolume));
        return resultInternal(playerId, maxVolume);
    }

    private Result resultInternal(final UUID playerId, final long maxVolume) {
        final MutableSelection selection = selections.get(playerId);
        if (selection == null) {
            return new Result(Status.INCOMPLETE, null, maxVolume);
        }
        final Cuboid cuboid;
        synchronized (selection) {
            if (selection.first == null || selection.second == null) {
                return new Result(Status.INCOMPLETE, null, maxVolume);
            }
            if (!selection.first.worldId().equals(selection.second.worldId())) {
                // Defensive invariant: setCorner already prevents this state.
                return new Result(Status.INCOMPLETE, null, maxVolume);
            }
            cuboid = normalize(selection.first, selection.second);
        }
        return cuboid.volume() > maxVolume
                ? new Result(Status.TOO_LARGE, cuboid, maxVolume)
                : new Result(Status.READY, cuboid, maxVolume);
    }

    public void clear(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        selections.remove(playerId);
        cancelPreview(playerId);
    }

    /** Shows the current completed cuboid for a bounded number of frames. */
    public Result show(final Player player, final int requestedSeconds) {
        final Result result = result(player.getUniqueId());
        if (!result.ready()) {
            return result;
        }
        showCuboid(player, result.cuboid(), requestedSeconds);
        return result;
    }

    /** Domain systems may preview a stored cuboid without copying the preview implementation. */
    public void showCuboid(final Player player, final Cuboid cuboid, final int requestedSeconds) {
        if (player == null || cuboid == null) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        final int seconds = Math.max(1, Math.min(30, requestedSeconds));
        final AtomicInteger frame = new AtomicInteger();
        final IdentityTaskRegistry.Installation<ScheduledTask> installation = previewTasks.install(playerId);
        cancelLease(installation.previous());
        if (!installation.active()) {
            return;
        }
        final IdentityTaskRegistry.Lease<ScheduledTask> lease = installation.current();

        final ScheduledTask scheduled;
        try {
            scheduled = player.getScheduler().runAtFixedRate(plugin, task -> {
                if (!player.isOnline() || frame.getAndIncrement() >= seconds) {
                    previewTasks.remove(playerId, lease);
                    task.cancel();
                    return;
                }
                drawFrame(player, cuboid);
            }, () -> previewTasks.remove(playerId, lease), 1L, 20L);
        } catch (final RuntimeException rejected) {
            previewTasks.remove(playerId, lease);
            return;
        }
        lease.attach(scheduled);
        if (scheduled == null) {
            previewTasks.remove(playerId, lease);
            return;
        }
        if (!previewTasks.isCurrent(playerId, lease)) {
            scheduled.cancel();
        }
    }

    /** Reload semantics are explicit: transient corners and preview tasks never survive reload. */
    public void clearAll() {
        previewTasks.invalidateAndDrain().forEach(CuboidSelectionService::cancelLease);
        selections.clear();
    }

    public void shutdown() {
        clearAll();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        clear(playerId);
    }

    private void cancelPreview(final UUID playerId) {
        cancelLease(previewTasks.remove(playerId));
    }

    private static void cancelLease(final IdentityTaskRegistry.Lease<ScheduledTask> lease) {
        if (lease != null && lease.task() != null) {
            lease.task().cancel();
        }
    }

    public static Cuboid normalize(final Corner first, final Corner second) {
        if (first == null || second == null || !first.worldId().equals(second.worldId())) {
            throw new IllegalArgumentException("A két kijelölési pontnak ugyanabban a világban kell lennie.");
        }
        return new Cuboid(first.worldId(), first.worldName(),
                Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z()),
                Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z()));
    }

    private void drawFrame(final Player player, final Cuboid cuboid) {
        final World world = player.getWorld();
        if (!world.getUID().equals(cuboid.worldId())) {
            return;
        }
        final int maxPoints = Math.max(24, Math.min(240,
                configManager.getInt("selection.preview-max-points", 120)));
        final double[][] corners = {
                {cuboid.minX(), cuboid.minY(), cuboid.minZ()},
                {cuboid.maxX() + 1.0D, cuboid.minY(), cuboid.minZ()},
                {cuboid.minX(), cuboid.maxY() + 1.0D, cuboid.minZ()},
                {cuboid.maxX() + 1.0D, cuboid.maxY() + 1.0D, cuboid.minZ()},
                {cuboid.minX(), cuboid.minY(), cuboid.maxZ() + 1.0D},
                {cuboid.maxX() + 1.0D, cuboid.minY(), cuboid.maxZ() + 1.0D},
                {cuboid.minX(), cuboid.maxY() + 1.0D, cuboid.maxZ() + 1.0D},
                {cuboid.maxX() + 1.0D, cuboid.maxY() + 1.0D, cuboid.maxZ() + 1.0D}
        };
        final int[][] edges = {
                {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
                {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
        };
        final int perEdge = Math.max(2, maxPoints / edges.length);
        for (final int[] edge : edges) {
            final double[] from = corners[edge[0]];
            final double[] to = corners[edge[1]];
            for (int index = 0; index <= perEdge; index++) {
                final double ratio = index / (double) perEdge;
                player.spawnParticle(Particle.END_ROD,
                        new Location(world,
                                lerp(from[0], to[0], ratio),
                                lerp(from[1], to[1], ratio),
                                lerp(from[2], to[2], ratio)),
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static double lerp(final double from, final double to, final double ratio) {
        return from + (to - from) * ratio;
    }
}
