package hu.taliann.icesmp.factions;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Bounded voxel traversal; every collision read is scheduled on that chunk's region. */
public final class WhisperSightline {
    private WhisperSightline() { }
    public record Cell(int x, int y, int z) { }
    private record Chunk(int x, int z) { }

    public static List<Cell> cells(final double x, final double y, final double z,
                                   final double endX, final double endY, final double endZ) {
        final double[] start = {x, y, z}, delta = {endX - x, endY - y, endZ - z};
        if (!Double.isFinite(x + y + z + endX + endY + endZ)
                || delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2] > 4096.01D) {
            throw new IllegalArgumentException("sightline outside 64 blocks");
        }
        final int[] current = {(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)};
        final int[] step = new int[3];
        final double[] next = new double[3], interval = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            step[axis] = delta[axis] == 0 ? 0 : delta[axis] > 0 ? 1 : -1;
            interval[axis] = step[axis] == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / delta[axis]);
            next[axis] = step[axis] == 0 ? Double.POSITIVE_INFINITY
                    : (current[axis] + (step[axis] > 0 ? 1 : 0) - start[axis]) / delta[axis];
        }
        final List<Cell> result = new ArrayList<>();
        for (int count = 0; count < 200; count++) {
            result.add(new Cell(current[0], current[1], current[2]));
            // Sequential ties include boundary cells, so a diagonal cannot slip through a corner wall.
            final int axis = next[0] <= next[1] && next[0] <= next[2] ? 0 : next[1] <= next[2] ? 1 : 2;
            if (next[axis] > 1D) return List.copyOf(result);
            current[axis] += step[axis];
            next[axis] += interval[axis];
        }
        throw new IllegalArgumentException("sightline traversal exhausted");
    }

    public static CompletableFuture<Boolean> visible(final JavaPlugin plugin, final Location eye, final Location scene) {
        if (eye.getWorld() != scene.getWorld()) return CompletableFuture.completedFuture(false);
        final var world = eye.getWorld();
        final var delta = scene.toVector().subtract(eye.toVector());
        final double length = delta.length();
        if (length < 0.001D) return CompletableFuture.completedFuture(true);
        final var direction = delta.normalize();
        final var chunks = new LinkedHashMap<Chunk, List<Cell>>();
        for (final Cell cell : cells(eye.getX(), eye.getY(), eye.getZ(), scene.getX(), scene.getY(), scene.getZ())) {
            final var chunk = new Chunk(cell.x() >> 4, cell.z() >> 4);
            final var group = chunks.computeIfAbsent(chunk, ignored -> new ArrayList<>());
            group.add(cell);
            // Fences/walls can extend above their own block cell.
            group.add(new Cell(cell.x(), cell.y() - 1, cell.z()));
        }
        final List<CompletableFuture<Boolean>> checks = new ArrayList<>();
        chunks.forEach((chunk, group) -> {
            final var check = new CompletableFuture<Boolean>();
            checks.add(check);
            Bukkit.getRegionScheduler().execute(plugin, world, chunk.x(), chunk.z(), () -> {
                try {
                    if (!world.isChunkLoaded(chunk.x(), chunk.z())) {
                        check.completeExceptionally(new IllegalStateException("unloaded observation chunk")); return;
                    }
                    for (final Cell cell : group) {
                        if (cell.y() < world.getMinHeight() || cell.y() >= world.getMaxHeight()) continue;
                        if (world.getBlockAt(cell.x(), cell.y(), cell.z()).rayTrace(eye, direction, length, FluidCollisionMode.NEVER) != null) {
                            check.complete(false); return;
                        }
                    }
                    check.complete(true);
                } catch (final RuntimeException failure) { check.completeExceptionally(failure); }
            });
        });
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> checks.stream().allMatch(check -> check.getNow(false)));
    }
}
