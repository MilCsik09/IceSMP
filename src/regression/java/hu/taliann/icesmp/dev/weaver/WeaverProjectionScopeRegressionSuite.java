package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.area.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.*;
import java.util.concurrent.*;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverProjectionRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverTypeCompatibilityRegressionSuite.rejects;

public final class WeaverProjectionScopeRegressionSuite {
    static final ActionDescriptor ACTION = new ActionDescriptor("fixture.area", "fixture.state", net.kyori.adventure.text.Component.text("Area projection"), RiskLevel.MUTATING,
            Set.of(Lifetime.SESSION, Lifetime.PERSISTENT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT), Set.of(WeaverSubjectKind.AREA), List.of(), AreaSupport.ENTITY_FANOUT,
            Optional.of(new AreaLimits(0, 128, 9, 9, 16)), true, Optional.empty(), 1);
    static class Provider extends WeaverContractRegressionSuite.FixtureProvider implements WeaverProjectionProvider {
        Provider() { super("fixture", ACTION, CoverageLevel.FULL_PROVIDER, Map.of("fixture.area", "fixture.assess")); }
        @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.AREA); }
        @Override public List<ProjectionConsumerDescriptor> projectionConsumers() {
            return List.of(new ProjectionConsumerDescriptor("fixture.combat", "fixture", "fixture.Combat#resolve", Set.of("fixture.area"), Set.of(WeaverSubjectKind.ENTITY), Map.of("fixture.rank", INT), Set.of("fixture.loot")));
        }
    }
    static WeaverAreaCollection collection(final int count) {
        final AreaRef area = new AreaRef(UUID.randomUUID(), new CuboidArea(new AreaBounds(0, 64, 0, 1, 64, 0)));
        final List<SubjectSnapshot> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) targets.add(WeaverAreaExecutionRegressionSuite.entity(area.worldId(), new EntityRef(UUID.randomUUID()), 0, "child_" + i));
        return new WeaverAreaCollection(area, AreaSupport.ENTITY_FANOUT, targets, Map.of(), Map.of());
    }
    static WeaverOperationRecord areaOperation(final WeaverAreaCollection selection, final Lifetime lifetime) {
        final var payload = new OperationRecoveryPayload(1, Map.of(WeaverAreaRecoveryEvidence.KEY, WeaverAreaRecoveryEvidence.of(selection).encode()));
        final var manifest = WeaverOperationScope.attach(selection.area(), selection.fingerprint(), lifetime, payload, Optional.empty());
        return new WeaverOperationRecord(UUID.randomUUID(), hu.taliann.icesmp.security.HiddenDevAuthority.PRIMARY_DEVELOPER, "fixture", new ActionRequest("fixture.area", Map.of(), lifetime, IntegrityMode.SANDBOX),
                selection.area(), selection.fingerprint(), Optional.empty(), manifest, OperationStatus.PREPARED, 0, 1, 1, Optional.empty(), false);
    }
    static WeaverReceipt areaReceipt(final WeaverOperationRecord operation) {
        return new WeaverReceipt(UUID.randomUUID(), operation.operationId(), "fixture", "fixture.area", operation.subject(), RiskLevel.MUTATING, operation.request().lifetime(), IntegrityMode.SANDBOX,
                operation.beforeFingerprint(), "after_area", Map.of(), Map.of(), Optional.of(new UndoSpec("fixture.undo", "after_area", Map.of())), 2, ReceiptStatus.COMMITTED);
    }
    static WeaverEffectCommit childEffects(final WeaverOperationRecord operation, final WeaverAreaCollection selection) {
        final List<WeaverProjection> projections = new ArrayList<>(); long sequence = 1;
        for (final var target : selection.targets()) projections.add(new WeaverProjection(UUID.randomUUID(), sequence++, "fixture", "fixture.area", target.ref(), operation.request().lifetime(),
                new DeveloperInfluence(operation.operationId(), IntegrityMode.SANDBOX, "fixture.area", operation.actorId(), 2),
                Map.of("fixture.rank", new WeaverValue(INT, Map.of("value", 7), "fixture", "fixture.state", Set.of(), 2)), target.revisionFingerprint(), 2, OptionalLong.empty()));
        return new WeaverEffectCommit(projections, Set.of(), List.of(), Optional.empty());
    }
    public static void main(final String[] args) throws Exception {
        materialization(); capacity(); legacyAndScope(); crash(); durablePath(); observedAreaRecovery();
        System.out.println("Weaver projection scope passed: persisted AREA child revisions, per-target reservations, pending uncertainty caps, consumer kinds, atomic child effects, crash/reload and durable AREA execution.");
    }
    private static void materialization() throws Exception {
        final var providers = WeaverContractRegressionSuite.registry(new Provider()); providers.freezeAndValidate();
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage, providers.projectionConsumers()::validate); await(journal.load());
        final var selection = collection(128); final var operation = areaOperation(selection, Lifetime.PERSISTENT); await(journal.prepare(operation));
        check(journal.snapshot().intents().get(operation.operationId()).targets().size() == 129, "AREA parent/child uncertainty not acknowledged before mutation");
        final var effects = childEffects(operation, selection); final var wrong = effects.projections().getFirst();
        final var foreign = new WeaverProjection(wrong.projectionId(), wrong.sequence(), wrong.providerId(), wrong.actionId(), new EntityRef(UUID.randomUUID()), wrong.lifetime(), wrong.influence(), wrong.values(), wrong.canonicalFingerprintAtApply(), wrong.createdAt(), wrong.expiresAt());
        fails(journal.applied(operation.operationId(), 0, areaReceipt(operation), effect(foreign), 2));
        final var wrongRevision = new WeaverProjection(wrong.projectionId(), wrong.sequence(), wrong.providerId(), wrong.actionId(), wrong.subject(), wrong.lifetime(), wrong.influence(), wrong.values(), operation.beforeFingerprint(), wrong.createdAt(), wrong.expiresAt());
        fails(journal.applied(operation.operationId(), 0, areaReceipt(operation), effect(wrongRevision), 2));
        final var applied = await(journal.applied(operation.operationId(), 0, areaReceipt(operation), effects, 2)); await(journal.finishAudit(applied.operationId(), applied.revision()));
        check(journal.snapshot().projections().size() == 128 && journal.snapshot().influences().size() == 129, "AREA child effects and influence not atomic");
        final var source = new JournalProjectionSource(journal, providers.projectionConsumers());
        check(source.scalar("fixture.combat", wrong.subject(), "fixture.rank", 3).isPresent(), "child consumer could not see acknowledged projection");
        final var yaml = new YamlWeaverJournalStorage(java.nio.file.Files.createTempDirectory("weaver-area-projection").toFile(), new WeaverJournalCodec(types()), java.util.logging.Logger.getLogger("fixture"));
        yaml.writeState(journal.snapshot()); check(yaml.readState().equals(journal.snapshot()), "AREA child revision/reservation/effects did not survive actual YAML");
        final var sever = areaOperation(selection, Lifetime.ONE_SHOT); await(journal.prepare(sever));
        final var inverseReceipt = areaReceipt(sever); final var severed = await(journal.applied(sever.operationId(), 0, inverseReceipt, new WeaverEffectCommit(List.of(), Set.of(wrong.projectionId()), List.of(), Optional.empty()), 3));
        check(!journal.snapshot().projections().containsKey(wrong.projectionId()), "AREA could not sever selected child projection");
        await(journal.resolve(severed.operationId(), severed.revision(), OperationStatus.COMPENSATED, 4));
        check(journal.snapshot().projections().get(wrong.projectionId()).equals(wrong), "AREA sever compensation lost exact child before-image"); await(journal.close());
    }
    private static void capacity() throws Exception {
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage); await(journal.load());
        final var first = areaOperation(collection(128), Lifetime.SESSION); final var second = areaOperation(collection(128), Lifetime.SESSION);
        await(journal.prepare(first)); await(journal.prepare(second)); fails(journal.prepare(areaOperation(collection(1), Lifetime.SESSION)));
        final var uncertain = await(journal.resolve(first.operationId(), 0, OperationStatus.NEEDS_REVIEW, 3)); await(journal.finishAudit(uncertain.operationId(), uncertain.revision()));
        fails(journal.prepare(areaOperation(collection(1), Lifetime.SESSION))); check(journal.ready(), "uncertainty released reserved lifetime capacity or poisoned writer"); await(journal.close());
        final Storage one = new Storage(); final WeaverJournal perTarget = new WeaverJournal(one); await(perTarget.load());
        final var original = operation(new PlayerRef(UUID.randomUUID()), Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
        final var payload = WeaverOperationScope.attach(original.subject(), "before", Lifetime.PERSISTENT, original.recoveryPayload(), Optional.of(Map.of(original.subject(), 32)));
        final var reserved = new WeaverOperationRecord(original.operationId(), original.actorId(), original.providerId(), original.request(), original.subject(), "before", Optional.empty(), payload, OperationStatus.PREPARED, 0, 1, 1, Optional.empty(), false);
        await(perTarget.prepare(reserved)); fails(perTarget.prepare(operation(original.subject(), Lifetime.PERSISTENT, IntegrityMode.SANDBOX))); await(perTarget.close());
        final var providers = WeaverContractRegressionSuite.registry(new WeaverProjectionRegressionSuite.Provider()); providers.freezeAndValidate();
        final Storage over = new Storage(); final WeaverJournal exceeded = new WeaverJournal(over, providers.projectionConsumers()::validate); await(exceeded.load());
        await(exceeded.prepare(original)); fails(exceeded.applied(original.operationId(), 0, receipt(original), new WeaverEffectCommit(List.of(projection(original, 1, 1, OptionalLong.empty()), projection(original, 2, 2, OptionalLong.empty())), Set.of(), List.of(), Optional.empty()), 2));
        check(exceeded.snapshot().projections().isEmpty() && exceeded.snapshot().intents().containsKey(original.operationId()), "provider exceeded reserved count or lost uncertainty"); await(exceeded.close());
    }
    private static void legacyAndScope() {
        final var selection = collection(2); final var evidence = WeaverAreaRecoveryEvidence.of(selection);
        check(WeaverAreaRecoveryEvidence.decode(selection.area(), evidence.encode()).equals(evidence), "AREA schema 2 round trip");
        final var legacy = new WeaverAreaRecoveryEvidence(selection.area(), selection.support(), evidence.targets(), Map.of(), Map.of());
        final var decoded = WeaverAreaRecoveryEvidence.decode(selection.area(), legacy.encode()); check(decoded.beforeFingerprints().isEmpty(), "legacy AREA invented child revisions");
        final var payload = new OperationRecoveryPayload(1, Map.of(WeaverAreaRecoveryEvidence.KEY, legacy.encode()));
        rejects(() -> WeaverOperationScope.attach(selection.area(), selection.fingerprint(), Lifetime.PERSISTENT, payload, Optional.empty()));
        final var fresh = new OperationRecoveryPayload(1, Map.of(WeaverAreaRecoveryEvidence.KEY, evidence.encode()));
        rejects(() -> WeaverOperationScope.attach(selection.area(), selection.fingerprint(), Lifetime.PERSISTENT, fresh, Optional.of(Map.of(new EntityRef(UUID.randomUUID()), 1))));
        final var operation = areaOperation(selection, Lifetime.PERSISTENT);
        rejects(() -> WeaverOperationScope.attach(operation.subject(), operation.beforeFingerprint(), operation.request().lifetime(), operation.recoveryPayload(), Optional.empty()));
    }
    private static void crash() throws Exception {
        final var providers = WeaverContractRegressionSuite.registry(new Provider()); providers.freezeAndValidate();
        for (int boundary = 1; boundary <= 4; boundary++) for (final boolean after : List.of(false, true)) {
            final Storage storage = new Storage(); storage.failWrite = boundary; storage.afterWrite = after;
            final WeaverJournal journal = new WeaverJournal(storage, providers.projectionConsumers()::validate); await(journal.load());
            final var selection = collection(2); final var operation = areaOperation(selection, Lifetime.PERSISTENT);
            try { await(journal.prepare(operation)); final var applied = await(journal.applied(operation.operationId(), 0, areaReceipt(operation), childEffects(operation, selection), 2)); await(journal.finishAudit(applied.operationId(), applied.revision())); throw new AssertionError("missing injected failure"); }
            catch (final ExecutionException expected) { }
            fails(journal.close()); storage.failWrite = 0; final WeaverJournal restarted = new WeaverJournal(storage, providers.projectionConsumers()::validate); await(restarted.load());
            final boolean applied = boundary > 2 || boundary == 2 && after;
            check(restarted.snapshot().projections().size() == (applied ? 2 : 0) && restarted.snapshot().receipts().size() == (applied ? 1 : 0), "crash split AREA children from receipt");
            if (boundary > 1 || after) for (final var target : selection.targets()) check(restarted.influenceIndex().quarantined(WeaverInfluenceTarget.subject(target.ref()).source(), Long.MAX_VALUE), "AREA crash lost pending/applied child quarantine");
            await(restarted.close());
        }
    }
    private static void durablePath() throws Exception {
        final var providers = WeaverContractRegressionSuite.registry(new Provider()); providers.freezeAndValidate();
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage, providers.projectionConsumers()::validate); await(journal.load());
        final var router = new WeaverAreaExecutionRegressionSuite.Router(); final var access = new WeaverAreaExecutionRegressionSuite.Access(router); final var engine = new WeaverAreaEngine(router, access);
        final var selection = collection(2); selection.targets().forEach(target -> access.snapshots.put(target.ref(), target));
        final var authority = WeaverAreaExecutionRegressionSuite.authority(); final var context = new ProviderContext(authority, types(), Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
        final List<ExecutionStage> stages = new ArrayList<>();
        for (final var target : selection.targets()) stages.add(new ExecutionStage("fixture.child_" + stages.size(), SubjectRoute.owner(target.ref()), Map.of(), (execution, payload) -> {
            check(journal.snapshot().intents().values().iterator().next().targets().size() == 3, "child stage preceded durable complete uncertainty");
            return CompletableFuture.completedFuture(new StageResult(target.revisionFingerprint(), Map.of(), Map.of()));
        }, Optional.of((compensation, payload) -> CompletableFuture.completedFuture(new StageResult(target.revisionFingerprint(), Map.of(), Map.of()))), 5000));
        final var plan = engine.guard(selection, new PreparedAction(UUID.randomUUID(), ACTION, selection.area(), selection.fingerprint(), stages, new OperationRecoveryPayload(1, Map.of()), (prepared, results, now) ->
                new WeaverReceipt(UUID.randomUUID(), prepared.operationId(), "fixture", "fixture.area", selection.area(), RiskLevel.MUTATING, Lifetime.PERSISTENT, IntegrityMode.SANDBOX,
                        selection.fingerprint(), results.getLast().afterFingerprint(), Map.of(), Map.of(), Optional.of(new UndoSpec("fixture.undo", results.getLast().afterFingerprint(), Map.of())), now, ReceiptStatus.COMMITTED)));
        final var executor = new WeaverDurableExecutionCoordinator(router, journal, types());
        final var effects = new PreparedEffects(WeaverEffectIntent.none(), (prepared, results, receipt, sequence) -> {
            final List<WeaverProjection> projections = new ArrayList<>(); long next = sequence;
            for (final var target : selection.targets()) projections.add(new WeaverProjection(UUID.randomUUID(), next++, "fixture", "fixture.area", target.ref(), Lifetime.PERSISTENT,
                    new DeveloperInfluence(prepared.operationId(), IntegrityMode.SANDBOX, "fixture.area", authority.actor(), receipt.createdAt()), Map.of("fixture.rank", new WeaverValue(INT, Map.of("value", 7), "fixture", "fixture.state", Set.of(), receipt.createdAt())), target.revisionFingerprint(), receipt.createdAt(), OptionalLong.empty()));
            return new WeaverEffectCommit(projections, Set.of(), List.of(), Optional.empty());
        });
        final var receipt = await(executor.execute("fixture", context, selection.decorate(new SubjectSnapshot(selection.area(), 1, "area", Map.of())), new ActionRequest("fixture.area", Map.of(), Lifetime.PERSISTENT, IntegrityMode.SANDBOX), plan, effects, () -> authority));
        check(WeaverOperationScope.reservations(journal.snapshot().operations().get(receipt.operationId())).size() == 2 && journal.snapshot().projections().size() == 2, "durable AREA path lost automatic child reservations");
        executor.close(); engine.close(); await(journal.close());
    }
    private static void observedAreaRecovery() throws Exception {
        final var selection = collection(2); final var operation = areaOperation(selection, Lifetime.PERSISTENT);
        final var provider = new Provider() {
            @Override public RecoveryAssessment assessAreaRecovery(final RecoveryContext context, final SubjectSnapshot snapshot, final WeaverAreaCollection targets, final WeaverOperationRecord record) {
                context.authority().require(record);
                check(targets.targets().stream().map(SubjectSnapshot::ref).toList().equals(selection.targets().stream().map(SubjectSnapshot::ref).toList()), "recovery changed acknowledged child identity");
                return new RecoveryAssessment(ObservedOperationState.APPLIED, false, Optional.of(areaReceipt(record)), "fixture_observed", Optional.of(childEffects(record, selection)));
            }
        };
        final var providers = WeaverContractRegressionSuite.registry(provider); providers.freezeAndValidate();
        final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage, providers.projectionConsumers()::validate); await(journal.load()); await(journal.prepare(operation));
        final var router = new WeaverAreaExecutionRegressionSuite.Router(); final var access = new WeaverAreaExecutionRegressionSuite.Access(router);
        selection.targets().forEach(target -> access.snapshots.put(target.ref(), target)); final var areas = new WeaverAreaEngine(router, access);
        final var recovery = new WeaverRecoveryCoordinator(journal, (actor, ref) -> CompletableFuture.completedFuture(new SubjectSnapshot(ref, 3, "area", Map.of())), providers, types(), areas);
        await(recovery.start());
        check(journal.snapshot().operations().get(operation.operationId()).status() == OperationStatus.COMMITTED && journal.snapshot().projections().size() == 2,
                "provider recovery validation rejected acknowledged child projection scope");
        recovery.close(); areas.close(); await(journal.close());
    }
}
