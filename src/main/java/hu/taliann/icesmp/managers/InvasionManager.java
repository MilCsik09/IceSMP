package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Invasion events: periodically (or on admin command) a wave
 * of scaled monsters spawns around a random player — a dangerous swarm that
 * rewards via the normal scaled-mob XP and soulstone drops. The spawn runs on
 * the target region's thread (Folia-safe).
 */
public final class InvasionManager {

    /**
     * Horde compositions. Each wave picks one at random; its regular mobs are drawn from the
     * {@code pool}, and a single scaled {@code miniBoss} (a tougher, named champion) leads it.
     */
    private enum Horde {
        UNDEAD_TIDE("Élőhalott Áradat",
                new EntityType[]{EntityType.ZOMBIE, EntityType.HUSK}, EntityType.WITHER_SKELETON),
        BONE_LEGION("Csontlégió",
                new EntityType[]{EntityType.SKELETON, EntityType.STRAY}, EntityType.WITHER_SKELETON),
        SPIDER_NEST("Pókfészek",
                new EntityType[]{EntityType.SPIDER, EntityType.CAVE_SPIDER}, EntityType.SPIDER),
        CHAOS_HORDE("Káosz-horda",
                new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.PILLAGER},
                EntityType.RAVAGER),
        NETHER_RAID("Alvilági Roham",
                new EntityType[]{EntityType.PIGLIN, EntityType.ZOMBIFIED_PIGLIN}, EntityType.PIGLIN_BRUTE),
        ILLAGER_WARBAND("Zsivány Hadtest",
                new EntityType[]{EntityType.PILLAGER, EntityType.VINDICATOR}, EntityType.RAVAGER),
        WITCH_COVEN("Boszorkány Gyülekezet",
                new EntityType[]{EntityType.WITCH, EntityType.VEX}, EntityType.EVOKER),
        BLAZING_HOST("Lángoló Sereg",
                new EntityType[]{EntityType.BLAZE, EntityType.MAGMA_CUBE}, EntityType.PIGLIN_BRUTE);

        private final String displayName;
        private final EntityType[] pool;
        private final EntityType miniBoss;

        Horde(final String displayName, final EntityType[] pool, final EntityType miniBoss) {
            this.displayName = displayName;
            this.pool = pool;
            this.miniBoss = miniBoss;
        }

