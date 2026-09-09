package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import net.kyori.adventure.text.Component;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.await;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.fails;

/** Actual YAML/fsync lineage through executor-issued authority; no connected owner or native item claims. */
public final class WeaverNativeEffectRegressionSuite {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        success(false); success(true);
        for (String refusal : List.of("outside", "temporary", "lasting", "missing-fresh", "other-pending", "revoked",
                "expired", "policy-closed", "fenced", "new-origin")) refusal(refusal);
        System.out.println("Weaver native effect authority passed. assertions=" + assertions);
    }
    private static final class Router implements WeaverOwnerRouter {
        public <T> CompletionStage<T> submit(ExecutionOwner owner, UUID actor, Duration timeout, Supplier<CompletionStage<T>> task) {
            try { return task.get(); } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
        }
        public void close() { }
    }
    private static final class Fixture implements AutoCloseable {
        final Path root = Files.createTempDirectory("weaver-native-admission-");
        final WeaverTypeRegistry types = WeaverProjectionRegressionSuite.types();
        final YamlWeaverJournalStorage storage = new YamlWeaverJournalStorage(root.toFile(), new WeaverJournalCodec(types), Logger.getAnonymousLogger());
        final WeaverJournal journal = new WeaverJournal(storage);
        final Router router = new Router(); final AtomicBoolean online = new AtomicBoolean(true);
        final WeaverAuthorityToken actor = new WeaverAuthorityToken(HiddenDevAuthority.PRIMARY_DEVELOPER, UUID.randomUUID(), Long.MAX_VALUE, online::get, () -> 0L);
        final ProviderContext context = new ProviderContext(actor, types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
        final SubjectSnapshot snapshot = new SubjectSnapshot(new PlayerRef(actor.actor()), 1, "before", Map.of());
        final RewardSource.Entity source = new RewardSource.Entity(UUID.randomUUID());
        final RewardSource.Item target = new RewardSource.Item(UUID.randomUUID());
        final List<RewardSource> sources = List.of(new RewardSource.Player(actor.actor()), source);
        final GameplayEffectContext effect = new GameplayEffectContext(sources, Set.of(target), 0);
        final AtomicInteger mutations = new AtomicInteger();
        final AtomicReference<WeaverNativeEffectAuthority> issued = new AtomicReference<>();
        final WeaverDurableExecutionCoordinator executor = new WeaverDurableExecutionCoordinator(router, journal, types);
        final GameplayEffectGate.Binding binding;
        final ActionDescriptor descriptor = new ActionDescriptor("fixture.native", "fixture.state", Component.text("Native fixture"),
                RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT),
                Set.of(WeaverSubjectKind.PLAYER), List.of(), AreaSupport.NONE, Optional.empty(), false, Optional.of("Native fixture effect"), 1);
        Fixture() throws Exception { await(journal.load()); binding = GameplayEffectGate.install(journal::prepareDerivedEffect); }
        WeaverOperationRecord origin(SubjectRef ref) {
            final long now = System.currentTimeMillis();
            return new WeaverOperationRecord(UUID.randomUUID(), actor.actor(), "fixture", new ActionRequest("fixture.origin", Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX),
                    ref, "before", Optional.empty(), new OperationRecoveryPayload(1, Map.of()), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false);
        }
        CompletionStage<WeaverOperationRecord> applyOrigin(SubjectRef ref) {
            final var operation = origin(ref);
            return journal.prepare(operation).thenCompose(ignored -> journal.applied(operation.operationId(), 0,
                    new WeaverReceipt(UUID.randomUUID(), operation.operationId(), "fixture", "fixture.origin", ref, RiskLevel.MUTATING,
                            Lifetime.ONE_SHOT, IntegrityMode.SANDBOX, "before", "after", Map.of(), Map.of(), Optional.empty(), System.currentTimeMillis(), ReceiptStatus.COMMITTED),
                    WeaverEffectCommit.none(), System.currentTimeMillis()));
        }
        PreparedAction plan(StageOperation nativeStage) {
            return new PreparedAction(UUID.randomUUID(), descriptor, snapshot.ref(), "before",
                    List.of(new ExecutionStage("fixture.native", new ActorOwner(), Map.of(), nativeStage, Optional.empty(), 5000)),
                    new OperationRecoveryPayload(1, Map.of()), (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(),
                            prepared.operationId(), "fixture", descriptor.id(), snapshot.ref(), descriptor.risk(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX,
                            "before", results.getLast().afterFingerprint(), Map.of(), Map.of(), Optional.empty(), time, ReceiptStatus.COMMITTED));
        }
        CompletionStage<WeaverReceipt> run(PreparedAction plan) {
            return executor.execute("fixture", context, snapshot, new ActionRequest(descriptor.id(), Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), plan,
                    new PreparedEffects(new WeaverEffectIntent(Set.of(WeaverInfluenceTarget.exact(target))),
                            (action, results, receipt, sequence) -> WeaverEffectCommit.none()), () -> actor);
        }
        CompletionStage<StageResult> apply(GameplayEffectPermit permit, List<RewardSource> fresh) {
            return router.submit(new ActorOwner(), actor.actor(), Duration.ofSeconds(5), () -> {
                if (!permit.claim(fresh)) return CompletableFuture.failedFuture(new WeaverDomainRejection("NATIVE_REFUSED"));
                mutations.incrementAndGet(); check(!permit.claim(fresh), "native permit replayed");
                return CompletableFuture.completedFuture(new StageResult("after", Map.of(), Map.of()));
            });
        }
        public void close() throws Exception {
            binding.close(); executor.close(); await(journal.close());
            try (var paths = Files.walk(root)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
    private static void success(boolean inherited) throws Exception {
        try (var f = new Fixture()) {
            if (inherited) await(f.applyOrigin(new EntityRef(f.source.id())));
            final var origins = f.journal.influenceIndex().trace(f.sources, System.currentTimeMillis()).origins();
            final var plan = f.plan((execution, payload) -> {
                final var authority = execution.nativeEffects().orElseThrow(); f.issued.set(authority);
                check(f.journal.snapshot().operations().get(authority.pendingOperation()).status() == OperationStatus.PREPARED,
                        "native authority entered before PREPARED acknowledgement");
                check(f.journal.influenceIndex().trace(f.sources, System.currentTimeMillis()).uncertain(), "ordinary sources lost pending quarantine");
                return GameplayEffectGate.prepare(f.effect).thenCompose(ordinary -> {
                    check(!ordinary.claim(f.sources), "ordinary derived gate excluded a pending operation");
                    return authority.prepare(f.effect);
                }).thenCompose(permit -> {
                    final WeaverJournalState durable;
                    try { durable = f.storage.readState(); } catch (Exception failure) { throw new AssertionError(failure); }
                    check(durable.equals(f.journal.snapshot()), "native permit preceded actual disk acknowledgement");
                    check(durable.intents().containsKey(authority.pendingOperation()) && durable.influences().values().stream()
                            .noneMatch(value -> value.influence().operationId().equals(authority.pendingOperation())), "native admission fabricated an applied own origin");
                    check(f.journal.influenceIndex().traceNativeEffect(List.of(f.target), System.currentTimeMillis(), authority).origins().equals(origins),
                            "native inheritance lost an original applied operation");
                    return f.apply(permit, f.sources);
                });
            });
            final var receipt = await(f.run(plan));
            check(f.mutations.get() == 1 && f.journal.snapshot().operations().get(plan.operationId()).status() == OperationStatus.COMMITTED,
                    "acknowledged native mutation did not commit exactly once");
            check(!await(f.issued.get().prepare(f.effect)).claim(f.sources), "finished stage retained native authority");
            final var restored = new WeaverJournal(f.storage); await(restored.load());
            check(restored.snapshot().receipts().containsKey(receipt.receiptId()), "restart lost committed native operation");
            check(restored.influenceIndex().trace(List.of(f.target), System.currentTimeMillis()).origins().containsAll(origins),
                    "restart replaced inherited provenance with only the newest action");
            await(restored.close());
        }
    }
    private static void refusal(String kind) throws Exception {
        try (var f = new Fixture()) {
            if (kind.equals("other-pending")) await(f.journal.prepare(f.origin(new EntityRef(f.source.id()))));
            final var plan = f.plan((execution, payload) -> {
                final var authority = execution.nativeEffects().orElseThrow(); f.issued.set(authority);
                final var context = switch (kind) {
                    case "outside" -> new GameplayEffectContext(f.sources, Set.of(new RewardSource.Item(UUID.randomUUID())), 0);
                    case "temporary" -> new GameplayEffectContext(f.sources, Set.of(new RewardSource.Player(f.actor.actor())), 0);
                    case "lasting" -> new GameplayEffectContext(f.sources, Set.of(f.target), 1);
                    default -> f.effect;
                };
                return authority.prepare(context).thenCompose(permit -> {
                    if (kind.equals("revoked")) f.online.set(false);
                    if (kind.equals("policy-closed")) f.binding.close();
                    if (kind.equals("expired")) return CompletableFuture.runAsync(() -> {},
                            CompletableFuture.delayedExecutor(5100, TimeUnit.MILLISECONDS)).thenCompose(ignored -> f.apply(permit, f.sources));
                    if (kind.equals("new-origin")) return f.applyOrigin(new EntityRef(f.source.id())).thenCompose(ignored -> f.apply(permit, f.sources));
                    if (kind.equals("fenced")) {
                        final var fence = GameplayEffectGate.fence(f.target).orElseThrow();
                        return f.apply(permit, f.sources).whenComplete((value, failure) -> fence.close());
                    }
                    return f.apply(permit, kind.equals("missing-fresh") ? List.of() : f.sources);
                });
            });
            fails(f.run(plan));
            check(f.mutations.get() == 0, "refused native admission changed state: " + kind);
            check(f.journal.snapshot().operations().get(plan.operationId()).status() == OperationStatus.NEEDS_REVIEW
                    && f.journal.snapshot().intents().containsKey(plan.operationId()), "failed native admission released uncertain intent");
            check(!await(f.issued.get().prepare(f.effect)).claim(f.sources), "failed stage retained native authority");
        }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
