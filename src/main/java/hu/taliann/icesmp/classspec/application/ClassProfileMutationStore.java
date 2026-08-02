package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.ClassProfile;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Narrow application port over the versioned repository/cache. Implementations
 * perform file I/O away from player/entity region threads.
 */
public interface ClassProfileMutationStore {

    Optional<ClassProfile> cached(UUID playerId);

    Optional<String> sessionBlockReason(UUID playerId);

    default Optional<String> quarantineReason(final UUID playerId) {
        return Optional.empty();
    }

    CompletionStage<SaveResult> save(UUID playerId, long expectedRevision, ClassProfile candidate);

    /** Explicit quarantine recovery. The evidence remains preserved and audit-linked. */
    CompletionStage<ClassProfile> recover(UUID playerId, String evidenceId, String auditId);

    default Optional<String> quarantineEvidenceId(final UUID playerId) {
        return Optional.empty();
    }

    void blockSession(UUID playerId, String reason);

    record SaveResult(Status status, ClassProfile durableProfile, long actualRevision, String detail) {

        public SaveResult {
            Objects.requireNonNull(status, "status");
            detail = detail == null ? "" : detail;
            if (status == Status.COMMITTED && durableProfile == null) {
                throw new IllegalArgumentException("Committed save requires the durable profile");
            }
        }

        public static SaveResult committed(final ClassProfile profile) {
            return new SaveResult(Status.COMMITTED, Objects.requireNonNull(profile, "profile"),
                    profile.revision(), "");
        }

        public static SaveResult conflict(final ClassProfile durableProfile, final long actualRevision) {
            return new SaveResult(Status.REVISION_CONFLICT, durableProfile, actualRevision,
                    "expected revision is stale");
        }

        public static SaveResult failed(final ClassProfile durableProfile, final String detail) {
            return new SaveResult(Status.PERSISTENCE_FAILED, durableProfile,
                    durableProfile == null ? -1L : durableProfile.revision(), detail);
        }

        public static SaveResult stopped(final ClassProfile durableProfile) {
            return new SaveResult(Status.LIFECYCLE_STOPPED, durableProfile,
                    durableProfile == null ? -1L : durableProfile.revision(), "repository stopped");
        }

        public enum Status {
            COMMITTED,
            REVISION_CONFLICT,
            PERSISTENCE_FAILED,
            LIFECYCLE_STOPPED
        }
    }
}
