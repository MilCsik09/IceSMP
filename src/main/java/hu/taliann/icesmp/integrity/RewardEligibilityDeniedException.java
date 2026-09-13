package hu.taliann.icesmp.integrity;

/** Expected admission refusal: no candidate, receipt or profile revision was committed. */
public final class RewardEligibilityDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public RewardEligibilityDeniedException() { super("Reward eligibility denied"); }
}
