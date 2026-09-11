package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.BlockCuboid;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.territory.TerritoryAdjustment;
import hu.taliann.icesmp.territory.TerritoryAdjustmentReceipt;
import hu.taliann.icesmp.territory.TerritoryAdjustmentResult;
import hu.taliann.icesmp.territory.TerritoryRevision;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for faction territory zones. A zone is either a circular disc or an
 * arbitrary polygon (traced from a series of admin-placed boundary points, e.g.
 * along a city wall), optionally limited to a vertical band, and persisted to
 * territories.yml. Each zone carries a {@link TerritoryType} that decides who may
 * build inside it and whether players may claim there. A faction has at most one
 * capital zone.
 *
 * <p>Threading (Folia): the hot-path lookup is lock-free — a volatile chunk index
 * maps {@code world;chunkX;chunkZ} to the (few) zones overlapping that chunk, and
 * every mutation rebuilds and atomically swaps the index under {@code synchronized}
 * (mutations are rare, command-driven). Polygon definition uses a per-player,
 * in-memory point buffer cleared on quit via {@link PlayerStateCleanup}.
 */
public final class TerritoryManager implements PersistentStore, PlayerStateCleanup {

    private final java.util.logging.Logger logger;
    @FunctionalInterface public interface StateWriter {
        void save(File file, YamlConfiguration yaml) throws IOException;
    }
    private final StateWriter writer;
    private volatile boolean writeUncertain;
    private volatile boolean loaded;
    private final Map<UUID, hu.taliann.icesmp.territory.TerritoryAdjustmentReceipt> adjustmentReceipts = new java.util.LinkedHashMap<>();
    private static final int MAX_ADJUSTMENT_RECEIPTS = 4096;
    private final File storageFile;
    private final Map<String, Territory> territories = new ConcurrentHashMap<>();
    /** Admin-set kingdom spawn points per faction (first-join / faction-join / respawn target). */
    private final Map<FactionType, FactionSpawn> factionSpawns = new ConcurrentHashMap<>();
    /**
     * world;chunkX;chunkZ → zones overlapping that chunk. Rebuilt and swapped whole
     * on every (rare) mutation, so the hot-path lookup is a lock-free read of an
     * immutable snapshot.
     */
    private record Published(Map<String, Territory> territories, Map<FactionType, FactionSpawn> spawns,
                             Map<String, List<Territory>> chunks,
                             Map<UUID, hu.taliann.icesmp.territory.TerritoryAdjustmentReceipt> receipts) { }
    private volatile Published published = new Published(Map.of(), Map.of(), Map.of(), Map.of());
    /** Per-player boundary-point buffer for polygon definition (world + {x,z} points). */
    private final Map<UUID, PointBuffer> pointBuffers = new ConcurrentHashMap<>();

    /** A player's in-progress polygon boundary (volatile, cleared on quit). */
    private static final class PointBuffer {
        private String world;
        private final List<int[]> points = new ArrayList<>();
    }

    public TerritoryManager(final JavaPlugin plugin) {
        this(new File(plugin.getDataFolder(), "territories.yml"), plugin.getLogger(), YamlStore::saveAtomic);
    }

    /** Explicit persistence port for native lifecycle/failure verification without Bukkit mocks. */
    public TerritoryManager(final File storageFile, final java.util.logging.Logger logger, final StateWriter writer) {
        this.storageFile = java.util.Objects.requireNonNull(storageFile);
        this.logger = java.util.Objects.requireNonNull(logger);
        this.writer = java.util.Objects.requireNonNull(writer);
    }

    /**
     * One faction's admin-set spawn point (kingdom spawn): the EXACT standing position of the
     * admin who set it — full Y and view direction included, so no highest-block guessing.
     * Stored by world name and resolved lazily, because the world may not be loaded yet.
     */
    public record FactionSpawn(String world, double x, double y, double z, float yaw, float pitch) {

        /** Resolves to a live Location, or null if the world is missing. */
        public Location toLocation() {
            final org.bukkit.World resolved = org.bukkit.Bukkit.getWorld(world);
            return resolved == null ? null : new Location(resolved, x, y, z, yaw, pitch);
        }
    }

