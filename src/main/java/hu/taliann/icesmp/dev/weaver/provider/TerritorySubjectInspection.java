package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.projection.WeaverProjectionFingerprint;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.managers.TerritoryProtectionService;
import hu.taliann.icesmp.territory.TerritoryProtectionPolicy.*;
import hu.taliann.icesmp.territory.TerritoryRevision;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.*;
import static hu.taliann.icesmp.dev.weaver.provider.TerritoryWeaverProvider.*;

/** Live handles exist only within this owner callback; discovery consumes its detached typed facts. */
final class TerritorySubjectInspection {
    private TerritorySubjectInspection() { }
    static Map<String, WeaverValue> capture(SubjectRef ref, TerritoryManager manager, TerritoryProtectionService protection,
                                            TerritoryRuntimeProjectionSource projections) {
        final Location location; final Player actor;
        if (ref instanceof PlayerRef || ref instanceof EntityRef) {
            final UUID id = ref instanceof PlayerRef player ? player.playerId() : ((EntityRef) ref).entityId();
            final var entity = Bukkit.getEntity(id);
            if (entity == null || !Bukkit.isOwnedByCurrentRegion(entity)) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
            if (!entity.isValid() || entity.isDead() || ref instanceof PlayerRef && !(entity instanceof Player)) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
            location = entity.getLocation(); actor = ref instanceof PlayerRef ? (Player) entity : null;
        } else if (ref instanceof BlockRef block) {
            final World world = region(block.worldId(), block.x() >> 4, block.z() >> 4);
            if (block.y() < world.getMinHeight() || block.y() >= world.getMaxHeight()) throw new WeaverDomainRejection("OUTSIDE_WORLD_HEIGHT");
            location = new Location(world, block.x(), block.y(), block.z()); actor = null;
        } else if (ref instanceof LocationRef point) {
            location = new Location(region(point.worldId(), (int) Math.floor(point.x()) >> 4, (int) Math.floor(point.z()) >> 4), point.x(), point.y(), point.z()); actor = null;
        } else return Map.of();
        if (!manager.adjustmentStateAvailable()) throw new WeaverDomainRejection("TERRITORY_UNAVAILABLE");
        final var point = new LocationRef(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        final var world = new WorldRef(point.worldId());
        final String beforeProjection = WeaverProjectionFingerprint.of(projections.active(world));
        final Territory before = manager.getTerritoryAt(location); final Map<Rule, Decision> trace = new EnumMap<>(Rule.class);
        for (final var rule : Rule.values()) trace.put(rule, protection.traceAt(location, actor, rule));
        if (!manager.adjustmentStateAvailable() || !Objects.equals(before, manager.getTerritoryAt(location))
                || !beforeProjection.equals(WeaverProjectionFingerprint.of(projections.active(world)))) throw new WeaverDomainRejection("CONFLICT");
        return facts(point, Optional.ofNullable(before), trace, actor != null, beforeProjection, System.currentTimeMillis());
    }
    private static World region(UUID id, int x, int z) {
        final var world = Bukkit.getWorld(id);
        if (world == null) throw new WeaverDomainRejection("WORLD_UNAVAILABLE");
        if (!Bukkit.isOwnedByCurrentRegion(world, x, z)) throw new WeaverDomainRejection("OWNER_UNAVAILABLE");
        if (!world.isChunkLoaded(x, z)) throw new WeaverDomainRejection("CHUNK_UNAVAILABLE");
        return world;
    }
    static Map<String, WeaverValue> facts(LocationRef location, Optional<Territory> zone, Map<Rule, Decision> decisions,
                                         boolean actor, String projectionRevision, long now) {
        if (!decisions.keySet().equals(Set.of(Rule.values()))) throw new WeaverDomainRejection("INCOMPLETE_PROTECTION_TRACE");
        final Map<String, WeaverValue> facts = new TreeMap<>();
        facts.put("territory.location", new WeaverValue(WeaverTypeId.parse("weaver:location@1"), SubjectKeyCodec.payload(location), "territory", FACET, Set.of(), now));
        facts.put("territory.trace_context", scalar(actor ? "Player actor at own location; no PvP victim context" : "Environmental context; no player actor or PvP victim", now));
        facts.put("territory.zone_revision", scalar(zone.map(TerritoryRevision::fingerprint).orElse("wilderness"), now));
        facts.put(PROJECTIONS, scalar(projectionRevision, now));
        zone.ifPresent(value -> {
            facts.put("territory.id", scalar(value.id(), now)); facts.put("territory.name", scalar(value.name(), now));
            facts.put("territory.type", scalar(value.type().name(), now)); facts.put("territory.owner", scalar(value.faction().name(), now));
            facts.put("territory.shape", scalar(value.isPolygon() ? "POLYGON" : "CIRCLE", now));
            facts.put("territory.center", scalar(value.world() + ":" + value.x() + "," + value.z(), now));
            facts.put("territory.radius", scalar(Integer.toString(value.radius()), now));
            facts.put("territory.y_bounds", scalar(value.minY() + ".." + value.maxY(), now));
            facts.put("territory.vertices", scalar(Integer.toString(value.polygon().size()), now));
        });
        for (final var entry : decisions.entrySet()) {
            final String prefix = "territory." + entry.getKey().name().toLowerCase(Locale.ROOT);
            facts.put(prefix + ".decision", scalar(entry.getValue().denied() ? "DENY" : "ALLOW", now));
            facts.put(prefix + ".reason", scalar(entry.getValue().reason().name(), now));
            facts.put(prefix + ".trace", scalar(String.join(" → ", entry.getValue().trace().stream().map(step -> step.reason().name() + "=" + step.matched()).toList()), now));
        }
        return Map.copyOf(facts);
    }
}
