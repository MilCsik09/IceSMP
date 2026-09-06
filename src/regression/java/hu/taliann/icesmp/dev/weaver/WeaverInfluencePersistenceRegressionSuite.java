package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryCoordinator;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverProjectionRegressionSuite.*;

public final class WeaverInfluencePersistenceRegressionSuite {
    public static void main(final String[] args) throws Exception {
        final UUID world = UUID.randomUUID(), player = UUID.randomUUID();
        final EntityRef subject = new EntityRef(UUID.randomUUID());
        final AreaRef area = new AreaRef(world, new CuboidArea(new AreaBounds(-3, 0, -3, 3, 20, 3)));
        final List<RewardSource> sources = List.of(new RewardSource.Entity(subject.entityId()), new RewardSource.Player(player), new RewardSource.Item(UUID.randomUUID()),
                new RewardSource.Event("fixture.event", UUID.randomUUID()), new RewardSource.Location(world, -2.5, 10.5, -2.5), new RewardSource.World(UUID.randomUUID()));
        final Set<WeaverInfluenceTarget> targets = new HashSet<>();
        sources.stream().filter(source -> !(source instanceof RewardSource.Location)).forEach(source -> targets.add(WeaverInfluenceTarget.exact(source)));
        targets.add(WeaverInfluenceTarget.spatial(area));
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage);
        final WeaverInfluenceLookup lookup = new WeaverInfluenceLookup(journal, () -> 10);
        check(lookup.source(sources.getFirst()) == InfluenceRewardEligibilityPolicy.Evidence.UNAVAILABLE, "unloaded store treated as clean");
        await(journal.load()); final var prepared = operation(subject, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
        await(journal.prepare(prepared, new WeaverEffectIntent(targets)));
        check(journal.snapshot().influences().isEmpty(), "PREPARED invented an applied influence");
        final InfluenceRewardEligibilityPolicy policy = new InfluenceRewardEligibilityPolicy(lookup); final UUID cleanRecipient = UUID.randomUUID();
        int blocked = 0;
        for (final RewardSource source : sources) for (final RewardChannel channel : RewardChannel.values()) {
            check(!policy.evaluate(new RewardContext(channel, cleanRecipient, List.of(source))).allowed(), "PREPARED source/channel reward leak"); blocked++;
        }
        check(lookup.source(new RewardSource.Entity(player)) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "player/entity identity alias bypassed quarantine");
        check(lookup.source(new RewardSource.Location(world, 4, 10, 4)) == InfluenceRewardEligibilityPolicy.Evidence.CLEAN, "bounded area tainted unrelated location");
        final var codec = new WeaverJournalCodec(types());
        check(codec.decodeState(codec.encodeState(journal.snapshot())).equals(journal.snapshot()), "all-scope intent round trip");
        final var applied = await(journal.applied(prepared.operationId(), 0, receipt(prepared), 2));
        check(journal.snapshot().intents().isEmpty() && journal.snapshot().influences().size() == targets.size(), "APPLIED did not atomically replace intent with all influences");
        check(codec.decodeState(codec.encodeState(journal.snapshot())).equals(journal.snapshot()), "all-scope influence round trip");
        await(journal.resolve(applied.operationId(), applied.revision(), OperationStatus.COMPENSATED, 100));
        final var longAfter = new WeaverInfluenceLookup(journal, () -> 400_000);
        for (final int index : List.of(0, 2, 3)) check(longAfter.source(sources.get(index)) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "compensation washed monotonic entity/item/event taint");
        check(new WeaverInfluenceLookup(journal, () -> 300_099).recipient(player) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "compensation lost quarantine tail");
        check(longAfter.recipient(player) == InfluenceRewardEligibilityPolicy.Evidence.CLEAN, "ended one-shot player quarantine did not expire");
        apply(journal, operation(subject, Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM), WeaverEffectCommit.none());
        check(longAfter.source(sources.getFirst()) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "LIVE_GM washed monotonic source");
        final var unresolved = operation(new PlayerRef(UUID.randomUUID()), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX); await(journal.prepare(unresolved));
        await(journal.resolve(unresolved.operationId(), 0, OperationStatus.NEEDS_REVIEW, 3));
        check(longAfter.recipient(((PlayerRef) unresolved.subject()).playerId()) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "NEEDS_REVIEW prematurely released pending quarantine");
        final var aborted = operation(new PlayerRef(UUID.randomUUID()), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX); await(journal.prepare(aborted));
        await(journal.resolve(aborted.operationId(), 0, OperationStatus.ABORTED, 3));
        check(lookup.recipient(((PlayerRef) aborted.subject()).playerId()) == InfluenceRewardEligibilityPolicy.Evidence.CLEAN, "observed BEFORE did not release unused intent");
        await(journal.close());
        check(lookup.source(sources.getFirst()) == InfluenceRewardEligibilityPolicy.Evidence.UNAVAILABLE, "shutdown failed open");
        failures(); migration(); recoveryEffects();
        System.out.println("Weaver influence persistence passed: " + blocked + " durable PREPARED source/channel denials, atomic effects, compensation tails, eight crash boundaries, migration and observed projection recovery.");
    }
    private static void failures() throws Exception {
        final var providers = WeaverContractRegressionSuite.registry(new Provider()); providers.freezeAndValidate();
        for (int boundary = 1; boundary <= 4; boundary++) for (final boolean afterWrite : List.of(false, true)) {
            final Storage storage = new Storage(); storage.failWrite = boundary; storage.afterWrite = afterWrite;
            final WeaverJournal journal = new WeaverJournal(storage, providers.projectionConsumers()::validate); await(journal.load());
            final PlayerRef subject = new PlayerRef(UUID.randomUUID()); final var operation = operation(subject, Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
            try { apply(journal, operation, effect(projection(operation, 1, 5, OptionalLong.empty()))); throw new AssertionError("Missing injected effect failure"); }
            catch (final java.util.concurrent.ExecutionException expected) { }
            check(new WeaverInfluenceLookup(journal, () -> 3).recipient(subject.playerId()) == InfluenceRewardEligibilityPolicy.Evidence.UNAVAILABLE, "ambiguous write allowed rewards");
            fails(journal.close()); storage.failWrite = 0;
            final WeaverJournal restarted = new WeaverJournal(storage, providers.projectionConsumers()::validate); await(restarted.load());
            final var state = restarted.snapshot(); final boolean durableApplied = boundary > 2 || boundary == 2 && afterWrite;
            check((state.projections().size() == 1) == durableApplied && (state.receipts().size() == 1) == durableApplied && (state.influences().size() == 1) == durableApplied,
                    "receipt/projection/influence were not atomic across crash");
            if (boundary != 1 || afterWrite) check(new WeaverInfluenceLookup(restarted, () -> 1_000_000).recipient(subject.playerId()) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED,
                    "restart lost prepared or applied quarantine");
            await(restarted.close());
        }
    }
    private static void migration() throws Exception {
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage); await(journal.load());
        final var operation = operation(new EntityRef(UUID.randomUUID()), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX); await(journal.prepare(operation));
        final var codec = new WeaverJournalCodec(types()); final Map<String, Object> legacy = new HashMap<>(codec.encodeState(journal.snapshot()));
        for (final String field : List.of("projection-sequence", "projections", "influences", "intents")) legacy.remove(field); legacy.put("schema-version", 1);
        final var migrated = codec.decodeState(legacy);
        check(migrated.intents().containsKey(operation.operationId()) && new WeaverInfluenceIndex(migrated).quarantined(WeaverInfluenceTarget.subject(operation.subject()).source(), Long.MAX_VALUE),
                "schema 1 PREPARED migration lost uncertainty quarantine");
        check(codec.decodeState(codec.encodeState(migrated)).equals(migrated), "schema migration not stable after next write"); await(journal.close());
    }
    private static void recoveryEffects() throws Exception {
        for (final boolean evidencePresent : List.of(false, true)) {
            final PlayerRef subject = new PlayerRef(UUID.randomUUID()); final var operation = operation(subject, Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
            final var recovered = effect(projection(operation, 1, 8, OptionalLong.empty()));
            final Provider provider = new Provider() {
                @Override public RecoveryAssessment assessRecovery(final RecoveryContext context, final SubjectSnapshot snapshot, final WeaverOperationRecord record) {
                    context.authority().require(record);
                    return new RecoveryAssessment(ObservedOperationState.APPLIED, false, Optional.of(receipt(record)), "observed", evidencePresent ? Optional.of(recovered) : Optional.empty());
                }
            };
            final var providers = WeaverContractRegressionSuite.registry(provider); providers.freezeAndValidate();
            final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage, providers.projectionConsumers()::validate); await(journal.load()); await(journal.prepare(operation));
            final WeaverRecoveryCoordinator recovery = new WeaverRecoveryCoordinator(journal, (actor, reference) -> CompletableFuture.completedFuture(new SubjectSnapshot(reference, 3, "after", Map.of())), providers, types());
            await(recovery.start());
            check(journal.snapshot().operations().get(operation.operationId()).status() == (evidencePresent ? OperationStatus.COMMITTED : OperationStatus.NEEDS_REVIEW), "persistent recovery accepted missing effect evidence");
            check(journal.snapshot().projections().size() == (evidencePresent ? 1 : 0), "observed recovery did not materialize exact effect state");
            recovery.close(); await(journal.close());
        }
    }
}
