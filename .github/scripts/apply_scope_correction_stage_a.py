#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


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
        raise RuntimeError(f"{path}: expected one occurrence, got {count}: {old[:120]!r}")
    write(path, content.replace(old, new, 1))


replace_once(
    "src/main/java/hu/taliann/icesmp/core/Permissions.java",
    '''        moderationNodes.put(MODERATION_VANISH, "Vanish állapot kezelése");
        moderationNodes.put(MODERATION_VANISH_SEE, "Vanish állapotú adminok megtekintése");
        moderationNodes.put(MODERATION_OFFLINE_TP, "Teleport az utolsó kijelentkezési helyre");''',
    '''        moderationNodes.put(MODERATION_VANISH, "Vanish állapot kezelése");
        moderationNodes.put(MODERATION_OFFLINE_TP, "Teleport az utolsó kijelentkezési helyre");'''
)
replace_once(
    "src/main/java/hu/taliann/icesmp/core/Permissions.java",
    '''        registerNode(pm, new Permission(MODERATION,
                "IceSMP natív moderációs jogosultságcsomag", PermissionDefault.OP, moderationChildren));
        allChildren.put(MODERATION, Boolean.TRUE);''',
    '''        // Deliberately NOT inherited by OP, the moderation bundle or icesmp.admin.all.
        // Otherwise every operator testing /vanish can still see the subject and the
        // feature appears completely broken. Grant this node only to explicit observers.
        registerNode(pm, new Permission(MODERATION_VANISH_SEE,
                "Vanish állapotú adminok megtekintése", PermissionDefault.FALSE));
        registerNode(pm, new Permission(MODERATION,
                "IceSMP natív moderációs jogosultságcsomag", PermissionDefault.OP, moderationChildren));
        allChildren.put(MODERATION, Boolean.TRUE);'''
)

