package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.pve.*;
import hu.taliann.icesmp.pve.MobRuntimeControlLedger.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import static hu.taliann.icesmp.dev.weaver.provider.PvEWeaverProvider.*;

/** Actual provider/kernel journal plus native operation ledger; live cast behavior has a separate server gate. */
public final class PvEWeaverRuntimeRegressionSuite {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static <T> T await(CompletionStage<T> future) throws Exception { return future.toCompletableFuture().get(10, TimeUnit.SECONDS); }
    private static void fails(CompletionStage<?> future) throws Exception { try { await(future); throw new AssertionError("Expected failure"); } catch (ExecutionException expected) { assertions++; } }
    private static final class Storage implements WeaverJournalStorage {
        final WeaverJournalCodec codec = new WeaverJournalCodec(); Map<String, Object> data; Map<String, WeaverAuditEntry> audit = Map.of();
        int writes, failAt; boolean after;
        public WeaverJournalState readState() { return data == null ? WeaverJournalState.empty() : codec.decodeState(data); }
        public Map<String, WeaverAuditEntry> readAudit() { return audit; }
        private void before() throws Exception { if (++writes == failAt && !after) throw new java.io.IOException("before write"); }
        private void after() throws Exception { if (writes == failAt && after) throw new java.io.IOException("after write"); }
        public void writeState(WeaverJournalState state) throws Exception { before(); data = codec.encodeState(state); after(); }
        public void writeAudit(Map<String, WeaverAuditEntry> entries) throws Exception { before(); audit = codec.decodeAudit(codec.encodeAudit(entries)); after(); }
    }
    private static final class Native {
        final MobRuntimeControlLedger ledger = new MobRuntimeControlLedger(); final AtomicLong epoch = new AtomicLong();
        final AtomicInteger effects = new AtomicInteger(); final AtomicBoolean allowed = new AtomicBoolean(true);
    }
    private static final class Fixture implements AutoCloseable {
        final EntityRef subject; final Native nativeState; final WeaverTypeRegistry types = new WeaverTypeRegistry();
        final WorldWeaverProviderRegistry providers = new WorldWeaverProviderRegistry(types, () -> 0L);
        final WeaverJournal journal; final PvEWeaverProvider provider; final ProviderContext context; final WeaverDurableExecutionCoordinator execution;
        final AtomicBoolean loaded = new AtomicBoolean(true), valid = new AtomicBoolean(true), revokeAtAdmission = new AtomicBoolean();
        final AtomicInteger calls = new AtomicInteger(); final ThreadLocal<Boolean> owner = ThreadLocal.withInitial(() -> false);
        final WeaverOwnerRouter router = new WeaverOwnerRouter() {
            public <T> CompletionStage<T> submit(ExecutionOwner route, UUID actor, Duration timeout, Supplier<CompletionStage<T>> task) {
                if (route instanceof EntityOwner && !loaded.get()) return CompletableFuture.failedFuture(new WeaverDomainRejection("ENTITY_UNAVAILABLE"));
                owner.set(route instanceof EntityOwner entity && entity.entityId().equals(subject.entityId()));
                try { return task.get(); } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); } finally { owner.remove(); }
            }
            public void close() { }
        };
        Fixture(Storage storage, EntityRef subject, Native nativeState) throws Exception {
            this.subject = subject; this.nativeState = nativeState; ScalarTypeCodec.registerBuiltins(types); journal = new WeaverJournal(storage);
            final var ability = new MobAbilityDefinition("fixture_ability", MobAbilityDefinition.Kind.LUNGE, 40, 10, 4, 2, 0, Map.of());
            provider = new PvEWeaverProvider(types, () -> Map.of(ability.abilityId(), ability), Map::of, this::facts, Optional.empty(),
                    subjects -> CompletableFuture.completedFuture(null), Optional.of((ref, request, admission) -> {
                        check(owner.get() && ref.equals(subject), "native control escaped entity owner");
                        check(journal.snapshot().operations().get(request.operationId()).status() == OperationStatus.PREPARED, "native control preceded durable PREPARED"); calls.incrementAndGet();
                        if (revokeAtAdmission.get()) valid.set(false);
                        return nativeState.ledger.execute(request, nativeState.epoch::get, () -> {
                            admission.run(); if (!nativeState.allowed.get()) return false;
                            nativeState.effects.incrementAndGet(); if (request.kind() == Kind.FORCE_ABILITY) nativeState.epoch.incrementAndGet(); return true;
                        }, System::currentTimeMillis);
                    }));
            providers.register(provider); providers.freezeAndValidate(); await(journal.load());
            context = WeaverProviderTestContext.sandbox(types, valid::get); execution = new WeaverDurableExecutionCoordinator(router, journal, types);
        }
        Map<String, WeaverValue> facts(SubjectRef ref) {
            check(owner.get() && ref.equals(subject), "foreign native snapshot"); final long now = System.currentTimeMillis();
            final var result = new HashMap<>(PvERuntimeActions.facts(nativeState.ledger.view(nativeState.epoch.get()), now));
            result.put("pve.rank", reference(RANK, "normal", "pve.rank", now));
            result.put(PvEProjectionActions.CANONICAL_REVISION, scalar("text", "canonical", now));
            result.put(PvEProjectionActions.PROJECTION_REVISION, scalar("text", "no-projection", now)); return Map.copyOf(result);
        }
        SubjectSnapshot capture() { owner.set(true); try { return new SubjectSnapshot(subject, System.currentTimeMillis(), "owner", providers.captureContributions(subject)); } finally { owner.remove(); } }
        SubjectSnapshot snapshot() { return PvERuntimeActions.SCOPE.apply(capture()); }
        ActionRequest request(String action) { return new ActionRequest(action, action.equals(PvERuntimeActions.FORCE)
                ? Map.of("value", reference(ABILITY, "fixture_ability", "pve.ability", 1)) : Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX); }
        CompletionStage<WeaverReceipt> apply(String action) {
            final var snapshot = snapshot(); final var request = request(action); final var plan = provider.prepare(context, snapshot, request);
            return execution.execute("pve", context, snapshot, request, plan, provider.prepareEffects(context, snapshot, request, plan), context::authority);
        }
        WeaverRecoveryCoordinator recovery() { return new WeaverRecoveryCoordinator(journal, (actor, ref) -> router.submit(new EntityOwner(subject.entityId()), actor, Duration.ofSeconds(5),
                () -> CompletableFuture.completedFuture(capture())), providers, types); }
        public void close() throws Exception { execution.close(); if (journal.ready()) await(journal.close()); else fails(journal.close()); }
    }
    public static void main(String[] args) throws Exception {
        try (final var f = new Fixture(new Storage(), new EntityRef(UUID.randomUUID()), new Native())) {
            check(f.providers.discover(f.snapshot()).providers().get("pve").actions().equals(Set.of(PvERuntimeActions.FORCE, PvERuntimeActions.REFRESH)), "generic native actions absent");
            final var force = f.providers.actions().get(PvERuntimeActions.FORCE);
            check(force.risk() == RiskLevel.MUTATING && !force.undoable() && force.areaSupport() == AreaSupport.NONE && force.lifetimes().equals(Set.of(Lifetime.ONE_SHOT)), "native action bypassed integrity/lifetime policy");
            final var receipt = await(f.apply(PvERuntimeActions.FORCE));
            check(f.nativeState.effects.get() == 1 && receipt.afterFingerprint().equals(f.snapshot().revisionFingerprint()) && receipt.undo().isEmpty(), "native acceptance or receipt mismatch");
            check(new WeaverInfluenceLookup(f.journal, System::currentTimeMillis).source(new RewardSource.Entity(f.subject.entityId())) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "one-shot native source escaped quarantine");
            final long epoch = f.nativeState.epoch.get(); await(f.apply(PvERuntimeActions.REFRESH)); check(f.nativeState.epoch.get() == epoch, "refresh fixture reset native cast epoch");
            final var stale = f.snapshot(); final var request = f.request(PvERuntimeActions.FORCE); final var plan = f.provider.prepare(f.context, stale, request);
            f.nativeState.epoch.incrementAndGet(); final int calls = f.calls.get();
            fails(f.execution.execute("pve", f.context, stale, request, plan, f.provider.prepareEffects(f.context, stale, request, plan), f.context::authority));
            check(f.calls.get() == calls, "stale owner snapshot entered native control");
            f.revokeAtAdmission.set(true); final int effects = f.nativeState.effects.get(); fails(f.apply(PvERuntimeActions.FORCE));
            check(f.nativeState.effects.get() == effects, "final authority loss allowed native mutation");
        }
        for (boolean accepted : List.of(false, true)) observedBeforeJournal(accepted);
        for (int boundary = 1; boundary <= 4; boundary++) for (boolean after : List.of(false, true)) crash(boundary, after);
        System.out.println("PvE native provider passed: " + assertions + " assertions; generic actions, EntityOwner/PREPARED/final authority, native correlation, quarantine, observed journal recovery, unknown-generation review and eight journal crash boundaries. Live cast/cooldown/target evidence is separate.");
    }
    private static void observedBeforeJournal(boolean accepted) throws Exception {
        final var nativeState = new Native(); final var storage = new Storage(); final var subject = new EntityRef(UUID.randomUUID());
        UUID operation;
        try (final var f = new Fixture(storage, subject, nativeState)) {
            final var snapshot = f.snapshot(); final var request = f.request(PvERuntimeActions.FORCE); final var plan = f.provider.prepare(f.context, snapshot, request); operation = plan.operationId();
            final long now = System.currentTimeMillis();
            await(f.journal.prepare(new WeaverOperationRecord(operation, f.context.authority().actor(), "pve", request, subject, snapshot.revisionFingerprint(), Optional.empty(),
                    plan.recoveryPayload(), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false)));
            if (accepted) await(f.router.submit(plan.stages().getFirst().owner(), f.context.authority().actor(), Duration.ofSeconds(5),
                    () -> plan.stages().getFirst().apply().execute(new ExecutionContext(f.context.authority(), snapshot, List.of()), Map.of())));
        }
        try (final var f = new Fixture(storage, subject, nativeState)) {
            final var recovery = f.recovery(); await(recovery.start());
            check(f.journal.snapshot().operations().get(operation).status() == (accepted ? OperationStatus.COMMITTED : OperationStatus.ABORTED) && f.calls.get() == 0, "retained runtime evidence was ignored or replayed"); recovery.close();
        }
        final var lostStorage = new Storage();
        try (final var f = new Fixture(lostStorage, subject, nativeState)) {
            final var snapshot = f.snapshot(); final var request = f.request(PvERuntimeActions.FORCE); final var plan = f.provider.prepare(f.context, snapshot, request); operation = plan.operationId(); final long now = System.currentTimeMillis();
            await(f.journal.prepare(new WeaverOperationRecord(operation, f.context.authority().actor(), "pve", request, subject, snapshot.revisionFingerprint(), Optional.empty(), plan.recoveryPayload(), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false)));
        }
        try (final var f = new Fixture(lostStorage, subject, new Native())) {
            final var recovery = f.recovery(); await(recovery.start());
            check(f.journal.snapshot().operations().get(operation).status() == OperationStatus.NEEDS_REVIEW && f.calls.get() == 0, "lost native generation was guessed or replayed"); recovery.close();
        }
    }
    private static void crash(int boundary, boolean after) throws Exception {
        final var storage = new Storage(); final var subject = new EntityRef(UUID.randomUUID()); final var nativeState = new Native();
        try (final var f = new Fixture(storage, subject, nativeState)) { storage.failAt = boundary; storage.after = after; fails(f.apply(PvERuntimeActions.FORCE)); }
        storage.failAt = 0; final int effects = nativeState.effects.get();
        try (final var f = new Fixture(storage, subject, nativeState)) {
            f.loaded.set(false); final var recovery = f.recovery(); await(recovery.start()); f.loaded.set(true); await(recovery.entityAvailable(subject.entityId()));
            check(nativeState.effects.get() == effects && effects == (boundary > 1 ? 1 : 0) && f.calls.get() == 0, "journal recovery replayed or lost native cast admission");
            check(f.journal.snapshot().operations().values().stream().allMatch(op -> op.status() == OperationStatus.COMMITTED || op.status() == OperationStatus.ABORTED), "observed native acceptance did not reconcile at " + boundary + "/" + after);
            recovery.close();
        }
    }
}
