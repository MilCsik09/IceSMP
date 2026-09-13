package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.Duration;
import java.util.function.Supplier;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;

public final class WeaverDurableExecutionRegressionSuite {
    private static final class Router implements WeaverOwnerRouter {
        final List<ExecutionOwner> owners = new ArrayList<>(); String failure; boolean timeoutAfterStart;
        @Override public <T> CompletionStage<T> submit(final ExecutionOwner owner, final UUID actor, final Duration timeout, final Supplier<CompletionStage<T>> task) {
            owners.add(owner);
            if (failure != null && !(owner instanceof ActorOwner)) return CompletableFuture.failedFuture(new WeaverDomainRejection(failure));
            if (timeoutAfterStart && !(owner instanceof ActorOwner)) {
                final OwnerTaskAdmission<T> admission = new OwnerTaskAdmission<>(); admission.run(task); admission.timeout(); return admission.result();
            }
            try { return task.get(); } catch (final RuntimeException error) { return CompletableFuture.failedFuture(error); }
        }
        @Override public void close() { }
    }
    private static final class Fixture implements AutoCloseable {
        final WeaverTypeRegistry types = WeaverProjectionRegressionSuite.types(); final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage);
        final Router router = new Router(); final AtomicBoolean online = new AtomicBoolean(true); final UUID session = UUID.randomUUID();
        final WeaverAuthorityToken authority = new WeaverAuthorityToken(HiddenDevAuthority.PRIMARY_DEVELOPER, session, Long.MAX_VALUE, online::get, () -> 0L);
        final ProviderContext context = new ProviderContext(authority, types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
        final SubjectSnapshot snapshot = new SubjectSnapshot(new PlayerRef(HiddenDevAuthority.PRIMARY_DEVELOPER), 1, "before", Map.of());
        final ActionDescriptor descriptor = WeaverContractRegressionSuite.action("fixture", RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT), true, 1);
        final WeaverDurableExecutionCoordinator execution = new WeaverDurableExecutionCoordinator(router, journal, types);
        final PreparedEffects effects = new PreparedEffects(WeaverEffectIntent.none(), (action, results, receipt, sequence) -> WeaverEffectCommit.none());
        Fixture() throws Exception { await(journal.load()); }
        PreparedAction plan(final List<ExecutionStage> stages) {
            return new PreparedAction(UUID.randomUUID(), descriptor, snapshot.ref(), "before", stages, new OperationRecoveryPayload(1, Map.of()), (prepared, results, time) ->
                    new WeaverReceipt(UUID.randomUUID(), prepared.operationId(), "fixture", descriptor.id(), snapshot.ref(), descriptor.risk(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX,
                            "before", results.getLast().afterFingerprint(), Map.of(), Map.of(), Optional.of(new UndoSpec("fixture.undo", results.getLast().afterFingerprint(), Map.of())), time, ReceiptStatus.COMMITTED));
        }
        CompletionStage<WeaverReceipt> run(final PreparedAction plan) {
            return execution.execute("fixture", context, snapshot, new ActionRequest(descriptor.id(), Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), plan, effects, () -> authority);
        }
        @Override public void close() throws Exception { execution.close(); if (journal.ready()) await(journal.close()); else fails(journal.close()); }
    }
    private static ExecutionStage stage(final String id, final StageOperation apply, final Optional<StageCompensation> compensation) {
        return new ExecutionStage("fixture." + id, new EntityOwner(UUID.randomUUID()), Map.of(), apply, compensation, 5000);
    }
    public static void main(final String[] args) throws Exception {
        WeaverNativeEffectRegressionSuite.main(args);
        try (final Fixture f = new Fixture()) {
            final AtomicInteger effects = new AtomicInteger();
            final var plan = f.plan(List.of(stage("apply", (context, payload) -> {
                check(f.journal.snapshot().operations().values().iterator().next().status() == OperationStatus.PREPARED, "stage entered before durable PREPARED");
                check(!f.journal.snapshot().intents().isEmpty(), "stage entered before reward quarantine"); effects.incrementAndGet();
                return CompletableFuture.completedFuture(new StageResult("after", Map.of(), Map.of()));
            }, Optional.empty())));
            final WeaverReceipt receipt = await(f.run(plan));
            check(effects.get() == 1 && f.journal.snapshot().operations().get(plan.operationId()).status() == OperationStatus.COMMITTED
                    && f.journal.snapshot().receipts().get(receipt.receiptId()).equals(receipt) && !f.journal.snapshot().influences().isEmpty(), "durable success did not commit effects once");
            fails(f.run(plan)); check(effects.get() == 1, "duplicate operation replayed a stage");
        }
        try (final Fixture f = new Fixture()) {
            f.storage.failWrite = 1; final AtomicInteger effects = new AtomicInteger();
            final var plan = f.plan(List.of(stage("no_write", (context, payload) -> { effects.incrementAndGet(); return CompletableFuture.completedFuture(new StageResult("after", Map.of(), Map.of())); }, Optional.empty())));
            fails(f.run(plan)); check(effects.get() == 0, "PREPARED write failure still mutated");
        }
        try (final Fixture f = new Fixture()) {
            final var plan = f.plan(List.of(stage("duplicate_prepared", (context, payload) -> { throw new AssertionError("duplicate operation replayed"); }, Optional.empty())));
            final long now = System.currentTimeMillis();
            final var existing = new WeaverOperationRecord(plan.operationId(), f.authority.actor(), "fixture", new ActionRequest(f.descriptor.id(), Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX),
                    f.snapshot.ref(), "before", Optional.empty(), plan.recoveryPayload(), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false);
            await(f.journal.prepare(existing)); fails(f.run(plan));
            check(f.journal.snapshot().operations().get(plan.operationId()).equals(existing) && !f.journal.snapshot().intents().isEmpty(), "duplicate rejection aborted another prepared operation");
        }
        for (final boolean drift : List.of(false, true)) try (final Fixture f = new Fixture()) {
            final AtomicReference<String> state = new AtomicReference<>("before"); final List<String> compensation = new ArrayList<>();
            final var first = stage("first", (context, payload) -> { state.set("middle"); return CompletableFuture.completedFuture(new StageResult("middle", Map.of(), Map.of())); }, Optional.of((context, payload) -> {
                context.requireCurrentFingerprint(state.get()); compensation.add("first"); state.set("before"); return CompletableFuture.completedFuture(new StageResult("before", Map.of(), Map.of()));
            }));
            final var second = stage("second", (context, payload) -> { state.set("after"); return CompletableFuture.completedFuture(new StageResult("after", Map.of(), Map.of())); }, Optional.of((context, payload) -> {
                context.requireCurrentFingerprint(state.get()); compensation.add("second"); state.set("middle"); return CompletableFuture.completedFuture(new StageResult("middle", Map.of(), Map.of()));
            }));
            final var fail = stage("fail", (context, payload) -> { if (drift) state.set("external"); f.online.set(false); return CompletableFuture.failedFuture(new IllegalStateException("injected stage failure")); }, Optional.empty());
            final var plan = f.plan(List.of(first, second, fail)); fails(f.run(plan));
            check(drift ? state.get().equals("external") && compensation.isEmpty() : state.get().equals("before") && compensation.equals(List.of("second", "first")), "reverse compensation overwrote drift or needed an online player token");
            check(f.journal.snapshot().operations().get(plan.operationId()).status() == OperationStatus.NEEDS_REVIEW && !f.journal.snapshot().intents().isEmpty(), "partial failure was reported aborted/clean");
        }
        for (final String failure : List.of("OWNER_TIMEOUT_UNSTARTED", "OWNER_TIMEOUT_STARTED", "OWNER_SHUTDOWN_STARTED")) try (final Fixture f = new Fixture()) {
            f.router.failure = failure; final var plan = f.plan(List.of(stage("timeout", (context, payload) -> { throw new AssertionError("router should refuse"); }, Optional.empty())));
            fails(f.run(plan)); final OperationStatus expected = failure.endsWith("UNSTARTED") ? OperationStatus.ABORTED : OperationStatus.NEEDS_REVIEW;
            check(f.journal.snapshot().operations().get(plan.operationId()).status() == expected, "started/unstarted ambiguity collapsed");
        }
        try (final Fixture f = new Fixture()) {
            final AtomicInteger lateEffects = new AtomicInteger(), compensations = new AtomicInteger(); final CompletableFuture<Void> late = new CompletableFuture<>();
            f.router.timeoutAfterStart = true;
            final var plan = f.plan(List.of(stage("late", (context, payload) -> late.thenApply(ignored -> { lateEffects.incrementAndGet(); return new StageResult("late", Map.of(), Map.of()); }),
                    Optional.of((context, payload) -> { compensations.incrementAndGet(); return CompletableFuture.completedFuture(new StageResult("before", Map.of(), Map.of())); }))));
            fails(f.run(plan)); check(lateEffects.get() == 0 && compensations.get() == 0, "started timeout triggered racing compensation");
            late.complete(null);
            check(lateEffects.get() == 1 && f.journal.snapshot().operations().get(plan.operationId()).status() == OperationStatus.NEEDS_REVIEW
                    && !f.journal.snapshot().intents().isEmpty() && f.journal.snapshot().receipts().isEmpty(), "late completion committed or released quarantine");
        }
        try (final Fixture f = new Fixture()) {
            final AtomicBoolean gate = new AtomicBoolean(); final WeaverExecutionCoordinator coordinator = new WeaverExecutionCoordinator(f.router, f.types, f.journal, gate::get);
            final var plan = f.plan(List.of(stage("integration", (context, payload) -> CompletableFuture.completedFuture(new StageResult("after", Map.of(), Map.of())), Optional.empty())));
            final var request = new ActionRequest(f.descriptor.id(), Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            fails(coordinator.execute("fixture", f.context, f.snapshot, request, plan, Optional.of(f.effects), () -> f.authority));
            check(f.journal.snapshot().operations().isEmpty(), "closed gameplay integrity gate still prepared mutation");
            gate.set(true); await(coordinator.execute("fixture", f.context, f.snapshot, request, plan, Optional.of(f.effects), () -> f.authority));
            coordinator.close();
        }
        try (final Fixture f = new Fixture()) {
            final CompletableFuture<StageResult> pending = new CompletableFuture<>(); final CountDownLatch entered = new CountDownLatch(1);
            final var plan = f.plan(List.of(stage("pending", (context, payload) -> { entered.countDown(); return pending; }, Optional.empty())));
            final var first = f.run(plan); check(entered.await(10, TimeUnit.SECONDS), "stage did not enter");
            fails(f.run(f.plan(plan.stages()))); pending.complete(new StageResult("after", Map.of(), Map.of())); await(first);
        }
        System.out.println("Weaver durable execution passed: PREPARED barrier, exact commit, no write-failure effects, duplicate rejection, reverse compensation after logout, drift conflict and started-timeout quarantine.");
    }
}
