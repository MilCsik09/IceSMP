package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Crash-safe authority for bounded reversible two-block apertures. */
public final class TrashSpatialFractureStore implements PersistentStore {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_OPEN = 32;
    private static final int MAX_OPEN_PER_PLAYER = 2;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Fracture> open = new LinkedHashMap<>();

    public TrashSpatialFractureStore(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = new File(plugin.getDataFolder(), "trash-spatial-fractures.yml");
        YamlStore.registerCriticalWrite(file);
    }

    @Override
    public synchronized void load() {
        open.clear();
        final YamlConfiguration yaml = YamlStore.loadTracked(file, plugin.getLogger());
        if (!file.exists()) return;
        if (yaml.getInt("schema-version", 0) != SCHEMA_VERSION) corrupt("schema-version");
        final ConfigurationSection root = yaml.getConfigurationSection("open");
        if (root == null) return;
        if (root.getKeys(false).size() > MAX_OPEN) corrupt("túl sok nyitott fracture");
        for (final String rawId : root.getKeys(false)) {
            try {
                final UUID id = UUID.fromString(rawId);
                final UUID ownerId = UUID.fromString(root.getString(rawId + ".owner", ""));
                final UUID worldId = UUID.fromString(root.getString(rawId + ".world", ""));
                final long expiresAt = root.getLong(rawId + ".expires-at", 0L);
                final List<BlockSnapshot> blocks = new ArrayList<>();
                for (final Map<?, ?> raw : root.getMapList(rawId + ".blocks")) {
                    blocks.add(new BlockSnapshot(number(raw.get("x")), number(raw.get("y")),
                            number(raw.get("z")), String.valueOf(raw.get("data"))));
                }
                if (expiresAt < 1L || blocks.isEmpty() || blocks.size() > 2) {
                    corrupt("érvénytelen fracture: " + rawId);
                }
                open.put(id, new Fracture(ownerId, worldId, expiresAt, blocks));
            } catch (final RuntimeException malformed) {
                corrupt("érvénytelen fracture: " + rawId);
            }
        }
    }

    public synchronized boolean open(final UUID ownerId, final Block base,
                                     final long durationTicks) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(base, "base");
        final long owned = open.values().stream()
                .filter(fracture -> fracture.ownerId().equals(ownerId)).count();
        if (open.size() >= MAX_OPEN || owned >= MAX_OPEN_PER_PLAYER
                || durationTicks < 20L || durationTicks > 600L) return false;
        final List<BlockSnapshot> snapshots = new ArrayList<>(2);
        for (int dy = 0; dy < 2; dy++) {
            final Block block = base.getRelative(0, dy, 0);
            if (block.getState() instanceof TileState || block.getType().isAir()
                    || block.getX() >> 4 != base.getX() >> 4
                    || block.getZ() >> 4 != base.getZ() >> 4) return false;
            snapshots.add(new BlockSnapshot(block.getX(), block.getY(), block.getZ(),
                    block.getBlockData().getAsString()));
        }
        final UUID id = UUID.randomUUID();
        final Fracture fracture = new Fracture(ownerId, base.getWorld().getUID(),
                System.currentTimeMillis() + durationTicks * 50L, snapshots);
        open.put(id, fracture);
        persistOrRestore(() -> open.remove(id));
        try {
            for (final BlockSnapshot snapshot : snapshots) {
                base.getWorld().getBlockAt(snapshot.x(), snapshot.y(), snapshot.z())
                        .setType(org.bukkit.Material.AIR, false);
            }
        } catch (final RuntimeException failure) {
            restoreNow(id, fracture);
            throw failure;
        }
        scheduleRestore(id, fracture, durationTicks);
        return true;
    }

    /** Restores every journal entry after restart, regardless of its old deadline. */
    public synchronized void recover() {
        for (final Map.Entry<UUID, Fracture> entry : List.copyOf(open.entrySet())) {
            scheduleRestore(entry.getKey(), entry.getValue(), 1L);
        }
    }

    /** Retries journal recovery when a world unavailable during startup becomes owned again. */
    public synchronized void recoverWorld(final World world) {
        Objects.requireNonNull(world, "world");
        for (final Map.Entry<UUID, Fracture> entry : List.copyOf(open.entrySet())) {
            if (entry.getValue().worldId().equals(world.getUID())) {
                scheduleRestore(entry.getKey(), entry.getValue(), 1L);
            }
        }
    }

    /**
     * Requests owner-region restoration before disable. Any request that cannot run remains in the
     * durable journal and is retried by startup/world-load recovery instead of losing its snapshot.
     */
    public synchronized void shutdown() {
        recover();
    }

    private void scheduleRestore(final UUID id, final Fracture fracture, final long delay) {
        final World world = Bukkit.getWorld(fracture.worldId());
        if (world == null) return;
        final BlockSnapshot first = fracture.blocks().getFirst();
        final Location location = new Location(world, first.x(), first.y(), first.z());
        Bukkit.getRegionScheduler().runDelayed(plugin, location,
                ignored -> restoreNow(id, fracture), Math.max(1L, delay));
    }

    private synchronized void restoreNow(final UUID id, final Fracture expected) {
        final Fracture current = open.get(id);
        if (current == null || !current.equals(expected)) return;
        final World world = Bukkit.getWorld(current.worldId());
        if (world == null) return;
        for (final BlockSnapshot snapshot : current.blocks()) {
            final BlockData data = Bukkit.createBlockData(snapshot.data());
            world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z()).setBlockData(data, false);
        }
        open.remove(id);
        persistOrRestore(() -> open.put(id, current));
    }

    @Override
    public synchronized void save() {
        persist();
    }

    private void persistOrRestore(final Runnable restore) {
        try {
            persist();
        } catch (final RuntimeException failure) {
            restore.run();
            throw failure;
        }
    }

    private void persist() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        for (final Map.Entry<UUID, Fracture> entry : open.entrySet()) {
            final String path = "open." + entry.getKey();
            yaml.set(path + ".owner", entry.getValue().ownerId().toString());
            yaml.set(path + ".world", entry.getValue().worldId().toString());
            yaml.set(path + ".expires-at", entry.getValue().expiresAt());
            final List<Map<String, Object>> blocks = new ArrayList<>();
            for (final BlockSnapshot snapshot : entry.getValue().blocks()) {
                blocks.add(Map.of("x", snapshot.x(), "y", snapshot.y(), "z", snapshot.z(),
                        "data", snapshot.data()));
            }
            yaml.set(path + ".blocks", blocks);
        }
        try {
            YamlStore.saveAtomic(file, yaml);
        } catch (final IOException failure) {
            throw new IllegalStateException("Trash spatial fracture mentése sikertelen", failure);
        }
    }

    private static int number(final Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("nem szám");
        return number.intValue();
    }

    private void corrupt(final String reason) {
        YamlStore.failCorrupt(file, plugin.getLogger(), reason);
        throw new IllegalStateException("Sérült Trash spatial fracture: " + reason);
    }

    private record BlockSnapshot(int x, int y, int z, String data) {
        private BlockSnapshot { Objects.requireNonNull(data, "data"); }
    }

    private record Fracture(UUID ownerId, UUID worldId, long expiresAt,
                            List<BlockSnapshot> blocks) {
        private Fracture {
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(worldId, "worldId");
            blocks = List.copyOf(blocks);
        }
    }
}
