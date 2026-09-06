package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.OperationRecoveryPayload;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.*;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverTypeCompatibilityRegressionSuite.rejects;

public final class WeaverProjectionRegressionSuite {
    static final WeaverTypeId INT = WeaverTypeId.parse("weaver:int@1");
    static class Provider extends WeaverContractRegressionSuite.FixtureProvider implements WeaverProjectionProvider {
        Provider() {
            super("fixture", WeaverContractRegressionSuite.action("fixture", RiskLevel.MUTATING, Set.of(Lifetime.SESSION, Lifetime.PERSISTENT),
                    Set.of(IntegrityMode.SANDBOX, IntegrityMode.LIVE_GM), Set.of(IntegrityImpact.TAINT_SUBJECT), true, 1), CoverageLevel.FULL_PROVIDER, Map.of("fixture.action", "fixture.assess"));
        }
        @Override public List<ProjectionConsumerDescriptor> projectionConsumers() {
            return List.of(new ProjectionConsumerDescriptor("fixture.combat", "fixture", "fixture.Combat#resolve", Set.of("fixture.action"), Set.of(WeaverSubjectKind.PLAYER),
                    Map.of("fixture.rank", INT), Set.of("fixture.loot", "fixture.history")));
        }
    }
    static WeaverOperationRecord operation(final SubjectRef subject, final Lifetime lifetime, final IntegrityMode mode) {
        return new WeaverOperationRecord(UUID.randomUUID(), HiddenDevAuthority.PRIMARY_DEVELOPER, "fixture", new ActionRequest("fixture.action", Map.of(), lifetime, mode),
                subject, "before", Optional.empty(), new OperationRecoveryPayload(1, Map.of()), OperationStatus.PREPARED, 0, 1, 1, Optional.empty(), false);
    }
    static WeaverProjection projection(final WeaverOperationRecord operation, final long sequence, final int rank, final OptionalLong expiry) {
        return new WeaverProjection(UUID.randomUUID(), sequence, "fixture", "fixture.action", operation.subject(), operation.request().lifetime(),
                new DeveloperInfluence(operation.operationId(), operation.request().integrityMode(), "fixture.action", operation.actorId(), 2),
                Map.of("fixture.rank", new WeaverValue(INT, Map.of("value", rank), "fixture", "fixture.state", Set.of(), 2)), "before", 2, expiry);
    }
    static WeaverEffectCommit effect(final WeaverProjection projection) { return new WeaverEffectCommit(List.of(projection), Set.of(), List.of(), Optional.empty()); }
    static WeaverOperationRecord apply(final WeaverJournal journal, final WeaverOperationRecord operation, final WeaverEffectCommit effects) throws Exception {
        await(journal.prepare(operation)); final var applied = await(journal.applied(operation.operationId(), 0, receipt(operation), effects, 2));
        return await(journal.finishAudit(applied.operationId(), applied.revision()));
    }
    static WeaverTypeRegistry types() { final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types); return types; }
    public static void main(final String[] args) throws Exception {
        final var providers = WeaverContractRegressionSuite.registry(new Provider()); providers.freezeAndValidate();
        final var consumers = providers.projectionConsumers();
        check(consumers.snapshot().size() == 1, "adapter consumer registration absent");
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage, consumers::validate); await(journal.load());
        final JournalProjectionSource source = new JournalProjectionSource(journal, consumers); final PlayerRef subject = new PlayerRef(UUID.randomUUID());
        final var first = operation(subject, Lifetime.PERSISTENT, IntegrityMode.SANDBOX); final var one = projection(first, 1, 3, OptionalLong.empty());
        apply(journal, first, effect(one));
        final var second = operation(subject, Lifetime.SESSION, IntegrityMode.LIVE_GM); final var two = projection(second, 2, 9, OptionalLong.of(200));
        apply(journal, second, effect(two));
        check(((Number) source.scalar("fixture.combat", subject, "fixture.rank", 100).orElseThrow().payload().get("value")).intValue() == 9, "highest sequence did not win");
        check(((Number) source.scalar("fixture.combat", subject, "fixture.rank", 200).orElseThrow().payload().get("value")).intValue() == 3, "expiry did not recompute effective state");
        check(source.scalar("fixture.combat", subject, "fixture.private", 100).isEmpty(), "undeclared field exposed");
        rejects(() -> source.active("fixture.loot", subject, 100));
        final WeaverJournalCodec codec = new WeaverJournalCodec(types());
        check(codec.decodeState(codec.encodeState(journal.snapshot())).equals(journal.snapshot()), "projection/influence codec round trip");
        final var directory = java.nio.file.Files.createTempDirectory("weaver-effects-regression");
        final var yaml = new YamlWeaverJournalStorage(directory.toFile(), codec, java.util.logging.Logger.getLogger("fixture"));
        yaml.writeState(journal.snapshot()); check(yaml.readState().equals(journal.snapshot()), "real effect YAML round trip");
        final var lookup = new WeaverInfluenceLookup(journal, () -> Long.MAX_VALUE - 1_000_000);
        check(lookup.recipient(subject.playerId()) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "LIVE_GM washed active sandbox projection");
        check(await(journal.expireProjections(100, true)) == 1 && journal.snapshot().projections().containsKey(one.projectionId()), "session cleanup removed persistent projection");
        final var remove = operation(subject, Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM);
        apply(journal, remove, new WeaverEffectCommit(List.of(), Set.of(one.projectionId()), List.of(), Optional.empty()));
        check(source.active("fixture.combat", subject, 200).isEmpty(), "sever did not restore canonical fallback");
        final var tail = new WeaverInfluenceLookup(journal, () -> 300_001);
        check(tail.recipient(subject.playerId()) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "sever lost minimum five-minute player quarantine");
        check(new WeaverInfluenceLookup(journal, () -> 300_002).recipient(subject.playerId()) == InfluenceRewardEligibilityPolicy.Evidence.CLEAN, "ended player quarantine never expires");
        final var invalid = operation(subject, Lifetime.PERSISTENT, IntegrityMode.SANDBOX); await(journal.prepare(invalid));
        final long revision = journal.snapshot().revision();
        fails(journal.applied(invalid.operationId(), 0, receipt(invalid), effect(projection(invalid, 2, 99, OptionalLong.empty())), 2));
        check(journal.snapshot().revision() == revision && journal.ready(), "stale projection sequence changed durable state");
        await(journal.resolve(invalid.operationId(), 0, OperationStatus.ABORTED, 3));
        final WeaverJournal rejected = new WeaverJournal(yaml); fails(rejected.load()); check(!rejected.ready(), "projection loaded without registered runtime consumer"); await(rejected.close());
        await(journal.close());
        final WeaverJournal restarted = new WeaverJournal(storage, consumers::validate); await(restarted.load());
        check(restarted.snapshot().projections().isEmpty() && restarted.snapshot().projectionSequence() == 2, "restart resurrected severed projection or reused sequence"); await(restarted.close());
        precedenceByMember(); caps(consumers);
        System.out.println("Weaver projection passed: adapter consumers, scalar/member precedence, expiry/session/sever, monotonic sequence, caps and real YAML persistence.");
    }
    private static void precedenceByMember() {
        final PlayerRef subject = new PlayerRef(UUID.randomUUID());
        final var a = projection(operation(subject, Lifetime.PERSISTENT, IntegrityMode.SANDBOX), 1, 1, OptionalLong.empty());
        final var b = projection(operation(subject, Lifetime.PERSISTENT, IntegrityMode.SANDBOX), 2, -1, OptionalLong.empty());
        final WeaverProjectionSource source = (consumer, target, now) -> List.of(b, a);
        check(((Number) source.latestPerMember("fixture.combat", subject, Set.of("fixture.rank"), value -> "member", 2).get("member").payload().get("value")).intValue() == -1,
                "capability removal did not override earlier addition");
    }
    private static void caps(final ProjectionConsumerRegistry consumers) throws Exception {
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage, consumers::validate); await(journal.load());
        final PlayerRef subject = new PlayerRef(UUID.randomUUID());
        for (int i = 1; i <= 32; i++) { final var operation = operation(subject, Lifetime.PERSISTENT, IntegrityMode.SANDBOX); apply(journal, operation, effect(projection(operation, i, i, OptionalLong.empty()))); }
        final var overflow = operation(subject, Lifetime.PERSISTENT, IntegrityMode.SANDBOX); fails(journal.prepare(overflow));
        check(journal.snapshot().projections().size() == 32 && !journal.snapshot().operations().containsKey(overflow.operationId()), "subject cap did not refuse before any mutation admission");
        await(journal.close());
        for (final Lifetime lifetime : List.of(Lifetime.SESSION, Lifetime.PERSISTENT)) {
            final int cap = lifetime == Lifetime.SESSION ? 256 : 1024;
            final Storage full = new Storage(); full.state = atCapacity(cap, lifetime);
            final WeaverJournal capped = new WeaverJournal(full, consumers::validate); await(capped.load());
            fails(capped.prepare(operation(new PlayerRef(UUID.randomUUID()), lifetime, IntegrityMode.SANDBOX)));
            check(capped.snapshot().projections().size() == cap, "global lifetime cap changed existing projections");
            rejects(() -> atCapacity(cap + 1, lifetime)); await(capped.close());
        }
    }
    private static WeaverJournalState atCapacity(final int count, final Lifetime lifetime) {
        final Map<UUID, WeaverOperationRecord> operations = new HashMap<>(); final Map<UUID, WeaverReceipt> receipts = new HashMap<>();
        final Map<UUID, WeaverProjection> projections = new HashMap<>(); final Map<UUID, WeaverInfluenceRecord> influences = new HashMap<>();
        for (int i = 1; i <= count; i++) {
            final var prepared = operation(new PlayerRef(UUID.randomUUID()), lifetime, IntegrityMode.SANDBOX); final var receipt = receipt(prepared);
            final var operation = new WeaverOperationRecord(prepared.operationId(), prepared.actorId(), prepared.providerId(), prepared.request(), prepared.subject(), "before", Optional.of("after"),
                    prepared.recoveryPayload(), OperationStatus.COMMITTED, 2, 1, 2, Optional.of(receipt), false);
            final var projection = projection(operation, i, i, OptionalLong.empty()); final var influence = WeaverInfluenceRecord.applied(projection.influence(), WeaverInfluenceTarget.subject(projection.subject()), true);
            operations.put(operation.operationId(), operation); receipts.put(receipt.receiptId(), receipt); projections.put(projection.projectionId(), projection); influences.put(influence.id(), influence);
        }
        return new WeaverJournalState(count * 3L, operations, receipts, count, Map.of(), projections, influences);
    }
}
