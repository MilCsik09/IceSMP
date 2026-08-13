package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.EventSpawnGuard;
import hu.taliann.icesmp.managers.MajorEventGate;
import hu.taliann.icesmp.utils.TransientEntities;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Reusable Gate Breach / finale wave / deterministic Prologue boss engine. */
public final class PrologueEncounterEngine implements Listener {
    private static final String EVENT_KEY = "prologue";

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PrologueWorldAccess worldAccess;
    private final PrologueParticipantTracker participants;
    private final EventSpawnGuard spawnGuard;
    private final MajorEventGate eventGate;
    private final NamespacedKey prologueMobKey;
    private final NamespacedKey encounterKey;
    private final NamespacedKey roleKey;
    private final Set<UUID> transientEntities = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, String> entityEncounters = new ConcurrentHashMap<>();
    private volatile ActiveEncounter activeEncounter;
    private volatile UUID bossId;
    private volatile boolean shuttingDown;

    public PrologueEncounterEngine(final JavaPlugin plugin, final ConfigManager config,
                                   final PrologueWorldAccess worldAccess,
                                   final PrologueParticipantTracker participants,
                                   final EventSpawnGuard spawnGuard,
                                   final MajorEventGate eventGate) {
        this.plugin = plugin;
        this.config = config;
        this.worldAccess = worldAccess;
        this.participants = participants;
        this.spawnGuard = spawnGuard;
        this.eventGate = eventGate;
        this.prologueMobKey = new NamespacedKey(plugin, "prologue_mob");
        this.encounterKey = new NamespacedKey(plugin, "prologue_encounter");
        this.roleKey = new NamespacedKey(plugin, "prologue_role");
    }

    public boolean isActive() {
        final ActiveEncounter encounter = activeEncounter;
        return encounter != null && !encounter.finished.get();
    }

    public boolean bossAlive() {
        final UUID id = bossId;
        return id != null && TransientEntities.isAlive(id);
    }

    public UUID bossId() { return bossId; }

    public boolean isPrologueEntity(final Entity entity) {
        return entity != null && entity.getPersistentDataContainer()
                .has(prologueMobKey, PersistentDataType.BYTE);
    }

    public boolean isBoss(final Entity entity) {
        return isPrologueEntity(entity) && "boss".equals(entity.getPersistentDataContainer()
                .get(roleKey, PersistentDataType.STRING));
    }

    public boolean startBreach(final BreachSeverity severity, final int participantCount,
                               final Runnable completion, final Consumer<String> failure) {
        if (severity == null || isActive()) return false;
        if (eventGate != null && !eventGate.mayStartNaturally(EVENT_KEY)) return false;
        return startBreachWave(severity, 1, participantCount,
                completion == null ? () -> { } : completion,
                failure == null ? ignored -> { } : failure);
    }

