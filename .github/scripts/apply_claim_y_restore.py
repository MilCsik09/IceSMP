from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def write(path, text):
    (ROOT / path).write_text(text, encoding="utf-8")

def replace_once(path, old, new):
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one occurrence, got {count}: {old[:180]!r}")
    write(path, text.replace(old, new, 1))

def regex_once(path, pattern, replacement, flags=0):
    text = read(path)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{path}: regex expected once, got {count}: {pattern[:180]!r}")
    write(path, updated)

claim = "src/main/java/hu/taliann/icesmp/managers/ClaimManager.java"
display = "src/main/java/hu/taliann/icesmp/utils/DisplayFxUtil.java"
command = "src/main/java/hu/taliann/icesmp/commands/ClaimCommand.java"
regression = "src/regression/java/hu/taliann/icesmp/runtime/RuntimeHardeningRegressionSuite.java"
audit = "docs/RUNTIME_HARDENING_AUDIT.md"

replace_once(
    claim,
''' * Native, BLOCK-precise 2D player-claim system (replaces the external
 * SimpleClaimSystem plugin, integrated with the IceSMP economy and war rules).
 * A normal claim is an exact X-Z column shape: quick/two-corner rectangles and
 * territory-style multi-point polygons share the same immutable geometry. Legacy
 * minY/maxY fields remain persistence-only and never affect membership, protection
 * or rendering.
''',
''' * Native, BLOCK-precise 3D player-claim system (replaces the external
 * SimpleClaimSystem plugin, integrated with the IceSMP economy and war rules).
 * A claim combines an exact X-Z shape (quick/two-corner rectangle or territory-style
 * polygon) with a bounded inclusive Y range. New claims reserve the configured
 * height/depth around their anchor Y and can be extended up or down for money.
'''
)

replace_once(
    claim,
'''    /** One exact X-Z column shape, its owner and trusted players. */
''',
'''    /** One exact X-Z shape with an inclusive Y range, its owner and trusted players. */
'''
)

replace_once(
    claim,
'''        private boolean contains(final String worldName, final int x, final int z) {
            return world.equals(worldName) && shape.contains(x, z);
        }
''',
'''        private boolean contains(final String worldName, final int x, final int y, final int z) {
            return world.equals(worldName)
                    && y >= minY && y <= maxY
                    && shape.contains(x, z);
        }
'''
)

replace_once(
    claim,
'''    /** Territory-style multi-point personal-claim selection. */
    private static final class PolygonSelection {
        private String world;
        private final List<ClaimShape.Point> points = new ArrayList<>();
    }
''',
'''    /** Territory-style multi-point personal-claim selection. */
    private static final class PolygonSelection {
        private String world;
        private int anchorY;
        private boolean hasAnchor;
        private final List<ClaimShape.Point> points = new ArrayList<>();
    }
'''
)

replace_once(
    claim,
'''    /** The claim covering the exact X-Z block column, or null. */
''',
'''    /** The claim covering the exact X/Y/Z block location, or null. */
'''
)

replace_once(
    claim,
'''            if (claim.contains(worldName, location.getBlockX(), location.getBlockZ())) {
''',
'''            if (claim.contains(worldName, location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
'''
)

replace_once(
    claim,
'''     * Whether the player may build/interact at the location: true when unclaimed
     * or when they own / are trusted in the covering claim. Y is intentionally ignored. (The admin bypass permission is the listener's concern.)
''',
'''     * Whether the player may build/interact at the location: true when unclaimed
     * (including above/below a claim's Y range), or when they own / are trusted in
     * the covering claim. The admin bypass permission is the listener's concern.
'''
)

replace_once(
    claim,
'''    private int polygonMaxPoints() {
        return Math.max(3, configManager.getInt("claims.polygon-max-points", 64));
    }


''',
'''    private int polygonMaxPoints() {
        return Math.max(3, configManager.getInt("claims.polygon-max-points", 64));
    }

    private int defaultHeight() {
        return Math.max(1, configManager.getInt("claims.default-height", 20));
    }

    private int defaultDepth() {
        return Math.max(1, configManager.getInt("claims.default-depth", 20));
    }


'''
)

