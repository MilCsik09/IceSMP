package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.EventSpawnGuard;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/** Event-driven, per-entity ability and elite-affix runtime. No global mob scan is used. */
public final class MobAbilityRuntime implements Listener {
    private static final int MAX_ACTIVE_MOBS = 2048;
    private static final long RUNTIME_STEP_TICKS = 20L;

    private static final class RuntimeState {
        private final Mob mob;
        private final List<MobAbilityDefinition> definitions;
        private final MobBehaviorProfile behavior;
        private final long attachedAtNanos = System.nanoTime();
        private final Map<String, Long> readyAtTick = new LinkedHashMap<>();
        private final ArrayDeque<MobAbilityDefinition> pendingThresholds = new ArrayDeque<>();
        private final java.util.Set<String> consumedThresholds = new HashSet<>();
        private long tick;
        private long recoveryUntilTick;
        private long castEpoch;
        private long combatUntilTick;
        private UUID targetId;
        private Location targetLocation;
        private boolean authoredCombat;
        private boolean casting;
        private boolean paused;
        private int rotationCursor;
        private String previousAbilityId = "";
        private MobAbilityDefinition currentAbility;
        private ScheduledTask task;

        private RuntimeState(final Mob mob, final List<MobAbilityDefinition> definitions,
                             final MobBehaviorProfile behavior) {
            this.mob = mob;
            this.definitions = List.copyOf(definitions);
            this.behavior = behavior;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MobScalingManager scaling;
    private final MobTemplateRegistry templates;
    private final MobAbilityRegistry abilities;
    private final CreatureSpeciesRegistry species;
    private final Map<UUID, RuntimeState> states = new ConcurrentHashMap<>();
    private final java.util.Set<String> reportedScheduleRejections = ConcurrentHashMap.newKeySet();
    private final NamespacedKey volatileArmedKey;
    private final NamespacedKey frenziedKey;
    private final NamespacedKey summonOwnerKey;

    public MobAbilityRuntime(final JavaPlugin plugin, final ConfigManager config,
                             final MobScalingManager scaling,
                             final MobTemplateRegistry templates,
                             final MobAbilityRegistry abilities,
                             final CreatureSpeciesRegistry species) {
        this.plugin = plugin;
        this.config = config;
        this.scaling = scaling;
        this.templates = templates;
        this.abilities = abilities;
        this.species = species;
        this.volatileArmedKey = new NamespacedKey(plugin, "mob_volatile_armed");
        this.frenziedKey = new NamespacedKey(plugin, "mob_frenzied_active");
        this.summonOwnerKey = new NamespacedKey(plugin, "mob_summon_owner");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        final LivingEntity entity = event.getEntity();
        // Event/authored spawners attach their template after World#spawn returns.
        entity.getScheduler().runDelayed(plugin, task -> attach(entity), null, 1L);
    }

    public void attach(final LivingEntity entity) {
        if (!(entity instanceof Mob mob) || !mob.isValid()) return;
        final String templateId = scaling.getTemplateId(mob);
        final MobTemplate template = templates.find(templateId).orElse(null);
        final ArrayList<MobAbilityDefinition> definitions = new ArrayList<>();
        final MobRank rank = scaling.getRank(mob);
        if (template != null) {
            for (final String abilityId : template.abilityIdsFor(rank)) {
                definitions.add(abilities.require(abilityId));
            }
        }
        final CreatureSpeciesPolicy creaturePolicy = species.profile(mob.getType());
        if (template == null) {
            for (final String abilityId : creaturePolicy.techniquesFor(rank)) {
                addIfAbsent(definitions, abilityId);
            }
            if (creaturePolicy.disposition() == CreatureSpeciesPolicy.Disposition.HOSTILE) {
                for (final String abilityId : config.getStringList("mob-scaling.rank-abilities."
                        + rank.name().toLowerCase(java.util.Locale.ROOT))) {
                    addIfAbsent(definitions, abilityId);
                }
            }
        }
        final List<EliteAffix> affixes = scaling.getAffixes(mob);
        if (affixes.contains(EliteAffix.ARCANE)) addIfAbsent(definitions, "rime_burst");
        if (affixes.contains(EliteAffix.SUMMONER)) addIfAbsent(definitions, "call_frozen");
        if (affixes.contains(EliteAffix.SHIELDED)) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    Integer.MAX_VALUE, 0, false, true, true));
        }
        final MobArchetype archetype = archetype(mob);
        definitions.removeIf(definition -> !definition.eligible(rank, archetype));
        final int maximum = maximumTechniques(rank);
        while (definitions.stream().filter(MobAbilityRuntime::countsTowardTechniqueCap).count() > maximum) {
            for (int index = definitions.size() - 1; index >= 0; index--) {
                if (countsTowardTechniqueCap(definitions.get(index))) {
                    definitions.remove(index);
                    break;
                }
            }
        }
        if (definitions.isEmpty()) return;
        final RuntimeState state = new RuntimeState(mob, definitions,
                template == null ? MobBehaviorProfile.defaults(archetype == null
                        ? MobArchetype.BRUISER : archetype) : template.behavior());
        if (!registerState(mob.getUniqueId(), state)) return;
        try {
            state.task = mob.getScheduler().runAtFixedRate(plugin,
                    task -> tick(mob, state),
                    () -> states.remove(mob.getUniqueId(), state),
                    RUNTIME_STEP_TICKS, RUNTIME_STEP_TICKS);
        } catch (final RuntimeException rejected) {
            states.remove(mob.getUniqueId(), state);
        }
    }

    /**
     * Rebuilds one entity's bounded technique projection after an authored spawner has finished
     * applying template/rank metadata. This closes the spawn-event ordering window without
     * creating a second combat lifecycle or retaining a stale generic ability kit.
     */
    public void refreshProfile(final Mob mob) {
        if (mob == null || !mob.isValid()) return;
        final RuntimeState current = states.get(mob.getUniqueId());
        if (current != null) detach(current);
        attach(mob);
    }

    /** Serialises producer admission so concurrent Folia regions cannot overshoot the hard cap. */
    private boolean registerState(final UUID mobId, final RuntimeState state) {
        synchronized (states) {
            if (states.containsKey(mobId) || states.size() >= MAX_ACTIVE_MOBS) return false;
            states.put(mobId, state);
            return true;
        }
    }

    /** Opens the same bounded cast/cooldown lifecycle used by hostile mobs. */
    public void enterCombat(final Mob mob, final Player target, final long durationTicks) {
        if (mob == null || target == null || !mob.isValid()) return;
        attach(mob);
        final RuntimeState state = states.get(mob.getUniqueId());
        if (state == null) return;
        if (!authoredTechniqueAllowed(mob, species.profile(mob.getType()))) return;
        state.targetId = target.getUniqueId();
        final Location cached = Bukkit.isOwnedByCurrentRegion(target)
                ? target.getLocation().clone()
                : hu.taliann.icesmp.utils.PositionCache.get(target.getUniqueId());
        state.targetLocation = cached == null ? mob.getLocation().clone() : cached.clone();
        state.authoredCombat = true;
        state.combatUntilTick = Math.max(state.combatUntilTick,
                state.tick + Math.max(40L, Math.min(2_400L, durationTicks)));
        if (Bukkit.isOwnedByCurrentRegion(target)) mob.setTarget(target);
        trigger(mob, MobAbilityDefinition.Trigger.ON_COMBAT_ENTER, target, false);
    }

    /** Fires an event trigger without creating a parallel species-specific mechanic. */
    public void trigger(final Mob mob, final MobAbilityDefinition.Trigger trigger,
                        final Player provoker, final boolean assisted) {
        if (mob == null || trigger == null || !mob.isValid()) return;
        attach(mob);
        final RuntimeState state = states.get(mob.getUniqueId());
        if (state == null) return;
        if (!authoredTechniqueAllowed(mob, species.profile(mob.getType()))) return;
        if (provoker != null) {
            state.targetId = provoker.getUniqueId();
            final Location cached = Bukkit.isOwnedByCurrentRegion(provoker)
                    ? provoker.getLocation().clone()
                    : hu.taliann.icesmp.utils.PositionCache.get(provoker.getUniqueId());
            if (cached != null) state.targetLocation = cached.clone();
        }
        final MobAbilityDefinition chosen = state.definitions.stream()
                .filter(definition -> definition.triggers().contains(trigger))
                .filter(definition -> state.tick >= state.readyAtTick
                        .getOrDefault(definition.abilityId(), 0L))
                .filter(definition -> conditionsPass(mob, definition, state))
                .findFirst().orElse(null);
        if (chosen == null) return;
        final Location target = targetSnapshot(mob, chosen, state);
        if (chosen.targetRule() != MobAbilityDefinition.TargetRule.SELF && target == null) return;
        startCast(mob, chosen, state, target);
        CombatTelemetry.record(assisted ? "technique_assist_trigger" : "technique_event_trigger",
                chosen.abilityId());
    }

    /**
     * Deterministic authored-trigger seam for bounded encounter callbacks and runtime proof.
     * The requested definition must already belong to the entity's canonical kit and declare
     * the supplied typed trigger; callers cannot inject an arbitrary mechanic.
     */
    public boolean triggerTechnique(final Mob mob, final String abilityId,
                                    final MobAbilityDefinition.Trigger trigger) {
        if (mob == null || abilityId == null || trigger == null || !mob.isValid()) return false;
        attach(mob);
        final RuntimeState state = states.get(mob.getUniqueId());
        if (state == null || !authoredTechniqueAllowed(mob, species.profile(mob.getType()))) {
            return false;
        }
        final MobAbilityDefinition chosen = state.definitions.stream()
                .filter(definition -> definition.abilityId().equals(abilityId))
                .filter(definition -> definition.triggers().contains(trigger))
                .filter(definition -> conditionsPass(mob, definition, state))
                .findFirst().orElse(null);
        if (chosen == null) return false;
        final Location target = targetSnapshot(mob, chosen, state);
        if (chosen.targetRule() != MobAbilityDefinition.TargetRule.SELF && target == null) return false;
        final boolean started = startCast(mob, chosen, state, target);
        if (started) CombatTelemetry.record("technique_typed_trigger", chosen.abilityId());
        return started;
    }

    public void disengageTarget(final UUID targetId) {
        if (targetId == null) return;
        for (final RuntimeState state : states.values()) {
            if (!targetId.equals(state.targetId)) continue;
            state.mob.getScheduler().run(plugin, task -> {
                if (targetId.equals(state.targetId)) disengage(state);
            }, null);
        }
    }

    private void addIfAbsent(final List<MobAbilityDefinition> definitions, final String abilityId) {
        if (definitions.stream().noneMatch(definition -> definition.abilityId().equals(abilityId))) {
            abilities.find(abilityId).ifPresent(definitions::add);
        }
    }

    /** A response-only movement primitive does not consume rank combat-complexity budget. */
    private static boolean countsTowardTechniqueCap(final MobAbilityDefinition definition) {
        return definition.triggers().stream().anyMatch(trigger ->
                trigger != MobAbilityDefinition.Trigger.ON_PROVOKED);
    }

    private void tick(final Mob mob, final RuntimeState state) {
        if (!mob.isValid() || mob.isDead()) {
            detach(state);
            return;
        }
        state.tick += RUNTIME_STEP_TICKS;
        if (state.authoredCombat && state.targetId != null) {
            final Player liveTarget = Bukkit.getPlayer(state.targetId);
            final Location latest = hu.taliann.icesmp.utils.PositionCache.get(state.targetId);
            if (liveTarget == null || latest != null && (latest.getWorld() != mob.getWorld()
                    || latest.distanceSquared(mob.getLocation()) > 32.0D * 32.0D)) {
                disengage(state);
            } else if (latest != null) {
                state.targetLocation = latest;
            }
        }
        if (state.authoredCombat && state.tick >= state.combatUntilTick) {
            disengage(state);
        }
        if (state.paused || state.casting || state.tick < state.recoveryUntilTick
                || state.definitions.isEmpty()) return;
        final CreatureSpeciesPolicy policy = species.profile(mob.getType());
        if (!authoredTechniqueAllowed(mob, policy)) return;
        if (policy.disposition() == CreatureSpeciesPolicy.Disposition.PASSIVE
                && !state.authoredCombat) return;
        if (policy.disposition() == CreatureSpeciesPolicy.Disposition.NEUTRAL
                && !state.authoredCombat && mob.getTarget() == null) return;
        applyBehavior(mob, state);
        final MobAbilityDefinition threshold = state.pendingThresholds.pollFirst();
        final MobAbilityDefinition chosen = threshold == null
                ? nextTimerAbility(mob, state) : threshold;
        if (chosen == null) return;
        final Location target = targetSnapshot(mob, chosen, state);
        if (chosen.targetRule() != MobAbilityDefinition.TargetRule.SELF && target == null) return;
        startCast(mob, chosen, state, target);
    }

    private MobAbilityDefinition nextTimerAbility(final Mob mob, final RuntimeState state) {
        MobAbilityDefinition chosen = null;
        double chosenScore = Double.NEGATIVE_INFINITY;
        for (int offset = 0; offset < state.definitions.size(); offset++) {
            final int index = (state.rotationCursor + offset) % state.definitions.size();
            final MobAbilityDefinition candidate = state.definitions.get(index);
            if (candidate.triggers().contains(MobAbilityDefinition.Trigger.ON_TIMER)
                    && state.tick >= state.readyAtTick.getOrDefault(candidate.abilityId(), 0L)
                    && conditionsPass(mob, candidate, state)) {
                final double score = techniqueScore(mob, candidate, state, offset);
                if (score > chosenScore) {
                    chosen = candidate;
                    chosenScore = score;
                }
            }
        }
        if (chosen != null) {
            state.rotationCursor = (state.definitions.indexOf(chosen) + 1) % state.definitions.size();
            state.previousAbilityId = chosen.abilityId();
        }
        return chosen;
    }

    private static double techniqueScore(final Mob mob, final MobAbilityDefinition ability,
                                         final RuntimeState state, final int rotationOffset) {
        final double base = ability.tuning().getOrDefault("selection_weight", 1.0D);
        final double health = Math.max(0.0D, Math.min(1.0D, mob.getHealth()
                / Math.max(1.0D, mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? 20.0D : mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue())));
        final double distance = state.targetLocation == null ? ability.radius()
                : Math.sqrt(Math.max(0.0D, state.targetLocation.distanceSquared(mob.getLocation())));
        double context = switch (ability.kind()) {
            case CLEAVE, GROUND_SLAM, POISON_CLOUD -> distance <= ability.radius() + 1.0D ? 1.25D : -0.75D;
            case LUNGE -> distance > 3.0D ? 1.0D : -0.5D;
            case PROJECTILE_BURST, DELAYED_RUNE -> distance >= 5.0D ? 1.0D : -0.5D;
            case RETREAT -> distance <= 4.0D ? 1.25D : -0.5D;
            case SHIELD, HEAL_PULSE -> (1.0D - health) * 1.5D;
            default -> 0.0D;
        };
        context += state.behavior.techniqueWeight(ability.kind(), distance, health);
        if (ability.abilityId().equals(state.previousAbilityId)) context -= 2.0D;
        final long mixed = mob.getUniqueId().getLeastSignificantBits()
                ^ ability.abilityId().hashCode() ^ (state.castEpoch * 0x9E3779B97F4A7C15L);
        final double jitter = ((mixed >>> 8) & 0xFFL) / 255.0D * 0.24D - 0.12D;
        return base + context + jitter - rotationOffset * 0.01D;
    }

    private boolean startCast(final Mob mob, final MobAbilityDefinition chosen,
                              final RuntimeState state, final Location target) {
        if (state.paused || state.casting || state.tick < state.recoveryUntilTick
                || state.tick < state.readyAtTick.getOrDefault(chosen.abilityId(), 0L)) return false;
        state.casting = true;
        state.currentAbility = chosen;
        final long castEpoch = ++state.castEpoch;
        state.readyAtTick.put(chosen.abilityId(), state.tick + Math.max(10L,
                Math.round(chosen.cooldownTicks() / state.behavior.aggressionCadence())));
        CombatTelemetry.record("technique_cast", chosen.abilityId());
        telegraph(mob, chosen, target);
        try {
            mob.getScheduler().runDelayed(plugin, task -> {
                if (state.castEpoch != castEpoch) return;
                if (mob.isValid() && !mob.isDead()) {
                    execute(mob, chosen, target, state);
                    CombatTelemetry.record("technique_execute", chosen.abilityId());
                    if (scaling.getRank(mob).bossLike()) {
                        CombatTelemetry.record("boss_technique", chosen.abilityId());
                    }
                }
                state.recoveryUntilTick = state.tick + chosen.recoveryTicks();
                state.currentAbility = null;
                state.casting = false;
            }, () -> {
                if (state.castEpoch == castEpoch) {
                    state.currentAbility = null;
                    state.casting = false;
                }
                states.remove(mob.getUniqueId(), state);
            }, Math.max(1L, chosen.telegraphTicks()));
            return true;
        } catch (final RuntimeException rejected) {
            state.currentAbility = null;
            state.casting = false;
            states.remove(mob.getUniqueId(), state);
            CombatTelemetry.record("technique_schedule_rejected", chosen.abilityId());
            if (reportedScheduleRejections.add(chosen.abilityId())) {
                plugin.getLogger().warning("Mob technique schedule rejected ["
                        + chosen.abilityId() + "]: " + rejected);
            }
            return false;
        }
    }

    private Location targetSnapshot(final Mob mob, final MobAbilityDefinition definition,
                                    final RuntimeState state) {
        if (definition.targetRule() == MobAbilityDefinition.TargetRule.SELF) return mob.getLocation().clone();
        if (definition.targetRule() == MobAbilityDefinition.TargetRule.PROVOKER) {
            return state.targetLocation == null ? null : state.targetLocation.clone();
        }
        if (definition.targetRule() == MobAbilityDefinition.TargetRule.CURRENT_TARGET
                && mob.getTarget() instanceof Player target && Bukkit.isOwnedByCurrentRegion(target)
                && survivor(target)) {
            state.targetId = target.getUniqueId();
            state.targetLocation = target.getLocation().clone();
            return target.getLocation().clone();
        }
        if (definition.targetRule() == MobAbilityDefinition.TargetRule.CURRENT_TARGET
                && state.targetLocation != null) return state.targetLocation.clone();
        for (final Entity nearby : mob.getNearbyEntities(
                definition.radius(), definition.radius(), definition.radius())) {
            if (nearby instanceof Player player && Bukkit.isOwnedByCurrentRegion(player)
                    && survivor(player)) {
                state.targetId = player.getUniqueId();
                state.targetLocation = player.getLocation().clone();
                return state.targetLocation.clone();
            }
        }
        return null;
    }

    private boolean conditionsPass(final Mob mob, final MobAbilityDefinition definition,
                                   final RuntimeState state) {
        for (final MobTechniqueCondition condition : definition.conditions()) {
            switch (condition.type()) {
                case COMBAT_ACTIVE -> {
                    if (!state.authoredCombat && mob.getTarget() == null) return false;
                }
                case ADULT -> {
                    if (mob instanceof Ageable ageable && !ageable.isAdult()) return false;
                }
                case UNTAMED -> {
                    if (mob instanceof Tameable tameable && tameable.isTamed()) return false;
                }
                case HEALTH_BELOW -> {
                    final var maximum = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    final double max = maximum == null ? mob.getHealth() : maximum.getValue();
                    if (max <= 0.0D || mob.getHealth() / max > condition.value()) return false;
                }
                case DISTANCE_WITHIN -> {
                    if (state.targetLocation == null || state.targetLocation.getWorld() != mob.getWorld()
                            || mob.getLocation().distanceSquared(state.targetLocation)
                            > condition.value() * condition.value()) return false;
                }
            }
        }
        return true;
    }

    private static boolean authoredTechniqueAllowed(final Mob mob,
                                                     final CreatureSpeciesPolicy policy) {
        if (policy.disposition() == CreatureSpeciesPolicy.Disposition.NON_COMBAT) return false;
        if (mob instanceof Ageable ageable && !ageable.isAdult()
                && policy.babyPolicy() != CreatureSpeciesPolicy.BabyPolicy.FULL) return false;
        return !(mob instanceof Tameable tameable && tameable.isTamed()
                && policy.tamePolicy() == CreatureSpeciesPolicy.TamePolicy.OWNER_SAFE);
    }

    private void disengage(final RuntimeState state) {
        state.castEpoch++;
        state.casting = false;
        state.currentAbility = null;
        state.authoredCombat = false;
        state.combatUntilTick = 0L;
        state.targetId = null;
        state.targetLocation = null;
        if (state.mob.isValid()) {
            state.mob.setTarget(null);
            CreatureProfileService.setCombatState(state.mob, "IDLE");
        }
        CombatTelemetry.record("creature_disengage", state.mob.getType().name());
    }

    public void pause(final Mob mob) {
        final RuntimeState state = mob == null ? null : states.get(mob.getUniqueId());
        if (state == null) return;
        state.paused = true;
        state.castEpoch++;
        state.casting = false;
        state.currentAbility = null;
        CombatTelemetry.record("technique_pause", scaling.getTemplateId(mob));
    }

    public void resume(final Mob mob) {
        final RuntimeState state = mob == null ? null : states.get(mob.getUniqueId());
        if (state == null) return;
        state.paused = false;
        CombatTelemetry.record("technique_resume", scaling.getTemplateId(mob));
    }

    public void detach(final Mob mob) {
        final RuntimeState state = mob == null ? null : states.get(mob.getUniqueId());
        if (state != null) detach(state);
        final AuthoredCreatureSpawnService spawns = AuthoredCreatureSpawnService.current();
        if (spawns != null && mob != null) spawns.cleanupSummons(mob.getUniqueId());
    }

    private void detach(final RuntimeState state) {
        states.remove(state.mob.getUniqueId(), state);
        state.castEpoch++;
        if (state.task != null) state.task.cancel();
    }

    private void telegraph(final Mob mob, final MobAbilityDefinition definition,
                           final Location target) {
        final Location center = target == null ? mob.getLocation() : target;
        final MobAbilityDefinition.Presentation presentation = definition.presentation();
        final Particle particle = particle(presentation.telegraphParticle(), Particle.CRIT);
        ParticleUtil.spawn(center.getWorld(), particle, center.clone().add(0.0D, 0.2D, 0.0D),
                presentation.particleCount(), Math.min(3.0D, definition.radius()), 0.2D,
                Math.min(3.0D, definition.radius()), 0.01D);
        center.getWorld().playSound(center, sound(presentation.telegraphSound(),
                Sound.ENTITY_GOAT_PREPARE_RAM), presentation.volume(), presentation.pitch());
    }

    private void execute(final Mob mob, final MobAbilityDefinition definition,
                         final Location target, final RuntimeState state) {
        if (definition.kind() == MobAbilityDefinition.Kind.COMPOSITE) {
            executeComposite(mob, definition, target, state);
            impactPresentation(mob, definition, target);
            if (state.authoredCombat) CreatureProfileService.setCombatState(mob, "FIGHT");
            return;
        }
        switch (definition.kind()) {
            case LUNGE -> {
                if (target == null || target.getWorld() != mob.getWorld()) return;
                final Vector direction = target.toVector().subtract(mob.getLocation().toVector());
                if (direction.lengthSquared() > 0.01D) {
                    mob.setVelocity(direction.normalize().multiply(definition.power()).setY(0.25D));
                }
            }
            case GROUND_SLAM -> impactPlayers(mob, mob.getLocation(), definition.radius(),
                    definition.power(), true);
            case PROJECTILE_BURST -> {
                if (target == null || target.getWorld() != mob.getWorld()) return;
                final Vector center = target.toVector().subtract(mob.getEyeLocation().toVector())
                        .normalize();
                final int count = Math.max(1, Math.min(5, (int) Math.round(
                        definition.tuning().getOrDefault("projectiles", 3.0D))));
                for (int index = 0; index < count; index++) {
                    final Vector spread = center.clone().add(new Vector(
                            (index - (count - 1) / 2.0D) * 0.08D, 0.02D * index, 0.0D));
                    final Arrow projectile = mob.launchProjectile(Arrow.class,
                            spread.normalize().multiply(1.2D));
                    projectile.setDamage(definition.power());
                    projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                }
            }
            case SHIELD -> mob.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    Math.max(40, (int) Math.min(400, definition.cooldownTicks() / 2)),
                    definition.power() >= 0.5D ? 1 : 0, false, true, true));
            case HEAL_PULSE -> {
                final double maximum = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                        ? mob.getHealth() : mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                mob.setHealth(Math.min(maximum, mob.getHealth() + maximum * definition.power()));
            }
            case SUMMON -> summonAdds(mob, definition);
            case CLEAVE -> impactPlayers(mob, mob.getLocation(), definition.radius(),
                    definition.power(), false);
            case POISON_CLOUD -> poisonPlayers(mob, definition);
            case DELAYED_RUNE -> {
                if (target != null) impactPlayers(mob, target, definition.radius(),
                        definition.power(), false);
            }
            case RETREAT -> {
                if (target == null || target.getWorld() != mob.getWorld()) return;
                final Vector direction = mob.getLocation().toVector().subtract(target.toVector());
                if (direction.lengthSquared() > 0.01D) {
                    mob.setVelocity(direction.normalize().multiply(definition.power()).setY(0.25D));
                }
            }
            case ALLY_BUFF -> buffAllies(mob, definition);
            case COMPOSITE -> throw new IllegalStateException("composite dispatch escaped");
        }
        impactPresentation(mob, definition, target);
    }

    private void applyBehavior(final Mob mob, final RuntimeState state) {
        if (state.tick % 40L != 0L) return;
        Location target = state.targetLocation;
        if (mob.getTarget() instanceof Player player && Bukkit.isOwnedByCurrentRegion(player)
                && survivor(player)) {
            state.targetId = player.getUniqueId();
            target = player.getLocation().clone();
            state.targetLocation = target;
        }
        if (target == null || target.getWorld() != mob.getWorld()) return;
        final Vector delta = target.toVector().subtract(mob.getLocation().toVector());
        final double distance = delta.length();
        if (distance > state.behavior.maximumPursuitRange()) {
            if (state.authoredCombat) disengage(state);
            return;
        }
        final double sample = deterministicUnit(mob.getUniqueId(), state.tick);
        if (distance < state.behavior.minimumComfortRange()
                && sample < state.behavior.retreatTendency() && distance > 0.05D) {
            mob.setVelocity(delta.normalize().multiply(-0.22D
                    - state.behavior.retreatTendency() * 0.18D).setY(0.12D));
            CombatTelemetry.record("behavior_retreat", scaling.getTemplateId(mob));
            return;
        }
        if (Math.abs(distance - state.behavior.preferredRange()) <= 3.0D
                && sample < state.behavior.repositionTendency() && distance > 0.05D) {
            final Vector side = new Vector(-delta.getZ(), 0.0D, delta.getX()).normalize();
            if (((mob.getUniqueId().getLeastSignificantBits() ^ state.tick) & 1L) == 0L) {
                side.multiply(-1.0D);
            }
            mob.setVelocity(side.multiply(0.12D + state.behavior.strafeTendency() * 0.18D)
                    .setY(mob.getVelocity().getY()));
            CombatTelemetry.record("behavior_reposition", scaling.getTemplateId(mob));
        } else if (distance > state.behavior.preferredRange() + 2.0D
                && state.behavior.chasePressure() > 0.55D) {
            mob.getPathfinder().moveTo(target, 0.85D + state.behavior.chasePressure() * 0.35D);
            CombatTelemetry.record("behavior_pursuit", scaling.getTemplateId(mob));
        }
    }

    private void impactPresentation(final Mob mob, final MobAbilityDefinition definition,
                                    final Location target) {
        final MobAbilityDefinition.Presentation presentation = definition.presentation();
        final Location center = definition.targetRule() == MobAbilityDefinition.TargetRule.SELF
                || target == null ? mob.getLocation() : target;
        ParticleUtil.spawn(center.getWorld(), particle(presentation.impactParticle(), Particle.POOF),
                center.clone().add(0.0D, 0.25D, 0.0D),
                Math.max(4, presentation.particleCount() / 2),
                Math.min(2.5D, definition.radius()), 0.25D,
                Math.min(2.5D, definition.radius()), 0.02D);
        center.getWorld().playSound(center, sound(presentation.impactSound(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG), presentation.volume(),
                Math.min(2.0F, presentation.pitch() + 0.05F));
    }

    private static Particle particle(final String raw, final Particle fallback) {
        try {
            return Particle.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            return fallback;
        }
    }

    private static Sound sound(final String raw, final Sound fallback) {
        final Sound value = org.bukkit.Registry.SOUNDS.get(NamespacedKey.minecraft(raw));
        return value == null ? fallback : value;
    }

    private static double deterministicUnit(final UUID id, final long tick) {
        long value = id.getLeastSignificantBits() ^ tick * 0x9E3779B97F4A7C15L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return (value >>> 11) * 0x1.0p-53;
    }

    private void executeComposite(final Mob mob, final MobAbilityDefinition definition,
                                  final Location target, final RuntimeState state) {
        for (final MobTechniqueAction action : definition.actions()) {
            final double levelMultiplier = Math.min(action.parameter("maximum_level_multiplier", 1.7D),
                    1.0D + Math.max(0, scaling.getLevel(mob) - 1)
                            * action.parameter("per_level", 0.01D));
            switch (action.type()) {
                case DASH -> move(mob, target, action.parameter("strength", definition.power()), false);
                case RETREAT -> move(mob, target, action.parameter("strength", definition.power()), true);
                case DAMAGE -> {
                    final double damage = Math.max(0.0D, Math.min(40.0D,
                            action.parameter("amount", definition.power()) * levelMultiplier));
                    if (action.target() == MobTechniqueAction.Target.NEARBY_PLAYERS) {
                        impactPlayers(mob, mob.getLocation(),
                                Math.max(0.5D, Math.min(definition.radius(),
                                        action.parameter("radius", definition.radius()))),
                                damage, false);
                    } else {
                        impactTarget(mob, state, target, damage, 0.0D);
                    }
                }
                case KNOCKBACK -> {
                    final double strength = Math.max(0.0D, Math.min(1.5D,
                            action.parameter("strength", 0.55D) * levelMultiplier));
                    if (action.target() == MobTechniqueAction.Target.NEARBY_PLAYERS) {
                        impactPlayers(mob, mob.getLocation(),
                                Math.max(0.5D, Math.min(definition.radius(),
                                        action.parameter("radius", definition.radius()))),
                                0.0D, strength);
                    } else {
                        impactTarget(mob, state, target, 0.0D, strength);
                    }
                }
                case GUARD -> mob.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                        Math.max(20, Math.min(400, (int) Math.round(
                                action.parameter("duration_ticks", 100.0D)))),
                        Math.max(0, Math.min(2, (int) Math.round(
                                action.parameter("amplifier", 0.0D)))), false, true, true));
                case APPLY_EFFECT -> applyEffect(mob, definition, action);
                case SUMMON_TEMPLATE -> summonTemplateAdds(mob, definition, action);
            }
        }
    }

    private void applyEffect(final Mob mob, final MobAbilityDefinition definition,
                             final MobTechniqueAction action) {
        final PotionEffectType effect = org.bukkit.Registry.EFFECT.get(
                NamespacedKey.minecraft(action.reference()));
        if (effect == null) return;
        final int duration = Math.max(20, Math.min(1_200,
                (int) Math.round(action.parameter("duration_ticks", 100.0D))));
        final int amplifier = Math.max(0, Math.min(4,
                (int) Math.round(action.parameter("amplifier", 0.0D))));
        if (action.target() == MobTechniqueAction.Target.SELF) {
            mob.addPotionEffect(new PotionEffect(effect, duration, amplifier, false, true, true));
            return;
        }
        final Location center = mob.getLocation().clone();
        final double radius = Math.max(0.5D, Math.min(definition.radius(),
                action.parameter("radius", definition.radius())));
        int affected = 0;
        for (final Entity entity : mob.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player player) || ++affected > 32) continue;
            player.getScheduler().run(plugin, task -> {
                if (survivor(player) && player.getWorld() == center.getWorld()
                        && player.getLocation().distanceSquared(center) <= radius * radius) {
                    player.addPotionEffect(new PotionEffect(effect, duration, amplifier,
                            false, true, true));
                }
            }, null);
        }
    }

    private void summonTemplateAdds(final Mob mob, final MobAbilityDefinition definition,
                                    final MobTechniqueAction action) {
        final AuthoredCreatureSpawnService spawns = AuthoredCreatureSpawnService.current();
        if (spawns == null) return;
        final int globalMaximum = Math.max(0, Math.min(8,
                config.getInt("mob-scaling.abilities.maximum-summons-per-cast", 3)));
        final int count = Math.min(globalMaximum, Math.max(1, Math.min(8,
                (int) Math.round(action.parameter("count", definition.maxSummons())))));
        final long lifespan = Math.max(40L, Math.min(1_200L,
                (long) action.parameter("lifespan_ticks", config.getLong(
                        "mob-scaling.abilities.summon-lifespan-ticks", 300L))));
        for (int index = 0; index < count; index++) {
            final Location at = mob.getLocation().clone().add(
                    ThreadLocalRandom.current().nextDouble(-2.5D, 2.5D), 0.0D,
                    ThreadLocalRandom.current().nextDouble(-2.5D, 2.5D));
            try {
                spawns.spawn(at, AuthoredCreatureSpawnService.Request.template(
                        "ability_summon", "summon:" + mob.getUniqueId(), "add",
                        action.reference(), Math.max(1, scaling.getLevel(mob)),
                        AuthoredCreatureSpawnService.RewardOwner.NONE, true,
                        1.0D, 1.0D, lifespan).summonedBy(mob.getUniqueId()));
                CombatTelemetry.record("authored_summon", action.reference());
            } catch (final RuntimeException invalid) {
                plugin.getLogger().warning("Authored summon failed closed: " + action.reference()
                        + " (" + invalid.getMessage() + ")");
            }
        }
    }

    private static void move(final Mob mob, final Location target,
                             final double rawStrength, final boolean retreat) {
        if (target == null || target.getWorld() != mob.getWorld()) return;
        final Vector direction = retreat
                ? mob.getLocation().toVector().subtract(target.toVector())
                : target.toVector().subtract(mob.getLocation().toVector());
        if (direction.lengthSquared() <= 0.01D) return;
        final double strength = Math.max(0.0D, Math.min(1.6D, rawStrength));
        mob.setVelocity(direction.normalize().multiply(strength).setY(
                retreat ? 0.20D : 0.28D));
    }

    private void impactTarget(final Mob caster, final RuntimeState state,
                              final Location targetSnapshot, final double damage,
                              final double knockback) {
        if (state.targetId == null || targetSnapshot == null) return;
        final Location source = caster.getLocation().clone();
        final Player player = Bukkit.getPlayer(state.targetId);
        if (player == null) return;
        player.getScheduler().run(plugin, task -> {
            if (!survivor(player) || player.getWorld() != targetSnapshot.getWorld()) return;
            final double maximumRange = 6.0D;
            if (player.getLocation().distanceSquared(targetSnapshot) > maximumRange * maximumRange
                    || player.getLocation().distanceSquared(source) > maximumRange * maximumRange) return;
            if (damage > 0.0D) {
                player.damage(damage);
                CombatTelemetry.record("technique_hit", "composite");
            }
            if (knockback > 0.0D) {
                final Vector vector = player.getLocation().toVector()
                        .subtract(source.toVector());
                if (vector.lengthSquared() > 0.01D) {
                    player.setVelocity(vector.normalize().multiply(knockback).setY(0.32D));
                }
            }
        }, null);
    }

    private void summonAdds(final Mob mob, final MobAbilityDefinition definition) {
        final int count = Math.min(definition.maxSummons(), Math.max(0,
                config.getInt("mob-scaling.abilities.maximum-summons-per-cast", 3)));
        final long lifespan = Math.max(40L, config.getLong(
                "mob-scaling.abilities.summon-lifespan-ticks", 300L));
        for (int index = 0; index < count; index++) {
            final Location at = mob.getLocation().clone().add(
                    ThreadLocalRandom.current().nextDouble(-2.5D, 2.5D), 0.0D,
                    ThreadLocalRandom.current().nextDouble(-2.5D, 2.5D));
            final Skeleton add = mob.getWorld().spawn(at, Skeleton.class);
            EventSpawnGuard.prepare(add);
            add.getPersistentDataContainer().set(summonOwnerKey, PersistentDataType.STRING,
                    mob.getUniqueId().toString());
            add.getScheduler().runDelayed(plugin, task -> {
                if (add.isValid()) add.remove();
            }, null, lifespan);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAffixDamage(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity attacker
                && event.getEntity() instanceof Player player) {
            final List<EliteAffix> affixes = scaling.getAffixes(attacker);
            if (affixes.contains(EliteAffix.FROSTBOUND)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        50, 0, false, true, true));
            }
            if (affixes.contains(EliteAffix.VAMPIRIC)) {
                final double healing = Math.min(12.0D, event.getFinalDamage() * 0.25D);
                attacker.getScheduler().run(plugin, task -> heal(attacker, healing), null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAffixHurt(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        final RuntimeState state = states.get(mob.getUniqueId());
        if (state != null && state.paused) return;
        if (state != null && state.casting && state.currentAbility != null
                && state.currentAbility.interruptible()
                && event.getFinalDamage() >= state.currentAbility.tuning()
                .getOrDefault("interrupt-damage", 1.0D)) {
            final String interrupted = state.currentAbility.abilityId();
            state.castEpoch++;
            state.casting = false;
            state.currentAbility = null;
            state.recoveryUntilTick = state.tick + 20L;
            CombatTelemetry.record("technique_interrupt", interrupted);
        }
        final List<EliteAffix> affixes = scaling.getAffixes(mob);
        final double projected = mob.getHealth() - event.getFinalDamage();
        final double maximum = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? mob.getHealth() : mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        if (state != null && projected > 0.0D && maximum > 0.0D) {
            final double fraction = projected / maximum;
            for (final MobAbilityDefinition definition : state.definitions) {
                if (!definition.triggers().contains(MobAbilityDefinition.Trigger.HEALTH_THRESHOLD)
                        || state.consumedThresholds.contains(definition.abilityId())
                        || !thresholdConditionsPass(state.mob, definition, state, fraction)) continue;
                state.consumedThresholds.add(definition.abilityId());
                state.pendingThresholds.addLast(definition);
                CombatTelemetry.record("boss_phase_transition", definition.abilityId());
            }
        }
        if (affixes.contains(EliteAffix.FRENZIED) && projected > 0.0D
                && projected <= maximum * 0.5D && !mob.getPersistentDataContainer()
                .has(frenziedKey, PersistentDataType.BYTE)) {
            mob.getPersistentDataContainer().set(frenziedKey, PersistentDataType.BYTE, (byte) 1);
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    Integer.MAX_VALUE, 0, false, true, true));
            mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                    Integer.MAX_VALUE, 0, false, true, true));
        }
        if (affixes.contains(EliteAffix.VOLATILE) && projected <= 0.0D
                && !mob.getPersistentDataContainer().has(volatileArmedKey, PersistentDataType.BYTE)) {
            mob.getPersistentDataContainer().set(volatileArmedKey, PersistentDataType.BYTE, (byte) 1);
            armVolatile(mob.getLocation().clone());
        }
    }

    private boolean thresholdConditionsPass(final Mob mob, final MobAbilityDefinition definition,
                                            final RuntimeState state, final double fraction) {
        for (final MobTechniqueCondition condition : definition.conditions()) {
            if (condition.type() == MobTechniqueCondition.Type.HEALTH_BELOW) {
                if (fraction > condition.value()) return false;
            } else if (!conditionPass(mob, condition, state)) return false;
        }
        return true;
    }

    private boolean conditionPass(final Mob mob, final MobTechniqueCondition condition,
                                  final RuntimeState state) {
        return switch (condition.type()) {
            case COMBAT_ACTIVE -> state.authoredCombat || mob.getTarget() != null;
            case ADULT -> !(mob instanceof Ageable ageable) || ageable.isAdult();
            case UNTAMED -> !(mob instanceof Tameable tameable) || !tameable.isTamed();
            case HEALTH_BELOW -> {
                final var maximum = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                final double max = maximum == null ? mob.getHealth() : maximum.getValue();
                yield max > 0.0D && mob.getHealth() / max <= condition.value();
            }
            case DISTANCE_WITHIN -> state.targetLocation != null
                    && state.targetLocation.getWorld() == mob.getWorld()
                    && mob.getLocation().distanceSquared(state.targetLocation)
                    <= condition.value() * condition.value();
        };
    }

    private void armVolatile(final Location center) {
        ParticleUtil.spawn(center.getWorld(), Particle.FLAME, center, 36, 2.5D, 0.2D, 2.5D, 0.02D);
        center.getWorld().playSound(center, Sound.ENTITY_CREEPER_PRIMED, 1.2F, 1.0F);
        try {
            plugin.getServer().getRegionScheduler().runDelayed(plugin, center, task -> {
                ParticleUtil.spawn(center.getWorld(), Particle.EXPLOSION, center, 2);
                for (final Player player : center.getWorld().getNearbyPlayers(center, 3.0D)) {
                    player.getScheduler().run(plugin, hit -> {
                        if (survivor(player) && player.getWorld() == center.getWorld()
                                && player.getLocation().distanceSquared(center) <= 9.0D) {
                            player.damage(Math.max(1.0D, config.getDouble(
                                    "mob-scaling.affixes.volatile-damage", 5.0D)));
                        }
                    }, null);
                }
            }, 30L);
        } catch (final RuntimeException rejected) {
            // Scheduler rejection leaves only the harmless telegraph; no stale state is retained.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final EntityDeathEvent event) {
        final RuntimeState state = states.remove(event.getEntity().getUniqueId());
        if (state != null) {
            state.castEpoch++;
            if (state.task != null) state.task.cancel();
            final String templateId = scaling.getTemplateId(event.getEntity());
            if (templateId != null && !templateId.isBlank()) {
                CombatTelemetry.record("template_death", templateId);
                CombatTelemetry.add("template_lifetime_seconds", templateId,
                        Math.min(86_400L, Math.max(0L,
                                (System.nanoTime() - state.attachedAtNanos) / 1_000_000_000L)));
                if (event.getEntity().getKiller() != null) {
                    CombatTelemetry.record("template_player_kill", templateId);
                }
            }
        }
        final AuthoredCreatureSpawnService spawns = AuthoredCreatureSpawnService.current();
        if (spawns != null) spawns.cleanupSummons(event.getEntity().getUniqueId());
    }

    public void shutdown() {
        if (!CombatTelemetry.snapshot().isEmpty()) {
            plugin.getLogger().info("Combat telemetry aggregate: " + CombatTelemetry.snapshot());
        }
        for (final RuntimeState state : List.copyOf(states.values())) {
            state.castEpoch++;
            if (state.task != null) state.task.cancel();
        }
        states.clear();
        CombatTelemetry.clear();
    }

    public int activeStateCount() { return states.size(); }

    /** Read-only inspection seam; never exposes seeds, PDC internals or per-player data. */
    public List<String> activeAbilityIds(final Mob mob) {
        final RuntimeState state = mob == null ? null : states.get(mob.getUniqueId());
        return state == null ? List.of() : state.definitions.stream()
                .map(MobAbilityDefinition::abilityId).toList();
    }

    /** Bounded lifecycle flags for admin/runtime diagnostics; contains no identity or PDC data. */
    public String activeStateSummary(final Mob mob) {
        final RuntimeState state = mob == null ? null : states.get(mob.getUniqueId());
        if (state == null) return "detached";
        return "paused=" + state.paused + ",casting=" + state.casting
                + ",tick=" + state.tick + ",recovery=" + state.recoveryUntilTick
                + ",ready=" + state.readyAtTick;
    }

    private static void heal(final LivingEntity entity, final double amount) {
        if (!entity.isValid() || entity.isDead() || amount <= 0.0D) return;
        final var max = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        entity.setHealth(Math.min(max == null ? entity.getHealth() : max.getValue(),
                entity.getHealth() + amount));
    }

    private void impactPlayers(final Mob caster, final Location center,
                               final double radius, final double damage,
                               final boolean knockback) {
        impactPlayers(caster, center, radius, damage, knockback ? 0.8D : 0.0D);
    }

    private void impactPlayers(final Mob caster, final Location center,
                               final double radius, final double damage,
                               final double knockback) {
        for (final Entity nearby : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Player player)) continue;
            player.getScheduler().run(plugin, task -> {
                if (!survivor(player) || player.getWorld() != center.getWorld()
                        || player.getLocation().distanceSquared(center) > radius * radius) return;
                if (damage > 0.0D) {
                    player.damage(damage);
                    CombatTelemetry.record("technique_hit", "direct");
                }
                if (knockback > 0.0D) {
                    final Vector vector = player.getLocation().toVector().subtract(center.toVector());
                    if (vector.lengthSquared() > 0.01D) player.setVelocity(
                            vector.normalize().multiply(knockback).setY(0.45D));
                }
            }, null);
        }
    }

    private void poisonPlayers(final Mob caster, final MobAbilityDefinition definition) {
        final Location center = caster.getLocation().clone();
        final int duration = Math.max(20, Math.min(200, (int) Math.round(
                definition.tuning().getOrDefault("duration-ticks", 80.0D))));
        final int amplifier = Math.max(0, Math.min(2, (int) Math.round(
                definition.tuning().getOrDefault("amplifier", 0.0D))));
        for (final Entity nearby : caster.getNearbyEntities(
                definition.radius(), definition.radius(), definition.radius())) {
            if (!(nearby instanceof Player player)) continue;
            player.getScheduler().run(plugin, task -> {
                if (!survivor(player) || player.getWorld() != center.getWorld()
                        || player.getLocation().distanceSquared(center)
                        > definition.radius() * definition.radius()) return;
                player.damage(definition.power());
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                        duration, amplifier, false, true, true));
                CombatTelemetry.record("technique_hit", definition.abilityId());
            }, null);
        }
    }

    private void buffAllies(final Mob caster, final MobAbilityDefinition definition) {
        final int duration = Math.max(40, Math.min(400, (int) Math.round(
                definition.tuning().getOrDefault("duration-ticks", 120.0D))));
        int affected = 0;
        for (final Entity nearby : caster.getNearbyEntities(
                definition.radius(), definition.radius(), definition.radius())) {
            if (!(nearby instanceof Mob ally) || !Bukkit.isOwnedByCurrentRegion(ally)) continue;
            ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                    duration, 0, false, true, true));
            if (++affected >= 6) break;
        }
    }

    private static boolean survivor(final Player player) {
        return player.isOnline() && (player.getGameMode() == GameMode.SURVIVAL
                || player.getGameMode() == GameMode.ADVENTURE);
    }

    private MobArchetype archetype(final LivingEntity entity) {
        final String raw = scaling.getArchetypeId(entity);
        if (raw == null || raw.isBlank()) return null;
        try {
            return MobArchetype.parse(raw);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int maximumTechniques(final MobRank rank) {
        return switch (rank) {
            case NORMAL -> 1;
            case VETERAN -> 2;
            case ELITE -> 3;
            case CHAMPION, MINIBOSS -> 4;
            case BOSS, WORLD_BOSS -> 5;
        };
    }
}
