package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.TransientEntities;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Invasion event with ownership-safe transient lifecycle tracking. */
public final class InvasionManager {

    private enum Horde {
        UNDEAD_TIDE("Élőhalott Áradat",
                new EntityType[]{EntityType.ZOMBIE, EntityType.HUSK}, EntityType.WITHER_SKELETON),
        BONE_LEGION("Csontlégió",
                new EntityType[]{EntityType.SKELETON, EntityType.STRAY}, EntityType.WITHER_SKELETON),
        SPIDER_NEST("Pókfészek",
                new EntityType[]{EntityType.SPIDER, EntityType.CAVE_SPIDER}, EntityType.SPIDER),
        CHAOS_HORDE("Káosz-horda",
                new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON,
                        EntityType.SPIDER, EntityType.PILLAGER}, EntityType.RAVAGER),
        NETHER_RAID("Alvilági Roham",
                new EntityType[]{EntityType.PIGLIN, EntityType.ZOMBIFIED_PIGLIN},
                EntityType.PIGLIN_BRUTE),
        ILLAGER_WARBAND("Zsivány Hadtest",
                new EntityType[]{EntityType.PILLAGER, EntityType.VINDICATOR}, EntityType.RAVAGER),
        WITCH_COVEN("Boszorkány Gyülekezet",
                new EntityType[]{EntityType.WITCH, EntityType.VEX}, EntityType.EVOKER),
        BLAZING_HOST("Lángoló Sereg",
                new EntityType[]{EntityType.BLAZE, EntityType.MAGMA_CUBE}, EntityType.PIGLIN_BRUTE);

        private final String displayName;
        private final EntityType[] pool;
        private final EntityType miniBoss;

        Horde(final String displayName, final EntityType[] pool,
              final EntityType miniBoss) {
            this.displayName = displayName;
            this.pool = pool;
            this.miniBoss = miniBoss;
        }

        private EntityType randomMob() {
            return pool[ThreadLocalRandom.current().nextInt(pool.length)];
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final MessageManager messageManager;
    private final Set<UUID> activeMobs = ConcurrentHashMap.newKeySet();

    private volatile long nextAttemptAt;
    private volatile long launchGraceUntil;
    private volatile EventSpawnGuard spawnGuard;
    private volatile SeasonFinaleManager seasonFinale;
    private volatile SeasonalModifierService seasonalModifiers;
    private volatile HolidayService holidayService;
    private volatile MajorEventGate eventGate;

    public InvasionManager(final JavaPlugin plugin,
                           final ConfigManager configManager,
                           final MobScalingManager mobScalingManager,
                           final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.messageManager = messageManager;
    }

    public void setSeasonFinale(final SeasonFinaleManager seasonFinale) {
        this.seasonFinale = seasonFinale;
    }

    public void setSeasonalModifiers(
            final SeasonalModifierService seasonalModifiers) {
        this.seasonalModifiers = seasonalModifiers;
    }

    public void setHolidayService(final HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    public void setEventGate(final MajorEventGate eventGate) {
        this.eventGate = eventGate;
    }

    public void setSpawnGuard(final EventSpawnGuard spawnGuard) {
        this.spawnGuard = spawnGuard;
    }

    public void tick() {
        if (!configManager.getBoolean("world-events.invasion.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextAttemptAt) {
            return;
        }
        nextAttemptAt = now + Math.max(1L, configManager.getLong(
                "world-events.invasion.check-interval-minutes", 75L)) * 60_000L;
        final MajorEventGate gate = eventGate;
        if (gate != null && !gate.mayStartNaturally("invasion")) {
            return;
        }
        final SeasonFinaleManager finale = seasonFinale;
        final SeasonalModifierService seasonal = seasonalModifiers;
        final HolidayService holiday = holidayService;
        final double finaleMult = finale == null ? 1.0D
                : finale.eventChanceMultiplier();
        final double seasonalMult = seasonal == null ? 1.0D
                : seasonal.chanceMultiplier("invasion");
        final String holidayRaw = holiday == null ? null
                : holiday.override("invasion-chance-mult");
        final double holidayMult = holidayRaw == null ? 1.0D
                : Math.max(0.0D, parseOr(holidayRaw, 1.0D));
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble(
                        "world-events.invasion.chance-percent", 30.0D)
                        * finaleMult * seasonalMult * holidayMult));
        if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
            launch(null);
        }
    }

