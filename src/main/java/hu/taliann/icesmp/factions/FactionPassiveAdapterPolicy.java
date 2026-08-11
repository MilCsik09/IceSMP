package hu.taliann.icesmp.factions;

import java.util.Objects;

/** Bukkit-free adapter decisions that must remain identical for every listener path. */
public final class FactionPassiveAdapterPolicy {

    /** A cancelled target acquisition must also replace the requested target with null. */
    public record TargetMutation(boolean cancelEvent, boolean clearRequestedTarget) {
    }

    private FactionPassiveAdapterPolicy() {
    }

    public static TargetMutation targetMutation(
            final FactionPassivePolicy.TargetDecision decision) {
        Objects.requireNonNull(decision, "decision");
        return decision == FactionPassivePolicy.TargetDecision.ALLOW
                ? new TargetMutation(false, false)
                : new TargetMutation(false, true);
    }
}
