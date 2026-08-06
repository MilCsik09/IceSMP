package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Meteor impact world event. Landing uses the shared distant spawn search, so the
 * crater cannot visibly appear beside a player. Every overwritten ordinary block
 * is persisted before mutation and restored per owning chunk on expiry, graceful
 * disable, or the next startup after an interrupted shutdown.
 */
public final class MeteorEventManager {

    private static final Material[] LINING = {
            Material.BLACKSTONE, Material.BASALT, Material.COBBLED_DEEPSLATE, Material.MAGMA_BLOCK
    };

    private static final Material[] ORES = {
            Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_EMERALD_ORE, Material.AMETHYST_BLOCK, Material.ANCIENT_DEBRIS
    };

    private record BlockKey(UUID worldId, int x, int y, int z) { }

    private record SavedBlock(UUID worldId, String worldName, int x, int y, int z,
                              String blockData) { }

    private record PlannedChange(SavedBlock original, Material replacement) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final EventSpawnGuard spawnGuard;
    private final MessageManager messageManager;
    private final File recoveryFile;

    private volatile Location craterCenter;
    private volatile long expiresAt;
    private volatile List<SavedBlock> restoreStates;
    private volatile long nextAttemptAt;
    private volatile long spawnGraceUntil;
    private volatile boolean recoveryInProgress;
    private volatile long nextRecoveryAttemptAt;

    public MeteorEventManager(final JavaPlugin plugin, final ConfigManager configManager,
                              final EventSpawnGuard spawnGuard, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.spawnGuard = spawnGuard;
        this.messageManager = messageManager;
        this.recoveryFile = new File(plugin.getDataFolder(), "meteor-restore.yml");
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    public boolean isActive() {
        return craterCenter != null;
    }

    public long getRemainingMillis() {
        return isActive() ? Math.max(0L, expiresAt - System.currentTimeMillis()) : -1L;
    }

    public void tick() {
        final boolean enabled = configManager.getBoolean("meteor.enabled", true);
        final long now = System.currentTimeMillis();
        if (isActive()) {
            if (!enabled) {
                restoreCrater(false);
            } else if (now >= expiresAt) {
                restoreCrater(true);
            }
            return;
        }

        recoverInterruptedCrater();
        if (recoveryFile.exists() || recoveryInProgress || !enabled) {
            return;
        }

        if (now >= nextAttemptAt) {
            nextAttemptAt = now + intervalMillis();
            final double chance = Math.max(0.0D, Math.min(100.0D,
                    configManager.getDouble("meteor.chance-percent", 35.0D)));
            if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
                spawn(null);
            }
        }
    }

