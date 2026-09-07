package hu.taliann.icesmp.integrity;

import java.util.*;

/** Immutable provenance for an unclaimed world reward, without a synthetic beneficiary. */
public record RewardSourceContext(RewardChannel channel, List<RewardSource> sources) {
    public RewardSourceContext {
        Objects.requireNonNull(channel); sources = List.copyOf(sources);
        if (sources.isEmpty() || sources.size() > 64) throw new IllegalArgumentException("Reward source lineage must contain 1..64 immutable identities");
    }
    public RewardContext forRecipient(final UUID recipient) { return new RewardContext(channel, recipient, sources); }
}
