package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.OperationRecoveryPayload;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceLookup;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.InfluenceRewardEligibilityPolicy;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverTypeCompatibilityRegressionSuite.rejects;

/** Removed content/schema/provider must not erase influence or disable unrelated journal writers. */
public final class WeaverStoredContentIsolationRegressionSuite {
    private static final WeaverTypeId CONTENT = WeaverTypeId.parse("fixture:registry_content@1");
    private static final String PROVIDER = "catalogstate";
    private static final class Provider extends WeaverContractRegressionSuite.FixtureProvider implements WeaverProjectionProvider {
        Provider() {
            super(PROVIDER, WeaverContractRegressionSuite.action(PROVIDER, RiskLevel.MUTATING,
                    Set.of(Lifetime.PERSISTENT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT), true, 1),
                    CoverageLevel.FULL_PROVIDER, Map.of(PROVIDER + ".action", PROVIDER + ".assess"));
        }
        @Override public List<ProjectionConsumerDescriptor> projectionConsumers() {
            return List.of(new ProjectionConsumerDescriptor(PROVIDER + ".combat", PROVIDER, "fixture.Combat#resolve",
                    Set.of(PROVIDER + ".action"), Set.of(WeaverSubjectKind.PLAYER), Map.of(PROVIDER + ".content", CONTENT), Set.of(PROVIDER + ".loot")));
        }
    }
    private record Registry(WeaverTypeRegistry types, WorldWeaverProviderRegistry providers) { }
    private static Registry registry(AtomicBoolean present, AtomicBoolean broken, AtomicInteger reads, boolean installProvider) {
        final var types = WeaverProjectionRegressionSuite.types();
        if (installProvider) types.register(ScalarTypeCodec.reference(CONTENT, id -> {
            reads.incrementAndGet(); if (broken.get()) throw new LinkageError("private provider fault");
            return present.get() && id.equals("fixture_ability");
        }));
        final var providers = new WorldWeaverProviderRegistry(types, () -> 1L);
        providers.register(new WeaverProjectionRegressionSuite.Provider());
        if (installProvider) providers.register(new Provider());
        providers.freezeAndValidate(); return new Registry(types, providers);
    }
    public static void main(final String[] args) throws Exception {
        final AtomicBoolean present = new AtomicBoolean(true), broken = new AtomicBoolean(); final AtomicInteger reads = new AtomicInteger();
        final Registry registered = registry(present, broken, reads, true);
        final Path directory = Files.createTempDirectory("weaver-stored-content-");
        try {
            final var codec = new WeaverJournalCodec(registered.types());
            final var storage = new YamlWeaverJournalStorage(directory.toFile(), codec, java.util.logging.Logger.getLogger("fixture"));
            final var journal = new WeaverJournal(storage, registered.providers().projectionConsumers()::validate); await(journal.load());
            final PlayerRef subject = new PlayerRef(UUID.randomUUID());
            final WeaverValue value = new WeaverValue(CONTENT, Map.of("id", "fixture_ability"), PROVIDER, PROVIDER + ".state", Set.of("fixture.content"), 2);
            final var operation = new WeaverOperationRecord(UUID.randomUUID(), HiddenDevAuthority.PRIMARY_DEVELOPER, PROVIDER,
                    new ActionRequest(PROVIDER + ".action", Map.of("value", value), Lifetime.PERSISTENT, IntegrityMode.SANDBOX),
                    subject, "before", Optional.empty(), new OperationRecoveryPayload(1, Map.of()), OperationStatus.PREPARED, 0, 1, 1, Optional.empty(), false);
            final var projection = new WeaverProjection(UUID.randomUUID(), 1, PROVIDER, PROVIDER + ".action", subject, Lifetime.PERSISTENT,
                    new DeveloperInfluence(operation.operationId(), IntegrityMode.SANDBOX, PROVIDER + ".action", operation.actorId(), 2),
                    Map.of(PROVIDER + ".content", value), "before", 2, OptionalLong.empty());
            final var receipt = new WeaverReceipt(UUID.randomUUID(), operation.operationId(), PROVIDER, PROVIDER + ".action", subject,
                    RiskLevel.MUTATING, Lifetime.PERSISTENT, IntegrityMode.SANDBOX, "before", "after", Map.of("old", value), Map.of("new", value),
                    Optional.of(new UndoSpec(PROVIDER + ".undo", "after", Map.of("value", value))), 2, ReceiptStatus.COMMITTED);
            await(journal.prepare(operation)); await(journal.applied(operation.operationId(), 0, receipt, WeaverProjectionRegressionSuite.effect(projection), 2));
            await(journal.finishAudit(operation.operationId(), 1));
            final var initial = journal.snapshot();
            final var changedFields = new ProjectionConsumerRegistry(registered.types());
            changedFields.register(new ProjectionConsumerDescriptor(PROVIDER + ".combat", PROVIDER, "fixture.Combat#resolve",
                    Set.of(PROVIDER + ".action"), Set.of(WeaverSubjectKind.PLAYER), Map.of(PROVIDER + ".replacement", CONTENT), Set.of(PROVIDER + ".loot")));
            changedFields.freeze(registered.providers().actions());
            rejects(() -> new JournalProjectionSource(journal, changedFields).active(PROVIDER + ".combat", subject, 100));
            check(journal.snapshot().equals(initial), "missing consumer field changed durable evidence");
            await(journal.close());

            present.set(false); broken.set(true); reads.set(0);
            final var restarted = new WeaverJournal(storage, registered.providers().projectionConsumers()::validate); await(restarted.load());
            check(reads.get() == 0 && restarted.ready() && restarted.snapshot().equals(initial), "storage executed missing/broken provider code or discarded history");
            check(codec.decodeState(codec.encodeState(initial)).equals(initial) && reads.get() == 0, "historical values depend on live codecs");
            final var source = new JournalProjectionSource(restarted, registered.providers().projectionConsumers());
            for (int i = 0; i < 3; i++) rejects(() -> registered.providers().readConsumer(PROVIDER,
                    () -> source.active(PROVIDER + ".combat", subject, 100)));
            check(registered.providers().quarantined(PROVIDER), "broken runtime codec was not isolated by provider circuit breaker");
            check(!registered.providers().quarantined("fixture"), "unrelated provider was quarantined");
            check(new WeaverInfluenceLookup(restarted, () -> 100).recipient(subject.playerId())
                    == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "missing content erased influence");

            final var healthy = WeaverProjectionRegressionSuite.operation(subject, Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
            final var healthyProjection = WeaverProjectionRegressionSuite.projection(healthy, 2, 7, OptionalLong.empty());
            WeaverProjectionRegressionSuite.apply(restarted, healthy, WeaverProjectionRegressionSuite.effect(healthyProjection));
            check(registered.providers().readConsumer("fixture", () -> source.active("fixture.combat", subject, 100)).size() == 1,
                    "unrelated provider could not publish/read while old content was unavailable");
            check(restarted.snapshot().operations().get(operation.operationId()).equals(initial.operations().get(operation.operationId()))
                    && restarted.snapshot().projections().get(projection.projectionId()).equals(projection), "unrelated write rewrote historical evidence");
            final var durable = restarted.snapshot(); await(restarted.close());

            // Provider and its type schema may be absent on the next startup. Keep bounded opaque
            // values and influence, and require a current consumer before exposing any effect.
            final Registry absent = registry(present, broken, reads, false);
            final var absentCodec = new WeaverJournalCodec(absent.types());
            final var absentStorage = new YamlWeaverJournalStorage(directory.toFile(), absentCodec, java.util.logging.Logger.getLogger("fixture"));
            final var absentJournal = new WeaverJournal(absentStorage, absent.providers().projectionConsumers()::validate); await(absentJournal.load());
            check(absentJournal.snapshot().equals(durable) && !absent.types().contains(CONTENT), "absent type/provider broke journal load");
            final var absentSource = new JournalProjectionSource(absentJournal, absent.providers().projectionConsumers());
            rejects(() -> absentSource.active(PROVIDER + ".combat", subject, 100));
            rejects(() -> absent.types().validate(value));
            check(absentSource.active("fixture.combat", subject, 100).size() == 1, "opaque schema blocked healthy consumer");
            check(new WeaverInfluenceLookup(absentJournal, () -> 100).recipient(subject.playerId())
                    == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "absent provider washed quarantine");
            final var extra = WeaverProjectionRegressionSuite.operation(new PlayerRef(UUID.randomUUID()), Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM);
            WeaverProjectionRegressionSuite.apply(absentJournal, extra, WeaverEffectCommit.none());
            await(absentJournal.close());

            // Explicit restart is the circuit breaker's recovery path; canonical content returns.
            present.set(true); broken.set(false);
            final Registry recovered = registry(present, broken, reads, true);
            final var recoveredJournal = new WeaverJournal(storage, recovered.providers().projectionConsumers()::validate); await(recoveredJournal.load());
            final var recoveredSource = new JournalProjectionSource(recoveredJournal, recovered.providers().projectionConsumers());
            check(recovered.providers().readConsumer(PROVIDER, () -> recoveredSource.active(PROVIDER + ".combat", subject, 100))
                    .getFirst().projectionId().equals(projection.projectionId()), "restored content lost exact persistent projection identity");
            check(recoveredJournal.snapshot().receipts().get(receipt.receiptId()).equals(receipt), "recovery changed immutable receipt/Undo values");
            present.set(false);
            for (int i = 0; i < 4; i++) rejects(() -> recovered.providers().readConsumer(PROVIDER,
                    () -> recoveredSource.active(PROVIDER + ".combat", subject, 100)));
            check(recoveredJournal.ready() && !recovered.providers().quarantined(PROVIDER), "expected missing content closed journal/provider");
            present.set(true);
            check(recovered.providers().readConsumer(PROVIDER, () -> recoveredSource.active(PROVIDER + ".combat", subject, 100)).size() == 1,
                    "restored registry content required rebuilding provider after an expected absence");
            final Map<String, Object> corrupt = new HashMap<>(codec.encodeState(recoveredJournal.snapshot())); corrupt.put("extra", "forbidden");
            rejects(() -> codec.decodeState(corrupt));
            corrupt.remove("extra"); corrupt.put("schema-version", 99); rejects(() -> codec.decodeState(corrupt));
            await(recoveredJournal.close());
            System.out.println("Stored content isolation passed: real YAML, removed content/type/provider, broken codec circuit breaker, unrelated writes, retained quarantine/receipts and explicit restart recovery; malformed journal envelopes still rejected.");
        } finally {
            try (final var paths = Files.walk(directory)) { for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
}