replace_once(
    claim,
'''                entries.add(claim.world + " (" + claim.minX + ", " + claim.minZ + ")→(" + claim.maxX + ", "
                        + claim.maxZ + ") — " + (claim.shape.isPolygon()
                        ? "poligon, " + claim.columns() + " oszlop"
                        : (claim.maxX - claim.minX + 1) + "×" + (claim.maxZ - claim.minZ + 1)));
''',
'''                entries.add(claim.world + " (" + claim.minX + ", " + claim.minZ + ")→(" + claim.maxX + ", "
                        + claim.maxZ + ") Y " + claim.minY + ".." + claim.maxY + " — "
                        + (claim.shape.isPolygon()
                        ? "poligon, " + claim.columns() + " oszlop"
                        : (claim.maxX - claim.minX + 1) + "×" + (claim.maxZ - claim.minZ + 1)));
'''
)

replace_once(
    claim,
'''    /** Quick-claim: a quick-size² X-Z square centred on the player. */
''',
'''    /** Quick-claim: a quick-size² X-Z square centred on the player, with the default Y range. */
'''
)

replace_once(
    claim,
'''        return createClaim(player, location.getWorld(),
                minX, minZ, minX + quickSize() - 1, minZ + quickSize() - 1);
''',
'''        return createClaim(player, location.getWorld(),
                minX, minZ, minX + quickSize() - 1, minZ + quickSize() - 1,
                location.getBlockY());
'''
)

replace_once(
    claim,
'''    private String createClaim(final Player player, final World world,
                               final int minX, final int minZ,
                               final int maxX, final int maxZ) {
        try {
            return createClaimShape(player, world, ClaimShape.rectangle(
                    ClaimFootprint.between(minX, minZ, maxX, maxZ), areaMaxColumns()));
''',
'''    private String createClaim(final Player player, final World world,
                               final int minX, final int minZ,
                               final int maxX, final int maxZ,
                               final int anchorY) {
        try {
            return createClaimShape(player, world, ClaimShape.rectangle(
                    ClaimFootprint.between(minX, minZ, maxX, maxZ), areaMaxColumns()), anchorY);
'''
)

replace_once(
    claim,
'''    private String createClaimShape(final Player player, final World world, final ClaimShape shape) {
''',
'''    private String createClaimShape(final Player player, final World world,
                                    final ClaimShape shape, final int anchorY) {
'''
)

replace_once(
    claim,
'''        final ClaimFootprint bounds = shape.bounds();
        final Claim claim = new Claim(UUID.randomUUID().toString(), worldName,
                bounds.minX(), world.getMinHeight(), bounds.minZ(),
                bounds.maxX(), world.getMaxHeight() - 1, bounds.maxZ(),
''',
'''        final ClaimFootprint bounds = shape.bounds();
        final int minY = Math.max(world.getMinHeight(), anchorY - defaultDepth());
        final int maxY = Math.min(world.getMaxHeight() - 1, anchorY + defaultHeight());
        final Claim claim = new Claim(UUID.randomUUID().toString(), worldName,
                bounds.minX(), minY, bounds.minZ(),
                bounds.maxX(), maxY, bounds.maxZ(),
'''
)

replace_once(
    claim,
'''     * Claims the exact normalized X-Z block rectangle between the two corners.
     * Corner Y values are selection metadata only and never constrain a normal claim.
     * Null on success, else message key.
''',
'''     * Claims the exact normalized X-Z block rectangle between the two corners.
     * The initial vertical range is centred on the two selected Y values' midpoint,
     * then expanded by the configured default depth/height. Null on success, else message key.
'''
)

replace_once(
    claim,
'''        final String errorKey = createClaim(player, player.getWorld(), minX, minZ, maxX, maxZ);
''',
'''        final int anchorY = (Math.min(selection.y1, selection.y2)
                + Math.max(selection.y1, selection.y2)) / 2;
        final String errorKey = createClaim(
                player, player.getWorld(), minX, minZ, maxX, maxZ, anchorY);
'''
)

