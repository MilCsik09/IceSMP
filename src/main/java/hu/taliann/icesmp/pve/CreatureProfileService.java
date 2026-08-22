package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.managers.MobScalingManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Assigns stable profiles and routes peaceful provocation into the common ability lifecycle. */
public final class CreatureProfileService implements Listener {
    public static final int PROFILE_VERSION = 1;
    private static final NamespacedKey REWARD_KEY = NamespacedKey.fromString("icesmp:creature_reward_profile");
    private static final NamespacedKey SPAWN_SOURCE_KEY = NamespacedKey.fromString("icesmp:creature_spawn_source");
    private static final NamespacedKey DISPOSITION_KEY = NamespacedKey.fromString("icesmp:creature_disposition");
    private static final NamespacedKey TEMPERAMENT_KEY = NamespacedKey.fromString("icesmp:creature_temperament");
    private static final NamespacedKey REACTION_KEY = NamespacedKey.fromString("icesmp:creature_reaction");
    private static final NamespacedKey COMBAT_STATE_KEY = NamespacedKey.fromString("icesmp:creature_combat_state");
    private static final NamespacedKey PROFILE_VERSION_KEY = NamespacedKey.fromString("icesmp:creature_profile_version");
    private static final NamespacedKey REACTION_READY_KEY = NamespacedKey.fromString("icesmp:creature_reaction_ready");
    private static final NamespacedKey SOCIAL_READY_KEY = NamespacedKey.fromString("icesmp:creature_social_ready");

    public record Projection(String species, int level, MobRank rank,
                             CreatureSpeciesPolicy.Disposition disposition,
                             CreatureSpeciesPolicy.Temperament temperament,
                             CreatureSpeciesPolicy.Reaction reaction,
                             MobArchetype archetype, List<String> techniques,
                             CreatureSpeciesPolicy.RewardProfile rewardProfile,
                             String spawnSource, String combatState) { }

    private final JavaPlugin plugin;
    private final CreatureSpeciesRegistry species;
    private final MobScalingManager scaling;
    private final MobAbilityRuntime runtime;

