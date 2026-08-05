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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Merchant caravan world event.
 * Periodically a travelling merchant arrives at one of the configured stops (or,
 * if none are set, near a random online player), stays for a limited window and
 * then departs. Every anchor is resolved through {@link EventSpawnGuard}; a configured
 * point is only a preferred column, never permission to spawn in water or on a shoreline.
 */
public final class CaravanManager {

    /** The reserved ShopManager name the caravan's stock is served under. */
    public static final String SHOP_NAME = "caravan";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    /** N25 — setterrel kötve (a spawnpont-manager később épül a DI-sorrendben). */
    private volatile EventSpawnPointManager spawnPointManager;

    public void setSpawnPointManager(final EventSpawnPointManager spawnPointManager) {
        this.spawnPointManager = spawnPointManager;
    }

    private volatile boolean active;
    private volatile boolean arrivalPending;
    private volatile long activeUntil;
    private volatile long nextArrivalAt;
    private volatile UUID merchantId;
    private volatile int stopIndex;
    private volatile long stockSeed = System.currentTimeMillis();
    /** Invalidates callbacks from an older search/depart/shutdown generation. */
    private final AtomicLong arrivalGeneration = new AtomicLong();

    public CaravanManager(final JavaPlugin plugin, final ConfigManager configManager,
                          final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.nextArrivalAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Whether the caravan merchant is actually spawned and buyable. */
    public boolean isActive() {
        return active;
    }

    public long getRemainingMillis() {
        return active ? Math.max(0L, activeUntil - System.currentTimeMillis()) : -1L;
    }

    public boolean isCaravanEntity(final UUID entityId) {
        return active && entityId != null && entityId.equals(merchantId);
    }

    public void tick() {
        if (!configManager.getBoolean("caravan.enabled", true)) {
            if (active || arrivalPending) {
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
        if (arrivalPending) {
            return;
        }
        if (now >= nextArrivalAt) {
            arrive();
        }
    }

    /** Admin override: begins one bounded safe-location search. */
    public boolean forceArrive(final Player anchor) {
        if (active || arrivalPending) {
            return false;
        }
        arrive(anchor);
        return true;
    }

    public boolean forceDepart() {
        if (!active && !arrivalPending) {
            return false;
        }
        depart();
        return true;
    }

    public long getStockSeed() {
        return stockSeed;
    }

    public void shutdown() {
        arrivalGeneration.incrementAndGet();
        arrivalPending = false;
        removeMerchant();
        active = false;
        merchantId = null;
    }

    private void arrive() {
        arrive(null);
    }

    private void arrive(final Player preferredAnchor) {
        final long now = System.currentTimeMillis();
        nextArrivalAt = now + intervalMillis();
        stockSeed = ThreadLocalRandom.current().nextLong();

        final Location stop = nextStop();
        if (stop != null) {
            beginSafeArrival(stop);
            return;
        }

        final EventSpawnPointManager pointsRef = spawnPointManager;
        final Location fixedAnchor = preferredAnchor != null || pointsRef == null
                ? null : pointsRef.resolveAnchorLocation("caravan");
        if (fixedAnchor != null) {
            beginSafeArrival(fixedAnchor);
            return;
        }

        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }

        final Player target = anchor;
        final long anchorGeneration = arrivalGeneration.incrementAndGet();
        arrivalPending = true;
        target.getScheduler().run(plugin, task -> {
            if (anchorGeneration != arrivalGeneration.get() || !arrivalPending) {
                return;
            }
            beginSafeArrival(target.getLocation().clone());
        }, () -> {
            if (anchorGeneration == arrivalGeneration.get()) {
                arrivalPending = false;
            }
        });
    }

    private void beginSafeArrival(final Location preferred) {
        final EventSpawnGuard guard = EventSpawnGuard.current();
        if (guard == null || preferred == null || preferred.getWorld() == null) {
            plugin.getLogger().warning("Caravan arrival aborted: EventSpawnGuard or anchor unavailable.");
            arrivalPending = false;
            return;
        }
        final long generation = arrivalGeneration.incrementAndGet();
        arrivalPending = true;
        guard.findSafeAtOrNear("caravan", preferred, stockSeed,
                spot -> spawnMerchant(spot, generation),
                () -> failArrival(generation, preferred));
    }

    /** Called on the region thread owning the already validated dry location. */
    private void spawnMerchant(final Location spot, final long generation) {
        if (generation != arrivalGeneration.get() || !arrivalPending) {
            return;
        }
        final World world = spot.getWorld();
        if (world == null) {
            failArrival(generation, spot);
            return;
        }

        try {
            final WanderingTrader merchant = world.spawn(spot, WanderingTrader.class, spawned -> {
                spawned.setAI(false);
                spawned.setInvulnerable(true);
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

            if (generation != arrivalGeneration.get()) {
                merchant.remove();
                return;
            }
            merchantId = merchant.getUniqueId();
            active = true;
            arrivalPending = false;
            activeUntil = System.currentTimeMillis() + durationMillis();
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
        } catch (final RuntimeException failure) {
            plugin.getLogger().warning("Caravan merchant spawn failed at validated location: " + failure);
            failArrival(generation, spot);
        }
    }

    private void failArrival(final long generation, final Location anchor) {
        if (generation != arrivalGeneration.get()) {
            return;
        }
        arrivalPending = false;
        active = false;
        merchantId = null;
        final World world = anchor == null ? null : anchor.getWorld();
        plugin.getLogger().warning("Caravan arrival aborted: no dry shoreline-safe location near "
                + (world == null ? "unknown" : world.getName() + " "
                + anchor.getBlockX() + "," + anchor.getBlockZ()) + '.');
    }

    private void depart() {
        arrivalGeneration.incrementAndGet();
        arrivalPending = false;
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
            entity.getScheduler().run(plugin, task -> entity.remove(), null);
        } catch (final Exception ignored) {
            // Non-persistent merchant cannot survive restart if shutdown won the scheduler race.
        }
    }

    private boolean merchantValid() {
        final UUID id = merchantId;
        if (id == null) {
            return false;
        }
        try {
            final Entity entity = Bukkit.getEntity(id);
            return entity != null && entity.isValid();
        } catch (final Exception exception) {
            return true;
        }
    }

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

    /** Returns the next configured preferred column, or null when no stop is usable. */
    private Location nextStop() {
        final List<Map<?, ?>> stops = configManager.getConfiguration() == null
                ? List.of() : configManager.getConfiguration().getMapList("caravan.stops");
        if (stops.isEmpty()) {
            return null;
        }
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

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("caravan.interval-minutes", 90L)) * 60_000L;
    }

    private long durationMillis() {
        return Math.max(1L, configManager.getLong("caravan.duration-minutes", 20L)) * 60_000L;
    }
}
