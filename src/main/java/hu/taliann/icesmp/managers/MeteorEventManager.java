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
 * Meteor impact world event (ROADMAP "élőbb világ", felfedező-ág): periodically a
 * meteor lands in the wilderness, leaving a small crater studded with rare, mineable
 * ore blocks that players race to strip before it erodes away. The reward is the ore
 * they mine — raw items, never currency.
 *
 * <p><b>Terrain safety (server rule: events never grief the world).</b> The meteor
 * does <em>not</em> explode: it <em>places</em> a crater and snapshots every block it
 * overwrites ({@link BlockState}), then restores those originals on expiry (and on
 * plugin disable). It also refuses to land inside a claimed faction territory. So the
 * only lasting change is whatever ore players carry off before it fades.
 */
public final class MeteorEventManager {

    /** Blackened crater lining (cosmetic; harmless blocks). */
    private static final Material[] LINING = {
            Material.BLACKSTONE, Material.BASALT, Material.COBBLED_DEEPSLATE, Material.MAGMA_BLOCK
    };

    /** Mineable ore blocks embedded in the crater (drop real materials when mined). */
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
    /**
     * Reserved while a spawn hops threads (set synchronously, self-heals after 10s):
     * craterCenter is only written at the END of the async landing chain, so without this a
     * second force-spawn issued during the hop could carve two craters and orphan one
     * (same pattern as TreasureEventManager.spawnGraceUntil).
     */
    private volatile long spawnGraceUntil;

    public MeteorEventManager(final JavaPlugin plugin, final ConfigManager configManager,
                              final EventSpawnGuard spawnGuard, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.spawnGuard = spawnGuard;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Whether a crater is currently present. */
    public boolean isActive() {
        return craterCenter != null;
    }

    /** Milliseconds left before the crater fades, or -1 when none is active. */
    public long getRemainingMillis() {
        return isActive() ? Math.max(0L, expiresAt - System.currentTimeMillis()) : -1L;
    }

    /** Periodic driver on the global world-events tick. */
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

    /** Admin override: lands a meteor now near the anchor (or a random player).
     * synchronized: két egyidejű admin-hívás ne áshasson két krátert (grace-rés). */
    public synchronized boolean forceSpawn(final Player anchor) {
        if (isActive() || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        return spawn(anchor);
    }

    /** Restores the crater on plugin disable so no terrain change survives a graceful restart. */
    public void shutdown() {
        restoreCrater(false);
    }

    private synchronized boolean spawn(final Player preferredAnchor) {
        // Zárt check-then-act: a synchronized belépés UTÁN is újraellenőrzünk — a tick
        // és egy egyidejű admin-hívás közül csak az első juthat át.
        if (System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }

        final int radius = Math.max(24, configManager.getInt("meteor.spawn-radius", 90));
        final Player target = anchor;
        target.getScheduler().run(plugin, task -> {
            final Location base = target.getLocation().clone();
            final int x = base.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final int z = base.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final World world = base.getWorld();
            if (world == null) {
                return;
            }
            final Location probe = new Location(world, x, 0.0D, z);
            plugin.getServer().getRegionScheduler().run(plugin, probe, place -> land(world, x, z));
        }, null);
        return true;
    }

    private void land(final World world, final int x, final int z) {
        final int surfaceY = world.getHighestBlockYAt(x, z);
        final Location center = new Location(world, x + 0.5D, surfaceY + 1, z + 0.5D);

        // Terrain safety: never land inside a claimed faction territory, a player claim, nor
        // a WorldGuard region (config: world-events.spawn-rules.meteor — replaces the old
        // meteor.avoid-territory key).
        if (spawnGuard.isBlocked("meteor", center)) {
            return; // Try again next interval, elsewhere.
        }

        // Terrain rule: a crater in a tree canopy or on a water surface would look broken
        // (getHighestBlockYAt reports leaf/water tops) — pick another spot next interval.
        final org.bukkit.block.Block surface = world.getBlockAt(x, surfaceY, z);
        if (surface.isLiquid() || org.bukkit.Tag.LEAVES.isTagged(surface.getType())
                || org.bukkit.Tag.LOGS.isTagged(surface.getType())) {
            return;
        }

        // Upper clamp: the carve loop runs in ONE region-scheduler callback keyed to the
        // centre block, so the scan must stay region-local (Folia) even if misconfigured.
        final int craterRadius = Math.min(8, Math.max(2, configManager.getInt("meteor.crater-radius", 3)));
        final int craterDepth = Math.max(1, configManager.getInt("meteor.crater-depth", 2));
        final double oreChance = Math.max(0.0D, Math.min(1.0D, configManager.getDouble("meteor.ore-chance", 0.45D)));

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
                final int depth = (int) Math.round((1.0D - dist / (craterRadius + 0.5D)) * craterDepth);

                // Carve a shallow bowl (snapshot each cell first so it can be restored).
                for (int dy = 0; dy < depth; dy++) {
                    carve(snapshots, world.getBlockAt(cx, columnTop - dy, cz), Material.AIR);
                }
                // Line/seed the bowl floor: ore near the centre, blackened lining toward the rim.
                final Material floor = (dist <= craterRadius * 0.6D && ThreadLocalRandom.current().nextDouble() < oreChance)
                        ? ORES[ThreadLocalRandom.current().nextInt(ORES.length)]
                        : LINING[ThreadLocalRandom.current().nextInt(LINING.length)];
                carve(snapshots, world.getBlockAt(cx, columnTop - depth, cz), floor);
            }
        }

        restoreStates = snapshots;
        craterCenter = center;
        expiresAt = System.currentTimeMillis() + expireMillis();

        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2);
        world.spawnParticle(Particle.LAVA, center, 40, craterRadius, 1.0D, craterRadius, 0.0D);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.6F);

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "meteor-landed",
                "&c☄ METEOR csapódott be a(z) {world} világban ({x}, {z}) — a kráterben ritka érc, de {minutes} perc múlva elenyészik!",
                Map.of("world", world.getName(), "x", String.valueOf(x), "z", String.valueOf(z),
                        "minutes", String.valueOf(Math.max(1L, expireMillis() / 60_000L)))
        ));
    }

    /** Snapshots {@code block}'s current state (for later restore) then sets the new material. */
    private void carve(final List<BlockState> snapshots, final Block block, final Material material) {
        snapshots.add(block.getState());
        block.setType(material, false);
    }

    private void restoreCrater(final boolean announce) {
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
                    center.getWorld().spawnParticle(Particle.CLOUD, center, 30, 2.0D, 1.0D, 2.0D, 0.02D);
                }
            });
        } catch (final Exception ignored) {
            // Scheduler unavailable during shutdown — a graceful disable normally still runs it.
        }
        if (announce) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "meteor-faded", "&7☄ A meteor-kráter beomlott és elenyészett — a táj visszanyerte régi formáját."));
        }
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("meteor.interval-minutes", 85L)) * 60_000L;
    }

    private long expireMillis() {
        return Math.max(1L, configManager.getLong("meteor.expire-minutes", 10L)) * 60_000L;
    }
}
