package hu.taliann.icesmp.dev.weaver.subject;

import hu.taliann.icesmp.dev.weaver.WorldWeaverProviderRegistry;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.WeaverOwnerRouter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class SubjectSnapshotFactory implements SubjectSnapshotSource {
    private final WeaverOwnerRouter router;
    private final WeaverItemSlots slots;
    private final WorldWeaverProviderRegistry providers;
    public SubjectSnapshotFactory(final WeaverOwnerRouter router, final WeaverItemSlots slots, final WorldWeaverProviderRegistry providers) {
        this.router = Objects.requireNonNull(router); this.slots = Objects.requireNonNull(slots); this.providers = Objects.requireNonNull(providers);
    }
    @Override public CompletionStage<SubjectSnapshot> capture(final UUID actor, final SubjectRef ref) {
        return router.submit(SubjectRoute.owner(ref), actor, Duration.ofSeconds(5), () -> CompletableFuture.completedFuture(captureOnOwner(ref)));
    }
    private SubjectSnapshot captureOnOwner(final SubjectRef ref) {
        final long now = System.currentTimeMillis();
        final Map<String, WeaverValue> facts = new TreeMap<>();
        switch (ref) {
            case PlayerRef player -> entityFacts(requirePlayer(player.playerId()), facts, now);
            case EntityRef entity -> entityFacts(requireEntity(entity.entityId()), facts, now);
            case ItemSlotRef item -> {
                final Player player = requirePlayer(item.holderId()); slots.verify(player, item);
                final var stack = slots.require(player, item.slot());
                scalar(facts, "material", "text", stack.getType().getKey().toString(), now);
                scalar(facts, "amount", "int", stack.getAmount(), now);
                scalar(facts, "item_fingerprint", "text", item.fingerprint(), now);
            }
            case BlockRef block -> {
                final World world = regionWorld(block.worldId(), block.x() >> 4, block.z() >> 4);
                if (block.y() < world.getMinHeight() || block.y() >= world.getMaxHeight()) throw new WeaverDomainRejection("OUTSIDE_WORLD_HEIGHT");
                scalar(facts, "block_data", "text", world.getBlockAt(block.x(), block.y(), block.z()).getBlockData().getAsString(), now);
                scalar(facts, "world", "uuid", block.worldId().toString(), now);
            }
            case LocationRef location -> {
                regionWorld(location.worldId(), (int) Math.floor(location.x()) >> 4, (int) Math.floor(location.z()) >> 4);
                facts.put("minecraft.location", value("location", SubjectKeyCodec.payload(location), now));
            }
            case WorldRef worldRef -> {
                if (!Bukkit.isGlobalTickThread()) throw new IllegalStateException("Foreign world-global access");
                final World world = Bukkit.getWorld(worldRef.worldId());
                if (world == null) throw new WeaverDomainRejection("WORLD_UNAVAILABLE");
                scalar(facts, "world", "uuid", worldRef.worldId().toString(), now);
                scalar(facts, "world_name", "text", world.getName(), now);
                scalar(facts, "time", "duration_ticks", world.getTime(), now);
                scalar(facts, "storm", "boolean", world.hasStorm(), now);
                scalar(facts, "thunder", "boolean", world.isThundering(), now);
            }
            case AreaRef area -> {
                if (!Bukkit.isGlobalTickThread()) throw new IllegalStateException("Foreign AREA descriptor access");
                if (Bukkit.getWorld(area.worldId()) == null) throw new WeaverDomainRejection("WORLD_UNAVAILABLE");
                facts.put("minecraft.area", value("area", SubjectKeyCodec.payload(area), now));
            }
        }
        final Map<String, WeaverValue> contributions = providers.captureContributions(ref);
        for (final var entry : contributions.entrySet()) {
            if (facts.putIfAbsent(entry.getKey(), entry.getValue()) != null) throw new WeaverDomainRejection("DUPLICATE_SNAPSHOT_FACT");
        }
        final Map<String, Object> fingerprint = new TreeMap<>(); fingerprint.put("subject", SubjectKeyCodec.payload(ref));
        facts.forEach((key, value) -> fingerprint.put(key, Map.of("type", value.type().canonical(), "payload", value.payload())));
        return new SubjectSnapshot(ref, now, WeaverItemSlots.fingerprint(CanonicalValueBytes.encode(fingerprint)), facts);
    }
    private static Entity requireEntity(final UUID id) {
        final Entity entity = Bukkit.getEntity(id);
        if (entity == null || !Bukkit.isOwnedByCurrentRegion(entity)) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
        if (!entity.isValid() || entity.isDead()) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
        return entity;
    }
    private static Player requirePlayer(final UUID id) {
        final Entity entity = requireEntity(id);
        if (!(entity instanceof Player player) || !player.isOnline()) throw new WeaverDomainRejection("PLAYER_UNAVAILABLE");
        return player;
    }
    private static World regionWorld(final UUID id, final int chunkX, final int chunkZ) {
        final World world = Bukkit.getWorld(id);
        if (world == null || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ) || !world.isChunkLoaded(chunkX, chunkZ)) {
            throw new WeaverDomainRejection("CHUNK_UNAVAILABLE");
        }
        return world;
    }
    private static void entityFacts(final Entity entity, final Map<String, WeaverValue> facts, final long now) {
        scalar(facts, "entity_type", "text", entity.getType().getKey().toString(), now);
        scalar(facts, "entity_id", "uuid", entity.getUniqueId().toString(), now);
        scalar(facts, "fire_ticks", "int", entity.getFireTicks(), now);
        scalar(facts, "freeze_ticks", "int", entity.getFreezeTicks(), now);
        scalar(facts, "invulnerable", "boolean", entity.isInvulnerable(), now);
        scalar(facts, "gravity", "boolean", entity.hasGravity(), now);
        final var location = entity.getLocation();
        facts.put("minecraft.location", value("location", SubjectKeyCodec.payload(new LocationRef(location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch())), now));
        if (entity instanceof LivingEntity living) {
            scalar(facts, "health", "double", living.getHealth(), now);
            scalar(facts, "ai", "boolean", living.hasAI(), now);
        }
        if (entity instanceof Mob mob) scalar(facts, "aware", "boolean", mob.isAware(), now);
        if (entity instanceof Player player) {
            scalar(facts, "player_name", "text", player.getName(), now);
            scalar(facts, "gamemode", "text", player.getGameMode().name(), now);
            scalar(facts, "food", "int", player.getFoodLevel(), now);
            scalar(facts, "flying", "boolean", player.isFlying(), now);
        }
    }
    private static void scalar(final Map<String, WeaverValue> facts, final String id, final String type, final Object scalar, final long now) {
        facts.put("minecraft." + id, value(type, Map.of("value", scalar), now));
    }
    private static WeaverValue value(final String type, final Map<String, Object> payload, final long now) {
        return new WeaverValue(new WeaverTypeId("weaver", type, 1), payload, "minecraft", "minecraft.runtime", Set.of(), now);
    }
}
