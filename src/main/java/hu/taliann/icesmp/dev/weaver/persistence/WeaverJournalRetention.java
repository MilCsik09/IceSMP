package hu.taliann.icesmp.dev.weaver.persistence;

import java.util.*;

/** Only settled, unreferenced history can rotate; live effects and reward evidence never expire to make room. */
public final class WeaverJournalRetention {
    private WeaverJournalRetention() { }
    public static WeaverJournalState trim(final WeaverJournalState state, final long now, final int receiptLimit, final int operationLimit, final Set<UUID> protectedOperations) {
        if (now < 0 || receiptLimit < 0 || receiptLimit > WeaverJournalState.MAX_RECEIPTS || operationLimit < 0 || operationLimit > WeaverJournalState.MAX_OPERATIONS) throw new IllegalArgumentException("Invalid retention bounds");
        if (state.receipts().size() <= receiptLimit && state.operations().size() <= operationLimit) return state;
        final Set<UUID> pinned = new HashSet<>(protectedOperations);
        state.operations().values().stream().filter(operation -> operation.pendingAudit() || !Set.of(OperationStatus.COMMITTED, OperationStatus.COMPENSATED, OperationStatus.ABORTED).contains(operation.status()))
                .forEach(operation -> pinned.add(operation.operationId()));
        state.projections().values().forEach(projection -> pinned.add(projection.influence().operationId()));
        state.influences().values().stream().filter(influence -> influence.active() || influence.quarantines(now)).forEach(influence -> pinned.add(influence.influence().operationId()));
        pinned.addAll(state.intents().keySet());
        final Map<UUID, Set<UUID>> dependencies = new HashMap<>(); final Map<UUID, Integer> incoming = new HashMap<>();
        for (final WeaverOperationRecord operation : state.operations().values()) {
            final Set<UUID> refs = new HashSet<>();
            operation.undoClaim().ifPresent(claim -> refs.add(state.receipts().get(claim.receiptId()).operationId()));
            final WeaverEffectDelta delta = state.effectDeltas().get(operation.operationId());
            if (delta != null) {
                delta.added().values().forEach(projection -> refs.add(projection.influence().operationId()));
                delta.removed().values().forEach(projection -> refs.add(projection.influence().operationId()));
                delta.endedBefore().values().forEach(influence -> refs.add(influence.influence().operationId()));
            }
            refs.remove(operation.operationId()); dependencies.put(operation.operationId(), Set.copyOf(refs));
            refs.forEach(ref -> incoming.merge(ref, 1, Integer::sum));
        }
        final PriorityQueue<WeaverOperationRecord> candidates = new PriorityQueue<>(Comparator.comparingLong(WeaverOperationRecord::updatedAt).thenComparing(WeaverOperationRecord::operationId));
        state.operations().values().stream().filter(operation -> !pinned.contains(operation.operationId()) && incoming.getOrDefault(operation.operationId(), 0) == 0).forEach(candidates::add);
        int receipts = state.receipts().size(); int operations = state.operations().size(); final Set<UUID> removed = new HashSet<>();
        while ((receipts > receiptLimit || operations > operationLimit) && !candidates.isEmpty()) {
            final WeaverOperationRecord operation = candidates.remove(); if (!removed.add(operation.operationId())) continue;
            operations--; if (operation.receipt().isPresent()) receipts--;
            for (final UUID dependency : dependencies.get(operation.operationId())) if (incoming.merge(dependency, -1, Integer::sum) == 0 && !pinned.contains(dependency)) candidates.add(state.operations().get(dependency));
        }
        if (removed.isEmpty()) return state;
        final Map<UUID, WeaverOperationRecord> nextOperations = new HashMap<>(state.operations()); removed.forEach(nextOperations::remove);
        final Map<UUID, hu.taliann.icesmp.dev.weaver.api.WeaverReceipt> nextReceipts = new HashMap<>(state.receipts()); nextReceipts.values().removeIf(receipt -> removed.contains(receipt.operationId()));
        final Map<UUID, hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceRecord> nextInfluences = new HashMap<>(state.influences()); nextInfluences.values().removeIf(influence -> removed.contains(influence.influence().operationId()));
        final Map<UUID, WeaverEffectDelta> nextDeltas = new HashMap<>(state.effectDeltas()); removed.forEach(nextDeltas::remove);
        return new WeaverJournalState(Math.addExact(state.revision(), 1), nextOperations, nextReceipts, state.projectionSequence(), state.intents(), state.projections(), nextInfluences, nextDeltas);
    }
}
