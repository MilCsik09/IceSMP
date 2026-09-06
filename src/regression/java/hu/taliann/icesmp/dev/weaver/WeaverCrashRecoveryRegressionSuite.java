package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;

public final class WeaverCrashRecoveryRegressionSuite {
    static final class Provider extends WeaverContractRegressionSuite.FixtureProvider {
        RecoveryAssessment assessment;
        int reads;
        Provider() { super("fixture", WeaverContractRegressionSuite.safe("fixture"), CoverageLevel.FULL_PROVIDER, Map.of()); }
        @Override public RecoveryAssessment assessRecovery(final RecoveryContext context, final SubjectSnapshot snapshot, final WeaverOperationRecord operation) {
            context.authority().require(operation); reads++; return assessment;
        }
        @Override public PreparedAction prepare(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request) { throw new AssertionError("Recovery replayed mutation"); }
    }
    public static void main(final String[] args) throws Exception {
        for (final boolean applied : List.of(false, true)) {
            for (final ObservedOperationState observed : ObservedOperationState.values()) {
                for (final boolean exact : List.of(false, true)) {
                    if (exact && observed != ObservedOperationState.BEFORE) continue;
                    final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage); await(journal.load());
                    final WeaverOperationRecord operation = prepared(); await(journal.prepare(operation)); final WeaverReceipt receipt = receipt(operation);
                    if (applied) await(journal.applied(operation.operationId(), 0, receipt, 2));
                    final Provider provider = new Provider();
                    provider.assessment = new RecoveryAssessment(observed, exact, observed == ObservedOperationState.APPLIED ? Optional.of(receipt) : Optional.empty(), "fixture");
                    final WorldWeaverProviderRegistry providers = WeaverContractRegressionSuite.registry(provider); providers.freezeAndValidate();
                    provider.broken = true; for (int i = 0; i < 3; i++) providers.discover(new SubjectSnapshot(new PlayerRef(UUID.randomUUID()), 1, "before", Map.of()));
                    check(providers.quarantined("fixture"), "quarantine fixture missing");
                    final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
                    final WeaverRecoveryCoordinator recovery = new WeaverRecoveryCoordinator(journal, (actor, ref) -> CompletableFuture.completedFuture(new SubjectSnapshot(ref, 3, observed.name(), Map.of())), providers, types);
                    await(recovery.start());
                    final OperationStatus expected = observed == ObservedOperationState.APPLIED ? OperationStatus.COMMITTED
                            : observed == ObservedOperationState.BEFORE && !applied ? OperationStatus.ABORTED
                            : observed == ObservedOperationState.BEFORE && exact ? OperationStatus.COMPENSATED : OperationStatus.NEEDS_REVIEW;
                    final WeaverOperationRecord settled = journal.snapshot().operations().get(operation.operationId());
                    check(settled.status() == expected && !settled.pendingAudit() && provider.reads == 1, "incorrect observed-state reconciliation: " + observed + "/" + applied + "/" + exact);
                    await(recovery.start()); check(provider.reads == 1 && storage.audit.size() == 1, "settled operation was reassessed/repeated");
                    recovery.close(); await(journal.close());
                }
            }
        }
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage); await(journal.load());
        final WeaverOperationRecord operation = prepared(); await(journal.prepare(operation));
        final Provider provider = new Provider(); provider.assessment = new RecoveryAssessment(ObservedOperationState.APPLIED, false, Optional.of(receipt(operation)), "fixture");
        final WorldWeaverProviderRegistry providers = WeaverContractRegressionSuite.registry(provider); providers.freezeAndValidate();
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        final AtomicBoolean loaded = new AtomicBoolean();
        final WeaverRecoveryCoordinator recovery = new WeaverRecoveryCoordinator(journal, (actor, ref) -> loaded.get() ? CompletableFuture.completedFuture(new SubjectSnapshot(ref, 3, "after", Map.of()))
                : CompletableFuture.failedFuture(new WeaverDomainRejection("ENTITY_UNAVAILABLE")), providers, types);
        await(recovery.start());
        check(journal.snapshot().operations().get(operation.operationId()).status() == OperationStatus.PREPARED && !recovery.pending().isEmpty() && provider.reads == 0, "unloaded entity was aborted or force-resolved");
        loaded.set(true); await(recovery.entityAvailable(((EntityRef) operation.subject()).entityId()));
        check(journal.snapshot().operations().get(operation.operationId()).status() == OperationStatus.COMMITTED && recovery.pending().isEmpty() && provider.reads == 1, "entity load did not resume pending assessment");
        recovery.close(); await(journal.close());
        final Storage racedStorage = new Storage(); final WeaverJournal racedJournal = new WeaverJournal(racedStorage); await(racedJournal.load());
        final WeaverOperationRecord raced = prepared(); await(racedJournal.prepare(raced));
        final Provider racedProvider = new Provider(); racedProvider.assessment = new RecoveryAssessment(ObservedOperationState.APPLIED, false, Optional.of(receipt(raced)), "fixture");
        final WorldWeaverProviderRegistry racedProviders = WeaverContractRegressionSuite.registry(racedProvider); racedProviders.freezeAndValidate();
        final java.util.concurrent.atomic.AtomicReference<WeaverRecoveryCoordinator> reference = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicInteger captures = new java.util.concurrent.atomic.AtomicInteger();
        final WeaverRecoveryCoordinator racing = new WeaverRecoveryCoordinator(racedJournal, (actor, ref) -> {
            if (captures.incrementAndGet() == 1) {
                reference.get().entityAvailable(((EntityRef) ref).entityId());
                return CompletableFuture.failedFuture(new WeaverDomainRejection("ENTITY_UNAVAILABLE"));
            }
            return CompletableFuture.completedFuture(new SubjectSnapshot(ref, 3, "after", Map.of()));
        }, racedProviders, types); reference.set(racing);
        await(racing.start());
        check(captures.get() == 2 && racedJournal.snapshot().operations().get(raced.operationId()).status() == OperationStatus.COMMITTED,
                "entity load raced unavailable snapshot and left a permanent pending operation");
        racing.close(); await(racedJournal.close());
        System.out.println("Weaver crash recovery passed: observed-state matrix, quarantined-provider recovery, no replay, pending entity load and idempotent restart.");
    }
}
