package hu.taliann.icesmp.integrity;

import java.util.UUID;

/** Immutable source identity is captured by its gameplay owner before any reward continuation. */
public sealed interface RewardSource {
    record Entity(UUID id) implements RewardSource { public Entity { java.util.Objects.requireNonNull(id); } }
    record Player(UUID id) implements RewardSource { public Player { java.util.Objects.requireNonNull(id); } }
    record Item(UUID instanceId) implements RewardSource { public Item { java.util.Objects.requireNonNull(instanceId); } }
    record Event(String lifecycleOwner, UUID instanceId) implements RewardSource {
        public Event {
            java.util.Objects.requireNonNull(instanceId);
            if (lifecycleOwner == null || !lifecycleOwner.matches("[a-z0-9_.-]{1,96}")) throw new IllegalArgumentException("Invalid event source");
        }
    }
    record World(UUID id) implements RewardSource { public World { java.util.Objects.requireNonNull(id); } }
    record Location(UUID world, double x, double y, double z) implements RewardSource {
        public Location {
            java.util.Objects.requireNonNull(world);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 1_000_000) {
                throw new IllegalArgumentException("Invalid spatial reward source");
            }
        }
    }
}
