package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.CorruptionManager;
import hu.taliann.icesmp.managers.CultistEventManager;
import hu.taliann.icesmp.managers.DarkUndeadAmbienceManager;
import hu.taliann.icesmp.managers.DungeonLootService;
import hu.taliann.icesmp.managers.EscortManager;
import hu.taliann.icesmp.managers.InvasionManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.managers.WildHuntManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wither;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Bukkit/Paper entity classifier feeding the pure faction-passive policy. */
public final class FactionMobContextResolver {

    private static final Set<TargetReason> RETALIATION_REASONS = EnumSet.of(
            TargetReason.TARGET_ATTACKED_ENTITY,
            TargetReason.TARGET_ATTACKED_NEARBY_ENTITY,
            TargetReason.REINFORCEMENT_TARGET);
    private static final Set<TargetReason> EXPLICIT_FORCE_REASONS = EnumSet.of(
            TargetReason.CUSTOM,
            TargetReason.UNKNOWN,
            TargetReason.OWNER_ATTACKED_TARGET,
            TargetReason.TARGET_ATTACKED_OWNER,
            TargetReason.DEFEND_VILLAGE);
    private static final Set<TargetReason> SPONTANEOUS_REASONS = EnumSet.of(
            TargetReason.CLOSEST_PLAYER,
            TargetReason.CLOSEST_ENTITY,
            TargetReason.RANDOM_TARGET,
            TargetReason.COLLISION);
    private final DarkUndeadAmbienceManager darkUndeadAmbienceManager;
    private final CorruptionManager corruptionManager;
    private final DungeonLootService dungeonLootService;
    private final InvasionManager invasionManager;
    private final WorldBossManager worldBossManager;
    private final CultistEventManager cultistEventManager;
    private final EscortManager escortManager;
    private final WildHuntManager wildHuntManager;
    private final TerritoryManager territoryManager;
    private final BloodMoonManager bloodMoonManager;

    public FactionMobContextResolver(
            final DarkUndeadAmbienceManager darkUndeadAmbienceManager,
            final CorruptionManager corruptionManager,
            final DungeonLootService dungeonLootService,
            final InvasionManager invasionManager,
            final WorldBossManager worldBossManager,
            final CultistEventManager cultistEventManager,
            final EscortManager escortManager,
            final WildHuntManager wildHuntManager,
            final TerritoryManager territoryManager,
            final BloodMoonManager bloodMoonManager) {
        this.darkUndeadAmbienceManager = darkUndeadAmbienceManager;
        this.corruptionManager = corruptionManager;
        this.dungeonLootService = dungeonLootService;
        this.invasionManager = invasionManager;
        this.worldBossManager = worldBossManager;
        this.cultistEventManager = cultistEventManager;
        this.escortManager = escortManager;
        this.wildHuntManager = wildHuntManager;
        this.territoryManager = territoryManager;
        this.bloodMoonManager = bloodMoonManager;
    }

    public FactionPassivePolicy.TargetContext resolve(
            final EntityTargetLivingEntityEvent event,
            final UUID playerId,
            final boolean whisperer,
            final FactionPassiveService state,
            final FactionPassiveSettings settings) {
        final Entity entity = event.getEntity();
        final TargetReason reason = event.getReason();
        final UUID mobId = entity.getUniqueId();
        final boolean undead = isUndead(entity);
        final boolean ambient = darkUndeadAmbienceManager.isMarked(entity);
        final EnumSet<FactionPassivePolicy.ContentContext> contexts =
                contentContexts(entity, settings, playerId);
        final boolean timedRetaliation = state.isNeutralRetaliating(playerId, mobId)
                || undead && state.isDarkRetaliating(playerId, mobId);
        // Ambient/NEUTRAL truce breaks are time-bounded by our own state. Wild undead keep
        // vanilla retaliation semantics; otherwise their natural target reason would be
        // mistaken for fresh spontaneous aggro after the player hit them.
        final boolean retaliating = timedRetaliation
                || undead && !ambient && RETALIATION_REASONS.contains(reason);
        return targetContext(
                entity,
                settings,
                whisperer,
                contexts,
                EXPLICIT_FORCE_REASONS.contains(reason)
                        && !contexts.contains(FactionPassivePolicy.ContentContext.CROWN_CURSE),
                retaliating,
                entity instanceof Enderman && reason == TargetReason.CLOSEST_PLAYER,
                SPONTANEOUS_REASONS.contains(reason),
                SPONTANEOUS_REASONS.contains(reason));
    }