        EntityType randomMob() {
            return pool[ThreadLocalRandom.current().nextInt(pool.length)];
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final MessageManager messageManager;

    private volatile long nextAttemptAt;
    /** Rövid indítási türelem: két egyidejű indítás (tick + admin) nem torlódhat egymásra. */
    private volatile long launchGraceUntil;
    /** UUIDs of currently-spawned invasion mobs, pruned each wave; despawned on shutdown. */
    private final Set<UUID> activeMobs = ConcurrentHashMap.newKeySet();
    /** Setter-injected (constructed later in the DI order); null = no placement restriction. */
    private volatile EventSpawnGuard spawnGuard;
    /** B33: setter-injected finálé-eszkaláció (null = nincs finálé-bónusz). */
    private volatile SeasonFinaleManager seasonFinale;

    /** B33: a szezonzáró-eszkaláció bekötése (a finálé-manager később épül a DI-sorrendben). */
    public void setSeasonFinale(final SeasonFinaleManager seasonFinale) {
        this.seasonFinale = seasonFinale;
    }

    /** B19: az évszak-szorzó bekötése. */
    private volatile SeasonalModifierService seasonalModifiers;

    public void setSeasonalModifiers(final SeasonalModifierService seasonalModifiers) {
        this.seasonalModifiers = seasonalModifiers;
    }

    /** D1 — ünnep-hook (setterrel kötve; null = nincs ünnep-szorzó). */
    private volatile HolidayService holidayService;

    public void setHolidayService(final HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    private static double parseOr(final String raw, final double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }


    /** Orchestráció-kapu (setterrel kötve; null = nincs kapuzás). */
    private volatile MajorEventGate eventGate;

    public void setEventGate(final MajorEventGate eventGate) {
        this.eventGate = eventGate;
    }

    public InvasionManager(final JavaPlugin plugin, final ConfigManager configManager,
                           final MobScalingManager mobScalingManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.messageManager = messageManager;
    }

    /** Wires the shared spawn-placement guard (built after this manager in the DI order). */
    public void setSpawnGuard(final EventSpawnGuard spawnGuard) {
        this.spawnGuard = spawnGuard;
    }

    /** Periodic attempt on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("world-events.invasion.enabled", true)) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now < nextAttemptAt) {
            return;
        }
        final long intervalMinutes = Math.max(1L, configManager.getLong("world-events.invasion.check-interval-minutes", 75L));
        nextAttemptAt = now + (intervalMinutes * 60_000L);

        // Orchestráció: ha másik nagy PvE-esemény fut, ez a természetes sorsolás kimarad.
        final MajorEventGate gateRef = eventGate;
        if (gateRef != null && !gateRef.mayStartNaturally("invasion")) {
            return;
        }
        // A végítélet-hét alatt sűrűbb és erősebb az invázió (napi eszkaláció);
        // évszak-szorzó (season-modifiers.<evszak>.invasion).
        final SeasonFinaleManager finaleRef = seasonFinale;
        final double finaleMult = finaleRef == null ? 1.0D : finaleRef.eventChanceMultiplier();
        final SeasonalModifierService seasonalRef = seasonalModifiers;
        final double seasonalMult = seasonalRef == null ? 1.0D : seasonalRef.chanceMultiplier("invasion");
        // Ünnep-felülbírálás (pl. Rém-éj: sűrűbb invázió). A halott hook életre kelt.
        final HolidayService holidayRef = holidayService;
        final String holidayMult = holidayRef == null ? null : holidayRef.override("invasion-chance-mult");
        final double holidayFactor = holidayMult == null ? 1.0D : Math.max(0.0D, parseOr(holidayMult, 1.0D));
        final double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("world-events.invasion.chance-percent", 30.0D)
                        * finaleMult * seasonalMult * holidayFactor));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        launch(null);
    }

    /**
     * Admin override: launches an invasion near the given anchor (or a random
     * online player if null).
     *
     * @param anchor preferred anchor (issuing admin), may be null
     * @return true if an invasion was launched
     */
    public boolean forceStart(final Player anchor) {
        return launch(anchor);
    }

    /**
     * Az egyetlen indítási út (tick + admin): synchronized + indítási türelem, hogy két
     * egyidejű hívás (vagy egy még élő hullám mellett érkező) ne torlódhasson egymásra.
     */
    private synchronized boolean launch(final Player anchor) {
        final long now = System.currentTimeMillis();
        if (now < launchGraceUntil || isActive()) {
            return false;
        }
        Player target = anchor;
        if (target == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }
        launchGraceUntil = now + 15_000L;
        triggerNear(target);
        return true;
    }

    private void triggerNear(final Player anchor) {
        // Folia: read the anchor's location on its OWN region thread first, then hop to that
        // location's region to spawn (the caller may run on the global or another region thread).
        anchor.getScheduler().run(plugin, task -> {
            final Location center = anchor.getLocation().clone();
            plugin.getServer().getRegionScheduler().run(plugin, center, spawnTask -> spawnWave(center));
        }, null);
    }

    /** Whether the entity is a live invasion-horde mob (killing one must never count as sin). */
    public boolean isInvasionMob(final UUID entityId) {
        return entityId != null && activeMobs.contains(entityId);
    }

    /** A horda-mob halálakor hívandó (MobLootListener) — az aktív-jelzés így nem ragad be. */
    public void handleMobDeath(final UUID entityId) {
        if (entityId != null) {
            activeMobs.remove(entityId);
        }
    }

    /**
     * Whether an invasion wave is currently under way (any of its mobs are still
     * tracked as alive). No expiry timestamp is kept for invasions — the wave simply
     * lasts until its mobs are all killed — so only presence, not remaining time, is
     * exposed here. A halott/eltűnt (pl. despawnolt vagy /kill-elt) mobokat lekérdezéskor
     * is kiszórjuk, hogy az állapot ne mutasson hamisan aktív inváziót.
     *
     * @return true while at least one wave mob is tracked as alive
     */
    public boolean isActive() {
        if (activeMobs.isEmpty()) {
            return false;
        }
        activeMobs.removeIf(id -> {
            try {
                final Entity existing = Bukkit.getEntity(id);
                return existing == null || !existing.isValid();
            } catch (final Exception exception) {
                return false; // Régió nem elérhető erről a szálról — döntsön a következő hívás.
            }
        });
        return !activeMobs.isEmpty();
    }

    public void shutdown() {
        nextAttemptAt = 0L;
        for (final UUID id : activeMobs) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                try {
                    entity.remove();
                } catch (final Exception ignored) {
                    // Region/thread unavailable during shutdown — leave it.
                }
            }
        }
        activeMobs.clear();
    }