write("src/main/java/hu/taliann/icesmp/managers/VanishManager.java", r'''package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies persisted vanish to both entity tracking and the per-viewer player list.
 *
 * <p>Both ledgers are ownership ledgers: IceSMP restores only pairs it changed. Entity
 * visibility remains plugin-scoped through hide/showPlayer; tab-list visibility is
 * tracked separately because Paper's list/unlist API is viewer-scoped rather than
 * plugin-scoped.</p>
 */
public final class VanishManager implements PlayerStateCleanup {

    private static final long TRACKING_REASSERT_TICKS = 20L;

    private final JavaPlugin plugin;
    private final ModerationManager moderationManager;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<UUID, Set<UUID>> hiddenByViewer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<UUID>> unlistedByViewer = new ConcurrentHashMap<>();
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

    public VanishManager(final JavaPlugin plugin, final ModerationManager moderationManager,
                         final ConfigManager configManager) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
        this.configManager = configManager;
    }

    public boolean isVanished(final UUID playerId) {
        return moderationManager.isVanished(playerId);
    }

    public boolean excludedFromOnlineCount() {
        return configManager.getBoolean("moderation.vanish.exclude-from-online-count", true);
    }

    public int visibleOnlineCount() {
        return excludedFromOnlineCount() ? onlineCountExcludingVanished() : onlineCount();
    }

    public int onlineCount() {
        return onlinePlayers.size();
    }

    public int onlineCountExcludingVanished() {
        return (int) onlinePlayers.stream().filter(playerId -> !isVanished(playerId)).count();
    }

    public void markOnline(final UUID playerId) {
        onlinePlayers.add(playerId);
    }

    public void markOffline(final UUID playerId) {
        onlinePlayers.remove(playerId);
    }

    public void refreshViewer(final Player viewer) {
        PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> applyViewer(viewer), () -> { });
    }

    public void refreshAll() {
        reconcileAllOnce();
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> reconcileAllOnce(), TRACKING_REASSERT_TICKS);
    }

    private void reconcileAllOnce() {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            final Set<UUID> current = ConcurrentHashMap.newKeySet();
            for (final Player viewer : Bukkit.getOnlinePlayers()) {
                current.add(viewer.getUniqueId());
                refreshViewer(viewer);
            }
            onlinePlayers.retainAll(current);
            onlinePlayers.addAll(current);
        });
    }

    public void refreshSubject(final UUID subjectId) {
        refreshSubjectOnce(subjectId);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin,
                task -> refreshSubjectOnce(subjectId), TRACKING_REASSERT_TICKS);
    }

    private void refreshSubjectOnce(final UUID subjectId) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            for (final Player viewer : Bukkit.getOnlinePlayers()) {
                PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(),
                        () -> applySubject(viewer, subjectId), () -> { });
            }
        });
    }

    private void applyViewer(final Player viewer) {
        if (!viewer.isOnline()) {
            return;
        }
        for (final UUID subjectId : Set.copyOf(onlinePlayers)) {
            if (!subjectId.equals(viewer.getUniqueId())) {
                applySubject(viewer, subjectId);
            }
        }
        pruneOffline(hiddenByViewer.computeIfAbsent(viewer.getUniqueId(), ignored ->
                ConcurrentHashMap.newKeySet()));
        pruneOffline(unlistedByViewer.computeIfAbsent(viewer.getUniqueId(), ignored ->
                ConcurrentHashMap.newKeySet()));
    }

    private static void pruneOffline(final Set<UUID> subjects) {
        subjects.removeIf(subjectId -> Bukkit.getPlayer(subjectId) == null);
    }

    private void applySubject(final Player viewer, final UUID subjectId) {
        if (!viewer.isOnline() || viewer.getUniqueId().equals(subjectId)) {
            return;
        }
        final Player subject = Bukkit.getPlayer(subjectId);
        final Set<UUID> hidden = hiddenByViewer.computeIfAbsent(viewer.getUniqueId(), ignored ->
                ConcurrentHashMap.newKeySet());
        final Set<UUID> unlisted = unlistedByViewer.computeIfAbsent(viewer.getUniqueId(), ignored ->
                ConcurrentHashMap.newKeySet());
        final boolean shouldHide = subject != null && isVanished(subjectId)
                && !viewer.hasPermission(Permissions.MODERATION_VANISH_SEE);

        if (shouldHide) {
            hidden.add(subjectId);
            viewer.hidePlayer(plugin, subject);
            if (viewer.isListed(subject)) {
                viewer.unlistPlayer(subject);
                unlisted.add(subjectId);
            } else if (unlisted.contains(subjectId)) {
                viewer.unlistPlayer(subject);
            }
            return;
        }

        if (subject != null && hidden.remove(subjectId)) {
            viewer.showPlayer(plugin, subject);
        }
        if (subject != null && unlisted.remove(subjectId)
                && viewer.canSee(subject) && !viewer.isListed(subject)) {
            viewer.listPlayer(subject);
        }
    }

    public void shutdown() {
        for (final Player viewer : Bukkit.getOnlinePlayers()) {
            final Set<UUID> hidden = hiddenByViewer.remove(viewer.getUniqueId());
            final Set<UUID> unlisted = unlistedByViewer.remove(viewer.getUniqueId());
            if ((hidden == null || hidden.isEmpty()) && (unlisted == null || unlisted.isEmpty())) {
                continue;
            }
            final Set<UUID> subjects = new LinkedHashSet<>();
            if (hidden != null) subjects.addAll(hidden);
            if (unlisted != null) subjects.addAll(unlisted);
            final Set<UUID> hiddenSnapshot = hidden == null ? Set.of() : Set.copyOf(hidden);
            final Set<UUID> unlistedSnapshot = unlisted == null ? Set.of() : Set.copyOf(unlisted);
            PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(), () -> {
                for (final UUID subjectId : subjects) {
                    final Player subject = Bukkit.getPlayer(subjectId);
                    if (subject == null) continue;
                    if (hiddenSnapshot.contains(subjectId)) {
                        viewer.showPlayer(plugin, subject);
                    }
                    if (unlistedSnapshot.contains(subjectId)
                            && viewer.canSee(subject) && !viewer.isListed(subject)) {
                        viewer.listPlayer(subject);
                    }
                }
            }, () -> { });
        }
        hiddenByViewer.clear();
        unlistedByViewer.clear();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        onlinePlayers.remove(playerId);
        hiddenByViewer.remove(playerId);
        unlistedByViewer.remove(playerId);
        for (final Set<UUID> hidden : hiddenByViewer.values()) {
            hidden.remove(playerId);
        }
        for (final Set<UUID> unlisted : unlistedByViewer.values()) {
            unlisted.remove(playerId);
        }
    }
}
''')