    public synchronized boolean forceSpawn(final Player anchor) {
        if (isActive() || recoveryFile.exists() || recoveryInProgress
                || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        return spawn(anchor);
    }

    public synchronized void shutdown() {
        spawnGraceUntil = 0L;
        restoreCrater(false);
    }

    private synchronized boolean spawn(final Player preferredAnchor) {
        if (isActive() || recoveryFile.exists() || recoveryInProgress
                || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + 60_000L;
        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                spawnGraceUntil = 0L;
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }

        final Player target = anchor;
        target.getScheduler().run(plugin, task -> {
            final Location origin = target.getLocation().clone();
            final long seed = System.nanoTime() ^ target.getUniqueId().getMostSignificantBits()
                    ^ target.getUniqueId().getLeastSignificantBits();
            spawnGuard.findSafeNear("meteor", origin, seed,
                    this::land, () -> spawnGraceUntil = 0L);
        }, () -> spawnGraceUntil = 0L);
        return true;
    }

    /** Called on the region thread owning an already dry and player-distant center. */
    private synchronized void land(final Location center) {
        final World world = center.getWorld();
        if (world == null || isActive() || recoveryFile.exists() || recoveryInProgress
                || spawnGuard.isBlocked("meteor", center)) {
            spawnGraceUntil = 0L;
            return;
        }
        final int x = center.getBlockX();
        final int z = center.getBlockZ();
        final int surfaceY = center.getBlockY() - 1;
        final Block surface = world.getBlockAt(x, surfaceY, z);
        if (surface.isLiquid() || org.bukkit.Tag.LEAVES.isTagged(surface.getType())
                || org.bukkit.Tag.LOGS.isTagged(surface.getType())) {
            spawnGraceUntil = 0L;
            return;
        }

        final int craterRadius = Math.min(8,
                Math.max(2, configManager.getInt("meteor.crater-radius", 3)));
        final int craterDepth = Math.max(1,
                configManager.getInt("meteor.crater-depth", 2));
        final double oreChance = Math.max(0.0D, Math.min(1.0D,
                configManager.getDouble("meteor.ore-chance", 0.45D)));

        final Map<BlockKey, PlannedChange> plan = new LinkedHashMap<>();
        for (int dx = -craterRadius; dx <= craterRadius; dx++) {
            for (int dz = -craterRadius; dz <= craterRadius; dz++) {
                final double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > craterRadius + 0.5D) {
                    continue;
                }
                final int cx = x + dx;
                final int cz = z + dz;
                final int columnTop = world.getHighestBlockYAt(cx, cz);
                final int depth = (int) Math.round(
                        (1.0D - dist / (craterRadius + 0.5D)) * craterDepth);
                for (int dy = 0; dy < depth; dy++) {
                    if (!planChange(plan, world.getBlockAt(cx, columnTop - dy, cz), Material.AIR)) {
                        spawnGraceUntil = 0L;
                        return;
                    }
                }
                final Material floor = (dist <= craterRadius * 0.6D
                        && ThreadLocalRandom.current().nextDouble() < oreChance)
                        ? ORES[ThreadLocalRandom.current().nextInt(ORES.length)]
                        : LINING[ThreadLocalRandom.current().nextInt(LINING.length)];
                if (!planChange(plan, world.getBlockAt(cx, columnTop - depth, cz), floor)) {
                    spawnGraceUntil = 0L;
                    return;
                }
            }
        }

        final List<SavedBlock> snapshots = plan.values().stream()
                .map(PlannedChange::original).toList();
        if (snapshots.isEmpty() || !persistRecovery(snapshots)) {
            spawnGraceUntil = 0L;
            return;
        }

        try {
            for (final PlannedChange change : plan.values()) {
                final SavedBlock original = change.original();
                world.getBlockAt(original.x(), original.y(), original.z())
                        .setType(change.replacement(), false);
            }
        } catch (final RuntimeException mutationFailure) {
            plugin.getLogger().severe("Meteor crater mutation failed after recovery snapshot: "
                    + mutationFailure.getMessage());
            restoreStates = snapshots;
            spawnGraceUntil = 0L;
            restoreCrater(false);
            return;
        }

        restoreStates = snapshots;
        craterCenter = center.clone();
        expiresAt = System.currentTimeMillis() + expireMillis();
        spawnGraceUntil = 0L;

        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2);
        world.spawnParticle(Particle.LAVA, center, 40,
                craterRadius, 1.0D, craterRadius, 0.0D);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.6F);

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "meteor-landed",
                "&c☄ METEOR csapódott be a(z) {world} világban ({x}, {z}) — a kráterben ritka érc, de {minutes} perc múlva elenyészik!",
                Map.of("world", world.getName(), "x", String.valueOf(x),
                        "z", String.valueOf(z),
                        "minutes", String.valueOf(Math.max(1L,
                                expireMillis() / 60_000L)))));
    }

    /** Tile entities are never overwritten because block-data alone cannot preserve their NBT. */
    private static boolean planChange(final Map<BlockKey, PlannedChange> plan,
                                      final Block block, final Material replacement) {
        if (block.getState() instanceof TileState) {
            return false;
        }
        final World world = block.getWorld();
        final BlockKey key = new BlockKey(world.getUID(), block.getX(), block.getY(), block.getZ());
        final PlannedChange existing = plan.get(key);
        if (existing == null) {
            plan.put(key, new PlannedChange(new SavedBlock(world.getUID(), world.getName(),
                    block.getX(), block.getY(), block.getZ(),
                    block.getBlockData().getAsString()), replacement));
        } else {
            plan.put(key, new PlannedChange(existing.original(), replacement));
        }
        return true;
    }

    private boolean persistRecovery(final List<SavedBlock> snapshots) {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            int index = 0;
            for (final SavedBlock block : snapshots) {
                final String base = "blocks." + index++ + ".";
                yaml.set(base + "world-id", block.worldId().toString());
                yaml.set(base + "world", block.worldName());
                yaml.set(base + "x", block.x());
                yaml.set(base + "y", block.y());
                yaml.set(base + "z", block.z());
                yaml.set(base + "block-data", block.blockData());
            }
            YamlStore.saveAtomic(recoveryFile, yaml);
            return true;
        } catch (final IOException failure) {
            plugin.getLogger().severe("Meteor recovery snapshot could not be saved; crater aborted: "
                    + failure.getMessage());
            return false;
        }
    }

    private void recoverInterruptedCrater() {
        final long now = System.currentTimeMillis();
        if (!recoveryFile.exists() || recoveryInProgress || now < nextRecoveryAttemptAt) {
            return;
        }
        final List<SavedBlock> pending = loadRecovery();
        if (pending.isEmpty()) {
            deleteRecoveryFile();
            return;
        }
        plugin.getLogger().warning("Recovering " + pending.size()
                + " meteor-modified blocks from an interrupted previous runtime.");
        scheduleRestore(pending, false, null);
    }

    private List<SavedBlock> loadRecovery() {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(recoveryFile);
        final ConfigurationSection root = yaml.getConfigurationSection("blocks");
        if (root == null) {
            return List.of();
        }
        final List<SavedBlock> result = new ArrayList<>();
        for (final String key : root.getKeys(false)) {
            final ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                result.add(new SavedBlock(
                        UUID.fromString(section.getString("world-id", "")),
                        section.getString("world", "world"),
                        section.getInt("x"), section.getInt("y"), section.getInt("z"),
                        section.getString("block-data", "minecraft:air")));
            } catch (final IllegalArgumentException malformed) {
                plugin.getLogger().warning("Skipping malformed meteor recovery entry " + key
                        + ": " + malformed.getMessage());
            }
        }
        return List.copyOf(result);
    }

    private synchronized void restoreCrater(final boolean announce) {
        final Location center = craterCenter;
        final List<SavedBlock> states = restoreStates;
        craterCenter = null;
        restoreStates = null;
        expiresAt = 0L;
        if (states == null || states.isEmpty()) {
            return;
        }
        scheduleRestore(states, announce, center);
    }

    private void scheduleRestore(final List<SavedBlock> states, final boolean announce,
                                 final Location center) {
        if (states.isEmpty() || recoveryInProgress) {
            return;
        }
        recoveryInProgress = true;
        final Map<String, List<SavedBlock>> groups = new LinkedHashMap<>();
        for (final SavedBlock block : states) {
            final String key = block.worldId() + ":" + (block.x() >> 4) + ":" + (block.z() >> 4);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(block);
        }
        final AtomicInteger remaining = new AtomicInteger(groups.size());
        final AtomicBoolean success = new AtomicBoolean(true);
        for (final List<SavedBlock> group : groups.values()) {
            final SavedBlock first = group.getFirst();
            final World world = Bukkit.getWorld(first.worldId());
            if (world == null) {
                success.set(false);
                finishRestoreGroup(remaining, success, announce, center);
                continue;
            }
            final World targetWorld = world;
            final int chunkX = first.x() >> 4;
            final int chunkZ = first.z() >> 4;
            final Location owner = new Location(targetWorld,
                    (chunkX << 4) + 8.0D, first.y(), (chunkZ << 4) + 8.0D);
            final Runnable restore = () -> {
                try {
                    for (final SavedBlock saved : group) {
                        final BlockData data = Bukkit.createBlockData(saved.blockData());
                        targetWorld.getBlockAt(saved.x(), saved.y(), saved.z())
                                .setBlockData(data, false);
                    }
                } catch (final RuntimeException failure) {
                    success.set(false);
                    plugin.getLogger().severe("Meteor block recovery failed in chunk "
                            + chunkX + "," + chunkZ + ": " + failure.getMessage());
                } finally {
                    finishRestoreGroup(remaining, success, announce, center);
                }
            };
            try {
                if (Bukkit.isOwnedByCurrentRegion(targetWorld, chunkX, chunkZ)) {
                    restore.run();
                } else {
                    plugin.getServer().getRegionScheduler().run(plugin, owner, task -> restore.run());
                }
            } catch (final RuntimeException unavailable) {
                success.set(false);
                finishRestoreGroup(remaining, success, announce, center);
            }
        }
    }

    private void finishRestoreGroup(final AtomicInteger remaining, final AtomicBoolean success,
                                    final boolean announce, final Location center) {
        if (remaining.decrementAndGet() != 0) {
            return;
        }
        recoveryInProgress = false;
        if (!success.get()) {
            nextRecoveryAttemptAt = System.currentTimeMillis() + 60_000L;
            plugin.getLogger().warning("Meteor recovery remains pending in "
                    + recoveryFile.getName() + "; retry scheduled.");
            return;
        }
        deleteRecoveryFile();
        nextRecoveryAttemptAt = 0L;
        if (announce) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "meteor-faded",
                    "&7☄ A meteor-kráter beomlott és elenyészett — a táj visszanyerte régi formáját."));
        }
        if (center != null && center.getWorld() != null && plugin.isEnabled()) {
            try {
                plugin.getServer().getRegionScheduler().run(plugin, center, task ->
                        center.getWorld().spawnParticle(Particle.CLOUD, center,
                                30, 2.0D, 1.0D, 2.0D, 0.02D));
            } catch (final RuntimeException ignored) {
                // Visual only; block recovery already completed.
            }
        }
    }

    private void deleteRecoveryFile() {
        try {
            Files.deleteIfExists(recoveryFile.toPath());
        } catch (final IOException failure) {
            plugin.getLogger().warning("Recovered meteor blocks but could not delete "
                    + recoveryFile.getName() + ": " + failure.getMessage());
        }
    }

    private long intervalMillis() {
        return Math.max(1L,
                configManager.getLong("meteor.interval-minutes", 85L)) * 60_000L;
    }

    private long expireMillis() {
        return Math.max(1L,
                configManager.getLong("meteor.expire-minutes", 10L)) * 60_000L;
    }
}
