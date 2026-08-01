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
import org.bukkit.entity.Monster;
import org.bukkit.entity.Wither;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Bukkit/Paper entity classifier feeding the pure faction-passive policy. */
public final class FactionMobContextResolver {

    private static final Set<String> RETALIATION_REASONS = Set.of(
            "TARGET_ATTACKED_ENTITY", "TARGET_ATTACKED_NEARBY_ENTITY", "TARGET_ATTACKED_PLAYER",
            "REINFORCEMENT_TARGET");
    private static final Set<String> EXPLICIT_FORCE_REASONS = Set.of(
            "CUSTOM", "UNKNOWN", "OWNER_ATTACKED", "OWNER_ATTACKED_TARGET",
            "TARGET_ATTACKED_OWNER", "RAIDER_ATTACKED_TARGET", "DEFEND_VILLAGE");
    private static final Set<String> SPONTANEOUS_REASONS = Set.of(
            "CLOSEST_PLAYER", "CLOSEST_ENTITY", "RANDOM_TARGET", "COLLISION");
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
        final String reason = event.getReason().name();
        final boolean undead = isUndead(entity);
        final boolean enderman = entity instanceof Enderman;
        final boolean ambient = darkUndeadAmbienceManager.isMarked(entity);
        final EnumSet<FactionPassivePolicy.ContentContext> contexts =
                contentContexts(entity, settings, playerId);
        final boolean timedRetaliation = state.isNeutralRetaliating(playerId, entity.getUniqueId())
                || undead && state.isDarkRetaliating(playerId);
        // Ambient/NEUTRAL truce breaks are time-bounded by our own state. Wild undead keep
        // vanilla retaliation semantics; otherwise their natural target reason would be
        // mistaken for fresh spontaneous aggro after the player hit them.
        final boolean retaliating = timedRetaliation
                || undead && !ambient && RETALIATION_REASONS.contains(reason);
        final long time = entity.getWorld().getTime();
        return new FactionPassivePolicy.TargetContext(
                EXPLICIT_FORCE_REASONS.contains(reason)
                        && !contexts.contains(FactionPassivePolicy.ContentContext.CROWN_CURSE),
                contexts,
                retaliating,
                bloodMoonManager.isActive(),
                time >= 13_000L && time <= 23_000L,
                undead,
                ambient,
                isNeutralMob(entity, settings),
                enderman,
                enderman && "CLOSEST_PLAYER".equals(reason),
                SPONTANEOUS_REASONS.contains(reason),
                SPONTANEOUS_REASONS.contains(reason),
                whisperer);
    }

    public EnumSet<FactionPassivePolicy.ContentContext> contentContexts(
            final Entity entity, final FactionPassiveSettings settings) {
        return contentContexts(entity, settings, null);
    }

    private EnumSet<FactionPassivePolicy.ContentContext> contentContexts(
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