replace_once(
    "src/main/java/hu/taliann/icesmp/utils/DisplayFxUtil.java",
    '''import org.bukkit.Location;
import org.bukkit.block.data.BlockData;''',
    '''import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;'''
)
replace_once(
    "src/main/java/hu/taliann/icesmp/utils/DisplayFxUtil.java",
    '''    private static void scheduleDespawn(final Plugin plugin, final Display display, final int ticks) {''',
    '''    /**
     * Spawns one vertical wall column from the real terrain surface of the sampled
     * X/Z column. Region ownership is acquired for every boundary column.
     */
    public static void terrainWallColumn(final Plugin plugin, final World world,
                                         final int sampleX, final int sampleZ,
                                         final double displayX, final double displayZ,
                                         final float sizeX, final float sizeY, final float sizeZ,
                                         final BlockData block, final Color glow,
                                         final int despawnTicks, final Player viewer) {
        if (plugin == null || world == null || block == null || sizeY <= 0.0F) return;
        final Location owner = new Location(world, sampleX + 0.5D, world.getMinHeight(), sampleZ + 0.5D);
        plugin.getServer().getRegionScheduler().run(plugin, owner, task -> {
            if (!world.isChunkLoaded(sampleX >> 4, sampleZ >> 4)) return;
            final int floorY = world.getHighestBlockYAt(
                    sampleX, sampleZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            final Location corner = new Location(world, displayX, floorY + 1.0D, displayZ);
            wallSegment(plugin, corner, sizeX, sizeY, sizeZ,
                    block, glow, despawnTicks, viewer);
        });
    }

    private static void scheduleDespawn(final Plugin plugin, final Display display, final int ticks) {'''
)

claim_path = "src/main/java/hu/taliann/icesmp/managers/ClaimManager.java"
claim = read(claim_path)
pattern = re.compile(
    r'''    /\*\*
     \* DisplayFx-pilot:.*?
    private void showDisplayWalls\(final Player player, final int seconds\) \{.*?
    \}

    /\*\* A fényfal blokk-anyaga''',
    re.S,
)
replacement = r'''    /**
     * Terrain-following BlockDisplay wall. Every boundary block gets its own
     * region-owned vertical segment, so the complete perimeter follows terrain.
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
        final float height = Math.max(1, configManager.getInt("display-fx.claim-wall.height", 3));
        final int ticks = seconds * 20;
        final org.bukkit.block.data.BlockData block = wallBlockData();
        for (final Claim claim : nearby) {
            final org.bukkit.Color glow = claim.isTrusted(player.getUniqueId())
                    ? org.bukkit.Color.fromRGB(0x3BE24A) : org.bukkit.Color.fromRGB(0xE23B3B);
            for (int x = claim.minX; x <= claim.maxX; x++) {
                hu.taliann.icesmp.utils.DisplayFxUtil.terrainWallColumn(plugin, world,
                        x, claim.minZ, x, claim.minZ, 1.0F, height, 0.08F,
                        block, glow, ticks, player);
                hu.taliann.icesmp.utils.DisplayFxUtil.terrainWallColumn(plugin, world,
                        x, claim.maxZ, x, claim.maxZ + 1.0D, 1.0F, height, 0.08F,
                        block, glow, ticks, player);
            }
            for (int z = claim.minZ; z <= claim.maxZ; z++) {
                hu.taliann.icesmp.utils.DisplayFxUtil.terrainWallColumn(plugin, world,
                        claim.minX, z, claim.minX, z, 0.08F, height, 1.0F,
                        block, glow, ticks, player);
                hu.taliann.icesmp.utils.DisplayFxUtil.terrainWallColumn(plugin, world,
                        claim.maxX, z, claim.maxX + 1.0D, z, 0.08F, height, 1.0F,
                        block, glow, ticks, player);
            }
        }
    }

    /** A fényfal blokk-anyaga'''
claim, count = pattern.subn(replacement, claim, count=1)
if count != 1:
    raise RuntimeError(f"{claim_path}: showDisplayWalls replacement count={count}")
write(claim_path, claim)

