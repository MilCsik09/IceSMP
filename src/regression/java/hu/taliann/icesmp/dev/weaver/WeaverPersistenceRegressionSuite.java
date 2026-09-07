package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.OperationRecoveryPayload;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Files;

public final class WeaverPersistenceRegressionSuite {
    static final class Storage implements WeaverJournalStorage {
        WeaverJournalState state = WeaverJournalState.empty(); Map<String, WeaverAuditEntry> audit = Map.of();
        int writes; int failWrite; boolean afterWrite;
        final List<String> order = new ArrayList<>();
        @Override public WeaverJournalState readState() { return state; }
        @Override public Map<String, WeaverAuditEntry> readAudit() { return audit; }
        private void before() throws java.io.IOException { if (++writes == failWrite && !afterWrite) throw new java.io.IOException("injected"); }
        private void after() throws java.io.IOException { if (writes == failWrite && afterWrite) throw new java.io.IOException("ambiguous acknowledgement"); }
        @Override public void writeState(final WeaverJournalState value) throws Exception { before(); state = value; order.add("STATE:" + value.operations().values().iterator().next().status()); after(); }
        @Override public void writeAudit(final Map<String, WeaverAuditEntry> value) throws Exception { before(); audit = value; order.add("AUDIT"); after(); }
    }
    static WeaverOperationRecord prepared() {
        return new WeaverOperationRecord(UUID.randomUUID(), HiddenDevAuthority.PRIMARY_DEVELOPER, "fixture", new ActionRequest("fixture.mutate", Map.of("count", new WeaverValue(WeaverTypeId.parse("weaver:int@1"), Map.of("value", 7L), "fixture", "fixture.state", Set.of(), 1)), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX),
                new EntityRef(UUID.randomUUID()), "before", Optional.empty(), new OperationRecoveryPayload(1, Map.of("expected", "after", "dotted.key", Map.of("==", "literal-not-bukkit-object", "nested.field", List.of(Map.of("x.y", 1))), "numeric", Map.of("small-long", 1L, "int", 2, "list", List.of(3L, 4.5D, true)))), OperationStatus.PREPARED, 0, 1, 1, Optional.empty(), false);
    }
    static WeaverReceipt receipt(final WeaverOperationRecord operation) {
        return new WeaverReceipt(UUID.randomUUID(), operation.operationId(), operation.providerId(), operation.request().actionId(), operation.subject(), RiskLevel.MUTATING,
                operation.request().lifetime(), operation.request().integrityMode(), "before", "after", Map.of(), Map.of(), Optional.of(new UndoSpec("fixture.undo", "after", Map.of())), 2, ReceiptStatus.COMMITTED);
    }
    static <T> T await(final CompletionStage<T> value) throws Exception { return value.toCompletableFuture().get(10, TimeUnit.SECONDS); }
    static void fails(final CompletionStage<?> value) throws Exception {
        try { await(value); throw new AssertionError("Expected failure"); } catch (final ExecutionException expected) { }
    }
    public static void main(final String[] args) throws Exception {
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage); await(journal.load());
        final WeaverOperationRecord prepared = prepared(); await(journal.prepare(prepared));
        final WeaverReceipt receipt = receipt(prepared); final WeaverOperationRecord applied = await(journal.applied(prepared.operationId(), 0, receipt, 2));
        check(journal.snapshot().operations().get(prepared.operationId()).status() == OperationStatus.APPLIED, "APPLIED not durable before audit");
        fails(journal.resolve(prepared.operationId(), 0, OperationStatus.NEEDS_REVIEW, 3));
        check(journal.ready(), "expected revision conflict closed journal");
        final WeaverOperationRecord committed = await(journal.finishAudit(prepared.operationId(), applied.revision()));
        check(committed.status() == OperationStatus.COMMITTED && !committed.pendingAudit(), "commit not settled");
        check(storage.order.equals(List.of("STATE:PREPARED", "STATE:APPLIED", "AUDIT", "STATE:COMMITTED")), "cross-file commit order changed");
        await(journal.finishAudit(prepared.operationId(), committed.revision())); check(storage.audit.size() == 1, "audit was duplicated");
        await(journal.close()); fails(journal.prepare(prepared()));
        for (int boundary = 1; boundary <= 4; boundary++) {
            for (final boolean afterWrite : List.of(false, true)) {
                final Storage failed = new Storage(); failed.failWrite = boundary; failed.afterWrite = afterWrite;
                final WeaverJournal attempt = new WeaverJournal(failed); await(attempt.load()); final WeaverOperationRecord operation = prepared();
                try {
                    await(attempt.prepare(operation)); final WeaverOperationRecord changed = await(attempt.applied(operation.operationId(), 0, receipt(operation), 2));
                    await(attempt.finishAudit(operation.operationId(), changed.revision()));
                    throw new AssertionError("Injected crash did not fail");
                } catch (final ExecutionException expected) { }
                check(!attempt.ready(), "storage error did not close journal"); fails(attempt.prepare(prepared())); fails(attempt.close());
                failed.failWrite = 0; final WeaverJournal restarted = new WeaverJournal(failed); await(restarted.load());
                final WeaverOperationRecord observed = restarted.snapshot().operations().get(operation.operationId());
                if (boundary == 1 && !afterWrite) check(observed == null, "failed PREPARED was exposed");
                else check(observed != null, "durable operation lost");
                if (boundary >= 3 && !(boundary == 4 && afterWrite)) check(observed.status() == OperationStatus.APPLIED && observed.pendingAudit(), "ambiguous audit committed blindly");
                if (observed != null && observed.pendingAudit()) await(restarted.finishAudit(observed.operationId(), observed.revision()));
                check(failed.audit.size() <= 1, "crash replay duplicated audit"); await(restarted.close());
            }
        }
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        final WeaverJournalCodec codec = new WeaverJournalCodec(types);
        check(codec.decodeState(codec.encodeState(storage.state)).equals(storage.state), "state codec round trip");
        check(codec.decodeAudit(codec.encodeAudit(storage.audit)).equals(storage.audit), "audit codec round trip");
        check(new WeaverValue(WeaverTypeId.parse("weaver:int@1"), Map.of("value", 7), "fixture", "fixture.state", Set.of(), 1)
                .equals(prepared.request().parameters().get("count")), "Integer/Long semantic identity differs across YAML load");
        final Map<String, Object> future = new HashMap<>(codec.encodeState(storage.state)); future.put("schema-version", 4);
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> codec.decodeState(future));
        final Map<String, Object> unknown = new HashMap<>(codec.encodeState(storage.state)); unknown.put("raw-object", new Object());
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> codec.decodeState(unknown));
        final var directory = Files.createTempDirectory("weaver-journal-regression");
        final YamlWeaverJournalStorage yaml = new YamlWeaverJournalStorage(directory.toFile(), codec, java.util.logging.Logger.getLogger("fixture"));
        yaml.writeState(storage.state); yaml.writeAudit(storage.audit);
        check(yaml.readState().equals(storage.state) && yaml.readAudit().equals(storage.audit), "real YAML/fsync round trip");
        System.out.println("Weaver persistence passed: PREPARED/APPLIED/audit/COMMITTED, eight crash boundaries, idempotence, CAS and real YAML round trip.");
    }
    static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
