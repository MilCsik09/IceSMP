package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.pve.ContributionLedger;
import hu.taliann.icesmp.pve.EncounterRewardDeliveryService;
import hu.taliann.icesmp.pve.EncounterScalingPolicy;
import hu.taliann.icesmp.pve.EquippedCombatPowerService;
import hu.taliann.icesmp.pve.MobAbilityRuntime;
import hu.taliann.icesmp.pve.MobRank;
import hu.taliann.icesmp.utils.GameModeCache;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.PositionCache;
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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * World bosses: periodically a boss-grade guardian
 * spawns near a random adventurer. Slaying it rewards the contribution leader's faction
 * treasury, grants league points and gives qualified players personal rewards. The spawn attempt is
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
    private volatile hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials;
    private volatile MobScalingManager mobScaling;
    private volatile MobAbilityRuntime mobAbilityRuntime;
    private volatile EncounterRewardDeliveryService rewardDelivery;
    private volatile EquippedCombatPowerService equippedCombatPower;
    private volatile EncounterScalingPolicy.Snapshot encounterSnapshot;
    private volatile ContributionLedger contributionLedger;
    private final Set<UUID> rewardCandidates = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void setUniqueMaterials(
            final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        this.uniqueMaterials = uniqueMaterials;
    }

    public void setPveRuntime(final MobScalingManager mobScaling,
                              final MobAbilityRuntime mobAbilityRuntime,
                              final EncounterRewardDeliveryService rewardDelivery,
                              final EquippedCombatPowerService equippedCombatPower) {
        this.mobScaling = mobScaling;
        this.mobAbilityRuntime = mobAbilityRuntime;
        this.rewardDelivery = rewardDelivery;
        this.equippedCombatPower = equippedCombatPower;
    }

    /** Event selection data only; combat identity is resolved from the canonical template id. */
    private enum BossArchetype {
        RING_WARDEN("ring_warden", 1.0D),
        MAGMA_BEHEMOTH("magma_behemoth", 1.1D),
        FROST_KING("frost_king", 1.0D),
        BONE_KING("bone_king", 1.1D),
        DEEP_HORROR("deep_horror", 1.5D),
        VENOM_BROODMOTHER("venom_broodmother", 1.1D),
        STORM_HERALD("storm_herald", 1.2D),
        PLAGUE_TITAN("plague_titan", 1.2D),
        GOLEM_SENTINEL("golem_sentinel", 1.1D),
        PIGLIN_WARLORD("piglin_warlord", 1.1D);

        private final String templateId;
        private final double rewardMult;

        BossArchetype(final String templateId, final double rewardMult) {
            this.templateId = templateId;
            this.rewardMult = rewardMult;
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
    /** Setter-injektált FX-route (null = nincs kliens-FX; a vanilla telegráf ettől független). */
    private volatile ClientFxRoute fxRoute;

    /** Display-tükör a kliens boss-frame-hez; a getterek isBossActive() mögé kapuzva. */
    private volatile String activeBossName = "";
    private volatile String activeBossArchetype = "";
    private volatile boolean bossEnraged;

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

    /**
     * A bestiárium a boss-ARCHETÍPUST jegyzi, nem az EntityType-ot: két azonos vanilla-fajú
     * archetípus külön lajstrom-bejegyzés kell maradjon. Legacy fallback: entity-típusnév.
     */
    public String archetypeId(final Entity entity) {
        if (entity == null) return null;
        final String stored = entity.getPersistentDataContainer()
                .get(bossArchetypeKey, PersistentDataType.STRING);
        return (stored == null || stored.isBlank())
                ? entity.getType().name().toLowerCase(java.util.Locale.ROOT)
                : stored.toLowerCase(java.util.Locale.ROOT);
    }

    /** id → nyers (&-kódos) display-név; a sorrend a roster deklarációs sorrendje. */
    public static java.util.Map<String, String> archetypeDisplayNames() {
        final java.util.LinkedHashMap<String, String> names = new java.util.LinkedHashMap<>();
        final hu.taliann.icesmp.pve.AuthoredCreatureSpawnService spawns =
                hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.current();
        for (final BossArchetype archetype : BossArchetype.values()) {
            final String display = spawns == null ? archetype.templateId
                    : spawns.template(archetype.templateId).displayName();
            names.put(archetype.templateId, display);
        }
        return names;
    }

    /** A display-név kánon-alakja (szín-kódok, szimbólumok és a [Világboss] címke nélkül). */
    public static String plainArchetypeName(final String rawDisplayName) {
        return rawDisplayName
                .replaceAll("&[0-9a-fk-or]", "")
                .replace("[Világboss]", "")
                .replaceAll("^[^\\p{L}]+", "")
                .trim();
    }

    /** Whether a world boss is currently alive (for HUD / boss-bar display). */
    public boolean isBossActive() {
        return activeBossUntil > System.currentTimeMillis();
    }

    /** The active boss's current health fraction (0–1) for the shared HUD boss-bar. */
    public float getBossHealthFraction() {
        return bossHealthFraction;
    }

    public void setFxRoute(final ClientFxRoute fxRoute) {
        this.fxRoute = fxRoute;
    }

    private void emitFx(final String fxId, final Location center, final double radius,
                        final int durationTicks) {
        final ClientFxRoute route = fxRoute;
        if (route != null) {
            route.emitFx(fxId, center, radius, durationTicks);
        }
    }

    /** Az aktív világboss plain display-neve a kliens boss-frame-hez; üres, ha nincs boss. */
    public String getActiveBossName() {
        return isBossActive() ? activeBossName : "";
    }

    /** Az aktív világboss archetípus-kulcsa; üres, ha nincs boss. */
    public String getActiveBossArchetypeId() {
        return isBossActive() ? activeBossArchetype : "";
    }

    /** Második fázis (50% alatti dühöngés) jelzése a kliens boss-frame-hez. */
    public boolean isBossEnraged() {
        return isBossActive() && bossEnraged;
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
        bossEnraged = bossHealthFraction <= 0.5F;
    }

    public void recordBossDamage(final LivingEntity boss, final UUID playerId,
                                 final double incomingDamage) {
        final ContributionLedger ledger = contributionLedger;
        if (ledger == null || playerId == null || activeBossId == null
                || boss == null || !activeBossId.equals(boss.getUniqueId())) return;
        ledger.register(playerId); // Late join contributes, but never mutates the scaling snapshot.
        ledger.recordDamage(playerId, Math.max(0.0D, Math.min(boss.getHealth(), incomingDamage)),
                System.currentTimeMillis());
        final double threshold = Math.max(1.0D, configManager.getDouble(
                "world-events.world-boss.contribution.minimum-score", 25.0D));
        final EncounterScalingPolicy.Snapshot snapshot = encounterSnapshot;
        final EncounterRewardDeliveryService delivery = rewardDelivery;
        if (snapshot != null && delivery != null
                && ledger.contribution(playerId).score() >= threshold
                && rewardCandidates.add(playerId)) {
            final String componentId = configManager.getString(
                    "world-events.world-boss.ascension-component", "osi_ereklyeszilank");
            final int amount = Math.max(1, Math.min(8, configManager.getInt(
                    "world-events.world-boss.ascension-component-amount", 1)
                    + (boss.getPersistentDataContainer().getOrDefault(finaleBossKey,
                    PersistentDataType.BYTE, (byte) 0) == (byte) 1 ? 1 : 0)));
            delivery.reserveEligibility(playerId, snapshot.encounterId(), componentId, amount);
        }
    }

    public void recordBossTanking(final UUID playerId, final double damage) {
        final ContributionLedger ledger = contributionLedger;
        if (ledger != null && playerId != null) {
            ledger.recordTanking(playerId, Math.max(0.0D, damage), System.currentTimeMillis());
        }
    }

    public void recordBossSupport(final UUID actor, final UUID target, final double amount) {
        final ContributionLedger ledger = contributionLedger;
        if (ledger != null && actor != null && target != null && amount > 0.0D) {
            ledger.recordSupport(actor, target, Math.min(1_000.0D, amount),
                    System.currentTimeMillis());
        }
    }

    public void recordBossObjective(final UUID playerId) {
        final ContributionLedger ledger = contributionLedger;
        if (ledger != null && playerId != null
                && ledger.contribution(playerId).score() > 0.0D
                && ledger.contribution(playerId).objectives() < Math.max(1, Math.min(10,
                configManager.getInt("world-events.world-boss.contribution.maximum-objectives", 5)))) {
            ledger.recordObjective(playerId, System.currentTimeMillis());
        }
    }

    public EncounterScalingPolicy.Snapshot encounterSnapshot() { return encounterSnapshot; }

    private EncounterScalingPolicy.Snapshot createEncounterSnapshot(final LivingEntity boss) {
        final double radius = finiteBounded(configManager.getDouble(
                "world-events.world-boss.scaling.participant-radius", 128.0D),
                128.0D, 16.0D, 512.0D);
        final LinkedHashSet<UUID> participants = new LinkedHashSet<>(PositionCache.nearbyPlayerIds(
                boss.getLocation(), radius,
                playerId -> GameModeCache.isKnown(playerId) && GameModeCache.isSurvival(playerId),
                ContributionLedger.MAX_PARTICIPANTS));
        if (participants.isEmpty()) {
            throw new IllegalStateException("world boss encounter has no same-world survival participant snapshot");
        }
        final double tierReference = finiteBounded(configManager.getDouble(
                "world-events.world-boss.scaling.tier-reference-power", 250.0D),
                250.0D, 1.0D, 10_000.0D);
        final EquippedCombatPowerService powerService = equippedCombatPower;
        final double averageCombatPower = participants.stream()
                .mapToDouble(playerId -> powerService == null
                        ? tierReference : powerService.powerOf(playerId, tierReference))
                .average().orElse(tierReference);
        final EncounterScalingPolicy.Tuning tuning = new EncounterScalingPolicy.Tuning(
                configManager.getDouble("world-events.world-boss.scaling.player-coefficient", 0.65D),
                configManager.getDouble("world-events.world-boss.scaling.player-exponent", 0.8D),
                configManager.getDouble("world-events.world-boss.scaling.maximum-health-multiplier", 12.0D),
                configManager.getDouble("world-events.world-boss.scaling.damage-per-doubling", 0.04D),
                configManager.getDouble("world-events.world-boss.scaling.maximum-damage-multiplier", 1.18D),
                configManager.getDouble("world-events.world-boss.scaling.combat-power-influence", 0.20D));
        return EncounterScalingPolicy.snapshot(boss.getUniqueId(), 1, participants,
                averageCombatPower, tierReference, System.currentTimeMillis(), tuning);
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
     * spawn cleanly next start). Best-effort direct removal. Every abort path rejects
     * still-PREPARED reward eligibility before dropping the encounter references.
     */
    public void shutdown() {
        final EncounterScalingPolicy.Snapshot snapshot = encounterSnapshot;
        if (snapshot != null) {
            EncounterRewardDeliveryService.abortPreparedEncounter(snapshot.encounterId());
        }
        activeBossUntil = 0L;
        nextAttemptAt = 0L;
        spawnGraceUntil = 0L;
        final java.util.UUID id = activeBossId;
        activeBossId = null;
        final ContributionLedger ledger = contributionLedger;
        if (ledger != null) ledger.close();
        contributionLedger = null;
        encounterSnapshot = null;
        rewardCandidates.clear();
        clearDisplayState();
        if (id == null) {
            return;
        }
        final hu.taliann.icesmp.pve.AuthoredCreatureSpawnService spawns =
                hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.current();
        if (spawns != null) spawns.cleanupSummons(id);
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

        final MajorEventGate gateRef = eventGate;
        if (gateRef != null && !gateRef.mayStartNaturally("world-boss")) {
            return;
        }

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

        final EventSpawnPointManager pointsRef = spawnPointManager;
        final Location fixedAnchor = pointsRef == null ? null : pointsRef.resolveAnchorLocation("world-boss");
        if (fixedAnchor != null) {
            triggerSpawnAt(fixedAnchor);
            return;
        }

        final List<? extends Player> candidates = online.stream()
                .filter(p -> online.size() == 1 || !p.getUniqueId().equals(lastAnchorId)).toList();
        final Player anchor = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
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
        final EventSpawnGuard guard = spawnGuard;
        if (guard == null) {
            spawnGraceUntil = 0L;
            plugin.getLogger().warning("World boss spawn aborted: EventSpawnGuard is unavailable.");
            return;
        }
        final long lifetimeMinutes = Math.max(1L,
                configManager.getLong("world-events.world-boss.lifetime-minutes", 20L));
        guard.findSafeAtOrNear("world-boss", where, System.nanoTime(),
                location -> spawnBoss(location, lifetimeMinutes), () -> spawnGraceUntil = 0L);
    }

    /**
     * Admin override: spawns a world boss immediately near the given anchor
     * (or a random online player if {@code anchor} is null). Safe to call from a
     * command; pass the issuing admin as anchor so the location read is region-local.
     */
    public synchronized boolean forceSpawn(final Player anchor) {
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

    /** B33 — szezonzáró boss spawnja egy konkrét főváros melletti ponton. */
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
        if (isBossActive() || System.currentTimeMillis() < spawnGraceUntil) {
            return;
        }
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        anchor.getScheduler().run(plugin, task -> {
            final Location origin = anchor.getLocation().clone();
            final long lifetimeMinutes = Math.max(1L,
                    configManager.getLong("world-events.world-boss.lifetime-minutes", 20L));
            final EventSpawnGuard guard = spawnGuard;
            if (guard == null) {
                spawnGraceUntil = 0L;
                plugin.getLogger().warning("World boss spawn aborted: EventSpawnGuard is unavailable.");
                return;
            }
            guard.findSafeNear("world-boss", origin,
                    System.nanoTime() ^ anchor.getUniqueId().getLeastSignificantBits(),
                    location -> spawnBoss(location, lifetimeMinutes),
                    () -> spawnGraceUntil = 0L);
        }, () -> spawnGraceUntil = 0L);
    }

    private void spawnBoss(final Location approx, final long lifetimeMinutes) {
        spawnBoss(approx, lifetimeMinutes, false);
    }

    private void spawnBoss(final Location approx, final long lifetimeMinutes, final boolean finale) {
        if (approx.getWorld() == null) {
            spawnGraceUntil = 0L;
            return;
        }
        final int highestY = approx.getWorld().getHighestBlockYAt(approx.getBlockX(), approx.getBlockZ());
        final Location spawnLocation = new Location(approx.getWorld(), approx.getBlockX() + 0.5D,
                highestY + 1.0D, approx.getBlockZ() + 0.5D);

        final EventSpawnGuard guard = spawnGuard;
        if (!finale && guard != null && (guard.isBlocked("world-boss", spawnLocation)
                || guard.isUnsafeSurface("world-boss", approx.getWorld(), approx.getBlockX(), approx.getBlockZ()))) {
            spawnGraceUntil = 0L;
            return;
        }

        final BossArchetype archetype = BossArchetype.values()[ThreadLocalRandom.current().nextInt(BossArchetype.values().length)];
        final hu.taliann.icesmp.pve.AuthoredCreatureSpawnService spawnService =
                hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.current();
        if (spawnService == null) {
            spawnGraceUntil = 0L;
            plugin.getLogger().warning("World boss spawn authority is not initialized.");
            return;
        }
        final int displayLevel = Math.max(1, configManager.getInt(
                "world-events.world-boss.display-level", 75));
        final hu.taliann.icesmp.pve.MobTemplate bossTemplate =
                spawnService.template(archetype.templateId);
        final Mob boss = spawnService.spawn(spawnLocation,
                hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.Request.template(
                        "world_boss", "world-boss:" + UUID.randomUUID(), "boss",
                        archetype.templateId, displayLevel,
                        hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.RewardOwner.GENERIC,
                        true, 1.0D, 1.0D, 0L));
        if (boss == null) {
            spawnGraceUntil = 0L;
            return;
        }

        // Build the immutable participant/power snapshot before publishing any active-boss state.
        // No arbitrary online-player fallback is allowed: a fixed/admin spawn without an eligible
        // same-world survival participant fails closed rather than scaling from another dimension.
        final EncounterScalingPolicy.Snapshot scalingSnapshot;
        try {
            scalingSnapshot = createEncounterSnapshot(boss);
        } catch (final RuntimeException invalidSnapshot) {
            plugin.getLogger().warning("World boss spawn aborted: " + invalidSnapshot.getMessage());
            if (boss.isValid()) boss.remove();
            spawnGraceUntil = 0L;
            return;
        }

        activeBossName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(net.kyori.adventure.text.Component.text(bossTemplate.displayName()));
        activeBossArchetype = bossTemplate.bestiaryId();
        bossEnraged = false;
        bossHealthFraction = 1.0F;
        activeBossId = boss.getUniqueId();
        activeBossUntil = System.currentTimeMillis() + (lifetimeMinutes * 60_000L);
        encounterSnapshot = scalingSnapshot;
        contributionLedger = new ContributionLedger(scalingSnapshot.encounterId(),
                scalingSnapshot.createdAt(), scalingSnapshot.participants());
        rewardCandidates.clear();

        boss.getPersistentDataContainer().set(worldBossKey, PersistentDataType.BYTE, (byte) 1);
        boss.getPersistentDataContainer().set(bossArchetypeKey, PersistentDataType.STRING,
                bossTemplate.bestiaryId());
        if (finale) {
            boss.getPersistentDataContainer().set(finaleBossKey, PersistentDataType.BYTE, (byte) 1);
        }
        boss.setPersistent(false);
        boss.setRemoveWhenFarAway(false);
        boss.setGlowing(true);
        if (finale) {
            boss.customName(LegacyComponentSerializer.legacySection().deserialize(TextUtil.color(
                    "&5&l📖 " + configManager.getString("world-events.season-finale.boss.name",
                            bossTemplate.displayName()) + " &c[Szezonboss]")));
        }
        boss.setCustomNameVisible(true);

        final double finaleHealthMult = finale
                ? finiteBounded(configManager.getDouble("world-events.season-finale.boss.health-mult", 1.5D),
                1.5D, 1.0D, 10.0D) : 1.0D;
        mobScaling.applyEncounterModifier(boss,
                Math.min(16.0D, finaleHealthMult * scalingSnapshot.healthMultiplier()),
                scalingSnapshot.damageMultiplier(), "world_boss:participants");
        bossHealthFraction = 1.0F;

        hu.taliann.icesmp.utils.ParticleUtil.spawn(spawnLocation.getWorld(), Particle.FLASH, spawnLocation, 1);
        spawnLocation.getWorld().playSound(spawnLocation, Sound.ENTITY_WITHER_SPAWN, 2.0F, 0.6F);

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

        boss.getScheduler().runDelayed(plugin, task -> {
            if (boss.isValid() && activeBossId != null
                    && activeBossId.equals(boss.getUniqueId())) {
                // Run the same abort contract as unload/admin/plugin removal before deleting the entity.
                shutdown();
                if (boss.isValid()) boss.remove();
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "world-boss-despawned",
                        "<gray>👹 A világboss elvonult — senki sem merte legyőzni.</gray>"
                ));
            }
        }, null, lifetimeMinutes * 60L * 20L);
    }

    /** A survivor (survival/adventure) — never debuff/hit creative or spectator players. */
    private static boolean isSurvivor(final Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    /** Contribution-gated, personal and restart-idempotent world-boss settlement. */
    public void handleBossDeath(final LivingEntity boss, final Player killingBlow,
                                final Predicate<UUID> rewardAllowed) {
        final UUID trackedId = activeBossId;
        final ContributionLedger ledger = contributionLedger;
        final EncounterScalingPolicy.Snapshot snapshot = encounterSnapshot;
        if (trackedId == null || boss == null || !boss.getUniqueId().equals(trackedId)
                || ledger == null || snapshot == null) return;
        activeBossUntil = 0L;
        activeBossId = null;
        ledger.close();
        contributionLedger = null;
        encounterSnapshot = null;
        clearDisplayState();

        final double threshold = Math.max(1.0D, configManager.getDouble(
                "world-events.world-boss.contribution.minimum-score", 25.0D));
        final List<Map.Entry<UUID, ContributionLedger.Contribution>> qualified =
                ledger.qualified(threshold, snapshot.createdAt());
        if (qualified.isEmpty()) {
            EncounterRewardDeliveryService.abortPreparedEncounter(snapshot.encounterId());
            rewardCandidates.clear();
            plugin.getLogger().warning("World boss died without a meaningful contribution winner: "
                    + boss.getUniqueId());
            return;
        }

        final UUID leaderId = qualified.getFirst().getKey();
        final Player leader = Bukkit.getPlayer(leaderId);
        final String leaderName = leader == null ? "Ismeretlen hős" : leader.getName();
        double rewardMultiplier = 1.0D;
        final String archetypeName = boss.getPersistentDataContainer().get(
                bossArchetypeKey, PersistentDataType.STRING);
        if (archetypeName != null) {
            for (final BossArchetype candidate : BossArchetype.values()) {
                if (candidate.templateId.equals(archetypeName)) rewardMultiplier = candidate.rewardMult;
            }
        }
        final FactionType faction = factionManager.getChosenFaction(leaderId).orElse(null);
        final double treasuryReward = Math.max(0.0D, configManager.getDouble(
                "world-events.world-boss.treasury-reward", 300.0D)) * rewardMultiplier;
        if (faction != null && treasuryReward > 0.0D) treasuryManager.deposit(faction, treasuryReward);
        if (faction != null) seasonManager.addPoints(faction, Math.max(0,
                configManager.getInt("world-events.world-boss.season-points", 10)), "world-boss");

        final boolean finale = boss.getPersistentDataContainer().getOrDefault(
                finaleBossKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
        if (finale && faction != null) seasonManager.addPoints(faction, Math.max(0,
                configManager.getInt("world-events.season-finale.boss.bonus-season-points", 15)),
                "world-boss");
        final int buffTicks = Math.max(1, configManager.getInt(
                "world-events.world-boss.buff-minutes", 10)) * 60 * 20;
        final String componentId = configManager.getString(
                "world-events.world-boss.ascension-component", "osi_ereklyeszilank");
        final int componentAmount = Math.max(1, Math.min(8, configManager.getInt(
                "world-events.world-boss.ascension-component-amount", 1) + (finale ? 1 : 0)));
        final EncounterRewardDeliveryService delivery = rewardDelivery;
        final Set<UUID> qualifiedIds = qualified.stream().map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (delivery != null) {
            for (final UUID candidate : Set.copyOf(rewardCandidates)) {
                if (!qualifiedIds.contains(candidate)) {
                    delivery.reject(candidate, snapshot.encounterId(), componentId, componentAmount);
                }
            }
        }

        for (final Map.Entry<UUID, ContributionLedger.Contribution> entry : qualified) {
            final UUID playerId = entry.getKey();
            if (!ledger.claimSettlement(playerId)) continue;
            final boolean allowed = rewardAllowed == null || rewardAllowed.test(playerId);
            if (!allowed) {
                if (delivery != null) delivery.reject(playerId, snapshot.encounterId(),
                        componentId, componentAmount);
                continue;
            }
            if (delivery != null) delivery.activate(playerId, snapshot.encounterId(),
                    componentId, componentAmount);
            final Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            player.getScheduler().run(plugin, task -> {
                if (!isSurvivor(player)) return;
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                        buffTicks, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                        buffTicks, 0, false, true, true));
                AdvancementService.award(player, "world_boss");
            }, null);
        }
        rewardCandidates.clear();

        Bukkit.getServer().broadcast(messageManager.getMessage(
                faction == null ? "world-boss-slain-guest" : "world-boss-slain",
                faction == null
                        ? "<gold>⚔ {player} vezette a világboss elleni győzelmet! Minden érdemi résztvevő személyes jutalmat kap.</gold>"
                        : "<gold>⚔ {player} vezette a világboss elleni győzelmet! A(z) {faction} kasszája <white>{reward}</white> kincset kapott; minden érdemi résztvevő személyes jutalmat kap.</gold>",
                faction == null ? Map.of("player", leaderName) : Map.of(
                        "player", leaderName, "faction", faction.getDisplayName(),
                        "reward", String.valueOf(treasuryReward),
                        "points", String.valueOf(configManager.getInt(
                                "world-events.world-boss.season-points", 10)))));
    }

    private void clearDisplayState() {
        activeBossName = "";
        activeBossArchetype = "";
        bossHealthFraction = 0.0F;
        bossEnraged = false;
    }

    private static double finiteBounded(final double configured, final double fallback,
                                        final double minimum, final double maximum) {
        final double value = Double.isFinite(configured) ? configured : fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