replace_once(
    "src/main/java/hu/taliann/icesmp/managers/EventSpawnGuard.java",
    '''import org.bukkit.GameMode;
import org.bukkit.Location;''',
    '''import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;'''
)
guard_path = "src/main/java/hu/taliann/icesmp/managers/EventSpawnGuard.java"
guard = read(guard_path)
surface_pattern = re.compile(
    r'''    /\*\* Must run on the region thread that owns x/z\. \*/
    public boolean isUnsafeSurface\(final String eventKey, final World world, final int x, final int z\) \{.*?
    \}

    /\*\*
     \* Finite Folia-safe search''',
    re.S,
)
surface_replacement = r'''    /**
     * Resolves stable footing on the region thread owning x/z. Leaves, gravity
     * blocks, liquids and damaging floors are rejected; tall mobs get three
     * passable body blocks.
     */
    public Location resolveSafeStandingLocation(final String eventKey, final World world,
                                                final int x, final int z) {
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        final int floorY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (floorY <= world.getMinHeight() || floorY + 3 >= world.getMaxHeight()) {
            return null;
        }
        final Block floor = world.getBlockAt(x, floorY, z);
        final Block feet = world.getBlockAt(x, floorY + 1, z);
        final Block head = world.getBlockAt(x, floorY + 2, z);
        final Block upperHead = world.getBlockAt(x, floorY + 3, z);
        if (!stableFloor(floor)
                || !clearBody(feet) || !clearBody(head) || !clearBody(upperHead)) {
            return null;
        }
        if (rule(eventKey, "water")
                && (floor.isLiquid() || feet.isLiquid() || head.isLiquid())) {
            return null;
        }
        return new Location(world, x + 0.5D, floorY + 1.0D, z + 0.5D);
    }

    public boolean isUnsafeSurface(final String eventKey, final World world, final int x, final int z) {
        return resolveSafeStandingLocation(eventKey, world, x, z) == null;
    }

    private static boolean stableFloor(final Block floor) {
        final Material material = floor.getType();
        return material.isSolid()
                && material.isOccluding()
                && !material.hasGravity()
                && material != Material.POWDER_SNOW
                && material != Material.MAGMA_BLOCK
                && material != Material.CAMPFIRE
                && material != Material.SOUL_CAMPFIRE
                && material != Material.CACTUS
                && material != Material.SWEET_BERRY_BUSH
                && material != Material.WITHER_ROSE;
    }

    private static boolean clearBody(final Block block) {
        return block.isPassable() && !block.isLiquid()
                && block.getType() != Material.FIRE
                && block.getType() != Material.SOUL_FIRE;
    }

    /**
     * Finite Folia-safe search'''
guard, count = surface_pattern.subn(surface_replacement, guard, count=1)
if count != 1:
    raise RuntimeError(f"{guard_path}: safe surface replacement count={count}")
old_candidate = '''            final int x = column.getBlockX();
            final int z = column.getBlockZ();
            final int y = world.getHighestBlockYAt(x, z) + 1;
            final Location candidate = new Location(world, x + 0.5D, y, z + 0.5D);
            if (blockReason(eventKey, candidate) != BlockReason.NONE
                    || isUnsafeSurface(eventKey, world, x, z)
                    || !reserve(eventKey, candidate)) {'''
new_candidate = '''            final int x = column.getBlockX();
            final int z = column.getBlockZ();
            final Location candidate = resolveSafeStandingLocation(eventKey, world, x, z);
            if (candidate == null
                    || blockReason(eventKey, candidate) != BlockReason.NONE
                    || !reserve(eventKey, candidate)) {'''
if guard.count(old_candidate) != 1:
    raise RuntimeError(f"{guard_path}: candidate replacement count={guard.count(old_candidate)}")
guard = guard.replace(old_candidate, new_candidate, 1)
write(guard_path, guard)

