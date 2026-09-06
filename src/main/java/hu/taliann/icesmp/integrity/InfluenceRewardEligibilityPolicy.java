package hu.taliann.icesmp.integrity;

import java.util.Objects;
import java.util.UUID;

/** This neutral gate consumes influence evidence; it cannot create gameplay credit or reward receipts. */
public final class InfluenceRewardEligibilityPolicy implements RewardEligibilityPolicy {
    public enum Evidence { CLEAN, QUARANTINED, UNAVAILABLE }
    public interface Lookup {
        Evidence recipient(UUID player);
        Evidence source(RewardSource source);
    }
    private final Lookup lookup;
    public InfluenceRewardEligibilityPolicy(final Lookup lookup) { this.lookup = Objects.requireNonNull(lookup); }
    @Override public RewardDecision evaluate(final RewardContext context) {
        Objects.requireNonNull(context);
        try {
            final Evidence player = Objects.requireNonNull(lookup.recipient(context.recipient()));
            if (player != Evidence.CLEAN) return RewardDecision.deny(player == Evidence.QUARANTINED ? "PLAYER_QUARANTINED" : "INFLUENCE_UNAVAILABLE");
            for (final RewardSource source : context.sources()) {
                final Evidence evidence = Objects.requireNonNull(lookup.source(source));
                if (evidence != Evidence.CLEAN) return RewardDecision.deny(evidence == Evidence.QUARANTINED ? "SOURCE_QUARANTINED" : "INFLUENCE_UNAVAILABLE");
            }
            return RewardDecision.allow();
        } catch (final RuntimeException | LinkageError unavailable) { return RewardDecision.deny("INFLUENCE_UNAVAILABLE"); }
    }
}