replace_once(
    claim,
'''            if (!worldName.equals(selection.world)) {
                selection.points.clear();
                selection.world = worldName;
            }
''',
'''            if (!worldName.equals(selection.world)) {
                selection.points.clear();
                selection.hasAnchor = false;
                selection.world = worldName;
            }
'''
)

replace_once(
    claim,
'''                if (selection.points.size() >= polygonMaxPoints()) {
                    return -selection.points.size();
                }
                selection.points.add(point);
''',
'''                if (selection.points.size() >= polygonMaxPoints()) {
                    return -selection.points.size();
                }
                if (!selection.hasAnchor) {
                    selection.anchorY = location.getBlockY();
                    selection.hasAnchor = true;
                }
                selection.points.add(point);
'''
)

replace_once(
    claim,
'''            selection.points.remove(selection.points.size() - 1);
            return selection.points.size();
''',
'''            selection.points.remove(selection.points.size() - 1);
            if (selection.points.isEmpty()) {
                selection.hasAnchor = false;
            }
            return selection.points.size();
'''
)

regex_once(
    claim,
 r'''        final List<ClaimShape\.Point> points;\n        final String worldName;\n        synchronized \(selection\) \{\n            points = List\.copyOf\(selection\.points\);\n            worldName = selection\.world;\n        \}\n        if \(points\.size\(\) < 3\) return "claim-polygon-too-few";''',
'''        final List<ClaimShape.Point> points;
        final String worldName;
        final int anchorY;
        synchronized (selection) {
            points = List.copyOf(selection.points);
            worldName = selection.world;
            anchorY = selection.anchorY;
        }
        if (points.size() < 3) return "claim-polygon-too-few";'''
)

replace_once(
    claim,
'''        final String error = createClaimShape(player, player.getWorld(), shape);
''',
'''        final String error = createClaimShape(player, player.getWorld(), shape, anchorY);
'''
)

regex_once(
    claim,
 r'''    // ==================== legacy vertical API ====================\n\n    /\*\* Normal claims are intentionally column-based; vertical extension is unsupported\. \*/\n    public String extendClaim\(final Player player, final boolean up\) \{\n        return "claim-vertical-unsupported";\n    \}\n\n    /\*\* There is no vertical extension price for a 2D claim\. \*/\n    public double extendCostAt\(final Player player\) \{\n        return -1\.0D;\n    \}\n''',
'''    // ==================== függőleges bővítés (pénzért) ====================

    /**
     * Extends the vertical range of the claim the player stands in by
     * {@code claims.y-extend-step} blocks up or down. The X-Z shape stays exact,
     * including polygon vertices, and the extension price is burned.
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
        final int newMinY = up
                ? claim.minY
                : Math.max(world.getMinHeight(), claim.minY - step);
        final int newMaxY = up
                ? Math.min(world.getMaxHeight() - 1, claim.maxY + step)
                : claim.maxY;
        if (newMinY == claim.minY && newMaxY == claim.maxY) {
            return "claim-extend-at-limit";
        }

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

    /** Price of one vertical extension step for the claim at the player. */
    public double extendCostAt(final Player player) {
        final Claim claim = getClaimAt(player.getLocation());
        if (claim == null || !claim.owner.equals(player.getUniqueId())) {
            return -1.0D;
        }
        return extendCost(claim);
    }

    private double extendCost(final Claim claim) {
        return Math.ceil(claim.columns()
                * Math.max(0.0D, configManager.getDouble(
                "claims.y-extend-cost-per-column", 0.1D)) * 100.0D) / 100.0D;
    }
''',
flags=re.S
)

replace_once(
    claim,
'''     * Draws the exact block-precise outlines of the claims around the player for a
     * few seconds (own/trusted=green, foreign=flame), plus a composter preview of
     * the quick-claim square when standing on unclaimed ground. The Y coordinate is
     * display-only and never represents a lower or upper claim boundary. Folia-safe: repeating task on the player's own entity
     * scheduler, particles sent only to that player, index reads lock-free.
''',
'''     * Draws the exact 3D boundaries of nearby claims for a few seconds
     * (own/trusted=green, foreign=flame), plus a composter preview of the quick-claim
     * square on unclaimed ground. Existing claim walls are clipped exactly to each
     * claim's inclusive minY..maxY range. Folia-safe: entity/region-owned schedulers,
     * per-viewer displays and lock-free index reads.
'''
)

