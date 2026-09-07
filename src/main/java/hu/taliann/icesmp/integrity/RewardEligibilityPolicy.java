package hu.taliann.icesmp.integrity;

@FunctionalInterface
public interface RewardEligibilityPolicy {
    RewardDecision evaluate(RewardContext context);
    /** World drops can exist without a recipient. Never invent a player identity for that check. */
    default RewardDecision evaluateSources(final RewardSourceContext context) { return RewardDecision.deny("SOURCE_POLICY_UNAVAILABLE"); }
}
