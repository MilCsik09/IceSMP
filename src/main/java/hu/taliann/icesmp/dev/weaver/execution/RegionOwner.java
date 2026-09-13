package hu.taliann.icesmp.dev.weaver.execution;

import java.util.UUID;

public record RegionOwner(UUID worldId, int chunkX, int chunkZ) implements ExecutionOwner {
    public RegionOwner {
        java.util.Objects.requireNonNull(worldId);
        if (Math.abs((long) chunkX) > 1_875_000 || Math.abs((long) chunkZ) > 1_875_000) throw new IllegalArgumentException("Region owner outside world bounds");
    }
}