replace_once(
    claim,
'''    /**
     * Terrain-following BlockDisplay wall. Every boundary block gets its own
     * region-owned vertical segment, so the complete perimeter follows terrain.
     */
''',
'''    /**
     * Exact claimed-volume BlockDisplay wall. Every X-Z boundary block owns one
     * region-scheduled vertical segment from minY through maxY, and no display is
     * created above or below the actually claimed range.
     */
'''
)

replace_once(
    claim,
'''        final float height = Math.max(1,
                configManager.getInt("display-fx.claim-wall.height", 3));
        final int ticks = seconds * 20;
''',
'''        final int ticks = seconds * 20;
'''
)

replacements = [
('''hu.taliann.icesmp.utils.DisplayFxUtil.terrainWallColumn(plugin, world,
                            x, claim.minZ, x, claim.minZ, 1.0F, height, 0.08F,
                            block, glow, ticks, player);''',
 '''hu.taliann.icesmp.utils.DisplayFxUtil.claimedWallColumn(plugin, world,
                            x, claim.minZ, x, claim.minZ, 1.0F, 0.08F,
                            claim.minY, claim.maxY, block, glow, ticks, player);'''),
('''hu.taliann.icesmp.utils.DisplayFxUtil.terrainWallColumn(plugin, world,
                            x, claim.maxZ, x, claim.maxZ + 1.0D, 1.0F, height, 0.08F,
                            block, glow, ticks, player);''',
 '''hu.taliann.icesmp.utils.DisplayFxUtil.claimedWallColumn(plugin, world,
                            x, claim.maxZ, x, claim.maxZ + 1.0D, 1.0F, 0.08F,
                            claim.minY, claim.maxY, block, glow, ticks, player);'''),
('''hu.taliann.icesmp.utils.DisplayFxUtil.terrainWallColumn(plugin, world,
                            claim.minX, z, claim.minX, z, 0.08F, height, 1.0F,
                            block, glow, ticks, player);''',
 '''hu.taliann.icesmp.utils.DisplayFxUtil.claimedWallColumn(plugin, world,
                            claim.minX, z, claim.minX, z, 0.08F, 1.0F,
                            claim.minY, claim.maxY, block, glow, ticks, player);'''),
('''hu.taliann.icesmp.utils.DisplayFxUtil.terrainWallColumn(plugin, world,
                            claim.maxX, z, claim.maxX + 1.0D, z, 0.08F, height, 1.0F,
                            block, glow, ticks, player);''',
 '''hu.taliann.icesmp.utils.DisplayFxUtil.claimedWallColumn(plugin, world,
                            claim.maxX, z, claim.maxX + 1.0D, z, 0.08F, 1.0F,
                            claim.minY, claim.maxY, block, glow, ticks, player);'''),
('''hu.taliann.icesmp.utils.DisplayFxUtil.terrainWallColumn(plugin, world,
                            point.x(), point.z(), point.x(), point.z(),
                            1.0F, height, 1.0F, block, glow, ticks, player);''',
 '''hu.taliann.icesmp.utils.DisplayFxUtil.claimedWallColumn(plugin, world,
                            point.x(), point.z(), point.x(), point.z(),
                            1.0F, 1.0F, claim.minY, claim.maxY,
                            block, glow, ticks, player);''')
]
for old, new in replacements:
    replace_once(claim, old, new)

replace_once(
    claim,
'''            drawShapeOutline(player, world, claim.shape, location.getBlockY(), particle);
''',
'''            drawShapeOutline(player, world, claim.shape,
                    claim.minY, claim.maxY, location.getBlockY(), particle);
'''
)

