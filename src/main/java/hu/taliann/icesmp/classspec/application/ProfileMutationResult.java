package hu.taliann.icesmp.classspec.application;

import java.util.Objects;
import java.util.Optional;

/**
 * Result envelope shared by application-level profile mutations. The profile in
 * this object is always the last known durable value; a failed candidate is never
 * exposed as authoritative state.
 */
public record ProfileMutationResult<P>(
        Status status,
        P durableProfile,
        String detail,
        boolean sessionBlocked) {

    public ProfileMutationResult {
        Objects.requireNonNull(status, "status");
        detail = detail == null ? "" : detail;
        if ((status == Status.COMMITTED || status == Status.RUNTIME_EFFECT_FAILED)
                && durableProfile == null) {
            throw new IllegalArgumentException("A durable mutation result requires its committed profile");
        }
        if ((status == Status.PERSISTENCE_FAILED || status == Status.REVISION_CONFLICT
                || status == Status.RUNTIME_EFFECT_FAILED)
                && !sessionBlocked) {
            throw new IllegalArgumentException("Persistence uncertainty must fail closed");
        }
    }

    public static <P> ProfileMutationResult<P> committed(final P durableProfile) {
        return new ProfileMutationResult<>(Status.COMMITTED,
                Objects.requireNonNull(durableProfile, "durableProfile"), "", false);
    }

    public static <P> ProfileMutationResult<P> noChange(final P durableProfile, final String detail) {
        return new ProfileMutationResult<>(Status.NO_CHANGE, durableProfile, detail, false);
    }

    public static <P> ProfileMutationResult<P> rejected(final P durableProfile, final String detail) {
        return new ProfileMutationResult<>(Status.REJECTED, durableProfile, detail, false);
    }

    public static <P> ProfileMutationResult<P> failed(
            final P durableProfile,
            final Status status,
            final String detail) {
        if (status != Status.PERSISTENCE_FAILED
                && status != Status.REVISION_CONFLICT
                && status != Status.RUNTIME_EFFECT_FAILED
                && status != Status.LIFECYCLE_STOPPED) {
            throw new IllegalArgumentException("Not a mutation failure status: " + status);
        }
        final boolean blocked = status != Status.LIFECYCLE_STOPPED;
        return new ProfileMutationResult<>(status, durableProfile, detail, blocked);
    }

    public boolean committed() {
        return status == Status.COMMITTED;
    }

    /** True once CAS persistence committed, even if post-commit runtime reconciliation failed. */
    public boolean durableMutationApplied() {
        return status == Status.COMMITTED || status == Status.RUNTIME_EFFECT_FAILED;
    }

    public Optional<P> durableProfileOptional() {
        return Optional.ofNullable(durableProfile);
    }

    public enum Status {
        COMMITTED,
        NO_CHANGE,
        REJECTED,
        REVISION_CONFLICT,
        PERSISTENCE_FAILED,
        RUNTIME_EFFECT_FAILED,
        LIFECYCLE_STOPPED
    }
}
