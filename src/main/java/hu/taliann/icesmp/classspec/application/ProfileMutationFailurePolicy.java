package hu.taliann.icesmp.classspec.application;

import java.util.Objects;

/**
 * Central fail-closed policy for economic and runtime side effects around a
 * profile mutation.
 */
public final class ProfileMutationFailurePolicy {

    public Action actionFor(final Failure failure) {
        return switch (Objects.requireNonNull(failure, "failure")) {
            case VALIDATION_REJECTED -> new Action(false, false, false, true, false);
            case REVISION_CONFLICT -> new Action(true, true, true, true, true);
            case PERSISTENCE_WRITE -> new Action(true, true, true, true, true);
            // The profile is already durable at this point. Refunding here would turn a
            // successfully committed respec/upgrade into a free operation after recovery.
            case RUNTIME_EFFECT -> new Action(true, true, false, true, true);
            case LIFECYCLE_STOPPED -> new Action(false, true, true, true, false);
        };
    }

    public enum Failure {
        VALIDATION_REJECTED,
        REVISION_CONFLICT,
        PERSISTENCE_WRITE,
        RUNTIME_EFFECT,
        LIFECYCLE_STOPPED
    }

    public record Action(
            boolean blockSession,
            boolean cleanRuntime,
            boolean refundEconomicCost,
            boolean preserveDurableAndLegacyState,
            boolean reloadRequired) {
    }
}
