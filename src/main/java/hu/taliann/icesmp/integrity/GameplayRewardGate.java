package hu.taliann.icesmp.integrity;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Shared neutral admission gate. It owns no reward, progression, receipt or influence state. */
public final class GameplayRewardGate {
    private static final AtomicReference<Binding> POLICY = new AtomicReference<>();
    private GameplayRewardGate() { }
    public static final class Binding implements AutoCloseable {
        private final RewardEligibilityPolicy policy;
        private Binding(final RewardEligibilityPolicy policy) { this.policy = Objects.requireNonNull(policy); }
        @Override public void close() { POLICY.compareAndSet(this, null); }
    }
    public static Binding install(final RewardEligibilityPolicy policy) {
        final Binding binding = new Binding(policy);
        if (!POLICY.compareAndSet(null, binding)) throw new IllegalStateException("Reward eligibility policy already installed");
        return binding;
    }
    public static RewardDecision evaluate(final RewardContext context) {
        final Binding binding = POLICY.get();
        if (binding == null) return RewardDecision.deny("POLICY_UNAVAILABLE");
        try {
            final RewardDecision decision = Objects.requireNonNull(binding.policy.evaluate(context));
            return POLICY.get() == binding ? decision : RewardDecision.deny("POLICY_UNAVAILABLE");
        }
        catch (final RuntimeException | LinkageError unavailable) { return RewardDecision.deny("POLICY_UNAVAILABLE"); }
    }
    public static RewardDecision evaluateSources(final RewardSourceContext context) {
        final Binding binding = POLICY.get();
        if (binding == null) return RewardDecision.deny("POLICY_UNAVAILABLE");
        try {
            final RewardDecision decision = Objects.requireNonNull(binding.policy.evaluateSources(context));
            return POLICY.get() == binding ? decision : RewardDecision.deny("POLICY_UNAVAILABLE");
        }
        catch (final RuntimeException | LinkageError unavailable) { return RewardDecision.deny("POLICY_UNAVAILABLE"); }
    }
}
