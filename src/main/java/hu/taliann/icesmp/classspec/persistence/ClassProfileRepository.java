package hu.taliann.icesmp.classspec.persistence;

import hu.taliann.icesmp.classspec.domain.ClassProfile;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Revision/CAS protected durable Profile v2 boundary. */
public interface ClassProfileRepository {

    long MISSING_REVISION = -1L;

    CompletionStage<LoadResult> load(UUID playerId);

    CompletionStage<ClassProfile> save(UUID playerId, long expectedRevision, ClassProfile nextProfile);

    CompletionStage<QuarantineRecord> quarantine(UUID playerId, byte[] originalPayload, String reason);

    CompletionStage<Void> flush(UUID playerId);

    CompletionStage<Void> flushAll();

    void invalidate(UUID playerId);

    Optional<ClassProfile> cached(UUID playerId);

    Optional<String> sessionBlockReason(UUID playerId);

    Optional<String> quarantineReason(UUID playerId);

    void blockSession(UUID playerId, String reason);

    record LoadResult(Status status, ClassProfile profile, String diagnostic) {
        public LoadResult {
            Objects.requireNonNull(status, "status");
            diagnostic = diagnostic == null ? "" : diagnostic;
            if (status == Status.FOUND && profile == null) {
                throw new IllegalArgumentException("FOUND requires a profile");
            }
            if (status != Status.FOUND && profile != null) {
                throw new IllegalArgumentException("Only FOUND may expose a profile");
            }
        }

        public static LoadResult missing() {
            return new LoadResult(Status.MISSING, null, "");
        }

        public static LoadResult found(final ClassProfile profile) {
            return new LoadResult(Status.FOUND, Objects.requireNonNull(profile, "profile"), "");
        }

        public static LoadResult quarantined(final String diagnostic) {
            return new LoadResult(Status.QUARANTINED, null, diagnostic);
        }
    }

    enum Status {
        MISSING,
        FOUND,
        QUARANTINED
    }

    record QuarantineRecord(UUID playerId, long createdAtEpochMillis, String reason, String fileName) {
        public QuarantineRecord {
            Objects.requireNonNull(playerId, "playerId");
            reason = reason == null ? "" : reason;
            fileName = fileName == null ? "" : fileName;
        }
    }
}
