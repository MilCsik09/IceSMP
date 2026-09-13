package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverProjectionRegressionSuite.*;

/** Native lifetime decisions are injected; real journal, consumer registry and neutral admission remain in use. */
public final class WeaverObservedInfluenceRegressionSuite {
    private static final WeaverTypeId TYPE = WeaverTypeId.parse("fixture:effect_lifetime@1");
    private static int assertions;
    static final class Observer extends WeaverContractRegressionSuite.FixtureProvider implements WeaverInfluenceObserverProvider {
        Function<WeaverInfluenceRecord, CompletionStage<InfluenceObservation>> observation = value -> CompletableFuture.completedFuture(InfluenceObservation.active());
        List<InfluenceLifetimeDescriptor> descriptors = List.of(new InfluenceLifetimeDescriptor("observer", "observer.state", TYPE, Set.of(InfluenceScope.PLAYER)));
        Observer() { super("observer", WeaverContractRegressionSuite.safe("observer"), CoverageLevel.FULL_PROVIDER, Map.of()); }
        public List<InfluenceLifetimeDescriptor> influenceLifetimes() { return descriptors; }
        public CompletionStage<InfluenceObservation> observeInfluence(WeaverInfluenceRecord record) { return observation.apply(record); }
    }
    static final class Fixture implements AutoCloseable {
        final AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        final WeaverPersistenceRegressionSuite.Storage storage = new WeaverPersistenceRegressionSuite.Storage();
        final Observer observer = new Observer();
        final WorldWeaverProviderRegistry registry = registry(observer);
        final WeaverJournal journal;
        final GameplayEffectGate.Binding binding;
        final EntityRef root = new EntityRef(UUID.randomUUID());
        final RewardSource.Player target = new RewardSource.Player(UUID.randomUUID());
        Fixture() throws Exception { this(null); }
        Fixture(WeaverJournalStorage replacement) throws Exception {
            registry.freezeAndValidate();
            journal = new WeaverJournal(replacement == null ? storage : replacement, ignored -> { }, clock::get, registry::resolveInfluenceLifetime);
            await(journal.load()); apply(journal, operation(root, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), WeaverEffectCommit.none());
            binding = GameplayEffectGate.install(journal::prepareDerivedEffect);
        }
        GameplayEffectContext context(String effect) { return context(target, effect); }
        GameplayEffectContext context(RewardSource.Player player, String effect) {
            return new GameplayEffectContext(List.of(new RewardSource.Entity(root.entityId())), Set.of(player), 60_000,
                    Optional.of(new GameplayEffectLifetime(TYPE.canonical(), Map.of("id", effect))));
        }
        WeaverInfluenceRecord record(String effect) {
            return journal.snapshot().influences().values().stream().filter(value -> value.target().source().equals(target)
                    && value.observedLifetime().map(life -> life.payload().get("id").equals(effect)).orElse(false)).findFirst().orElseThrow();
        }
        @Override public void close() throws Exception { binding.close(); await(journal.close()); }
    }
    private static WorldWeaverProviderRegistry registry(Observer observer) {
        final var types = types(); types.register(ScalarTypeCodec.reference(TYPE, id -> Set.of("poison", "slow").contains(id)));
        final var registry = new WorldWeaverProviderRegistry(types, System::currentTimeMillis); registry.register(observer); return registry;
    }
    public static void main(String[] args) throws Exception {
        observedEnd(); races(); fences(); dispatcher(); publication(); failures(); migration(); providerIsolation(); projectionEnd(); lateDelivery();
        System.out.println("Observed influence lifetime passed: " + assertions + " assertions; native-observation contract, owner fencing, drift, held fsync, offline retention, all reward channels, schema migration and failure isolation.");
    }
    private static void observedEnd() throws Exception {
        try (final var f = new Fixture()) {
            final var baseline = f.journal.snapshot();
            check(await(GameplayEffectGate.prepare(f.context("poison"))).claim(), "registered typed lifetime denied");
            check(f.record("poison").active(), "lingering effect not active");
            f.clock.addAndGet(86_400_000);
            final var lookup = new InfluenceRewardEligibilityPolicy(new WeaverInfluenceLookup(f.journal, f.clock::get));
            for (final var channel : RewardChannel.values()) check(!lookup.evaluate(new RewardContext(channel, f.target.id(), List.of(f.target))).allowed(), "nominal duration leaked reward");
            final var dispatcher = new WeaverInfluenceDispatcher(f.journal, f.registry::observeInfluence);
            await(dispatcher.pulse()); check(f.record("poison").active(), "ACTIVE observation ended effect");
            f.observer.observation = record -> CompletableFuture.completedFuture(InfluenceObservation.unavailable());
            await(dispatcher.pulse()); check(f.record("poison").active(), "offline/unloaded target ended effect");
            final var codec = new WeaverJournalCodec(); final var path = Files.createTempDirectory("weaver-observed-lifetime");
            final var yaml = new YamlWeaverJournalStorage(path.toFile(), codec, java.util.logging.Logger.getLogger("fixture"));
            yaml.writeState(f.journal.snapshot()); yaml.writeAudit(f.storage.audit);
            final var restart = new WeaverJournal(yaml); await(restart.load());
            check(restart.snapshot().equals(f.journal.snapshot()) && restart.influenceIndex().quarantined(f.target, f.clock.get()), "restart without current provider washed active lifetime");
            await(restart.close());
            f.observer.observation = record -> CompletableFuture.completedFuture(InfluenceObservation.ended(GameplayEffectGate.fence(record.target().source()).orElseThrow()));
            final var delayed = await(GameplayEffectGate.prepare(f.context("poison")));
            await(dispatcher.pulse());
            check(!f.record("poison").active() && !delayed.claim(), "ended lifetime allowed an older pending native application");
            check(f.record("poison").quarantinedUntil() >= f.clock.get() + PlayerQuarantine.MINIMUM_TAIL_MILLIS, "actual end shortened minimum tail");
            f.clock.addAndGet(PlayerQuarantine.MINIMUM_TAIL_MILLIS + 65_001);
            for (final var channel : RewardChannel.values()) check(lookup.evaluate(new RewardContext(channel, f.target.id(), List.of(f.target))).allowed(), "ended tail never released reward");
            check(f.journal.snapshot().operations().equals(baseline.operations()) && f.journal.snapshot().receipts().equals(baseline.receipts()), "observing changed canonical history");
            check(await(GameplayEffectGate.prepare(f.context("poison"))).claim() && f.record("poison").active(), "new actual application cannot reactivate lifetime");
            check(await(GameplayEffectGate.prepare(f.context("slow"))).claim() && f.journal.snapshot().influences().size() == 3, "different effects shared an expiry identity");
            final var count = f.journal.snapshot().influences().size();
            check(await(GameplayEffectGate.prepare(f.context("poison"))).claim() && f.journal.snapshot().influences().size() == count, "same effect duplicated lineage");
            dispatcher.close(); await(dispatcher.pulse()); check(f.record("poison").active(), "closed dispatcher performed an observation");
        }
    }
    private static void races() throws Exception {
        try (final var f = new Fixture()) {
            check(await(GameplayEffectGate.prepare(f.context("poison"))).claim(), "setup");
            final var before = f.record("poison"); f.clock.incrementAndGet();
            final var newPermit = await(GameplayEffectGate.prepare(f.context("poison")));
            try (final var fence = GameplayEffectGate.fence(f.target).orElseThrow()) {
                check(!await(f.journal.endObservedInfluence(before, fence)), "old observation overwrote extended influence");
                check(!newPermit.claim(), "application crossed native absence/fsync fence");
                final var pending = await(GameplayEffectGate.prepare(f.context("poison")));
                check(await(f.journal.endObservedInfluence(f.record("poison"), fence)), "exact current observation refused");
                check(!pending.claim(), "held observation permitted new effect");
            }
            check(await(GameplayEffectGate.prepare(f.context("poison"))).claim(), "released fence blocked future native effect");
            final var current = f.record("poison");
            try (final var foreign = GameplayEffectGate.fence(new RewardSource.Player(UUID.randomUUID())).orElseThrow()) {
                check(!await(f.journal.endObservedInfluence(current, foreign)), "foreign target fence ended effect");
            }
            final var expiredFence = GameplayEffectGate.fence(f.target).orElseThrow(); expiredFence.close();
            check(!await(f.journal.endObservedInfluence(current, expiredFence)), "closed fence ended effect");
            await(f.journal.expireProjections(f.clock.get(), true));
            check(f.record("poison").active(), "session cleanup ended lingering native effect");
        }
    }
    private static void fences() throws Exception {
        final var target = new RewardSource.Player(UUID.randomUUID());
        check(GameplayEffectGate.fence(target).isEmpty(), "unbound observation fence");
        final var old = GameplayEffectGate.install(context -> CompletableFuture.completedFuture(GameplayEffectPermit.guarded(() -> true)));
        final var held = GameplayEffectGate.fence(target).orElseThrow();
        check(held.activeFor(new RewardSource.Entity(target.id())) && GameplayEffectGate.fence(new RewardSource.Entity(target.id())).isEmpty(), "entity/player alias bypassed fence");
        final var context = new GameplayEffectContext(List.of(target), Set.of(new RewardSource.Entity(target.id())), 0);
        check(!await(GameplayEffectGate.prepare(context)).claim(), "entity alias application bypassed player observation");
        final var handles = new ArrayList<GameplayEffectGate.ObservationFence>();
        for (int i = 1; i < 128; i++) handles.add(GameplayEffectGate.fence(new RewardSource.Player(UUID.randomUUID())).orElseThrow());
        check(GameplayEffectGate.fence(new RewardSource.Player(UUID.randomUUID())).isEmpty(), "unbounded observation fences");
        handles.forEach(GameplayEffectGate.ObservationFence::close); old.close();
        try (final var next = GameplayEffectGate.install(value -> CompletableFuture.completedFuture(GameplayEffectPermit.guarded(() -> true)))) {
            check(GameplayEffectGate.fence(target).isEmpty() && !await(GameplayEffectGate.prepare(context)).claim(), "policy replacement bypassed an in-flight durable observation");
            held.close(); final var replacement = GameplayEffectGate.fence(target).orElseThrow(); held.close(); old.close();
            check(!held.activeFor(target) && replacement.activeFor(target), "old observation retired replacement binding/fence"); replacement.close();
            final UUID world = UUID.randomUUID();
            try (final var spatial = GameplayEffectGate.fence(new RewardSource.Location(world, 1.2, 2.3, 3.4)).orElseThrow()) {
                check(GameplayEffectGate.fence(new RewardSource.Location(world, 1.9, 2.9, 3.9)).isEmpty(), "fractional same-block fence bypass");
            }
        }
    }
    private static void dispatcher() throws Exception {
        try (final var f = new Fixture()) {
            for (int i = 0; i < 33; i++) check(await(GameplayEffectGate.prepare(f.context(new RewardSource.Player(UUID.randomUUID()), "poison"))).claim(), "fanout fixture setup");
            final Set<UUID> seen = new HashSet<>(); final var pending = new CompletableFuture<InfluenceObservation>(); final AtomicBoolean hold = new AtomicBoolean(true);
            f.observer.observation = record -> { seen.add(record.id()); return hold.get() ? pending : CompletableFuture.completedFuture(InfluenceObservation.active()); };
            final var dispatcher = new WeaverInfluenceDispatcher(f.journal, f.registry::observeInfluence);
            final var first = dispatcher.pulse(); await(dispatcher.pulse()); check(seen.size() == 1 && !first.toCompletableFuture().isDone(), "overlapping observations were admitted");
            hold.set(false); pending.complete(InfluenceObservation.active()); await(first); check(seen.size() == 16, "per-pulse owner fanout exceeds 16");
            await(dispatcher.pulse()); check(seen.size() == 32, "round-robin starved later targets"); await(dispatcher.pulse()); check(seen.size() == 33, "round-robin missed final target"); dispatcher.close();
        }
    }
    private static void publication() throws Exception {
        final var storage = new WeaverPersistenceRegressionSuite.Storage(); final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1); final var hold = new AtomicBoolean();
        final WeaverJournalStorage paused = new WeaverJournalStorage() {
            public WeaverJournalState readState() { return storage.state; }
            public Map<String, WeaverAuditEntry> readAudit() { return storage.audit; }
            public void writeAudit(Map<String, WeaverAuditEntry> data) throws Exception { storage.writeAudit(data); }
            public void writeState(WeaverJournalState data) throws Exception { if (hold.get()) { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture IO timeout"); } storage.writeState(data); }
        };
        try (final var f = new Fixture(paused)) {
            check(await(GameplayEffectGate.prepare(f.context("poison"))).claim(), "held fixture setup");
            final var pending = await(GameplayEffectGate.prepare(f.context("poison")));
            f.observer.observation = record -> CompletableFuture.completedFuture(InfluenceObservation.ended(GameplayEffectGate.fence(record.target().source()).orElseThrow()));
            final var dispatcher = new WeaverInfluenceDispatcher(f.journal, f.registry::observeInfluence); hold.set(true); final var observing = dispatcher.pulse();
            try {
                check(entered.await(5, TimeUnit.SECONDS), "end write not entered");
                dispatcher.close(); check(!observing.toCompletableFuture().isDone() && f.record("poison").active(), "end published before durable acknowledgement");
                check(GameplayEffectGate.fence(f.target).isEmpty() && !pending.claim(), "shutdown released in-flight observation fence");
            } finally { hold.set(false); release.countDown(); }
            await(observing); try (final var fence = GameplayEffectGate.fence(f.target).orElseThrow()) { check(!f.record("poison").active(), "acknowledged end not published"); }
        }
    }
    private static void failures() throws Exception {
        for (final boolean afterWrite : List.of(false, true)) {
            final var f = new Fixture(); check(await(GameplayEffectGate.prepare(f.context("poison"))).claim(), "failure fixture setup");
            f.storage.failWrite = f.storage.writes + 1; f.storage.afterWrite = afterWrite;
            try (final var fence = GameplayEffectGate.fence(f.target).orElseThrow()) { fails(f.journal.endObservedInfluence(f.record("poison"), fence)); }
            check(!f.journal.ready() && !await(GameplayEffectGate.prepare(f.context("poison"))).claim(), "ambiguous end write admitted effect");
            f.binding.close(); fails(f.journal.close()); f.storage.failWrite = 0;
            final var restart = new WeaverJournal(f.storage); await(restart.load());
            final var record = restart.snapshot().influences().values().stream().filter(v -> v.observedLifetime().isPresent()).findFirst().orElseThrow();
            check(record.active() != afterWrite && restart.influenceIndex().quarantined(f.target, f.clock.get()), "end crash replayed or erased tail"); await(restart.close());
        }
    }
    private static void migration() throws Exception {
        try (final var f = new Fixture()) {
            final var codec = new WeaverJournalCodec(); final Map<String, Object> legacy = new HashMap<>(codec.encodeState(f.journal.snapshot()));
            legacy.put("schema-version", 3); legacy.put("influences", stripLifetime(WeaverJournalCodec.map(legacy.get("influences"))));
            check(codec.decodeState(legacy).equals(f.journal.snapshot()), "schema 3 migration changed existing lineage");
            check(await(GameplayEffectGate.prepare(f.context("poison"))).claim(), "migration fixture setup");
            final var encoded = codec.encodeState(f.journal.snapshot());
            check(codec.decodeState(encoded).equals(f.journal.snapshot()), "schema 4 observed lifetime roundtrip");
            final var current = f.record("poison"); final var unknown = new WeaverValue(WeaverTypeId.parse("removed:effect@9"), Map.of("old", "content"), "removed", "removed.state", Set.of("weaver.influence_lifetime"), 0);
            final var state = f.journal.snapshot(); final var influences = new HashMap<>(state.influences());
            influences.put(current.id(), new WeaverInfluenceRecord(current.id(), current.influence(), current.target(), true, current.quarantinedUntil(), Optional.of(unknown)));
            final var historical = new WeaverJournalState(state.revision(), state.operations(), state.receipts(), state.projectionSequence(), state.intents(), state.projections(), influences, state.effectDeltas());
            check(codec.decodeState(codec.encodeState(historical)).equals(historical), "missing future provider blocked safe durable decode");
            check(await(f.registry.observeInfluence(influences.get(current.id()))).status() == InfluenceObservation.Status.UNAVAILABLE, "unknown historical content ended quarantine");
        }
    }
    private static Map<String, Object> stripLifetime(Map<String, Object> values) {
        final Map<String, Object> rows = new HashMap<>(); values.forEach((id, value) -> { final var row = new HashMap<>(WeaverJournalCodec.map(value)); row.remove("lifetime"); rows.put(id, row); }); return rows;
    }
    private static void providerIsolation() throws Exception {
        final var invalid = new Observer(); invalid.descriptors = List.of(new InfluenceLifetimeDescriptor("foreign", "observer.state", TYPE, Set.of(InfluenceScope.PLAYER)));
        rejects(() -> registry(invalid).freezeAndValidate());
        final var wrongFacet = new Observer(); wrongFacet.descriptors = List.of(new InfluenceLifetimeDescriptor("observer", "foreign.state", TYPE, Set.of(InfluenceScope.PLAYER)));
        rejects(() -> registry(wrongFacet).freezeAndValidate());
        try (final var f = new Fixture()) {
            check(await(GameplayEffectGate.prepare(f.context("poison"))).claim(), "isolation setup");
            final var context = f.context("poison");
            rejects(() -> f.registry.resolveInfluenceLifetime(new GameplayEffectContext(context.sources(), Set.of(new RewardSource.World(UUID.randomUUID())), 1000, context.lifetime())));
            check(!await(GameplayEffectGate.prepare(f.context("missing"))).claim() && !f.registry.quarantined("observer"), "unknown current content did not refuse normally");
            f.observer.observation = record -> { throw new IllegalStateException("private fixture failure"); };
            final var dispatcher = new WeaverInfluenceDispatcher(f.journal, f.registry::observeInfluence);
            for (int i = 0; i < 3; i++) await(dispatcher.pulse());
            check(f.registry.quarantined("observer") && f.record("poison").active(), "failed observer not isolated or lost quarantine");
            check(!await(GameplayEffectGate.prepare(f.context("slow"))).claim(), "quarantined consumer accepted new lingering effect"); dispatcher.close();
        }
    }
    private static void projectionEnd() throws Exception {
        for (final boolean compensate : List.of(false, true)) try (final var f = new Fixture()) {
            final var operation = operation(f.root, Lifetime.SESSION, IntegrityMode.SANDBOX);
            final var projection = projection(operation, 1, 4, OptionalLong.empty());
            await(f.journal.prepare(operation));
            final var applied = await(f.journal.applied(operation.operationId(), 0, receipt(operation), effect(projection), 2));
            if (!compensate) await(f.journal.finishAudit(operation.operationId(), applied.revision()));
            check(await(GameplayEffectGate.prepare(f.context("poison"))).claim(), "projection lifetime setup");
            if (compensate) await(f.journal.resolve(operation.operationId(), applied.revision(), OperationStatus.COMPENSATED, 3));
            else check(await(f.journal.expireProjections(f.clock.get(), true)) == 1, "projection expiry fixture did not remove projection");
            check(f.journal.snapshot().projections().isEmpty(), "projection removal not executed");
            check(f.journal.snapshot().influences().values().stream().filter(value -> value.observedLifetime().isPresent()).allMatch(WeaverInfluenceRecord::active),
                    "projection compensation/expiry ended an already applied lingering effect");
        }
    }
    private static void lateDelivery() throws Exception {
        try (final var f = new Fixture()) {
            check(await(GameplayEffectGate.prepare(f.context("poison"))).claim(), "late delivery setup");
            final var actual = new CompletableFuture<InfluenceObservation>();
            f.observer.observation = record -> actual;
            final var bounded = f.registry.observeInfluence(f.record("poison"));
            fails(bounded);
            final var lateFence = GameplayEffectGate.fence(f.target).orElseThrow();
            actual.complete(InfluenceObservation.ended(lateFence));
            check(!lateFence.activeFor(f.target) && f.record("poison").active(), "late provider result leaked a fence or ended quarantine");
            for (final boolean shutdown : List.of(false, true)) {
                final var admission = new OwnerTaskAdmission<Void>();
                final AtomicReference<java.util.function.Supplier<CompletionStage<Void>>> queued = new AtomicReference<>();
                final WeaverOwnerRouter router = new WeaverOwnerRouter() {
                    @SuppressWarnings("unchecked") public <T> CompletionStage<T> submit(ExecutionOwner owner, UUID actor, java.time.Duration timeout, java.util.function.Supplier<CompletionStage<T>> task) {
                        check(owner.equals(new EntityOwner(f.target.id())), "lifetime observation routed to wrong owner");
                        queued.set((java.util.function.Supplier<CompletionStage<Void>>) (Object) task);
                        return (CompletionStage<T>) admission.result();
                    }
                    public void close() { admission.shutdown(); }
                };
                final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1);
                final var delivered = WeaverInfluenceOwnerObservation.submit(router, f.target.id(), () -> {
                    final var fence = GameplayEffectGate.fence(f.target).orElseThrow(); entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture owner timeout"); }
                    catch (InterruptedException failure) { fence.close(); throw new IllegalStateException(failure); }
                    return InfluenceObservation.ended(fence);
                });
                final var task = CompletableFuture.runAsync(() -> admission.run(queued.get()));
                try {
                    check(entered.await(5, TimeUnit.SECONDS), "owner observation not started");
                    if (shutdown) admission.shutdown(); else admission.timeout();
                    fails(delivered);
                } finally { release.countDown(); }
                await(task);
                try (final var reclaimed = GameplayEffectGate.fence(f.target).orElseThrow()) { check(reclaimed.activeFor(f.target), "discarded owner result leaked its fence"); }
            }
        }
    }
    private static void rejects(Runnable action) { try { action.run(); throw new AssertionError("expected refusal"); } catch (IllegalArgumentException | WeaverDomainRejection expected) { assertions++; } }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
