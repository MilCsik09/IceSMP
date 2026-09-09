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
        snapshotRecoveryIsolation();
        profileMaintenanceAdmission();
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
    private static void snapshotRecoveryIsolation() throws Exception {
        final var target = new WeaverContractRegressionSuite.SnapshotProvider("fixture");
        final var other = new WeaverContractRegressionSuite.SnapshotProvider("other");
        final var providers = WeaverContractRegressionSuite.registry(target, other); providers.freezeAndValidate();
        final var types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        final var journal = new WeaverJournal(new Storage()); await(journal.load());
        final var original = prepared(); final var subject = new PlayerRef(UUID.randomUUID());
        final var operation = new WeaverOperationRecord(original.operationId(), original.actorId(), original.providerId(), original.request(), subject,
                original.beforeFingerprint(), original.afterFingerprint(), original.recoveryPayload(), original.status(), original.revision(),
                System.currentTimeMillis(), System.currentTimeMillis(), original.receipt(), false);
        await(journal.prepare(operation)); target.broken = true; other.broken = true;
        for (int i = 0; i < 3; i++) providers.captureContributions(subject);
        check(providers.quarantined(target.id) && providers.quarantined(other.id), "snapshot quarantine fixture missing");
        target.broken = false; other.broken = false;
        target.recoveryValue = "native-operation-observation";
        final int otherCaptures = other.captures;
        final java.util.concurrent.atomic.AtomicReference<RecoveryContext> observed = new java.util.concurrent.atomic.AtomicReference<>();
        final SubjectSnapshotSource source = new SubjectSnapshotSource() {
            public CompletionStage<SubjectSnapshot> capture(UUID actor, SubjectRef ref) { throw new AssertionError("recovery did not request scoped snapshot authority"); }
            public CompletionStage<SubjectSnapshot> captureRecovery(RecoveryContext context) {
                observed.set(context);
                final var facts = providers.captureRecoveryContributions(context);
                check(facts.get("fixture.fact").payload().get("value").equals("native-operation-observation"),
                        "operation recovery was routed through ordinary stale-item inspection");
                return CompletableFuture.completedFuture(new SubjectSnapshot(context.operation().subject(), 1, "before", facts));
            }
        };
        final var recovery = new WeaverRecoveryCoordinator(journal, source, providers, types); await(recovery.start());
        check(journal.snapshot().operations().get(operation.operationId()).status() == OperationStatus.ABORTED && other.captures == otherCaptures,
                "scoped recovery failed to observe its provider or bypassed another provider's quarantine");
        check(providers.quarantined(target.id), "read-only recovery re-enabled interactive provider actions");
        check(target.recoveryCaptures == 1 && other.recoveryCaptures == 0, "recovery capability escaped its own provider");
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> providers.captureRecoveryContributions(observed.get()));
        check(target.recoveryCaptures == 1, "stale operation token entered a native recovery callback");
        recovery.close(); await(journal.close());
    }
    private static void profileMaintenanceAdmission() throws Exception {
        final var journal = new WeaverJournal(new Storage()); await(journal.load());
        for (int i = 0; i < 8; i++) {
            final var original = prepared(); final long now = System.currentTimeMillis();
            await(journal.prepare(new WeaverOperationRecord(original.operationId(), original.actorId(), original.providerId(), original.request(), original.subject(),
                    original.beforeFingerprint(), original.afterFingerprint(), original.recoveryPayload(), original.status(), original.revision(), now, now, original.receipt(), false)));
        }
        final Provider provider = new Provider(); provider.assessment = new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "PROFILE_BEFORE");
        final var providers = WeaverContractRegressionSuite.registry(provider); providers.freezeAndValidate();
        final var types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        final AtomicBoolean ready = new AtomicBoolean(); final var barrier = new CompletableFuture<Void>();
        final var captures = new java.util.concurrent.atomic.AtomicInteger();
        final var recovery = new WeaverRecoveryCoordinator(journal, (actor, ref) -> {
            if (!ready.get()) return CompletableFuture.failedFuture(new WeaverDomainRejection("PROFILE_UNAVAILABLE"));
            captures.incrementAndGet(); return barrier.thenApply(ignored -> new SubjectSnapshot(ref, 1, "before", Map.of()));
        }, providers, types);
        await(recovery.start()); check(recovery.pending().size() == 8, "pending profile batch was lost"); ready.set(true);
        final var pulse = recovery.profilesAvailable(); await(recovery.profilesAvailable());
        check(captures.get() == 1, "overlapping maintenance duplicated work or started unbounded parallel profile reads"); barrier.complete(null); await(pulse);
        check(captures.get() == 8 && provider.reads == 8 && recovery.pending().isEmpty(), "bounded maintenance failed to visit every pending profile");
        await(recovery.profilesAvailable()); recovery.close(); await(recovery.profilesAvailable());
        check(captures.get() == 8, "settled or closed maintenance repeated profile reads"); await(journal.close());
    }
}
