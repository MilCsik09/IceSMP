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
 * N25 — esemény-spawnpontok (teszter-kérés): a világesemények ne (csak) játékosra
 * spawnoljanak, hanem HELYRE. Két út, eseményenként élő configgal
 * (world-events.anchors.&lt;esemény&gt;.mode):
 * <ul>
 *   <li><b>points</b> — admin-kijelölt fix pontok (/events spawnpoint add — pl. állandó
 *       világboss-aréna); több pont közül véletlen választás;</li>
 *   <li><b>random</b> — véletlen koordináta a fő világ spawnja körüli sávban
 *       (world-events.anchors.random-radius);</li>
 *   <li><b>mixed</b> — pont, ha van; különben random;</li>
 *   <li><b>player</b> (default) — a régi játékos-horgony marad.</li>
 * </ul>
 * A pontok restart-állók (event-spawnpoints.yml). A hívó a kapott helyet a SAJÁT
 * régió-hopjával dolgozza fel (Y-t is ő igazítja) — itt csak adat születik.
 */
public final class EventSpawnPointManager implements PersistentStore {

    public record SpawnPoint(String id, String eventKey, String world, int x, int y, int z) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;
    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();

    public EventSpawnPointManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "event-spawnpoints.yml");
        plugin.getDataFolder().mkdirs();
    }

    /** Pont felvétele; a visszaadott id a végleges (ütközésnél számozott). */
    public synchronized String add(final String requestedId, final String eventKey, final Location location) {
        String id = (requestedId == null || requestedId.isBlank()
                ? eventKey + "-" + (points.size() + 1) : requestedId).toLowerCase(Locale.ROOT);
        while (points.containsKey(id)) {
            id = id + "x";
        }
        points.put(id, new SpawnPoint(id, eventKey.toLowerCase(Locale.ROOT),
                location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()));
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
     * A hívó esemény horgony-helye a configolt mód szerint, vagy null = maradjon a
     * játékos-horgony út. Az "any" esemény-kulcsú pontok minden eseménynek jók.
     */
    public Location resolveAnchorLocation(final String eventKey) {
        final String mode = configManager.getString(
                "world-events.anchors." + eventKey + ".mode", "player").toLowerCase(Locale.ROOT);
        if ("player".equals(mode)) {
            return null;
        }
        if ("points".equals(mode) || "mixed".equals(mode)) {
            final List<SpawnPoint> matching = new ArrayList<>();
            for (final SpawnPoint point : points.values()) {
                if (point.eventKey().equals(eventKey) || "any".equals(point.eventKey())) {
                    matching.add(point);
                }
            }
            if (!matching.isEmpty()) {
                final SpawnPoint pick = matching.get(ThreadLocalRandom.current().nextInt(matching.size()));
                final World world = Bukkit.getWorld(pick.world());
                if (world != null) {
                    return new Location(world, pick.x() + 0.5D, pick.y(), pick.z() + 0.5D);
                }
            }
            if ("points".equals(mode)) {
                return null; // nincs használható pont — vissza a játékos-útra
            }
        }
        if ("random".equals(mode) || "mixed".equals(mode)) {
            final World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world == null) {
                return null;
            }
            final int radius = Math.max(64, configManager.getInt("world-events.anchors.random-radius", 1500));
            final Location center = world.getSpawnLocation();
            final int x = center.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final int z = center.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            return new Location(world, x + 0.5D, center.getY(), z + 0.5D);
        }
        return null;
    }

    @Override
    public synchronized void load() {
        points.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
        final ConfigurationSection root = yaml.getConfigurationSection("points");
        if (root == null) {
            return;
        }
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection p = root.getConfigurationSection(id);
            if (p == null) {
                continue;
            }
            points.put(id, new SpawnPoint(id, p.getString("event", "any"),
                    p.getString("world", "world"), p.getInt("x"), p.getInt("y"), p.getInt("z")));
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
            plugin.getLogger().severe("Failed to save event-spawnpoints.yml: " + exception.getMessage());
            throw new java.io.UncheckedIOException("Failed to save event-spawnpoints.yml", exception);
        }
    }
}