    private void spawnWave(final Location center) {
        if (center.getWorld() == null) {
            return;
        }

        // Never launch an invasion inside a town/claim/WG region — the anchor player may be
        // standing in a protected city (config: world-events.spawn-rules.invasion). Silent
        // skip; the next interval picks a new anchor.
        final EventSpawnGuard guard = spawnGuard;
        if (guard != null && guard.isBlocked("invasion", center)) {
            return;
        }

        // Keep the tracked set bounded: drop UUIDs whose mobs are already dead/gone.
        activeMobs.removeIf(id -> {
            try {
                final Entity existing = Bukkit.getEntity(id);
                return existing == null || !existing.isValid();
            } catch (final Exception exception) {
                return false;
            }
        });

        // Pick a random horde composition for this wave (variety).
        final Horde horde = Horde.values()[ThreadLocalRandom.current().nextInt(Horde.values().length)];
        final int count = Math.max(1, configManager.getInt("world-events.invasion.mob-count", 8));
        // A végítélet-hét napi mob-szint bónusza (0, ha nincs finálé).
        final SeasonFinaleManager finaleRef = seasonFinale;
        final int finaleBonus = finaleRef == null ? 0 : finaleRef.bonusMobLevels();
        final int level = Math.max(1, configManager.getInt("world-events.invasion.mob-level", 4)) + finaleBonus;
        final double radius = Math.max(2.0D, configManager.getDouble("world-events.invasion.radius", 8.0D));

        // Folia: a gyűrű szélső tagjai (radius ~8 blokk) régióhatár közelében MÁSIK régió
        // chunkjaira eshetnek — minden gyűrű-pont a SAJÁT hely-régió-schedulerén spawnol
        // (a getHighestBlockYAt is csak ott biztonságos). Minta: CorruptionManager.spreadAndSpawn.
        final org.bukkit.World world = center.getWorld();
        for (int i = 0; i < count; i++) {
            final double angle = (Math.PI * 2.0D / count) * i;
            final int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            final int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            final EntityType member = horde.randomMob();
            plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, 0, z),
                    task -> spawnAt(topOf(world, x, z), member, level));
        }

        // The horde's champion: a tougher, named mini-boss at the centre (final-wave feel).
        // A center régió-szálán vagyunk — a bajnok spawnja itt közvetlen.
        final int bossBonus = Math.max(0, configManager.getInt("world-events.invasion.mini-boss-level-bonus", 6));
        final Mob champion = spawnAt(topOf(world, center.getBlockX(), center.getBlockZ()),
                horde.miniBoss, level + bossBonus);
        if (champion != null) {
            champion.customName(net.kyori.adventure.text.Component.text(
                    "☠ " + horde.displayName + " Bajnoka",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
            champion.setCustomNameVisible(true);
            startChampionTick(champion);
        }

        // A hullám elindult (a gyűrű-tagok aszinkron érkeznek; a per-pont guard legfeljebb
        // ritkítja a szélét) — a kihirdetés a top-szintű guard-on átjutott indításhoz kötött.
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "invasion-started",
                "<dark_red>⚔ INVÁZIÓ — {horde}! Egy szörnyhorda tört be a vidékre, élükön egy bajnokkal — vigyázz!</dark_red>",
                Map.of("horde", horde.displayName)
        ));
    }

    /** Highest safe spawn spot above the given column. */
    private Location topOf(final org.bukkit.World world, final int x, final int z) {
        final int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    /** Spawns a glowing, level-scaled invasion mob; returns null if the type is not a mob. */
    private Mob spawnAt(final Location spot, final EntityType type, final int level) {
        final Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
            return null;
        }
        // Per-spot rules: the wave ring can straddle a town border or reach over water — those
        // members are simply skipped (the wave stays a wave, just thinner at the edge).
        final EventSpawnGuard guard = spawnGuard;
        if (guard != null && (guard.isBlocked("invasion", spot)
                || guard.isUnsafeSurface("invasion", spot.getWorld(), spot.getBlockX(), spot.getBlockZ()))) {
            return null;
        }
        final Mob mob = (Mob) spot.getWorld().spawn(spot, entityClass.asSubclass(Mob.class));
        // No overworld zombification (a conversion would spawn a NEW entity outside activeMobs,
        // so killing it would wrongly count as sin) / no daylight burn for undead hordes.
        EventSpawnGuard.prepare(mob);
        mob.setGlowing(true);
        mob.setRemoveWhenFarAway(false);
        mobScalingManager.forceLevel(mob, level);
        activeMobs.add(mob.getUniqueId());
        // Élettartam (a túlélő pók nappal megszelídül és bevándorol a
        // városba): a le nem ölt horda-mob lejáratkor köddé válik — a saját entity-
        // schedulerén, ami a halálakor magától nyugdíjazódik. 0 = örök (régi viselkedés).
        final long lifespanTicks = Math.max(0L,
                configManager.getLong("world-events.invasion.mob-lifespan-seconds", 600L)) * 20L;
        if (lifespanTicks > 0L) {
            mob.getScheduler().runDelayed(plugin, task -> {
                if (mob.isValid()) {
                    mob.getWorld().spawnParticle(org.bukkit.Particle.POOF,
                            mob.getLocation().add(0.0D, 0.8D, 0.0D), 8, 0.3D, 0.4D, 0.3D, 0.01D);
                    mob.remove();
                }
            }, null, lifespanTicks);
        }
        return mob;
    }

    /**
     * Gives the horde champion a telegraphed ground-slam every ~6s so it plays like a mini-boss, not
     * just a tanky mob. Runs on the champion's own region scheduler (Folia-safe; touched players are
     * region-local nearby), and auto-cancels when the champion dies.
     */
    private void startChampionTick(final Mob champion) {
        final double damage = Math.max(1.0D, configManager.getDouble("world-events.invasion.champion-slam-damage", 5.0D));
        champion.getScheduler().runAtFixedRate(plugin, task -> {
            if (!champion.isValid()) {
                task.cancel();
                return;
            }
            final Location center = champion.getLocation().clone();
            final org.bukkit.World world = champion.getWorld();
            world.spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER, center.clone().add(0.0D, 1.0D, 0.0D), 24, 3.0D, 0.3D, 3.0D, 0.02D);
            world.playSound(center, org.bukkit.Sound.ENTITY_RAVAGER_ROAR, 1.2F, 0.8F);
            champion.getScheduler().runDelayed(plugin, t -> {
                if (!champion.isValid()) {
                    return;
                }
                hu.taliann.icesmp.utils.ParticleUtil.spawn(world, org.bukkit.Particle.FLASH, center.clone().add(0.0D, 1.0D, 0.0D), 1);
                // Folia: a nearby player can belong to a neighbouring region — touch them directly
                // only when we own them, otherwise hop to their scheduler (damager omitted
                // cross-region). Same pattern as WorldBossManager.fireSpecial.
                for (final Entity nearby : champion.getNearbyEntities(4.0D, 4.0D, 4.0D)) {
                    if (nearby instanceof Player player) {
                        if (Bukkit.isOwnedByCurrentRegion(player)) {
                            if (isSurvivor(player)) {
                                player.damage(damage, champion);
                                applySlamKnockback(player, center);
                            }
                        } else {
                            player.getScheduler().run(plugin, t2 -> {
                                if (isSurvivor(player)) {
                                    player.damage(damage);
                                    applySlamKnockback(player, center);
                                }
                            }, null);
                        }
                    }
                }
            }, null, 25L);
        }, null, 120L, 120L);
    }

    /** A survivor (survival/adventure) — never hit creative or spectator players. */
    private static boolean isSurvivor(final Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    /** Knocks the player away from the slam center (runs on the player's own region thread). */
    private static void applySlamKnockback(final Player player, final Location center) {
        final org.bukkit.util.Vector kb = player.getLocation().toVector().subtract(center.toVector());
        if (kb.lengthSquared() > 0.0D) {
            player.setVelocity(kb.normalize().setY(0.5D).multiply(0.7D));
        }
    }
}
