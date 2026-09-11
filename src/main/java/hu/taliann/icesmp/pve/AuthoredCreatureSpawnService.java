package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.managers.EventSpawnGuard;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.utils.TransientEntities;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded spawn seam shared by authored PvE orchestrators. It owns canonical profile attachment
 * and encounter metadata, while placement, sequencing and settlement remain event concerns.
 */
public final class AuthoredCreatureSpawnService {
    public enum RewardOwner { GENERIC, EVENT, NONE }

    public record Request(String sourceId, String encounterId, String role,
                          String templateId, EntityType entityType, Integer level,
                          MobRank rank, String archetypeId, RewardOwner rewardOwner,
                          boolean transientEntity, double participantHealthMultiplier,
                          double participantDamageMultiplier, long lifespanTicks,
                          UUID summonOwner) {
        public Request {
            sourceId = id(sourceId, "source id");
            encounterId = id(encounterId, "encounter id");
            role = id(role, "role");
            templateId = templateId == null || templateId.isBlank() ? null : id(templateId, "template id");
            if (templateId == null && (entityType == null || level == null || level < 1 || rank == null)) {
                throw new IllegalArgumentException("generic authored spawn requires entity, level and rank");
            }
            if (templateId != null && (entityType != null || rank != null || archetypeId != null)) {
                throw new IllegalArgumentException("template spawn cannot carry shadow entity/rank/archetype overrides");
            }
            rewardOwner = Objects.requireNonNull(rewardOwner, "reward owner");
            if (!Double.isFinite(participantHealthMultiplier)
                    || participantHealthMultiplier < 1.0D || participantHealthMultiplier > 16.0D
                    || !Double.isFinite(participantDamageMultiplier)
                    || participantDamageMultiplier < 0.5D || participantDamageMultiplier > 2.0D
                    || lifespanTicks < 0L || lifespanTicks > 72_000L) {
                throw new IllegalArgumentException("authored spawn context out of bounds");
            }
        }

        public static Request template(final String sourceId, final String encounterId,
                                       final String role, final String templateId,
                                       final Integer level, final RewardOwner rewardOwner,
                                       final boolean transientEntity, final double healthMultiplier,
                                       final double damageMultiplier, final long lifespanTicks) {
            return new Request(sourceId, encounterId, role, templateId, null, level,
                    null, null, rewardOwner, transientEntity, healthMultiplier,
                    damageMultiplier, lifespanTicks, null);
        }

        public static Request generic(final String sourceId, final String encounterId,
                                      final String role, final EntityType entityType,
                                      final int level, final MobRank rank,
                                      final String archetypeId, final RewardOwner rewardOwner,
                                      final boolean transientEntity, final long lifespanTicks) {
            return new Request(sourceId, encounterId, role, null, entityType, level, rank,
                    archetypeId, rewardOwner, transientEntity, 1.0D, 1.0D,
                    lifespanTicks, null);
        }

        public Request summonedBy(final UUID owner) {
            return new Request(sourceId, encounterId, role, templateId, entityType, level, rank,
                    archetypeId, rewardOwner, transientEntity, participantHealthMultiplier,
                    participantDamageMultiplier, lifespanTicks, owner);
        }
    }

    private static volatile AuthoredCreatureSpawnService current;
    private final JavaPlugin plugin;
    private final MobTemplateRegistry templates;
    private final MobScalingManager scaling;
    private final MobAbilityRuntime abilities;
    private final NamespacedKey sourceKey;
    private final NamespacedKey encounterKey;
    private final NamespacedKey roleKey;
    private final NamespacedKey rewardOwnerKey;
    private final NamespacedKey summonOwnerKey;
    private final ConcurrentHashMap<UUID, java.util.Set<UUID>> activeSummonIds =
            new ConcurrentHashMap<>();

