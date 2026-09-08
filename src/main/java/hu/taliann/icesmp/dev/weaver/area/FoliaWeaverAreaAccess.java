package hu.taliann.icesmp.dev.weaver.area;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.RegionOwner;
import hu.taliann.icesmp.dev.weaver.subject.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;

public final class FoliaWeaverAreaAccess implements WeaverAreaAccess {
    private final SubjectSnapshotFactory snapshots;
    public FoliaWeaverAreaAccess(final SubjectSnapshotFactory snapshots) { this.snapshots = Objects.requireNonNull(snapshots); }
    @Override public ChunkSelection collectOnOwner(final AreaRef area, final RegionOwner owner, final AreaSupport support, final int limit) {
        final var world = Bukkit.getWorld(owner.worldId());
        if (world == null || !Bukkit.isOwnedByCurrentRegion(world, owner.chunkX(), owner.chunkZ())) throw new WeaverDomainRejection("OWNER_UNAVAILABLE");
        if (!world.isChunkLoaded(owner.chunkX(), owner.chunkZ())) return ChunkSelection.unavailable("CHUNK_UNAVAILABLE");
        final List<SubjectRef> targets = new ArrayList<>();
        if (support == AreaSupport.ENTITY_FANOUT) {
            final var chunk = world.getChunkAt(owner.chunkX(), owner.chunkZ());
            // getEntities force-loads absent entity data, so block-chunk availability alone is insufficient.
            if (!chunk.isEntitiesLoaded()) return ChunkSelection.unavailable("ENTITIES_UNAVAILABLE");
            final var entities = chunk.getEntities();
            if (entities.length > 4096) throw new WeaverDomainRejection("AREA_SCAN_LIMIT");
            for (final var entity : entities) {
                if (!Bukkit.isOwnedByCurrentRegion(entity)) throw new WeaverDomainRejection("AREA_OWNERSHIP_CHANGED");
                if (!entity.isValid() || entity.isDead()) continue;
                final var location = entity.getLocation();
                if (!location.getWorld().getUID().equals(area.worldId()) || !area.shape().contains(location.getBlockX(), location.getBlockY(), location.getBlockZ())) continue;
                targets.add(entity instanceof Player ? new PlayerRef(entity.getUniqueId()) : new EntityRef(entity.getUniqueId()));
                if (targets.size() > limit) throw new WeaverDomainRejection("AREA_TARGET_CAP");
            }
        } else if (support == AreaSupport.BLOCK_FANOUT) {
            final AreaBounds bounds = area.shape().bounds();
            for (int x = Math.max(bounds.minX(), owner.chunkX() << 4); x <= Math.min(bounds.maxX(), (owner.chunkX() << 4) + 15); x++)
                for (int z = Math.max(bounds.minZ(), owner.chunkZ() << 4); z <= Math.min(bounds.maxZ(), (owner.chunkZ() << 4) + 15); z++)
                    for (int y = Math.max(bounds.minY(), world.getMinHeight()); y <= Math.min(bounds.maxY(), world.getMaxHeight() - 1); y++) {
                        if (!area.shape().contains(x, y, z)) continue;
                        targets.add(new BlockRef(area.worldId(), x, y, z));
                        if (targets.size() > limit) throw new WeaverDomainRejection("AREA_TARGET_CAP");
                    }
        } else throw new IllegalArgumentException("Non-fanout collection");
        return new ChunkSelection(targets, Optional.empty());
    }
    @Override public SubjectSnapshot snapshotOnOwner(final SubjectRef subject) { return snapshots.captureOnOwner(subject); }
}
