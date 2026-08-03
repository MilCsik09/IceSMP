#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one occurrence, got {count}: {old[:120]!r}")
    write(path, content.replace(old, new, 1))


def replace_regex(path: str, pattern: str, replacement: str, flags: int = 0) -> None:
    content = read(path)
    updated, count = re.subn(pattern, replacement, content, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{path}: regex expected exactly one occurrence, got {count}: {pattern}")
    write(path, updated)


write("src/main/java/hu/taliann/icesmp/data/ClaimFootprint.java", r'''package hu.taliann.icesmp.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical, Y-independent personal-claim geometry. Both protection lookups and
 * visualisation consume this normalized X-Z rectangle so corner order, negative
 * coordinates and chunk boundaries cannot produce divergent results.
 */
public record ClaimFootprint(int minX, int minZ, int maxX, int maxZ) {

    public ClaimFootprint {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Claim footprint bounds must be normalized");
        }
    }

    public static ClaimFootprint between(final int x1, final int z1, final int x2, final int z2) {
        return new ClaimFootprint(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
    }

    public boolean contains(final int x, final int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(final ClaimFootprint other) {
        return minX <= other.maxX && maxX >= other.minX
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public long columns() {
        return Math.multiplyExact((long) maxX - minX + 1L, (long) maxZ - minZ + 1L);
    }

    public int outerMaxX() {
        return maxX + 1;
    }

    public int outerMaxZ() {
        return maxZ + 1;
    }

    /**
     * Sampled outside-edge points with all four corners present exactly once.
     * The insertion-ordered set also prevents diagonal joins or duplicated corner
     * particles for one-block and one-block-wide rectangles.
     */
    public List<BoundaryPoint> perimeter(final int spacing) {
        final int step = Math.max(1, spacing);
        final Set<BoundaryPoint> points = new LinkedHashSet<>();
        addHorizontal(points, minX, outerMaxX(), minZ, step);
        addVertical(points, minZ, outerMaxZ(), outerMaxX(), step);
        addHorizontal(points, minX, outerMaxX(), outerMaxZ(), step);
        addVertical(points, minZ, outerMaxZ(), minX, step);
        return List.copyOf(points);
    }

    private static void addHorizontal(final Set<BoundaryPoint> points, final int start, final int end,
                                      final int z, final int spacing) {
        for (final int x : sampledRange(start, end, spacing)) {
            points.add(new BoundaryPoint(x, z));
        }
    }

    private static void addVertical(final Set<BoundaryPoint> points, final int start, final int end,
                                    final int x, final int spacing) {
        for (final int z : sampledRange(start, end, spacing)) {
            points.add(new BoundaryPoint(x, z));
        }
    }

    private static List<Integer> sampledRange(final int start, final int end, final int spacing) {
        final List<Integer> values = new ArrayList<>();
        for (int value = start; value <= end; value += spacing) {
            values.add(value);
        }
        if (values.isEmpty() || values.get(values.size() - 1) != end) {
            values.add(end);
        }
        return values;
    }

    public record BoundaryPoint(int x, int z) { }
}
''')

# ClaimManager: one canonical X-Z footprint, viewer-only Y rendering and task ownership.
path = "src/main/java/hu/taliann/icesmp/managers/ClaimManager.java"
content = read(path)
content = content.replace("import hu.taliann.icesmp.data.CurrencyType;", "import hu.taliann.icesmp.data.CurrencyType;\nimport hu.taliann.icesmp.data.ClaimFootprint;")
content = content.replace("import org.bukkit.plugin.java.JavaPlugin;", "import org.bukkit.plugin.java.JavaPlugin;\nimport io.papermc.paper.threadedregions.scheduler.ScheduledTask;")
content = content.replace("Native, BLOCK-precise 3D player-claim system", "Native, BLOCK-precise 2D player-claim system")
content = content.replace("A claim is a box: an exact block-rectangle footprint plus a bounded Y-range\n * (by default 20 blocks up and 20 down from where it was created — the vertical\n * span can be EXTENDED for money from the menu).", "A normal claim is exactly the normalized X-Z rectangle between its two corners.\n * Legacy minY/maxY fields remain readable for storage compatibility, but never affect\n * membership, protection or rendering.")
content = content.replace("/** One claimed 3D box: exact block bounds, the owner and the trusted players. */", "/** One claimed X-Z footprint, its owner and trusted players. */")
content = content.replace("        private final int maxZ;\n        private final UUID owner;", "        private final int maxZ;\n        private final ClaimFootprint footprint;\n        private final UUID owner;")
content = content.replace("            this.maxZ = maxZ;\n            this.owner = owner;", "            this.maxZ = maxZ;\n            this.footprint = ClaimFootprint.between(minX, minZ, maxX, maxZ);\n            this.owner = owner;")
content = content.replace("            return (maxX - minX + 1) * (maxZ - minZ + 1);", "            return Math.toIntExact(footprint.columns());")
content = content.replace("        private boolean contains(final String worldName, final int x, final int y, final int z) {\n            return world.equals(worldName)\n                    && x >= minX && x <= maxX\n                    && y >= minY && y <= maxY\n                    && z >= minZ && z <= maxZ;\n        }", "        private boolean contains(final String worldName, final int x, final int z) {\n            return world.equals(worldName) && footprint.contains(x, z);\n        }")
content = content.replace("            return world.equals(worldName)\n                    && minX <= oMaxX && maxX >= oMinX\n                    && minZ <= oMaxZ && maxZ >= oMinZ;", "            return world.equals(worldName)\n                    && footprint.overlaps(ClaimFootprint.between(oMinX, oMinZ, oMaxX, oMaxZ));")
content = content.replace("    private final Map<UUID, String> lastClaimId = new ConcurrentHashMap<>();", "    private final Map<UUID, String> lastClaimId = new ConcurrentHashMap<>();\n    /** At most one owned border preview task per player. */\n    private final Map<UUID, ScheduledTask> borderTasks = new ConcurrentHashMap<>();")
content = content.replace("    /** The claim covering the exact block location (Y included), or null. */", "    /** The claim covering the exact X-Z block column, or null. */")
content = content.replace("claim.contains(worldName, location.getBlockX(), location.getBlockY(), location.getBlockZ())", "claim.contains(worldName, location.getBlockX(), location.getBlockZ())")
content = content.replace("(or outside the claim's Y-range), or when they own / are trusted in the\n     * covering claim.", "or when they own / are trusted in the covering claim. Y is intentionally ignored.")
content = re.sub(r"\n    private int defaultHeight\(\) \{.*?\n    \}\n\n    private int defaultDepth\(\) \{.*?\n    \}\n", "\n", content, flags=re.S)
content = content.replace(" + claim.maxZ + \") Y \" + claim.minY + \"..\" + claim.maxY\n                        + \" — \"", " + claim.maxZ + \") — \"")
content = content.replace("    /** Quick-claim: a quick-size² square centred on the player, default Y-range. */", "    /** Quick-claim: a quick-size² X-Z square centred on the player. */")
content = content.replace("                minX, minZ, minX + quickSize() - 1, minZ + quickSize() - 1, location.getBlockY());", "                minX, minZ, minX + quickSize() - 1, minZ + quickSize() - 1);")
content = content.replace("                               final int maxX, final int maxZ, final int anchorY) {", "                               final int maxX, final int maxZ) {")
content = content.replace("        final int minY = Math.max(world.getMinHeight(), anchorY - defaultDepth());\n        final int maxY = Math.min(world.getMaxHeight(), anchorY + defaultHeight());", "        final int minY = world.getMinHeight(); // legacy persistence field only\n        final int maxY = world.getMaxHeight() - 1; // legacy persistence field only")
content = content.replace("     * Claims the exact block-rectangle between the two corners (Y-range: the lower\n     * corner minus default depth up to the higher corner plus default height).", "     * Claims the exact normalized X-Z block rectangle between the two corners.\n     * Corner Y values are selection metadata only and never constrain a normal claim.")
content = content.replace("        final int anchorY = (Math.min(selection.y1, selection.y2) + Math.max(selection.y1, selection.y2)) / 2;\n        final String errorKey = createClaim(player, player.getWorld(), minX, minZ, maxX, maxZ, anchorY);", "        final String errorKey = createClaim(player, player.getWorld(), minX, minZ, maxX, maxZ);")
content = re.sub(r"    // ==================== függőleges bővítés \(menüből, pénzért\) ====================.*?    // ==================== admin / trust ====================", r'''    // ==================== legacy vertical API ====================

    /** Normal claims are intentionally column-based; vertical extension is unsupported. */
    public String extendClaim(final Player player, final boolean up) {
        return "claim-vertical-unsupported";
    }

    /** There is no vertical extension price for a 2D claim. */
    public double extendCostAt(final Player player) {
        return -1.0D;
    }

    // ==================== admin / trust ====================''', content, flags=re.S)
content = content.replace("     * few seconds (own/trusted=green, foreign=flame), plus a composter preview of\n     * the quick-claim square when standing on unclaimed ground. Corner posts hint\n     * the vertical extent.", "     * few seconds (own/trusted=green, foreign=flame), plus a composter preview of\n     * the quick-claim square when standing on unclaimed ground. The Y coordinate is\n     * display-only and never represents a lower or upper claim boundary.")
content = content.replace("        final int[] frames = {0};\n        player.getScheduler().runAtFixedRate(plugin, task -> {", "        final UUID playerId = player.getUniqueId();\n        final ScheduledTask previous = borderTasks.remove(playerId);\n        if (previous != null) {\n            previous.cancel();\n        }\n        final int[] frames = {0};\n        final ScheduledTask scheduled = player.getScheduler().runAtFixedRate(plugin, task -> {")
content = content.replace("                task.cancel();\n                return;", "                task.cancel();\n                borderTasks.remove(playerId, task);\n                return;", 1)
content = content.replace("        }, null, 1L, 20L);\n    }", "        }, null, 1L, 20L);\n        borderTasks.put(playerId, scheduled);\n    }", 1)
content = content.replace("            final double baseY = hu.taliann.icesmp.utils.ParticleUtil.markerY(\n                    world, claim.minX + (int) (width / 2), claim.minZ + (int) (depth / 2), location.getY()) - 1.2D;", "            final double baseY = location.getY() - 1.2D;")
content = content.replace("            drawBoxOutline(player, world, claim.minX, claim.minZ, claim.maxX, claim.maxZ,\n                    claim.minY, claim.maxY, location.getBlockY(), particle);", "            drawFootprintOutline(player, world, claim.footprint, location.getBlockY(), particle);")
content = content.replace("            drawBoxOutline(player, world, minX, minZ, minX + quickSize() - 1, minZ + quickSize() - 1,\n                    location.getBlockY(), location.getBlockY(), location.getBlockY(), Particle.COMPOSTER);", "            drawFootprintOutline(player, world,\n                    ClaimFootprint.between(minX, minZ, minX + quickSize() - 1, minZ + quickSize() - 1),\n                    location.getBlockY(), Particle.COMPOSTER);")
content = re.sub(r"    /\*\*\n     \* TEREP-KÖVETŐ perem.*?\n    \}\n\n    /\*\* Egy perem-pont", r'''    /** Terrain-following perimeter for the canonical X-Z footprint; no vertical posts. */
    private void drawFootprintOutline(final Player player, final World world, final ClaimFootprint footprint,
                                      final int viewerY, final Particle particle) {
        for (final ClaimFootprint.BoundaryPoint point : footprint.perimeter(2)) {
            drawEdgePoint(player, world, point.x(), point.z(), viewerY, particle);
        }
    }

    /** Egy perem-pont''', content, flags=re.S)
content = content.replace("        selections.remove(playerId);\n        lastClaimId.remove(playerId);", "        selections.remove(playerId);\n        lastClaimId.remove(playerId);\n        final ScheduledTask task = borderTasks.remove(playerId);\n        if (task != null) {\n            task.cancel();\n        }")
write(path, content)

# Command/help text must not advertise a 3D claim or paid vertical extension.
path = "src/main/java/hu/taliann/icesmp/commands/ClaimCommand.java"
content = read(path)
content = content.replace('            case "extend" -> handleExtend(player, args);', '            case "extend" -> player.sendMessage(messageManager.get("claim-vertical-unsupported",\n                    "&cA normál claim X–Z terület: nincs alsó/felső Y-határa és nem bővíthető függőlegesen."));')
content = content.replace("(&f%s&a oszlop)%s &7— ár", "(&f%s&a oszlop, teljes magasság)%s &7— ár")
content = content.replace("&aTerület lefoglalva: &f%s&a oszlop (±20 blokk magasságban). Ár:", "&aTerület lefoglalva: &f%s&a oszlop (minden Y-szinten). Ár:")
content = re.sub(r"\n    private void handleExtend\(final Player player, final String\[] args\) \{.*?\n    \}\n\n    private void handleAdmin", "\n    private void handleAdmin", content, flags=re.S)
content = content.replace('        player.sendMessage(messageManager.get("claim-help-extend",\n                "&e/claim extend up|down &7- A claim magasítása/mélyítése (+5 blokk, pénzért)."));\n', '')
content = content.replace('            case "claim-extend-at-limit"', '            case "claim-vertical-unsupported" -> "&cA normál claim csak X–Z téglalap; nincs Y-határa.";\n            case "claim-extend-at-limit"')
write(path, content)

# Vanish visibility must be reasserted after client retracking, while the ownership ledger remains cleanup-safe.
path = "src/main/java/hu/taliann/icesmp/managers/VanishManager.java"
content = read(path)
content = content.replace("        if (shouldHide) {\n            if (hidden.add(subjectId)) {\n                viewer.hidePlayer(plugin, subject);\n            }", "        if (shouldHide) {\n            hidden.add(subjectId);\n            // hidePlayer is idempotent. Reassert it even when our ledger already owns the\n            // pair because teleport/world-change/respawn can recreate client tracking.\n            viewer.hidePlayer(plugin, subject);")
write(path, content)

write("src/main/java/hu/taliann/icesmp/listeners/VanishListener.java", r'''package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.managers.VanishManager;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle, leak-prevention and explicit capability gates for persisted vanish state. */
public final class VanishListener implements Listener {
    private final JavaPlugin plugin;
    private final ModerationManager moderationManager;
    private final VanishManager vanishManager;
    private final ConfigManager configManager;

    public VanishListener(final JavaPlugin plugin, final ModerationManager moderationManager,
                          final VanishManager vanishManager, final ConfigManager configManager) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
        this.vanishManager = vanishManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinMessage(final PlayerJoinEvent event) {
        vanishManager.markOnline(event.getPlayer().getUniqueId());
        moderationManager.rememberOnlinePlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        if (isVanished(event.getPlayer())) {
            event.joinMessage(null);
        }
        refreshAfterTrackingChange(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(final PlayerQuitEvent event) {
        vanishManager.markOffline(event.getPlayer().getUniqueId());
        if (isVanished(event.getPlayer())) {
            event.quitMessage(null);
        }
        moderationManager.recordLastLocationAsync(event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(final PlayerKickEvent event) {
        vanishManager.markOffline(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        refreshAfterTrackingChange(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        refreshAfterTrackingChange(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final PlayerRespawnEvent event) {
        refreshAfterTrackingChange(event.getPlayer(), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobTarget(final EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isVanished(player)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isVanished(player)
                && !configManager.getBoolean("moderation.vanish.allow-item-pickup", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isVanished(player)
                && !configManager.getBoolean("moderation.vanish.allow-damage", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageBy(final EntityDamageByEntityEvent event) {
        final Player player = attackingPlayer(event.getDamager());
        if (player != null && isVanished(player)
                && !configManager.getBoolean("moderation.vanish.allow-damage", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (interactionBlocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (interactionBlocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(final InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && interactionBlocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && interactionBlocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && interactionBlocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(final AsyncChatEvent event) {
        if (isVanished(event.getPlayer())
                && !configManager.getBoolean("moderation.vanish.allow-chat", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(final PlayerAdvancementDoneEvent event) {
        if (isVanished(event.getPlayer())) {
            event.message(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(final PlayerDeathEvent event) {
        if (isVanished(event.getPlayer())) {
            event.deathMessage(null);
        }
    }

    private boolean interactionBlocked(final Player player) {
        return isVanished(player)
                && !configManager.getBoolean("moderation.vanish.allow-interaction", false);
    }

    private boolean isVanished(final Player player) {
        return moderationManager.isVanished(player.getUniqueId());
    }

    private void refreshAfterTrackingChange(final Player player, final long delayTicks) {
        PaperEntityTaskSubmission.runDelayed(plugin, player.getScheduler(), () -> {
            vanishManager.refreshViewer(player);
            vanishManager.refreshSubject(player.getUniqueId());
        }, () -> { }, delayTicks);
    }

    private static Player attackingPlayer(final Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
''')

# Event mobs get explicit durable protection markers, separate from territory ownership.
path = "src/main/java/hu/taliann/icesmp/managers/EventSpawnGuard.java"
content = read(path)
content = content.replace("public final class EventSpawnGuard {", "public final class EventSpawnGuard {\n    public static final String EVENT_NO_BURN_KEY = \"event_no_daylight_burn\";\n    public static final String EVENT_NO_ZOMBIFICATION_KEY = \"event_no_zombification\";")
content = content.replace("        if (entity instanceof org.bukkit.entity.AbstractSkeleton skeleton) {", "        entity.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(\"icesmp\", EVENT_NO_BURN_KEY),\n                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);\n        entity.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(\"icesmp\", EVENT_NO_ZOMBIFICATION_KEY),\n                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);\n        if (entity instanceof org.bukkit.entity.AbstractSkeleton skeleton) {")
write(path, content)

# Mob hardening is independent from level scaling and reversible when leaving the territory.
path = "src/main/java/hu/taliann/icesmp/managers/MobScalingManager.java"
content = read(path)
content = content.replace("    private final NamespacedKey mobLevelKey;", "    private final NamespacedKey mobLevelKey;\n    private final NamespacedKey territoryBurnManagedKey;\n    private final NamespacedKey territoryBurnBaselineKey;\n    private final NamespacedKey territoryZombificationManagedKey;\n    private final NamespacedKey territoryZombificationBaselineKey;\n    private final NamespacedKey eventBurnKey;\n    private final NamespacedKey eventZombificationKey;")
content = content.replace("        this.mobLevelKey = new NamespacedKey(plugin, \"mob_level\");", "        this.mobLevelKey = new NamespacedKey(plugin, \"mob_level\");\n        this.territoryBurnManagedKey = new NamespacedKey(plugin, \"territory_no_daylight_burn\");\n        this.territoryBurnBaselineKey = new NamespacedKey(plugin, \"territory_no_daylight_burn_baseline\");\n        this.territoryZombificationManagedKey = new NamespacedKey(plugin, \"territory_no_zombification\");\n        this.territoryZombificationBaselineKey = new NamespacedKey(plugin, \"territory_no_zombification_baseline\");\n        this.eventBurnKey = new NamespacedKey(plugin, EventSpawnGuard.EVENT_NO_BURN_KEY);\n        this.eventZombificationKey = new NamespacedKey(plugin, EventSpawnGuard.EVENT_NO_ZOMBIFICATION_KEY);")
content = content.replace("    public void applyScaling(final LivingEntity entity, final SpawnReason spawnReason) {\n        if (!enabled", "    public void applyScaling(final LivingEntity entity, final SpawnReason spawnReason) {\n        reconcileTerritoryProtection(entity);\n        if (!enabled")
content = content.replace("        applyZoneHardening(entity, zoneSelectors);\n", "")
content = re.sub(r"    /\*\*\n     \* Zóna-alapú mob-keményítés.*?\n    \}\n\n    /\*\*\n     \* Resolves", r'''    /** Reconciles reversible DARK/doom daylight and zombification protection. */
    public void reconcileTerritoryProtection(final LivingEntity entity) {
        if (entity != null) {
            reconcileTerritoryProtection(entity, entity.getLocation());
        }
    }

    /** Location-explicit overload used by teleport/move events before the entity location mutates. */
    public void reconcileTerritoryProtection(final LivingEntity entity, final Location location) {
        if (entity == null || location == null || location.getWorld() == null) {
            return;
        }
        boolean noBurn = false;
        boolean noZombification = false;
        for (final String selector : zoneRuleSelectors(location)) {
            final boolean fallback = "doom-gate".equals(selector);
            noBurn |= configManager.getBoolean("territory.mob-rules." + selector + ".no-daylight-burn", fallback);
            noZombification |= configManager.getBoolean("territory.mob-rules." + selector + ".no-zombification", fallback);
        }
        reconcileBurn(entity, noBurn);
        reconcileZombification(entity, noZombification);
    }

    public boolean hasTerritoryDaylightProtection(final LivingEntity entity) {
        return entity != null && entity.getPersistentDataContainer()
                .has(territoryBurnManagedKey, PersistentDataType.BYTE);
    }

    private void reconcileBurn(final LivingEntity entity, final boolean requested) {
        final Boolean current = shouldBurnInDay(entity);
        if (current == null) {
            return;
        }
        final var pdc = entity.getPersistentDataContainer();
        final boolean managed = pdc.has(territoryBurnManagedKey, PersistentDataType.BYTE);
        if (requested) {
            if (!managed) {
                pdc.set(territoryBurnBaselineKey, PersistentDataType.BYTE, (byte) (current ? 1 : 0));
                pdc.set(territoryBurnManagedKey, PersistentDataType.BYTE, (byte) 1);
            }
            setShouldBurnInDay(entity, false);
            if (entity.getFireTicks() > 0 && locationHasOpenDaylight(entity.getLocation())) {
                entity.setFireTicks(0);
            }
            return;
        }
        if (!managed) {
            return;
        }
        final boolean eventProtected = pdc.has(eventBurnKey, PersistentDataType.BYTE);
        final byte baseline = pdc.getOrDefault(territoryBurnBaselineKey, PersistentDataType.BYTE, (byte) 1);
        setShouldBurnInDay(entity, eventProtected ? false : baseline != 0);
        pdc.remove(territoryBurnManagedKey);
        pdc.remove(territoryBurnBaselineKey);
    }

    private void reconcileZombification(final LivingEntity entity, final boolean requested) {
        final Boolean current = immuneToZombification(entity);
        if (current == null) {
            return;
        }
        final var pdc = entity.getPersistentDataContainer();
        final boolean managed = pdc.has(territoryZombificationManagedKey, PersistentDataType.BYTE);
        if (requested) {
            if (!managed) {
                pdc.set(territoryZombificationBaselineKey, PersistentDataType.BYTE, (byte) (current ? 1 : 0));
                pdc.set(territoryZombificationManagedKey, PersistentDataType.BYTE, (byte) 1);
            }
            setImmuneToZombification(entity, true);
            return;
        }
        if (!managed) {
            return;
        }
        final boolean eventProtected = pdc.has(eventZombificationKey, PersistentDataType.BYTE);
        final byte baseline = pdc.getOrDefault(territoryZombificationBaselineKey,
                PersistentDataType.BYTE, (byte) 0);
        setImmuneToZombification(entity, eventProtected || baseline != 0);
        pdc.remove(territoryZombificationManagedKey);
        pdc.remove(territoryZombificationBaselineKey);
    }

    private static Boolean shouldBurnInDay(final LivingEntity entity) {
        if (entity instanceof org.bukkit.entity.AbstractSkeleton value) return value.shouldBurnInDay();
        if (entity instanceof org.bukkit.entity.Zombie value) return value.shouldBurnInDay();
        if (entity instanceof org.bukkit.entity.Phantom value) return value.shouldBurnInDay();
        return null;
    }

    private static void setShouldBurnInDay(final LivingEntity entity, final boolean value) {
        if (entity instanceof org.bukkit.entity.AbstractSkeleton skeleton) skeleton.setShouldBurnInDay(value);
        else if (entity instanceof org.bukkit.entity.Zombie zombie) zombie.setShouldBurnInDay(value);
        else if (entity instanceof org.bukkit.entity.Phantom phantom) phantom.setShouldBurnInDay(value);
    }

    private static Boolean immuneToZombification(final LivingEntity entity) {
        if (entity instanceof org.bukkit.entity.PiglinAbstract value) return value.isImmuneToZombification();
        if (entity instanceof org.bukkit.entity.Hoglin value) return value.isImmuneToZombification();
        return null;
    }

    private static void setImmuneToZombification(final LivingEntity entity, final boolean value) {
        if (entity instanceof org.bukkit.entity.PiglinAbstract piglin) piglin.setImmuneToZombification(value);
        else if (entity instanceof org.bukkit.entity.Hoglin hoglin) hoglin.setImmuneToZombification(value);
    }

    private static boolean locationHasOpenDaylight(final Location location) {
        return location.getWorld() != null && location.getWorld().isDayTime()
                && location.getBlock().getLightFromSky() >= 14;
    }

    /**
     * Resolves''', content, flags=re.S)
write(path, content)

write("src/main/java/hu/taliann/icesmp/listeners/MobScalingListener.java", r'''package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.MobScalingManager;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Scaling plus reversible territory-mob lifecycle reconciliation. */
public final class MobScalingListener implements Listener {
    private final MobScalingManager mobScalingManager;

    public MobScalingListener(final MobScalingManager mobScalingManager) {
        this.mobScalingManager = mobScalingManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        mobScalingManager.applyScaling(event.getEntity(), event.getSpawnReason());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(final EntitiesLoadEvent event) {
        for (final org.bukkit.entity.Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) {
                mobScalingManager.reconcileTerritoryProtection(living);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(final EntityMoveEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || sameColumn(event.getFrom(), event.getTo())) {
            return;
        }
        mobScalingManager.reconcileTerritoryProtection(living, event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final EntityTeleportEvent event) {
        if (event.getEntity() instanceof LivingEntity living && event.getTo() != null) {
            mobScalingManager.reconcileTerritoryProtection(living, event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(final EntityCombustEvent event) {
        // Bukkit's base event is the daylight ignition path; block/entity combustion
        // subclasses must remain vanilla so this never becomes global fire immunity.
        if (event.getClass() == EntityCombustEvent.class
                && event.getEntity() instanceof LivingEntity living
                && mobScalingManager.hasTerritoryDaylightProtection(living)) {
            event.setCancelled(true);
        }
    }

    private static boolean sameColumn(final Location from, final Location to) {
        return from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ();
    }
}
''')

# Config switches used by the new explicit vanish capability.
path = "src/main/resources/config/moderation.yml"
content = read(path)
needle = "    allow-interaction: false\n"
if needle not in content:
    raise RuntimeError("moderation vanish insertion point missing")
content = content.replace(needle, needle + "    # Vanished chat would reveal the moderator; opt in explicitly when needed.\n    allow-chat: false\n", 1)
write(path, content)

# Runtime-hardening regression suite exercises pure geometry and source-level lifecycle invariants.
write("src/regression/java/hu/taliann/icesmp/runtime/RuntimeHardeningRegressionSuite.java", r'''package hu.taliann.icesmp.runtime;

import hu.taliann.icesmp.data.ClaimFootprint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RuntimeHardeningRegressionSuite {
    private RuntimeHardeningRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        claimGeometry();
        lifecycleSourceContracts();
        System.out.println("Runtime hardening regression suite passed.");
    }

    private static void claimGeometry() {
        final int[][] corners = {{1, 3, 8, 11}, {8, 11, 1, 3}, {1, 11, 8, 3}, {8, 3, 1, 11}};
        for (final int[] value : corners) {
            final ClaimFootprint footprint = ClaimFootprint.between(value[0], value[1], value[2], value[3]);
            check(footprint.equals(new ClaimFootprint(1, 3, 8, 11)), "all corner orders normalize");
            check(footprint.contains(1, 3) && footprint.contains(8, 11), "inclusive corners");
        }
        final List<ClaimFootprint> cases = List.of(
                ClaimFootprint.between(4, 9, 4, 9),
                ClaimFootprint.between(-20, -1, -20, 15),
                ClaimFootprint.between(-8, -8, 8, 8),
                ClaimFootprint.between(0, 0, 15, 15),
                ClaimFootprint.between(15, 15, 32, 47));
        for (final ClaimFootprint footprint : cases) {
            final List<ClaimFootprint.BoundaryPoint> points = footprint.perimeter(2);
            check(new HashSet<>(points).size() == points.size(), "perimeter has no duplicate corners");
            final Set<ClaimFootprint.BoundaryPoint> expected = Set.of(
                    new ClaimFootprint.BoundaryPoint(footprint.minX(), footprint.minZ()),
                    new ClaimFootprint.BoundaryPoint(footprint.outerMaxX(), footprint.minZ()),
                    new ClaimFootprint.BoundaryPoint(footprint.minX(), footprint.outerMaxZ()),
                    new ClaimFootprint.BoundaryPoint(footprint.outerMaxX(), footprint.outerMaxZ()));
            check(points.containsAll(expected), "all outside-edge corners rendered");
        }
        check(ClaimFootprint.between(-1, -1, 1, 1).contains(0, 0), "zero-crossing membership");
        check(!ClaimFootprint.between(0, 0, 15, 15).contains(16, 15), "outside edge excluded");
        check(ClaimFootprint.between(0, 0, 15, 15).overlaps(ClaimFootprint.between(15, 0, 31, 15)),
                "shared block column overlaps");
        check(!ClaimFootprint.between(0, 0, 15, 15).overlaps(ClaimFootprint.between(16, 0, 31, 15)),
                "adjacent claims do not overlap");
    }

    private static void lifecycleSourceContracts() throws Exception {
        final String claim = source("src/main/java/hu/taliann/icesmp/managers/ClaimManager.java");
        check(claim.contains("claim.contains(worldName, location.getBlockX(), location.getBlockZ())"),
                "claim lookup is Y-independent");
        check(!claim.contains("drawBoxOutline"), "claim renderer has no 3D box path");
        check(claim.contains("borderTasks.remove(playerId)"), "border preview owns cleanup task");

        final String vanish = source("src/main/java/hu/taliann/icesmp/managers/VanishManager.java");
        check(vanish.contains("viewer.hidePlayer(plugin, subject);"), "vanish reasserts hidePlayer");
        check(!vanish.contains("setInvulnerable"), "vanish never leaks Bukkit invulnerability state");
        final String vanishListener = source("src/main/java/hu/taliann/icesmp/listeners/VanishListener.java");
        check(vanishListener.contains("PlayerTeleportEvent") && vanishListener.contains("PlayerRespawnEvent"),
                "vanish retracking lifecycle covered");

        final String mobs = source("src/main/java/hu/taliann/icesmp/managers/MobScalingManager.java");
        check(mobs.indexOf("reconcileTerritoryProtection(entity);") < mobs.indexOf("if (!enabled"),
                "DARK protection runs before scaling gates");
        check(mobs.contains("territoryBurnBaselineKey") && mobs.contains("pdc.remove(territoryBurnManagedKey)"),
                "DARK protection restores baseline on exit");
        final String mobListener = source("src/main/java/hu/taliann/icesmp/listeners/MobScalingListener.java");
        check(mobListener.contains("EntitiesLoadEvent") && mobListener.contains("EntityMoveEvent")
                        && mobListener.contains("EntityTeleportEvent"),
                "DARK protection covers load/move/teleport");
        check(mobListener.contains("event.getClass() == EntityCombustEvent.class"),
                "only daylight combustion is cancelled");
    }

    private static String source(final String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
''')

path = "build.gradle.kts"
content = read(path)
anchor = '''val factionPassiveRegressionTest by tasks.registering(JavaExec::class) {'''
task = '''val runtimeHardeningRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs 2D claim, vanish retracking and DARK mob lifecycle regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.runtime.RuntimeHardeningRegressionSuite")
}

'''
if anchor not in content:
    raise RuntimeError("build task anchor missing")
content = content.replace(anchor, task + anchor, 1)
content = content.replace("hudRegressionTest, pauseMenuDialogRegressionTest, runtimeBugfixRegressionTest,", "hudRegressionTest, pauseMenuDialogRegressionTest, runtimeBugfixRegressionTest, runtimeHardeningRegressionTest,")
write(path, content)

print("stage1 runtime patch applied")