replace_once(
    claim,
'''    private void drawShapeOutline(final Player player, final World world, final ClaimShape shape,
                                  final int viewerY, final Particle particle) {
        final List<ClaimShape.Point> boundary = shape.boundaryColumns();
        for (int index = 0; index < boundary.size(); index += 2) {
            final ClaimShape.Point point = boundary.get(index);
            drawEdgePoint(player, world, point.x(), point.z(), viewerY, particle);
        }
    }
''',
'''    private void drawShapeOutline(final Player player, final World world, final ClaimShape shape,
                                  final int minY, final int maxY,
                                  final int viewerY, final Particle particle) {
        final List<ClaimShape.Point> boundary = shape.boundaryColumns();
        for (int index = 0; index < boundary.size(); index += 2) {
            final ClaimShape.Point point = boundary.get(index);
            drawEdgePoint(player, world, point.x(), point.z(), minY, maxY, viewerY, particle);
        }
    }
'''
)

replace_once(
    claim,
'''        drawShapeOutline(player, world, ClaimShape.rectangle(footprint), viewerY, particle);
''',
'''        drawShapeOutline(player, world, ClaimShape.rectangle(footprint),
                viewerY, viewerY, viewerY, particle);
'''
)

replace_once(
    claim,
'''    /** Egy perem-pont: terepre igazítva; barlangban (néző jóval a felszín alatt) plusz pont a néző szintjén. */
    private void drawEdgePoint(final Player player, final World world, final int x, final int z,
                               final int viewerY, final Particle particle) {
        final double groundY = hu.taliann.icesmp.utils.ParticleUtil.markerY(world, x, z, viewerY + 1.2D);
        player.spawnParticle(particle, new Location(world, x, groundY, z), 1, 0, 0, 0, 0);
        if (viewerY + 4.0D < groundY - 1.2D) {
            player.spawnParticle(particle, new Location(world, x, viewerY + 1.2D, z), 1, 0, 0, 0, 0);
        }
    }
''',
'''    /** One boundary marker, always inside the actually claimed vertical range. */
    private void drawEdgePoint(final Player player, final World world, final int x, final int z,
                               final int minY, final int maxY,
                               final int viewerY, final Particle particle) {
        final int markerY = Math.max(minY, Math.min(maxY, viewerY));
        player.spawnParticle(particle,
                new Location(world, x, markerY + 0.5D, z), 1, 0, 0, 0, 0);
    }
'''
)

replace_once(
    display,
'''    private static void scheduleDespawn(final Plugin plugin, final Display display, final int ticks) {
''',
'''    /**
     * Spawns one wall column exactly inside an inclusive claimed Y range.
     * Region ownership is acquired from the sampled X/Z column; nothing is rendered
     * below minY or above maxY.
     */
    public static void claimedWallColumn(final Plugin plugin, final World world,
                                         final int sampleX, final int sampleZ,
                                         final double displayX, final double displayZ,
                                         final float sizeX, final float sizeZ,
                                         final int minY, final int maxY,
                                         final BlockData block, final Color glow,
                                         final int despawnTicks, final Player viewer) {
        if (plugin == null || world == null || block == null) return;
        final int clampedMinY = Math.max(world.getMinHeight(), minY);
        final int clampedMaxY = Math.min(world.getMaxHeight() - 1, maxY);
        if (clampedMinY > clampedMaxY) return;
        final Location owner = new Location(
                world, sampleX + 0.5D, clampedMinY, sampleZ + 0.5D);
        plugin.getServer().getRegionScheduler().run(plugin, owner, task -> {
            if (!world.isChunkLoaded(sampleX >> 4, sampleZ >> 4)) return;
            final Location corner = new Location(world, displayX, clampedMinY, displayZ);
            wallSegment(plugin, corner, sizeX,
                    clampedMaxY - clampedMinY + 1.0F, sizeZ,
                    block, glow, despawnTicks, viewer);
        });
    }

    private static void scheduleDespawn(final Plugin plugin, final Display display, final int ticks) {
'''
)

replace_once(
    command,
'''            case "extend" -> player.sendMessage(messageManager.get("claim-vertical-unsupported",
                    "&cA claim teljes magasságú X–Z alakzat; nincs alsó/felső Y-határa."));
''',
'''            case "extend" -> handleExtend(player, args);
'''
)