write("src/main/java/hu/taliann/icesmp/managers/DarkUndeadAmbienceManager.java", r'''package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Maintains ambient undead inside DARK territories. Candidates are retried
 * finitely and must have stable solid footing; there is no airborne fallback.
 */
public final class DarkUndeadAmbienceManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final MobScalingManager mobScalingManager;
    private final EventSpawnGuard spawnGuard;
    private final org.bukkit.NamespacedKey markKey;
    private final Map<UUID, Long> population = new ConcurrentHashMap<>();
    private volatile long nextSpawnAt;

    public DarkUndeadAmbienceManager(final JavaPlugin plugin, final ConfigManager configManager,
                                     final TerritoryManager territoryManager,
                                     final MobScalingManager mobScalingManager,
                                     final EventSpawnGuard spawnGuard) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.mobScalingManager = mobScalingManager;
        this.spawnGuard = spawnGuard;
        this.markKey = new org.bukkit.NamespacedKey(plugin, "dark_undead");
    }

    public boolean isMarked(final org.bukkit.entity.Entity entity) {
        return entity.getPersistentDataContainer().has(markKey,
                org.bukkit.persistence.PersistentDataType.BYTE);
    }

    public void onDeath(final UUID entityId) {
        population.remove(entityId);
    }

    public void tick() {
        if (!configManager.getBoolean("dark-undead.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextSpawnAt) {
            return;
        }
        nextSpawnAt = now + Math.max(5,
                configManager.getInt("dark-undead.spawn-interval-seconds", 30)) * 1000L;
        population.entrySet().removeIf(entry -> entry.getValue() < now);

        final List<Territory> targets = targetTerritories();
        if (targets.isEmpty()) {
            return;
        }
        final int maxPopulation = Math.max(1,
                configManager.getInt("dark-undead.max-population", 24));
        final int batch = Math.min(
                Math.max(1, configManager.getInt("dark-undead.spawn-batch", 4)),
                maxPopulation - population.size());
        if (batch <= 0) {
            return;
        }
        final List<String> configuredTypes = configManager.getStringList("dark-undead.types");
        final List<String> pool = configuredTypes.isEmpty()
                ? List.of("ZOMBIE", "SKELETON", "HUSK", "STRAY", "WITHER_SKELETON")
                : List.copyOf(configuredTypes);
        final int minLevel = Math.max(1, configManager.getInt("dark-undead.min-level", 4));
        final int maxLevel = Math.max(minLevel, configManager.getInt("dark-undead.max-level", 7));
        final long lifespanMillis = Math.max(60,
                configManager.getInt("dark-undead.lifespan-seconds", 600)) * 1000L;
        final int attempts = Math.max(1,
                configManager.getInt("dark-undead.spawn-attempts-per-mob", 12));

        for (int index = 0; index < batch; index++) {
            final Territory territory = targets.get(
                    ThreadLocalRandom.current().nextInt(targets.size()));
            trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis, attempts);
        }
    }

    private void trySpawn(final Territory territory, final List<String> pool,
                          final int minLevel, final int maxLevel,
                          final long lifespanMillis, final int remainingAttempts) {
        if (remainingAttempts <= 0) {
            return;
        }
        final World world = Bukkit.getWorld(territory.world());
        if (world == null) {
            return;
        }
        final int[] column = randomColumnInside(territory);
        if (column == null) {
            trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis,
                    remainingAttempts - 1);
            return;
        }
        final int x = column[0];
        final int z = column[1];
        final Location owner = new Location(world, x + 0.5D, world.getMinHeight(), z + 0.5D);
        plugin.getServer().getRegionScheduler().run(plugin, owner, task -> {
            final Location target = spawnGuard.resolveSafeStandingLocation(
                    "dark-undead", world, x, z);
            if (target == null) {
                trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis,
                        remainingAttempts - 1);
                return;
            }
            final Territory actual = territoryManager.getTerritoryAt(target);
            if (actual == null || !actual.id().equals(territory.id())
                    || spawnGuard.isBlocked("dark-undead", target)) {
                trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis,
                        remainingAttempts - 1);
                return;
            }

            final EntityType type = randomMobType(pool);
            if (type == null) {
                trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis,
                        remainingAttempts - 1);
                return;
            }
            final org.bukkit.entity.Entity spawned = world.spawnEntity(target, type);
            if (!(spawned instanceof Mob mob)) {
                spawned.remove();
                trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis,
                        remainingAttempts - 1);
                return;
            }
            EventSpawnGuard.prepare(mob);
            mob.getPersistentDataContainer().set(markKey,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            mob.setPersistent(false);
            mob.setFallDistance(0.0F);
            mob.setVelocity(new org.bukkit.util.Vector(0.0D, 0.0D, 0.0D));
            if (mobScalingManager != null) {
                mobScalingManager.forceLevel(mob,
                        ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1));
            }
            population.put(mob.getUniqueId(), System.currentTimeMillis() + lifespanMillis);
            mob.getScheduler().runDelayed(plugin, lifespanTask -> {
                population.remove(mob.getUniqueId());
                mob.remove();
            }, null, lifespanMillis / 50L);
        });
    }

    private static int[] randomColumnInside(final Territory territory) {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            final double angle = random.nextDouble(2.0D * Math.PI);
            final double distance = Math.sqrt(random.nextDouble())
                    * Math.max(1.0D, territory.radius() - 1.0D);
            final int x = territory.x() + (int) Math.round(Math.cos(angle) * distance);
            final int z = territory.z() + (int) Math.round(Math.sin(angle) * distance);
            if (territory.contains(territory.world(), x + 0.5D, z + 0.5D)) {
                return new int[] {x, z};
            }
        }
        return null;
    }

    private static EntityType randomMobType(final List<String> pool) {
        if (pool.isEmpty()) {
            return null;
        }
        try {
            return EntityType.valueOf(pool.get(ThreadLocalRandom.current().nextInt(pool.size()))
                    .toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            return null;
        }
    }

    private List<Territory> targetTerritories() {
        final String override = configManager.getString("dark-undead.territory-id", "");
        final boolean all = "all".equalsIgnoreCase(
                configManager.getString("dark-undead.scope", "capital"));
        final List<Territory> out = new java.util.ArrayList<>();
        for (final Territory territory : territoryManager.all()) {
            if (!override.isBlank()) {
                if (territory.id().equalsIgnoreCase(override)) {
                    out.add(territory);
                }
                continue;
            }
            if (territory.faction() == FactionType.DARK
                    && (all || territory.type() == TerritoryType.CAPITAL)) {
                out.add(territory);
            }
        }
        return out;
    }
}
''')

