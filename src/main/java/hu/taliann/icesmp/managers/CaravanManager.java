package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Merchant caravan world event (ROADMAP economy: "kereskedő-karaván esemény").
 * Periodically a travelling merchant arrives at one of the configured stops (or,
 * if none are set, near a random online player), stays for a limited window and
 * then departs. While it is in town, right-clicking the merchant opens a special
 * caravan shop of rare goods sold for currency that is BURNED — a money sink,
 * like the faction shops, but a rotating, time-limited destination.
 *
 * <p>The caravan's stock is the {@code caravan.items} config section, served
 * through {@link ShopManager} under the reserved name {@code "caravan"} (gated by
 * {@link #isActive()} so it can only be bought while the caravan is present).
 * The spawn runs on the target region's thread (Folia-safe).
 */
public final class CaravanManager {

    /** The reserved ShopManager name the caravan's stock is served under. */
    public static final String SHOP_NAME = "caravan";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private volatile boolean active;
    private volatile long activeUntil;
    private volatile long nextArrivalAt;
    private volatile UUID merchantId;
    private volatile int stopIndex;

    public CaravanManager(final JavaPlugin plugin, final ConfigManager configManager,
                          final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        // Don't arrive the instant the server boots — wait a full interval first.
        this.nextArrivalAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Whether the caravan is currently in town (used to gate the caravan shop). */
    public boolean isActive() {
        return active;
    }

    /** Milliseconds left before the caravan departs, or -1 when it is not in town. */
    public long getRemainingMillis() {
        return active ? Math.max(0L, activeUntil - System.currentTimeMillis()) : -1L;
    }

    /** Whether the given entity is the caravan merchant (for the interact listener). */
    public boolean isCaravanEntity(final UUID entityId) {
        return active && entityId != null && entityId.equals(merchantId);
    }

    /** Periodic driver on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("caravan.enabled", true)) {
            if (active) {
                depart();
            }
            return;
        }

        final long now = System.currentTimeMillis();
        if (active) {
            if (now >= activeUntil || !merchantValid()) {
                depart();
            }
            return;
        }

        if (now >= nextArrivalAt) {
            arrive();
        }
    }

    /**
     * Admin override: makes the caravan arrive now (near the issuing player, or a
     * configured stop / random player).
     *
     * @param anchor preferred anchor when no stops are configured, may be null
     * @return true if the caravan arrived, false if it was already in town
     */
    public boolean forceArrive(final Player anchor) {
        if (active) {
            return false;
        }
        arrive(anchor);
        return true;
    }

    /**
     * Admin override: makes the caravan depart now.
     *
     * @return true if the caravan left, false if it was not in town
     */
    public boolean forceDepart() {
        if (!active) {
            return false;
        }
        depart();
        return true;
    }

    /** A jelenlegi látogatás készlet-sorsolási magja (érkezésenként újrasorsolva). */
    private volatile long stockSeed = System.currentTimeMillis();

    public long getStockSeed() {
        return stockSeed;
    }

    /** Removes the merchant on plugin disable so it does not survive as an orphan. */
    public void shutdown() {
        removeMerchant();
        active = false;
        merchantId = null;
    }

    private void arrive() {
        arrive(null);
    }

    private void arrive(final Player preferredAnchor) {
        final long now = System.currentTimeMillis();
        // Reschedule the NEXT arrival up front so a failed spawn still retries later.
        nextArrivalAt = now + intervalMillis();
        // Rotáló készlet: minden érkezéskor új sorsolási mag — a ShopManager ebből
        // válogatja ki, hogy MOST épp mit árul a karaván (caravan.rotation.*).
        stockSeed = java.util.concurrent.ThreadLocalRandom.current().nextLong();

        final Location stop = nextStop();
        if (stop != null) {
            active = true;
            activeUntil = now + durationMillis();
            plugin.getServer().getRegionScheduler().run(plugin, stop, task -> spawnMerchant(stop));
            return;
        }

        // No stops configured: appear near a random online player (or the admin anchor).
        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return; // Nobody around; try again next interval.
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }

        active = true;
        activeUntil = now + durationMillis();
        final Player target = anchor;
        // Folia: read the anchor's location on its own region thread, then hop to that region to spawn.
        target.getScheduler().run(plugin, task -> {
            final Location near = topOf(target.getLocation().clone());
            plugin.getServer().getRegionScheduler().run(plugin, near, spawn -> spawnMerchant(near));
        }, null);
    }

    private void spawnMerchant(final Location spot) {
        final World world = spot.getWorld();
        if (world == null) {
            active = false;
            return;
        }

        final WanderingTrader merchant = world.spawn(spot, WanderingTrader.class, spawned -> {
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            // Not persistent: a temporary event entity must never survive a restart as an
            // untracked orphan. setRemoveWhenFarAway(false) keeps it from self-despawning.
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setCanPickupItems(false);
            spawned.setDespawnDelay(Integer.MAX_VALUE);
            spawned.setCollidable(false);
            spawned.customName(net.kyori.adventure.text.Component.text(
                    "✦ " + configManager.getString("caravan.title", "Vándorkereskedő Karaván"),
                    net.kyori.adventure.text.format.NamedTextColor.GOLD));
            spawned.setCustomNameVisible(true);
        });

        merchantId = merchant.getUniqueId();
        startAmbientTick(merchant);

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "caravan-arrived",
                "<gold>✦ KERESKEDŐ-KARAVÁN érkezett a(z) {world} világba ({x}, {y}, {z}) — {minutes} percig marad! Kattints rá a ritka portékákért.</gold>",
                Map.of(
                        "world", world.getName(),
                        "x", String.valueOf(spot.getBlockX()),
                        "y", String.valueOf(spot.getBlockY()),
                        "z", String.valueOf(spot.getBlockZ()),
                        "minutes", String.valueOf(Math.max(1L, durationMillis() / 60_000L))
                )
        ));
    }

    private void depart() {
        removeMerchant();
        final boolean wasActive = active;
        active = false;
        merchantId = null;
        if (wasActive) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "caravan-departed",
                    "&6✦ A kereskedő-karaván összepakolt és továbbállt. Legközelebb máshol bukkan fel."));
        }
    }

    private void removeMerchant() {
        final UUID id = merchantId;
        if (id == null) {
            return;
        }
        try {
            final Entity entity = Bukkit.getEntity(id);
            if (entity == null || !entity.isValid()) {
                return;
            }
            // Folia: entity removal must run on the entity's own region thread.
            entity.getScheduler().run(plugin, task -> entity.remove(), null);
        } catch (final Exception ignored) {
            // Region/scheduler unavailable (e.g. during shutdown) — the non-persistent
            // merchant will not survive a restart anyway.
        }
    }

    private boolean merchantValid() {
        final UUID id = merchantId;
        if (id == null) {
            return true; // Spawn may still be in flight on the region thread — don't depart yet.
        }
        try {
            final Entity entity = Bukkit.getEntity(id);
            return entity != null && entity.isValid();
        } catch (final Exception exception) {
            return true; // Chunk/region unavailable — assume alive rather than yanking it away.
        }
    }

    /** Light happy-villager sparkle above the merchant so players can spot it; auto-cancels. */
    private void startAmbientTick(final WanderingTrader merchant) {
        merchant.getScheduler().runAtFixedRate(plugin, task -> {
            if (!merchant.isValid() || !active) {
                task.cancel();
                return;
            }
            final Location at = merchant.getLocation().clone().add(0.0D, 2.2D, 0.0D);
            merchant.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 6, 0.3D, 0.3D, 0.3D, 0.0D);
            merchant.getWorld().playSound(merchant.getLocation(), Sound.ENTITY_WANDERING_TRADER_AMBIENT, 0.6F, 1.0F);
        }, null, 60L, 60L);
    }

    /**
     * Resolves the next stop from {@code caravan.stops} (rotating through the list),
     * or null if no stops are configured / the world is unavailable.
     */
    private Location nextStop() {
        final List<Map<?, ?>> stops = configManager.getConfiguration() == null
                ? List.of() : configManager.getConfiguration().getMapList("caravan.stops");
        if (stops.isEmpty()) {
            return null;
        }
        // Advance the rotation index; wrap around the configured stops.
        final int index = Math.floorMod(stopIndex++, stops.size());
        final Map<?, ?> stop = stops.get(index);
        final Object worldName = stop.get("world");
        if (worldName == null) {
            return null;
        }
        final World world = Bukkit.getWorld(String.valueOf(worldName));
        if (world == null) {
            return null;
        }
        return new Location(world,
                toDouble(stop.get("x")) + 0.5D,
                toDouble(stop.get("y")),
                toDouble(stop.get("z")) + 0.5D);
    }

    private static double toDouble(final Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0D : Double.parseDouble(String.valueOf(value));
        } catch (final NumberFormatException exception) {
            return 0.0D;
        }
    }

    /** Highest safe spot above the given location's column (for the player-fallback spawn). */
    private Location topOf(final Location base) {
        final World world = base.getWorld();
        final int x = base.getBlockX();
        final int z = base.getBlockZ();
        final int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("caravan.interval-minutes", 90L)) * 60_000L;
    }

    private long durationMillis() {
        return Math.max(1L, configManager.getLong("caravan.duration-minutes", 20L)) * 60_000L;
    }
}
