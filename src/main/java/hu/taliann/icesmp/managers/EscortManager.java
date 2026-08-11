package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Caravan escort world event with distant, fully guarded route placement. */
public final class EscortManager {
    private static final EntityType[] WAVE_POOL = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.PILLAGER,
            EntityType.VINDICATOR, EntityType.SPIDER
    };
    /** Search + atmospheric arrival + route validation may legitimately take a few seconds. */
    private static final long SPAWN_GRACE_MILLIS = 30_000L;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final MessageManager messageManager;
    private final EventSpawnGuard spawnGuard;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 0.0F,
            BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
    private final hu.taliann.icesmp.utils.PeriodicChanceEvent schedule =
            new hu.taliann.icesmp.utils.PeriodicChanceEvent();
    private final Set<UUID> waveMobs = ConcurrentHashMap.newKeySet();

    private volatile UUID convoyId;
    private volatile UUID lastAnchorId;
    private volatile EventSpawnPointManager spawnPointManager;
    private volatile MajorEventGate eventGate;
    private volatile Location destination;
    private volatile double totalDistance;
    private volatile long expiresAt;
    private volatile long bonusStockUntil;

    public EscortManager(final JavaPlugin plugin, final ConfigManager configManager,
                         final MobScalingManager mobScalingManager,
                         final MessageManager messageManager,
                         final EventSpawnGuard spawnGuard) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.messageManager = messageManager;
        this.spawnGuard = spawnGuard;
        this.schedule.delayNextAttempt(intervalMillis());
    }

    public void setSpawnPointManager(final EventSpawnPointManager spawnPointManager) {
        this.spawnPointManager = spawnPointManager;
    }

    public void setEventGate(final MajorEventGate eventGate) {
        this.eventGate = eventGate;
    }

    public boolean isActive() {
        return convoyId != null;
    }

    public long getRemainingMillis() {
        return isActive() ? Math.max(0L, expiresAt - System.currentTimeMillis()) : -1L;
    }

    private synchronized UUID claimSettlement() {
        final UUID id = convoyId;
        convoyId = null;
        return id;
    }

    public boolean isConvoy(final UUID entityId) {
        return entityId != null && entityId.equals(convoyId);
    }

    public boolean isWaveMob(final UUID entityId) {
        return entityId != null && waveMobs.contains(entityId);
    }

    public boolean isBonusStockActive() {
        return System.currentTimeMillis() < bonusStockUntil;
    }

    public void showTo(final Player player) {
        if (isActive()) {
            player.showBossBar(bar);
        }
    }

    public void tick() {
        if (!configManager.getBoolean("escort.enabled", true)) {
            if (isActive()) {
                fail("escort-failed-timeout");
            }
            return;
        }
        final long now = System.currentTimeMillis();
        if (isActive()) {
            if (now >= expiresAt) {
                fail("escort-failed-timeout");
            } else if (!hu.taliann.icesmp.utils.TransientEntities.isAlive(convoyId)) {
                fail("escort-failed-died");
            }
            return;
        }
        final MajorEventGate gateRef = eventGate;
        if (gateRef != null && !gateRef.mayStartNaturally("escort")) {
            return;
        }
        if (schedule.tryAttempt(intervalMillis(),
                configManager.getDouble("escort.chance-percent", 30.0D),
                SPAWN_GRACE_MILLIS) && !start(null)) {
            schedule.release();
        }
    }

    /** Admin force defaults to the configured distant anchor; a player anchor is opt-in. */
    public boolean forceStart(final Player anchor) {
        if (isActive() || !schedule.tryForce(SPAWN_GRACE_MILLIS)) {
            return false;
        }
        final Player selected = configManager.getBoolean(
                "escort.force-use-player-anchor", false) ? anchor : null;
        if (start(selected)) {
            return true;
        }
        schedule.release();
        return false;
    }

    public void onConvoyDied() {
        if (isActive()) {
            fail("escort-failed-died");
        }
    }

    public void onWaveMobDied(final UUID entityId) {
        waveMobs.remove(entityId);
    }

    public void shutdown() {
        final UUID id = convoyId;
        convoyId = null;
        destination = null;
        expiresAt = 0L;
        totalDistance = 0.0D;
        schedule.release();
        hu.taliann.icesmp.utils.TransientEntities.removeById(plugin, id);
        clearWaves();
        hideBarFromAll();
    }

    private boolean start(final Player preferredAnchor) {
        if (preferredAnchor == null) {
            final EventSpawnPointManager pointsRef = spawnPointManager;
            final Location fixedAnchor = pointsRef == null
                    ? null : pointsRef.resolveAnchorLocation("escort");
            if (fixedAnchor != null) {
                spawnGuard.findSafeAtOrNear("escort", fixedAnchor,
                        System.nanoTime(), this::prepareRoute, schedule::release);
                return true;
            }
        }

        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            final List<Player> candidates = online.stream()
                    .filter(player -> online.size() == 1
                            || !player.getUniqueId().equals(lastAnchorId))
                    .toList();
            anchor = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            lastAnchorId = anchor.getUniqueId();
        }

        final Player target = anchor;
        target.getScheduler().run(plugin, task -> {
            final Location base = target.getLocation().clone();
            spawnGuard.findSafeNear("escort", base, System.nanoTime(),
                    this::prepareRoute, schedule::release);
        }, schedule::release);
        return true;
    }

    /** Start is already guarded; now validate every quarter of a complete route. */
    private void prepareRoute(final Location start) {
        final double distance = Math.max(40.0D,
                configManager.getDouble("escort.route-distance", 150.0D));
        spawnGuard.findSafeRoute("escort", start, distance, System.nanoTime(), dest -> {
            try {
                plugin.getServer().getRegionScheduler().run(plugin, start,
                        task -> spawnConvoyAt(start, dest));
            } catch (final RuntimeException unavailable) {
                schedule.release();
            }
        }, schedule::release);
    }

    private void spawnConvoyAt(final Location requestedStart, final Location dest) {
        final World world = requestedStart.getWorld();
        if (world == null || dest == null || dest.getWorld() != world) {
            schedule.release();
            return;
        }
        final Location start = spawnGuard.resolveSafeStandingLocation("escort", world,
                requestedStart.getBlockX(), requestedStart.getBlockZ());
        if (start == null || spawnGuard.isBlocked("escort", start)) {
            schedule.release();
            return;
        }

        final Llama convoy = world.spawn(start, Llama.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setTamed(true);
            spawned.setCarryingChest(true);
            spawned.customName(Component.text("🛡 Karaván-konvoj", NamedTextColor.GOLD));
            spawned.setCustomNameVisible(true);
            spawned.setGlowing(true);
            final double health = Math.max(10.0D,
                    configManager.getDouble("escort.convoy-health", 60.0D));
            final AttributeInstance maxHealth = spawned.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(health);
                spawned.setHealth(health);
            }
        });
        hu.taliann.icesmp.utils.TransientEntities.register(plugin, convoy);
        convoyId = convoy.getUniqueId();
        destination = dest.clone();
        totalDistance = Math.max(1.0D, horizontalDistance(start, dest));
        expiresAt = System.currentTimeMillis() + maxDurationMillis();
        schedule.release();

        startConvoyDriver(convoy);
        updateBar(convoy, totalDistance);
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> player.showBossBar(bar), null);
        }
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "escort-started",
                "&e🛡 KARAVÁN-KÍSÉRET! Egy konvoj indult útnak ({world}: {x}, {z} → {dx}, {dz}) — védd meg a szörnyektől, míg célba ér!",
                Map.of("world", world.getName(),
                        "x", String.valueOf(start.getBlockX()),
                        "z", String.valueOf(start.getBlockZ()),
                        "dx", String.valueOf(dest.getBlockX()),
                        "dz", String.valueOf(dest.getBlockZ()))));
    }

    private void startConvoyDriver(final Llama convoy) {
        final long periodTicks = 30L;
        final long waveEveryCycles = Math.max(1L,
                (configManager.getLong("escort.wave-interval-seconds", 45L) * 20L)
                        / periodTicks);
        final long[] cycle = {0L};
        final double[] lastDistance = {totalDistance};
        final int[] stuckCycles = {0};

        convoy.getScheduler().runAtFixedRate(plugin, task -> {
            if (!isConvoy(convoy.getUniqueId()) || !convoy.isValid()) {
                task.cancel();
                return;
            }
            final Location dest = destination;
            if (dest == null) {
                task.cancel();
                return;
            }
            final double remaining = horizontalDistance(convoy.getLocation(), dest);
            if (remaining <= 4.0D) {
                task.cancel();
                succeed(convoy);
                return;
            }

            convoy.getPathfinder().moveTo(nextLeg(convoy.getLocation(), dest), 1.1D);
            if (lastDistance[0] - remaining < 0.5D) {
                stuckCycles[0]++;
                if (stuckCycles[0] >= 4) {
                    stuckCycles[0] = 0;
                    final Location nudge = safeNudgeToward(convoy.getLocation(), dest);
                    if (nudge != null && !spawnGuard.isBlocked("escort-route", nudge)) {
                        convoy.teleportAsync(nudge);
                    }
                }
            } else {
                stuckCycles[0] = 0;
            }
            lastDistance[0] = remaining;

            updateBar(convoy, remaining);
            convoy.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    convoy.getLocation().clone().add(0.0D, 1.8D, 0.0D),
                    3, 0.3D, 0.3D, 0.3D, 0.0D);
            if (++cycle[0] % waveEveryCycles == 0L) {
                spawnWave(convoy);
            }
        }, null, periodTicks, periodTicks);
    }

    private Location nextLeg(final Location from, final Location dest) {
        final double remaining = Math.max(1.0D, horizontalDistance(from, dest));
        final double step = Math.min(24.0D, remaining);
        final double dx = (dest.getX() - from.getX()) / remaining * step;
        final double dz = (dest.getZ() - from.getZ()) / remaining * step;
        final int x = (int) Math.round(from.getX() + dx);
        final int z = (int) Math.round(from.getZ() + dz);
        final Location safe = spawnGuard.resolveSafeStandingLocation(
                "escort", from.getWorld(), x, z);
        return safe == null ? from.clone().add(dx, 0.0D, dz) : safe;
    }

    private Location safeNudgeToward(final Location from, final Location dest) {
        final double remaining = Math.max(1.0D, horizontalDistance(from, dest));
        final double step = Math.min(3.0D, remaining);
        final int x = (int) Math.round(from.getX()
                + (dest.getX() - from.getX()) / remaining * step);
        final int z = (int) Math.round(from.getZ()
                + (dest.getZ() - from.getZ()) / remaining * step);
        return spawnGuard.resolveSafeStandingLocation("escort", from.getWorld(), x, z);
    }

    private void spawnWave(final Llama convoy) {
        final World world = convoy.getWorld();
        final Location center = convoy.getLocation();
        final int count = Math.max(1, configManager.getInt("escort.wave-size", 4));
        final int level = Math.max(1, configManager.getInt("escort.wave-level", 3));
        waveMobs.removeIf(id -> !hu.taliann.icesmp.utils.TransientEntities.isAlive(id));

        for (int i = 0; i < count; i++) {
            final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            final double radius = 8.0D + ThreadLocalRandom.current().nextDouble(4.0D);
            final int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            final int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            final Location waveSpot = spawnGuard.resolveSafeStandingLocation(
                    "escort-wave", world, x, z);
            if (waveSpot == null || spawnGuard.isBlocked("escort-wave", waveSpot)) {
                continue;
            }
            final EntityType type = WAVE_POOL[ThreadLocalRandom.current().nextInt(WAVE_POOL.length)];
            final Class<? extends Entity> entityClass = type.getEntityClass();
            if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
                continue;
            }
            final Mob mob = (Mob) world.spawn(waveSpot, entityClass.asSubclass(Mob.class));
            EventSpawnGuard.prepare(mob);
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(false);
            mobScalingManager.forceLevel(mob, level);
            mob.setTarget(convoy);
            hu.taliann.icesmp.utils.TransientEntities.register(plugin, mob);
            waveMobs.add(mob.getUniqueId());
        }
        world.playSound(center, Sound.EVENT_RAID_HORN, 0.6F, 1.2F);
        world.spawnParticle(Particle.ANGRY_VILLAGER,
                center.clone().add(0.0D, 1.5D, 0.0D),
                10, 4.0D, 0.5D, 4.0D, 0.0D);
    }

    private void succeed(final Llama convoy) {
        if (claimSettlement() == null) {
            return;
        }
        final Location where = convoy.getLocation().clone();
        final World world = where.getWorld();
        destination = null;
        final int rolls = Math.max(1, configManager.getInt("escort.reward-rolls", 4));
        if (world != null) {
            for (final ItemStack loot : LootTable.roll(configManager, "escort.reward-loot", rolls)) {
                world.dropItemNaturally(where, loot);
            }
            world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                    where.clone().add(0.0D, 1.0D, 0.0D),
                    16, 0.6D, 0.7D, 0.6D, 0.1D);
            world.playSound(where, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        }
        convoy.remove();
        clearWaves();
        hideBarFromAll();
        final long bonusMinutes = Math.max(1L,
                configManager.getLong("escort.bonus-stock-minutes", 60L));
        bonusStockUntil = System.currentTimeMillis() + bonusMinutes * 60_000L;
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "escort-success",
                "&a🛡 A konvoj célba ért! A zsákmány a célnál vár, és a kereskedő-karaván &f{minutes} percig&a bővebb készlettel árul — köszönet a kísérőknek!",
                Map.of("minutes", String.valueOf(bonusMinutes))));
    }

    private void fail(final String messageKey) {
        final UUID id = claimSettlement();
        if (id == null) {
            return;
        }
        hu.taliann.icesmp.utils.TransientEntities.removeById(plugin, id);
        destination = null;
        clearWaves();
        hideBarFromAll();
        Bukkit.getServer().broadcast(messageManager.getMessage(
                messageKey, defaultFor(messageKey)));
    }

    private String defaultFor(final String messageKey) {
        return "escort-failed-died".equals(messageKey)
                ? "&c🛡 A konvoj odaveszett — a szörnyek széthordták a rakományt."
                : "&7🛡 A konvoj nem ért célba időben — a karaván lemondta a szállítmányt.";
    }

    private void updateBar(final Llama convoy, final double remaining) {
        final float fraction = (float) Math.max(0.0D,
                Math.min(1.0D, 1.0D - remaining / totalDistance));
        bar.progress(fraction);
        bar.name(Component.text(String.format("🛡 Karaván-kíséret — %d%% • HP %.0f",
                Math.round(fraction * 100.0F), convoy.getHealth()), NamedTextColor.GOLD));
    }

    private void hideBarFromAll() {
        if (!plugin.isEnabled()) {
            return;
        }
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            try {
                player.getScheduler().run(plugin, task -> player.hideBossBar(bar), null);
            } catch (final IllegalPluginAccessException exception) {
                return;
            }
        }
    }

    private void clearWaves() {
        for (final UUID id : waveMobs) {
            hu.taliann.icesmp.utils.TransientEntities.removeById(plugin, id);
        }
        waveMobs.clear();
    }

    private static double horizontalDistance(final Location first, final Location second) {
        final double dx = first.getX() - second.getX();
        final double dz = first.getZ() - second.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private long intervalMillis() {
        return Math.max(1L,
                configManager.getLong("escort.interval-minutes", 120L)) * 60_000L;
    }

    private long maxDurationMillis() {
        return Math.max(1L,
                configManager.getLong("escort.max-duration-minutes", 15L)) * 60_000L;
    }
}