    /** Sets a faction's kingdom spawn to the given exact position and persists it. */
    public synchronized void setFactionSpawn(final FactionType faction, final Location location) {
        if (faction == null || location == null || location.getWorld() == null) {
            return;
        }
        factionSpawns.put(faction, new FactionSpawn(location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()));
        commitChanges();
    }

    /** The faction's kingdom spawn as a live Location, or null if unset / world missing. */
    public Location getFactionSpawn(final FactionType faction) {
        final FactionSpawn spawn = faction == null ? null : published.spawns().get(faction);
        return spawn == null ? null : spawn.toLocation();
    }

    public synchronized void load() {
        writeUncertain = true;
        territories.clear();
        factionSpawns.clear();
        adjustmentReceipts.clear();
        loadFactionSpawns();

        if (!storageFile.exists()) {
            rebuildIndex();
            return;
        }

        try {
            final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, logger);
            readAdjustmentReceipts(yaml);
            final ConfigurationSection territoriesSection = yaml.getConfigurationSection("territories");
            if (territoriesSection == null) {
                rebuildIndex();
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
                    logger.warning("Invalid territory entry '" + territoryId + "' in territories.yml; skipping.");
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
                        polygon,
                        section.getInt("min-y", Territory.NO_MIN_Y),
                        section.getInt("max-y", Territory.NO_MAX_Y)
                ));
            }

            hu.taliann.icesmp.utils.StartupLog.info(logger, null, "Loaded " + territories.size() + " faction territory zone(s).");
        } catch (final Exception exception) {
            logger.severe("Failed to load territories: " + exception.getMessage());
            throw new IllegalStateException("Territory state unavailable", exception);
        }
        rebuildIndex();
    }

    public synchronized void save() {
        persist(() -> true);
    }

    private static final class AdmissionRejected extends RuntimeException { }

    private void persist(final java.util.function.BooleanSupplier finalAdmission) {
        if (writeUncertain) throw new IllegalStateException("Territory write requires reload assessment");
        boolean enteredWrite = false;
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
                if (territory.minY() != Territory.NO_MIN_Y) {
                    yaml.set(basePath + ".min-y", territory.minY());
                }
                if (territory.maxY() != Territory.NO_MAX_Y) {
                    yaml.set(basePath + ".max-y", territory.maxY());
                }
            }

            for (final Map.Entry<FactionType, FactionSpawn> entry : factionSpawns.entrySet()) {
                final String basePath = "spawns." + entry.getKey().name();
                final FactionSpawn spawn = entry.getValue();
                yaml.set(basePath + ".world", spawn.world());
                yaml.set(basePath + ".x", spawn.x());
                yaml.set(basePath + ".y", spawn.y());
                yaml.set(basePath + ".z", spawn.z());
                yaml.set(basePath + ".yaw", spawn.yaw());
                yaml.set(basePath + ".pitch", spawn.pitch());
            }

            for (final var receipt : adjustmentReceipts.values()) {
                final String base = "developer-adjustments." + receipt.operationId();
                yaml.set(base + ".territory", receipt.territoryId());
                yaml.set(base + ".before", receipt.beforeFingerprint());
                yaml.set(base + ".after", receipt.afterFingerprint());
                yaml.set(base + ".request", receipt.adjustmentFingerprint());
                yaml.set(base + ".committed-at", receipt.committedAt());
            }
            if (!finalAdmission.getAsBoolean()) throw new AdmissionRejected();
            enteredWrite = true;
            writer.save(storageFile, yaml);
        } catch (final IOException exception) {
            writeUncertain = true;
            logger.severe("Failed to save territories: " + exception.getMessage());
            throw new java.io.UncheckedIOException("Failed to save territories", exception);
        } catch (final RuntimeException | Error failure) {
            if (enteredWrite) writeUncertain = true;
            throw failure;
        }
    }

    /** Only this writer lock stages changes; lock-free readers see one acknowledged map/index view. */
    private void commitChanges() {
        commitChanges(() -> true);
    }

    private void commitChanges(final java.util.function.BooleanSupplier finalAdmission) {
        final Published before = published;
        try {
            if (writeUncertain) throw new IllegalStateException("Territory write requires reload assessment");
            final Published after = new Published(Map.copyOf(territories), Map.copyOf(factionSpawns),
                    buildIndex(territories), Map.copyOf(adjustmentReceipts));
            persist(finalAdmission);
            published = after;
        } catch (final RuntimeException | Error failure) {
            territories.clear(); territories.putAll(before.territories());
            factionSpawns.clear(); factionSpawns.putAll(before.spawns());
            adjustmentReceipts.clear(); adjustmentReceipts.putAll(before.receipts());
            throw failure;
        }
    }

    private void readAdjustmentReceipts(final YamlConfiguration yaml) {
        if (yaml.contains("developer-adjustments") && !yaml.isConfigurationSection("developer-adjustments")) {
            throw new IllegalStateException("Invalid territory adjustment ledger");
        }
        final ConfigurationSection section = yaml.getConfigurationSection("developer-adjustments");
        if (section == null) return;
        if (section.getKeys(false).size() > MAX_ADJUSTMENT_RECEIPTS) throw new IllegalStateException("Territory adjustment ledger capacity exceeded");
        for (final String key : section.getKeys(false)) {
            final UUID operation = UUID.fromString(key);
            final ConfigurationSection entry = section.getConfigurationSection(key);
            if (!operation.toString().equals(key) || entry == null
                    || !entry.isString("territory") || !entry.isString("before")
                    || !entry.isString("after") || !entry.isString("request")
                    || !entry.isLong("committed-at") && !entry.isInt("committed-at")) {
                throw new IllegalStateException("Invalid territory adjustment acknowledgement");
            }
            final var receipt = new TerritoryAdjustmentReceipt(operation, entry.getString("territory"),
                    entry.getString("before"), entry.getString("after"), entry.getString("request"), entry.getLong("committed-at"));
            if (adjustmentReceipts.putIfAbsent(operation, receipt) != null) throw new IllegalStateException("Duplicate territory adjustment acknowledgement");
        }
    }

    /**
     * Storage-executor entry point: no live Bukkit reads. The caller must durably prepare its
     * operation first. The real acknowledgement and zone state share one fsynced replacement.
     * A capital collision is explicit; this bounded transaction never silently edits a second zone.
     */
    public synchronized TerritoryAdjustmentResult adjustConditionally(final UUID operationId, final String id,
            final String expectedFingerprint, final TerritoryAdjustment adjustment, final long now,
            final java.util.function.BooleanSupplier finalAdmission) {
        java.util.Objects.requireNonNull(operationId); java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(adjustment); java.util.Objects.requireNonNull(finalAdmission);
        if (now < 0 || expectedFingerprint == null || !expectedFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid territory adjustment admission");
        }
        if (!loaded || writeUncertain) throw new IllegalStateException("Territory state unavailable for adjustment");
        if (!finalAdmission.getAsBoolean()) return TerritoryAdjustmentResult.rejected(TerritoryAdjustmentResult.Status.DENIED);
        final String normalized = id.toLowerCase(Locale.ROOT);
        final TerritoryAdjustmentReceipt previous = adjustmentReceipts.get(operationId);
        if (previous != null) {
            if (!previous.territoryId().equals(normalized) || !previous.beforeFingerprint().equals(expectedFingerprint)
                    || !previous.adjustmentFingerprint().equals(adjustment.fingerprint())) {
                return TerritoryAdjustmentResult.rejected(TerritoryAdjustmentResult.Status.CONFLICT);
            }
            return new TerritoryAdjustmentResult(TerritoryAdjustmentResult.Status.ALREADY_APPLIED, java.util.Optional.of(previous));
        }
        final Territory before = published.territories().get(normalized);
        if (before == null) return TerritoryAdjustmentResult.rejected(TerritoryAdjustmentResult.Status.UNKNOWN_TERRITORY);
        if (!TerritoryRevision.fingerprint(before).equals(expectedFingerprint)) return TerritoryAdjustmentResult.rejected(TerritoryAdjustmentResult.Status.CONFLICT);
        final Territory after = adjustment.apply(before);
        if (before.equals(after)) return TerritoryAdjustmentResult.rejected(TerritoryAdjustmentResult.Status.NO_CHANGE);
        if (after.capital() && published.territories().values().stream()
                .anyMatch(zone -> zone.capital() && zone.faction() == after.faction() && !zone.id().equals(after.id()))) {
            return TerritoryAdjustmentResult.rejected(TerritoryAdjustmentResult.Status.CAPITAL_CONFLICT);
        }
        if (adjustmentReceipts.size() >= MAX_ADJUSTMENT_RECEIPTS) return TerritoryAdjustmentResult.rejected(TerritoryAdjustmentResult.Status.CAPACITY);
        final var receipt = new TerritoryAdjustmentReceipt(operationId, normalized, expectedFingerprint,
                TerritoryRevision.fingerprint(after), adjustment.fingerprint(), now);
        if (!finalAdmission.getAsBoolean()) return TerritoryAdjustmentResult.rejected(TerritoryAdjustmentResult.Status.DENIED);
        territories.put(normalized, after); adjustmentReceipts.put(operationId, receipt);
        try { commitChanges(finalAdmission); }
        catch (final AdmissionRejected denied) { return TerritoryAdjustmentResult.rejected(TerritoryAdjustmentResult.Status.DENIED); }
        return new TerritoryAdjustmentResult(TerritoryAdjustmentResult.Status.APPLIED, java.util.Optional.of(receipt));
    }

    /** Native observed-state assessment; reads never replay a mutation or consume a receipt. */
    public java.util.Optional<TerritoryAdjustmentReceipt> adjustmentReceipt(final UUID operationId) {
        return java.util.Optional.ofNullable(published.receipts().get(java.util.Objects.requireNonNull(operationId)));
    }

    public boolean adjustmentStateAvailable() { return loaded && !writeUncertain; }

    /** Reads the per-faction kingdom spawns from territories.yml (own pass, runs before the zone early-returns). */
    private void loadFactionSpawns() {
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, logger);
        final ConfigurationSection section = yaml.getConfigurationSection("spawns");
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final FactionType faction = FactionType.fromInput(key);
            final String world = section.getString(key + ".world", "");
            if (faction == null || world.isBlank()) {
                logger.warning("Hibás frakció-spawn bejegyzés kihagyva: " + key);
                continue;
            }
            factionSpawns.put(faction, new FactionSpawn(world,
                    section.getDouble(key + ".x"), section.getDouble(key + ".y"), section.getDouble(key + ".z"),
                    (float) section.getDouble(key + ".yaw"), (float) section.getDouble(key + ".pitch")));
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
    public synchronized Territory define(final String id, final FactionType faction, final String name,
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
                null,
                Territory.NO_MIN_Y,
                Territory.NO_MAX_Y
        );
        territories.put(normalizedId, territory);
        commitChanges();
        return territory;
    }

    /**
     * Defines (or overwrites) a POLYGON zone from a ring of {@code {x, z}} vertices.
     * The centroid and bounding-circle radius are derived from the vertices.
     *
     * @return the stored zone, or {@code null} when fewer than 3 vertices are given
     */
    public synchronized Territory definePolygon(final String id, final FactionType faction, final String name,
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
                List.copyOf(points),
                Territory.NO_MIN_Y,
                Territory.NO_MAX_Y
        );
        territories.put(normalizedId, territory);
        commitChanges();
        return territory;
    }

    /**
     * Defines an exact block-inclusive cuboid in one atomic mutation. The X/Z
     * footprint is persisted through the existing polygon schema while the selected
     * Y range is stored directly, so no migration or transient full-height zone is
     * involved.
     */
    public synchronized Territory defineCuboid(final String id, final FactionType faction, final String name,
                                               final TerritoryType type, final BlockCuboid bounds) {
        if (id == null || id.isBlank() || faction == null || type == null || bounds == null) {
            throw new IllegalArgumentException("id, faction, type and bounds are required");
        }
        final List<int[]> points = bounds.footprintPolygon();
        final String normalizedId = id.toLowerCase(Locale.ROOT);

        // Use an actual selected block as the operational centre. Averaging the
        // outer polygon edges with integer division rounds negative half-block
        // centres toward zero and can place a one-block cuboid's centre outside.
        final int centroidX = bounds.centerX();
        final int centroidZ = bounds.centerZ();
        int boundingRadius = 1;
        for (final int[] point : points) {
            final long dx = (long) point[0] - centroidX;
            final long dz = (long) point[1] - centroidZ;
            boundingRadius = Math.max(boundingRadius,
                    Math.toIntExact((long) Math.ceil(Math.sqrt((double) dx * dx + (double) dz * dz))));
        }

        final Territory territory = new Territory(
                normalizedId,
                faction,
                name == null || name.isBlank() ? normalizedId : name,
                type,
                bounds.world(),
                centroidX,
                centroidZ,
                boundingRadius,
                List.copyOf(points),
                bounds.minY(),
                bounds.maxY()
        );

        // Validation and candidate construction complete before the old capital is
        // demoted, so a bad/overflowing selection cannot leave the faction seatless.
        demotePreviousCapital(type, faction, normalizedId);
        territories.put(normalizedId, territory);
        commitChanges();
        return territory;
    }

    private void demotePreviousCapital(final TerritoryType type, final FactionType faction, final String keepId) {
        if (type != TerritoryType.CAPITAL) {
            return;
        }
        for (final Map.Entry<String, Territory> entry : territories.entrySet()) {
            final Territory existing = entry.getValue();
            if (existing.capital() && existing.faction() == faction && !existing.id().equals(keepId)) {
                entry.setValue(withType(existing, TerritoryType.FACTION));
            }
        }
    }

    // ==================== edits (rename / resize / settype / sety) ====================

    /** Renames an existing zone. Returns the updated zone, or null if unknown. */
    public synchronized Territory rename(final String id, final String name) {
        final Territory existing = getById(id);
        if (existing == null || name == null || name.isBlank()) {
            return null;
        }
        final Territory updated = new Territory(existing.id(), existing.faction(), name, existing.type(),
                existing.world(), existing.x(), existing.z(), existing.radius(), existing.polygon(),
                existing.minY(), existing.maxY());
        territories.put(existing.id(), updated);
        commitChanges();
        return updated;
    }

    /** Resizes a CIRCULAR zone's radius. Returns null if unknown or a polygon. */
    public synchronized Territory resize(final String id, final int radius) {
        final Territory existing = getById(id);
        if (existing == null || existing.isPolygon()) {
            return null;
        }
        final Territory updated = new Territory(existing.id(), existing.faction(), existing.name(), existing.type(),
                existing.world(), existing.x(), existing.z(), Math.max(1, radius), null,
                existing.minY(), existing.maxY());
        territories.put(existing.id(), updated);
        commitChanges();
        return updated;
    }

    /** Changes a zone's type (re-running capital demotion when promoting to capital). */
    public synchronized Territory setType(final String id, final TerritoryType type) {
        final Territory existing = getById(id);
        if (existing == null) {
            return null;
        }
        demotePreviousCapital(type, existing.faction(), existing.id());
        final Territory updated = withType(existing, type);
        territories.put(existing.id(), updated);
        commitChanges();
        return updated;
    }

    /**
     * Sets (or clears) a zone's vertical band. Pass {@link Territory#NO_MIN_Y} /
     * {@link Territory#NO_MAX_Y} to leave that side unbounded. Returns null if unknown.
     */
    public synchronized Territory setYBounds(final String id, final int minY, final int maxY) {
        final Territory existing = getById(id);
        if (existing == null) {
            return null;
        }
        final int lo = Math.min(minY, maxY);
        final int hi = Math.max(minY, maxY);
        final Territory updated = new Territory(existing.id(), existing.faction(), existing.name(), existing.type(),
                existing.world(), existing.x(), existing.z(), existing.radius(), existing.polygon(), lo, hi);
        territories.put(existing.id(), updated);
        commitChanges();
        return updated;
    }

    /**
     * Ownership change WITHOUT re-definition: the shape (circle radius or polygon), the world,
     * the centre and the vertical band all stay as they were. The raid capture used to call
     * {@code define}/{@code definePolygon} instead, and those base paths build a full-height
     * zone — a surface-only or underground zone silently became world-height on capture,
     * changing protection, PvP, mob rules and capital law with it.
     *
     * @param type the new zone type (a captured zone is never a capital)
     * @return the updated zone, or null if unknown
     */
    public synchronized Territory setOwner(final String id, final FactionType faction, final TerritoryType type) {
        final Territory existing = getById(id);
        if (existing == null || faction == null || type == null) {
            return null;
        }
        demotePreviousCapital(type, faction, existing.id());
        final Territory updated = new Territory(existing.id(), faction, existing.name(), type,
                existing.world(), existing.x(), existing.z(), existing.radius(), existing.polygon(),
                existing.minY(), existing.maxY());
        territories.put(existing.id(), updated);
        commitChanges();
        return updated;
    }

    private static Territory withType(final Territory existing, final TerritoryType type) {
        return new Territory(existing.id(), existing.faction(), existing.name(), type, existing.world(),
                existing.x(), existing.z(), existing.radius(), existing.polygon(),
                existing.minY(), existing.maxY());
    }

    public synchronized boolean remove(final String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        final boolean removed = territories.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            commitChanges();
        }
        return removed;
    }

    public Territory getById(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        return published.territories().get(id.toLowerCase(Locale.ROOT));
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
     * Gets the zone at a location (Y included when the zone is height-limited). When
     * zones overlap, the smallest (most specific) one wins — by radius/bounding-radius
     * — with protected zones and capitals breaking ties so a protective zone always
     * shadows plain faction land. Lock-free: reads an immutable chunk-index snapshot.
     *
     * @param location the location to check
     * @return the zone, or null if unclaimed wilderness
     */
    public Territory getTerritoryAt(final Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        final String worldName = location.getWorld().getName();
        final List<Territory> candidates = published.chunks().get(
                chunkKey(worldName, location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (candidates == null) {
            return null;
        }

        Territory best = null;
        for (final Territory territory : candidates) {
            if (!territory.contains(worldName, location.getX(), location.getY(), location.getZ())) {
                continue;
            }
            if (best == null || shadows(territory, best)) {
                best = territory;
            }
        }
        return best;
    }

    /**
     * Whether {@code candidate} should win over the current {@code best} at an
     * overlapping point. A PROTECTED zone always shadows a non-protected one (so
     * the map's shield can never be undercut by a smaller faction zone drawn
     * inside it); otherwise the most specific (smallest radius/bounding-radius) wins.
     */
    private static boolean shadows(final Territory candidate, final Territory best) {
        final boolean candidateProtected = candidate.type().isProtectedZone();
        if (candidateProtected != best.type().isProtectedZone()) {
            return candidateProtected;
        }
        return candidate.radius() < best.radius()
                || candidate.radius() == best.radius() && candidate.id().compareTo(best.id()) < 0;
    }

    /**
     * 2D (column) zone lookup — like {@link #getTerritoryAt} but ignoring any Y
     * band. Used by the claim veto: a claim is a tall box, so its footprint must
     * not overlap a protected zone's footprint at ANY height, even a Y-limited one.
     */
    public Territory getTerritoryColumnAt(final String worldName, final int x, final int z) {
        final List<Territory> candidates = published.chunks().get(chunkKey(worldName, x >> 4, z >> 4));
        if (candidates == null) {
            return null;
        }
        Territory best = null;
        for (final Territory territory : candidates) {
            if (!territory.contains(worldName, x, z)) {
                continue;
            }
            if (best == null || shadows(territory, best)) {
                best = territory;
            }
        }
        return best;
    }

    public Territory getCapital(final FactionType faction) {
        for (final Territory territory : published.territories().values()) {
            if (territory.capital() && territory.faction() == faction) {
                return territory;
            }
        }

        return null;
    }

    public Collection<Territory> all() {
        return published.territories().values();
    }

    /**
     * Zóna-rámpás mob-skálázás (tulaj-kérés): a legközelebbi BIZTONSÁGOS zóna
     * (faction/protected/capital — a doom-gate és a dungeon NEM számít) peremétől
     * mért távolság blokkban. A zóna belsejében 0; ha a világban nincs ilyen zóna,
     * -1. A becslés a zóna befoglaló körével számol (poligonnál a centroid + a
     * befoglaló sugár) — a rámpához ez a pontosság elég, és lock-mentesen olcsó.
     *
     * @param location a vizsgált hely
     * @return távolság a legközelebbi biztonságos zóna peremétől, vagy -1
     */
    public double distanceFromNearestSafeZoneEdge(final Location location) {
        if (location == null || location.getWorld() == null) {
            return -1.0D;
        }
        final String worldName = location.getWorld().getName();
        double best = -1.0D;
        for (final Territory zone : published.territories().values()) {
            if (zone.type() == TerritoryType.DOOM_GATE || zone.type() == TerritoryType.DUNGEON) {
                continue;
            }
            if (!worldName.equals(zone.world())) {
                continue;
            }
            final double deltaX = location.getX() - zone.x();
            final double deltaZ = location.getZ() - zone.z();
            final double edge = Math.max(0.0D, Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ)) - zone.radius());
            if (best < 0.0D || edge < best) {
                best = edge;
            }
        }
        return best;
    }

    // ==================== spatial index ====================

    /** Rebuilds the chunk→zones lookup from each zone's bounding box and swaps it atomically. */
    private void rebuildIndex() {
        published = new Published(Map.copyOf(territories), Map.copyOf(factionSpawns), buildIndex(territories), Map.copyOf(adjustmentReceipts));
        loaded = true;
        writeUncertain = false;
    }

    private static Map<String, List<Territory>> buildIndex(final Map<String, Territory> territories) {
        final Map<String, List<Territory>> fresh = new HashMap<>();
        for (final Territory territory : territories.values()) {
            final int minX;
            final int maxX;
            final int minZ;
            final int maxZ;
            if (territory.isPolygon()) {
                int loX = Integer.MAX_VALUE;
                int hiX = Integer.MIN_VALUE;
                int loZ = Integer.MAX_VALUE;
                int hiZ = Integer.MIN_VALUE;
                for (final int[] point : territory.polygon()) {
                    loX = Math.min(loX, point[0]);
                    hiX = Math.max(hiX, point[0]);
                    loZ = Math.min(loZ, point[1]);
                    hiZ = Math.max(hiZ, point[1]);
                }
                minX = loX;
                maxX = hiX;
                minZ = loZ;
                maxZ = hiZ;
            } else {
                minX = territory.x() - territory.radius();
                maxX = territory.x() + territory.radius();
                minZ = territory.z() - territory.radius();
                maxZ = territory.z() + territory.radius();
            }
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                    fresh.computeIfAbsent(chunkKey(territory.world(), cx, cz), k -> new ArrayList<>()).add(territory);
                }
            }
        }
        fresh.replaceAll((k, list) -> List.copyOf(list));
        return Map.copyOf(fresh);
    }

    private static String chunkKey(final String world, final int chunkX, final int chunkZ) {
        return world + ";" + chunkX + ";" + chunkZ;
    }

    // ==================== polygon boundary point buffer (per player) ====================

    /**
     * Records the player's standing block as the next boundary point. A buffer is
     * world-scoped: moving to another world resets it. Returns the new point count.
     */
    public int addPoint(final Player player) {
        return addPoint(player, player.getLocation());
    }

    /** Pálcás kijelölés: a KATTINTOTT blokk koordinátájával (nem a játékos helyével). */
    public int addPoint(final Player player, final Location location) {
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
