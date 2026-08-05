package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.BlockCuboid;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.ClaimFootprint;
import hu.taliann.icesmp.data.ClaimShape;
import hu.taliann.icesmp.integration.ProtectionBridge;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Native, BLOCK-precise 3D player-claim system (replaces the external
 * SimpleClaimSystem plugin, integrated with the IceSMP economy and war rules).
 * A claim combines an exact X-Z shape (quick/two-corner rectangle or territory-style
 * polygon) with a bounded inclusive Y range. New claims reserve the configured
 * height/depth around their anchor Y and can be extended up or down for money.
 *
 * <p>Pricing is per COLUMN (1×1 block footprint): the first
 * {@code claims.free-columns} are free, every further column costs
 * {@code claims.column-cost} — always BURNED (money sink, never credited).
 *
 * <p>War rule: claims are private property and stay protected during raids by
 * default; the {@code claims.raid-lootable} switch lets registered attackers
 * open (not break) containers in enemy-faction claims as war plunder.
 *
 * <p>Threading (Folia): lookups are lock-free — a volatile chunk-index maps
 * {@code world;chunkX;chunkZ} to the (few) claims overlapping that chunk, and
 * every mutation rebuilds and atomically swaps the index under {@code synchronized}
 * (mutations are rare, command-driven). Region threads only ever read fully-built,
 * never-mutated snapshots.
 */
public final class ClaimManager implements PersistentStore, hu.taliann.icesmp.session.PlayerStateCleanup {

    private static final int FALLBACK_WORLD_MIN_Y = -64;
    private static final int FALLBACK_WORLD_MAX_Y = 319;

    /** One exact X-Z shape with an inclusive Y range, its owner and trusted players. */
    public static final class Claim {
        private final String id;
        private final String world;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final ClaimFootprint footprint;
        private final ClaimShape shape;
        private final UUID owner;
        private final String ownerName;
        private final Set<UUID> trusted = ConcurrentHashMap.newKeySet();
        private final long claimedAt;

        private Claim(final String id, final String world,
                      final int minX, final int minY, final int minZ,
                      final int maxX, final int maxY, final int maxZ,
                      final UUID owner, final String ownerName, final long claimedAt) {
            this(id, world, minX, minY, minZ, maxX, maxY, maxZ,
                    null, owner, ownerName, claimedAt);
        }

        private Claim(final String id, final String world,
                      final int minX, final int minY, final int minZ,
                      final int maxX, final int maxY, final int maxZ,
                      final List<ClaimShape.Point> polygon,
                      final UUID owner, final String ownerName, final long claimedAt) {
            this.id = id;
            this.world = world;
            this.minY = minY;
            this.maxY = maxY;
            this.shape = polygon == null || polygon.isEmpty()
                    ? ClaimShape.rectangle(ClaimFootprint.between(minX, minZ, maxX, maxZ))
                    : ClaimShape.polygon(polygon);
            this.footprint = shape.bounds();
            this.minX = footprint.minX();
            this.minZ = footprint.minZ();
            this.maxX = footprint.maxX();
            this.maxZ = footprint.maxZ();
            this.owner = owner;
            this.ownerName = ownerName;
            this.claimedAt = claimedAt;
        }

        public UUID getOwner() { return owner; }
        public String getOwnerName() { return ownerName; }
        public boolean isTrusted(final UUID playerId) {
            return owner.equals(playerId) || trusted.contains(playerId);
        }
        public int columns() { return shape.columns(); }
        public boolean isPolygon() { return shape.isPolygon(); }
        public int minY() { return minY; }
        public int maxY() { return maxY; }

        private boolean contains(final String worldName, final int x, final int y, final int z) {
            return world.equals(worldName)
                    && y >= minY && y <= maxY
                    && shape.contains(x, z);
        }

        private boolean overlapsShape(final String worldName, final ClaimShape other) {
            return world.equals(worldName) && shape.overlaps(other);
        }

        private boolean overlapsFootprint(final String worldName, final int oMinX, final int oMinZ,
                                          final int oMaxX, final int oMaxZ) {
            return world.equals(worldName) && shape.overlaps(
                    ClaimFootprint.between(oMinX, oMinZ, oMaxX, oMaxZ));
        }
    }

    /** Per-player block-corner selection for /claim pos1|pos2 (volatile, cleared on quit). */
    private static final class Selection {
        private String world;
        private int x1;
        private int y1;
        private int z1;
        private boolean hasFirst;
        private int x2;
        private int y2;
        private int z2;
        private boolean hasSecond;
    }

    /** Territory-style multi-point personal-claim selection. */
    private static final class PolygonSelection {
        private String world;
        private int anchorY;
        private boolean hasAnchor;
        private final List<ClaimShape.Point> points = new ArrayList<>();
    }

    /** A completed selection's summary for previews and the area-claim flow. */
    public record SelectionInfo(long width, long depth, long columns, boolean overlaps, double cost) { }

    /** A valid multi-point selection's exact filled-column summary. */
    public record PolygonSelectionInfo(int points, int columns, boolean overlaps, double cost) { }

    /** Existing personal claim that conflicts with an admin selection footprint. */
    public record ClaimConflict(UUID owner, String ownerName) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final TerritoryManager territoryManager;
    private final File storageFile;

