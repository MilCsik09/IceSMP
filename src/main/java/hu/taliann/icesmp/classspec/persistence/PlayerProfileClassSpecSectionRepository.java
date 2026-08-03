package hu.taliann.icesmp.classspec.persistence;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionSnapshot;
import hu.taliann.icesmp.playerprofile.domain.SectionHealth;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import hu.taliann.icesmp.playerprofile.persistence.PlayerProfileRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Compatibility application port backed exclusively by the PlayerProfile class-spec section. */
public final class PlayerProfileClassSpecSectionRepository implements ClassSpecSectionRepository {
    private final PlayerProfileRepository repository;
    private final ConcurrentMap<UUID, String> sessionBlocks = new ConcurrentHashMap<>();

    public PlayerProfileClassSpecSectionRepository(final PlayerProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public CompletionStage<LoadResult> load(final UUID playerId) {
        return repository.loadSnapshot(playerId).thenApply(profile -> {
            final SectionHealth health = profile.classSpec().health();
            if (health.status() == SectionHealth.Status.QUARANTINED) {
                return LoadResult.quarantined(health.diagnostic(), health.evidenceId());
            }
            if (!health.usable()) return LoadResult.quarantined(health.diagnostic(),
                    health.evidenceId().isBlank() ? "review-required" : health.evidenceId());
            return LoadResult.found(profile.classSpec().value());
        });
    }

    @Override
    public CompletionStage<ClassSpecSection> save(final UUID playerId, final long expectedRevision,
                                                  final ClassSpecSection nextProfile) {
        Objects.requireNonNull(nextProfile, "nextProfile");
        final ProfileSectionSnapshot<ClassSpecSection> next = new ProfileSectionSnapshot<>(
                ProfileSectionId.CLASS_SPEC, ProfileSectionId.CLASS_SPEC.currentSchema(), nextProfile.revision(),
                Instant.now(), nextProfile, SectionHealth.healthy());
        return repository.saveSection(playerId, ProfileSectionId.CLASS_SPEC, expectedRevision, next)
                .thenCompose(result -> switch (result.status()) {
                    case COMMITTED -> CompletableFuture.completedFuture(result.snapshot().classSpec().value());
                    case STALE_REVISION, STALE_GENERATION -> CompletableFuture.failedFuture(
                            new ProfileRepositoryException.RevisionConflict(expectedRevision,
                                    result.snapshot().classSpec().revision(), "expected revision is stale"));
                    case SECTION_QUARANTINED -> CompletableFuture.failedFuture(
                            new ProfileRepositoryException("class-spec section is quarantined"));
                    case REJECTED -> CompletableFuture.failedFuture(new ProfileRepositoryException(result.detail()));
                });
    }

    @Override
    public CompletionStage<QuarantineRecord> quarantine(final UUID playerId, final byte[] originalPayload,
                                                        final String reason) {
        return repository.quarantineSection(playerId, ProfileSectionId.CLASS_SPEC, originalPayload, reason)
                .thenApply(result -> new QuarantineRecord(playerId, System.currentTimeMillis(), result.detail(),
                        result.evidenceId(), result.evidenceId() + ".bin"));
    }

    @Override
    public CompletionStage<ClassSpecSection> recover(final UUID playerId, final String evidenceId,
                                                     final String auditId) {
        return repository.recoverSection(playerId, ProfileSectionId.CLASS_SPEC, evidenceId, auditId)
                .thenApply(result -> result.snapshot().classSpec().value());
    }

    @Override public CompletionStage<Void> flush(final UUID playerId) { return repository.flush(playerId); }
    @Override public CompletionStage<Void> flushAll() { return repository.flushAll(); }
    @Override public CompletionStage<ShutdownResult> shutdown(final Duration timeout) {
        return repository.shutdown(timeout).thenApply(result -> new ShutdownResult(
                result.drained(), result.pendingOperations(), result.detail()));
    }
    @Override public void invalidate(final UUID playerId) { repository.invalidate(playerId); sessionBlocks.remove(playerId); }
    @Override public Optional<ClassSpecSection> cached(final UUID playerId) {
        return repository.cached(playerId).map(profile -> profile.classSpec().value());
    }
    @Override public Optional<String> sessionBlockReason(final UUID playerId) {
        return Optional.ofNullable(sessionBlocks.get(playerId));
    }
    @Override public Optional<String> quarantineReason(final UUID playerId) {
        return repository.cached(playerId).map(profile -> profile.classSpec().health().diagnostic()).filter(value -> !value.isBlank());
    }
    public Optional<String> quarantineEvidenceId(final UUID playerId) {
        return repository.cached(playerId).map(profile -> profile.classSpec().health().evidenceId()).filter(value -> !value.isBlank());
    }
    @Override public void blockSession(final UUID playerId, final String reason) {
        sessionBlocks.put(playerId, reason == null || reason.isBlank() ? "class-spec session blocked" : reason.trim());
    }
}
