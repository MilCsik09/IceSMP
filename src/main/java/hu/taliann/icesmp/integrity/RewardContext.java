package hu.taliann.icesmp.integrity;

import java.util.List;
import java.util.UUID;

public record RewardContext(RewardChannel channel, UUID recipient, List<RewardSource> sources) {
    public static RewardContext recipientOnly(final RewardChannel channel, final UUID recipient) {
        return new RewardContext(channel, recipient, List.of(new RewardSource.Player(recipient)));
    }

    public RewardContext require(final RewardChannel expectedChannel, final UUID expectedRecipient) {
        if (channel != expectedChannel || !recipient.equals(expectedRecipient)) {
            throw new IllegalArgumentException("Reward context does not match the mutation");
        }
        return this;
    }

    public RewardContext {
        java.util.Objects.requireNonNull(channel); java.util.Objects.requireNonNull(recipient); sources = List.copyOf(sources);
        if (sources.isEmpty() || sources.size() > 64) throw new IllegalArgumentException("Reward source lineage must contain 1..64 immutable identities");
    }
}
