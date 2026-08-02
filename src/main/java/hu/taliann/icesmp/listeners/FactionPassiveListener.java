package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.SpellSchool;
import hu.taliann.icesmp.factions.FactionCombatMarkers;
import hu.taliann.icesmp.factions.FactionMembership;
import hu.taliann.icesmp.factions.FactionMobContextResolver;
import hu.taliann.icesmp.factions.FactionPassiveConfig;
import hu.taliann.icesmp.factions.FactionPassivePolicy;
import hu.taliann.icesmp.factions.FactionPassiveService;
import hu.taliann.icesmp.factions.FactionPassiveSettings;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.WhisperManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.utils.PositionCache;
import hu.taliann.icesmp.utils.SpellDamageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Paper/Folia event adapter for the central faction-passive policy. */
public final class FactionPassiveListener implements Listener, PlayerStateCleanup {

    private enum RetaliationKind {
        NEUTRAL,
        DARK_AMBIENT,
        WHISPER
    }

    private record TrackedTarget(WeakReference<Mob> mob, RetaliationKind kind) {
    }

    private final JavaPlugin plugin;
    private final FactionManager factionManager;
    private final WhisperManager whisperManager;
    private final FactionPassiveConfig config;
    private final FactionPassivePolicy policy;
    private final FactionPassiveService state;
    private final FactionMobContextResolver mobContexts;
    /** Weak entity refs; every read/mutation is still hopped to the entity scheduler. */
    private final Map<UUID, Map<UUID, TrackedTarget>> retaliationTargets = new ConcurrentHashMap<>();