    public boolean forceStart(final Player anchor) {
        return launch(anchor);
    }

    private synchronized boolean launch(final Player preferredAnchor) {
        final long now = System.currentTimeMillis();
        if (now < launchGraceUntil || isActive()) {
            return false;
        }
        Player target = preferredAnchor;
        if (target == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }
        launchGraceUntil = now + 15_000L;
        final Player anchor = target;
        anchor.getScheduler().run(plugin, task -> {
            final Location origin = anchor.getLocation().clone();
            final EventSpawnGuard guard = spawnGuard;
            if (guard == null) {
                launchGraceUntil = 0L;
                plugin.getLogger().warning("Invasion spawn aborted: EventSpawnGuard is unavailable.");
                return;
            }
            guard.findSafeNear("invasion", origin, now ^ anchor.getUniqueId().getMostSignificantBits(),
                    this::spawnWave, () -> launchGraceUntil = 0L);
        }, () -> launchGraceUntil = 0L);
        return true;
    }

    public boolean isInvasionMob(final UUID entityId) {
        return entityId != null && activeMobs.contains(entityId);
    }

    public void handleMobDeath(final UUID entityId) {
        if (entityId != null) {
            activeMobs.remove(entityId);
            TransientEntities.markGone(entityId);
        }
    }

    /** Pure UUID/atomic active query, safe from the global orchestration tick. */
    public boolean isActive() {
        activeMobs.removeIf(id -> !TransientEntities.isAlive(id));
        return !activeMobs.isEmpty();
    }

    public void shutdown() {
        nextAttemptAt = 0L;
        launchGraceUntil = 0L;
        TransientEntities.removeAllOnShutdown(activeMobs);
    }

