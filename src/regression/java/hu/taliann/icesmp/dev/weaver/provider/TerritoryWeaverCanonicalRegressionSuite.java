package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.persistence.OperationStatus;
import hu.taliann.icesmp.dev.weaver.persistence.WeaverOperationRecord;
import hu.taliann.icesmp.dev.weaver.persistence.WeaverJournalState;
import hu.taliann.icesmp.dev.weaver.provider.TerritoryWeaverProjectionRegressionSuite.*;
import hu.taliann.icesmp.territory.TerritoryRevision;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static hu.taliann.icesmp.dev.weaver.provider.TerritoryCanonicalActions.*;
import static hu.taliann.icesmp.dev.weaver.provider.TerritoryWeaverProvider.*;

/** Real canonical YAML and Weaver journal boundary/recovery; no direct domain file edits. */
public final class TerritoryWeaverCanonicalRegressionSuite {
    private static int assertions;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(10, TimeUnit.SECONDS); }
    private static void rejects(Runnable work) { try { work.run(); throw new AssertionError("refusal expected"); } catch (IllegalArgumentException | WeaverDomainRejection expected) { assertions++; } }
    private static WeaverValue integer(int value) { return new WeaverValue(WeaverTypeId.parse("weaver:int@1"), Map.of("value", value), "territory", FACET, Set.of(), System.currentTimeMillis()); }
    private static ActionRequest request(Fixture f, String action, Map<String, WeaverValue> values) {
        final Map<String, WeaverValue> parameters = new HashMap<>(values); parameters.put("territory", f.value("territory.zones", zoneKey("first")));
        return new ActionRequest(action, parameters, Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM);
    }
    public static void main(String[] args) throws Exception {
        typedMutationsAndUndo(); admissionAndDrift(); journalCrashBoundaries(); recoveryConflicts();
        System.out.println("Territory Weaver canonical passed. assertions=" + assertions);
    }
    private static void typedMutationsAndUndo() throws Exception {
        try (final var f = new Fixture()) {
            final var original = f.manager.getById("first");
            for (final var request : List.of(request(f, RENAME, Map.of("value", scalar("Renamed", System.currentTimeMillis()))),
                    request(f, TYPE, Map.of("value", f.value("territory.types", "faction"))),
                    request(f, OWNER, Map.of("value", f.value("territory.owners", "dark"))),
                    request(f, Y, Map.of("minimum", integer(2), "maximum", integer(6))))) {
                final var receipt = f.execute(request); final var nativeReceipt = f.manager.adjustmentReceipt(receipt.operationId()).orElseThrow();
                check(!f.manager.getById("first").equals(original), "canonical field changes through native authority");
                check(nativeReceipt.beforeFingerprint().equals(TerritoryRevision.fingerprint(original)) && nativeReceipt.afterFingerprint().equals(TerritoryRevision.fingerprint(f.manager.getById("first"))), "native acknowledgement binds actual states");
                check(receipt.risk() == RiskLevel.CANONICAL && receipt.integrityMode() == IntegrityMode.LIVE_GM && receipt.undo().isPresent(), "canonical receipt axes and compensating Undo");
                check(f.journal.snapshot().projections().isEmpty(), "canonical transaction is not a shadow projection");
                final var compensation = f.undo(receipt);
                check(f.manager.getById("first").equals(original), "conditional compensating transaction restores only changed field");
                check(!compensation.operationId().equals(receipt.operationId()) && f.manager.adjustmentReceipt(receipt.operationId()).isPresent()
                        && f.manager.adjustmentReceipt(compensation.operationId()).isPresent(), "Undo preserves both native receipts");
                check(f.journal.snapshot().receipts().get(receipt.receiptId()).status() == ReceiptStatus.UNDONE, "Weaver history records compensation");
            }
            check(f.ioStages.get() == 8, "four mutations and four compensations use I/O owner after PREPARED");
        }
    }
    private static void admissionAndDrift() throws Exception {
        try (final var f = new Fixture()) {
            final var request = request(f, RENAME, Map.of("value", scalar("Desired", System.currentTimeMillis())));
            final var sandbox = new ProviderContext(f.context.authority(), f.types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            rejects(() -> f.provider.prepare(sandbox, f.snapshot(), new ActionRequest(RENAME, request.parameters(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX)));
            final var live = new ProviderContext(f.context.authority(), f.types, Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM);
            final var snapshot = f.snapshot(); final var prepared = f.provider.prepare(live, snapshot, request);
            check(f.manager.getById("first").name().equals("first"), "preparation does not write canonical state");
            f.manager.rename("first", "External");
            try { await(f.execution.execute("territory", live, snapshot, request, prepared, f.provider.prepareEffects(live, snapshot, request, prepared), () -> live.authority()));
                throw new AssertionError("stale canonical snapshot expected"); } catch (ExecutionException expected) { assertions++; }
            check(f.manager.getById("first").name().equals("External") && f.manager.adjustmentReceipt(prepared.operationId()).isEmpty(), "stale GUI cannot mutate or create native receipt");
        }
        try (final var f = new Fixture()) {
            final var receipt = f.execute(request(f, RENAME, Map.of("value", scalar("Applied", System.currentTimeMillis()))));
            f.manager.rename("first", "Later native edit");
            rejects(() -> new WeaverUndoCoordinator(f.journal, f.registry).target(f.context.authority(), receipt.receiptId(), f.snapshot()));
            check(f.manager.getById("first").name().equals("Later native edit"), "Undo does not overwrite external canonical drift");
        }
    }
    private static void journalCrashBoundaries() throws Exception {
        for (int boundary = 1; boundary <= 4; boundary++) for (boolean afterWrite : List.of(false, true)) {
            final Storage storage = new Storage(); final UUID world = UUID.randomUUID();
            final Path file = Files.createTempDirectory("territory-canonical-journal-").resolve("territories.yml");
            try (final var f = new Fixture(storage, world, file)) {
                final var request = request(f, RENAME, Map.of("value", scalar("Durable native", System.currentTimeMillis())));
                storage.failAt = boundary; storage.afterWrite = afterWrite;
                try { f.execute(request); throw new AssertionError("injected crash expected"); } catch (ExecutionException expected) { assertions++; }
            }
            storage.failAt = 0;
            try (final var f = new Fixture(storage, world, file)) {
                final byte[] nativeBytes = Files.readAllBytes(file); final var recovery = f.recovery();
                f.loaded.set(false); await(recovery.start());
                if (boundary > 1 || afterWrite) check(!recovery.pending().isEmpty() || f.journal.snapshot().operations().values().iterator().next().status() == OperationStatus.COMMITTED, "unavailable world remains pending");
                f.loaded.set(true); await(recovery.worldAvailable(world, OptionalLong.empty()));
                final boolean applied = boundary >= 2;
                check(f.manager.getById("first").name().equals(applied ? "Durable native" : "first"), "native receipt observation distinguishes PREPARED from applied native state");
                check(Arrays.equals(nativeBytes, Files.readAllBytes(file)) && f.ioStages.get() == 0, "recovery never repeats native mutation or writes domain YAML");
                check(f.journal.snapshot().operations().values().stream().allMatch(o -> o.status() == (applied ? OperationStatus.COMMITTED : OperationStatus.ABORTED)), "observed canonical state settles journal");
                if (applied) {
                    final var operation = f.journal.snapshot().operations().values().iterator().next();
                    final var receipt = operation.receipt().orElseThrow();
                    check(f.manager.adjustmentReceipt(operation.operationId()).isPresent(), "recovered Weaver receipt has real native acknowledgement");
                    f.undo(receipt); check(f.manager.getById("first").name().equals("first"), "reconstructed receipt supports conditional compensation");
                }
                recovery.close();
            }
        }
    }
    @SuppressWarnings("unchecked")
    private static void recoveryConflicts() throws Exception {
        for (boolean externalDrift : List.of(false, true)) {
            final Storage storage = new Storage(); final UUID world = UUID.randomUUID();
            final Path file = Files.createTempDirectory("territory-recovery-conflict-").resolve("territories.yml");
            try (final var f = new Fixture(storage, world, file)) {
                storage.failAt = 2;
                try { f.execute(request(f, RENAME, Map.of("value", scalar("Native applied", System.currentTimeMillis())))); throw new AssertionError("crash expected"); }
                catch (ExecutionException expected) { assertions++; }
            }
            storage.failAt = 0;
            if (!externalDrift) {
                // Fault fixture changes the serialized recovery plan, never a native private map.
                final var state = storage.readState(); final var operation = state.operations().values().iterator().next();
                final var fields = new HashMap<>(operation.recoveryPayload().fields());
                final var reverse = new HashMap<>((Map<String, Object>) fields.get("reverse"));
                final var wrong = new HashMap<>((Map<String, Object>) reverse.get("value")); wrong.put("payload", Map.of("value", "Unrelated replacement"));
                reverse.put("value", wrong); fields.put("reverse", reverse);
                final var changed = new WeaverOperationRecord(operation.operationId(), operation.actorId(), operation.providerId(), operation.request(), operation.subject(),
                        operation.beforeFingerprint(), operation.afterFingerprint(), new OperationRecoveryPayload(1, fields), operation.status(), operation.revision(), operation.preparedAt(), operation.updatedAt(), operation.receipt(), operation.pendingAudit(), operation.undoClaim());
                storage.writeState(new WeaverJournalState(state.revision(), Map.of(changed.operationId(), changed), state.receipts(), state.projectionSequence(), state.intents(), state.projections(), state.influences(), state.effectDeltas()));
            }
            try (final var f = new Fixture(storage, world, file)) {
                if (externalDrift) f.manager.rename("first", "External after crash");
                final var bytes = Files.readAllBytes(file); final var recovery = f.recovery(); await(recovery.start());
                check(f.journal.snapshot().operations().values().iterator().next().status() == OperationStatus.NEEDS_REVIEW, "external drift or invalid inverse requires review");
                check(f.journal.snapshot().receipts().isEmpty() && f.ioStages.get() == 0, "conflict fabricates no committed receipt and replays nothing");
                check(Arrays.equals(bytes, Files.readAllBytes(file)), "conflict never repairs or rewrites native state"); recovery.close();
            }
        }
    }
}
