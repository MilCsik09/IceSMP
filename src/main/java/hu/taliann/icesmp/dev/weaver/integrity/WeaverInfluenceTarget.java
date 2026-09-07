package hu.taliann.icesmp.dev.weaver.integrity;

import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.RewardSource;
import java.util.*;

public record WeaverInfluenceTarget(RewardSource source, Optional<AreaRef> area) {
    public WeaverInfluenceTarget {
        Objects.requireNonNull(source); Objects.requireNonNull(area);
        if (area.isPresent() && (!(source instanceof RewardSource.World world) || !world.id().equals(area.get().worldId()))) throw new IllegalArgumentException("Spatial influence world mismatch");
    }
    public static WeaverInfluenceTarget exact(final RewardSource source) { return new WeaverInfluenceTarget(source, Optional.empty()); }
    public static WeaverInfluenceTarget spatial(final AreaRef area) { return new WeaverInfluenceTarget(new RewardSource.World(area.worldId()), Optional.of(area)); }
    public boolean monotonic() { return source instanceof RewardSource.Entity || source instanceof RewardSource.Item || source instanceof RewardSource.Event; }
    public static WeaverInfluenceTarget subject(final SubjectRef subject) {
        return switch (subject) {
            case PlayerRef player -> exact(new RewardSource.Player(player.playerId()));
            case EntityRef entity -> exact(new RewardSource.Entity(entity.entityId()));
            case ItemSlotRef item -> item.instanceId().isPresent() ? exact(new RewardSource.Item(item.instanceId().get())) : exact(new RewardSource.Player(item.holderId()));
            case BlockRef block -> exact(new RewardSource.Location(block.worldId(), block.x(), block.y(), block.z()));
            case LocationRef location -> exact(new RewardSource.Location(location.worldId(), location.x(), location.y(), location.z()));
            case WorldRef world -> exact(new RewardSource.World(world.worldId()));
            case AreaRef area -> spatial(area);
        };
    }
}