    private void spawnWave(final Location center) {
        final org.bukkit.World world = center.getWorld();
        if (world == null) {
            launchGraceUntil = 0L;
            return;
        }
        final EventSpawnGuard guard = spawnGuard;
        if (guard != null && guard.isBlocked("invasion", center)) {
            launchGraceUntil = 0L;
            return;
        }
        activeMobs.removeIf(id -> !TransientEntities.isAlive(id));
        final Horde horde = Horde.values()[ThreadLocalRandom.current()
                .nextInt(Horde.values().length)];
        final int count = Math.max(1, configManager.getInt(
                "world-events.invasion.mob-count", 8));
        final SeasonFinaleManager finale = seasonFinale;
        final int finaleBonus = finale == null ? 0 : finale.bonusMobLevels();
        final int level = Math.max(1, configManager.getInt(
                "world-events.invasion.mob-level", 4)) + finaleBonus;
        final double radius = Math.max(2.0D, configManager.getDouble(
                "world-events.invasion.radius", 8.0D));

        for (int index = 0; index < count; index++) {
            final double angle = Math.PI * 2.0D * index / count;
            final int x = center.getBlockX()
                    + (int) Math.round(Math.cos(angle) * radius);
            final int z = center.getBlockZ()
                    + (int) Math.round(Math.sin(angle) * radius);
            final EntityType type = horde.randomMob();
            plugin.getServer().getRegionScheduler().run(
                    plugin, new Location(world, x, 0, z), task ->
                            spawnAt(topOf(world, x, z), type, level));
        }

        final int bossBonus = Math.max(0, configManager.getInt(
                "world-events.invasion.mini-boss-level-bonus", 6));
        final Mob champion = spawnAt(topOf(world, center.getBlockX(),
                center.getBlockZ()), horde.miniBoss, level + bossBonus);
        if (champion != null) {
            champion.customName(net.kyori.adventure.text.Component.text(
                    "☠ " + horde.displayName + " Bajnoka",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
            champion.setCustomNameVisible(true);
            startChampionTick(champion);
        }
        launchGraceUntil = 0L;
        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "invasion-started",
                        "<dark_red>⚔ INVÁZIÓ — {horde}! Egy szörnyhorda tört be a vidékre, élükön egy bajnokkal — vigyázz!</dark_red>",
                        Map.of("horde", horde.displayName))));
    }

    private Location topOf(final org.bukkit.World world, final int x, final int z) {
        return new Location(world, x + 0.5D,
                world.getHighestBlockYAt(x, z) + 1, z + 0.5D);
    }

    private Mob spawnAt(final Location spot, final EntityType type,
                        final int level) {
        final Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)
                || spot.getWorld() == null) {
            return null;
        }
        final EventSpawnGuard guard = spawnGuard;
        if (guard != null && (guard.isBlocked("invasion", spot)
                || guard.isUnsafeSurface("invasion", spot.getWorld(),
                spot.getBlockX(), spot.getBlockZ()))) {
            return null;
        }
        final Mob mob = (Mob) spot.getWorld().spawn(
                spot, entityClass.asSubclass(Mob.class));
        EventSpawnGuard.prepare(mob);
        mob.setGlowing(true);
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(false);
        mobScalingManager.forceLevel(mob, level);
        TransientEntities.register(plugin, mob);
        activeMobs.add(mob.getUniqueId());
        final long lifespanTicks = Math.max(0L, configManager.getLong(
                "world-events.invasion.mob-lifespan-seconds", 600L)) * 20L;
        if (lifespanTicks > 0L) {
            mob.getScheduler().runDelayed(plugin, task -> {
                if (mob.isValid()) {
                    mob.getWorld().spawnParticle(org.bukkit.Particle.POOF,
                            mob.getLocation().add(0.0D, 0.8D, 0.0D),
                            8, 0.3D, 0.4D, 0.3D, 0.01D);
                    mob.remove();
                }
                activeMobs.remove(mob.getUniqueId());
                TransientEntities.markGone(mob.getUniqueId());
            }, () -> {
                activeMobs.remove(mob.getUniqueId());
                TransientEntities.markGone(mob.getUniqueId());
            }, lifespanTicks);
        }
        return mob;
    }

    private void startChampionTick(final Mob champion) {
        final double damage = Math.max(1.0D, configManager.getDouble(
                "world-events.invasion.champion-slam-damage", 5.0D));
        champion.getScheduler().runAtFixedRate(plugin, task -> {
            if (!champion.isValid()) {
                task.cancel();
                return;
            }
            final Location center = champion.getLocation().clone();
            final org.bukkit.World world = champion.getWorld();
            world.spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER,
                    center.clone().add(0.0D, 1.0D, 0.0D),
                    24, 3.0D, 0.3D, 3.0D, 0.02D);
            world.playSound(center, org.bukkit.Sound.ENTITY_RAVAGER_ROAR,
                    1.2F, 0.8F);
            champion.getScheduler().runDelayed(plugin, delayed -> {
                if (!champion.isValid()) {
                    return;
                }
                hu.taliann.icesmp.utils.ParticleUtil.spawn(world,
                        org.bukkit.Particle.FLASH,
                        center.clone().add(0.0D, 1.0D, 0.0D), 1);
                for (final Entity nearby : champion.getNearbyEntities(
                        4.0D, 4.0D, 4.0D)) {
                    if (!(nearby instanceof Player player)) {
                        continue;
                    }
                    if (Bukkit.isOwnedByCurrentRegion(player)) {
                        if (isSurvivor(player)) {
                            player.damage(damage, champion);
                            applySlamKnockback(player, center);
                        }
                    } else {
                        player.getScheduler().run(plugin, playerTask -> {
                            if (isSurvivor(player)) {
                                player.damage(damage);
                                applySlamKnockback(player, center);
                            }
                        }, null);
                    }
                }
            }, null, 25L);
        }, null, 120L, 120L);
    }

    private static boolean isSurvivor(final Player player) {
        return player.getGameMode() == GameMode.SURVIVAL
                || player.getGameMode() == GameMode.ADVENTURE;
    }

    private static void applySlamKnockback(final Player player,
                                           final Location center) {
        final org.bukkit.util.Vector knockback = player.getLocation().toVector()
                .subtract(center.toVector());
        if (knockback.lengthSquared() > 0.0D) {
            player.setVelocity(knockback.normalize().setY(0.5D).multiply(0.7D));
        }
    }

    private static double parseOr(final String raw, final double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }
}
