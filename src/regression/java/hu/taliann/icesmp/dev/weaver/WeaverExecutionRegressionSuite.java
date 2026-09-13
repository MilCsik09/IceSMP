package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class WeaverExecutionRegressionSuite {
    private static final class Router implements WeaverOwnerRouter {
        final List<ExecutionOwner> owners = new ArrayList<>();
        boolean retired;
        @Override public <T> CompletionStage<T> submit(final ExecutionOwner owner, final UUID actor, final Duration timeout, final Supplier<CompletionStage<T>> task) {
            owners.add(owner);
            if (retired) return CompletableFuture.failedFuture(new WeaverDomainRejection("OWNER_UNAVAILABLE"));
            try { return task.get(); } catch (final RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
        }
        @Override public void close() { retired = true; }
    }
    public static void main(final String[] args) {
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types); types.freeze();
        final UUID actor = HiddenDevAuthority.PRIMARY_DEVELOPER, session = UUID.randomUUID();
        final WeaverAuthorityToken authority = new WeaverAuthorityToken(actor, session, 100, () -> true, () -> 0L);
        final ProviderContext context = new ProviderContext(authority, types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
        final SubjectSnapshot snapshot = new SubjectSnapshot(new PlayerRef(actor), 1, "before", Map.of());
        final ActionDescriptor action = WeaverContractRegressionSuite.safe("fixture");
        final Router router = new Router(); final WeaverExecutionCoordinator coordinator = new WeaverExecutionCoordinator(router, types);
        final AtomicInteger effects = new AtomicInteger(), guards = new AtomicInteger();
        final ExecutionStage first = new ExecutionStage("fixture.first", new RegionOwner(UUID.randomUUID(), 3, -4), Map.of(), (execution, payload) -> {
            effects.incrementAndGet(); return CompletableFuture.completedFuture(new StageResult("middle", Map.of(), Map.of()));
        }, Optional.empty(), 5000);
        final ExecutionStage second = new ExecutionStage("fixture.second", new EntityOwner(UUID.randomUUID()), Map.of(), (execution, payload) -> {
            check(execution.previousResults().size() == 1, "immutable stage continuation missing");
            effects.incrementAndGet(); return CompletableFuture.completedFuture(new StageResult("after", Map.of(), Map.of()));
        }, Optional.empty(), 5000);
        final PreparedAction plan = plan(action, snapshot, List.of(first, second));
        final WeaverReceipt receipt = success(coordinator.execute("fixture", context, snapshot, plan, () -> { guards.incrementAndGet(); return authority; }));
        check(effects.get() == 2 && guards.get() == 2 && receipt.afterFingerprint().equals("after"), "execution did not complete exactly once");
        check(router.owners.size() == 4 && router.owners.get(0) instanceof ActorOwner && router.owners.get(1) instanceof RegionOwner
                && router.owners.get(2) instanceof ActorOwner && router.owners.get(3) instanceof EntityOwner, "actor revalidation missing before remote stage");
        final int before = effects.get();
        failure(coordinator.execute("fixture", context, new SubjectSnapshot(snapshot.ref(), 2, "drift", Map.of()), plan, () -> authority), "INVALID_EXECUTION_PLAN");
        check(effects.get() == before, "stale fingerprint produced effects");
        final ActionDescriptor mutating = WeaverContractRegressionSuite.action("fixture", RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT), true, 1);
        failure(coordinator.execute("fixture", context, snapshot, plan(mutating, snapshot, List.of(first)), () -> authority), "DURABLE_EXECUTION_UNAVAILABLE");
        check(effects.get() == before, "mutation passed absent journal/influence gate");
        final Router waitingRouter = new Router(); final WeaverExecutionCoordinator waiting = new WeaverExecutionCoordinator(waitingRouter, types);
        final CompletableFuture<StageResult> pending = new CompletableFuture<>();
        final var paused = plan(action, snapshot, List.of(new ExecutionStage("fixture.paused", new ActorOwner(), Map.of(), (execution, payload) -> pending, Optional.empty(), 5000)));
        final CompletionStage<WeaverReceipt> firstAttempt = waiting.execute("fixture", context, snapshot, paused, () -> authority);
        failure(waiting.execute("fixture", context, snapshot, paused, () -> authority), "EXECUTION_BUSY");
        pending.complete(new StageResult("after", Map.of(), Map.of())); success(firstAttempt);
        final Router retired = new Router(); retired.close();
        failure(new WeaverExecutionCoordinator(retired, types).execute("fixture", context, snapshot, plan, () -> authority), "OWNER_UNAVAILABLE");
        coordinator.close(); failure(coordinator.execute("fixture", context, snapshot, plan, () -> authority), "DURABLE_EXECUTION_UNAVAILABLE");
        final ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(() -> 0L);
        for (int i = 0; i < 3; i++) failure(breaker.observe(CompletableFuture.failedFuture(new IllegalStateException("private provider detail"))), "PROVIDER_ERROR");
        check(breaker.quarantined(), "async provider errors escaped circuit breaker");
        System.out.println("Weaver execution regression passed: continuations, revalidation, stale/retired rejection and closed journal gate.");
    }
    private static PreparedAction plan(final ActionDescriptor action, final SubjectSnapshot snapshot, final List<ExecutionStage> stages) {
        return new PreparedAction(UUID.randomUUID(), action, snapshot.ref(), snapshot.revisionFingerprint(), stages, new OperationRecoveryPayload(1, Map.of()),
                (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), prepared.operationId(), "fixture", action.id(), snapshot.ref(), action.risk(),
                        Lifetime.ONE_SHOT, IntegrityMode.SANDBOX, snapshot.revisionFingerprint(), results.getLast().afterFingerprint(), Map.of(), Map.of(), Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
    private static <T> T success(final CompletionStage<T> result) {
        final java.util.concurrent.atomic.AtomicReference<T> value = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        result.whenComplete((v, f) -> { value.set(v); failure.set(f); });
        if (failure.get() != null || value.get() == null) throw new AssertionError("Expected completed success", failure.get()); return value.get();
    }
    private static void failure(final CompletionStage<?> result, final String code) {
        final java.util.concurrent.atomic.AtomicReference<Throwable> captured = new java.util.concurrent.atomic.AtomicReference<>();
        result.whenComplete((value, failure) -> captured.set(failure)); Throwable failure = captured.get();
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) failure = failure.getCause();
        check(failure instanceof WeaverDomainRejection rejected && rejected.code().equals(code), "Wrong execution error: " + code);
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
