package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * World bosses: periodically a boss-grade guardian
 * spawns near a random adventurer. Slaying it rewards the killer's faction
 * treasury, grants league points and buffs the slayer. The spawn attempt is
 * rolled on the global world-events tick, but the actual entity spawn runs on
 * the owning region's scheduler (Folia-correct); the despawn timer uses the
 * boss's per-entity scheduler.
 */
public final class WorldBossManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private final FactionTreasuryManager treasuryManager;
    private final SeasonManager seasonManager;
    private final NamespacedKey worldBossKey;
    private final NamespacedKey bossArchetypeKey;
    /** B33: a szezonzáró boss jelölője (halálakor egyedi loot-tábla gurul). */
    private final NamespacedKey finaleBossKey;

    /**
     * The boss roster. Each archetype is a distinct mob with its own theme, stat
     * multipliers (layered on the configured base health/damage), a self-buff applied
     * at spawn, a signature aura debuff periodically applied to nearby players, and a
     * reward multiplier. One is chosen at random on every spawn (rotation/variety).
     */
    private enum BossArchetype {
        RING_WARDEN(EntityType.RAVAGER, "&4&l☠ A Gyűrűk Őre &c[Világboss]", 1.0D, 1.0D, 1.0D,
                PotionEffectType.SLOWNESS, 1, null, Sound.ENTITY_RAVAGER_ROAR, Particle.CRIT, Special.SLAM),
        MAGMA_BEHEMOTH(EntityType.BLAZE, "&6&l🔥 Lávakohó Behemót &c[Világboss]", 1.0D, 1.1D, 1.1D,
                PotionEffectType.WEAKNESS, 0, PotionEffectType.FIRE_RESISTANCE, Sound.ENTITY_GENERIC_EXPLODE, Particle.FLAME, Special.ZONE),
        FROST_KING(EntityType.STRAY, "&b&l❄ Fagyott Trón Királya &c[Világboss]", 1.1D, 1.0D, 1.0D,
                PotionEffectType.SLOWNESS, 2, null, Sound.BLOCK_GLASS_BREAK, Particle.SNOWFLAKE, Special.ZONE),
        BONE_KING(EntityType.WITHER_SKELETON, "&8&l☠ Csontkirály &c[Világboss]", 1.0D, 1.2D, 1.1D,
                PotionEffectType.WITHER, 0, PotionEffectType.STRENGTH, Sound.ENTITY_WITHER_AMBIENT, Particle.SOUL, Special.SUMMON),
        DEEP_HORROR(EntityType.WARDEN, "&3&l👁 Mélységi Rém &c[Világboss]", 1.0D, 1.0D, 1.5D,
                PotionEffectType.DARKNESS, 0, null, Sound.ENTITY_WARDEN_LISTENING, Particle.SCULK_SOUL, Special.SLAM),
        VENOM_BROODMOTHER(EntityType.CAVE_SPIDER, "&2&l🕷 Méreg Anyakirálynő &c[Világboss]", 1.0D, 1.0D, 1.1D,
                PotionEffectType.POISON, 1, PotionEffectType.SPEED, Sound.ENTITY_PHANTOM_AMBIENT, Particle.SNEEZE, Special.ZONE),
        STORM_HERALD(EntityType.VINDICATOR, "&e&l⚡ Vihar Hírnöke &c[Világboss]", 1.0D, 1.2D, 1.2D,
                PotionEffectType.WEAKNESS, 0, PotionEffectType.SPEED, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, Particle.ELECTRIC_SPARK, Special.ZONE),
        PLAGUE_TITAN(EntityType.HUSK, "&8&l☣ Dögvész Titán &c[Világboss]", 1.2D, 1.0D, 1.2D,
                PotionEffectType.WITHER, 0, null, Sound.ENTITY_WITHER_AMBIENT, Particle.SQUID_INK, Special.SLAM),
        GOLEM_SENTINEL(EntityType.IRON_GOLEM, "&7&l⚙ Vas Őrszem &c[Világboss]", 1.3D, 1.0D, 1.1D,
                PotionEffectType.SLOWNESS, 1, PotionEffectType.RESISTANCE, Sound.ITEM_SHIELD_BLOCK, Particle.CRIT, Special.SLAM),
        PIGLIN_WARLORD(EntityType.PIGLIN_BRUTE, "&6&l⚔ Pokoli Hadúr &c[Világboss]", 1.0D, 1.2D, 1.1D,
                PotionEffectType.WEAKNESS, 0, PotionEffectType.STRENGTH, Sound.ENTITY_RAVAGER_ROAR, Particle.CRIT, Special.SUMMON);

        /** Signature special: an around-boss telegraphed slam, a targeted ground zone, or summoning adds. */
        private enum Special { SLAM, ZONE, SUMMON }

        private final EntityType entityType;
        private final String displayName;
        private final double healthMult;
        private final double damageMult;
        private final double rewardMult;
        private final PotionEffectType aura;
        private final int auraAmplifier;
        private final PotionEffectType selfBuff;
        private final Sound sound;
        private final Particle particle;
        private final Special special;

        BossArchetype(final EntityType entityType, final String displayName, final double healthMult,
                      final double damageMult, final double rewardMult, final PotionEffectType aura,
                      final int auraAmplifier, final PotionEffectType selfBuff, final Sound sound,
                      final Particle particle, final Special special) {
            this.entityType = entityType;
            this.displayName = displayName;
            this.healthMult = healthMult;
            this.damageMult = damageMult;
            this.rewardMult = rewardMult;
            this.aura = aura;
            this.auraAmplifier = auraAmplifier;
            this.selfBuff = selfBuff;
            this.sound = sound;
            this.particle = particle;
            this.special = special;
        }
    }

    private volatile long activeBossUntil;
    private volatile long nextAttemptAt;
    private volatile java.util.UUID activeBossId;
    /** Setter-injected (constructed later in the DI order); null = no placement restriction. */
    private volatile EventSpawnGuard spawnGuard;
    /** B33: setter-injected finálé-eszkaláció (null = nincs finálé-szorzó). */
    private volatile SeasonFinaleManager seasonFinale;
    /** Current health fraction (0–1) of the active boss, driving the shared HUD boss-bar. */
    private volatile float bossHealthFraction = 1.0F;


    /** Orchestráció-kapu (setterrel kötve; null = nincs kapuzás). */
    private volatile MajorEventGate eventGate;

    public void setEventGate(final MajorEventGate eventGate) {
        this.eventGate = eventGate;
    }

    public WorldBossManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final MessageManager messageManager, final FactionManager factionManager,
                            final FactionTreasuryManager treasuryManager, final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        this.treasuryManager = treasuryManager;
        this.seasonManager = seasonManager;
        this.worldBossKey = new NamespacedKey(plugin, "world_boss");
        this.bossArchetypeKey = new NamespacedKey(plugin, "world_boss_archetype");
        this.finaleBossKey = new NamespacedKey(plugin, "finale_boss");
    }

    /** Wires the shared spawn-placement guard (built after this manager in the DI order). */
    public void setSpawnGuard(final EventSpawnGuard spawnGuard) {
        this.spawnGuard = spawnGuard;
    }

    /** B33: a szezonzáró-eszkaláció bekötése (a finálé-manager később épül a DI-sorrendben). */
    public void setSeasonFinale(final SeasonFinaleManager seasonFinale) {
        this.seasonFinale = seasonFinale;
    }

    /** B19: az évszak-szorzó bekötése. */
    private volatile SeasonalModifierService seasonalModifiers;

    public void setSeasonalModifiers(final SeasonalModifierService seasonalModifiers) {
        this.seasonalModifiers = seasonalModifiers;
    }

    public boolean isWorldBoss(final Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().getOrDefault(worldBossKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /** Whether a world boss is currently alive (for HUD / boss-bar display). */
    public boolean isBossActive() {
        return activeBossUntil > System.currentTimeMillis();
    }

    /** The active boss's current health fraction (0–1) for the shared HUD boss-bar. */
    public float getBossHealthFraction() {
        return bossHealthFraction;
    }

    /** Milliseconds left before the active world boss despawns, or -1 when none is active. */
    public long getRemainingMillis() {
        return isBossActive() ? Math.max(0L, activeBossUntil - System.currentTimeMillis()) : -1L;
    }

    /**
     * Refreshes the shared boss-bar fraction right after the boss takes a hit, so players see their
     * damage register immediately (the phase tick only refreshes every ~2s). The {@link org.bukkit.event.entity.EntityDamageEvent}
     * fires pre-damage, so the incoming amount is subtracted to project the post-hit health. Called
     * from {@code WorldBossListener} on the boss's own region thread (the damaged entity), so reading
     * its health here is Folia-safe.
     *
     * @param boss the world boss being hit
     * @param incomingDamage the event's final damage (subtracted from current health)
     */
    public void updateHealthBar(final LivingEntity boss, final double incomingDamage) {
        final AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
        final double maxHp = maxHealth != null ? maxHealth.getValue() : boss.getHealth();
        if (maxHp <= 0.0D) {
            return;
        }
        final double projected = Math.max(0.0D, boss.getHealth() - Math.max(0.0D, incomingDamage));
        bossHealthFraction = (float) Math.max(0.0D, Math.min(1.0D, projected / maxHp));
    }

    /**
     * Reserved while a spawn hops threads (set synchronously, self-heals after 10s):
     * activeBossUntil is only written at the END of the two-hop spawn chain, so without
     * this a second force-spawn issued during the hop would double-spawn and orphan a boss
     * (same pattern as WildHuntManager/TreasureEventManager.spawnGraceUntil).
     */
    private volatile long spawnGraceUntil;

    /**
     * Despawns the active world boss on plugin disable so the persistent, buffed
     * boss does not survive a reload as an unmanaged orphan (and a fresh boss can
     * spawn cleanly next start). Best-effort direct removal.
     */
    public void shutdown() {
        activeBossUntil = 0L;
        nextAttemptAt = 0L;
        spawnGraceUntil = 0L;
        final java.util.UUID id = activeBossId;
        activeBossId = null;
        if (id == null) {
            return;
        }
        hu.taliann.icesmp.utils.TransientEntities.removeOnShutdown(id);
    }

    /** Periodic spawn attempt on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("world-events.world-boss.enabled", true)) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now < nextAttemptAt || now < activeBossUntil || now < spawnGraceUntil) {
            return;
        }

        final long intervalMinutes = Math.max(1L, configManager.getLong("world-events.world-boss.check-interval-minutes", 90L));
        nextAttemptAt = now + (intervalMinutes * 60_000L);

        // Orchestráció: ha másik nagy PvE-esemény fut, ez a természetes sorsolás kimarad.
        final MajorEventGate gateRef = eventGate;
        if (gateRef != null && !gateRef.mayStartNaturally("world-boss")) {
            return;
        }

        // A végítélet-hét alatt a spawn-esély napi szorzóval nő (finálé-eszkaláció);
        // a valós évszak finom szorzója (season-modifiers.<evszak>.world-boss).
        final SeasonFinaleManager finaleRef = seasonFinale;
        final double finaleMult = finaleRef == null ? 1.0D : finaleRef.eventChanceMultiplier();
        final SeasonalModifierService seasonalRef = seasonalModifiers;
        final double seasonalMult = seasonalRef == null ? 1.0D : seasonalRef.chanceMultiplier("world-boss");
        final double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("world-events.world-boss.chance-percent", 35.0D) * finaleMult * seasonalMult));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }

        // Hely-horgony: admin-pont vagy random koordináta, ha a config úgy mondja.
        final EventSpawnPointManager pointsRef = spawnPointManager;
        final Location fixedAnchor = pointsRef == null ? null : pointsRef.resolveAnchorLocation("world-boss");
        if (fixedAnchor != null) {
            triggerSpawnAt(fixedAnchor);
            return;
        }

        // Horgony-rotáció: ne mindig ugyanannak a játékosnak a nyakára szülessen a boss.
        final List<? extends Player> candidates = online.stream()
                .filter(p -> online.size() == 1 || !p.getUniqueId().equals(lastAnchorId)).toList();
        final Player anchor = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        // Szándékosan nincs kegyelem-mechanika: se gyengébb boss (farmolható),
        // se buff — az ismétlődés ellen a horgony-rotáció + a fenti hely-horgony véd.
        lastAnchorId = anchor.getUniqueId();
        triggerSpawnNear(anchor);
    }

    /** Az utolsó természetes spawn horgony-játékosa (rotáció). */
    private volatile java.util.UUID lastAnchorId;

    /** Setterrel kötve (a spawnpont-manager később épül a DI-sorrendben). */
    private volatile EventSpawnPointManager spawnPointManager;

    public void setSpawnPointManager(final EventSpawnPointManager spawnPointManager) {
        this.spawnPointManager = spawnPointManager;
    }

    /** N25 — spawn fix helyre (admin-pont / random koordináta), játékos-horgony nélkül. */
    private synchronized void triggerSpawnAt(final Location where) {
        if (isBossActive() || System.currentTimeMillis() < spawnGraceUntil) {
            return;
        }
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        plugin.getServer().getRegionScheduler().run(plugin, where, task -> {
            final Location approx = where.clone().add(
                    ThreadLocalRandom.current().nextDouble(-8.0D, 8.0D), 0.0D,
                    ThreadLocalRandom.current().nextDouble(-8.0D, 8.0D));
            final long lifetimeMinutes = Math.max(1L, configManager.getLong("world-events.world-boss.lifetime-minutes", 20L));
            spawnBoss(approx, lifetimeMinutes);
        });
    }

    /**
     * Admin override: spawns a world boss immediately near the given anchor
     * (or a random online player if {@code anchor} is null). Safe to call from a
     * command; pass the issuing admin as anchor so the location read is region-local.
     *
     * @param anchor preferred anchor player (may be null)
     * @return true if a boss spawn was scheduled (false if one is already active or nobody is online)
     */
    public synchronized boolean forceSpawn(final Player anchor) {
        // synchronized: két egyidejű admin-hívás ne juthasson át együtt az active/grace
        // ellenőrzésen (dupla boss) — ugyanaz a minta, mint WildHunt/Treasure forceStart.
        if (isBossActive() || System.currentTimeMillis() < spawnGraceUntil) {
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

        triggerSpawnNear(target);
        return true;
    }

    /**
     * B33 — a szezonzáró boss spawnja egy KONKRÉT (főváros melletti) ponton: a spawn-guard
     * kihagyásával (a finálé-boss szándékosan a városfalaknál jelenik meg), emelt élettel
     * és finálé-jelölővel (halálakor egyedi loot-tábla). A SeasonFinaleManager hívja.
     *
     * @return true, ha a spawn ütemezve lett (nincs élő boss / spawn-grace)
     */
    public synchronized boolean forceFinaleSpawn(final Location approx, final long lifetimeMinutes) {
        if (isBossActive() || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        plugin.getServer().getRegionScheduler().run(plugin, approx,
                spawnTask -> spawnBoss(approx, lifetimeMinutes, true));
        return true;
    }

    private synchronized void triggerSpawnNear(final Player anchor) {
        // Zárt check-then-act: a synchronized belépés UTÁN is újraellenőrzünk — a tick és
        // egy egyidejű admin-hívás közül csak az első juthat át.
        if (isBossActive() || System.currentTimeMillis() < spawnGraceUntil) {
            return;
        }
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        // Folia: read the anchor's location on its OWN region thread first (it may be in a
        // different region than the caller), then hop to the spawn location's region.
        anchor.getScheduler().run(plugin, task -> {
            final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            final double distance = 24.0D + ThreadLocalRandom.current().nextDouble(16.0D);
            final Location approx = anchor.getLocation().clone().add(
                    Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);

            final long lifetimeMinutes = Math.max(1L, configManager.getLong("world-events.world-boss.lifetime-minutes", 20L));
            // activeBossUntil is set inside spawnBoss only AFTER the spawn is confirmed, so a bad
            // entity-type config never leaves the manager reporting a phantom active boss.
            plugin.getServer().getRegionScheduler().run(plugin, approx, spawnTask -> spawnBoss(approx, lifetimeMinutes));
        }, null);
    }

    private void spawnBoss(final Location approx, final long lifetimeMinutes) {
        spawnBoss(approx, lifetimeMinutes, false);
    }

    private void spawnBoss(final Location approx, final long lifetimeMinutes, final boolean finale) {
        if (approx.getWorld() == null) {
            return;
        }
        final int highestY = approx.getWorld().getHighestBlockYAt(approx.getBlockX(), approx.getBlockZ());
        final Location spawnLocation = new Location(approx.getWorld(), approx.getBlockX() + 0.5D,
                highestY + 1.0D, approx.getBlockZ() + 0.5D);

        // Placement rules (config: world-events.spawn-rules.world-boss): never inside a
        // town/claim/WG region, never on a water surface. Skipping leaves activeBossUntil
        // unset; the 10s spawn-grace self-heals and the next interval rolls a fresh spot.
        // Finálé-mód: a guard KIMARAD — a szezonboss szándékosan a főváros falainál áll.
        final EventSpawnGuard guard = spawnGuard;
        if (!finale && guard != null && (guard.isBlocked("world-boss", spawnLocation)
                || guard.isUnsafeSurface("world-boss", approx.getWorld(), approx.getBlockX(), approx.getBlockZ()))) {
            return;
        }

        // Pick a random archetype from the roster (variety/rotation).
        final BossArchetype archetype = BossArchetype.values()[ThreadLocalRandom.current().nextInt(BossArchetype.values().length)];

        final Class<? extends Entity> entityClass = archetype.entityType.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
            plugin.getLogger().warning("World boss archetype entity-type is not a mob; skipping spawn.");
            return;
        }

        final Mob boss = (Mob) spawnLocation.getWorld().spawn(spawnLocation, entityClass.asSubclass(Mob.class));
        // No overworld zombification (would orphan the PDC-tag) / no daylight burn.
        EventSpawnGuard.prepare(boss);
        activeBossId = boss.getUniqueId();
        activeBossUntil = System.currentTimeMillis() + (lifetimeMinutes * 60_000L);
        boss.getPersistentDataContainer().set(worldBossKey, PersistentDataType.BYTE, (byte) 1);
        boss.getPersistentDataContainer().set(bossArchetypeKey, PersistentDataType.STRING, archetype.name());
        if (finale) {
            boss.getPersistentDataContainer().set(finaleBossKey, PersistentDataType.BYTE, (byte) 1);
        }
        boss.setPersistent(true);
        boss.setRemoveWhenFarAway(false);
        boss.setGlowing(true);
        boss.customName(LegacyComponentSerializer.legacySection().deserialize(TextUtil.color(
                finale ? "&5&l📖 " + configManager.getString("world-events.season-finale.boss.name",
                        "A Lapforduló Őre") + " &c[Szezonboss]" : archetype.displayName)));
        boss.setCustomNameVisible(true);

        final double finaleHealthMult = finale
                ? Math.max(1.0D, configManager.getDouble("world-events.season-finale.boss.health-mult", 1.5D)) : 1.0D;
        final double health = Math.max(20.0D, configManager.getDouble("world-events.world-boss.health", 300.0D))
                * archetype.healthMult * finaleHealthMult;
        final AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
            boss.setHealth(health);
        }
        bossHealthFraction = 1.0F;

        final double damageMultiplier = Math.max(1.0D, configManager.getDouble("world-events.world-boss.damage-multiplier", 2.0D)) * archetype.damageMult;
        final AttributeInstance attackDamage = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(attackDamage.getBaseValue() * damageMultiplier);
        }

        // Archetype self-buff (e.g. fire immunity for the magma boss), for the boss's lifetime.
        if (archetype.selfBuff != null) {
            boss.addPotionEffect(new PotionEffect(archetype.selfBuff, (int) (lifetimeMinutes * 60L * 20L), 0, false, false, true));
        }

        hu.taliann.icesmp.utils.ParticleUtil.spawn(spawnLocation.getWorld(), Particle.FLASH, spawnLocation, 1);
        spawnLocation.getWorld().playSound(spawnLocation, archetype.sound, 2.0F, 0.6F);

        startPhaseTick(boss, archetype);

        Bukkit.getServer().broadcast(messageManager.getMessage(
                finale ? "season-finale-boss-spawned" : "world-boss-spawned",
                finale
                        ? "<dark_purple>📖 A Korszakok Könyvének lapja fordul — a SZEZONBOSS a főváros falainál áll (<white>{x}, {z}</white>)! {minutes} perc, mielőtt a lap végleg átfordul. Az egész szerver kincse a tét!</dark_purple>"
                        : "<dark_red>👹 Világboss jelent meg: <white>{x}, {z}</white> környékén — {minutes} perc múlva elvonul! Aki legyőzi, frakciója dicsőséget és kincset nyer.</dark_red>",
                Map.of(
                        "x", String.valueOf(spawnLocation.getBlockX()),
                        "z", String.valueOf(spawnLocation.getBlockZ()),
                        "minutes", String.valueOf(lifetimeMinutes)
                )
        ));

        // Per-entity despawn timer (retires automatically if the boss dies first).
        boss.getScheduler().runDelayed(plugin, task -> {
            if (boss.isValid()) {
                boss.remove();
                activeBossUntil = 0L;
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "world-boss-despawned",
                        "<gray>👹 A világboss elvonult — senki sem merte legyőzni.</gray>"
                ));
            }
        }, null, lifetimeMinutes * 60L * 20L);
    }

    /**
     * Drives the boss's phases on its OWN region scheduler (Folia-correct): once below
     * half health it enrages (permanent strength + speed), and every tick it applies its
     * signature aura debuff to nearby survivors and emits themed particles. The task
     * auto-retires when the boss is removed; it also self-cancels if the boss is invalid.
     */
    private void startPhaseTick(final Mob boss, final BossArchetype archetype) {
        final AtomicBoolean enraged = new AtomicBoolean(false);
        final AtomicInteger ticks = new AtomicInteger();
        final double radius = Math.max(4.0D, configManager.getDouble("world-events.world-boss.aura-radius", 12.0D));
        boss.getScheduler().runAtFixedRate(plugin, task -> {
            if (!boss.isValid()) {
                task.cancel();
                return;
            }

            final AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
            final double maxHp = maxHealth != null ? maxHealth.getValue() : boss.getHealth();
            bossHealthFraction = maxHp > 0.0D
                    ? (float) Math.max(0.0D, Math.min(1.0D, boss.getHealth() / maxHp)) : 0.0F;
            if (!enraged.get() && boss.getHealth() < maxHp * 0.5D) {
                enraged.set(true);
                boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false, true));
                boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, true));
                hu.taliann.icesmp.utils.ParticleUtil.spawn(boss.getWorld(), Particle.FLASH, boss.getLocation().add(0.0D, 1.0D, 0.0D), 1);
                boss.getWorld().playSound(boss.getLocation(), archetype.sound, 2.0F, 0.5F);
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "world-boss-enraged",
                        "<dark_red>👹 A világboss feldühödött — második fázis!</dark_red>"));
            }

            // Signature aura: debuff nearby survivors. Folia: a nearby player can belong to a
            // neighbouring region, so mutate it on its own scheduler unless we own it.
            for (final Entity nearby : boss.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof Player player) {
                    final PotionEffect aura = new PotionEffect(archetype.aura, 4 * 20, archetype.auraAmplifier, true, false, true);
                    if (Bukkit.isOwnedByCurrentRegion(player)) {
                        if (isSurvivor(player)) {
                            player.addPotionEffect(aura);
                        }
                    } else {
                        player.getScheduler().run(plugin, t -> {
                            if (isSurvivor(player)) {
                                player.addPotionEffect(aura);
                            }
                        }, null);
                    }
                }
            }
            hu.taliann.icesmp.utils.ParticleUtil.spawn(boss.getWorld(), archetype.particle, boss.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.6D, 0.8D, 0.6D, 0.02D);

            // Every ~8s (every 4th tick) the boss uses its signature special — a telegraphed mechanic
            // players must react to, so it is more than a stat-buffed mob.
            if (ticks.incrementAndGet() % 4 == 0) {
                fireSpecial(boss, archetype, enraged.get());
            }
        }, null, 40L, 40L);
    }

    /** A survivor (survival/adventure) — never debuff/hit creative or spectator players. */
    private static boolean isSurvivor(final Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    /** A survivor standing inside the 3-block telegraphed ZONE spot (checked on the player's thread). */
    private static boolean isInZone(final Player player, final Location spot) {
        return isSurvivor(player) && player.getWorld().equals(spot.getWorld())
                && player.getLocation().distanceSquared(spot) <= 9.0D;
    }

    /** Knocks the player away from the SLAM center (runs on the player's own region thread). */
    private static void applySlamKnockback(final Player player, final Location center) {
        final org.bukkit.util.Vector kb = player.getLocation().toVector().subtract(center.toVector());
        if (kb.lengthSquared() > 0.0D) {
            player.setVelocity(kb.normalize().setY(0.6D).multiply(0.9D));
        }
    }

    /**
     * Fires the archetype's signature special on the boss's own region thread (Folia-safe; all touched
     * entities are region-local nearby). SLAM = telegraphed ring around the boss; ZONE = a telegraphed
     * spot on a random nearby survivor; SUMMON = a few buffed adds. Telegraph (particles + sound) lands
     * first, then the effect after a short delay, so players can react.
     */
    private void fireSpecial(final Mob boss, final BossArchetype archetype, final boolean enraged) {
        final org.bukkit.World world = boss.getWorld();
        final double damage = Math.max(1.0D, configManager.getDouble("world-events.world-boss.special-damage", 6.0D))
                * (enraged ? 1.5D : 1.0D);

        switch (archetype.special) {
            case SLAM -> {
                final Location center = boss.getLocation().clone();
                hu.taliann.icesmp.utils.ParticleUtil.spawn(world, archetype.particle, center.clone().add(0.0D, 0.2D, 0.0D), 80, 5.0D, 0.2D, 5.0D, 0.02D);
                spawnTelegraphRing(world, archetype.particle, center, 5.0D);
                telegraphFloor(center, 5.0D);
                world.playSound(center, archetype.sound, 1.6F, 0.6F);
                world.playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 2.0F, 0.6F);
                boss.getScheduler().runDelayed(plugin, t -> {
                    if (!boss.isValid()) {
                        return;
                    }
                    hu.taliann.icesmp.utils.ParticleUtil.spawn(world, Particle.FLASH, center.clone().add(0.0D, 1.0D, 0.0D), 1);
                    // Folia: hit players region-safely — direct (with the boss as damager) when we own
                    // them, otherwise hopped to their scheduler (damager omitted cross-region).
                    for (final Entity nearby : boss.getNearbyEntities(5.0D, 5.0D, 5.0D)) {
                        if (nearby instanceof Player player) {
                            if (Bukkit.isOwnedByCurrentRegion(player)) {
                                if (isSurvivor(player)) {
                                    player.damage(damage, boss);
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
                }, null, 30L);
            }
            case ZONE -> {
                final java.util.List<Player> survivors = new java.util.ArrayList<>();
                for (final Entity nearby : boss.getNearbyEntities(20.0D, 20.0D, 20.0D)) {
                    // A telegráf-célpont kiválasztása a boss szálán fut — idegen régió játékosát
                    // nem olvassuk (gamemode/pozíció), ugyanúgy kapuzva, mint a becsapódás-ág.
                    if (nearby instanceof Player player && Bukkit.isOwnedByCurrentRegion(player)
                            && isSurvivor(player)) {
                        survivors.add(player);
                    }
                }
                if (survivors.isEmpty()) {
                    return;
                }
                final Location spot = survivors.get(ThreadLocalRandom.current().nextInt(survivors.size())).getLocation().clone();
                hu.taliann.icesmp.utils.ParticleUtil.spawn(world, archetype.particle, spot.clone().add(0.0D, 0.2D, 0.0D), 50, 1.6D, 0.2D, 1.6D, 0.02D);
                spawnTelegraphRing(world, archetype.particle, spot, 3.0D);
                telegraphFloor(spot, 3.0D);
                world.playSound(spot, archetype.sound, 1.2F, 0.8F);
                world.playSound(spot, Sound.ENTITY_WARDEN_SONIC_CHARGE, 2.0F, 0.8F);
                boss.getScheduler().runDelayed(plugin, t -> {
                    if (!boss.isValid()) {
                        return;
                    }
                    hu.taliann.icesmp.utils.ParticleUtil.spawn(world, archetype.particle, spot.clone().add(0.0D, 1.0D, 0.0D), 60, 1.6D, 0.6D, 1.6D, 0.05D);
                    // Folia: same region-safe hit pattern as SLAM; the zone check runs on the
                    // target's own thread so its location read is always safe.
                    for (final Entity nearby : boss.getNearbyEntities(28.0D, 28.0D, 28.0D)) {
                        if (nearby instanceof Player player) {
                            final PotionEffect zoneDebuff = new PotionEffect(archetype.aura, 6 * 20, archetype.auraAmplifier + 1, true, false, true);
                            if (Bukkit.isOwnedByCurrentRegion(player)) {
                                if (isInZone(player, spot)) {
                                    player.damage(damage, boss);
                                    player.addPotionEffect(zoneDebuff);
                                }
                            } else {
                                player.getScheduler().run(plugin, t2 -> {
                                    if (isInZone(player, spot)) {
                                        player.damage(damage);
                                        player.addPotionEffect(zoneDebuff);
                                    }
                                }, null);
                            }
                        }
                    }
                }, null, 30L);
            }
            case SUMMON -> {
                final Location at = boss.getLocation();
                final int count = 2 + (enraged ? 1 : 0);
                final long addLifespanTicks = Math.max(10L, configManager.getLong("world-events.world-boss.add-lifespan-seconds", 25L)) * 20L;
                // Telegraph cue distinct from SLAM/ZONE's warning tone, so players learn to
                // recognize "adds incoming" purely by ear.
                world.playSound(at, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5F, 1.0F);
                for (int i = 0; i < count; i++) {
                    final Location spot = at.clone().add(
                            ThreadLocalRandom.current().nextInt(-3, 4), 0.0D, ThreadLocalRandom.current().nextInt(-3, 4));
                    final org.bukkit.entity.Skeleton add = world.spawn(spot, org.bukkit.entity.Skeleton.class);
                    EventSpawnGuard.prepare(add); // daytime SUMMON adds must not burn away instantly
                    add.getPersistentDataContainer().set(
                            hu.taliann.icesmp.factions.FactionCombatMarkers.EVENT_MOB,
                            PersistentDataType.BYTE, (byte) 1);
                    add.setGlowing(true);
                    add.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, (int) addLifespanTicks, 0, false, false, false));
                    // Bounded lifespan so summoned adds never accumulate / outlive the fight (on the add's own scheduler).
                    add.getScheduler().runDelayed(plugin, task -> {
                        if (add.isValid()) {
                            add.remove();
                        }
                    }, null, addLifespanTicks);
                }
                world.playSound(at, archetype.sound, 1.5F, 0.8F);
            }
        }
    }

    /** Points evenly spaced around a telegraph ring's circumference (visual clarity of the "particle cloud" style). */
    private static final int TELEGRAPH_RING_POINTS = 24;

    /**
     * Telegraphs an impending impact zone by tracing its EDGE with evenly-spaced particles
     * (a crisp ring players can read at a glance) rather than only a scattered cloud over the
     * whole area — the ring boundary is what tells a player "stand outside this" at a glance.
     *
     * @param world the boss's world (region-local; called only from the boss's own thread)
     * @param particle the archetype's signature particle
     * @param center the impact zone's center (boss location for SLAM, target spot for ZONE)
     * @param radius the impact zone radius in blocks
     */
    private static void spawnTelegraphRing(final org.bukkit.World world, final Particle particle,
                                           final Location center, final double radius) {
        for (int i = 0; i < TELEGRAPH_RING_POINTS; i++) {
            final double angle = (Math.PI * 2.0D * i) / TELEGRAPH_RING_POINTS;
            final Location point = center.clone().add(Math.cos(angle) * radius, 0.15D, Math.sin(angle) * radius);
            hu.taliann.icesmp.utils.ParticleUtil.spawn(world, particle, point, 1);
        }
    }

    /**
     * DisplayFx-telegraph a particle-gyűrű mellé: a veszélyzóna talaján egy lapos, piros, izzó lap,
     * amely a 30-tick figyelmeztetés alatt kicsiről a teljes sugárig nő — a becsapódásig kitölti a
     * zónát, így a „lépj ki" pillanatok alatt olvasható. A boss régió-szálán hívjuk (a spawn a
     * DisplayFxUtil-ban régió-schedulerre kerül, a lap nem-perzisztens + FX-tagelt).
     */
    private void telegraphFloor(final Location center, final double radius) {
        if (!configManager.getBoolean("display-fx.boss-telegraph.enabled", true)) {
            return;
        }
        final String name = configManager.getString("display-fx.boss-telegraph.material", "RED_STAINED_GLASS");
        final org.bukkit.Material material = org.bukkit.Material.matchMaterial(name);
        final org.bukkit.block.data.BlockData block =
                (material != null && material.isBlock() ? material : org.bukkit.Material.RED_STAINED_GLASS).createBlockData();
        hu.taliann.icesmp.utils.DisplayFxUtil.groundTelegraph(plugin, center, radius, 30, org.bukkit.Color.fromRGB(0xE23B3B), block);
    }

    /**
     * Pays out the boss kill: treasury reward (scaled by the archetype) + league points for
     * the killer's faction and a temporary buff for the slayer. Called by WorldBossListener.
     *
     * @param boss the slain boss
     * @param killer the slayer
     * @param allowRewards false when the global AFK gate suppresses every payout
     */
    public void handleBossDeath(final LivingEntity boss, final Player killer,
                                final boolean allowRewards) {
        // Csak az ÉLŐ, követett bossért jár jutalom: crash után árván maradt (PDC-tages,
        // de már nem követett) példány leölése nem fizethet dupla kasszát/liga-pontot,
        // és nem nullázhatja az épp futó boss követését.
        final java.util.UUID trackedId = activeBossId;
        if (trackedId == null || !boss.getUniqueId().equals(trackedId)) {
            return;
        }
        activeBossUntil = 0L;
        activeBossId = null;

        // The tracked lifecycle must close even when AFK protection suppresses every reward.
        if (!allowRewards) {
            killer.getScheduler().run(plugin, task -> killer.sendActionBar(messageManager.getMessage(
                    "afk-reward-blocked",
                    "<gray>⌚ AFK állapotban a világboss jutalmai nem járnak.</gray>")), null);
            return;
        }

        double rewardMult = 1.0D;
        final String archetypeName = boss.getPersistentDataContainer().get(bossArchetypeKey, PersistentDataType.STRING);
        if (archetypeName != null) {
            try {
                rewardMult = BossArchetype.valueOf(archetypeName).rewardMult;
            } catch (final IllegalArgumentException ignored) {
                // Unknown/old archetype tag — fall back to the base reward.
            }
        }

        final FactionType faction = factionManager.getChosenFaction(
                killer.getUniqueId()).orElse(null);
        final double reward = Math.max(0.0D, configManager.getDouble("world-events.world-boss.treasury-reward", 300.0D)) * rewardMult;
        if (faction != null && reward > 0.0D) {
            treasuryManager.deposit(faction, reward);
        }

        if (faction != null) {
            seasonManager.addPoints(faction, Math.max(0,
                    configManager.getInt("world-events.world-boss.season-points", 10)), "world-boss");
        }
        AdvancementService.award(killer, "world_boss");

        // Szezonboss: egyedi loot-tábla gurul a tetem helyén (a halál-esemény a boss
        // régió-szálán fut, a drop ott biztonságos) + extra liga-pont + saját broadcast.
        if (boss.getPersistentDataContainer().getOrDefault(finaleBossKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1) {
            final int rolls = Math.max(1, configManager.getInt("world-events.season-finale.boss.loot-rolls", 6));
            for (final org.bukkit.inventory.ItemStack loot
                    : LootTable.roll(configManager, "world-events.season-finale.boss.loot", rolls)) {
                boss.getWorld().dropItemNaturally(boss.getLocation(), loot);
            }
            if (faction != null) {
                seasonManager.addPoints(faction, Math.max(0,
                        configManager.getInt("world-events.season-finale.boss.bonus-season-points", 15)), "world-boss");
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "season-finale-boss-slain",
                        "<dark_purple>📖 {player} ledöntötte a Lapforduló Őrét — a Korszakok Könyve új fejezetet nyit! A(z) {faction} extra liga-pontot nyert a záráshoz.</dark_purple>",
                        Map.of("player", killer.getName(), "faction", faction.getDisplayName())));
            } else {
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "season-finale-boss-slain-guest",
                        "<dark_purple>📖 {player}, a Menedék vendége ledöntötte a Lapforduló Őrét — személyes jutalma jár, de frakciópont nem.</dark_purple>",
                        Map.of("player", killer.getName())));
            }
        }

        final int buffMinutes = Math.max(1, configManager.getInt("world-events.world-boss.buff-minutes", 10));
        // Folia: the death event runs on the boss's region; buff the killer on their own region thread.
        final int buffTicks = buffMinutes * 60 * 20;
        // A leütő SZEMÉLYES bónusz-zsákmánya a kasszajutalom mellett —
        // a boss egyénileg is megéri (tárgy, sosem pénz). Inventory-írás = saját régió-szál.
        final int killerRolls = Math.max(0, configManager.getInt("world-events.world-boss.killer-loot-rolls", 2));
        killer.getScheduler().run(plugin, task -> {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, buffTicks, 0, false, true, true));
            killer.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, buffTicks, 0, false, true, true));
            if (killerRolls > 0) {
                for (final org.bukkit.inventory.ItemStack loot
                        : LootTable.roll(configManager, "world-events.world-boss.killer-loot", killerRolls)) {
                    killer.getInventory().addItem(loot).values()
                            .forEach(left -> killer.getWorld().dropItemNaturally(killer.getLocation(), left));
                }
            }
        }, null);

        if (faction == null) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "world-boss-slain-guest",
                    "<gold>⚔ {player}, a Menedék vendége legyőzte a világbosst! Személyes jutalma jár, de frakciókassza- és liga-jóváírás nem.</gold>",
                    Map.of("player", killer.getName())));
        } else {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "world-boss-slain",
                    "<gold>⚔ {player} legyőzte a világbosst! A(z) {faction} kasszája <white>{reward}</white> kincset és <white>{points}</white> liga-pontot nyert!</gold>",
                    Map.of(
                            "player", killer.getName(),
                            "faction", faction.getDisplayName(),
                            "reward", String.valueOf(reward),
                            "points", String.valueOf(configManager.getInt("world-events.world-boss.season-points", 10))
                    )
            ));
        }
    }
}
