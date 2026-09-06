package hu.taliann.icesmp.integrity;

@FunctionalInterface
public interface RewardEligibilityPolicy { RewardDecision evaluate(RewardContext context); }
