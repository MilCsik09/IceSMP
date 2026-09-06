package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.api.*;
import java.util.*;

/** Durable publication is a single immutable snapshot; staged writes never mutate its visible maps. */
public record WeaverJournalState(long revision, Map<UUID, WeaverOperationRecord> operations,
                                 Map<UUID, WeaverReceipt> receipts) {
    public static final int MAX_OPERATIONS = 2056;
    public static final int MAX_RECEIPTS = 2048;
    public WeaverJournalState {
        operations = Map.copyOf(operations); receipts = Map.copyOf(receipts);
        if (revision < 0 || operations.size() > MAX_OPERATIONS || receipts.size() > MAX_RECEIPTS) throw new IllegalArgumentException("Journal capacity exceeded");
        if (operations.values().stream().filter(operation -> operation.status() == OperationStatus.PREPARED || operation.status() == OperationStatus.APPLIED).count() > 8) {
            throw new IllegalArgumentException("Too many unresolved active operations");
        }
        operations.forEach((id, operation) -> { if (!id.equals(operation.operationId())) throw new IllegalArgumentException("Journal operation key mismatch"); });
        for (final var entry : receipts.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().receiptId()) || !operations.containsKey(entry.getValue().operationId())) throw new IllegalArgumentException("Receipt has no journal operation");
        }
        for (final WeaverOperationRecord operation : operations.values()) {
            if (operation.receipt().isPresent() && !operation.receipt().get().equals(receipts.get(operation.receipt().get().receiptId()))) {
                throw new IllegalArgumentException("Operation receipt differs from durable receipt");
            }
        }
    }
    public static WeaverJournalState empty() { return new WeaverJournalState(0, Map.of(), Map.of()); }
    public WeaverJournalState replace(final WeaverOperationRecord operation) {
        final Map<UUID, WeaverOperationRecord> next = new HashMap<>(operations); next.put(operation.operationId(), operation);
        final Map<UUID, WeaverReceipt> nextReceipts = new HashMap<>(receipts);
        operation.receipt().ifPresent(receipt -> nextReceipts.put(receipt.receiptId(), receipt));
        return new WeaverJournalState(Math.addExact(revision, 1), next, nextReceipts);
    }
}
