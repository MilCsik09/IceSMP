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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Caravan escort world event (ROADMAP "élőbb világ", kooperatív harci ág): a
 * merchant convoy sets out toward a destination while monster waves harry it —
 * nearby players must keep the pack animal alive until it arrives. Success drops
 * a shared loot table at the destination and unlocks the caravan shop's bonus
 * stock for a while (raw items only, never currency); if the convoy dies or the
 * timer lapses, the goods are lost.
 *
 * <p>World-safety guarantees (server rule: events must never grief terrain): the
 * wave pool contains <em>no explosive mobs</em>, and as a second line of defence
 * the {@link hu.taliann.icesmp.listeners.EscortListener} strips block damage from
 * any explosion a wave mob might still cause. The convoy's movement/wave driver
 * runs on the convoy's own entity scheduler, which follows it across regions
 * (Folia-safe); the global tick only handles the schedule and the timeout.
 */
public final class EscortManager {

    /** Wave composition — deliberately no creepers/ghasts: nothing that can crater terrain. */
    private static final EntityType[] WAVE_POOL = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.PILLAGER,
            EntityType.VINDICATOR, EntityType.SPIDER
    };

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final MessageManager messageManager;

    private final BossBar bar = BossBar.bossBar(Component.empty(), 0.0F, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);

    private volatile UUID convoyId;
    /** Reserves the "active" state while a spawn is still hopping region threads. */
    private volatile long spawnGraceUntil;
    private volatile Location destination;
    private volatile double totalDistance;
    private volatile long expiresAt;
    private volatile long nextAttemptAt;
    /** Escort-success perk: the caravan shop's bonus stock stays unlocked until this time. */
    private volatile long bonusStockUntil;

    /** UUIDs of live wave mobs, pruned as they die; despawned on end/shutdown. */
    private final Set<UUID> waveMobs = ConcurrentHashMap.newKeySet();

    public EscortManager(final JavaPlugin plugin, final ConfigManager configManager,
                         final MobScalingManager mobScalingManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Whether an escort is currently under way. */
    public boolean isActive() {
        return convoyId != null;
    }

    /**
     * Atomically claims the right to settle the escort (success OR failure): only
     * the first caller wins — the convoy driver (region thread) and the timeout
     * check (global tick) can never both settle the same convoy.
     */
    private synchronized UUID claimSettlement() {
        final UUID id = convoyId;
        convoyId = null;
        return id;
    }

    /** Whether the given entity is the escorted convoy (for the death listener). */
    public boolean isConvoy(final UUID entityId) {
        return entityId != null && entityId.equals(convoyId);
    }

    /** Whether the given entity is an escort wave mob (for the explosion guard). */
    public boolean isWaveMob(final UUID entityId) {
        return entityId != null && waveMobs.contains(entityId);
    }

    /** Whether a recent escort success keeps the caravan's bonus stock unlocked. */
    public boolean isBonusStockActive() {
        return System.currentTimeMillis() < bonusStockUntil;
    }

    /** Shows the escort boss bar to a (joining) player if an escort is live. */
    public void showTo(final Player player) {
        if (isActive()) {
            player.showBossBar(bar);
        }
    }

    /** Periodic driver on the global world-events tick (schedule + timeout only). */
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
            } else if (!convoyValid()) {
                // Died without our listener catching it (e.g. void) — settle as lost.
                fail("escort-failed-died");
            }
            return;
        }
        if (now < spawnGraceUntil) {
            return; // A spawn is still hopping region threads.
        }

        if (now >= nextAttemptAt) {
            nextAttemptAt = now + intervalMillis();
            final double chance = Math.max(0.0D, Math.min(100.0D,
                    configManager.getDouble("escort.chance-percent", 30.0D)));
            if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
                start(null);
            }
        }
    }

    /** Admin override: starts an escort now near the anchor (or a random player). */
    public synchronized boolean forceStart(final Player anchor) {
        if (isActive() || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        return start(anchor);
    }

    /** Handles the convoy's death (from the death listener, on the convoy's region thread). */
    public void onConvoyDied() {
        if (isActive()) {
            fail("escort-failed-died");
        }
    }

    /** Prunes a dead wave mob from the tracked set (from the death listener). */
    public void onWaveMobDied(final UUID entityId) {
        waveMobs.remove(entityId);
    }

    /** Despawns the convoy and any live wave mobs on plugin disable. */
    public void shutdown() {
        removeEntity(convoyId);
        convoyId = null;
        clearWaves();
        hideBarFromAll();
    }

    private boolean start(final Player preferredAnchor) {
        // Reserve the active state while the spawn hops threads (self-heals after 10s),
        // so a second convoy can never start and orphan the first.
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }

        final Player target = anchor;
        target.getScheduler().run(plugin, task -> {
            final Location base = target.getLocation().clone();
            plugin.getServer().getRegionScheduler().run(plugin, base, spawnTask -> spawnConvoy(base));
        }, null);
        return true;
    }

    private void spawnConvoy(final Location origin) {
        final World world = origin.getWorld();
        if (world == null) {
            return;
        }

        // Route: from the anchor's spot toward a random compass direction, config distance away.
        final double distance = Math.max(40.0D, configManager.getDouble("escort.route-distance", 150.0D));
        final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
        final int startX = origin.getBlockX();
        final int startZ = origin.getBlockZ();
        final int destX = startX + (int) Math.round(Math.cos(angle) * distance);
        final int destZ = startZ + (int) Math.round(Math.sin(angle) * distance);

        final Location start = topOf(world, startX, startZ);
        final Location dest = new Location(world, destX + 0.5D, 0.0D, destZ + 0.5D);

        final Llama convoy = world.spawn(start, Llama.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setTamed(true);
            spawned.setCarryingChest(true);
            spawned.customName(Component.text("🛡 Karaván-konvoj", NamedTextColor.GOLD));
            spawned.setCustomNameVisible(true);
            spawned.setGlowing(true);
            final double health = Math.max(10.0D, configManager.getDouble("escort.convoy-health", 60.0D));
            final AttributeInstance maxHealth = spawned.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(health);
                spawned.setHealth(health);
            }
        });

        convoyId = convoy.getUniqueId();
        destination = dest;
        totalDistance = Math.max(1.0D, horizontalDistance(start, dest));
        expiresAt = System.currentTimeMillis() + maxDurationMillis();
        spawnGraceUntil = 0L;

        startConvoyDriver(convoy);
        updateBar(convoy, totalDistance);
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> player.showBossBar(bar), null);
        }

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "escort-started",
                "&e🛡 KARAVÁN-KÍSÉRET! Egy konvoj indult útnak ({world}: {x}, {z} → {dx}, {dz}) — védd meg a szörnyektől, míg célba ér!",
                Map.of(
                        "world", world.getName(),
                        "x", String.valueOf(startX),
                        "z", String.valueOf(startZ),
                        "dx", String.valueOf(destX),
                        "dz", String.valueOf(destZ)
                )
        ));
    }

    /**
     * The convoy's own driver: pathfind toward the destination, spawn harassing
     * waves, refresh the boss bar and detect arrival. Runs on the convoy's entity
     * scheduler, which follows it across regions (Folia-safe); auto-cancels when
     * the escort ends.
     */
    private void startConvoyDriver(final Llama convoy) {
        final long periodTicks = 30L;
        final long waveEveryCycles = Math.max(1L,
                (configManager.getLong("escort.wave-interval-seconds", 45L) * 20L) / periodTicks);
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

            // Keep walking toward the goal; if terrain pinned us for several cycles, nudge forward.
            convoy.getPathfinder().moveTo(nextLeg(convoy.getLocation(), dest), 1.1D);
            if (lastDistance[0] - remaining < 0.5D) {
                stuckCycles[0]++;
                if (stuckCycles[0] >= 4) {
                    stuckCycles[0] = 0;
                    convoy.teleportAsync(nudgeToward(convoy.getLocation(), dest));
                }
            } else {
                stuckCycles[0] = 0;
            }
            lastDistance[0] = remaining;

            updateBar(convoy, remaining);
            convoy.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    convoy.getLocation().clone().add(0.0D, 1.8D, 0.0D), 3, 0.3D, 0.3D, 0.3D, 0.0D);

            if (++cycle[0] % waveEveryCycles == 0L) {
                spawnWave(convoy);
            }
        }, null, periodTicks, periodTicks);
    }

    /** A short pathfinding leg toward the destination (the full route may exceed path range). */
    private Location nextLeg(final Location from, final Location dest) {
        final double remaining = horizontalDistance(from, dest);
        final double step = Math.min(24.0D, remaining);
        final double dx = (dest.getX() - from.getX()) / remaining * step;
        final double dz = (dest.getZ() - from.getZ()) / remaining * step;
        final World world = from.getWorld();
        final int x = (int) Math.round(from.getX() + dx);
        final int z = (int) Math.round(from.getZ() + dz);
        return topOf(world, x, z);
    }

    /** A small forward teleport for when the pathfinder is wedged against terrain. */
    private Location nudgeToward(final Location from, final Location dest) {
        final double remaining = Math.max(1.0D, horizontalDistance(from, dest));
        final double step = Math.min(3.0D, remaining);
        final int x = (int) Math.round(from.getX() + (dest.getX() - from.getX()) / remaining * step);
        final int z = (int) Math.round(from.getZ() + (dest.getZ() - from.getZ()) / remaining * step);
        return topOf(from.getWorld(), x, z);
    }

    /** Spawns a harassing wave around the convoy, aggroed onto it. Runs on the convoy's region. */
    private void spawnWave(final Llama convoy) {
        final World world = convoy.getWorld();
        final Location center = convoy.getLocation();
        final int count = Math.max(1, configManager.getInt("escort.wave-size", 4));
        final int level = Math.max(1, configManager.getInt("escort.wave-level", 3));

        // Bounded tracking: drop entries whose mobs are already gone.
        waveMobs.removeIf(id -> {
            final Entity existing = Bukkit.getEntity(id);
            return existing == null || !existing.isValid();
        });

        for (int i = 0; i < count; i++) {
            final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            final double radius = 8.0D + ThreadLocalRandom.current().nextDouble(4.0D);
            final int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            final int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            final EntityType type = WAVE_POOL[ThreadLocalRandom.current().nextInt(WAVE_POOL.length)];
            final Class<? extends Entity> entityClass = type.getEntityClass();
            if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
                continue;
            }
            final Mob mob = (Mob) world.spawn(topOf(world, x, z), entityClass.asSubclass(Mob.class));
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(false);
            mobScalingManager.forceLevel(mob, level);
            mob.setTarget(convoy);
            waveMobs.add(mob.getUniqueId());
        }

        world.playSound(center, Sound.EVENT_RAID_HORN, 0.6F, 1.2F);
        world.spawnParticle(Particle.ANGRY_VILLAGER, center.clone().add(0.0D, 1.5D, 0.0D), 10, 4.0D, 0.5D, 4.0D, 0.0D);
    }

    /** The convoy arrived: shared loot at the goal + the caravan's bonus stock unlocks. */
    private void succeed(final Llama convoy) {
        if (claimSettlement() == null) {
            return; // The timeout tick won the race and already settled as failure.
        }
        final Location where = convoy.getLocation().clone();
        final World world = where.getWorld();
        destination = null;

        final int rolls = Math.max(1, configManager.getInt("escort.reward-rolls", 4));
        if (world != null) {
            for (final ItemStack loot : LootTable.roll(configManager, "escort.reward-loot", rolls)) {
                world.dropItemNaturally(where, loot);
            }
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, where.clone().add(0.0D, 1.0D, 0.0D), 40, 0.8D, 0.8D, 0.8D, 0.1D);
            world.playSound(where, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        }
        convoy.remove();
        clearWaves();
        hideBarFromAll();

        final long bonusMinutes = Math.max(1L, configManager.getLong("escort.bonus-stock-minutes", 60L));
        bonusStockUntil = System.currentTimeMillis() + bonusMinutes * 60_000L;

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "escort-success",
                "&a🛡 A konvoj célba ért! A zsákmány a célnál vár, és a kereskedő-karaván &f{minutes} percig&a bővebb készlettel árul — köszönet a kísérőknek!",
                Map.of("minutes", String.valueOf(bonusMinutes))
        ));
    }

    private void fail(final String messageKey) {
        final UUID id = claimSettlement();
        if (id == null) {
            return; // The driver already settled it (success) — no contradictory broadcast.
        }
        removeEntity(id);
        destination = null;
        clearWaves();
        hideBarFromAll();
        Bukkit.getServer().broadcast(messageManager.get(messageKey, defaultFor(messageKey)));
    }

    private String defaultFor(final String messageKey) {
        return "escort-failed-died".equals(messageKey)
                ? "&c🛡 A konvoj odaveszett — a szörnyek széthordták a rakományt."
                : "&7🛡 A konvoj nem ért célba időben — a karaván lemondta a szállítmányt.";
    }

    private void updateBar(final Llama convoy, final double remaining) {
        final float fraction = (float) Math.max(0.0D, Math.min(1.0D, 1.0D - remaining / totalDistance));
        bar.progress(fraction);
        bar.name(Component.text(String.format("🛡 Karaván-kíséret — %d%% • HP %.0f",
                Math.round(fraction * 100.0F), convoy.getHealth()), NamedTextColor.GOLD));
    }

    private void hideBarFromAll() {
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> player.hideBossBar(bar), null);
        }
    }

    private void clearWaves() {
        for (final UUID id : waveMobs) {
            removeEntity(id);
        }
        waveMobs.clear();
    }

    /** Best-effort, Folia-safe removal on the entity's own region thread. */
    private void removeEntity(final UUID id) {
        if (id == null) {
            return;
        }
        try {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                entity.getScheduler().run(plugin, task -> entity.remove(), null);
            }
        } catch (final Exception ignored) {
            // Region/scheduler unavailable (shutdown) — non-persistent entities don't survive a restart.
        }
    }

    private boolean convoyValid() {
        final UUID id = convoyId;
        if (id == null) {
            return false;
        }
        try {
            final Entity entity = Bukkit.getEntity(id);
            return entity != null && entity.isValid();
        } catch (final Exception exception) {
            return true; // Region unavailable — assume alive rather than failing the escort.
        }
    }

    private static double horizontalDistance(final Location a, final Location b) {
        final double dx = a.getX() - b.getX();
        final double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Location topOf(final World world, final int x, final int z) {
        final int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("escort.interval-minutes", 120L)) * 60_000L;
    }

    private long maxDurationMillis() {
        return Math.max(1L, configManager.getLong("escort.max-duration-minutes", 15L)) * 60_000L;
    }
}