    /**
     * Rebuilds the live, unprovoked context before a delayed cleanup clears a target. A later
     * CUSTOM/marked target untracks the old retaliation entry in the listener; this resolver then
     * only decides whether the current membership/config/world state would still grant truce.
     */
    public FactionPassivePolicy.TargetContext resolveCurrentTruce(
            final Mob mob,
            final UUID playerId,
            final boolean whisperer,
            final FactionPassiveSettings settings) {
        final boolean enderman = mob instanceof Enderman;
        return targetContext(
                mob,
                settings,
                whisperer,
                contentContexts(mob, settings, playerId),
                false,
                false,
                enderman,
                true,
                isUndead(mob));
    }

    private FactionPassivePolicy.TargetContext targetContext(
            final Entity entity,
            final FactionPassiveSettings settings,
            final boolean whisperer,
            final Set<FactionPassivePolicy.ContentContext> contexts,
            final boolean explicitForce,
            final boolean retaliation,
            final boolean spontaneousEndermanStare,
            final boolean spontaneousNeutralAggro,
            final boolean spontaneousUndeadAggro) {
        final boolean undead = isUndead(entity);
        final long time = entity.getWorld().getTime();
        return new FactionPassivePolicy.TargetContext(
                explicitForce,
                contexts,
                retaliation,
                bloodMoonManager.isActive(),
                time >= 13_000L && time <= 23_000L,
                undead,
                undead && darkUndeadAmbienceManager.isMarked(entity),
                isNeutralMob(entity, settings),
                entity instanceof Enderman,
                spontaneousEndermanStare,
                spontaneousNeutralAggro,
                spontaneousUndeadAggro,
                whisperer);
    }

    /**
     * Explicit source identity for damage provenance. Unlike {@link #contentContexts(Entity,
     * FactionPassiveSettings)}, this method never infers scripted combat merely from location.
     */
    public EnumSet<FactionPassivePolicy.ContentContext> explicitCombatContexts(
            final Entity entity, final FactionPassiveSettings settings) {
        final EnumSet<FactionPassivePolicy.ContentContext> contexts =
                EnumSet.noneOf(FactionPassivePolicy.ContentContext.class);
        final UUID entityId = entity.getUniqueId();
        if (corruptionManager.isCorruptMob(entityId)
                || entity.getPersistentDataContainer().has(
                FactionCombatMarkers.CORRUPTION_MOB, PersistentDataType.BYTE)) {
            contexts.add(FactionPassivePolicy.ContentContext.CORRUPTION);
        }
        if (dungeonLootService.isDungeonBoss(entity)
                || entity.getPersistentDataContainer().has(
                FactionCombatMarkers.DUNGEON_COMBAT, PersistentDataType.BYTE)) {
            contexts.add(FactionPassivePolicy.ContentContext.DUNGEON);
        }
        if (invasionManager.isInvasionMob(entityId)) {
            contexts.add(FactionPassivePolicy.ContentContext.INVASION);
        }
        if (worldBossManager.isWorldBoss(entity) || entity instanceof Wither) {
            contexts.add(FactionPassivePolicy.ContentContext.WORLD_BOSS);
        }
        if (cultistEventManager.isCultist(entity) || escortManager.isWaveMob(entityId)
                || wildHuntManager.isWildHunt(entityId)
                || hasConfiguredMarker(entity, settings.dark().combatMarkerKeys())) {
            contexts.add(FactionPassivePolicy.ContentContext.EVENT_MOB);
        }
        if (hasConfiguredMarker(entity, settings.dark().questMarkerKeys())) {
            contexts.add(FactionPassivePolicy.ContentContext.QUEST_MOB);
        }
        return contexts;
    }

