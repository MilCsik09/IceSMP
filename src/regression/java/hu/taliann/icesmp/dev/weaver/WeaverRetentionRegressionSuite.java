package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.*;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverProjectionRegressionSuite.*;

public final class WeaverRetentionRegressionSuite {
    static final class CapacityStorage implements WeaverJournalStorage {
        WeaverJournalState state = WeaverJournalState.empty(); Map<String, WeaverAuditEntry> audit = Map.of();
        int stateLimit = Integer.MAX_VALUE, auditLimit = Integer.MAX_VALUE, writes; boolean fail, afterWrite;
        @Override public WeaverJournalState readState() { return state; }
        @Override public Map<String, WeaverAuditEntry> readAudit() { return audit; }
        @Override public void validateStateCapacity(final WeaverJournalState value) {
            if (value.operations().size() > stateLimit) throw new WeaverDomainRejection("JOURNAL_BYTE_CAPACITY");
        }
        @Override public void validateAuditCapacity(final Map<String, WeaverAuditEntry> value) {
            if (value.size() > auditLimit) throw new WeaverDomainRejection("JOURNAL_BYTE_CAPACITY");
        }
        @Override public void writeState(final WeaverJournalState value) throws Exception {
            writes++; if (fail && !afterWrite) throw new java.io.IOException("before atomic write");
            state = value; if (fail) throw new java.io.IOException("after atomic write");
        }
        @Override public void writeAudit(final Map<String, WeaverAuditEntry> value) { writes++; audit = value; }
    }
    static WeaverJournalState history(final int size) {
        final Map<UUID, WeaverOperationRecord> operations = new HashMap<>(); final Map<UUID, WeaverReceipt> receipts = new HashMap<>();
        for (int i = 0; i < size; i++) {
            final var prepared = operation(new PlayerRef(UUID.randomUUID()), Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM);
            final var receipt = receipt(prepared);
            final var committed = new WeaverOperationRecord(prepared.operationId(), prepared.actorId(), prepared.providerId(), prepared.request(), prepared.subject(), "before", Optional.of("after"),
                    prepared.recoveryPayload(), OperationStatus.COMMITTED, 2, 1, 2L + i, Optional.of(receipt), false);
            operations.put(committed.operationId(), committed); receipts.put(receipt.receiptId(), receipt);
        }
        return new WeaverJournalState(0, operations, receipts, 0, Map.of(), Map.of(), Map.of());
    }
    static WeaverAuditEntry audit(final WeaverOperationRecord operation, final long time) {
        return new WeaverAuditEntry(operation.operationId(), operation.actorId(), operation.providerId(), operation.request().actionId(), operation.request().integrityMode(), AuditOutcome.COMMITTED, time);
    }
    public static void main(final String[] args) throws Exception {
        admissionAndCrash(); protectedEvidence(); dependencies(); capacityPreflight(); auditRotation();
        System.out.println("Weaver retention passed: atomic full-history admission, before/after write failures, audit idempotency, protected quarantine/projection/Undo lineage and recoverable byte capacity.");
    }
    private static void admissionAndCrash() throws Exception {
        final WeaverJournalState full = history(WeaverJournalState.MAX_RECEIPTS);
        final var oldest = full.operations().values().stream().min(Comparator.comparingLong(WeaverOperationRecord::updatedAt)).orElseThrow();
        final CapacityStorage storage = new CapacityStorage(); storage.state = full; final var originalAudit = audit(oldest, 2); storage.audit = Map.of(originalAudit.key(), originalAudit);
        final WeaverJournal journal = new WeaverJournal(storage); await(journal.load()); final var next = prepared(); await(journal.prepare(next));
        check(storage.writes == 1 && storage.state.operations().containsKey(next.operationId()) && !storage.state.operations().containsKey(oldest.operationId()), "retention and PREPARED were not one durable generation");
        check(storage.state.receipts().size() == 2047 && storage.state.intents().containsKey(next.operationId()), "admission lost receipt capacity or subject quarantine");
        final var replay = new WeaverOperationRecord(oldest.operationId(), oldest.actorId(), oldest.providerId(), oldest.request(), oldest.subject(), "before", Optional.empty(), oldest.recoveryPayload(), OperationStatus.PREPARED, 0, 1, 1, Optional.empty(), false);
        fails(journal.prepare(replay)); check(journal.ready() && storage.writes == 1, "retained audit allowed old operation replay"); await(journal.close());
        for (final boolean after : List.of(false, true)) {
            final CapacityStorage failed = new CapacityStorage(); failed.state = full; failed.fail = true; failed.afterWrite = after;
            final WeaverJournal attempt = new WeaverJournal(failed); await(attempt.load()); final var pending = prepared(); fails(attempt.prepare(pending));
            check(!attempt.ready() && attempt.snapshot().equals(full), "ambiguous acknowledgement published a partial local generation"); fails(attempt.close());
            failed.fail = false; final WeaverJournal restarted = new WeaverJournal(failed); await(restarted.load());
            check(after ? restarted.snapshot().operations().containsKey(pending.operationId()) && !restarted.snapshot().operations().containsKey(oldest.operationId()) : restarted.snapshot().equals(full), "restart split pruning from PREPARED");
            await(restarted.close());
        }
    }
    private static void protectedEvidence() throws Exception {
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage); await(journal.load());
        final var clean = apply(journal, operation(new PlayerRef(UUID.randomUUID()), Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM), WeaverEffectCommit.none());
        final var player = apply(journal, operation(new PlayerRef(UUID.randomUUID()), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), WeaverEffectCommit.none());
        final var entity = apply(journal, operation(new EntityRef(UUID.randomUUID()), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), WeaverEffectCommit.none());
        final var pending = prepared(); await(journal.prepare(pending));
        final var review = prepared(); await(journal.prepare(review)); final var uncertain = await(journal.resolve(review.operationId(), 0, OperationStatus.NEEDS_REVIEW, 3)); await(journal.finishAudit(uncertain.operationId(), uncertain.revision()));
        final var aborted = prepared(); await(journal.prepare(aborted)); await(journal.resolve(aborted.operationId(), 0, OperationStatus.ABORTED, 3));
        final var trimmed = WeaverJournalRetention.trim(journal.snapshot(), 3, 0, 0, Set.of());
        check(!trimmed.operations().containsKey(clean.operationId()) && trimmed.operations().size() == 5, "retention removed unresolved/audit/tail/monotonic evidence");
        final var expired = WeaverJournalRetention.trim(trimmed, 1_000_000, 0, 0, Set.of());
        check(!expired.operations().containsKey(player.operationId()) && expired.operations().containsKey(entity.operationId()), "retention failed to distinguish expired player tail from monotonic entity taint");
        check(new WeaverInfluenceIndex(expired).quarantined(WeaverInfluenceTarget.subject(entity.subject()).source(), Long.MAX_VALUE), "retention washed reward quarantine");
        await(journal.close());
        try (final var fixture = new WeaverUndoRegressionSuite.Fixture()) {
            check(WeaverJournalRetention.trim(fixture.journal.snapshot(), Long.MAX_VALUE, 0, 0, Set.of()) == fixture.journal.snapshot(), "live persistent projection history was removed");
        }
    }
    private static void dependencies() throws Exception {
        try (final var fixture = new WeaverUndoRegressionSuite.Fixture()) {
            final var target = fixture.undo.target(fixture.authority, fixture.receipt.receiptId(), fixture.snapshot);
            final var undone = await(fixture.run(target, fixture.undo.prepare(fixture.context, target.claim(), fixture.snapshot)));
            final var state = fixture.journal.snapshot();
            check(WeaverJournalRetention.trim(state, Long.MAX_VALUE, 0, 0, Set.of(undone.operationId())) == state, "pinned Undo lost original receipt or projection/influence before-images");
            final var one = WeaverJournalRetention.trim(state, Long.MAX_VALUE, 1, 1, Set.of());
            check(one.operations().size() == 1 && one.operations().containsKey(fixture.original.operationId()), "parent rotated before its referencing Undo child");
            check(WeaverJournalRetention.trim(one, Long.MAX_VALUE, 0, 0, Set.of()).operations().isEmpty(), "unreferenced settled dependency chain could not rotate");
        }
        final CapacityStorage storage = new CapacityStorage(); storage.state = history(1); storage.stateLimit = 1;
        final var original = storage.state.operations().values().iterator().next(); final var receipt = original.receipt().orElseThrow();
        final var inverse = new WeaverOperationRecord(UUID.randomUUID(), original.actorId(), original.providerId(), new ActionRequest("fixture.undo", Map.of(), Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM), original.subject(), "after", Optional.empty(),
                original.recoveryPayload(), OperationStatus.PREPARED, 0, 3, 3, Optional.empty(), false, Optional.of(new WeaverUndoClaim(receipt.receiptId(), original.revision(), "after")));
        final WeaverJournal journal = new WeaverJournal(storage); await(journal.load()); fails(journal.prepare(inverse));
        check(journal.ready() && storage.writes == 0 && journal.snapshot().operations().containsKey(original.operationId()), "incoming Undo admission discarded its own source history"); await(journal.close());
    }
    private static void capacityPreflight() throws Exception {
        final CapacityStorage storage = new CapacityStorage(); storage.state = history(5); storage.stateLimit = 5;
        final WeaverJournal journal = new WeaverJournal(storage); await(journal.load()); final var pending = prepared(); await(journal.prepare(pending));
        check(storage.writes == 1 && journal.ready() && storage.state.operations().containsKey(pending.operationId()), "byte preflight did not rotate eligible history before admission");
        storage.stateLimit = 1; final var before = journal.snapshot(); fails(journal.prepare(prepared()));
        check(journal.ready() && journal.snapshot().equals(before) && storage.writes == 1, "capacity refusal froze writer or erased unresolved quarantine");
        storage.stateLimit = 2; await(journal.prepare(prepared())); check(storage.writes == 2, "capacity rejection was sticky after capacity became available"); await(journal.close());
        final var directory = java.nio.file.Files.createTempDirectory("weaver-retention-capacity");
        final var yaml = new YamlWeaverJournalStorage(directory.toFile(), new WeaverJournalCodec(types()), java.util.logging.Logger.getLogger("fixture"));
        try { yaml.validateStateCapacity(history(2048)); throw new AssertionError("real YAML byte cap did not refuse oversized history"); }
        catch (final WeaverDomainRejection expected) { check(expected.code().equals("JOURNAL_BYTE_CAPACITY"), "wrong real YAML capacity refusal"); }
        check(!java.nio.file.Files.exists(directory.resolve("world-weaver-state.yml")), "capacity preflight wrote state");
    }
    private static void auditRotation() throws Exception {
        for (final int limit : List.of(2, 10_000)) {
            final CapacityStorage storage = new CapacityStorage(); storage.auditLimit = limit; final Map<String, WeaverAuditEntry> audit = new HashMap<>();
            for (final var operation : history(limit == 2 ? 2 : 2048).operations().values()) {
                final var entry = audit(operation, 100); audit.put(entry.key(), entry);
            }
            while (audit.size() < limit) { final var entry = audit(prepared(), 100); audit.put(entry.key(), entry); }
            storage.audit = Map.copyOf(audit); final WeaverJournal journal = new WeaverJournal(storage); await(journal.load());
            final var operation = operation(new PlayerRef(UUID.randomUUID()), Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM); apply(journal, operation, WeaverEffectCommit.none());
            check(storage.audit.size() == limit && storage.audit.containsKey(operation.operationId() + ":COMMITTED") && journal.snapshot().operations().get(operation.operationId()).status() == OperationStatus.COMMITTED,
                    "oldest timestamp/byte/count rotation dropped newly acknowledged audit");
            await(journal.close());
        }
    }
}
