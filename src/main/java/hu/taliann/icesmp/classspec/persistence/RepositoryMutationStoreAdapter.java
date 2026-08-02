package hu.taliann.icesmp.classspec.persistence;

import hu.taliann.icesmp.classspec.application.ClassProfileMutationStore;
import hu.taliann.icesmp.classspec.domain.ClassProfile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** Maps repository exceptions to the application mutation contract without publishing candidates. */
public final class RepositoryMutationStoreAdapter implements ClassProfileMutationStore {

    private final ClassProfileRepository repository;

    public RepositoryMutationStoreAdapter(final ClassProfileRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<ClassProfile> cached(final UUID playerId) {
        return repository.cached(playerId);
    }

    @Override
    public Optional<String> sessionBlockReason(final UUID playerId) {
        return repository.sessionBlockReason(playerId);
    }

    @Override
    public Optional<String> quarantineReason(final UUID playerId) {
        return repository.quarantineReason(playerId);
    }

    @Override
    public CompletionStage<SaveResult> save(final UUID playerId, final long expectedRevision,
                                            final ClassProfile candidate) {
        return repository.save(playerId, expectedRevision, candidate)
                .handle((durable, failure) -> {
                    if (failure == null) {
                        return SaveResult.committed(durable);
                    }
                    final Throwable root = unwrap(failure);
                    final ClassProfile current = repository.cached(playerId).orElse(null);
                    if (root instanceof ProfileRepositoryException.RevisionConflict conflict) {
                        return SaveResult.conflict(current, conflict.actual());
                    }
                    if (root instanceof ProfileRepositoryException
                            && root.getMessage() != null
                            && root.getMessage().contains("lifecycle")) {
                        return SaveResult.stopped(current);
                    }
                    return SaveResult.failed(current, root.getMessage());
                });
    }


    @Override
    public CompletionStage<ClassProfile> recover(final UUID playerId, final String evidenceId,
                                                  final String auditId) {
        return repository.recover(playerId, evidenceId, auditId);
    }

    @Override
    public Optional<String> quarantineEvidenceId(final UUID playerId) {
        return repository instanceof YamlClassProfileRepository yaml
                ? yaml.quarantineEvidenceId(playerId) : Optional.empty();
    }

    @Override
    public void blockSession(final UUID playerId, final String reason) {
        repository.blockSession(playerId, reason);
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