    public EnumSet<FactionPassivePolicy.ContentContext> contentContexts(
            final Entity entity, final FactionPassiveSettings settings) {
        return contentContexts(entity, settings, null);
    }

    public EnumSet<FactionPassivePolicy.ContentContext> contentContexts(
            final Entity entity, final FactionPassiveSettings settings,
            final UUID targetPlayerId) {
        final EnumSet<FactionPassivePolicy.ContentContext> contexts =
                EnumSet.noneOf(FactionPassivePolicy.ContentContext.class);
        final UUID entityId = entity.getUniqueId();
        if (corruptionManager.isCorruptMob(entityId)
                || entity.getPersistentDataContainer().has(
                FactionCombatMarkers.CORRUPTION_MOB, PersistentDataType.BYTE)) {
            contexts.add(FactionPassivePolicy.ContentContext.CORRUPTION);
        }
        final Territory territory = territoryManager.getTerritoryAt(entity.getLocation());
        final boolean inDungeon = territory != null && (territory.type() == TerritoryType.DUNGEON
                || territory.type() == TerritoryType.DOOM_GATE);
        if (dungeonLootService.isDungeonBoss(entity) || inDungeon
                || entity.getPersistentDataContainer().has(
                FactionCombatMarkers.DUNGEON_COMBAT, PersistentDataType.BYTE)) {
            contexts.add(FactionPassivePolicy.ContentContext.DUNGEON);
        }
        if (invasionManager.isInvasionMob(entityId)) {
            contexts.add(FactionPassivePolicy.ContentContext.INVASION);
        }
        if (worldBossManager.isWorldBoss(entity) || entity instanceof Wither) {
            contexts.add(FactionPassivePolicy.ContentContext.WORLD_BOSS);
        }
        if (cultistEventManager.isCultist(entity) || escortManager.isWaveMob(entityId)
                || wildHuntManager.isWildHunt(entityId)
                || hasConfiguredMarker(entity, settings.dark().combatMarkerKeys())) {
            contexts.add(FactionPassivePolicy.ContentContext.EVENT_MOB);
        }
        if (hasConfiguredMarker(entity, settings.dark().questMarkerKeys())) {
            contexts.add(FactionPassivePolicy.ContentContext.QUEST_MOB);
        }
        final String crownTarget = entity.getPersistentDataContainer().get(
                FactionCombatMarkers.CROWN_CURSE_TARGET, PersistentDataType.STRING);
        if (crownTarget != null && (targetPlayerId == null
                || crownTarget.equals(targetPlayerId.toString()))) {
            contexts.add(FactionPassivePolicy.ContentContext.CROWN_CURSE);
        }
        return contexts;
    }

    public boolean isUndead(final Entity entity) {
        return hu.taliann.icesmp.utils.UndeadUtil.isUndead(entity);
    }

    public boolean isAmbientUndead(final Entity entity) {
        return isUndead(entity) && darkUndeadAmbienceManager.isMarked(entity);
    }

    public boolean isNeutralMob(final Entity entity, final FactionPassiveSettings settings) {
        if (entity instanceof Tameable tameable && tameable.getOwner() != null) {
            return false;
        }
        if (!explicitCombatContexts(entity, settings).isEmpty()) {
            return false;
        }
        return settings.neutral().includeNonMonsters() && !(entity instanceof Monster)
                || settings.neutral().additionalNeutralEntityTypes().contains(entity.getType().name());
    }

    private static boolean hasConfiguredMarker(final Entity entity, final Set<String> markers) {
        if (markers.isEmpty()) {
            return false;
        }
        return entity.getPersistentDataContainer().getKeys().stream()
                .map(Object::toString)
                .anyMatch(markers::contains);
    }
}
