package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * N25 — esemény-spawnpontok: a világesemények ne csak játékosra,
 * hanem admin által kijelölt vagy véletlen helyre is horgonyozhassanak.
 */
public final class EventSpawnPointManager implements PersistentStore {

    public record SpawnPoint(String id, String eventKey, String world, int x, int y, int z) { }

    private static volatile EventSpawnPointManager active;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;
    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();

    public EventSpawnPointManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "event-spawnpoints.yml");
        plugin.getDataFolder().mkdirs();
        active = this;
    }

    /** Runtime bridge for systems installed after the core DI graph is assembled. */
    public static EventSpawnPointManager current() {
        return active;
    }

    public synchronized String add(final String requestedId, final String eventKey,
                                   final Location location) {
        String id = (requestedId == null || requestedId.isBlank()
                ? eventKey + "-" + (points.size() + 1) : requestedId).toLowerCase(Locale.ROOT);
        while (points.containsKey(id)) {
            id = id + "x";
        }
        points.put(id, new SpawnPoint(id, eventKey.toLowerCase(Locale.ROOT),
                location.getWorld().getName(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ()));
        save();
        return id;
    }

    public synchronized boolean remove(final String id) {
        final boolean removed = points.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public List<SpawnPoint> list() {
        return List.copyOf(points.values());
    }

    /**
     * Resolves the configured anchor or returns null for the player-anchor fallback.
     * World-boss legacy fixed anchors are normalized to the exact centre coordinate of
     * their chunk. The manager's historical ±8 randomization then remains inside that
     * same chunk, so the scheduled Folia region always owns the eventual probe column.
     */
    public Location resolveAnchorLocation(final String eventKey) {
        final String normalizedEvent = eventKey.toLowerCase(Locale.ROOT);
        final String mode = configManager.getString(
                "world-events.anchors." + normalizedEvent + ".mode", "player")
                .toLowerCase(Locale.ROOT);
        if ("player".equals(mode)) {
            return null;
        }
        if ("points".equals(mode) || "mixed".equals(mode)) {
            final List<SpawnPoint> matching = new ArrayList<>();
            for (final SpawnPoint point : points.values()) {
                if (point.eventKey().equals(normalizedEvent) || "any".equals(point.eventKey())) {
                    matching.add(point);
                }
            }
            if (!matching.isEmpty()) {
                final SpawnPoint pick = matching.get(
                        ThreadLocalRandom.current().nextInt(matching.size()));
                final World world = Bukkit.getWorld(pick.world());
                if (world != null) {
                    return anchorLocation(normalizedEvent, world,
                            pick.x(), pick.y(), pick.z());
                }
            }
            if ("points".equals(mode)) {
                return null;
            }
        }
        if ("random".equals(mode) || "mixed".equals(mode)) {
            final World world = Bukkit.getWorlds().isEmpty()
                    ? null : Bukkit.getWorlds().getFirst();
            if (world == null) {
                return null;
            }
            final int radius = Math.max(64,
                    configManager.getInt("world-events.anchors.random-radius", 1500));
            final Location center = world.getSpawnLocation();
            final int x = center.getBlockX()
                    + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final int z = center.getBlockZ()
                    + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            return anchorLocation(normalizedEvent, world, x, center.getBlockY(), z);
        }
        return null;
    }

    private static Location anchorLocation(final String eventKey, final World world,
                                           final int x, final int y, final int z) {
        if ("world-boss".equals(eventKey)) {
            return new Location(world,
                    EventSpawnSafetyPolicy.chunkCenterCoordinate(x), y,
                    EventSpawnSafetyPolicy.chunkCenterCoordinate(z));
        }
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    @Override
    public synchronized void load() {
        points.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        final ConfigurationSection root = yaml.getConfigurationSection("points");
        if (root == null) {
            return;
        }
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection point = root.getConfigurationSection(id);
            if (point == null) {
                continue;
            }
            points.put(id, new SpawnPoint(id,
                    point.getString("event", "any"),
                    point.getString("world", "world"),
                    point.getInt("x"), point.getInt("y"), point.getInt("z")));
        }
    }

    @Override
    public synchronized void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final SpawnPoint point : points.values()) {
                final String base = "points." + point.id() + ".";
                yaml.set(base + "event", point.eventKey());
                yaml.set(base + "world", point.world());
                yaml.set(base + "x", point.x());
                yaml.set(base + "y", point.y());
                yaml.set(base + "z", point.z());
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save event-spawnpoints.yml: "
                    + exception.getMessage());
            throw new java.io.UncheckedIOException(
                    "Failed to save event-spawnpoints.yml", exception);
        }
    }
}