    public AuthoredCreatureSpawnService(final JavaPlugin plugin, final MobTemplateRegistry templates,
                                        final MobScalingManager scaling,
                                        final MobAbilityRuntime abilities) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.scaling = Objects.requireNonNull(scaling, "scaling");
        this.abilities = Objects.requireNonNull(abilities, "abilities");
        this.sourceKey = new NamespacedKey(plugin, "authored_spawn_source");
        this.encounterKey = new NamespacedKey(plugin, "authored_encounter_id");
        this.roleKey = new NamespacedKey(plugin, "authored_creature_role");
        this.rewardOwnerKey = new NamespacedKey(plugin, "authored_reward_owner");
        this.summonOwnerKey = new NamespacedKey(plugin, "authored_summon_owner");
        current = this;
    }

    public static AuthoredCreatureSpawnService current() { return current; }

    public MobTemplate template(final String templateId) {
        return templates.require(templateId);
    }

    /** Must be invoked on the spawn location's owning region thread. */
    public Mob spawn(final Location location, final Request request) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(request, "request");
        if (location.getWorld() == null) return null;
        final MobTemplate template = request.templateId() == null
                ? null : templates.require(request.templateId());
        final EntityType type = template == null ? request.entityType()
                : EntityType.valueOf(template.entityType());
        final Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException("authored creature is not a spawnable Mob: " + type);
        }
        if (!Bukkit.isOwnedByCurrentRegion(location) || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4))
            throw new IllegalStateException("Authored spawn owner and loaded chunk required");
        final Mob mob = location.getWorld().spawn(location, entityClass.asSubclass(Mob.class), created -> stampOrigin(created, request));
        EventSpawnGuard.prepare(mob);
        final boolean genericReward = request.rewardOwner() == RewardOwner.GENERIC;
        if (template != null) {
            scaling.forceTemplate(mob, template.mobId(), request.level(), genericReward);
        } else {
            scaling.forceRankedLevel(mob, request.level(), request.rank(), null,
                    request.archetypeId(), genericReward);
        }
        if (request.participantHealthMultiplier() != 1.0D
                || request.participantDamageMultiplier() != 1.0D) {
            scaling.applyEncounterModifier(mob, request.participantHealthMultiplier(),
                    request.participantDamageMultiplier(), request.sourceId() + ":participants");
        }
        if (request.summonOwner() != null) {
            final UUID child = mob.getUniqueId();
            activeSummonIds.compute(request.summonOwner(), (owner, ids) -> {
                if (ids == null) ids = ConcurrentHashMap.newKeySet();
                ids.add(child); return ids;
            });
        }
        if (request.transientEntity()) {
            mob.setPersistent(false);
            mob.setRemoveWhenFarAway(false);
            TransientEntities.register(plugin, mob);
        }
        final UUID entityId = mob.getUniqueId(), parentId = request.summonOwner();
        if (request.lifespanTicks() > 0L) {
            if (mob.getScheduler().runDelayed(plugin, task -> remove(entityId, parentId), () -> forget(entityId, parentId), request.lifespanTicks()) == null)
                forget(entityId, parentId);
        }
        abilities.attach(mob);
        if ("world_weaver".equals(request.sourceId())) abilities.pause(mob);
        // A creature may move before refresh; each continuation resolves its UUID on that entity's owner.
        if (mob.getScheduler().runDelayed(plugin, task -> {
            final Mob owned = ownedMob(entityId);
            if (owned != null) abilities.refreshProfile(owned);
        }, () -> forget(entityId, parentId), 1L) == null) forget(entityId, parentId);
        CombatTelemetry.record("authored_template_spawn", template == null ? type.name() : template.mobId());
        return mob;
    }

    /** Spawn consumer runs before world activation, so causal/reward identity exists before spawn listeners. */
    private void stampOrigin(final Mob mob, final Request request) {
        if (request.transientEntity()) mob.setPersistent(false);
        if ("world_weaver".equals(request.sourceId())) { mob.setAI(false); mob.setInvulnerable(true); }
        final var pdc = mob.getPersistentDataContainer();
        pdc.set(sourceKey, PersistentDataType.STRING, request.sourceId());
        pdc.set(encounterKey, PersistentDataType.STRING, request.encounterId());
        pdc.set(roleKey, PersistentDataType.STRING, request.role());
        pdc.set(rewardOwnerKey, PersistentDataType.STRING, request.rewardOwner().name());
        if (request.summonOwner() != null) pdc.set(summonOwnerKey, PersistentDataType.STRING, request.summonOwner().toString());
    }

    /** Expected native origin prevents a delayed cleanup from touching a replacement entity. */
    public void cleanupSandboxFork(final UUID entityId, final UUID expectedOperation) {
        if (!plugin.isEnabled()) { TransientEntities.removeOnShutdown(entityId); return; }
        final Entity handle = Bukkit.getEntity(entityId);
        if (handle == null) return;
        try {
            handle.getScheduler().run(plugin, task -> {
                final Mob mob = ownedMob(entityId);
                if (mob == null || sandboxEventOrigin(mob).filter(origin -> origin.instanceId().equals(expectedOperation)).isEmpty()) return;
                abilities.detach(mob); remove(entityId, null);
            }, () -> { });
        } catch (org.bukkit.plugin.IllegalPluginAccessException disabled) { TransientEntities.removeOnShutdown(entityId); }
    }
    public void cleanupSummons(final UUID owner) {
        final java.util.Set<UUID> ids = activeSummonIds.remove(owner);
        if (ids == null) return;
        for (final UUID id : java.util.Set.copyOf(ids)) {
            TransientEntities.removeById(plugin, id);
        }
    }

    public void applyParticipantModifier(final Mob mob, final double healthMultiplier,
                                         final double damageMultiplier, final String provenance) {
        scaling.applyEncounterModifier(mob, healthMultiplier, damageMultiplier, provenance);
    }

    public String statProvenance(final Mob mob) {
        return scaling.encounterStatProvenance(mob);
    }

    public void pause(final Mob mob) { abilities.pause(mob); setSummonsPaused(mob, true); }
    public void resume(final Mob mob) { abilities.resume(mob); setSummonsPaused(mob, false); }
    public void detach(final Mob mob) { abilities.detach(mob); }

    private void setSummonsPaused(final Mob owner, final boolean paused) {
        if (owner == null) return;
        final UUID ownerId = owner.getUniqueId();
        final java.util.Set<UUID> ids = activeSummonIds.get(ownerId);
        if (ids == null) return;
        for (final UUID id : java.util.Set.copyOf(ids)) {
            final Entity handle = Bukkit.getEntity(id);
            if (handle == null) { forget(id, ownerId); continue; }
            if (handle.getScheduler().run(plugin, task -> {
                final Mob add = ownedMob(id);
                if (add == null) { forget(id, ownerId); return; }
                // Parent cleanup may have retired this group while the child hop was queued.
                final var current = activeSummonIds.get(ownerId);
                if (current == null || !current.contains(id)) return;
                add.setAI(!paused); add.setInvulnerable(paused);
                if (paused) add.setTarget(null);
                if (paused) abilities.pause(add); else abilities.resume(add);
            }, () -> forget(id, ownerId)) == null) forget(id, ownerId);
        }
    }

    public static java.util.Optional<UUID> summonOrigin(final Entity entity) {
        if (entity == null || !Bukkit.isOwnedByCurrentRegion(entity)) throw new IllegalStateException("Authored origin owner required");
        final String raw = entity.getPersistentDataContainer().get(NamespacedKey.fromString("icesmp:authored_summon_owner"), PersistentDataType.STRING);
        if (raw == null) return java.util.Optional.empty();
        final UUID id = UUID.fromString(raw);
        if (!id.toString().equals(raw)) throw new IllegalArgumentException("Invalid authored summon origin");
        return java.util.Optional.of(id);
    }

    /** Immutable native encounter provenance, captured before an async reward/effect handoff. */
    public static java.util.Optional<hu.taliann.icesmp.integrity.RewardSource.Event> sandboxEventOrigin(final Entity entity) {
        if (entity == null || !Bukkit.isOwnedByCurrentRegion(entity)) throw new IllegalStateException("Authored origin owner required");
        final var pdc = entity.getPersistentDataContainer();
        if (!"world_weaver".equals(pdc.get(NamespacedKey.fromString("icesmp:authored_spawn_source"), PersistentDataType.STRING))) return java.util.Optional.empty();
        final String raw = pdc.get(NamespacedKey.fromString("icesmp:authored_encounter_id"), PersistentDataType.STRING);
        return java.util.Optional.of(new hu.taliann.icesmp.integrity.RewardSource.Event("world_weaver", UUID.fromString(raw)));
    }
    public static RewardOwner rewardOwner(final Entity entity) {
        if (entity == null) return RewardOwner.GENERIC;
        final String stored = entity.getPersistentDataContainer().get(
                NamespacedKey.fromString("icesmp:authored_reward_owner"), PersistentDataType.STRING);
        if (stored == null) return RewardOwner.GENERIC;
        try { return RewardOwner.valueOf(stored); }
        catch (final IllegalArgumentException invalid) { return RewardOwner.NONE; }
    }

    private static Mob ownedMob(final UUID id) {
        final Entity entity = Bukkit.getEntity(id);
        if (entity == null || !Bukkit.isOwnedByCurrentRegion(entity) || !(entity instanceof Mob mob) || !mob.isValid() || mob.isDead()) return null;
        return mob;
    }
    private void remove(final UUID id, final UUID owner) {
        forget(id, owner);
        final Mob mob = ownedMob(id);
        if (mob != null) mob.remove();
    }
    /** Retired callbacks have no live owner: cleanup is only an immutable identity/index operation. */
    private void forget(final UUID id, final UUID owner) {
        if (owner == null) return;
        activeSummonIds.computeIfPresent(owner, (key, ids) -> { ids.remove(id); return ids.isEmpty() ? null : ids; });
    }

    private static String id(final String raw, final String field) {
        final String value = Objects.requireNonNull(raw, field).trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._:-]", "_");
        if (value.isBlank() || value.length() > 128) throw new IllegalArgumentException(field + " invalid");
        return value;
    }
}
