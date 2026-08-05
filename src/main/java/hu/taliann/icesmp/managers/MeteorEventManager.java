package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Meteor impact world event. Landing uses the shared distant spawn search, so the
 * crater cannot visibly appear beside a player. Every overwritten block is restored
 * on expiry or graceful shutdown.
 */
public final class MeteorEventManager {

    private static final Material[] LINING = {
            Material.BLACKSTONE, Material.BASALT, Material.COBBLED_DEEPSLATE, Material.MAGMA_BLOCK
    };

    private static final Material[] ORES = {
            Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_EMERALD_ORE, Material.AMETHYST_BLOCK, Material.ANCIENT_DEBRIS
    };

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final EventSpawnGuard spawnGuard;
    private final MessageManager messageManager;

    private volatile Location craterCenter;
    private volatile long expiresAt;
    private volatile List<BlockState> restoreStates;
    private volatile long nextAttemptAt;
    private volatile long spawnGraceUntil;

    public MeteorEventManager(final JavaPlugin plugin, final ConfigManager configManager,
                              final EventSpawnGuard spawnGuard, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.spawnGuard = spawnGuard;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    public boolean isActive() {
        return craterCenter != null;
    }

    public long getRemainingMillis() {
        return isActive() ? Math.max(0L, expiresAt - System.currentTimeMillis()) : -1L;
    }

    public void tick() {
        if (!configManager.getBoolean("meteor.enabled", true)) {
            if (isActive()) {
                restoreCrater(false);
            }
            return;
        }

        final long now = System.currentTimeMillis();
        if (isActive()) {
            if (now >= expiresAt) {
                restoreCrater(true);
            }
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
        if (isActive() || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        return spawn(anchor);
    }

    public synchronized void shutdown() {
        spawnGraceUntil = 0L;
        restoreCrater(false);
    }

    private synchronized boolean spawn(final Player preferredAnchor) {
        if (isActive() || System.currentTimeMillis() < spawnGraceUntil) {
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
        if (world == null || isActive() || spawnGuard.isBlocked("meteor", center)) {
            spawnGraceUntil = 0L;
            return;
        }
        final int x = center.getBlockX();
        final int z = center.getBlockZ();
        final int surfaceY = center.getBlockY() - 1;
        final org.bukkit.block.Block surface = world.getBlockAt(x, surfaceY, z);
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

        final List<BlockState> snapshots = new ArrayList<>();
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
                    carve(snapshots, world.getBlockAt(cx, columnTop - dy, cz), Material.AIR);
                }
                final Material floor = (dist <= craterRadius * 0.6D
                        && ThreadLocalRandom.current().nextDouble() < oreChance)
                        ? ORES[ThreadLocalRandom.current().nextInt(ORES.length)]
                        : LINING[ThreadLocalRandom.current().nextInt(LINING.length)];
                carve(snapshots, world.getBlockAt(cx, columnTop - depth, cz), floor);
            }
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

    private void carve(final List<BlockState> snapshots,
                       final Block block, final Material material) {
        snapshots.add(block.getState());
        block.setType(material, false);
    }

    private synchronized void restoreCrater(final boolean announce) {
        final Location center = craterCenter;
        final List<BlockState> states = restoreStates;
        craterCenter = null;
        restoreStates = null;
        if (center == null || states == null) {
            return;
        }
        try {
            plugin.getServer().getRegionScheduler().run(plugin, center, task -> {
                for (final BlockState state : states) {
                    state.update(true, false);
                }
                if (announce && center.getWorld() != null) {
                    center.getWorld().spawnParticle(Particle.CLOUD, center,
                            30, 2.0D, 1.0D, 2.0D, 0.02D);
                }
            });
        } catch (final Exception ignored) {
            // Scheduler unavailable during shutdown.
        }
        if (announce) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "meteor-faded",
                    "&7☄ A meteor-kráter beomlott és elenyészett — a táj visszanyerte régi formáját."));
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