replace_once(
    command,
'''            player.sendMessage(messageManager.get("claim-info-owner",
                    "&6Tulajdonos: &f%s &7• alakzat: &f%s &7• oszlop: &f%s",
                    claim.getOwnerName(), claim.isPolygon() ? "poligon" : "téglalap", claim.columns()));
''',
'''            player.sendMessage(messageManager.get("claim-info-owner",
                    "&6Tulajdonos: &f%s &7• alakzat: &f%s &7• oszlop: &f%s &7• Y: &f%s..%s",
                    claim.getOwnerName(), claim.isPolygon() ? "poligon" : "téglalap",
                    claim.columns(), claim.minY(), claim.maxY()));
'''
)

replace_once(
    command,
'''        player.sendMessage(messageManager.get("claim-area-success",
                "&aTéglalap-claim létrehozva: &f%s&a oszlop. Ár: &f%s&a.",
''',
'''        player.sendMessage(messageManager.get("claim-area-success",
                "&aTéglalap-claim létrehozva: &f%s&a oszlop, alapból ±20 Y-blokk. Ár: &f%s&a.",
'''
)

replace_once(
    command,
'''        player.sendMessage(messageManager.get("claim-polygon-success",
                "&aPoligon-claim létrehozva: &f%s&a oszlop. Ár: &f%s&a.",
''',
'''        player.sendMessage(messageManager.get("claim-polygon-success",
                "&aPoligon-claim létrehozva: &f%s&a oszlop, alapból ±20 Y-blokk. Ár: &f%s&a.",
'''
)

replace_once(
    command,
'''    private void handleAdmin(final Player player, final String[] args) {
''',
'''    private void handleExtend(final Player player, final String[] args) {
        final boolean up = args.length < 2 || !"down".equalsIgnoreCase(args[1]);
        final double cost = claimManager.extendCostAt(player);
        final String errorKey = claimManager.extendClaim(player, up);
        if (errorKey != null) {
            sendError(player, errorKey);
            return;
        }
        player.sendMessage(messageManager.get(up ? "claim-extended-up" : "claim-extended-down",
                up ? "&aA claim teteje 5 blokkal megemelve. &7Ár: &f%s&7 (elégett)."
                        : "&aA claim alja 5 blokkal lejjebb víve. &7Ár: &f%s&7 (elégett).",
                cost <= 0.0D ? "ingyenes" : currencyManager.formatBalance(cost)));
        claimManager.showBorder(player);
    }

    private void handleAdmin(final Player player, final String[] args) {
'''
)

replace_once(
    command,
'''        player.sendMessage(messageManager.get("claim-help-management",
                "&e/claim unclaim/info/list/show/trust/untrust &7- Claimkezelés."));
''',
'''        player.sendMessage(messageManager.get("claim-help-extend",
                "&e/claim extend up|down &7- Y-határ bővítése 5 blokkal, pénzért."));
        player.sendMessage(messageManager.get("claim-help-management",
                "&e/claim unclaim/info/list/show/trust/untrust &7- Claimkezelés."));
'''
)

replace_once(
    command,
'''            case "claim-polygon-invalid" -> "&cÉrvénytelen vagy nulla területű poligon.";
            case "claim-vertical-unsupported" -> "&cA claim teljes magasságú X–Z alakzat; nincs Y-határa.";
''',
'''            case "claim-polygon-invalid" -> "&cÉrvénytelen vagy nulla területű poligon.";
            case "claim-extend-at-limit" -> "&cA claim Y-határa már elérte a világ szélét.";
'''
)

replace_once(
    command,
'''                "pos1", "pos2", "wand", "area", "point", "undo", "clearpoints", "points",
                "polygon", "polywand", "help"));
''',
'''                "pos1", "pos2", "wand", "area", "point", "undo", "clearpoints", "points",
                "polygon", "polywand", "extend", "help"));
'''
)

replace_once(
    command,
'''        if ("admin".equals(first) && args.length <= 2) {
''',
'''        if ("extend".equals(first) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return List.of("up", "down").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        if ("admin".equals(first) && args.length <= 2) {
'''
)