    public FactionPassiveListener(final JavaPlugin plugin,
                                  final FactionManager factionManager,
                                  final WhisperManager whisperManager,
                                  final FactionPassiveConfig config,
                                  final FactionPassivePolicy policy,
                                  final FactionPassiveService state,
                                  final FactionMobContextResolver mobContexts) {
        this.plugin = plugin;
        this.factionManager = factionManager;
        this.whisperManager = whisperManager;
        this.config = config;
        this.policy = policy;
        this.state = state;
        this.mobContexts = mobContexts;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final FactionPassiveSettings settings = config.snapshot();
        final FactionPassivePolicy.DamageChannel channel = damageChannel(event, player.getUniqueId());
        if (channel == null) {
            return;
        }
        final boolean inheritedScriptedFire = channel == FactionPassivePolicy.DamageChannel.RED_ENTITY_FIRE
                && state.isScriptedCombatFire(player.getUniqueId());
        if (isRedFireChannel(channel) && !settings.red().affectScriptedCombatFire()
                && (inheritedScriptedFire || hasMarkedCombatSource(event, settings))) {
            return;
        }
        final double multiplier = policy.damageMultiplier(
                factionManager.getMembership(player.getUniqueId()), channel, settings);
        applyDamageMultiplier(event, multiplier);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityCombust(final EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final FactionPassiveSettings settings = config.snapshot();
        if (!settings.enabled() || !settings.red().enabled()) {
            return;
        }
        final long durationMillis = FactionPassiveService.combustDurationMillis(event.getDuration());
        if (durationMillis <= 0L) {
            return;
        }
        state.markEntityFire(player.getUniqueId(), durationMillis,
                hasMarkedCombatEntity(event.getCombuster(), settings));
    }

    /** Copies combat identity while shooter and projectile still share the launch region. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(final ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Mob shooter)
                || !hasMarkedCombatEntity(shooter, config.snapshot())) {
            return;
        }
        event.getEntity().getPersistentDataContainer().set(
                FactionCombatMarkers.SCRIPTED_COMBAT, PersistentDataType.BYTE, (byte) 1);
    }

    /** Producer-side dungeon identity; the resolver itself remains read-only. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        final FactionPassiveSettings settings = config.snapshot();
        if (mobContexts.contentContexts(event.getEntity(), settings).contains(
                FactionPassivePolicy.ContentContext.DUNGEON)) {
            event.getEntity().getPersistentDataContainer().set(
                    FactionCombatMarkers.DUNGEON_COMBAT, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExhaustion(final EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final double chance = policy.blueExhaustionSaveChance(
                factionManager.getMembership(player.getUniqueId()),
                event.getExhaustionReason().name(), config.snapshot());
        if (chance > 0.0D && ThreadLocalRandom.current().nextDouble() < chance) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWitherEffect(final EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final PotionEffect effect = event.getNewEffect();
        if (effect == null || effect.getType() != PotionEffectType.WITHER
                || state.isAdjustingWitherEffect(player.getUniqueId())) {
            return;
        }
        final double multiplier = policy.witherDurationMultiplier(
                factionManager.getMembership(player.getUniqueId()), config.snapshot());
        if (multiplier == 1.0D) {
            return;
        }
        if (multiplier == 0.0D) {
            event.setCancelled(true);
            return;
        }
        if (effect.isInfinite()) {
            return;
        }
        final long scaledDuration = Math.round(effect.getDuration() * multiplier);
        if (scaledDuration <= 0L) {
            event.setCancelled(true);
            return;
        }
        if (scaledDuration > Integer.MAX_VALUE) {
            plugin.getLogger().warning("A DARK Wither-idő szorzása meghaladja a Paper int tick tartományát; "
                    + "az effekt változatlan marad (nincs rejtett clamp).");
            return;
        }
        if (!state.beginWitherAdjustment(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        try {
            player.addPotionEffect(effect.withDuration((int) scaledDuration), event.isOverride());
        } finally {
            state.endWitherAdjustment(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobProvoked(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mob victim)) {
            return;
        }
        final UUID playerId = damagingPlayerId(event);
        if (playerId == null) {
            return;
        }
        final FactionPassiveSettings settings = config.snapshot();
        if (!settings.enabled()) {
            return;
        }
        final FactionMembership membership = factionManager.getMembership(playerId);
        final UUID victimId = victim.getUniqueId();
        final boolean neutralCreature = mobContexts.isNeutralMob(victim, settings) || victim instanceof Enderman;
        if (settings.neutral().enabled() && settings.neutral().passiveMobTruceEnabled()
                && membership.isMember(FactionType.NEUTRAL) && neutralCreature
                && settings.neutral().breakOnDamage()) {
            state.provokeNeutral(playerId, victimId, settings.neutral().retaliationMillis());
            trackRetaliationTarget(playerId, victim, RetaliationKind.NEUTRAL);
        }

        if (!mobContexts.isUndead(victim)) {
            return;
        }
        if (settings.dark().enabled() && settings.dark().ambientUndead().enabled()
                && membership.isMember(FactionType.DARK) && mobContexts.isAmbientUndead(victim)
                && settings.dark().ambientUndead().breakOnDamage()) {
            state.provokeDark(playerId, victimId,
                    settings.dark().ambientUndead().retaliationMillis());
            trackRetaliationTarget(playerId, victim, RetaliationKind.DARK_AMBIENT);
            alertNearbyUndead(victim, playerId, settings);
            return;
        }
        if (settings.whisper().enabled() && whisperManager.isWhispererCached(playerId)
                && settings.whisper().breakOnDamage()) {
            state.provokeDark(playerId, victimId, settings.whisper().retaliationMillis());
            trackRetaliationTarget(playerId, victim, RetaliationKind.WHISPER);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityTarget(final EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        final FactionPassiveSettings settings = config.snapshot();
        final FactionPassivePolicy.TargetContext context = mobContexts.resolve(
                event, playerId, whisperManager.isWhispererCached(playerId), state, settings);
        final FactionPassivePolicy.TargetDecision decision = policy.resolveTarget(
                factionManager.getMembership(playerId), context, settings,
                ThreadLocalRandom.current().nextDouble());
        if (decision == FactionPassivePolicy.TargetDecision.ALLOW) {
            if (context.adminOrScriptedForce() || !context.contentContexts().isEmpty()) {
                untrackRetaliationTarget(playerId, event.getEntity().getUniqueId());
            }
            return;
        }
        event.setCancelled(true);
        if (decision == FactionPassivePolicy.TargetDecision.CANCEL_WHISPER_WILD) {
            recordWhisperWitness(player, settings.whisper());
        }
    }

    /** A koronaátok marker csak az explicit cél UUID-jára és annak célzási életciklusára él. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrownCurseTargetLifecycle(final EntityTargetLivingEntityEvent event) {
        final String markedTarget = event.getEntity().getPersistentDataContainer().get(
                FactionCombatMarkers.CROWN_CURSE_TARGET, PersistentDataType.STRING);
        if (markedTarget == null) {
            return;
        }
        if (event.isCancelled() || !(event.getTarget() instanceof Player target)
                || !markedTarget.equals(target.getUniqueId().toString())) {
            event.getEntity().getPersistentDataContainer().remove(
                    FactionCombatMarkers.CROWN_CURSE_TARGET);
        }
    }

    private void alertNearbyUndead(final Mob victim, final UUID playerId,
                                   final FactionPassiveSettings settings) {
        final double radius = settings.dark().ambientUndead().alertNearbyRadius();
        if (radius <= 0.0D) {
            return;
        }
        final UUID sourceMobId = victim.getUniqueId();
        for (final Entity nearby : victim.getNearbyEntities(radius, radius, radius)) {
            nearby.getScheduler().run(plugin, task -> {
                if (!(nearby instanceof Mob mob) || !mobContexts.isUndead(mob)) {
                    return;
                }
                final long remaining = state.darkRetaliationRemainingMillis(playerId, sourceMobId);
                if (remaining <= 0L) {
                    return;
                }
                final FactionPassiveSettings liveSettings = config.snapshot();
                if (!policy.canAlertDarkUndead(
                        factionManager.getMembership(playerId),
                        true,
                        mobContexts.contentContexts(mob, liveSettings, playerId),
                        liveSettings)) {
                    return;
                }
                final Player target = Bukkit.getPlayer(playerId);
                if (target == null) {
                    return;
                }
                state.provokeDark(playerId, mob.getUniqueId(), remaining);
                if (trackRetaliationTarget(playerId, mob, RetaliationKind.DARK_AMBIENT)) {
                    mob.setTarget(target);
                }
            }, null);
        }
    }

    private void recordWhisperWitness(final Player player,
                                      final FactionPassiveSettings.Whisper whisper) {
        if (whisper.witnessChance() <= 0.0D
                || ThreadLocalRandom.current().nextDouble() >= whisper.witnessChance()) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        if (!PositionCache.hasNearbyPlayer(playerId, whisper.witnessRadius(),
                witnessId -> !whisperManager.isWhispererCached(witnessId))) {
            return;
        }
        player.getScheduler().run(plugin,
                task -> whisperManager.addSuspicion(player, whisper.witnessSuspicion()), null);
    }

    private static UUID damagingPlayerId(final EntityDamageByEntityEvent event) {
        final UUID direct = owningPlayerId(event.getDamager());
        return direct != null
                ? direct : owningPlayerId(event.getDamageSource().getCausingEntity());
    }

    private static UUID owningPlayerId(final Object source) {
        if (source instanceof Player player) {
            return player.getUniqueId();
        }
        if (source instanceof Projectile projectile) {
            return owningPlayerId(projectile.getShooter());
        }
        if (source instanceof org.bukkit.entity.Tameable tameable
                && tameable.getOwner() != null) {
            return tameable.getOwner().getUniqueId();
        }
        return null;
    }

    private FactionPassivePolicy.DamageChannel damageChannel(final EntityDamageEvent event,
                                                             final UUID playerId) {
        if (SpellDamageUtil.schoolOf(event.getDamageSource()) == SpellSchool.TUZ) {
            return FactionPassivePolicy.DamageChannel.ICE_SMP_FIRE_MAGIC;
        }
        return switch (event.getCause()) {
            case FIRE -> event.getDamageSource().getCausingEntity() == null
                    ? FactionPassivePolicy.DamageChannel.RED_FIRE
                    : FactionPassivePolicy.DamageChannel.RED_ENTITY_FIRE;
            case FIRE_TICK -> state.isEntityFire(playerId)
                    ? FactionPassivePolicy.DamageChannel.RED_ENTITY_FIRE
                    : FactionPassivePolicy.DamageChannel.RED_FIRE_TICK;
            case LAVA -> FactionPassivePolicy.DamageChannel.RED_LAVA;
            case HOT_FLOOR -> FactionPassivePolicy.DamageChannel.RED_HOT_FLOOR;
            case FREEZE -> FactionPassivePolicy.DamageChannel.BLUE_FREEZE;
            case DROWNING -> FactionPassivePolicy.DamageChannel.BLUE_DROWNING;
            case FALL -> FactionPassivePolicy.DamageChannel.NEUTRAL_FALL;
            case WITHER -> FactionPassivePolicy.DamageChannel.DARK_WITHER;
            default -> null;
        };
    }

    private boolean hasMarkedCombatSource(final EntityDamageEvent event,
                                          final FactionPassiveSettings settings) {
        Entity source = event.getDamageSource().getDirectEntity();
        if (source == null) {
            source = event.getDamageSource().getCausingEntity();
        }
        return hasMarkedCombatEntity(source, settings);
    }

    private boolean hasMarkedCombatEntity(final Entity source,
                                          final FactionPassiveSettings settings) {
        return source != null && Bukkit.isOwnedByCurrentRegion(source)
                && !mobContexts.contentContexts(source, settings).isEmpty();
    }

    private static boolean isRedFireChannel(final FactionPassivePolicy.DamageChannel channel) {
        return switch (channel) {
            case RED_FIRE, RED_FIRE_TICK, RED_ENTITY_FIRE, RED_LAVA, RED_HOT_FLOOR,
                    ICE_SMP_FIRE_MAGIC -> true;
            default -> false;
        };
    }

    private static void applyDamageMultiplier(final EntityDamageEvent event, final double multiplier) {
        if (multiplier == 0.0D) {
            event.setCancelled(true);
        } else if (multiplier != 1.0D) {
            final double scaled = event.getDamage() * multiplier;
            if (Double.isFinite(scaled) && scaled >= 0.0D) {
                event.setDamage(scaled);
            }
        }
    }

    private boolean trackRetaliationTarget(final UUID playerId, final Mob mob,
                                           final RetaliationKind kind) {
        final UUID mobId = mob.getUniqueId();
        final long remaining = kind == RetaliationKind.NEUTRAL
                ? state.neutralRetaliationRemainingMillis(playerId, mobId)
                : state.darkRetaliationRemainingMillis(playerId, mobId);
        if (remaining <= 0L) {
            return false;
        }
        final TrackedTarget tracked = new TrackedTarget(new WeakReference<>(mob), kind);
        retaliationTargets.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .put(mobId, tracked);
        return scheduleRetaliationRelease(playerId, mobId, tracked, remaining);
    }

    private boolean scheduleRetaliationRelease(final UUID playerId, final UUID mobId,
                                               final TrackedTarget tracked,
                                               final long remainingMillis) {
        final Mob mob = tracked.mob().get();
        if (mob == null) {
            untrackRetaliationTarget(playerId, mobId, tracked);
            return false;
        }
        final long delayTicks = Math.max(1L, remainingMillis / 50L
                + (remainingMillis % 50L == 0L ? 0L : 1L));
        final io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled;
        try {
            scheduled = mob.getScheduler().runDelayed(plugin, task -> {
                final Map<UUID, TrackedTarget> playerTargets = retaliationTargets.get(playerId);
                if (playerTargets == null || playerTargets.get(mobId) != tracked) {
                    return;
                }
                final long remaining = tracked.kind() == RetaliationKind.NEUTRAL
                        ? state.neutralRetaliationRemainingMillis(playerId, mobId)
                        : state.darkRetaliationRemainingMillis(playerId, mobId);
                if (remaining > 0L) {
                    scheduleRetaliationRelease(playerId, mobId, tracked, remaining);
                    return;
                }
                clearTargetIfStillProtected(mob, playerId, mobId, tracked);
                untrackRetaliationTarget(playerId, mobId, tracked);
            }, () -> untrackRetaliationTarget(playerId, mobId, tracked), delayTicks);
        } catch (final RuntimeException schedulingFailure) {
            untrackRetaliationTarget(playerId, mobId, tracked);
            return false;
        }
        if (scheduled == null) {
            untrackRetaliationTarget(playerId, mobId, tracked);
            return false;
        }
        return true;
    }

    private void clearTargetIfStillProtected(final Mob mob, final UUID playerId,
                                             final UUID mobId, final TrackedTarget oldTracking) {
        final Map<UUID, TrackedTarget> currentTargets = retaliationTargets.get(playerId);
        final TrackedTarget currentTracking = currentTargets == null ? null : currentTargets.get(mobId);
        if (currentTracking != null && currentTracking != oldTracking) {
            return;
        }
        final org.bukkit.entity.LivingEntity current = mob.getTarget();
        if (current == null || !current.getUniqueId().equals(playerId)) {
            return;
        }
        final Player online = Bukkit.getPlayer(playerId);
        if (online == null) {
            mob.setTarget(null);
            return;
        }
        final FactionPassiveSettings liveSettings = config.snapshot();
        final FactionPassivePolicy.TargetContext liveContext = mobContexts.resolveCurrentTruce(
                mob, playerId, whisperManager.isWhispererCached(playerId), liveSettings);
        final FactionPassivePolicy.TargetDecision liveDecision = policy.resolveTarget(
                factionManager.getMembership(playerId), liveContext, liveSettings,
                ThreadLocalRandom.current().nextDouble());
        if (liveDecision != FactionPassivePolicy.TargetDecision.ALLOW) {
            mob.setTarget(null);
        }
    }

    private void untrackRetaliationTarget(final UUID playerId, final UUID mobId) {
        final Map<UUID, TrackedTarget> targets = retaliationTargets.get(playerId);
        if (targets == null) {
            return;
        }
        targets.remove(mobId);
        if (targets.isEmpty()) {
            retaliationTargets.remove(playerId, targets);
        }
    }

    private void untrackRetaliationTarget(final UUID playerId, final UUID mobId,
                                          final TrackedTarget tracked) {
        final Map<UUID, TrackedTarget> targets = retaliationTargets.get(playerId);
        if (targets == null) {
            return;
        }
        targets.remove(mobId, tracked);
        if (targets.isEmpty()) {
            retaliationTargets.remove(playerId, targets);
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        state.clearPlayerState(playerId);
        final Map<UUID, TrackedTarget> targets = retaliationTargets.remove(playerId);
        if (targets == null) {
            return;
        }
        targets.forEach((mobId, tracked) -> {
            final Mob mob = tracked.mob().get();
            if (mob != null) {
                scheduleTargetRevalidation(mob, playerId, mobId, tracked);
            }
        });
    }

    public void clearAllState() {
        state.clearAll();
        final Map<UUID, Map<UUID, TrackedTarget>> snapshot = Map.copyOf(retaliationTargets);
        retaliationTargets.clear();
        snapshot.forEach((playerId, targets) -> targets.forEach((mobId, tracked) -> {
            final Mob mob = tracked.mob().get();
            if (mob != null) {
                scheduleTargetRevalidation(mob, playerId, mobId, tracked);
            }
        }));
    }

    private void scheduleTargetRevalidation(final Mob mob, final UUID playerId,
                                            final UUID mobId, final TrackedTarget tracked) {
        try {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled =
                    mob.getScheduler().run(plugin,
                            task -> clearTargetIfStillProtected(mob, playerId, mobId, tracked),
                            null);
            if (scheduled == null) {
                return;
            }
        } catch (final IllegalStateException ignored) {
            // Plugin-disable alatt a scheduler már visszautasíthat új feladatot; a Java-oldali
            // állapot ekkor is kiürült, az entity AI-t pedig a plugin unload újraértékeli.
        }
    }
}
