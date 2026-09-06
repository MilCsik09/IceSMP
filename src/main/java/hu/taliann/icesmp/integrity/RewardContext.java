package hu.taliann.icesmp.integrity;

import java.util.List;
import java.util.UUID;

public record RewardContext(RewardChannel channel, UUID recipient, List<RewardSource> sources) {
    public RewardContext {
        java.util.Objects.requireNonNull(channel); java.util.Objects.requireNonNull(recipient); sources = List.copyOf(sources);
        if (sources.isEmpty() || sources.size() > 64) throw new IllegalArgumentException("Reward source lineage must contain 1..64 immutable identities");
    }
}