replace_once(
    regression,
'''        check(claim.contains("claim.contains(worldName, location.getBlockX(), location.getBlockZ())"),
                "claim lookup is Y-independent");
        check(!claim.contains("drawBoxOutline"), "claim renderer has no 3D box path");
''',
'''        check(claim.contains("claim.contains(worldName, location.getBlockX(), location.getBlockY(), location.getBlockZ())"),
                "claim lookup includes the bounded Y range");
        check(claim.contains("claims.default-height\\\", 20")
                        && claim.contains("claims.default-depth\\\", 20")
                        && claim.contains("claims.y-extend-step\\\", 5"),
                "original ±20 default Y range and five-block extension step are restored");
        check(claim.contains("claim.shape.isPolygon() ? claim.shape.vertices() : null"),
                "vertical extension preserves the exact polygon shape");
'''
)

replace_once(
    regression,
'''        check(claimCommand.contains("case \\\"polygon\\\", \\\"poligon\\\"")
                        && claimCommand.contains("case \\\"polywand\\\""),
                "normal claim command exposes territory-style polygon selection");
''',
'''        check(claimCommand.contains("case \\\"polygon\\\", \\\"poligon\\\"")
                        && claimCommand.contains("case \\\"polywand\\\"")
                        && claimCommand.contains("case \\\"extend\\\" -> handleExtend(player, args)"),
                "normal claim command exposes polygons and restored vertical extension");
'''
)

replace_once(
    regression,
'''        final String display = source("src/main/java/hu/taliann/icesmp/utils/DisplayFxUtil.java");
        check(display.contains("HeightMap.MOTION_BLOCKING_NO_LEAVES")
                        && display.contains("terrainWallColumn"),
                "BlockDisplay wall follows each owned terrain column");
        check(!claim.contains("baseY = location.getY()"),
                "claim display wall is never anchored to viewer Y");
''',
'''        final String display = source("src/main/java/hu/taliann/icesmp/utils/DisplayFxUtil.java");
        check(display.contains("claimedWallColumn")
                        && display.contains("clampedMaxY - clampedMinY + 1.0F")
                        && claim.contains("claim.minY, claim.maxY"),
                "BlockDisplay wall is clipped exactly to the actually claimed Y range");
        check(!claim.contains("baseY = location.getY()"),
                "claim display wall is never anchored to viewer Y");
        final String generalConfig = source("src/main/resources/config/general.yml");
        check(generalConfig.contains("default-height: 20")
                        && generalConfig.contains("default-depth: 20")
                        && generalConfig.contains("y-extend-step: 5"),
                "packaged config retains the proven original vertical defaults");
'''
)

text = read(audit)
section = '''
## Restored original claim Y behaviour

- Claims are again bounded 3D volumes: exact rectangle/polygon X-Z shape plus inclusive `minY..maxY`.
- New quick claims use the player's Y; rectangle selections use the two Y values' midpoint; polygons use the first boundary point's Y.
- Packaged defaults remain the proven original values: `default-height: 20`, `default-depth: 20`.
- `/claim extend up|down` again expands by `y-extend-step: 5` for the original per-column burned cost.
- X-Z overlap stays exclusive, so vertically separated claims cannot be stacked over the same footprint.
- The BlockDisplay boundary is created only from `minY` through `maxY`; no wall exists at unclaimed Y levels.
'''
if "## Restored original claim Y behaviour" not in text:
    text = text.rstrip() + "\n\n" + section.strip() + "\n"
    write(audit, text)

claim_text = read(claim)
if "claim-vertical-unsupported" in claim_text:
    raise RuntimeError("unsupported vertical API marker survived")
if "world.getMinHeight(), bounds.minZ()" in claim_text:
    raise RuntimeError("full-height claim creation survived")
if "claimedWallColumn" not in claim_text:
    raise RuntimeError("exact claimed wall call missing")
if claim_text.count("createClaimShape(") < 3:
    raise RuntimeError("unexpected createClaimShape call count")

print("restored original bounded claim Y behavior")