    public CreatureProfileService(final JavaPlugin plugin, final CreatureSpeciesRegistry species,
                                  final MobScalingManager scaling, final MobAbilityRuntime runtime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.species = Objects.requireNonNull(species, "species");
        this.scaling = Objects.requireNonNull(scaling, "scaling");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        final LivingEntity entity = event.getEntity();
        assign(entity, event.getSpawnReason());
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) {
            entity.getScheduler().runDelayed(plugin, task -> {
                if (entity.isValid() && scaling.getLevel(entity) == 0) {
                    scaling.applyScaling(entity, CreatureSpawnEvent.SpawnReason.DEFAULT);
                }
            }, null, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(final EntitiesLoadEvent event) {
        for (final Entity raw : event.getEntities()) {
            if (!(raw instanceof LivingEntity living) || raw instanceof Player) continue;
            living.getScheduler().run(plugin, task -> {
                if (!living.isValid()) return;
                assign(living, CreatureSpawnEvent.SpawnReason.DEFAULT);
                if (scaling.getLevel(living) == 0) {
                    scaling.applyScaling(living, CreatureSpawnEvent.SpawnReason.DEFAULT);
                }
                runtime.attach(living);
            }, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProvoked(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mob creature)) return;
        final Player provoker = responsiblePlayer(event.getDamager());
        if (provoker == null) return;
        final CreatureSpeciesPolicy policy = species.profile(creature.getType());
        if (policy.provocationPolicy() == CreatureSpeciesPolicy.ProvocationPolicy.NONE
                || !combatEligible(creature, policy)) return;
        CombatTelemetry.record("creature_provocation", policy.speciesId());
        runtime.trigger(creature, MobAbilityDefinition.Trigger.ON_DAMAGED, provoker, false);

        if (policy.disposition() == CreatureSpeciesPolicy.Disposition.NEUTRAL) {
            runtime.trigger(creature, MobAbilityDefinition.Trigger.ON_PROVOKED, provoker, false);
            return;
        }
        if (policy.disposition() != CreatureSpeciesPolicy.Disposition.PASSIVE
                || policy.provocationPolicy() != CreatureSpeciesPolicy.ProvocationPolicy.DIRECT_PLAYER
                || !claimReactionCooldown(creature, policy)) return;

        final CreatureSpeciesPolicy.Reaction reaction = reaction(creature, policy);
        switch (reaction) {
            case NONE -> { }
            case FLEE -> {
                setCombatState(creature, "FLEE");
                CombatTelemetry.record("creature_flee", policy.speciesId());
                runtime.trigger(creature, MobAbilityDefinition.Trigger.ON_PROVOKED, provoker, false);
                clearStateLater(creature, 80L);
            }
            case WARN, FIGHT -> {
                setCombatState(creature, reaction == CreatureSpeciesPolicy.Reaction.WARN ? "WARN" : "FIGHT");
                CombatTelemetry.record(reaction == CreatureSpeciesPolicy.Reaction.WARN
                        ? "creature_warning" : "creature_fight", policy.speciesId());
                runtime.enterCombat(creature, provoker, policy.temperamentPolicy().combatTicks());
                assist(creature, provoker, policy);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        runtime.disengageTarget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        runtime.disengageTarget(event.getPlayer().getUniqueId());
    }

    public Projection projection(final LivingEntity entity) {
        final CreatureSpeciesPolicy policy = species.profile(entity.getType());
        return new Projection(policy.speciesId(), scaling.getLevel(entity), scaling.getRank(entity),
                policy.disposition(), temperament(entity, policy), reaction(entity, policy),
                policy.archetype(), policy.techniquesFor(scaling.getRank(entity)), rewardProfile(entity),
                value(entity, SPAWN_SOURCE_KEY, "UNKNOWN"), value(entity, COMBAT_STATE_KEY, "IDLE"));
    }

    public void assign(final LivingEntity entity, final CreatureSpawnEvent.SpawnReason reason) {
        if (entity == null || entity instanceof Player) return;
        final CreatureSpeciesPolicy policy = species.profile(entity.getType());
        final var pdc = entity.getPersistentDataContainer();
        pdc.set(PROFILE_VERSION_KEY, PersistentDataType.INTEGER, PROFILE_VERSION);
        pdc.set(DISPOSITION_KEY, PersistentDataType.STRING, policy.disposition().name());
        if (!pdc.has(SPAWN_SOURCE_KEY, PersistentDataType.STRING)) {
            pdc.set(SPAWN_SOURCE_KEY, PersistentDataType.STRING,
                    reason == null ? "UNKNOWN" : reason.name());
        }
        if (!pdc.has(REWARD_KEY, PersistentDataType.STRING)) {
            pdc.set(REWARD_KEY, PersistentDataType.STRING, policy.rewardProfile().name());
        }
        temperament(entity, policy);
        reaction(entity, policy);
        CombatTelemetry.record("creature_profile", policy.speciesId());
    }

    private CreatureSpeciesPolicy.Temperament temperament(final LivingEntity entity,
                                                           final CreatureSpeciesPolicy policy) {
        final String stored = value(entity, TEMPERAMENT_KEY, "");
        if (!stored.isBlank()) {
            try {
                final CreatureSpeciesPolicy.Temperament value = CreatureSpeciesPolicy.Temperament.valueOf(stored);
                if (policy.allowedTemperaments().contains(value)) return value;
            } catch (final IllegalArgumentException ignored) { }
        }
        final CreatureSpeciesPolicy.Temperament selected = policy.temperamentPolicy()
                .select(entity.getUniqueId(), policy.allowedTemperaments());
        if (selected == null) entity.getPersistentDataContainer().remove(TEMPERAMENT_KEY);
        else entity.getPersistentDataContainer().set(TEMPERAMENT_KEY, PersistentDataType.STRING, selected.name());
        return selected;
    }

    private CreatureSpeciesPolicy.Reaction reaction(final LivingEntity entity,
                                                     final CreatureSpeciesPolicy policy) {
        final String stored = value(entity, REACTION_KEY, "");
        if (!stored.isBlank()) {
            try {
                return CreatureSpeciesPolicy.Reaction.valueOf(stored);
            } catch (final IllegalArgumentException ignored) { }
        }
        final CreatureSpeciesPolicy.Temperament temperament = temperament(entity, policy);
        final CreatureSpeciesPolicy.Reaction selected = policy.disposition()
                == CreatureSpeciesPolicy.Disposition.PASSIVE
                ? policy.temperamentPolicy().reaction(entity.getUniqueId(), temperament)
                : CreatureSpeciesPolicy.Reaction.NONE;
        entity.getPersistentDataContainer().set(REACTION_KEY, PersistentDataType.STRING, selected.name());
        return selected;
    }

    private boolean claimReactionCooldown(final LivingEntity entity,
                                           final CreatureSpeciesPolicy policy) {
        final long now = entity.getWorld().getFullTime();
        final long ready = entity.getPersistentDataContainer().getOrDefault(
                REACTION_READY_KEY, PersistentDataType.LONG, 0L);
        if (now < ready) return false;
        entity.getPersistentDataContainer().set(REACTION_READY_KEY, PersistentDataType.LONG,
                now + Math.max(40L, policy.temperamentPolicy().combatTicks() / 2L));
        return true;
    }

    private void assist(final Mob source, final Player provoker,
                        final CreatureSpeciesPolicy policy) {
        final CreatureSpeciesPolicy.SocialPolicy social = policy.socialPolicy();
        if (social.relation() != CreatureSpeciesPolicy.SocialRelation.SAME_SPECIES
                || social.maximumAssistants() <= 0) return;
        int candidates = 0;
        int scheduled = 0;
        final EntityType sourceType = source.getType();
        final UUID provokerId = provoker.getUniqueId();
        for (final Entity raw : source.getNearbyEntities(social.radius(), social.radius(), social.radius())) {
            if (++candidates > social.maximumCandidates() || scheduled >= social.maximumAssistants()) break;
            if (!(raw instanceof Mob ally) || ally == source) continue;
            final boolean admitted = ally.getScheduler().run(plugin, task -> {
                if (!ally.isValid() || ally.getType() != sourceType) return;
                final CreatureSpeciesPolicy allyPolicy = species.profile(ally.getType());
                if (!combatEligible(ally, allyPolicy) || !social.requiredTemperaments()
                        .contains(temperament(ally, allyPolicy)) || !claimSocialCooldown(ally, social)) return;
                final Player currentProvoker = org.bukkit.Bukkit.getPlayer(provokerId);
                if (currentProvoker == null || !currentProvoker.isOnline()) return;
                setCombatState(ally, "FIGHT");
                runtime.enterCombat(ally, currentProvoker, allyPolicy.temperamentPolicy().combatTicks());
                CombatTelemetry.record("creature_social_assist", policy.speciesId());
            }, null) != null;
            if (admitted) scheduled++;
        }
    }

    private static boolean claimSocialCooldown(final LivingEntity entity,
                                               final CreatureSpeciesPolicy.SocialPolicy policy) {
        final long now = entity.getWorld().getFullTime();
        final long ready = entity.getPersistentDataContainer().getOrDefault(
                SOCIAL_READY_KEY, PersistentDataType.LONG, 0L);
        if (now < ready) return false;
        entity.getPersistentDataContainer().set(SOCIAL_READY_KEY, PersistentDataType.LONG,
                now + policy.cooldownTicks());
        return true;
    }

    private void clearStateLater(final Mob creature, final long ticks) {
        creature.getScheduler().runDelayed(plugin, task -> {
            if (creature.isValid()) setCombatState(creature, "IDLE");
        }, null, ticks);
    }

    private static boolean combatEligible(final LivingEntity entity,
                                          final CreatureSpeciesPolicy policy) {
        if (!entity.isValid() || entity.isDead()
                || policy.disposition() == CreatureSpeciesPolicy.Disposition.NON_COMBAT) return false;
        if (entity instanceof Ageable ageable && !ageable.isAdult()
                && policy.babyPolicy() != CreatureSpeciesPolicy.BabyPolicy.FULL) return false;
        return !(entity instanceof Tameable tameable && tameable.isTamed()
                && policy.tamePolicy() == CreatureSpeciesPolicy.TamePolicy.OWNER_SAFE);
    }

    private static Player responsiblePlayer(final Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            final ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) return player;
            if (source instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) return owner;
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) return owner;
        return null;
    }

    public static void markExplicitAuthoredReward(final LivingEntity entity) {
        if (entity != null) entity.getPersistentDataContainer().set(REWARD_KEY,
                PersistentDataType.STRING, CreatureSpeciesPolicy.RewardProfile.EXPLICIT_AUTHORED.name());
    }

    public static boolean authoredRewardEligible(final LivingEntity entity) {
        if (entity == null) return false;
        final CreatureSpeciesPolicy.RewardProfile profile = rewardProfile(entity);
        if (profile == CreatureSpeciesPolicy.RewardProfile.VANILLA_ONLY) return false;
        if (profile == CreatureSpeciesPolicy.RewardProfile.EXPLICIT_AUTHORED) return true;
        final String source = value(entity, SPAWN_SOURCE_KEY, entity instanceof Monster ? "LEGACY" : "UNKNOWN");
        return !source.equals("SPAWNER") && !source.equals("SPAWNER_EGG")
                && !source.equals("BREEDING") && !source.equals("COMMAND")
                && !source.equals("CUSTOM");
    }

    public static CreatureSpeciesPolicy.RewardProfile rewardProfile(final LivingEntity entity) {
        final String raw = value(entity, REWARD_KEY, entity instanceof Monster ? "HOSTILE" : "VANILLA_ONLY");
        try {
            return CreatureSpeciesPolicy.RewardProfile.valueOf(raw);
        } catch (final IllegalArgumentException ignored) {
            return CreatureSpeciesPolicy.RewardProfile.VANILLA_ONLY;
        }
    }

    public static CreatureSpeciesPolicy.Disposition disposition(final Entity entity) {
        if (!(entity instanceof LivingEntity living)) return CreatureSpeciesPolicy.Disposition.NON_COMBAT;
        final String raw = value(living, DISPOSITION_KEY,
                living instanceof Monster ? "HOSTILE" : "NON_COMBAT");
        try {
            return CreatureSpeciesPolicy.Disposition.valueOf(raw);
        } catch (final IllegalArgumentException ignored) {
            return CreatureSpeciesPolicy.Disposition.NON_COMBAT;
        }
    }

    public static String presentationStatus(final LivingEntity entity) {
        final String combat = value(entity, COMBAT_STATE_KEY, "IDLE");
        if (!combat.equals("IDLE")) return combat;
        final String temperament = value(entity, TEMPERAMENT_KEY, "");
        return temperament.isBlank() ? disposition(entity).name() : temperament;
    }

    public static void setCombatState(final LivingEntity entity, final String state) {
        if (entity == null) return;
        entity.getPersistentDataContainer().set(COMBAT_STATE_KEY, PersistentDataType.STRING,
                state == null ? "IDLE" : state.trim().toUpperCase(Locale.ROOT));
    }

    private static String value(final LivingEntity entity, final NamespacedKey key,
                                final String fallback) {
        return entity.getPersistentDataContainer().getOrDefault(
                key, PersistentDataType.STRING, fallback);
    }
}
