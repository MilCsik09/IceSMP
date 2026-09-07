package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Files;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverProjectionRegressionSuite.*;

public final class WeaverEffectPropagationRegressionSuite {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        final var storage = new Storage(); final var journal = new WeaverJournal(storage);
        final var root = new EntityRef(UUID.randomUUID()); final var source = new RewardSource.Entity(root.entityId());
        final UUID world = UUID.randomUUID(); final var recipient = new RewardSource.Player(UUID.randomUUID());
        final Set<RewardSource> targets = Set.of(recipient, new RewardSource.Entity(UUID.randomUUID()), new RewardSource.Item(UUID.randomUUID()),
                new RewardSource.Event("fixture.effect", UUID.randomUUID()), new RewardSource.World(UUID.randomUUID()), new RewardSource.Location(world, 1.5, 2.5, 3.5));
        final var context = new GameplayEffectContext(List.of(source), targets, 0);
        check(!await(journal.prepareDerivedEffect(context)).claim(), "unloaded journal allowed effect");
        check(!await(GameplayEffectGate.prepare(context)).claim(), "unbound policy allowed effect");
        await(journal.load());
        final var clean = await(journal.prepareDerivedEffect(context));
        check(clean.claim() && !clean.claim() && storage.writes == 0, "clean path must be single-use without a storage write");
        final var stale = await(journal.prepareDerivedEffect(context));
        final var operation = operation(root, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX); await(journal.prepare(operation));
        check(!stale.claim(), "previously clean permit ignored new source uncertainty");
        check(!await(journal.prepareDerivedEffect(context)).claim() && journal.snapshot().influences().isEmpty(), "PREPARED origin invented an applied child");
        final var applied = await(journal.applied(operation.operationId(), 0, receipt(operation), 2));
        await(journal.finishAudit(operation.operationId(), applied.revision()));
        final var before = journal.snapshot(); final long started = System.currentTimeMillis();
        check(!await(journal.prepareDerivedEffect(new GameplayEffectContext(List.of(source), Set.of(recipient), 60_000))).claim(), "lingering player effect used wall time instead of observed lifetime");
        final var permit = await(journal.prepareDerivedEffect(context));
        final var after = journal.snapshot();
        check(after.influences().size() == before.influences().size() + targets.size() && storage.state.equals(after), "permit preceded durable all-target publication");
        check(after.operations().equals(before.operations()) && after.receipts().equals(before.receipts()) && after.effectDeltas().equals(before.effectDeltas()), "propagation changed canonical operation/receipt history");
        check(permit.claim() && !permit.claim(), "tainted effect not single-use");
        final var policy = new InfluenceRewardEligibilityPolicy(new WeaverInfluenceLookup(journal, System::currentTimeMillis));
        for (final var target : targets) for (final var channel : RewardChannel.values())
            check(!policy.evaluate(new RewardContext(channel, UUID.randomUUID(), List.of(target))).allowed(), "derived target/channel reward leak");
        final var playerEvidence = after.influences().values().stream().filter(v -> v.target().source().equals(recipient)).findFirst().orElseThrow();
        check(playerEvidence.quarantinedUntil() >= started + context.durationMillis() + PlayerQuarantine.MINIMUM_TAIL_MILLIS, "effect duration shortened player tail");
        check(await(journal.prepareDerivedEffect(context)).claim() && journal.snapshot().influences().size() == after.influences().size(), "retry duplicated origin/target evidence");
        final var alternate = new GameplayEffectContext(List.of(source), Set.of(new RewardSource.Location(world, 1.9, 2.1, 3.8)), 0);
        check(await(journal.prepareDerivedEffect(alternate)).claim() && journal.snapshot().influences().size() == after.influences().size(), "same block fractional coordinates multiplied lineage");
        final var child = new RewardSource.Entity(UUID.randomUUID());
        check(await(journal.prepareDerivedEffect(new GameplayEffectContext(List.of(recipient), Set.of(child), 60_000))).claim(), "monotonic second-generation propagation refused");
        check(journal.influenceIndex().trace(List.of(child), System.currentTimeMillis()).origins().equals(journal.influenceIndex().trace(List.of(source), System.currentTimeMillis()).origins()), "transitive propagation lost original developer lineage");
        apply(journal, operation(root, Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM), WeaverEffectCommit.none());
        check(!policy.evaluate(new RewardContext(RewardChannel.QUEST_REWARD, UUID.randomUUID(), List.of(child))).allowed(), "LIVE_GM washed a derived entity");
        final var codec = new WeaverJournalCodec(types());
        final var path = Files.createTempDirectory("weaver-derived-influence");
        final var yaml = new YamlWeaverJournalStorage(path.toFile(), codec, java.util.logging.Logger.getLogger("fixture"));
        yaml.writeState(journal.snapshot()); yaml.writeAudit(storage.audit);
        final var restart = new WeaverJournal(yaml); await(restart.load());
        check(restart.snapshot().equals(journal.snapshot()) && restart.influenceIndex().quarantined(child, System.currentTimeMillis()), "real YAML restart lost derived lineage");
        await(restart.close());
        final var shutdown = await(journal.prepareDerivedEffect(context)); await(journal.close());
        check(!shutdown.claim(), "shutdown permit remained usable");
        failures(); publicationFence(); slowAcknowledgement(); bindings(); freshSources(); acknowledgedInstantReuse();
        System.out.println("Derived influence propagation passed: " + assertions + " assertions; durable all-scope targets, transitive origin, no receipt mutation, one-use admission, publication race, crash uncertainty and real YAML restart.");
    }
    private static void acknowledgedInstantReuse() throws Exception {
        final var storage = new Storage(); final var clock = new java.util.concurrent.atomic.AtomicLong(100L);
        final var journal = new WeaverJournal(storage, ignored -> { }, clock::get); await(journal.load());
        final var root = new EntityRef(UUID.randomUUID()); apply(journal, operation(root, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), WeaverEffectCommit.none());
        final var source = new RewardSource.Entity(root.entityId()); final var target = new RewardSource.Player(UUID.randomUUID());
        final var context = new GameplayEffectContext(List.of(source), Set.of(target), 0);
        check(await(journal.prepareDerivedEffect(context)).claim(), "first durable instant effect refused");
        final var acknowledged = journal.snapshot(); final int writes = storage.writes;
        clock.set(5000);
        final var immediate = journal.prepareDerivedEffect(context);
        check(immediate.toCompletableFuture().isDone() && await(immediate).claim(), "acknowledged instant effect queued another write");
        check(storage.writes == writes && journal.snapshot().equals(acknowledged), "instant reuse rewrote durable history or tail");
        final var deadline = await(journal.prepareDerivedEffect(context)); clock.set(5101);
        check(!deadline.claim(), "reused permit spent the mandatory post-effect tail");
        check(await(journal.prepareDerivedEffect(context)).claim() && storage.writes == writes + 1, "expired admission did not renew durable tail");
        final var newTarget = new RewardSource.Player(UUID.randomUUID());
        check(await(journal.prepareDerivedEffect(new GameplayEffectContext(List.of(source), Set.of(target, newTarget), 0))).claim()
                && journal.influenceIndex().quarantined(newTarget, clock.get()), "partial target coverage admitted missing lineage");
        final var old = await(journal.prepareDerivedEffect(context));
        apply(journal, operation(root, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), WeaverEffectCommit.none());
        check(!old.claim(), "new source origin crossed reused permit admission");
        check(await(journal.prepareDerivedEffect(context)).claim(), "new source origin could not be durably propagated");
        try (var binding = GameplayEffectGate.install(journal::prepareDerivedEffect)) {
            final var reuse = await(GameplayEffectGate.prepare(context));
            try (var fence = GameplayEffectGate.fence(target).orElseThrow()) {
                check(!reuse.claim(), "instant reuse bypassed owner observation fence");
            }
        }
        check(!await(journal.prepareDerivedEffect(new GameplayEffectContext(List.of(source), Set.of(target), 1000))).claim(),
                "instant reuse admitted lingering effect without observer");
        final var child = new RewardSource.Entity(UUID.randomUUID());
        check(await(journal.prepareDerivedEffect(new GameplayEffectContext(List.of(source), Set.of(child), 0))).claim(), "monotonic child preparation refused");
        final int beforeMonotonic = storage.writes; clock.addAndGet(1_000_000);
        final var monotonic = journal.prepareDerivedEffect(new GameplayEffectContext(List.of(source), Set.of(child), 0));
        check(monotonic.toCompletableFuture().isDone() && await(monotonic).claim() && storage.writes == beforeMonotonic,
                "monotonic child evidence acquired an expiry or redundant write");
        final var closing = await(journal.prepareDerivedEffect(new GameplayEffectContext(List.of(source), Set.of(child), 0)));
        await(journal.close()); check(!closing.claim(), "shutdown admitted acknowledged instant permit");
    }
    private static void failures() throws Exception {
        for (boolean afterWrite : List.of(false, true)) {
            final var storage = new Storage(); final var journal = new WeaverJournal(storage); await(journal.load());
            final var subject = new EntityRef(UUID.randomUUID()); apply(journal, operation(subject, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), WeaverEffectCommit.none());
            final var child = new RewardSource.Entity(UUID.randomUUID()); storage.failWrite = storage.writes + 1; storage.afterWrite = afterWrite;
            final var context = new GameplayEffectContext(List.of(new RewardSource.Entity(subject.entityId())), Set.of(child), 0);
            check(!await(journal.prepareDerivedEffect(context)).claim() && !journal.ready(), "ambiguous persistence admitted native effect");
            fails(journal.close()); storage.failWrite = 0;
            final var recovered = new WeaverJournal(storage); await(recovered.load());
            check(recovered.influenceIndex().quarantined(child, System.currentTimeMillis()) == afterWrite, "before/after durable child boundary");
            check(await(recovered.prepareDerivedEffect(context)).claim() && recovered.snapshot().influences().size() == 2, "restart retry duplicated or lost child taint");
            await(recovered.close());
        }
    }
    private static void publicationFence() throws Exception {
        final var delegate = new Storage(); final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1);
        final var pause = new java.util.concurrent.atomic.AtomicBoolean();
        final WeaverJournalStorage storage = new WeaverJournalStorage() {
            public WeaverJournalState readState() { return delegate.state; }
            public Map<String, WeaverAuditEntry> readAudit() { return delegate.audit; }
            public void writeAudit(Map<String, WeaverAuditEntry> value) throws Exception { delegate.writeAudit(value); }
            public void writeState(WeaverJournalState state) throws Exception {
                if (pause.get()) { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test IO timeout"); }
                delegate.writeState(state);
            }
        };
        final var journal = new WeaverJournal(storage); await(journal.load()); final var root = new EntityRef(UUID.randomUUID());
        apply(journal, operation(root, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), WeaverEffectCommit.none());
        final var child = new RewardSource.Entity(UUID.randomUUID()); pause.set(true);
        final var future = journal.prepareDerivedEffect(new GameplayEffectContext(List.of(new RewardSource.Entity(root.entityId())), Set.of(child), 0));
        try {
            check(entered.await(5, TimeUnit.SECONDS), "storage boundary not reached");
            check(!future.toCompletableFuture().isDone() && !journal.influenceIndex().quarantined(child, System.currentTimeMillis()), "derived effect escaped fsync publication fence");
        } finally { release.countDown(); }
        check(await(future).claim() && journal.influenceIndex().quarantined(child, System.currentTimeMillis()), "acknowledged lineage not published");
        await(journal.close());
    }
    private static void bindings() throws Exception {
        final var context = new GameplayEffectContext(List.of(new RewardSource.Entity(UUID.randomUUID())), Set.of(new RewardSource.Player(UUID.randomUUID())), 0);
        final var first = GameplayEffectGate.install(value -> CompletableFuture.completedFuture(GameplayEffectPermit.guarded(() -> true)));
        final var permit = await(GameplayEffectGate.prepare(context)); first.close();
        check(!permit.claim(), "retired policy permit admitted effect");
        final var second = GameplayEffectGate.install(value -> CompletableFuture.completedFuture(GameplayEffectPermit.guarded(() -> true)));
        first.close(); check(await(GameplayEffectGate.prepare(context)).claim(), "old binding retired new policy"); second.close();
        final var bad = GameplayEffectGate.install(value -> { throw new LinkageError("fixture"); });
        check(!await(GameplayEffectGate.prepare(context)).claim(), "failed policy allowed effect"); bad.close();
        check(!await(GameplayEffectGate.prepare(context)).claim(), "unbound policy failed open");
    }
    private static void slowAcknowledgement() throws Exception {
        final var delegate = new Storage(); final var clock = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
        final var slow = new java.util.concurrent.atomic.AtomicBoolean();
        final WeaverJournalStorage storage = new WeaverJournalStorage() {
            public WeaverJournalState readState() { return delegate.state; }
            public Map<String, WeaverAuditEntry> readAudit() { return delegate.audit; }
            public void writeAudit(Map<String, WeaverAuditEntry> value) throws Exception { delegate.writeAudit(value); }
            public void writeState(WeaverJournalState value) throws Exception { delegate.writeState(value); if (slow.get()) clock.addAndGet(6000); }
        };
        final var journal = new WeaverJournal(storage, projection -> { throw new AssertionError("not a projection"); }, clock::get);
        await(journal.load()); final var root = new EntityRef(UUID.randomUUID());
        apply(journal, operation(root, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), WeaverEffectCommit.none());
        slow.set(true); final var target = new RewardSource.Player(UUID.randomUUID());
        final var permit = await(journal.prepareDerivedEffect(new GameplayEffectContext(List.of(new RewardSource.Entity(root.entityId())), Set.of(target), 0)));
        check(!permit.claim() && journal.ready(), "slow fsync spent the tail before an effect was admitted");
        check(journal.influenceIndex().quarantined(target, clock.get()), "slow acknowledgement removed conservative evidence");
        await(journal.close());
    }
    private static void freshSources() throws Exception {
        final var journal = new WeaverJournal(new Storage(), ignored -> { }, () -> 100L); await(journal.load());
        final var entity = new RewardSource.Entity(UUID.randomUUID()); final var destination = new RewardSource.World(UUID.randomUUID());
        final var context = new GameplayEffectContext(List.of(entity), Set.of(entity), 0);
        // The source was already tainted; the native owner enters it only after preparing its permit.
        final var worldProjection = operation(new WorldRef(destination.id()), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
        await(journal.prepare(worldProjection));
        final var applied = await(journal.applied(worldProjection.operationId(), 0, receipt(worldProjection), 2));
        await(journal.finishAudit(worldProjection.operationId(), applied.revision()));
        try (final var binding = GameplayEffectGate.install(journal::prepareDerivedEffect)) {
            final var old = await(GameplayEffectGate.prepare(context));
            check(!old.claim(List.of(entity, destination)) && !old.claim(), "movement into a tainted source bypassed final owner capture");
            final var prepared = operation(new WorldRef(UUID.randomUUID()), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX); await(journal.prepare(prepared));
            final var uncertain = await(GameplayEffectGate.prepare(context));
            check(!uncertain.claim(List.of(new RewardSource.World(((WorldRef) prepared.subject()).worldId()))), "fresh PREPARED source ignored");
            final var safe = await(GameplayEffectGate.prepare(context));
            check(safe.claim(List.of(entity)) && !safe.claim(List.of(entity)), "unchanged fresh source cannot claim once");
            final var parent = new RewardSource.Entity(UUID.randomUUID());
            final var root = operation(new EntityRef(parent.id()), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            apply(journal, root, WeaverEffectCommit.none());
            final var lineage = await(GameplayEffectGate.prepare(new GameplayEffectContext(List.of(parent), Set.of(entity), 0)));
            check(lineage.claim(List.of(entity, parent)), "already admitted original lineage rejected on fresh capture");
            final var newOrigin = new RewardSource.Entity(UUID.randomUUID());
            final var delayed = await(GameplayEffectGate.prepare(new GameplayEffectContext(List.of(parent), Set.of(entity), 0)));
            apply(journal, operation(new EntityRef(newOrigin.id()), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), WeaverEffectCommit.none());
            check(!delayed.claim(List.of(entity, newOrigin)), "new canonical parent origin bypassed prepared lineage");
            final var oversized = GameplayEffectPermit.guardedSources(values -> true);
            check(!oversized.claim(java.util.Collections.nCopies(65, entity)), "fresh source cap ignored");
            final var immutable = GameplayEffectPermit.guardedSources(values -> { values.clear(); return true; });
            check(!immutable.claim(new ArrayList<>(List.of(entity))), "fresh capture list remained mutable");
        }
        await(journal.close());
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
