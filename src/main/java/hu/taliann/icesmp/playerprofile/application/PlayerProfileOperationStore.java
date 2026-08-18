package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileOperation;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.OperationSection;
import hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Canonical typed receipt/outbox state for restart-durable player-owned operations.
 *
 * <p>The store never executes Bukkit side effects. Callers prepare an immutable operation,
 * perform their owner-thread effect, persist the affected external state, and only then mark
 * the receipt COMMITTED. PREPARED operations remain recoverable after restart.</p>
 */
public final class PlayerProfileOperationStore {

    private static final int MAX_OPERATIONS = 512;

    public Optional<PlayerProfileOperation> read(final UUID playerId,
                                                 final String operationId) {
        final OperationSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.OPERATIONS, OperationSection.class);
        return Optional.ofNullable(section.operations().get(requireId(operationId)));
    }

    /** Bounded restart-recovery view for one operation type. */
    public java.util.List<PlayerProfileOperation> prepared(final UUID playerId,
                                                            final String type) {
        return byTypeAndStatus(playerId, type, PlayerProfileOperation.Status.PREPARED);
    }

    public java.util.List<PlayerProfileOperation> byTypeAndStatus(
            final UUID playerId, final String type,
            final PlayerProfileOperation.Status status) {
        final String normalizedType = requireId(type);
        Objects.requireNonNull(status, "status");
        final OperationSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.OPERATIONS, OperationSection.class);
        return section.operations().values().stream()
                .filter(operation -> operation.status() == status
                        && operation.type().equals(normalizedType))
                .sorted(Comparator.comparing(PlayerProfileOperation::createdAt))
                .toList();
    }

    public CompletionStage<PlayerProfileOperation> prepare(
            final UUID playerId,
            final String operationId,
            final String type,
            final String fingerprint,
            final Map<String, String> metadata) {
        final String id = requireId(operationId);
        final String normalizedType = requireId(type);
        final String normalizedFingerprint = requireId(fingerprint);
        final Map<String, String> immutableMetadata = metadata == null
                ? Map.of() : Map.copyOf(metadata);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.OPERATIONS, OperationSection.class, current -> {
                    final PlayerProfileOperation existing = current.operations().get(id);
                    if (existing != null) {
                        requireIdentity(existing, normalizedType, normalizedFingerprint);
                        if (existing.status() == PlayerProfileOperation.Status.PREPARED
                                || existing.status() == PlayerProfileOperation.Status.COMMITTED) {
                            return PlayerProfileService.ConditionalMutation.unchanged(existing);
                        }
                    }
                    final Instant now = Instant.now();
                    final PlayerProfileOperation prepared = new PlayerProfileOperation(
                            id, normalizedType, PlayerProfileOperation.Status.PREPARED,
                            normalizedFingerprint, now, now, immutableMetadata);
                    final LinkedHashMap<String, PlayerProfileOperation> operations =
                            writable(current.operations(), id);
                    operations.put(id, prepared);
                    return PlayerProfileService.ConditionalMutation.changed(
                            new OperationSection(operations, current.extensions()), prepared);
                });
    }

    public CompletionStage<PlayerProfileOperation> commit(
            final UUID playerId,
            final String operationId,
            final String type,
            final String fingerprint) {
        return transition(playerId, operationId, type, fingerprint,
                PlayerProfileOperation.Status.COMMITTED);
    }

    public CompletionStage<PlayerProfileOperation> rollback(
            final UUID playerId,
            final String operationId,
            final String type,
            final String fingerprint) {
        return transition(playerId, operationId, type, fingerprint,
                PlayerProfileOperation.Status.ROLLED_BACK);
    }

    public CompletionStage<PlayerProfileOperation> fail(
            final UUID playerId,
            final String operationId,
            final String type,
            final String fingerprint) {
        return transition(playerId, operationId, type, fingerprint,
                PlayerProfileOperation.Status.FAILED);
    }

    private CompletionStage<PlayerProfileOperation> transition(
            final UUID playerId,
            final String operationId,
            final String type,
            final String fingerprint,
            final PlayerProfileOperation.Status target) {
        final String id = requireId(operationId);
        final String normalizedType = requireId(type);
        final String normalizedFingerprint = requireId(fingerprint);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.OPERATIONS, OperationSection.class, current -> {
                    final PlayerProfileOperation existing = current.operations().get(id);
                    if (existing == null) {
                        throw new IllegalStateException("operation is not prepared: " + id);
                    }
                    requireIdentity(existing, normalizedType, normalizedFingerprint);
                    if (existing.status() == target) {
                        return PlayerProfileService.ConditionalMutation.unchanged(existing);
                    }
                    if (existing.status() == PlayerProfileOperation.Status.COMMITTED
                            && target != PlayerProfileOperation.Status.COMMITTED) {
                        throw new IllegalStateException("committed operation is terminal: " + id);
                    }
                    if (target == PlayerProfileOperation.Status.COMMITTED
                            && existing.status() != PlayerProfileOperation.Status.PREPARED) {
                        throw new IllegalStateException(
                                "only PREPARED operation may commit: " + id + " / "
                                        + existing.status());
                    }
                    final PlayerProfileOperation updated = new PlayerProfileOperation(
                            existing.operationId(), existing.type(), target,
                            existing.fingerprint(), existing.createdAt(), Instant.now(),
                            existing.metadata());
                    final LinkedHashMap<String, PlayerProfileOperation> operations =
                            new LinkedHashMap<>(current.operations());
                    operations.put(id, updated);
                    return PlayerProfileService.ConditionalMutation.changed(
                            new OperationSection(operations, current.extensions()), updated);
                });
    }

    private static LinkedHashMap<String, PlayerProfileOperation> writable(
            final Map<String, PlayerProfileOperation> source,
            final String incomingId) {
        final LinkedHashMap<String, PlayerProfileOperation> operations =
                new LinkedHashMap<>(source);
        if (operations.containsKey(incomingId) || operations.size() < MAX_OPERATIONS) {
            return operations;
        }
        final ArrayList<PlayerProfileOperation> terminal = new ArrayList<>(
                operations.values().stream()
                        .filter(operation -> operation.status()
                                != PlayerProfileOperation.Status.PREPARED)
                        .toList());
        terminal.sort(Comparator.comparing(PlayerProfileOperation::updatedAt));
        if (terminal.isEmpty()) {
            throw new PlayerProfileTransactionManager.LedgerSaturated();
        }
        operations.remove(terminal.getFirst().operationId());
        return operations;
    }

    private static void requireIdentity(final PlayerProfileOperation operation,
                                        final String type,
                                        final String fingerprint) {
        if (!operation.type().equals(type)
                || !operation.fingerprint().equals(fingerprint)) {
            throw new IllegalStateException(
                    "operation id collision with different identity: "
                            + operation.operationId());
        }
    }

    private static String requireId(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("operation identity is required");
        }
        final String cleaned = value.trim();
        if (cleaned.length() > 128) {
            throw new IllegalArgumentException("operation identity is too long");
        }
        return cleaned;
    }
}