replace_once(
    "src/main/resources/config/world.yml",
    '''  spawn-batch: 4
  spawn-interval-seconds: 30
  min-level: 4''',
    '''  spawn-batch: 4
  spawn-interval-seconds: 30
  # Minden mob ennyi külön oszlopot próbál; nincs levegőben/közeli fallback.
  spawn-attempts-per-mob: 12
  min-level: 4'''
)

suite_path = "src/regression/java/hu/taliann/icesmp/runtime/RuntimeHardeningRegressionSuite.java"
suite = read(suite_path)
old_vanish = '''        final String vanish = source("src/main/java/hu/taliann/icesmp/managers/VanishManager.java");
        check(vanish.contains("viewer.hidePlayer(plugin, subject);"), "vanish reasserts hidePlayer");
        check(!vanish.contains("setInvulnerable"), "vanish never leaks Bukkit invulnerability state");
        final String vanishListener = source("src/main/java/hu/taliann/icesmp/listeners/VanishListener.java");
        check(vanishListener.contains("PlayerTeleportEvent") && vanishListener.contains("PlayerRespawnEvent"),
                "vanish retracking lifecycle covered");'''
new_vanish = '''        final String vanish = source("src/main/java/hu/taliann/icesmp/managers/VanishManager.java");
        check(vanish.contains("viewer.hidePlayer(plugin, subject);"), "vanish removes the in-world entity");
        check(vanish.contains("viewer.unlistPlayer(subject);")
                        && vanish.contains("viewer.listPlayer(subject);"),
                "vanish owns per-viewer tab-list removal and restoration");
        check(vanish.contains("TRACKING_REASSERT_TICKS"), "vanish is reasserted after tracking rebuilds");
        check(!vanish.contains("setInvulnerable"), "vanish never leaks Bukkit invulnerability state");
        final String permissions = source("src/main/java/hu/taliann/icesmp/core/Permissions.java");
        check(permissions.contains("MODERATION_VANISH_SEE,\\n                \\"Vanish állapotú adminok megtekintése\\", PermissionDefault.FALSE"),
                "vanish-see is explicit and not default OP");
        check(!permissions.contains("moderationNodes.put(MODERATION_VANISH_SEE"),
                "moderation bundle cannot silently bypass vanish");
        final String vanishListener = source("src/main/java/hu/taliann/icesmp/listeners/VanishListener.java");
        check(vanishListener.contains("PlayerTeleportEvent") && vanishListener.contains("PlayerRespawnEvent"),
                "vanish retracking lifecycle covered");'''
