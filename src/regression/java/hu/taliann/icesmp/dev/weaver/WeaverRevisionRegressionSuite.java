package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverTypeCompatibilityRegressionSuite.rejects;

public final class WeaverRevisionRegressionSuite {
    private static WeaverValue value(final int rank, final long capturedAt) {
        return new WeaverValue(new WeaverTypeId("weaver", "int", 1), Map.of("value", rank), "fixture", "fixture.state", Set.of(), capturedAt);
    }
    private static SubjectSnapshot snapshot(final SubjectRef ref, final int rank, final int movement) {
        return new SubjectSnapshot(ref, movement, "full-snapshot-" + rank + "-" + movement,
                Map.of("fixture.rank", value(rank, movement), "minecraft.movement_fixture", value(movement, movement)));
    }
    public static void main(final String[] args) throws Exception {
        final var ref = new PlayerRef(UUID.randomUUID()); final var scope = new WeaverRevisionScope(1, Set.of("fixture.rank"));
        final var first = snapshot(ref, 7, 1); final var moved = snapshot(ref, 7, 2); final var changed = snapshot(ref, 8, 2);
        check(!first.revisionFingerprint().equals(moved.revisionFingerprint()), "fixture lacks full-snapshot movement drift");
        check(scope.apply(first).revisionFingerprint().equals(scope.apply(moved).revisionFingerprint()), "unrelated field/capture time invalidated action");
        check(!scope.apply(first).revisionFingerprint().equals(scope.apply(changed).revisionFingerprint()), "actual mutation drift was ignored");
        check(scope.apply(scope.apply(first)).equals(scope.apply(first)), "revision scoping is not idempotent");
        check(scope.apply(first).facts().equals(first.facts()) && scope.apply(first).ref().equals(first.ref()), "revision selection hid inspect facts or changed identity");
        check(!scope.apply(first).revisionFingerprint().equals(scope.apply(snapshot(new PlayerRef(UUID.randomUUID()), 7, 1)).revisionFingerprint()), "subject identity omitted");
        check(!scope.apply(first).revisionFingerprint().equals(new WeaverRevisionScope(2, scope.fields()).apply(first).revisionFingerprint()), "scope schema omitted");
        check(WeaverRevisionScope.full().apply(first).equals(first), "legacy full fingerprint silently reinterpreted");
        rejects(() -> new WeaverRevisionScope(1, Set.of("fixture.absent")).apply(first));
        final var changedType = new SubjectSnapshot(ref, 1, first.revisionFingerprint(), Map.of("fixture.rank", new WeaverValue(new WeaverTypeId("weaver", "double", 1), Map.of("value", 7.0), "fixture", "fixture.state", Set.of(), 1)));
        check(!scope.apply(first).revisionFingerprint().equals(scope.apply(changedType).revisionFingerprint()), "typed value schema omitted");
        final UUID instance = UUID.randomUUID();
        final var oldItem = new ItemSlotRef(ref.playerId(), WeaverSlot.named(WeaverSlot.Kind.OFF_HAND), Optional.of("fixture.item"), Optional.of(instance), OptionalLong.of(1), "a".repeat(64));
        final var newItem = new ItemSlotRef(ref.playerId(), oldItem.slot(), oldItem.logicalId(), oldItem.instanceId(), OptionalLong.of(2), "b".repeat(64));
        check(!scope.apply(snapshot(oldItem, 7, 1)).revisionFingerprint().equals(scope.apply(snapshot(newItem, 7, 1)).revisionFingerprint()), "ITEM_SLOT fences were erased by field scope");
        durableUndo(scope, ref);
        System.out.println("Weaver revision scopes passed: unrelated movement, relevant drift, stable identity/item fences, schema/type changes, legacy compatibility and durable Undo.");
    }
    private static void durableUndo(final WeaverRevisionScope scope, final PlayerRef ref) throws Exception {
        final var action = WeaverContractRegressionSuite.action("fixture", RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT), true, 1).withRevisionScope(scope);
        final var inverse = new ActionDescriptor("fixture.undo", action.facetId(), action.label(), action.risk(), action.lifetimes(), action.integrityModes(), action.integrityImpacts(), action.subjects(), List.of(), AreaSupport.NONE, Optional.empty(), false, Optional.empty(), 1, scope);
        final var provider = new WeaverContractRegressionSuite.FixtureProvider("fixture", action, CoverageLevel.FULL_PROVIDER, Map.of(action.id(), "fixture.assess")) {
            @Override public ProviderContribution contribution() { return new ProviderContribution(contribution.facets(), List.of(action, inverse), List.of(), List.of(), List.of(), Map.of(action.id(), "fixture.assess", inverse.id(), "fixture.assess")); }
            @Override public PreparedAction prepareUndo(final ProviderContext context, final SubjectSnapshot snapshot, final WeaverReceipt receipt) {
                final String restored = scope.apply(snapshot(ref, 7, 99)).revisionFingerprint();
                final var stage = new ExecutionStage("fixture.inverse", SubjectRoute.owner(ref), Map.of(), (execution, payload) -> CompletableFuture.completedFuture(new StageResult(restored, Map.of(), Map.of())), Optional.empty(), 5000);
                return new PreparedAction(UUID.randomUUID(), inverse, ref, snapshot.revisionFingerprint(), List.of(stage), new OperationRecoveryPayload(1, Map.of()),
                        (plan, results, now) -> new WeaverReceipt(UUID.randomUUID(), plan.operationId(), "fixture", inverse.id(), ref, RiskLevel.MUTATING, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX,
                                plan.expectedBeforeFingerprint(), results.getLast().afterFingerprint(), Map.of(), Map.of(), Optional.empty(), now, ReceiptStatus.COMMITTED));
            }
        };
        final var providers = WeaverContractRegressionSuite.registry(provider); providers.freezeAndValidate();
        final var journal = new WeaverJournal(new Storage()); await(journal.load()); final var authority = WeaverAreaExecutionRegressionSuite.authority();
        final var before = scope.apply(snapshot(ref, 7, 1)); final var after = scope.apply(snapshot(ref, 8, 2));
        final var operation = new WeaverOperationRecord(UUID.randomUUID(), authority.actor(), "fixture", new ActionRequest(action.id(), Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), ref,
                before.revisionFingerprint(), Optional.empty(), new OperationRecoveryPayload(1, Map.of()), OperationStatus.PREPARED, 0, 1, 1, Optional.empty(), false);
        final var receipt = new WeaverReceipt(UUID.randomUUID(), operation.operationId(), "fixture", action.id(), ref, RiskLevel.MUTATING, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX, before.revisionFingerprint(), after.revisionFingerprint(),
                Map.of("fixture.rank", value(7, 1)), Map.of("fixture.rank", value(8, 2)), Optional.of(new UndoSpec(inverse.id(), after.revisionFingerprint(), Map.of())), 2, ReceiptStatus.COMMITTED);
        await(journal.prepare(operation)); final var applied = await(journal.applied(operation.operationId(), 0, receipt, WeaverEffectCommit.none(), 2)); await(journal.finishAudit(applied.operationId(), applied.revision()));
        final var undo = new WeaverUndoCoordinator(journal, providers); final var types = WeaverProjectionRegressionSuite.types(); final var context = new ProviderContext(authority, types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
        rejects(() -> undo.target(authority, receipt.receiptId(), scope.apply(snapshot(ref, 9, 99))));
        final var moved = inverse.revisionScope().apply(snapshot(ref, 8, 99)); final var target = undo.target(authority, receipt.receiptId(), moved); final var prepared = undo.prepare(context, target.claim(), moved);
        final var execution = new WeaverDurableExecutionCoordinator(new WeaverUndoRegressionSuite.Router(), journal, types);
        await(execution.execute("fixture", context, moved, new ActionRequest(inverse.id(), Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), prepared,
                new PreparedEffects(hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent.none(), (plan, results, inverted, sequence) -> WeaverEffectCommit.none()), Optional.of(target.claim()), () -> authority));
        check(journal.snapshot().receipts().get(receipt.receiptId()).status() == ReceiptStatus.UNDONE, "unrelated movement prevented exact field Undo");
        execution.close(); await(journal.close());
    }
}
