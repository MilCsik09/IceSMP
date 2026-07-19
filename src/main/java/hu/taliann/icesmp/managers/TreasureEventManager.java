package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Treasure hunt world event (ROADMAP "élőbb világ", felfedező-ág): periodically a
 * marked treasure chest appears near a random adventurer and its rough location is
 * broadcast — the first player to reach and open it claims the loot, then it
 * vanishes. The reward is a config loot table of raw items, never currency.
 *
 * <p>The chest is a real block, so every placement/removal hops to the chest's
 * region thread (Folia-safe); the claim runs on the finder's region thread, which
 * is the same region as the block.
 */
public final class TreasureEventManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PartyManager partyManager;
    private final ClaimManager claimManager;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;

    private volatile Location chest;
    private volatile long expiresAt;
    private volatile ScheduledTask beaconTask;
    /** The block the chest replaced — restored (not AIR-ed) when the treasure ends. */
    private volatile org.bukkit.block.BlockState originalState;
    /** Reserves the "active" state while a spawn is still hopping region threads. */
    private volatile long spawnGraceUntil;
    /** True while an expiry settle-task is queued on the chest's region thread. */
    private volatile boolean expirySettling;

    private volatile long nextAttemptAt;

    public TreasureEventManager(final JavaPlugin plugin, final ConfigManager configManager,
                                final PartyManager partyManager, final ClaimManager claimManager,
                                final TerritoryManager territoryManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.partyManager = partyManager;
        this.claimManager = claimManager;
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Whether a treasure chest is currently waiting to be found. */
    public boolean isActive() {
        return chest != null;
    }

    /** Milliseconds left before the treasure expires, or -1 when none is active. */
    public long getRemainingMillis() {
        return chest != null ? Math.max(0L, expiresAt - System.currentTimeMillis()) : -1L;
    }

    /** Whether the given block is the active treasure chest (for the interact listener). */
    public boolean isTreasureBlock(final Block block) {
        final Location active = chest;
        return active != null && block != null
                && block.getWorld().getUID().equals(active.getWorld().getUID())
                && block.getX() == active.getBlockX()
                && block.getY() == active.getBlockY()
                && block.getZ() == active.getBlockZ();
    }

    /** Periodic driver on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("treasure-events.enabled", true)) {
            if (chest != null) {
                expireViaRegion();
            }
            return;
        }

        final long now = System.currentTimeMillis();
        if (chest != null) {
            if (now >= expiresAt) {
                expireViaRegion();
            }
            return;
        }
        if (now < spawnGraceUntil) {
            return; // A spawn is still hopping region threads.
        }

        if (now >= nextAttemptAt) {
            nextAttemptAt = now + intervalMillis();
            final double chance = Math.max(0.0D, Math.min(100.0D,
                    configManager.getDouble("treasure-events.chance-percent", 40.0D)));
            if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
                spawn(null);
            }
        }
    }

    /** Admin override: spawns a treasure now near the given anchor (or a random player). */
    public synchronized boolean forceSpawn(final Player anchor) {
        if (chest != null || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        return spawn(anchor);
    }

    private boolean spawn(final Player preferredAnchor) {
        // Reserve the active state while the spawn hops threads (self-heals after 10s),
        // so a second chest can never be placed and orphan the first.
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }

        final int radius = Math.max(16, configManager.getInt("treasure-events.spawn-radius", 80));
        final Player target = anchor;
        target.getScheduler().run(plugin, task -> {
            final Location base = target.getLocation().clone();
            final int x = base.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final int z = base.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final World world = base.getWorld();
            if (world == null) {
                return;
            }
            final Location spot = new Location(world, x, 0, z);
            plugin.getServer().getRegionScheduler().run(plugin, spot, place -> placeChest(world, x, z));
        }, null);
        return true;
    }

    private void placeChest(final World world, final int x, final int z) {
        final int y = world.getHighestBlockYAt(x, z) + 1;
        final Location spot = new Location(world, x, y, z);
        // Never place the chest inside a WorldGuard region (towns/spawn), a player claim,
        // or claimed faction territory — protection would also block opening/breaking it
        // (same rule set as MeteorEventManager.land). Retry next interval.
        if (hu.taliann.icesmp.integration.ProtectionBridge.isProtected(spot)
                || claimManager.getClaimAt(spot) != null
                || territoryManager.getTerritoryAt(spot) != null) {
            return;
        }
        // Terrain rule: don't hover over water, and remember whatever non-solid block
        // (grass, snow layer, flower…) we replace so it is RESTORED, never AIR-ed.
        if (world.getBlockAt(x, y - 1, z).isLiquid()) {
            return; // Retry next interval, elsewhere.
        }
        final Block block = spot.getBlock();
        originalState = block.getState();
        block.setType(Material.CHEST);
        chest = spot;
        expiresAt = System.currentTimeMillis() + expireMillis();
        spawnGraceUntil = 0L;
        startBeacon(spot);

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "treasure-spawned",
                "&6🗺 Elrejtett kincs bukkant fel a(z) {world} világban a(z) ({x}, {z}) környékén — {minutes} percig marad, az első megtaláló viszi!",
                Map.of(
                        "world", world.getName(),
                        "x", String.valueOf(x),
                        "z", String.valueOf(z),
                        "minutes", String.valueOf(Math.max(1L, expireMillis() / 60_000L))
                )
        ));
    }

    /**
     * Claims the treasure for the finder: grants the loot, removes the chest and
     * announces the winner. Called on the finder's region thread (the chest's region).
     *
     * @param finder the claiming player
     */
    public void claim(final Player finder) {
        final Location active = chest;
        if (active == null) {
            return;
        }
        // Clear tracking first so a second interaction / the expiry tick can't double-grant.
        chest = null;
        cancelBeacon();

        restoreBlock(active);
        active.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, active.clone().add(0.5D, 1.0D, 0.5D), 24, 0.5D, 0.5D, 0.5D, 0.0D);
        active.getWorld().playSound(active, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);

        final int rolls = Math.max(1, configManager.getInt("treasure-events.rolls", 3));
        // WoW-style personal loot: a finder in a party shares the discovery — every
        // nearby member rolls their own reward; solo finders keep the classic grant.
        if (!partyManager.distributePersonalLoot(finder, "treasure-events.loot", rolls)) {
            for (final ItemStack loot : LootTable.roll(configManager, "treasure-events.loot", rolls)) {
                finder.getInventory().addItem(loot).values()
                        .forEach(left -> finder.getWorld().dropItemNaturally(finder.getLocation(), left));
            }
        }

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "treasure-claimed",
                "&e🗺 {player} megtalálta az elrejtett kincset!",
                Map.of("player", finder.getName())
        ));
    }

    /** Removes the chest on plugin disable / expiry. */
    public void shutdown() {
        despawn(false);
    }

    /**
     * Expiry settle, serialized with {@link #claim} on the chest's region thread:
     * the field is only cleared INSIDE the region task, so a player clicking the
     * chest in the same instant either wins the loot or sees it already gone —
     * never an unclaimable ghost chest.
     */
    private void expireViaRegion() {
        final Location active = chest;
        if (active == null || expirySettling) {
            return;
        }
        expirySettling = true;
        try {
            plugin.getServer().getRegionScheduler().run(plugin, active, task -> {
                try {
                    if (chest == active) { // claim() may have won on this same thread.
                        chest = null;
                        cancelBeacon();
                        restoreBlock(active);
                        Bukkit.getServer().broadcast(messageManager.getMessage(
                                "treasure-expired", "&7🗺 Az elrejtett kincs feltáratlanul eltűnt a homokban."));
                    }
                } finally {
                    expirySettling = false;
                }
            });
        } catch (final Exception exception) {
            expirySettling = false;
        }
    }

    /** Puts back whatever block the chest replaced (falls back to AIR). Region thread. */
    private void restoreBlock(final Location at) {
        final org.bukkit.block.BlockState original = originalState;
        originalState = null;
        final Block block = at.getBlock();
        if (original != null) {
            original.update(true, false);
        } else if (block.getType() == Material.CHEST) {
            block.setType(Material.AIR);
        }
    }

    private void despawn(final boolean announce) {
        final Location active = chest;
        chest = null;
        cancelBeacon();
        if (active == null) {
            return;
        }
        // Folia: restoring the block must run on its own region thread.
        try {
            plugin.getServer().getRegionScheduler().run(plugin, active, task -> restoreBlock(active));
        } catch (final Exception ignored) {
            // Scheduler unavailable during shutdown — leave the block.
        }
        if (announce) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "treasure-expired", "&7🗺 Az elrejtett kincs feltáratlanul eltűnt a homokban."));
        }
    }

    /** Marks the chest with a rising particle beacon so players can spot it up close; auto-cancels. */
    private void startBeacon(final Location spot) {
        beaconTask = plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, spot, task -> {
            if (chest == null) {
                task.cancel();
                return;
            }
            final World world = spot.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.END_ROD, spot.clone().add(0.5D, 1.2D, 0.5D), 8, 0.15D, 1.0D, 0.15D, 0.01D);
                world.spawnParticle(Particle.GLOW, spot.clone().add(0.5D, 0.6D, 0.5D), 4, 0.3D, 0.3D, 0.3D, 0.0D);
                // Messziről is látható fény-oszlop a láda fölött (a "merre van?" jelzés —
                // a láda-szintű csillogás közelről szép, de dombok mögül láthatatlan volt).
                world.spawnParticle(Particle.END_ROD, spot.clone().add(0.5D, 9.0D, 0.5D), 14, 0.1D, 7.0D, 0.1D, 0.0D);
            }
        }, 20L, 20L);
    }

    private void cancelBeacon() {
        final ScheduledTask task = beaconTask;
        beaconTask = null;
        if (task != null) {
            try {
                task.cancel();
            } catch (final Exception ignored) {
                // Already cancelled / region gone.
            }
        }
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("treasure-events.interval-minutes", 45L)) * 60_000L;
    }

    private long expireMillis() {
        return Math.max(1L, configManager.getLong("treasure-events.expire-minutes", 15L)) * 60_000L;
    }
}
