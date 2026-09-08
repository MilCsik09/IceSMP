package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.area.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverTypeCompatibilityRegressionSuite.rejects;

public final class WeaverAreaExecutionRegressionSuite {
    static final class Router implements WeaverOwnerRouter {
        final Queue<Runnable> pending = new ArrayDeque<>(); ExecutionOwner current; int peak; boolean queued;
        @Override public <T> CompletionStage<T> submit(final ExecutionOwner owner, final UUID actor, final Duration timeout, final Supplier<CompletionStage<T>> action) {
            final CompletableFuture<T> result = new CompletableFuture<>();
            final Runnable invoke = () -> {
                final ExecutionOwner previous = current; current = owner;
                try { action.get().whenComplete((value, failure) -> { if (failure == null) result.complete(value); else result.completeExceptionally(failure); }); }
                catch (final RuntimeException failure) { result.completeExceptionally(failure); }
                finally { current = previous; }
            };
            if (queued) { pending.add(invoke); peak = Math.max(peak, pending.size()); } else invoke.run();
            return result;
        }
        void drain() { while (!pending.isEmpty()) pending.remove().run(); }
        @Override public void close() { }
    }
    static final class Access implements WeaverAreaAccess {
        final Router router; final Map<RegionOwner, ChunkSelection> chunks = new HashMap<>(); final Map<SubjectRef, SubjectSnapshot> snapshots = new HashMap<>(); int reads;
        Access(final Router router) { this.router = router; }
        @Override public ChunkSelection collectOnOwner(final AreaRef area, final RegionOwner chunk, final AreaSupport support, final int limit) {
            check(chunk.equals(router.current), "foreign region collection"); reads++;
            return chunks.getOrDefault(chunk, ChunkSelection.unavailable("CHUNK_UNAVAILABLE"));
        }
        @Override public SubjectSnapshot snapshotOnOwner(final SubjectRef ref) {
            check(SubjectRoute.owner(ref).equals(router.current), "foreign child snapshot");
            final var result = snapshots.get(ref); if (result == null) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE"); return result;
        }
    }
    static WeaverAuthorityToken authority() { return new WeaverAuthorityToken(HiddenDevAuthority.PRIMARY_DEVELOPER, UUID.randomUUID(), Long.MAX_VALUE, () -> true, () -> 0L); }
    static ActionDescriptor descriptor(final AreaSupport support, final RiskLevel risk, final int entities, final int blocks, final int regions) {
        return new ActionDescriptor("fixture.area", "fixture.state", net.kyori.adventure.text.Component.text("Area"), risk,
                Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT), Set.of(WeaverSubjectKind.AREA), List.of(), support,
                Optional.of(new AreaLimits(blocks, entities, 9, regions, 16)), false, Optional.empty(), 1);
    }
    static SubjectSnapshot entity(final UUID world, final SubjectRef ref, final int x, final String fingerprint) {
        return new SubjectSnapshot(ref, 1, fingerprint, Map.of("minecraft.location", new WeaverValue(new WeaverTypeId("weaver", "location", 1),
                SubjectKeyCodec.payload(new LocationRef(world, x, 64, 0, 0, 0)), "minecraft", "minecraft.runtime", Set.of(), 1)));
    }
    static void revision(final Access access, final SubjectRef ref, final String fingerprint) {
        final var old = access.snapshots.get(ref); access.snapshots.put(ref, new SubjectSnapshot(ref, 2, fingerprint, old.facts()));
    }
    public static void main(final String[] args) throws Exception {
        collection(); blockCap(); boundedTasks(); childExecution(false, false); childExecution(true, false); childExecution(true, true); invalidation(); membership(); pendingRecovery();
        check(WeaverRateLimiter.areaCost(128, 1) == 9 && WeaverRateLimiter.areaCost(4096, 1) == 257, "AREA rate cost was clamped");
        check(!new WeaverRateLimiter(() -> 0).tryAcquireArea(4096, 1), "Oversized block action bypassed token budget");
        System.out.println("Weaver AREA execution passed: 9 owner chunks, 16 bounded continuations, 128 entities/4096 blocks, no unloaded reads, membership drift, child compensation and logout/close.");
    }
    private static void collection() throws Exception {
        final Router router = new Router(); router.queued = true; final Access access = new Access(router); final WeaverAreaEngine engine = new WeaverAreaEngine(router, access);
        final AreaRef area = new AreaRef(UUID.randomUUID(), new RadiusArea(15.5, 64, 15.5, 16));
        final var owners = WeaverAreaEngine.chunks(area); check(owners.size() == 9, "nine chunk plan");
        final List<SubjectRef> refs = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            final EntityRef ref = new EntityRef(UUID.randomUUID()); refs.add(ref); access.snapshots.put(ref, new SubjectSnapshot(ref, 1, "before", Map.of("minecraft.location", new WeaverValue(new WeaverTypeId("weaver", "location", 1),
                    SubjectKeyCodec.payload(new LocationRef(area.worldId(), 15, 64, 15, 0, 0)), "minecraft", "minecraft.runtime", Set.of(), 1))));
        }
        access.chunks.put(owners.getFirst(), new WeaverAreaAccess.ChunkSelection(refs, Optional.empty()));
        final var collecting = engine.collect(authority(), area, descriptor(AreaSupport.ENTITY_FANOUT, RiskLevel.MUTATING, 128, 0, 9));
        check(router.pending.size() == 9, "unbounded/absent initial chunk admission"); router.drain(); final var result = await(collecting);
        check(result.targets().size() == 128 && result.skippedChunks().size() == 8 && router.peak <= 16, "target/concurrency/unloaded cap");
        final var reversed = new ArrayList<>(result.targets()); Collections.reverse(reversed);
        check(result.fingerprint().equals(new WeaverAreaCollection(area, result.support(), reversed, result.skippedChunks(), Map.of()).fingerprint()), "AREA fingerprint depends on completion order");
        final EntityRef extra = new EntityRef(UUID.randomUUID()); access.chunks.put(owners.getLast(), new WeaverAreaAccess.ChunkSelection(List.of(extra), Optional.empty()));
        final var exceeded = engine.collect(authority(), area, descriptor(AreaSupport.ENTITY_FANOUT, RiskLevel.MUTATING, 128, 0, 9)); router.drain(); fails(exceeded);
        rejects(() -> engine.collect(authority(), area, descriptor(AreaSupport.ENTITY_FANOUT, RiskLevel.DESTRUCTIVE, 128, 0, 9)));
        rejects(() -> engine.collect(authority(), area, descriptor(AreaSupport.ENTITY_FANOUT, RiskLevel.MUTATING, 128, 0, 8)));
        engine.close();
    }
    private static void blockCap() throws Exception {
        final Router router = new Router(); final Access access = new Access(router); final WeaverAreaEngine engine = new WeaverAreaEngine(router, access);
        final AreaRef area = new AreaRef(UUID.randomUUID(), new CuboidArea(new AreaBounds(0, 0, 0, 15, 15, 15)));
        final List<SubjectRef> refs = new ArrayList<>();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            final var ref = new BlockRef(area.worldId(), x, y, z); refs.add(ref); access.snapshots.put(ref, new SubjectSnapshot(ref, 1, "stone", Map.of()));
        }
        access.chunks.put(WeaverAreaEngine.chunks(area).getFirst(), new WeaverAreaAccess.ChunkSelection(refs, Optional.empty()));
        final var result = await(engine.collect(authority(), area, descriptor(AreaSupport.BLOCK_FANOUT, RiskLevel.READ_ONLY, 0, 4096, 9)));
        check(result.targets().size() == 4096 && result.fingerprint().length() == 64, "4096 block collection/fingerprint failed");
        fails(engine.collect(authority(), area, descriptor(AreaSupport.BLOCK_FANOUT, RiskLevel.READ_ONLY, 0, 4095, 9))); engine.close();
    }
    private static void boundedTasks() throws Exception {
        final AtomicInteger started = new AtomicInteger(); final List<CompletableFuture<Integer>> pending = new ArrayList<>();
        final var result = WeaverBoundedTasks.map(java.util.stream.IntStream.range(0, 4096).boxed().toList(), 16, index -> {
            started.incrementAndGet(); final var future = new CompletableFuture<Integer>(); pending.add(future); return future;
        });
        check(started.get() == 16, "fanout exceeded first admission bound"); pending.get(0).completeExceptionally(new IllegalStateException("fixture"));
        check(!result.toCompletableFuture().isDone() && started.get() == 16, "failure abandoned admitted work or admitted more");
        for (int i = 1; i < 16; i++) pending.get(i).complete(i); fails(result);
        final var immediate = await(WeaverBoundedTasks.map(java.util.stream.IntStream.range(0, 4096).boxed().toList(), 16, CompletableFuture::completedFuture));
        check(immediate.size() == 4096 && immediate.getLast() == 4095, "synchronous continuation stack overflow/order loss");
    }
    private static void childExecution(final boolean fail, final boolean drift) throws Exception {
        final Router router = new Router(); final Access access = new Access(router); final WeaverAreaEngine engine = new WeaverAreaEngine(router, access);
        final AreaRef area = new AreaRef(UUID.randomUUID(), new CuboidArea(new AreaBounds(0, 64, 0, 1, 64, 0))); final var owner = WeaverAreaEngine.chunks(area).getFirst();
        final List<SubjectRef> refs = List.of(new EntityRef(new UUID(0, 1)), new EntityRef(new UUID(0, 2)));
        for (final SubjectRef ref : refs) access.snapshots.put(ref, entity(area.worldId(), ref, 0, "before"));
        access.chunks.put(owner, new WeaverAreaAccess.ChunkSelection(refs, Optional.empty()));
        final var descriptor = descriptor(AreaSupport.ENTITY_FANOUT, RiskLevel.MUTATING, 128, 0, 9); final var authority = authority();
        final var collection = await(engine.collect(authority, area, descriptor)); final var snapshot = collection.decorate(new SubjectSnapshot(area, 1, "area", Map.of()));
        final List<String> effects = new ArrayList<>(); final List<ExecutionStage> stages = new ArrayList<>();
        for (int i = 0; i < collection.targets().size(); i++) {
            final int index = i; final SubjectRef ref = collection.targets().get(i).ref();
            stages.add(new ExecutionStage("fixture.child_" + i, SubjectRoute.owner(ref), Map.of(), (context, payload) -> {
                check(context.snapshot().ref().equals(ref) && SubjectRoute.owner(ref).equals(router.current), "child did not receive owner snapshot");
                if (fail && index == 1) { if (drift) revision(access, collection.targets().getFirst().ref(), "external"); throw new WeaverDomainRejection("FIXTURE_FAILURE"); }
                revision(access, ref, "after"); effects.add("apply" + index); return CompletableFuture.completedFuture(new StageResult("after", Map.of(), Map.of()));
            }, Optional.of((context, payload) -> { revision(access, ref, "before"); effects.add("compensate" + index); return CompletableFuture.completedFuture(new StageResult("before", Map.of(), Map.of())); }), 5000));
        }
        final var prepared = engine.guard(collection, new PreparedAction(UUID.randomUUID(), descriptor, area, snapshot.revisionFingerprint(), stages, new OperationRecoveryPayload(1, Map.of()),
                (plan, results, now) -> new WeaverReceipt(UUID.randomUUID(), plan.operationId(), "fixture", descriptor.id(), area, RiskLevel.MUTATING, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX,
                        plan.expectedBeforeFingerprint(), results.getLast().afterFingerprint(), Map.of(), Map.of(), Optional.empty(), now, ReceiptStatus.COMMITTED)));
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage); await(journal.load());
        final var types = WeaverProjectionRegressionSuite.types(); final var context = new ProviderContext(authority, types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
        final var executor = new WeaverDurableExecutionCoordinator(router, journal, types); final var request = new ActionRequest(descriptor.id(), Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
        final Set<WeaverInfluenceTarget> targets = new HashSet<>(); refs.forEach(ref -> targets.add(WeaverInfluenceTarget.subject(ref)));
        final var execution = executor.execute("fixture", context, snapshot, request, prepared, new PreparedEffects(new WeaverEffectIntent(targets), (plan, results, receipt, sequence) -> WeaverEffectCommit.none()), () -> authority);
        if (fail) {
            fails(execution); check(effects.equals(drift ? List.of("apply0") : List.of("apply0", "compensate0")), "partial child compensation ignored drift/order");
            check(journal.snapshot().operations().get(prepared.operationId()).status() == OperationStatus.NEEDS_REVIEW, "partial AREA released uncertainty");
        } else {
            final var receipt = await(execution); final var after = await(engine.collect(authority, area, descriptor));
            check(receipt.afterFingerprint().equals(after.fingerprint()), "aggregate receipt fingerprint differs from observed children");
            check(journal.snapshot().influences().size() == 3, "AREA/child evidence not committed together");
        }
        executor.close(); await(journal.close()); engine.close();
    }
    private static void invalidation() throws Exception {
        for (final boolean logout : List.of(false, true)) {
            final Router router = new Router(); router.queued = true; final Access access = new Access(router); final WeaverAreaEngine engine = new WeaverAreaEngine(router, access);
            final var authority = authority(); final var area = new AreaRef(UUID.randomUUID(), new CuboidArea(new AreaBounds(0, 64, 0, 0, 64, 0)));
            final var pending = engine.collect(authority, area, descriptor(AreaSupport.ENTITY_FANOUT, RiskLevel.MUTATING, 128, 0, 9));
            if (logout) authority.revoke(); else engine.close(); router.drain(); fails(pending); check(access.reads == 0, "invalidated queued collection read world state");
            engine.close();
        }
    }
    private static void membership() throws Exception {
        final Router router = new Router(); final Access access = new Access(router); final var engine = new WeaverAreaEngine(router, access);
        final AreaRef area = new AreaRef(UUID.randomUUID(), new CuboidArea(new AreaBounds(0, 64, 0, 31, 64, 0)));
        final var kept = new EntityRef(UUID.randomUUID()); final var moved = new EntityRef(UUID.randomUUID()); final var retired = new EntityRef(UUID.randomUUID());
        final var owners = WeaverAreaEngine.chunks(area); final var refs = List.<SubjectRef>of(kept, moved, retired);
        access.chunks.put(owners.getFirst(), new WeaverAreaAccess.ChunkSelection(refs, Optional.empty()));
        access.chunks.put(owners.getLast(), new WeaverAreaAccess.ChunkSelection(List.of(kept), Optional.empty()));
        access.snapshots.put(kept, entity(area.worldId(), kept, 0, "before")); access.snapshots.put(moved, entity(area.worldId(), moved, 50, "before"));
        final var collected = await(engine.collect(authority(), area, descriptor(AreaSupport.ENTITY_FANOUT, RiskLevel.MUTATING, 128, 0, 9)));
        check(collected.targets().size() == 1 && collected.skippedTargets().get(moved).equals("LEFT_AREA") && collected.skippedTargets().get(retired).equals("ENTITY_UNAVAILABLE"), "migration duplicate/moved/retired collection");
        final Set<WeaverInfluenceTarget> targets = new HashSet<>(); targets.add(WeaverInfluenceTarget.subject(area));
        for (int i = 0; i < 128; i++) targets.add(WeaverInfluenceTarget.subject(new EntityRef(UUID.randomUUID())));
        check(new WeaverEffectIntent(targets).targets().size() == 129, "128 child intents lost parent AREA evidence"); engine.close();
    }
    static final class RecoveryProvider extends WeaverContractRegressionSuite.FixtureProvider {
        int assessed;
        RecoveryProvider(final ActionDescriptor action) { super("fixture", action, CoverageLevel.FULL_PROVIDER, Map.of(action.id(), "fixture.assess")); }
        @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.AREA); }
        @Override public ProviderCoverage coverage() { return new ProviderCoverage("fixture.domain", CoverageLevel.FULL_PROVIDER, "Isolated AREA fixture", Set.of("fixture.state", "fixture.area")); }
        @Override public RecoveryAssessment assessAreaRecovery(final RecoveryContext context, final SubjectSnapshot snapshot, final WeaverAreaCollection collection, final WeaverOperationRecord operation) {
            assessed++; context.authority().require(operation); check(collection.targets().size() == 2, "recovery collected newly arrived entities");
            return new RecoveryAssessment(snapshot.revisionFingerprint().equals(operation.beforeFingerprint()) ? ObservedOperationState.BEFORE : ObservedOperationState.PARTIAL, false, Optional.empty(), "fixture");
        }
    }
    private static void pendingRecovery() throws Exception {
        final Router router = new Router(); final Access access = new Access(router); final var engine = new WeaverAreaEngine(router, access);
        final AreaRef area = new AreaRef(UUID.randomUUID(), new CuboidArea(new AreaBounds(0, 64, 0, 0, 64, 0))); final var owner = WeaverAreaEngine.chunks(area).getFirst();
        final var first = new EntityRef(UUID.randomUUID()); final var second = new EntityRef(UUID.randomUUID());
        access.chunks.put(owner, new WeaverAreaAccess.ChunkSelection(List.of(first, second), Optional.empty()));
        access.snapshots.put(first, entity(area.worldId(), first, 0, "before")); access.snapshots.put(second, entity(area.worldId(), second, 0, "before"));
        final var descriptor = descriptor(AreaSupport.ENTITY_FANOUT, RiskLevel.MUTATING, 128, 0, 9); final var collection = await(engine.collect(authority(), area, descriptor));
        final var storage = new Storage(); final var journal = new WeaverJournal(storage); await(journal.load());
        final var record = new WeaverOperationRecord(UUID.randomUUID(), HiddenDevAuthority.PRIMARY_DEVELOPER, "fixture", new ActionRequest(descriptor.id(), Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX),
                area, collection.fingerprint(), Optional.empty(), new OperationRecoveryPayload(1, Map.of(WeaverAreaRecoveryEvidence.KEY, WeaverAreaRecoveryEvidence.of(collection).encode())), OperationStatus.PREPARED, 0, 1, 1, Optional.empty(), false);
        await(journal.prepare(record, new WeaverEffectIntent(Set.of(WeaverInfluenceTarget.subject(first), WeaverInfluenceTarget.subject(second)))));
        final var types = WeaverProjectionRegressionSuite.types(); final var codec = new WeaverJournalCodec(types);
        check(codec.decodeState(codec.encodeState(journal.snapshot())).equals(journal.snapshot()), "AREA evidence persistence");
        final var provider = new RecoveryProvider(descriptor); final var providers = WeaverContractRegressionSuite.registry(provider); providers.freezeAndValidate();
        final var recovery = new WeaverRecoveryCoordinator(journal, (actor, subject) -> CompletableFuture.completedFuture(new SubjectSnapshot(subject, 3, "area", Map.of())), providers, types, engine);
        final var saved = access.snapshots.remove(second); await(recovery.start());
        check(provider.assessed == 0 && recovery.pending().get(record.operationId()) == WeaverRecoveryCoordinator.PendingReason.PENDING_ENTITY_LOAD, "unloaded AREA child became applied/aborted instead of pending");
        final int reads = access.reads; access.snapshots.put(second, saved);
        access.chunks.put(owner, new WeaverAreaAccess.ChunkSelection(List.of(first, second, new EntityRef(UUID.randomUUID())), Optional.empty()));
        await(recovery.entityAvailable(second.entityId()));
        check(provider.assessed == 1 && access.reads == reads && journal.snapshot().operations().get(record.operationId()).status() == OperationStatus.ABORTED, "AREA recovery replayed collection or lost before-state assessment");
        recovery.close(); engine.close(); await(journal.close());
    }
}