    private boolean startBreachWave(final BreachSeverity severity, final int wave,
                                    final int participantCount, final Runnable completion,
                                    final Consumer<String> failure) {
        final int waves = severity.waves();
        final boolean elite = wave == waves;
        final String id = "breach-" + severity.name().toLowerCase(Locale.ROOT) + '-' + wave;
        return startWave(id, severity, participantCount, elite, () -> {
            if (wave >= waves) {
                completion.run();
                return;
            }
            final long delayTicks = Math.max(1L, config.getLong(
                    "world-events.prologue.breach.wave-delay-seconds", 5L)) * 20L;
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task ->
                    startBreachWave(severity, wave + 1, participantCount, completion, failure), delayTicks);
        }, failure);
    }

    public boolean startWave(final String encounterId, final BreachSeverity severity,
                             final int participantCount, final boolean elite,
                             final Runnable completion, final Consumer<String> failure) {
        if (encounterId == null || encounterId.isBlank() || isActive()) return false;
        final Location anchor = worldAccess.breachAnchor();
        if (anchor == null || anchor.getWorld() == null) {
            if (failure != null) failure.accept("Nincs beállítva Prologue breach/gate anchor.");
            return false;
        }
        final int minimumPlayers = Math.max(1, config.getInt(
                "world-events.prologue.scaling.minimum-players", 5));
        final int maximumPlayers = Math.max(minimumPlayers, config.getInt(
                "world-events.prologue.scaling.maximum-players", 45));
        final int base = Math.max(1, config.getInt(
                "world-events.prologue.breach." + severity.name().toLowerCase(Locale.ROOT)
                        + ".base-count", switch (severity) {
                    case MINOR -> 4;
                    case MAJOR -> 7;
                    case CRITICAL -> 10;
                }));
        final double perPlayer = Math.max(0.0D, config.getDouble(
                "world-events.prologue.scaling.mob-per-extra-player", 0.40D));
        final int minCount = Math.max(1, config.getInt(
                "world-events.prologue.scaling.minimum-mob-count", 4));
        final int maxCount = Math.max(minCount, config.getInt(
                "world-events.prologue.scaling.maximum-mob-count", 28));
        final int count = PrologueScaling.mobCount(
                (int) Math.round(base * severity.mobMultiplier()), participantCount,
                minimumPlayers, maximumPlayers, perPlayer, minCount, maxCount);
        final ActiveEncounter encounter = new ActiveEncounter(encounterId,
                completion == null ? () -> { } : completion,
                failure == null ? ignored -> { } : failure,
                new AtomicInteger(count));
        activeEncounter = encounter;
        warningPulse(anchor, severity);
        final double radius = Math.max(3.0D, config.getDouble(
                "world-events.prologue.breach.spawn-radius", 10.0D));
        final List<EntityType> types = mobTypes("world-events.prologue.breach.mob-types",
                List.of(EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN,
                        EntityType.BLAZE, EntityType.WITHER_SKELETON));
        for (int index = 0; index < count; index++) {
            final int spawnIndex = index;
            final double angle = Math.PI * 2.0D * index / Math.max(1, count);
            final double ring = radius * (0.65D + 0.35D * ((index % 3) / 2.0D));
            final int x = anchor.getBlockX() + (int) Math.round(Math.cos(angle) * ring);
            final int z = anchor.getBlockZ() + (int) Math.round(Math.sin(angle) * ring);
            final EntityType type = types.get(index % types.size());
            plugin.getServer().getRegionScheduler().run(plugin,
                    new Location(anchor.getWorld(), x, anchor.getBlockY(), z), task -> {
                        final Location spawn = topOf(anchor.getWorld(), x, z);
                        spawnMob(encounter, spawn, type,
                                elite && spawnIndex == 0 ? "elite" : "wave");
                        encounter.pendingSpawns.decrementAndGet();
                        checkWaveComplete(encounter);
                    });
        }
        final long timeoutTicks = Math.max(30L, config.getLong(
                "world-events.prologue.breach.timeout-seconds", 300L)) * 20L;
        encounter.timeout = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (activeEncounter == encounter && !encounter.finished.get()) {
                failEncounter(encounter, "A breach időkorlátja lejárt.");
            }
        }, timeoutTicks);
        return true;
    }

    public boolean startBoss(final int participantCount, final Runnable victory,
                             final Consumer<String> failure) {
        if (isActive()) return false;
        final Location anchor = worldAccess.bossAnchor();
        if (anchor == null || anchor.getWorld() == null) {
            if (failure != null) failure.accept("Nincs beállítva Prologue boss/gate anchor.");
            return false;
        }
        final ActiveEncounter encounter = new ActiveEncounter("prologue-boss",
                victory == null ? () -> { } : victory,
                failure == null ? ignored -> { } : failure, new AtomicInteger(1));
        encounter.bossEncounter = true;
        activeEncounter = encounter;
        plugin.getServer().getRegionScheduler().run(plugin, anchor, task -> {
            anchor.getWorld().playSound(anchor, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.8F, 0.55F);
            anchor.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                    anchor.clone().add(0, 1.0D, 0), 60, 1.5D, 2.0D, 1.5D, 0.08D);
            final Mob boss = spawnMob(encounter,
                    topOf(anchor.getWorld(), anchor.getBlockX(), anchor.getBlockZ()),
                    bossType(), "boss");
            encounter.pendingSpawns.decrementAndGet();
            if (boss == null) {
                failEncounter(encounter, "A Prologue boss nem spawnolható a beállított anchoron.");
                return;
            }
            bossId = boss.getUniqueId();
            configureBoss(boss, participantCount, encounter);
        });
        final long timeoutTicks = Math.max(60L, config.getLong(
                "world-events.prologue.finale.boss.timeout-seconds", 900L)) * 20L;
        encounter.timeout = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (activeEncounter == encounter && !encounter.finished.get()) {
                failEncounter(encounter, "A Hasadék Őrének időkorlátja lejárt.");
            }
        }, timeoutTicks);
        return true;
    }

    private Mob spawnMob(final ActiveEncounter encounter, final Location spot,
                         final EntityType type, final String role) {
        if (spot == null || spot.getWorld() == null) return null;
        final Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) return null;
        if (spawnGuard != null && (spawnGuard.isBlocked(EVENT_KEY, spot)
                || spawnGuard.isUnsafeSurface(EVENT_KEY, spot.getWorld(), spot.getBlockX(), spot.getBlockZ()))) {
            return null;
        }
        final Mob mob = (Mob) spot.getWorld().spawn(spot, entityClass.asSubclass(Mob.class));
        EventSpawnGuard.prepare(mob);
        mob.getPersistentDataContainer().set(prologueMobKey, PersistentDataType.BYTE, (byte) 1);
        mob.getPersistentDataContainer().set(encounterKey, PersistentDataType.STRING, encounter.id);
        mob.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role);
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(false);
        mob.setGlowing("elite".equals(role) || "boss".equals(role));
        TransientEntities.register(plugin, mob);
        transientEntities.add(mob.getUniqueId());
        entityEncounters.put(mob.getUniqueId(), encounter.id);
        encounter.mobs.add(mob.getUniqueId());
        if ("elite".equals(role)) {
            final AttributeInstance health = mob.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(health.getBaseValue() * 1.6D);
                mob.setHealth(health.getValue());
            }
            final AttributeInstance attack = mob.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attack != null) attack.setBaseValue(attack.getBaseValue() * 1.25D);
            mob.customName(Component.text("Hasadékbajnok", NamedTextColor.DARK_RED));
            mob.setCustomNameVisible(true);
        }
        return mob;
    }

    private void configureBoss(final Mob boss, final int participantCount,
                               final ActiveEncounter encounter) {
        final int minimumPlayers = Math.max(1, config.getInt(
                "world-events.prologue.scaling.minimum-players", 5));
        final int maximumPlayers = Math.max(minimumPlayers, config.getInt(
                "world-events.prologue.scaling.maximum-players", 45));
        final double baseHealth = Math.max(40.0D, config.getDouble(
                "world-events.prologue.finale.boss.base-health", 500.0D));
        final double health = PrologueScaling.bossHealth(baseHealth, participantCount,
                minimumPlayers, maximumPlayers,
                Math.max(0.0D, config.getDouble(
                        "world-events.prologue.scaling.boss-health-per-extra-player", 0.075D)),
                Math.max(1.0D, config.getDouble(
                        "world-events.prologue.scaling.boss-health-maximum-multiplier", 4.0D)));
        final AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
            boss.setHealth(maxHealth.getValue());
        }
        final AttributeInstance damage = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            final double baseDamage = Math.max(4.0D, config.getDouble(
                    "world-events.prologue.finale.boss.base-attack-damage", 9.0D));
            final int effective = PrologueScaling.effectivePlayers(
                    participantCount, minimumPlayers, maximumPlayers);
            damage.setBaseValue(baseDamage * Math.min(1.8D,
                    1.0D + Math.max(0, effective - minimumPlayers) * 0.015D));
        }
        boss.customName(Component.text(config.getString(
                "world-events.prologue.finale.boss.name", "A Hasadék Őre"), NamedTextColor.DARK_RED));
        boss.setCustomNameVisible(true);
        boss.setGlowing(true);
        final AtomicBoolean addsTriggered = new AtomicBoolean(false);
        final AtomicBoolean enraged = new AtomicBoolean(false);
        final AtomicInteger mechanicTick = new AtomicInteger();
        boss.getScheduler().runAtFixedRate(plugin, task -> {
            if (!boss.isValid() || boss.isDead()) {
                task.cancel();
                if (!encounter.finished.get() && !shuttingDown) {
                    failEncounter(encounter, "A Prologue boss váratlanul eltűnt.");
                }
                return;
            }
            final AttributeInstance healthAttribute = boss.getAttribute(Attribute.MAX_HEALTH);
            final double maximum = Math.max(1.0D,
                    healthAttribute == null ? boss.getHealth() : healthAttribute.getValue());
            final double fraction = Math.max(0.0D, Math.min(1.0D, boss.getHealth() / maximum));
            final int tick = mechanicTick.incrementAndGet();
            if (fraction > 0.65D) {
                if (tick % 2 == 0) telegraphedSlam(boss);
            } else if (fraction > 0.30D) {
                if (addsTriggered.compareAndSet(false, true)) spawnBossAdds(boss, encounter, participantCount);
                if (tick % 2 == 0) telegraphedSlam(boss);
            } else {
                if (enraged.compareAndSet(false, true)) {
                    final AttributeInstance attack = boss.getAttribute(Attribute.ATTACK_DAMAGE);
                    if (attack != null) attack.setBaseValue(attack.getBaseValue() * 1.25D);
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.4F, 0.6F);
                }
                arenaHazard(boss);
            }
        }, () -> {
            if (!encounter.finished.get() && !shuttingDown) {
                failEncounter(encounter, "A Prologue boss entity schedulerje megszűnt.");
            }
        }, 60L, 80L);
    }

    private void telegraphedSlam(final Mob boss) {
        final Location center = boss.getLocation().clone();
        boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                center.clone().add(0, 1.0D, 0), 28, 2.8D, 0.5D, 2.8D, 0.03D);
        boss.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0F, 0.7F);
        boss.getScheduler().runDelayed(plugin, task -> {
            if (!boss.isValid() || boss.isDead()) return;
            final double damage = Math.max(1.0D, config.getDouble(
                    "world-events.prologue.finale.boss.slam-damage", 7.0D));
            boss.getWorld().spawnParticle(Particle.FLASH, center.clone().add(0, 1.0D, 0), 1);
            for (final Entity nearby : boss.getNearbyEntities(5.0D, 3.5D, 5.0D)) {
                if (nearby instanceof Player player) damagePlayer(player, damage, boss, center, 0.65D);
            }
        }, null, 24L);
    }

    private void arenaHazard(final Mob boss) {
        final Location center = boss.getLocation().clone();
        boss.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                center.clone().add(0, 0.4D, 0), 34, 4.5D, 0.4D, 4.5D, 0.12D);
        final double damage = Math.max(0.5D, config.getDouble(
                "world-events.prologue.finale.boss.hazard-damage", 3.0D));
        for (final Entity nearby : boss.getNearbyEntities(6.0D, 3.5D, 6.0D)) {
            if (nearby instanceof Player player) damagePlayer(player, damage, boss, center, 0.25D);
        }
    }

    private void damagePlayer(final Player player, final double amount, final Mob source,
                              final Location center, final double knockback) {
        final Runnable apply = () -> {
            if (!player.isOnline() || player.isDead()) return;
            player.damage(amount, source);
            final org.bukkit.util.Vector away = player.getLocation().toVector()
                    .subtract(center.toVector()).setY(0.25D);
            if (away.lengthSquared() > 0.01D) {
                player.setVelocity(player.getVelocity().add(
                        away.normalize().multiply(knockback).setY(0.25D)));
            }
        };
        if (Bukkit.isOwnedByCurrentRegion(player)) apply.run();
        else player.getScheduler().run(plugin, task -> apply.run(), null);
    }

    private void spawnBossAdds(final Mob boss, final ActiveEncounter encounter,
                               final int participantCount) {
        final int minimumPlayers = Math.max(1, config.getInt(
                "world-events.prologue.scaling.minimum-players", 5));
        final int maximumPlayers = Math.max(minimumPlayers, config.getInt(
                "world-events.prologue.scaling.maximum-players", 45));
        final int count = PrologueScaling.mobCount(3, participantCount,
                minimumPlayers, maximumPlayers, 0.16D, 3, 9);
        final List<EntityType> types = mobTypes("world-events.prologue.finale.boss.add-types",
                List.of(EntityType.PIGLIN_BRUTE, EntityType.BLAZE, EntityType.WITHER_SKELETON));
        final Location center = boss.getLocation().clone();
        encounter.pendingSpawns.addAndGet(count);
        for (int index = 0; index < count; index++) {
            final double angle = Math.PI * 2.0D * index / Math.max(1, count);
            final int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * 5.0D);
            final int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * 5.0D);
            final EntityType type = types.get(index % types.size());
            plugin.getServer().getRegionScheduler().run(plugin,
                    new Location(center.getWorld(), x, center.getBlockY(), z), task -> {
                        spawnMob(encounter, topOf(center.getWorld(), x, z), type, "add");
                        encounter.pendingSpawns.decrementAndGet();
                    });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        if (!isPrologueEntity(event.getEntity())) return;
        final Player attacker = playerAttacker(event.getDamager());
        if (attacker != null) {
            participants.recordDamage(attacker.getUniqueId(), event.getFinalDamage(), isBoss(event.getEntity()));
        }
    }

    @EventHandler
    public void onDeath(final EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        if (!isPrologueEntity(entity)) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        final UUID id = entity.getUniqueId();
        transientEntities.remove(id);
        TransientEntities.markGone(id);
        final String encounterId = entityEncounters.remove(id);
        final ActiveEncounter encounter = activeEncounter;
        if (encounter == null || encounterId == null || !encounter.id.equals(encounterId)) return;
        encounter.mobs.remove(id);
        if (id.equals(bossId)) {
            bossId = null;
            if (encounter.finished.compareAndSet(false, true)) {
                cancelTimeout(encounter);
                cleanupEncounterEntities(encounter);
                activeEncounter = null;
                encounter.completion.run();
            }
            return;
        }
        checkWaveComplete(encounter);
    }

    private void checkWaveComplete(final ActiveEncounter encounter) {
        if (encounter.bossEncounter || encounter.pendingSpawns.get() > 0 || !encounter.mobs.isEmpty()) return;
        if (encounter.finished.compareAndSet(false, true)) {
            cancelTimeout(encounter);
            activeEncounter = null;
            encounter.completion.run();
        }
    }

    public void abortActive(final String reason) {
        final ActiveEncounter encounter = activeEncounter;
        if (encounter != null) failEncounter(encounter,
                reason == null || reason.isBlank() ? "Az encounter megszakadt." : reason);
    }

    private void failEncounter(final ActiveEncounter encounter, final String reason) {
        if (!encounter.finished.compareAndSet(false, true)) return;
        cancelTimeout(encounter);
        cleanupEncounterEntities(encounter);
        if (activeEncounter == encounter) activeEncounter = null;
        bossId = null;
        encounter.failure.accept(reason);
    }

    private void cleanupEncounterEntities(final ActiveEncounter encounter) {
        for (final UUID id : Set.copyOf(encounter.mobs)) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.getScheduler().run(plugin, task -> {
                    if (entity.isValid()) entity.remove();
                    TransientEntities.markGone(id);
                }, () -> TransientEntities.markGone(id));
            }
            transientEntities.remove(id);
            entityEncounters.remove(id);
        }
        encounter.mobs.clear();
    }

    public void shutdown() {
        shuttingDown = true;
        final ActiveEncounter encounter = activeEncounter;
        if (encounter != null) {
            encounter.finished.set(true);
            cancelTimeout(encounter);
        }
        activeEncounter = null;
        bossId = null;
        TransientEntities.removeAllOnShutdown(transientEntities);
        transientEntities.clear();
        entityEncounters.clear();
    }

    private void warningPulse(final Location anchor, final BreachSeverity severity) {
        plugin.getServer().getRegionScheduler().run(plugin, anchor, task -> {
            if (anchor.getWorld() == null) return;
            anchor.getWorld().playSound(anchor, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                    severity == BreachSeverity.CRITICAL ? 1.8F : 1.2F,
                    severity == BreachSeverity.MINOR ? 0.9F : 0.6F);
            anchor.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                    anchor.clone().add(0.0D, 1.0D, 0.0D),
                    severity == BreachSeverity.CRITICAL ? 72 : 36,
                    1.5D, 2.0D, 1.5D, 0.08D);
        });
    }

    private Location topOf(final org.bukkit.World world, final int x, final int z) {
        return new Location(world, x + 0.5D, world.getHighestBlockYAt(x, z) + 1, z + 0.5D);
    }

    private EntityType bossType() {
        final String raw = config.getString(
                "world-events.prologue.finale.boss.entity-type", "WITHER_SKELETON");
        try {
            final EntityType type = EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            final Class<? extends Entity> typeClass = type.getEntityClass();
            return typeClass != null && Mob.class.isAssignableFrom(typeClass)
                    ? type : EntityType.WITHER_SKELETON;
        } catch (final IllegalArgumentException ignored) {
            return EntityType.WITHER_SKELETON;
        }
    }

    private List<EntityType> mobTypes(final String path, final List<EntityType> fallback) {
        final List<EntityType> parsed = new ArrayList<>();
        for (final String raw : config.getStringList(path)) {
            try {
                final EntityType type = EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                final Class<? extends Entity> typeClass = type.getEntityClass();
                if (typeClass != null && Mob.class.isAssignableFrom(typeClass)) parsed.add(type);
            } catch (final IllegalArgumentException ignored) { }
        }
        return parsed.isEmpty() ? fallback : List.copyOf(parsed);
    }

    private static Player playerAttacker(final Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private static void cancelTimeout(final ActiveEncounter encounter) {
        final ScheduledTask timeout = encounter.timeout;
        if (timeout != null) timeout.cancel();
    }

    private static final class ActiveEncounter {
        private final String id;
        private final Runnable completion;
        private final Consumer<String> failure;
        private final AtomicInteger pendingSpawns;
        private final Set<UUID> mobs = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private volatile ScheduledTask timeout;
        private volatile boolean bossEncounter;

        private ActiveEncounter(final String id, final Runnable completion,
                                final Consumer<String> failure, final AtomicInteger pendingSpawns) {
            this.id = id;
            this.completion = completion;
            this.failure = failure;
            this.pendingSpawns = pendingSpawns;
        }
    }
}
