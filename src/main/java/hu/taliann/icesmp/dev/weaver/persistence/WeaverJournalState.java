package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.projection.WeaverProjection;
import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import java.util.*;

/** One durable snapshot owns receipts, effective projections and reward evidence; none can publish alone. */
public record WeaverJournalState(long revision, Map<UUID, WeaverOperationRecord> operations,
                                 Map<UUID, WeaverReceipt> receipts, long projectionSequence,
                                 Map<UUID, WeaverEffectIntent> intents, Map<UUID, WeaverProjection> projections,
                                 Map<UUID, WeaverInfluenceRecord> influences) {
    private record EvidenceKey(DeveloperInfluence origin, WeaverInfluenceTarget target) { }
    public static final int MAX_OPERATIONS = 2056;
    public static final int MAX_RECEIPTS = 2048;
    public static final int MAX_INFLUENCES = 16_384;
    public WeaverJournalState {
        operations = Map.copyOf(operations); receipts = Map.copyOf(receipts); intents = Map.copyOf(intents);
        projections = Map.copyOf(projections); influences = Map.copyOf(influences);
        if (revision < 0 || projectionSequence < 0 || operations.size() > MAX_OPERATIONS || receipts.size() > MAX_RECEIPTS
                || influences.size() > MAX_INFLUENCES || intents.size() > MAX_OPERATIONS) throw new IllegalArgumentException("Journal capacity exceeded");
        if (operations.values().stream().filter(operation -> operation.status() == OperationStatus.PREPARED || operation.status() == OperationStatus.APPLIED).count() > 8) {
            throw new IllegalArgumentException("Too many unresolved active operations");
        }
        for (final var entry : operations.entrySet()) if (!entry.getKey().equals(entry.getValue().operationId())) throw new IllegalArgumentException("Journal operation key mismatch");
        for (final var entry : receipts.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().receiptId()) || (!operations.containsKey(entry.getValue().operationId()) || !operations.get(entry.getValue().operationId()).receipt().filter(entry.getValue()::equals).isPresent())) throw new IllegalArgumentException("Receipt has no journal operation");
        }
        final Map<UUID, Set<WeaverInfluenceTarget>> evidenceByOperation = new HashMap<>(); final Set<EvidenceKey> activeEvidence = new HashSet<>();
        for (final WeaverInfluenceRecord influence : influences.values()) {
            evidenceByOperation.computeIfAbsent(influence.influence().operationId(), ignored -> new HashSet<>()).add(influence.target());
            if (influence.active()) activeEvidence.add(new EvidenceKey(influence.influence(), influence.target()));
        }
        for (final WeaverOperationRecord operation : operations.values()) {
            if (operation.receipt().isPresent() && !operation.receipt().get().equals(receipts.get(operation.receipt().get().receiptId()))) throw new IllegalArgumentException("Operation receipt differs from durable receipt");
            if (operation.request().integrityMode() == IntegrityMode.SANDBOX) {
                final WeaverInfluenceTarget target = WeaverInfluenceTarget.subject(operation.subject());
                if ((operation.status() == OperationStatus.PREPARED || operation.status() == OperationStatus.NEEDS_REVIEW && operation.receipt().isEmpty())
                        && !intents.getOrDefault(operation.operationId(), WeaverEffectIntent.none()).targets().contains(target)) throw new IllegalArgumentException("Sandbox uncertainty lacks durable subject quarantine");
                if (operation.receipt().isPresent() && !evidenceByOperation.getOrDefault(operation.operationId(), Set.of()).contains(target)) throw new IllegalArgumentException("Sandbox receipt lacks durable subject influence");
            }
        }
        for (final var entry : intents.entrySet()) {
            final WeaverOperationRecord operation = operations.get(entry.getKey());
            if (operation == null || operation.status() == OperationStatus.ABORTED || operation.status() == OperationStatus.COMPENSATED
                    || operation.status() == OperationStatus.COMMITTED || entry.getValue().targets().isEmpty()) throw new IllegalArgumentException("Orphan or empty influence intent");
        }
        final Map<SubjectRef, Integer> subjectCounts = new HashMap<>(); final Set<Long> sequences = new HashSet<>();
        int sessions = 0; int persistent = 0;
        for (final var entry : projections.entrySet()) {
            final WeaverProjection projection = entry.getValue(); final WeaverOperationRecord operation = origin(operations, projection.influence());
            if (!entry.getKey().equals(projection.projectionId()) || projection.sequence() > projectionSequence || !sequences.add(projection.sequence())
                    || !operation.providerId().equals(projection.providerId()) || !operation.subject().equals(projection.subject())
                    || operation.request().lifetime() != projection.lifetime() || !operation.beforeFingerprint().equals(projection.canonicalFingerprintAtApply())
                    || operation.receipt().isEmpty() || operation.status() == OperationStatus.COMPENSATED) throw new IllegalArgumentException("Projection origin mismatch");
            if (projection.lifetime() == Lifetime.SESSION) sessions++; else persistent++;
            if (subjectCounts.merge(projection.subject(), 1, Integer::sum) > 32) throw new IllegalArgumentException("Projection subject capacity exceeded");
            final boolean evidence = activeEvidence.contains(new EvidenceKey(projection.influence(), WeaverInfluenceTarget.subject(projection.subject())));
            if (!evidence) throw new IllegalArgumentException("Projection lacks active influence evidence");
        }
        if (sessions > 256 || persistent > 1024) throw new IllegalArgumentException("Projection lifetime capacity exceeded");
        for (final var entry : influences.entrySet()) {
            origin(operations, entry.getValue().influence());
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("Influence identity mismatch");
        }
    }
    static WeaverOperationRecord origin(final Map<UUID, WeaverOperationRecord> operations, final DeveloperInfluence influence) {
        final WeaverOperationRecord operation = operations.get(influence.operationId());
        if (operation == null || !operation.actorId().equals(influence.actorId()) || !operation.request().actionId().equals(influence.actionId())
                || operation.request().integrityMode() != influence.mode() || influence.appliedAt() < operation.preparedAt()
                || operation.status() == OperationStatus.PREPARED || operation.status() == OperationStatus.ABORTED) throw new IllegalArgumentException("Influence origin mismatch");
        return operation;
    }
    public static WeaverJournalState empty() { return new WeaverJournalState(0, Map.of(), Map.of(), 0, Map.of(), Map.of(), Map.of()); }
    public WeaverJournalState replace(final WeaverOperationRecord operation) {
        return replace(operation, intents, projections, influences, projectionSequence);
    }
    public WeaverJournalState replace(final WeaverOperationRecord operation, final Map<UUID, WeaverEffectIntent> nextIntents,
            final Map<UUID, WeaverProjection> nextProjections, final Map<UUID, WeaverInfluenceRecord> nextInfluences, final long sequence) {
        final Map<UUID, WeaverOperationRecord> next = new HashMap<>(operations); next.put(operation.operationId(), operation);
        final Map<UUID, WeaverReceipt> nextReceipts = new HashMap<>(receipts);
        operation.receipt().ifPresent(receipt -> nextReceipts.put(receipt.receiptId(), receipt));
        return new WeaverJournalState(Math.addExact(revision, 1), next, nextReceipts, sequence, nextIntents, nextProjections, nextInfluences);
    }
}
