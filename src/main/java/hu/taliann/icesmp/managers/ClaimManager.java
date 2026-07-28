package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.integration.ProtectionBridge;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.selection.CuboidSelectionService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
 * A claim is a box: an exact block-rectangle footprint plus a bounded Y-range
 * (by default 20 blocks up and 20 down from where it was created — the vertical
 * span can be EXTENDED for money from the menu). {@code /claim} quick-claims a
 * square around the player; {@code /claim pos1 + pos2 + area} claims an exact
 * rectangle between two standing points.
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

    /** One claimed 3D box: exact block bounds, the owner and the trusted players. */
    public static final class Claim {
        private final String id;
        private final String world;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final UUID owner;
        private final String ownerName;
        private final Set<UUID> trusted = ConcurrentHashMap.newKeySet();
        private final long claimedAt;

        private Claim(final String id, final String world, final int minX, final int minY, final int minZ,
                      final int maxX, final int maxY, final int maxZ,
                      final UUID owner, final String ownerName, final long claimedAt) {
            this.id = id;
            this.world = world;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.owner = owner;
            this.ownerName = ownerName;
            this.claimedAt = claimedAt;
        }

        public UUID getOwner() {
            return owner;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public boolean isTrusted(final UUID playerId) {
            return owner.equals(playerId) || trusted.contains(playerId);
        }

        /** Footprint size in columns (1×1 block ground area). */
        public int columns() {
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }

        public int minY() {
            return minY;
        }

        public int maxY() {
            return maxY;
        }

        private boolean contains(final String worldName, final int x, final int y, final int z) {
            return world.equals(worldName)
                    && x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        /** Footprint (XZ) overlap test — Y is ignored so boxes can't stack over each other. */
        private boolean overlapsFootprint(final String worldName, final int oMinX, final int oMinZ,
                                          final int oMaxX, final int oMaxZ) {
            return world.equals(worldName)
                    && minX <= oMaxX && maxX >= oMinX
                    && minZ <= oMaxZ && maxZ >= oMinZ;
        }
    }

    /** A completed selection's summary for previews and the area-claim flow. */
    public record SelectionInfo(int width, int depth, int columns, boolean overlaps, double cost) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final TerritoryManager territoryManager;
    private final CuboidSelectionService selectionService;
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

    /** Last claim-id per player (border-cross action-bar notices; cleared on quit). */
    private final Map<UUID, String> lastClaimId = new ConcurrentHashMap<>();

    public ClaimManager(final JavaPlugin plugin, final ConfigManager configManager,
                        final CurrencyManager currencyManager, final FactionManager factionManager,
                        final TerritoryManager territoryManager,
                        final CuboidSelectionService selectionService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.territoryManager = territoryManager;
        this.selectionService = selectionService;
        this.storageFile = new File(plugin.getDataFolder(), "claims.yml");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("claims.enabled", true);
    }

    // ==================== queries (lock-free, hot path) ====================

    /** The claim covering the exact block location (Y included), or null. */
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
     * (or outside the claim's Y-range), or when they own / are trusted in the
     * covering claim. (The admin bypass permission is the listener's concern.)
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
        final double configured = configManager.getDouble("claims.column-cost", 0.5D);
        return Double.isFinite(configured) && configured >= 0.0D ? configured : 0.5D;
    }

    /**
     * The price of {@code newColumns} more columns for a player already owning
     * {@code ownedColumns}: only the part beyond the free allowance is paid,
     * linearly per column — always burned.
     */
    public double priceFor(final int ownedColumns, final int newColumns) {
        final int free = freeColumns();
        final long paidBefore = Math.max(0L, (long) ownedColumns - free);
        final long paidAfter = Math.max(0L, (long) ownedColumns + newColumns - free);
        final double raw = (paidAfter - paidBefore) * columnCost();
        return Double.isFinite(raw) ? Math.ceil(raw * 100.0D) / 100.0D : Double.MAX_VALUE;
    }

    /** The price of the player's next quick-claim (a quick-size² square). */
    public double nextClaimCost(final UUID playerId) {
        final int size = quickSize();
        return priceFor(countColumns(playerId), size * size);
    }

    private int quickSize() {
        return Math.max(4, configManager.getInt("claims.quick-size", 16));
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
                        + claim.maxZ + ") Y " + claim.minY + ".." + claim.maxY
                        + " — " + (claim.maxX - claim.minX + 1) + "×" + (claim.maxZ - claim.minZ + 1));
            }
        }
        return entries;
    }

    // ==================== command-facing API (null = success, else message key) ====================

    /** Quick-claim: a quick-size² square centred on the player, default Y-range. */
    public synchronized String claimHere(final Player player) {
        if (!isEnabled()) {
            return "claim-disabled";
        }
        final Location location = player.getLocation();
        final int half = quickSize() / 2;
        final int minX = location.getBlockX() - half;
        final int minZ = location.getBlockZ() - half;
        return createClaim(player, location.getWorld(),
                minX, minZ, minX + quickSize() - 1, minZ + quickSize() - 1, location.getBlockY());
    }

    /**
     * Shared creation path for quick-claim and area-claim: overlap, territory,
     * WorldGuard, column-cap and price checks, then insert + persist.
     */
    private String createClaim(final Player player, final World world, final int minX, final int minZ,
                               final int maxX, final int maxZ, final int anchorY) {
        final String worldName = world.getName();
        final Claim overlapping = findFootprintOverlap(worldName, minX, minZ, maxX, maxZ);
        if (overlapping != null) {
            return overlapping.owner.equals(player.getUniqueId()) ? "claim-overlap-own" : "claim-already-taken";
        }

        // Kingdom land and admin regions veto per overlapped chunk (both are chunk/region
        // scoped systems — probing each chunk centre is exact enough and cheap).
        final String vetoKey = territoryOrRegionVeto(world, minX, minZ, maxX, maxZ);
        if (vetoKey != null) {
            return vetoKey;
        }

        final int columns = (maxX - minX + 1) * (maxZ - minZ + 1);
        final int owned = countColumns(player.getUniqueId());
        final int cap = Math.max(1, configManager.getInt("claims.max-columns-per-player", 8192));
        if (owned + columns > cap) {
            return "claim-limit-reached";
        }

        final double cost = priceFor(owned, columns);
        if (cost > 0.0D) {
            final CurrencyType currency = CurrencyType.fromFactionType(factionManager.getFaction(player.getUniqueId()));
            // Az ár elég (sosem íródik jóvá) — tiszta money sink.
            if (!currencyManager.deductFromBalance(player.getUniqueId(), currency, cost)) {
                return "claim-insufficient";
            }
        }

        final int minY = Math.max(world.getMinHeight(), anchorY - defaultDepth());
        final int maxY = Math.min(world.getMaxHeight(), anchorY + defaultHeight());
        final Claim claim = new Claim(UUID.randomUUID().toString(), worldName,
                minX, minY, minZ, maxX, maxY, maxZ,
                player.getUniqueId(), player.getName(), System.currentTimeMillis());
        claims.put(claim.id, claim);
        rebuildIndex();
        requestSave();
        return null;
    }

    private Claim findFootprintOverlap(final String worldName, final int minX, final int minZ,
                                       final int maxX, final int maxZ) {
        for (final Claim claim : claims.values()) {
            if (claim.overlapsFootprint(worldName, minX, minZ, maxX, maxZ)) {
                return claim;
            }
        }
        return null;
    }

    /**
     * Territory/WorldGuard veto over the footprint (null = OK). Claiming inside a
     * PROTECTED zone (capital, protected city/faction) is always forbidden — the
     * whole point of the map's shield; NORMAL faction land stays claimable unless
     * the legacy {@code claims.block-in-territory} switch is turned back on.
     *
     * <p>Probes both every touched chunk centre AND the footprint's four corners +
     * centre, so a small protected polygon can't slip between chunk-centre samples.
     */
    private String territoryOrRegionVeto(final World world, final int minX, final int minZ,
                                         final int maxX, final int maxZ) {
        final boolean blockProtectedZone = configManager.getBoolean("claims.block-in-protected-zone", true);
        final boolean blockFactionTerritory = configManager.getBoolean("claims.block-in-territory", false);
        final boolean blockRegion = configManager.getBoolean("claims.block-in-protected-region", true);
        if (!blockProtectedZone && !blockFactionTerritory && !blockRegion) {
            return null;
        }
        final int y = world.getSeaLevel();
        final List<Location> probes = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                probes.add(new Location(world, (cx << 4) + 8, y, (cz << 4) + 8));
            }
        }
        final int midX = (minX + maxX) / 2;
        final int midZ = (minZ + maxZ) / 2;
        probes.add(new Location(world, minX, y, minZ));
        probes.add(new Location(world, maxX, y, minZ));
        probes.add(new Location(world, minX, y, maxZ));
        probes.add(new Location(world, maxX, y, maxZ));
        probes.add(new Location(world, midX, y, midZ));

        for (final Location probe : probes) {
            // Column (2D) lookup: a claim box is tall, so a protected zone must veto
            // it regardless of the zone's optional Y band or the probe's sample height.
            final hu.taliann.icesmp.data.Territory territory =
                    territoryManager.getTerritoryColumnAt(world.getName(), probe.getBlockX(), probe.getBlockZ());
            if (territory != null) {
                if (blockProtectedZone && !territory.type().isClaimable()) {
                    return "claim-in-protected-zone";
                }
                if (blockFactionTerritory && territory.type().isClaimable()) {
                    return "claim-in-territory";
                }
            }
        }

        // A WG-átfedés VALÓDI box-metszés a világ teljes magasságán: a pont-mintavétel
        // (chunk-közép + sarkok, tengerszinten) átengedte a minták közé eső kicsi/keskeny
        // és a más magasságban lévő régiót. FAIL-CLOSED: ha a híd épp hibás („nem tudom"),
        // inkább elutasítjuk a claimet, mint hogy spawn/kazamata/admin-épület fölé kerüljön.
        if (blockRegion) {
            final Boolean overlap = ProtectionBridge.queryRegionOverlap(world, minX, minZ, maxX, maxZ);
            if (overlap == null || overlap) {
                return "claim-in-protected-region";
            }
        }
        return null;
    }

    /** Releases the claim the player stands in (owner only; no refund — the cost burned). */
    public synchronized String unclaimHere(final Player player) {
        final Claim claim = getClaimAt(player.getLocation());
        if (claim == null) {
            return "claim-none-here";
        }
        if (!claim.owner.equals(player.getUniqueId())) {
            return "claim-not-owner";
        }
        claims.remove(claim.id);
        rebuildIndex();
        requestSave();
        return null;
    }

    // ==================== terület-kijelölés (közös 3D selection → area) ====================

    /** Records the player's standing block through the shared 3D selection service. */
    public int[] setCorner(final Player player, final boolean first) {
        return setCorner(player, first, player.getLocation());
    }

    /** Wand selection delegates to the shared, feature-neutral service. */
    public int[] setCorner(final Player player, final boolean first, final Location location) {
        final CuboidSelectionService.Corner corner = selectionService.setCorner(player, first, location);
        return new int[] {corner.x(), corner.z()};
    }

    /** Summary of the player's completed shared selection, or null when incomplete/too large. */
    public SelectionInfo getSelectionInfo(final UUID playerId) {
        final CuboidSelectionService.Result selected = selectionService.result(playerId);
        if (!selected.ready()) {
            return null;
        }
        final CuboidSelectionService.Cuboid cuboid = selected.cuboid();
        final long columnsLong = cuboid.footprint();
        final int columns = columnsLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) columnsLong;
        final int width = cuboid.width() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cuboid.width();
        final int depth = cuboid.depth() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cuboid.depth();
        final boolean overlaps = findFootprintOverlap(cuboid.worldName(),
                cuboid.minX(), cuboid.minZ(), cuboid.maxX(), cuboid.maxZ()) != null;
        return new SelectionInfo(width, depth, columns, overlaps,
                priceFor(countColumns(playerId), columns));
    }

    /**
     * Claims the exact block rectangle of the shared selection (the claim domain still applies
     * its own XZ cap and vertical expansion policy). Null on success, else message key.
     */
    public synchronized String claimSelection(final Player player) {
        if (!isEnabled()) {
            return "claim-disabled";
        }
        final long areaMax = Math.max(16L, configManager.getLong("claims.area-max-columns", 6400L));
        // The shared service owns global 3D overflow protection. The claim's XZ footprint limit is
        // checked below because its domain price/protection model is column-based.
        final CuboidSelectionService.Result selected = selectionService.result(player.getUniqueId());
        if (selected.status() == CuboidSelectionService.Status.INCOMPLETE) {
            return "claim-area-incomplete";
        }
        if (selected.status() == CuboidSelectionService.Status.TOO_LARGE) {
            return "claim-area-too-big";
        }
        final CuboidSelectionService.Cuboid cuboid = selected.cuboid();
        if (!player.getWorld().getUID().equals(cuboid.worldId())) {
            return "claim-area-cross-world";
        }

        final long columns = cuboid.footprint();
        if (columns > areaMax) {
            return "claim-area-too-big";
        }

        final int anchorY = cuboid.minY() + (cuboid.maxY() - cuboid.minY()) / 2;
        final String errorKey = createClaim(player, player.getWorld(), cuboid.minX(), cuboid.minZ(),
                cuboid.maxX(), cuboid.maxZ(), anchorY);
        if (errorKey != null) {
            return switch (errorKey) {
                case "claim-already-taken" -> "claim-area-foreign";
                case "claim-overlap-own" -> "claim-area-overlap-own";
                default -> errorKey;
            };
        }
        selectionService.clear(player.getUniqueId());
        return null;
    }

    // ==================== függőleges bővítés (menüből, pénzért) ====================

    /**
     * Extends the vertical range of the claim the player stands in by
     * {@code claims.y-extend-step} blocks up or down, for columns × per-column
     * price (burned). Null on success, else message key.
     */
    public synchronized String extendClaim(final Player player, final boolean up) {
        if (!isEnabled()) {
            return "claim-disabled";
        }
        final Claim claim = getClaimAt(player.getLocation());
        if (claim == null) {
            return "claim-none-here";
        }
        if (!claim.owner.equals(player.getUniqueId())) {
            return "claim-not-owner";
        }
        final World world = player.getWorld();
        final int step = Math.max(1, configManager.getInt("claims.y-extend-step", 5));
        final int newMinY = up ? claim.minY : Math.max(world.getMinHeight(), claim.minY - step);
        final int newMaxY = up ? Math.min(world.getMaxHeight(), claim.maxY + step) : claim.maxY;
        if (newMinY == claim.minY && newMaxY == claim.maxY) {
            return "claim-extend-at-limit";
        }

        final double cost = Math.ceil(claim.columns()
                * Math.max(0.0D, configManager.getDouble("claims.y-extend-cost-per-column", 0.1D)) * 100.0D) / 100.0D;
        if (cost > 0.0D) {
            final CurrencyType currency = CurrencyType.fromFactionType(factionManager.getFaction(player.getUniqueId()));
            if (!currencyManager.deductFromBalance(player.getUniqueId(), currency, cost)) {
                return "claim-insufficient";
            }
        }

        final Claim extended = new Claim(claim.id, claim.world, claim.minX, newMinY, claim.minZ,
                claim.maxX, newMaxY, claim.maxZ, claim.owner, claim.ownerName, claim.claimedAt);
        extended.trusted.addAll(claim.trusted);
        claims.put(claim.id, extended);
        rebuildIndex();
        requestSave();
        return null;
    }

    /** The price of one vertical extension step for the claim at the player (or -1 if none/owned by other). */
    public double extendCostAt(final Player player) {
        final Claim claim = getClaimAt(player.getLocation());
        if (claim == null || !claim.owner.equals(player.getUniqueId())) {
            return -1.0D;
        }
        return Math.ceil(claim.columns()
                * Math.max(0.0D, configManager.getDouble("claims.y-extend-cost-per-column", 0.1D)) * 100.0D) / 100.0D;
    }

    // ==================== admin / trust ====================

    /** Admin: releases the claim at the location regardless of owner. Returns false if unclaimed. */
    public synchronized boolean adminUnclaimAt(final Location location) {
        final Claim claim = getClaimAt(location);
        if (claim == null) {
            return false;
        }
        claims.remove(claim.id);
        rebuildIndex();
        requestSave();
        return true;
    }

    /** Grants the named ONLINE player full access to every claim of the owner. */
    public synchronized String trust(final Player owner, final String targetName) {
        final Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            return "target-player-offline";
        }
        if (target.getUniqueId().equals(owner.getUniqueId())) {
            return "claim-trust-self";
        }
        boolean any = false;
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(owner.getUniqueId())) {
                claim.trusted.add(target.getUniqueId());
                any = true;
            }
        }
        if (!any) {
            return "claim-no-claims";
        }
        requestSave();
        return null;
    }

    /** Revokes the named player's access from every claim of the owner (offline target OK). */
    public synchronized String untrust(final Player owner, final String targetName) {
        final UUID targetId = resolveIdByName(targetName);
        if (targetId == null) {
            return "target-player-offline";
        }
        boolean any = false;
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(owner.getUniqueId()) && claim.trusted.remove(targetId)) {
                any = true;
            }
        }
        if (!any) {
            return "claim-not-trusted";
        }
        requestSave();
        return null;
    }

    /** Trusted-player names of the claim the player stands in (for /claim info). */
    public List<String> trustedNamesAt(final Location location) {
        final Claim claim = getClaimAt(location);
        if (claim == null) {
            return List.of();
        }
        final List<String> names = new ArrayList<>();
        for (final UUID trustedId : claim.trusted) {
            final Player online = Bukkit.getPlayer(trustedId);
            names.add(online != null ? online.getName() : Bukkit.getOfflinePlayer(trustedId).getName());
        }
        names.removeIf(java.util.Objects::isNull);
        return names;
    }

    /**
     * Read-only aggregate of every player trusted on ANY of the owner's claims
     * (union across all their claim boxes). Used by {@code ClaimTrustGUI} to list
     * the "revoke" tiles — does not mutate any trust state.
     */
    public synchronized Set<UUID> trustedPlayers(final UUID owner) {
        final Set<UUID> result = new java.util.LinkedHashSet<>();
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(owner)) {
                result.addAll(claim.trusted);
            }
        }
        return result;
    }

    // ==================== határ-megjelenítés + belépés-értesítés ====================

    /**
     * Draws the exact block-precise outlines of the claims around the player for a
     * few seconds (own/trusted=green, foreign=flame), plus a composter preview of
     * the quick-claim square when standing on unclaimed ground. Corner posts hint
     * the vertical extent. Folia-safe: repeating task on the player's own entity
     * scheduler, particles sent only to that player, index reads lock-free.
     */
    public void showBorder(final Player player) {
        final int seconds = Math.max(2, configManager.getInt("claims.border.show-seconds", 8));
        if (configManager.getBoolean("display-fx.claim-wall.enabled", true)) {
            showDisplayWalls(player, seconds);
        }
        final int[] frames = {0};
        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (frames[0]++ >= seconds || !player.isOnline()) {
                task.cancel();
                return;
            }
            drawBorderFrame(player);
        }, null, 1L, 20L);
    }

    /**
     * DisplayFx-pilot: a közeli claimek köré EGYSZER (nem frame-enként) izzó fényfalat állít a
     * BlockDisplay-rétegből — claim-élenként egy megnyújtott, per-nézős, {@code seconds} múlva
     * eltűnő entitás (saját/trusted=zöld, idegen=piros). A particle-perem a terep-követő részlet,
     * ez a folytonos, olvasható határ.
     */
    private void showDisplayWalls(final Player player, final int seconds) {
        final Location location = player.getLocation();
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final String worldName = world.getName();
        final int radius = Math.max(1, configManager.getInt("claims.border.radius", 2));
        final int pcx = location.getBlockX() >> 4;
        final int pcz = location.getBlockZ() >> 4;
        final java.util.LinkedHashSet<Claim> nearby = new java.util.LinkedHashSet<>();
        final Map<String, List<Claim>> index = chunkIndex;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                final List<Claim> hits = index.get(chunkKey(worldName, cx, cz));
                if (hits != null) {
                    nearby.addAll(hits);
                }
            }
        }
        if (nearby.isEmpty()) {
            return;
        }
        final int height = Math.max(1, configManager.getInt("display-fx.claim-wall.height", 3));
        final int ticks = seconds * 20;
        final org.bukkit.block.data.BlockData block = wallBlockData();
        for (final Claim claim : nearby) {
            final org.bukkit.Color glow = claim.isTrusted(player.getUniqueId())
                    ? org.bukkit.Color.fromRGB(0x3BE24A) : org.bukkit.Color.fromRGB(0xE23B3B);
            final float width = claim.maxX + 1 - claim.minX;
            final float depth = claim.maxZ + 1 - claim.minZ;
            final double baseY = hu.taliann.icesmp.utils.ParticleUtil.markerY(
                    world, claim.minX + (int) (width / 2), claim.minZ + (int) (depth / 2), location.getY()) - 1.2D;
            hu.taliann.icesmp.utils.DisplayFxUtil.wallSegment(plugin,
                    new Location(world, claim.minX, baseY, claim.minZ), width, height, 0.1F, block, glow, ticks, player);
            hu.taliann.icesmp.utils.DisplayFxUtil.wallSegment(plugin,
                    new Location(world, claim.minX, baseY, claim.maxZ + 1), width, height, 0.1F, block, glow, ticks, player);
            hu.taliann.icesmp.utils.DisplayFxUtil.wallSegment(plugin,
                    new Location(world, claim.minX, baseY, claim.minZ), 0.1F, height, depth, block, glow, ticks, player);
            hu.taliann.icesmp.utils.DisplayFxUtil.wallSegment(plugin,
                    new Location(world, claim.maxX + 1, baseY, claim.minZ), 0.1F, height, depth, block, glow, ticks, player);
        }
    }

    /** A fényfal blokk-anyaga configból (áttetsző üveg default), fail-safe fallbackkal. */
    private org.bukkit.block.data.BlockData wallBlockData() {
        final String name = configManager.getString("display-fx.claim-wall.material", "LIGHT_BLUE_STAINED_GLASS");
        final org.bukkit.Material material = org.bukkit.Material.matchMaterial(name);
        return (material != null && material.isBlock() ? material : org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS)
                .createBlockData();
    }

    private void drawBorderFrame(final Player player) {
        final Location location = player.getLocation();
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final String worldName = world.getName();
        final int radius = Math.max(1, configManager.getInt("claims.border.radius", 2));
        final int pcx = location.getBlockX() >> 4;
        final int pcz = location.getBlockZ() >> 4;

        final java.util.LinkedHashSet<Claim> nearby = new java.util.LinkedHashSet<>();
        final Map<String, List<Claim>> index = chunkIndex;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                final List<Claim> hits = index.get(chunkKey(worldName, cx, cz));
                if (hits != null) {
                    nearby.addAll(hits);
                }
            }
        }
        for (final Claim claim : nearby) {
            final Particle particle = claim.isTrusted(player.getUniqueId())
                    ? Particle.HAPPY_VILLAGER : Particle.FLAME;
            drawBoxOutline(player, world, claim.minX, claim.minZ, claim.maxX, claim.maxZ,
                    claim.minY, claim.maxY, location.getBlockY(), particle);
        }

        if (getClaimAt(location) == null) {
            final int half = quickSize() / 2;
            final int minX = location.getBlockX() - half;
            final int minZ = location.getBlockZ() - half;
            drawBoxOutline(player, world, minX, minZ, minX + quickSize() - 1, minZ + quickSize() - 1,
                    location.getBlockY(), location.getBlockY(), location.getBlockY(), Particle.COMPOSTER);
        }
    }

    /**
     * TEREP-KÖVETŐ perem („földszinten, a blokkok felett") + TELJES magasságú sarok-oszlopok:
     * a peremvonal minden pontja a saját oszlopának legfelső blokkja fölé kerül
     * — dombon/völgyön átfutó határnál is a talajt követi; ha a néző jóval a felszín alatt jár
     * (barlang), a perem a néző szintjén IS kirajzolódik. A sarok-oszlopok a claim teljes
     * minY→maxY tartományát mutatják, ritkított mintavétellel.
     */
    private void drawBoxOutline(final Player player, final World world, final int minX, final int minZ,
                                final int maxX, final int maxZ, final int minY, final int maxY,
                                final int viewerY, final Particle particle) {
        for (int x = minX; x <= maxX + 1; x += 2) {
            drawEdgePoint(player, world, x, minZ, viewerY, particle);
            drawEdgePoint(player, world, x, maxZ + 1, viewerY, particle);
        }
        for (int z = minZ; z <= maxZ + 1; z += 2) {
            drawEdgePoint(player, world, minX, z, viewerY, particle);
            drawEdgePoint(player, world, maxX + 1, z, viewerY, particle);
        }
        // Sarok-oszlopok a claim TELJES függőleges kiterjedésén (max ~13 pont oszloponként).
        final int step = Math.max(3, (maxY - minY) / 12);
        for (int py = minY; py <= maxY; py += step) {
            player.spawnParticle(particle, new Location(world, minX, py + 0.5D, minZ), 1, 0, 0, 0, 0);
            player.spawnParticle(particle, new Location(world, maxX + 1, py + 0.5D, minZ), 1, 0, 0, 0, 0);
            player.spawnParticle(particle, new Location(world, minX, py + 0.5D, maxZ + 1), 1, 0, 0, 0, 0);
            player.spawnParticle(particle, new Location(world, maxX + 1, py + 0.5D, maxZ + 1), 1, 0, 0, 0, 0);
        }
    }

    /** Egy perem-pont: terepre igazítva; barlangban (néző jóval a felszín alatt) plusz pont a néző szintjén. */
    private void drawEdgePoint(final Player player, final World world, final int x, final int z,
                               final int viewerY, final Particle particle) {
        final double groundY = hu.taliann.icesmp.utils.ParticleUtil.markerY(world, x, z, viewerY + 1.2D);
        player.spawnParticle(particle, new Location(world, x, groundY, z), 1, 0, 0, 0, 0);
        if (viewerY + 4.0D < groundY - 1.2D) {
            player.spawnParticle(particle, new Location(world, x, viewerY + 1.2D, z), 1, 0, 0, 0, 0);
        }
    }

    /**
     * Border-cross hook (move listener, player's own region thread): action-bar
     * notice when the covering claim changes. Returns "own" / "foreign" /
     * "wilderness", or null when nothing changed.
     */
    public String handleChunkEnter(final Player player, final Location to) {
        if (!isEnabled() || !configManager.getBoolean("claims.border.enter-notice", true)) {
            return null;
        }
        final Claim claim = getClaimAt(to);
        final String currentId = claim == null ? "" : claim.id;
        final String previousId = lastClaimId.put(player.getUniqueId(), currentId);
        if (previousId == null || currentId.equals(previousId)) {
            return null; // Első mérés vagy nincs határátlépés — nincs értesítés.
        }
        if (claim == null) {
            return "wilderness";
        }
        return claim.isTrusted(player.getUniqueId()) ? "own" : "foreign";
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        // Corner state belongs to CuboidSelectionService and is cleaned centrally.
        lastClaimId.remove(playerId);
    }

    // ==================== persistence ====================

    @Override
    public synchronized void load() {
        claims.clear();
        if (!storageFile.exists()) {
            rebuildIndex();
            return;
        }
        final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
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
                    // Régi, chunk-alapú bejegyzés (world;cx;cz) migrálása: a teljes chunk,
                    // teljes magasságban — a korábbi "bedrock-to-sky" védelem megmarad.
                    final String[] parts = key.split(";");
                    final int baseX = Integer.parseInt(parts[1]) << 4;
                    final int baseZ = Integer.parseInt(parts[2]) << 4;
                    final World world = Bukkit.getWorld(parts[0]);
                    final int minY = world == null ? -64 : world.getMinHeight();
                    final int maxY = world == null ? 320 : world.getMaxHeight();
                    claim = new Claim(UUID.randomUUID().toString(), parts[0],
                            baseX, minY, baseZ, baseX + 15, maxY, baseZ + 15, owner, ownerName, claimedAt);
                } else {
                    claim = new Claim(key,
                            section.getString(key + ".world", "world"),
                            section.getInt(key + ".min-x"), section.getInt(key + ".min-y"),
                            section.getInt(key + ".min-z"), section.getInt(key + ".max-x"),
                            section.getInt(key + ".max-y"), section.getInt(key + ".max-z"),
                            owner, ownerName, claimedAt);
                }
                for (final String trustedId : section.getStringList(key + ".trusted")) {
                    claim.trusted.add(UUID.fromString(trustedId));
                }
                claims.put(claim.id, claim);
            } catch (final IllegalArgumentException exception) {
                plugin.getLogger().warning("Hibás claim-bejegyzés kihagyva: " + key);
            }
        }
        rebuildIndex();
    }

    /**
     * Synchronously flushes claims to disk. Used on shutdown; gameplay mutations
     * call {@link #requestSave()} instead, which debounces the full-file rewrite
     * off the region threads (mirrors the CurrencyManager pattern).
     */
    @Override
    public synchronized void save() {
        flushToDisk();
    }

    /** Schedules a debounced asynchronous flush — many claim edits in a short window coalesce into one write. */
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
            yaml.set(basePath + ".owner", claim.owner.toString());
            yaml.set(basePath + ".owner-name", claim.ownerName);
            yaml.set(basePath + ".claimed-at", claim.claimedAt);
            if (!claim.trusted.isEmpty()) {
                yaml.set(basePath + ".trusted", claim.trusted.stream().map(UUID::toString).toList());
            }
        }
        return yaml;
    }

    // ==================== internals ====================

    /** Rebuilds the chunk→claims lookup and swaps it atomically (call under synchronized). */
    private void rebuildIndex() {
        final Map<String, List<Claim>> fresh = new HashMap<>();
        for (final Claim claim : claims.values()) {
            for (int cx = claim.minX >> 4; cx <= claim.maxX >> 4; cx++) {
                for (int cz = claim.minZ >> 4; cz <= claim.maxZ >> 4; cz++) {
                    fresh.computeIfAbsent(chunkKey(claim.world, cx, cz), k -> new ArrayList<>()).add(claim);
                }
            }
        }
        fresh.replaceAll((k, list) -> List.copyOf(list));
        chunkIndex = Map.copyOf(fresh);
    }

    private UUID resolveIdByName(final String name) {
        final Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        // Untrust must work for offline players; claims store the id, so look it up.
        for (final Claim claim : claims.values()) {
            for (final UUID trustedId : claim.trusted) {
                final String knownName = Bukkit.getOfflinePlayer(trustedId).getName();
                if (knownName != null && knownName.equalsIgnoreCase(name)) {
                    return trustedId;
                }
            }
        }
        return null;
    }

    private static String chunkKey(final String world, final int chunkX, final int chunkZ) {
        return world + ";" + chunkX + ";" + chunkZ;
    }
}