if suite.count(old_vanish) != 1:
    raise RuntimeError(f"{suite_path}: vanish test replacement count={suite.count(old_vanish)}")
suite = suite.replace(old_vanish, new_vanish, 1)
old_mobs = '''        check(mobListener.contains("event.getClass() == EntityCombustEvent.class"),
                "only daylight combustion is cancelled");'''
new_mobs = '''        check(mobListener.contains("event.getClass() == EntityCombustEvent.class"),
                "only daylight combustion is cancelled");

        final String display = source("src/main/java/hu/taliann/icesmp/utils/DisplayFxUtil.java");
        check(display.contains("HeightMap.MOTION_BLOCKING_NO_LEAVES")
                        && display.contains("terrainWallColumn"),
                "BlockDisplay wall follows each owned terrain column");
        check(!claim.contains("baseY = location.getY()"),
                "claim display wall is never anchored to viewer Y");
        final String guard = source("src/main/java/hu/taliann/icesmp/managers/EventSpawnGuard.java");
        check(guard.contains("resolveSafeStandingLocation")
                        && guard.contains("material.isOccluding()")
                        && guard.contains("!material.hasGravity()"),
                "event and DARK spawns require stable solid footing");
        final String dark = source("src/main/java/hu/taliann/icesmp/managers/DarkUndeadAmbienceManager.java");
        check(dark.contains("dark-undead.spawn-attempts-per-mob")
                        && dark.contains("spawnGuard.resolveSafeStandingLocation")
                        && dark.contains("territory.contains(territory.world()"),
                "DARK undead use finite exact-territory safe-surface retries");'''
if suite.count(old_mobs) != 1:
    raise RuntimeError(f"{suite_path}: mob test replacement count={suite.count(old_mobs)}")
suite = suite.replace(old_mobs, new_mobs, 1)
write(suite_path, suite)

audit_path = "docs/RUNTIME_HARDENING_AUDIT.md"
audit = read(audit_path)
audit = audit.replace(
    '''- **Vanish:** the visibility ledger prevented `hidePlayer` from being reissued after client retracking. Hide is now idempotently
  reasserted after join, teleport, world change and respawn. Invulnerability is not stored on the Player; damage immunity is an
  explicit event capability and is removed automatically when vanish is disabled.''',
    '''- **Vanish:** `icesmp.moderation.vanish.see` was inherited by every OP/moderation super-node, so the usual admin tester was
  explicitly exempt from hiding and the feature appeared to do nothing. The observer permission is now explicit-only. Vanish
  removes both the tracked entity (`hidePlayer`) and the per-viewer player-list entry (`unlistPlayer`), reasserts both after
  tracking rebuilds, and restores only IceSMP-owned visibility pairs.'''
)
audit = audit.replace(
    '''- **Claim geometry:** membership and rendering independently used stored Y bounds and drew a 3D box. Both now consume one
  normalized, inclusive X–Z `ClaimFootprint`; legacy Y fields are persistence-only. Preview tasks are single-owner and cleaned on
  replacement/logout.''',
    '''- **Claim wall:** the BlockDisplay renderer used four stretched slabs anchored to the viewer's Y, so terrain changes made the
  glass float above the area or disappear below it. Every boundary column is now region-owned and resolved from the actual
  `MOTION_BLOCKING_NO_LEAVES` surface, producing the configured wall height along the full perimeter. Polygon claim selection is
  implemented in the following correction stage.'''
)
audit = audit.replace(
    '''- **World events:** invasions spawned at the selected player and bosses used a hard-coded 24–40 block ring. A shared bounded
  guard now enforces all relevant players, world spawn, world border, territory/claim/region, loaded chunk, safe surface and
  concurrent-event reservations. No valid candidate means a logged, controlled abort — never a close fallback.''',
    '''- **DARK undead footing:** ambient DARK spawns used a single `getHighestBlockYAt()+1` candidate without a stable-floor contract.
  They now retry finitely inside the exact territory shape and require an occluding, non-gravity, non-hazard floor plus three
  passable body blocks through the shared spawn guard. No valid column means no spawn; there is no airborne fallback.
- **World events:** the same stable standing-location resolver now backs bounded event searches in addition to player, spawn,
  border, territory/claim/region, loaded-chunk and reservation rules.'''
)
write(audit_path, audit)

print("scope correction stage A applied")
