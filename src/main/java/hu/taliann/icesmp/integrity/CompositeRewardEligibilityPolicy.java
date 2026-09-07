package hu.taliann.icesmp.integrity;

import java.util.List;

public final class CompositeRewardEligibilityPolicy implements RewardEligibilityPolicy {
    private final List<RewardEligibilityPolicy> policies;
    public CompositeRewardEligibilityPolicy(final List<RewardEligibilityPolicy> policies) {
        this.policies = List.copyOf(policies);
        if (policies.isEmpty() || policies.size() > 16) throw new IllegalArgumentException("Reward gate requires 1..16 policies");
    }
    @Override public RewardDecision evaluate(final RewardContext context) {
        for (final RewardEligibilityPolicy policy : policies) {
            final RewardDecision decision;
            try { decision = java.util.Objects.requireNonNull(policy.evaluate(context)); }
            catch (final RuntimeException | LinkageError failure) { return RewardDecision.deny("POLICY_UNAVAILABLE"); }
            if (!decision.allowed()) return decision;
        }
        return RewardDecision.allow();
    }
}
