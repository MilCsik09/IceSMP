package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for faction territory zones. A zone is either a circular disc or an
 * arbitrary polygon (traced from a series of admin-placed boundary points, e.g.
 * along a city wall), persisted to territories.yml. Each zone carries a
 * {@link TerritoryType} that decides who may build inside it and whether players
 * may claim there. A faction has at most one capital zone.
 *
 * <p>Polygon definition uses a per-player, in-memory point buffer (cleared on
 * quit via {@link PlayerStateCleanup}); the zone map itself is concurrent and
 * read lock-free from region threads.
 */
public final class TerritoryManager implements PersistentStore, PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final File storageFile;
    private final Map<String, Territory> territories = new ConcurrentHashMap<>();
    /** Per-player boundary-point buffer for polygon definition (world + {x,z} points). */
    private final Map<UUID, PointBuffer> pointBuffers = new ConcurrentHashMap<>();

    /** A player's in-progress polygon boundary (volatile, cleared on quit). */
    private static final class PointBuffer {
        private String world;
        private final List<int[]> points = new ArrayList<>();
    }

    public TerritoryManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "territories.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        territories.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
            final ConfigurationSection territoriesSection = yaml.getConfigurationSection("territories");
            if (territoriesSection == null) {
                return;
            }

            for (final String territoryId : territoriesSection.getKeys(false)) {
                final ConfigurationSection section = territoriesSection.getConfigurationSection(territoryId);
                if (section == null) {
                    continue;
                }

                final FactionType faction = FactionType.fromString(section.getString("faction", "NEUTRAL"));
                final String world = section.getString("world", "");
                if (world.isBlank()) {
                    plugin.getLogger().warning("Invalid territory entry '" + territoryId + "' in territories.yml; skipping.");
                    continue;
                }

                // Backward-compat: older entries only had a boolean 'capital'.
                TerritoryType type = TerritoryType.fromInput(section.getString("type", ""));
                if (type == null) {
                    type = section.getBoolean("capital", false) ? TerritoryType.CAPITAL : TerritoryType.FACTION;
                }

                final List<int[]> polygon = readPolygon(section.getStringList("polygon"));
                final String id = territoryId.toLowerCase(Locale.ROOT);
                territories.put(id, new Territory(
                        id,
                        faction,
                        section.getString("name", territoryId),
                        type,
                        world,
                        section.getInt("x", 0),
                        section.getInt("z", 0),
                        Math.max(1, section.getInt("radius", 50)),
                        polygon
                ));
            }

            plugin.getLogger().info("Loaded " + territories.size() + " faction territory zone(s).");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load territories: " + exception.getMessage());
        }
    }

    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final Territory territory : territories.values()) {
                final String basePath = "territories." + territory.id();
                yaml.set(basePath + ".faction", territory.faction().name());
                yaml.set(basePath + ".name", territory.name());
                yaml.set(basePath + ".type", territory.type().name());
                yaml.set(basePath + ".world", territory.world());
                yaml.set(basePath + ".x", territory.x());
                yaml.set(basePath + ".z", territory.z());
                yaml.set(basePath + ".radius", territory.radius());
                yaml.set(basePath + ".capital", territory.capital());
                if (territory.isPolygon()) {
                    yaml.set(basePath + ".polygon", writePolygon(territory.polygon()));
                }
            }

            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save territories: " + exception.getMessage());
        }
    }

    private static List<int[]> readPolygon(final List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        final List<int[]> points = new ArrayList<>();
        for (final String entry : raw) {
            final String[] parts = entry.split(",");
            if (parts.length != 2) {
                continue;
            }
            try {
                points.add(new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())});
            } catch (final NumberFormatException ignored) {
                // skip malformed vertex
            }
        }
        return points.size() >= 3 ? points : null;
    }

    private static List<String> writePolygon(final List<int[]> polygon) {
        final List<String> raw = new ArrayList<>(polygon.size());
        for (final int[] point : polygon) {
            raw.add(point[0] + "," + point[1]);
        }
        return raw;
    }

    /**
     * Defines (or overwrites) a CIRCULAR zone and persists it. Marking a zone as
     * a capital demotes the faction's previous capital to normal faction land.
     *
     * @param id the zone id
     * @param faction the owning faction
     * @param name the display name
     * @param type the zone kind
     * @param center the center location
     * @param radius the radius in blocks
     * @return the stored zone
     */
    public Territory define(final String id, final FactionType faction, final String name,
                            final TerritoryType type, final Location center, final int radius) {
        final String normalizedId = id.toLowerCase(Locale.ROOT);
        demotePreviousCapital(type, faction, normalizedId);

        final Territory territory = new Territory(
                normalizedId,
                faction,
                name == null || name.isBlank() ? normalizedId : name,
                type,
                center.getWorld().getName(),
                center.getBlockX(),
                center.getBlockZ(),
                Math.max(1, radius),
                null
        );
        territories.put(normalizedId, territory);
        save();
        return territory;
    }

    /**
     * Defines (or overwrites) a POLYGON zone from a ring of {@code {x, z}} vertices.
     * The centroid and bounding-circle radius are derived from the vertices.
     *
     * @return the stored zone, or {@code null} when fewer than 3 vertices are given
     */
    public Territory definePolygon(final String id, final FactionType faction, final String name,
                                   final TerritoryType type, final String world, final List<int[]> points) {
        if (points == null || points.size() < 3) {
            return null;
        }
        final String normalizedId = id.toLowerCase(Locale.ROOT);
        demotePreviousCapital(type, faction, normalizedId);

        long sumX = 0;
        long sumZ = 0;
        for (final int[] point : points) {
            sumX += point[0];
            sumZ += point[1];
        }
        final int centroidX = (int) (sumX / points.size());
        final int centroidZ = (int) (sumZ / points.size());
        int boundingRadius = 1;
        for (final int[] point : points) {
            final int dx = point[0] - centroidX;
            final int dz = point[1] - centroidZ;
            boundingRadius = Math.max(boundingRadius, (int) Math.ceil(Math.sqrt((double) dx * dx + (double) dz * dz)));
        }

        final Territory territory = new Territory(
                normalizedId,
                faction,
                name == null || name.isBlank() ? normalizedId : name,
                type,
                world,
                centroidX,
                centroidZ,
                boundingRadius,
                List.copyOf(points)
        );
        territories.put(normalizedId, territory);
        save();
        return territory;
    }

    private void demotePreviousCapital(final TerritoryType type, final FactionType faction, final String keepId) {
        if (type != TerritoryType.CAPITAL) {
            return;
        }
        for (final Map.Entry<String, Territory> entry : territories.entrySet()) {
            final Territory existing = entry.getValue();
            if (existing.capital() && existing.faction() == faction && !existing.id().equals(keepId)) {
                entry.setValue(new Territory(existing.id(), existing.faction(), existing.name(),
                        TerritoryType.FACTION, existing.world(), existing.x(), existing.z(),
                        existing.radius(), existing.polygon()));
            }
        }
    }

    public boolean remove(final String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        final boolean removed = territories.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Territory getById(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        return territories.get(id.toLowerCase(Locale.ROOT));
    }

    /** Whether the location lies inside ANY faction's capital (banking/exchange gate). */
    public boolean isInCapital(final Location location) {
        final Territory territory = getTerritoryAt(location);
        return territory != null && territory.capital();
    }

    /**
     * Whether players are forbidden to lay a personal {@code /claim} at the
     * location: true when it falls inside a PROTECTED zone (protected city/faction
     * or capital). Normal faction land stays claimable.
     */
    public boolean isClaimBlockedAt(final Location location) {
        final Territory territory = getTerritoryAt(location);
        return territory != null && !territory.type().isClaimable();
    }

    /**
     * Gets the zone at a location. When zones overlap, the smallest (most specific)
     * one wins — by radius/bounding-radius — with protected zones and capitals
     * breaking ties so a protective zone always shadows plain faction land.
     *
     * @param location the location to check
     * @return the zone, or null if unclaimed wilderness
     */
    public Territory getTerritoryAt(final Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        Territory best = null;
        for (final Territory territory : territories.values()) {
            if (!territory.contains(location.getWorld().getName(), location.getX(), location.getZ())) {
                continue;
            }

            if (best == null
                    || territory.radius() < best.radius()
                    || (territory.radius() == best.radius()
                        && territory.type().isProtectedZone() && !best.type().isProtectedZone())) {
                best = territory;
            }
        }

        return best;
    }

    public Territory getCapital(final FactionType faction) {
        for (final Territory territory : territories.values()) {
            if (territory.capital() && territory.faction() == faction) {
                return territory;
            }
        }

        return null;
    }

    public Collection<Territory> all() {
        return territories.values();
    }

    // ==================== polygon boundary point buffer (per player) ====================

    /**
     * Records the player's standing block as the next boundary point. A buffer is
     * world-scoped: moving to another world resets it. Returns the new point count.
     */
    public int addPoint(final Player player) {
        final Location location = player.getLocation();
        final String worldName = location.getWorld().getName();
        final PointBuffer buffer = pointBuffers.computeIfAbsent(player.getUniqueId(), id -> new PointBuffer());
        synchronized (buffer) {
            if (!worldName.equals(buffer.world)) {
                buffer.points.clear();
                buffer.world = worldName;
            }
            buffer.points.add(new int[] {location.getBlockX(), location.getBlockZ()});
            return buffer.points.size();
        }
    }

    /** Removes the last boundary point; returns the remaining count (-1 if none). */
    public int undoPoint(final UUID playerId) {
        final PointBuffer buffer = pointBuffers.get(playerId);
        if (buffer == null) {
            return -1;
        }
        synchronized (buffer) {
            if (buffer.points.isEmpty()) {
                return -1;
            }
            buffer.points.remove(buffer.points.size() - 1);
            return buffer.points.size();
        }
    }

    public void clearPoints(final UUID playerId) {
        pointBuffers.remove(playerId);
    }

    /** A snapshot copy of the player's current boundary points (never null). */
    public List<int[]> getPoints(final UUID playerId) {
        final PointBuffer buffer = pointBuffers.get(playerId);
        if (buffer == null) {
            return List.of();
        }
        synchronized (buffer) {
            final List<int[]> copy = new ArrayList<>(buffer.points.size());
            for (final int[] point : buffer.points) {
                copy.add(point.clone());
            }
            return copy;
        }
    }

    public String getPointsWorld(final UUID playerId) {
        final PointBuffer buffer = pointBuffers.get(playerId);
        return buffer == null ? null : buffer.world;
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        pointBuffers.remove(playerId);
    }
}