    /** claim-id → claim. */
    private final Map<String, Claim> claims = new ConcurrentHashMap<>();
    private final Object saveLock = new Object();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    /**
     * world;chunkX;chunkZ → claims overlapping that chunk. Rebuilt and swapped whole
     * on every (rare) mutation, so the hot-path lookup is a lock-free read of an
     * immutable snapshot.
     */
    private volatile Map<String, List<Claim>> chunkIndex = Map.of();

    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, PolygonSelection> polygonSelections = new ConcurrentHashMap<>();
    /** Last claim-id per player (border-cross action-bar notices; cleared on quit). */
    private final Map<UUID, String> lastClaimId = new ConcurrentHashMap<>();
    /** At most one owned border preview task per player. */
    private final Map<UUID, ScheduledTask> borderTasks = new ConcurrentHashMap<>();

    public ClaimManager(final JavaPlugin plugin, final ConfigManager configManager,
                        final CurrencyManager currencyManager, final FactionManager factionManager,
                        final TerritoryManager territoryManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.territoryManager = territoryManager;
        this.storageFile = new File(plugin.getDataFolder(), "claims.yml");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("claims.enabled", true);
    }

    // ==================== queries (lock-free, hot path) ====================

    /** The claim covering the exact X/Y/Z block location, or null. */
    public Claim getClaimAt(final Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        final String worldName = location.getWorld().getName();
        final List<Claim> candidates = chunkIndex.get(
                chunkKey(worldName, location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (candidates == null) {
            return null;
        }
        for (final Claim claim : candidates) {
            if (claim.contains(worldName, location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                return claim;
            }
        }
        return null;
    }

    /**
     * Whether the player may build/interact at the location: true when unclaimed
     * (including above/below a claim's Y range), or when they own / are trusted in
     * the covering claim. The admin bypass permission is the listener's concern.
     */
    public boolean canUse(final UUID playerId, final Location location) {
        if (!isEnabled()) {
            return true;
        }
        final Claim claim = getClaimAt(location);
        return claim == null || claim.isTrusted(playerId);
    }

    /** How many claim regions the player owns. */
    public int countClaims(final UUID playerId) {
        int count = 0;
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(playerId)) {
                count++;
            }
        }
        return count;
    }

    /** The player's total claimed footprint in columns (pricing basis). */
    public int countColumns(final UUID playerId) {
        int columns = 0;
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(playerId)) {
                columns += claim.columns();
            }
        }
        return columns;
    }

    public int freeColumns() {
        return Math.max(0, configManager.getInt("claims.free-columns", 768));
    }

    public double columnCost() {
        return Math.max(0.0D, configManager.getDouble("claims.column-cost", 0.5D));
    }

    /**
     * The price of {@code newColumns} more columns for a player already owning
     * {@code ownedColumns}: only the part beyond the free allowance is paid,
     * linearly per column — always burned.
     */
    public double priceFor(final int ownedColumns, final int newColumns) {
        final int free = freeColumns();
        final int paidBefore = Math.max(0, ownedColumns - free);
        final int paidAfter = Math.max(0, ownedColumns + newColumns - free);
        return Math.ceil((paidAfter - paidBefore) * columnCost() * 100.0D) / 100.0D;
    }

    /** The price of the player's next quick-claim (a quick-size² square). */
    public double nextClaimCost(final UUID playerId) {
        final int size = quickSize();
        return priceFor(countColumns(playerId), size * size);
    }

    private int quickSize() {
        return Math.max(4, configManager.getInt("claims.quick-size", 16));
    }

    private int areaMaxColumns() {
        return Math.max(16, configManager.getInt("claims.area-max-columns", 6400));
    }

    private int polygonMaxPoints() {
        return Math.max(3, configManager.getInt("claims.polygon-max-points", 64));
    }

    private int defaultHeight() {
        return Math.max(1, configManager.getInt("claims.default-height", 20));
    }

    private int defaultDepth() {
        return Math.max(1, configManager.getInt("claims.default-depth", 20));
    }

    /** The player's claims as human-readable region descriptors (for /claim list). */
    public List<String> describeClaims(final UUID playerId) {
        final List<String> entries = new ArrayList<>();
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(playerId)) {
                entries.add(claim.world + " (" + claim.minX + ", " + claim.minZ + ")→(" + claim.maxX + ", "
                        + claim.maxZ + ") Y " + claim.minY + ".." + claim.maxY + " — "
                        + (claim.shape.isPolygon()
                        ? "poligon, " + claim.columns() + " oszlop"
                        : (claim.maxX - claim.minX + 1) + "×" + (claim.maxZ - claim.minZ + 1)));
            }
        }
        return entries;
    }

    // ==================== command-facing API (null = success, else message key) ====================

    /** Quick-claim: a quick-size² X-Z square centred on the player, with the default Y range. */
    public synchronized String claimHere(final Player player) {
        if (!isEnabled()) {
            return "claim-disabled";
        }
        final Location location = player.getLocation();
        final int half = quickSize() / 2;
        final int minX = location.getBlockX() - half;
        final int minZ = location.getBlockZ() - half;
        return createClaim(player, location.getWorld(),
                minX, minZ, minX + quickSize() - 1, minZ + quickSize() - 1,
                location.getBlockY());
    }

    private String createClaim(final Player player, final World world,
                               final int minX, final int minZ,
                               final int maxX, final int maxZ,
                               final int anchorY) {
        try {
            return createClaimShape(player, world, ClaimShape.rectangle(
                    ClaimFootprint.between(minX, minZ, maxX, maxZ), areaMaxColumns()), anchorY);
        } catch (final IllegalArgumentException tooLarge) {
            return "claim-area-too-big";
        }
    }

    private String createClaimShape(final Player player, final World world,
                                    final ClaimShape shape, final int anchorY) {
        final String worldName = world.getName();
        final Claim overlapping = findShapeOverlap(worldName, shape);
        if (overlapping != null) {
            return overlapping.owner.equals(player.getUniqueId())
                    ? "claim-overlap-own" : "claim-already-taken";
        }
        final String vetoKey = territoryOrRegionVeto(world, shape);
        if (vetoKey != null) return vetoKey;

        final int columns = shape.columns();
        final int owned = countColumns(player.getUniqueId());
        final int cap = Math.max(1, configManager.getInt("claims.max-columns-per-player", 8192));
        if ((long) owned + columns > cap) return "claim-limit-reached";

        final double cost = priceFor(owned, columns);
        if (cost > 0.0D) {
            final CurrencyType currency = CurrencyType.fromFactionType(
                    factionManager.getEconomyFaction(player.getUniqueId()));
            if (!currencyManager.deductFromBalance(player.getUniqueId(), currency, cost)) {
                return "claim-insufficient";
            }
        }

        final ClaimFootprint bounds = shape.bounds();
        final int minY = Math.max(world.getMinHeight(), anchorY - defaultDepth());
        final int maxY = Math.min(world.getMaxHeight() - 1, anchorY + defaultHeight());
        final Claim claim = new Claim(UUID.randomUUID().toString(), worldName,
                bounds.minX(), minY, bounds.minZ(),
                bounds.maxX(), maxY, bounds.maxZ(),
                shape.isPolygon() ? shape.vertices() : null,
                player.getUniqueId(), player.getName(), System.currentTimeMillis());
        claims.put(claim.id, claim);
        rebuildIndex();
        requestSave();
        return null;
    }

    private Claim findShapeOverlap(final String worldName, final ClaimShape shape) {
        for (final Claim claim : claims.values()) {
            if (claim.overlapsShape(worldName, shape)) return claim;
        }
        return null;
    }

    private Claim findFootprintOverlap(final String worldName, final int minX, final int minZ,
                                       final int maxX, final int maxZ) {
        for (final Claim claim : claims.values()) {
            if (claim.overlapsFootprint(worldName, minX, minZ, maxX, maxZ)) return claim;
        }
        return null;
    }

    private String territoryOrRegionVeto(final World world, final ClaimShape shape) {
        final boolean blockProtectedZone =
                configManager.getBoolean("claims.block-in-protected-zone", true);
        final boolean blockFactionTerritory =
                configManager.getBoolean("claims.block-in-territory", false);
        final boolean blockRegion =
                configManager.getBoolean("claims.block-in-protected-region", true);

        if (blockProtectedZone || blockFactionTerritory) {
            for (final ClaimShape.Point column : shape.claimedColumns()) {
                final hu.taliann.icesmp.data.Territory territory =
                        territoryManager.getTerritoryColumnAt(
                                world.getName(), column.x(), column.z());
                if (territory == null) continue;
                if (blockProtectedZone && !territory.type().isClaimable()) {
                    return "claim-in-protected-zone";
                }
                if (blockFactionTerritory && territory.type().isClaimable()) {
                    return "claim-in-territory";
                }
            }
        }
        if (blockRegion) {
            for (final ClaimShape.RowSpan span : shape.rowSpans()) {
                final Boolean overlap = ProtectionBridge.queryRegionOverlap(
                        world, span.minX(), span.z(), span.maxX(), span.z());
                if (overlap == null || overlap) return "claim-in-protected-region";
            }
        }
        return null;
    }

    public synchronized String unclaimHere(final Player player) {
        final Claim claim = getClaimAt(player.getLocation());
        if (claim == null) return "claim-none-here";
        if (!claim.owner.equals(player.getUniqueId())) return "claim-not-owner";
        claims.remove(claim.id);
        rebuildIndex();
        requestSave();
        return null;
    }

    public int[] setCorner(final Player player, final boolean first) {
        return setCorner(player, first, player.getLocation());
    }

    public int[] setCorner(final Player player, final boolean first, final Location location) {
        final Selection selection = selections.computeIfAbsent(player.getUniqueId(), id -> new Selection());
        synchronized (selection) {
            final String worldName = location.getWorld().getName();
            if (!worldName.equals(selection.world)) {
                selection.hasFirst = false;
                selection.hasSecond = false;
                selection.world = worldName;
            }
            if (first) {
                selection.x1 = location.getBlockX();
                selection.y1 = location.getBlockY();
                selection.z1 = location.getBlockZ();
                selection.hasFirst = true;
            } else {
                selection.x2 = location.getBlockX();
                selection.y2 = location.getBlockY();
                selection.z2 = location.getBlockZ();
                selection.hasSecond = true;
            }
        }
        return new int[] {location.getBlockX(), location.getBlockY(), location.getBlockZ()};
    }

    public SelectionInfo getSelectionInfo(final UUID playerId) {
        final Selection selection = selections.get(playerId);
        if (selection == null) return null;
        synchronized (selection) {
            if (!selection.hasFirst || !selection.hasSecond) return null;
            final int minX = Math.min(selection.x1, selection.x2);
            final int maxX = Math.max(selection.x1, selection.x2);
            final int minZ = Math.min(selection.z1, selection.z2);
            final int maxZ = Math.max(selection.z1, selection.z2);
            final long width = (long) maxX - minX + 1L;
            final long depth = (long) maxZ - minZ + 1L;
            final long columns = Math.multiplyExact(width, depth);
            final boolean overlaps = findFootprintOverlap(selection.world, minX, minZ, maxX, maxZ) != null;
            final long owned = countColumns(playerId);
            final long free = freeColumns();
            final long paidBefore = Math.max(0L, owned - free);
            final long paidAfter = Math.max(0L, Math.addExact(owned, columns) - free);
            final double cost = Math.ceil((paidAfter - paidBefore) * columnCost() * 100.0D) / 100.0D;
            return new SelectionInfo(width, depth, columns, overlaps, cost);
        }
    }

    public BlockCuboid snapshotSelection(final UUID playerId) {
        final Selection selection = selections.get(playerId);
        if (selection == null) return null;
        synchronized (selection) {
            if (!selection.hasFirst || !selection.hasSecond) return null;
            return BlockCuboid.between(selection.world,
                    selection.x1, selection.y1, selection.z1,
                    selection.x2, selection.y2, selection.z2);
        }
    }

    public void clearSelection(final UUID playerId) {
        selections.remove(playerId);
    }

    public ClaimConflict findFootprintConflict(final BlockCuboid bounds) {
        if (bounds == null) return null;
        final Claim claim = findFootprintOverlap(bounds.world(),
                bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
        return claim == null ? null : new ClaimConflict(claim.owner, claim.ownerName);
    }

    public synchronized String claimSelection(final Player player) {
        if (!isEnabled()) return "claim-disabled";
        final Selection selection = selections.get(player.getUniqueId());
        if (selection == null || !selection.hasFirst || !selection.hasSecond) {
            return "claim-area-incomplete";
        }
        if (!player.getWorld().getName().equals(selection.world)) return "claim-area-cross-world";

        final int minX = Math.min(selection.x1, selection.x2);
        final int maxX = Math.max(selection.x1, selection.x2);
        final int minZ = Math.min(selection.z1, selection.z2);
        final int maxZ = Math.max(selection.z1, selection.z2);
        final long columns = (long) (maxX - minX + 1) * (long) (maxZ - minZ + 1);
        final int areaMax = Math.max(16, configManager.getInt("claims.area-max-columns", 6400));
        if (columns > areaMax) return "claim-area-too-big";

        final int anchorY = (Math.min(selection.y1, selection.y2)
                + Math.max(selection.y1, selection.y2)) / 2;
        final String errorKey = createClaim(
                player, player.getWorld(), minX, minZ, maxX, maxZ, anchorY);
        if (errorKey != null) {
            return switch (errorKey) {
                case "claim-already-taken" -> "claim-area-foreign";
                case "claim-overlap-own" -> "claim-area-overlap-own";
                default -> errorKey;
            };
        }
        selections.remove(player.getUniqueId());
        return null;
    }

    public int addPolygonPoint(final Player player) {
        return addPolygonPoint(player, player.getLocation());
    }

    public int addPolygonPoint(final Player player, final Location location) {
        final PolygonSelection selection = polygonSelections.computeIfAbsent(
                player.getUniqueId(), ignored -> new PolygonSelection());
        synchronized (selection) {
            final String worldName = location.getWorld().getName();
            if (!worldName.equals(selection.world)) {
                selection.points.clear();
                selection.hasAnchor = false;
                selection.world = worldName;
            }
            final ClaimShape.Point point = new ClaimShape.Point(
                    location.getBlockX(), location.getBlockZ());
            if (selection.points.isEmpty()
                    || !selection.points.get(selection.points.size() - 1).equals(point)) {
                if (selection.points.size() >= polygonMaxPoints()) {
                    return -selection.points.size();
                }
                if (!selection.hasAnchor) {
                    selection.anchorY = location.getBlockY();
                    selection.hasAnchor = true;
                }
                selection.points.add(point);
            }
            return selection.points.size();
        }
    }

    public int undoPolygonPoint(final UUID playerId) {
        final PolygonSelection selection = polygonSelections.get(playerId);
        if (selection == null) return -1;
        synchronized (selection) {
            if (selection.points.isEmpty()) return -1;
            selection.points.remove(selection.points.size() - 1);
            if (selection.points.isEmpty()) selection.hasAnchor = false;
            return selection.points.size();
        }
    }

    public void clearPolygonPoints(final UUID playerId) {
        polygonSelections.remove(playerId);
    }

    public List<ClaimShape.Point> getPolygonPoints(final UUID playerId) {
        final PolygonSelection selection = polygonSelections.get(playerId);
        if (selection == null) return List.of();
        synchronized (selection) {
            return List.copyOf(selection.points);
        }
    }

    public PolygonSelectionInfo getPolygonSelectionInfo(final UUID playerId) {
        final PolygonSelection selection = polygonSelections.get(playerId);
        if (selection == null) return null;
        final List<ClaimShape.Point> points;
        final String worldName;
        synchronized (selection) {
            if (selection.points.size() < 3 || selection.points.size() > polygonMaxPoints()) return null;
            points = List.copyOf(selection.points);
            worldName = selection.world;
        }
        try {
            final ClaimShape shape = ClaimShape.polygon(points, areaMaxColumns());
            return new PolygonSelectionInfo(points.size(), shape.columns(),
                    findShapeOverlap(worldName, shape) != null,
                    priceFor(countColumns(playerId), shape.columns()));
        } catch (final IllegalArgumentException invalid) {
            return null;
        }
    }

    public synchronized String claimPolygon(final Player player) {
        if (!isEnabled()) return "claim-disabled";
        final PolygonSelection selection = polygonSelections.get(player.getUniqueId());
        if (selection == null) return "claim-polygon-too-few";
        final List<ClaimShape.Point> points;
        final String worldName;
        final int anchorY;
        synchronized (selection) {
            points = List.copyOf(selection.points);
            worldName = selection.world;
            anchorY = selection.anchorY;
        }
        if (points.size() < 3) return "claim-polygon-too-few";
        if (points.size() > polygonMaxPoints()) return "claim-polygon-too-many";
        if (!player.getWorld().getName().equals(worldName)) return "claim-polygon-cross-world";

        final ClaimShape shape;
        try {
            shape = ClaimShape.polygon(points, areaMaxColumns());
        } catch (final IllegalArgumentException invalid) {
            return invalid.getMessage() != null
                    && invalid.getMessage().contains("self-intersects")
                    ? "claim-polygon-self-intersect" : "claim-polygon-invalid";
        }
        final String error = createClaimShape(player, player.getWorld(), shape, anchorY);
        if (error != null) {
            return switch (error) {
                case "claim-already-taken" -> "claim-polygon-foreign";
                case "claim-overlap-own" -> "claim-polygon-overlap-own";
                default -> error;
            };
        }
        polygonSelections.remove(player.getUniqueId());
        return null;
    }

    public synchronized String extendClaim(final Player player, final boolean up) {
        if (!isEnabled()) return "claim-disabled";
        final Claim claim = getClaimAt(player.getLocation());
        if (claim == null) return "claim-none-here";
        if (!claim.owner.equals(player.getUniqueId())) return "claim-not-owner";

        final World world = player.getWorld();
        final int step = Math.max(1, configManager.getInt("claims.y-extend-step", 5));
        final int newMinY = up ? claim.minY : Math.max(world.getMinHeight(), claim.minY - step);
        final int newMaxY = up ? Math.min(world.getMaxHeight() - 1, claim.maxY + step) : claim.maxY;
        if (newMinY == claim.minY && newMaxY == claim.maxY) return "claim-extend-at-limit";

        final double cost = extendCost(claim);
        if (cost > 0.0D) {
            final CurrencyType currency = CurrencyType.fromFactionType(
                    factionManager.getEconomyFaction(player.getUniqueId()));
            if (!currencyManager.deductFromBalance(player.getUniqueId(), currency, cost)) {
                return "claim-insufficient";
            }
        }

        final Claim extended = new Claim(claim.id, claim.world,
                claim.minX, newMinY, claim.minZ,
                claim.maxX, newMaxY, claim.maxZ,
                claim.shape.isPolygon() ? claim.shape.vertices() : null,
                claim.owner, claim.ownerName, claim.claimedAt);
        extended.trusted.addAll(claim.trusted);
        claims.put(claim.id, extended);
        rebuildIndex();
        requestSave();
        return null;
    }

    public double extendCostAt(final Player player) {
        final Claim claim = getClaimAt(player.getLocation());
        if (claim == null || !claim.owner.equals(player.getUniqueId())) return -1.0D;
        return extendCost(claim);
    }

    private double extendCost(final Claim claim) {
        return Math.ceil(claim.columns()
                * Math.max(0.0D, configManager.getDouble(
                "claims.y-extend-cost-per-column", 0.1D)) * 100.0D) / 100.0D;
    }

    public synchronized boolean adminUnclaimAt(final Location location) {
        final Claim claim = getClaimAt(location);
        if (claim == null) return false;
        claims.remove(claim.id);
        rebuildIndex();
        requestSave();
        return true;
    }

    public synchronized String trust(final Player owner, final String targetName) {
        final Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) return "target-player-offline";
        if (target.getUniqueId().equals(owner.getUniqueId())) return "claim-trust-self";
        boolean any = false;
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(owner.getUniqueId())) {
                claim.trusted.add(target.getUniqueId());
                any = true;
            }
        }
        if (!any) return "claim-no-claims";
        requestSave();
        return null;
    }

    public synchronized String untrust(final Player owner, final String targetName) {
        final UUID targetId = resolveIdByName(targetName);
        if (targetId == null) return "target-player-offline";
        boolean any = false;
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(owner.getUniqueId()) && claim.trusted.remove(targetId)) any = true;
        }
        if (!any) return "claim-not-trusted";
        requestSave();
        return null;
    }

    public List<String> trustedNamesAt(final Location location) {
        final Claim claim = getClaimAt(location);
        if (claim == null) return List.of();
        final List<String> names = new ArrayList<>();
        for (final UUID trustedId : claim.trusted) {
            final Player online = Bukkit.getPlayer(trustedId);
            names.add(online != null ? online.getName() : Bukkit.getOfflinePlayer(trustedId).getName());
        }
        names.removeIf(java.util.Objects::isNull);
        return names;
    }

    public synchronized Set<UUID> trustedPlayers(final UUID owner) {
        final Set<UUID> result = new java.util.LinkedHashSet<>();
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(owner)) result.addAll(claim.trusted);
        }
        return result;
    }

    public void showBorder(final Player player) {
        final int seconds = Math.max(2, configManager.getInt("claims.border.show-seconds", 8));
        if (configManager.getBoolean("display-fx.claim-wall.enabled", true)) {
            showDisplayWalls(player, seconds);
        }
        final UUID playerId = player.getUniqueId();
        final ScheduledTask previous = borderTasks.remove(playerId);
        if (previous != null) previous.cancel();
        final int[] frames = {0};
        final ScheduledTask scheduled = player.getScheduler().runAtFixedRate(plugin, task -> {
            if (frames[0]++ >= seconds || !player.isOnline()) {
                task.cancel();
                borderTasks.remove(playerId, task);
                return;
            }
            drawBorderFrame(player);
        }, null, 1L, 20L);
        borderTasks.put(playerId, scheduled);
    }

    private void showDisplayWalls(final Player player, final int seconds) {
        final Location location = player.getLocation();
        final World world = location.getWorld();
        if (world == null) return;
        final String worldName = world.getName();
        final int radius = Math.max(1, configManager.getInt("claims.border.radius", 2));
        final int pcx = location.getBlockX() >> 4;
        final int pcz = location.getBlockZ() >> 4;
        final java.util.LinkedHashSet<Claim> nearby = new java.util.LinkedHashSet<>();
        final Map<String, List<Claim>> index = chunkIndex;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                final List<Claim> hits = index.get(chunkKey(worldName, cx, cz));
                if (hits != null) nearby.addAll(hits);
            }
        }
        final int ticks = seconds * 20;
        final org.bukkit.block.data.BlockData block = wallBlockData();
        for (final Claim claim : nearby) {
            final org.bukkit.Color glow = claim.isTrusted(player.getUniqueId())
                    ? org.bukkit.Color.fromRGB(0x3BE24A)
                    : org.bukkit.Color.fromRGB(0xE23B3B);
            if (!claim.shape.isPolygon()) {
                for (int x = claim.minX; x <= claim.maxX; x++) {
                    hu.taliann.icesmp.utils.DisplayFxUtil.claimedWallColumn(plugin, world,
                            x, claim.minZ, x, claim.minZ, 1.0F, 0.08F,
                            claim.minY, claim.maxY, block, glow, ticks, player);
                    hu.taliann.icesmp.utils.DisplayFxUtil.claimedWallColumn(plugin, world,
                            x, claim.maxZ, x, claim.maxZ + 1.0D, 1.0F, 0.08F,
                            claim.minY, claim.maxY, block, glow, ticks, player);
                }
                for (int z = claim.minZ; z <= claim.maxZ; z++) {
                    hu.taliann.icesmp.utils.DisplayFxUtil.claimedWallColumn(plugin, world,
                            claim.minX, z, claim.minX, z, 0.08F, 1.0F,
                            claim.minY, claim.maxY, block, glow, ticks, player);
                    hu.taliann.icesmp.utils.DisplayFxUtil.claimedWallColumn(plugin, world,
                            claim.maxX, z, claim.maxX + 1.0D, z, 0.08F, 1.0F,
                            claim.minY, claim.maxY, block, glow, ticks, player);
                }
            } else {
                for (final ClaimShape.Point point : claim.shape.boundaryColumns()) {
                    hu.taliann.icesmp.utils.DisplayFxUtil.claimedWallColumn(plugin, world,
                            point.x(), point.z(), point.x(), point.z(),
                            1.0F, 1.0F, claim.minY, claim.maxY,
                            block, glow, ticks, player);
                }
            }
        }
    }

    private org.bukkit.block.data.BlockData wallBlockData() {
        final String name = configManager.getString("display-fx.claim-wall.material", "LIGHT_BLUE_STAINED_GLASS");
        final org.bukkit.Material material = org.bukkit.Material.matchMaterial(name);
        return (material != null && material.isBlock() ? material : org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS)
                .createBlockData();
    }

    private void drawBorderFrame(final Player player) {
        final Location location = player.getLocation();
        final World world = location.getWorld();
        if (world == null) return;
        final String worldName = world.getName();
        final int radius = Math.max(1, configManager.getInt("claims.border.radius", 2));
        final int pcx = location.getBlockX() >> 4;
        final int pcz = location.getBlockZ() >> 4;
        final java.util.LinkedHashSet<Claim> nearby = new java.util.LinkedHashSet<>();
        final Map<String, List<Claim>> index = chunkIndex;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                final List<Claim> hits = index.get(chunkKey(worldName, cx, cz));
                if (hits != null) nearby.addAll(hits);
            }
        }
        for (final Claim claim : nearby) {
            final Particle particle = claim.isTrusted(player.getUniqueId())
                    ? Particle.HAPPY_VILLAGER : Particle.FLAME;
            drawShapeOutline(player, world, claim.shape,
                    claim.minY, claim.maxY, location.getBlockY(), particle);
        }
        if (getClaimAt(location) == null) {
            final int half = quickSize() / 2;
            final int minX = location.getBlockX() - half;
            final int minZ = location.getBlockZ() - half;
            drawFootprintOutline(player, world,
                    ClaimFootprint.between(minX, minZ, minX + quickSize() - 1, minZ + quickSize() - 1),
                    location.getBlockY(), Particle.COMPOSTER);
        }
    }

    private void drawShapeOutline(final Player player, final World world, final ClaimShape shape,
                                  final int minY, final int maxY,
                                  final int viewerY, final Particle particle) {
        final List<ClaimShape.Point> boundary = shape.boundaryColumns();
        for (int index = 0; index < boundary.size(); index += 2) {
            final ClaimShape.Point point = boundary.get(index);
            drawEdgePoint(player, world, point.x(), point.z(), minY, maxY, viewerY, particle);
        }
    }

    private void drawFootprintOutline(final Player player, final World world, final ClaimFootprint footprint,
                                      final int viewerY, final Particle particle) {
        drawShapeOutline(player, world, ClaimShape.rectangle(footprint),
                viewerY, viewerY, viewerY, particle);
    }

    private void drawEdgePoint(final Player player, final World world, final int x, final int z,
                               final int minY, final int maxY,
                               final int viewerY, final Particle particle) {
        final int markerY = Math.max(minY, Math.min(maxY, viewerY));
        player.spawnParticle(particle,
                new Location(world, x, markerY + 0.5D, z), 1, 0, 0, 0, 0);
    }

    public String handleChunkEnter(final Player player, final Location to) {
        if (!isEnabled() || !configManager.getBoolean("claims.border.enter-notice", true)) return null;
        final Claim claim = getClaimAt(to);
        final String currentId = claim == null ? "" : claim.id;
        final String previousId = lastClaimId.put(player.getUniqueId(), currentId);
        if (previousId == null || currentId.equals(previousId)) return null;
        if (claim == null) return "wilderness";
        return claim.isTrusted(player.getUniqueId()) ? "own" : "foreign";
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        selections.remove(playerId);
        polygonSelections.remove(playerId);
        lastClaimId.remove(playerId);
        final ScheduledTask task = borderTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    private static int worldMinY(final World world) {
        return world == null ? FALLBACK_WORLD_MIN_Y : world.getMinHeight();
    }

    private static int inclusiveWorldMaxY(final World world) {
        return world == null ? FALLBACK_WORLD_MAX_Y : world.getMaxHeight() - 1;
    }

    private static List<ClaimShape.Point> readClaimPolygon(final List<String> raw) {
        if (raw == null || raw.isEmpty()) return null;
        final List<ClaimShape.Point> points = new ArrayList<>();
        for (final String entry : raw) {
            final String[] parts = entry.split(",");
            if (parts.length != 2) return null;
            try {
                points.add(new ClaimShape.Point(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim())));
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }
        return points.size() >= 3 ? List.copyOf(points) : null;
    }

    @Override
    public synchronized void load() {
        claims.clear();
        if (!storageFile.exists()) {
            rebuildIndex();
            return;
        }
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        final ConfigurationSection section = yaml.getConfigurationSection("claims");
        if (section == null) {
            rebuildIndex();
            return;
        }
        for (final String key : section.getKeys(false)) {
            try {
                final UUID owner = UUID.fromString(section.getString(key + ".owner", ""));
                final String ownerName = section.getString(key + ".owner-name", "?");
                final long claimedAt = section.getLong(key + ".claimed-at", System.currentTimeMillis());
                final Claim claim;
                if (key.contains(";")) {
                    final String[] parts = key.split(";", -1);
                    if (parts.length != 3 || parts[0].isBlank()) {
                        throw new IllegalArgumentException("Malformed legacy claim key");
                    }
                    final int baseX = Integer.parseInt(parts[1]) << 4;
                    final int baseZ = Integer.parseInt(parts[2]) << 4;
                    final World world = Bukkit.getWorld(parts[0]);
                    claim = new Claim(UUID.randomUUID().toString(), parts[0],
                            baseX, worldMinY(world), baseZ,
                            baseX + 15, inclusiveWorldMaxY(world), baseZ + 15,
                            owner, ownerName, claimedAt);
                } else {
                    final String polygonPath = key + ".polygon";
                    final boolean polygonStored = section.contains(polygonPath);
                    final List<ClaimShape.Point> polygon = readClaimPolygon(section.getStringList(polygonPath));
                    if (polygonStored && polygon == null) {
                        throw new IllegalArgumentException("Malformed stored claim polygon");
                    }

                    final String worldName = section.getString(key + ".world", "world");
                    final World world = Bukkit.getWorld(worldName);
                    final String minYPath = key + ".min-y";
                    final String maxYPath = key + ".max-y";
                    final boolean hasMinY = section.contains(minYPath);
                    final boolean hasMaxY = section.contains(maxYPath);
                    if (hasMinY != hasMaxY) {
                        throw new IllegalArgumentException("Claim Y bounds are incomplete");
                    }
                    final boolean hasStoredYBounds = hasMinY;
                    final int minY;
                    final int maxY;
                    if (!hasStoredYBounds) {
                        minY = worldMinY(world);
                        maxY = inclusiveWorldMaxY(world);
                        plugin.getLogger().warning(
                                "Y-határ nélküli claim teljes világmagasságra migrálva: " + key);
                    } else {
                        final int storedMinY = section.getInt(minYPath);
                        final int storedMaxY = section.getInt(maxYPath);
                        if (storedMinY > storedMaxY) {
                            throw new IllegalArgumentException("Claim Y bounds are reversed");
                        }
                        if (world == null) {
                            minY = storedMinY;
                            maxY = storedMaxY;
                        } else {
                            minY = Math.max(world.getMinHeight(), storedMinY);
                            maxY = Math.min(world.getMaxHeight() - 1, storedMaxY);
                            if (minY > maxY) {
                                throw new IllegalArgumentException("Claim Y bounds are outside world height");
                            }
                        }
                    }
                    claim = new Claim(key, worldName,
                            section.getInt(key + ".min-x"), minY,
                            section.getInt(key + ".min-z"), section.getInt(key + ".max-x"),
                            maxY, section.getInt(key + ".max-z"),
                            polygon, owner, ownerName, claimedAt);
                }
                for (final String trustedId : section.getStringList(key + ".trusted")) {
                    try {
                        claim.trusted.add(UUID.fromString(trustedId));
                    } catch (final IllegalArgumentException malformedTrusted) {
                        plugin.getLogger().warning(
                                "Hibás trusted UUID kihagyva a claimnél " + key + ": " + trustedId);
                    }
                }
                claims.put(claim.id, claim);
            } catch (final RuntimeException exception) {
                plugin.getLogger().warning("Hibás claim-bejegyzés kihagyva: " + key
                        + " (" + exception.getMessage() + ")");
            }
        }
        rebuildIndex();
    }

    @Override
    public synchronized void save() {
        flushToDisk();
    }

    private void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                flushToDisk();
            }, 2L, TimeUnit.SECONDS);
        }
    }

    private void flushToDisk() {
        synchronized (saveLock) {
            final YamlConfiguration yaml = buildYaml();
            try {
                YamlStore.saveAtomic(storageFile, yaml);
            } catch (final IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "A claims.yml mentése nem sikerült", exception);
            }
        }
    }

    private YamlConfiguration buildYaml() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Claim claim : claims.values()) {
            final String basePath = "claims." + claim.id;
            yaml.set(basePath + ".world", claim.world);
            yaml.set(basePath + ".min-x", claim.minX);
            yaml.set(basePath + ".min-y", claim.minY);
            yaml.set(basePath + ".min-z", claim.minZ);
            yaml.set(basePath + ".max-x", claim.maxX);
            yaml.set(basePath + ".max-y", claim.maxY);
            yaml.set(basePath + ".max-z", claim.maxZ);
            if (claim.shape.isPolygon()) {
                yaml.set(basePath + ".polygon", claim.shape.vertices().stream()
                        .map(point -> point.x() + "," + point.z()).toList());
            }
            yaml.set(basePath + ".owner", claim.owner.toString());
            yaml.set(basePath + ".owner-name", claim.ownerName);
            yaml.set(basePath + ".claimed-at", claim.claimedAt);
            if (!claim.trusted.isEmpty()) {
                yaml.set(basePath + ".trusted", claim.trusted.stream().map(UUID::toString).toList());
            }
        }
        return yaml;
    }

    private void rebuildIndex() {
        final Map<String, List<Claim>> fresh = new HashMap<>();
        for (final Claim claim : claims.values()) {
            final ClaimFootprint bounds = claim.shape.bounds();
            for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
                for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                    fresh.computeIfAbsent(chunkKey(claim.world, cx, cz), k -> new ArrayList<>()).add(claim);
                }
            }
        }
        fresh.replaceAll((k, list) -> List.copyOf(list));
        chunkIndex = Map.copyOf(fresh);
    }

    private UUID resolveIdByName(final String name) {
        final Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (final Claim claim : claims.values()) {
            for (final UUID trustedId : claim.trusted) {
                final String knownName = Bukkit.getOfflinePlayer(trustedId).getName();
                if (knownName != null && knownName.equalsIgnoreCase(name)) return trustedId;
            }
        }
        return null;
    }

    private static String chunkKey(final String world, final int chunkX, final int chunkZ) {
        return world + ";" + chunkX + ";" + chunkZ;
    }
}
