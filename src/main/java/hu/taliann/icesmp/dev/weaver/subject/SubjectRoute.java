package hu.taliann.icesmp.dev.weaver.subject;

import hu.taliann.icesmp.dev.weaver.execution.*;

public final class SubjectRoute {
    private SubjectRoute() {}
    public static ExecutionOwner owner(final SubjectRef ref) {
        return switch (ref) {
            case PlayerRef player -> new EntityOwner(player.playerId());
            case EntityRef entity -> new EntityOwner(entity.entityId());
            case ItemSlotRef item -> new EntityOwner(item.holderId());
            case BlockRef block -> new RegionOwner(block.worldId(), block.x() >> 4, block.z() >> 4);
            case LocationRef location -> new RegionOwner(location.worldId(), (int) Math.floor(location.x()) >> 4, (int) Math.floor(location.z()) >> 4);
            case WorldRef ignored -> new GlobalOwner();
            case AreaRef ignored -> new GlobalOwner();
        };
    }
}
