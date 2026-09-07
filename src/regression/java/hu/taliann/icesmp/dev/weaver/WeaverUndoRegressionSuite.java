package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.time.Duration;
import static hu.taliann.icesmp.dev.weaver.WeaverPersistenceRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverProjectionRegressionSuite.*;
import static hu.taliann.icesmp.dev.weaver.WeaverTypeCompatibilityRegressionSuite.rejects;

public final class WeaverUndoRegressionSuite {
    static final class Provider extends WeaverProjectionRegressionSuite.Provider {
        UUID projection; int applications;
        final ActionDescriptor undo;
        @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.PLAYER, WeaverSubjectKind.ENTITY, WeaverSubjectKind.ITEM_SLOT); }
        Provider() {
            final ActionDescriptor source = WeaverContractRegressionSuite.action("fixture", RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX, IntegrityMode.LIVE_GM), Set.of(IntegrityImpact.TAINT_SUBJECT), false, 1);
            undo = new ActionDescriptor("fixture.undo", source.facetId(), source.label(), source.risk(), source.lifetimes(), source.integrityModes(), source.integrityImpacts(), Set.of(WeaverSubjectKind.PLAYER, WeaverSubjectKind.ENTITY, WeaverSubjectKind.ITEM_SLOT), source.parameters(), source.areaSupport(), source.areaLimits(), false, Optional.empty(), 1);
        }
        @Override public ProviderContribution contribution() {
            final var base = super.contribution(); return new ProviderContribution(base.facets(), List.of(base.actions().getFirst(), undo), List.of(), List.of(), List.of(), Map.of("fixture.action", "fixture.assess", "fixture.undo", "fixture.assess"));
        }
        @Override public PreparedAction prepareUndo(final ProviderContext context, final SubjectSnapshot snapshot, final WeaverReceipt receipt) {
            final var stage = new ExecutionStage("fixture.undo.effect", new EntityOwner(switch (snapshot.ref()) { case PlayerRef player -> player.playerId(); case EntityRef entity -> entity.entityId(); case ItemSlotRef item -> item.holderId(); default -> throw new IllegalArgumentException("Fixture subject"); }), Map.of(), (execution, payload) -> {
                execution.authority().requireValid(); applications++; return CompletableFuture.completedFuture(new StageResult("restored", Map.of(), Map.of()));
            }, Optional.empty(), 5000);
            return new PreparedAction(UUID.randomUUID(), undo, snapshot.ref(), snapshot.revisionFingerprint(), List.of(stage), new OperationRecoveryPayload(1, Map.of()), (prepared, results, now) ->
                    new WeaverReceipt(UUID.randomUUID(), prepared.operationId(), "fixture", "fixture.undo", snapshot.ref(), RiskLevel.MUTATING, Lifetime.ONE_SHOT, context.integrityMode(), snapshot.revisionFingerprint(),
                            "restored", Map.of(), Map.of(), Optional.empty(), now, ReceiptStatus.COMMITTED));
        }
        @Override public PreparedEffects prepareEffects(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request, final PreparedAction action) {
            return new PreparedEffects(WeaverEffectIntent.none(), (prepared, results, receipt, sequence) -> new WeaverEffectCommit(List.of(), projection == null ? Set.of() : Set.of(projection), List.of(), Optional.empty()));
        }
    }
    static final class Router implements WeaverOwnerRouter {
        @Override public <T> CompletionStage<T> submit(final ExecutionOwner owner, final UUID actor, final Duration timeout, final Supplier<CompletionStage<T>> action) {
            try { return action.get(); } catch (final RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
        }
        @Override public void close() { }
    }
    static final class Fixture implements AutoCloseable {
        final Storage storage = new Storage(); final Provider provider = new Provider(); final WorldWeaverProviderRegistry providers = WeaverContractRegressionSuite.registry(provider);
        final WeaverTypeRegistry types = types(); final WeaverJournal journal; final WeaverUndoCoordinator undo;
        final WeaverAuthorityToken authority = new WeaverAuthorityToken(HiddenDevAuthority.PRIMARY_DEVELOPER, UUID.randomUUID(), Long.MAX_VALUE, () -> true, () -> 0L);
        final ProviderContext context = new ProviderContext(authority, types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
        final WeaverDurableExecutionCoordinator execution; final WeaverOperationRecord original; final WeaverReceipt receipt; final SubjectSnapshot snapshot;
        Fixture() throws Exception {
            providers.freezeAndValidate(); journal = new WeaverJournal(storage, providers.projectionConsumers()::validate); await(journal.load());
            original = operation(new PlayerRef(UUID.randomUUID()), Lifetime.PERSISTENT, IntegrityMode.SANDBOX); final var projection = projection(original, 1, 7, OptionalLong.empty()); provider.projection = projection.projectionId();
            receipt = apply(journal, original, effect(projection)).receipt().orElseThrow(); snapshot = new SubjectSnapshot(original.subject(), 3, "after", Map.of());
            undo = new WeaverUndoCoordinator(journal, providers); execution = new WeaverDurableExecutionCoordinator(new Router(), journal, types);
        }
        CompletionStage<WeaverReceipt> run(final WeaverUndoCoordinator.Target target, final PreparedAction plan) {
            final ActionRequest request = new ActionRequest("fixture.undo", target.receipt().undo().orElseThrow().parameters(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            return execution.execute("fixture", context, snapshot, request, plan, provider.prepareEffects(context, snapshot, request, plan), Optional.of(target.claim()), () -> authority);
        }
        @Override public void close() throws Exception { execution.close(); if (journal.ready()) await(journal.close()); else fails(journal.close()); }
    }
    public static void main(final String[] args) throws Exception {
        try (final Fixture f = new Fixture()) {
            rejects(() -> f.undo.target(f.authority, f.receipt.receiptId(), new SubjectSnapshot(f.original.subject(), 4, "external", Map.of())));
            check(f.provider.applications == 0 && f.journal.snapshot().operations().size() == 1, "drift reached mutation preparation");
            rejects(() -> new WeaverReceipt(f.receipt.receiptId(), f.receipt.operationId(), f.receipt.providerId(), f.receipt.actionId(), f.receipt.subject(), f.receipt.risk(), f.receipt.lifetime(), f.receipt.integrityMode(),
                    f.receipt.beforeFingerprint(), f.receipt.afterFingerprint(), f.receipt.before(), f.receipt.after(), Optional.of(new UndoSpec("fixture.undo", "unrelated", Map.of())), f.receipt.createdAt(), ReceiptStatus.COMMITTED));
            final var target = f.undo.target(f.authority, f.receipt.receiptId(), f.snapshot); final var plan = f.undo.prepare(f.context, target.claim(), f.snapshot);
            final var undone = await(f.run(target, plan));
            check(f.provider.applications == 1 && f.journal.snapshot().projections().isEmpty(), "provider Undo did not sever the projection once");
            check(f.journal.snapshot().receipts().get(f.receipt.receiptId()).status() == ReceiptStatus.UNDONE
                    && f.journal.snapshot().operations().get(f.original.operationId()).revision() == target.claim().operationRevision() + 1, "original receipt/revision not committed atomically");
            check(f.storage.audit.containsKey(undone.operationId() + ":UNDONE") && f.storage.audit.containsKey(f.original.operationId() + ":COMMITTED"), "Undo erased original audit or lacked compensating audit");
            check(!f.undo.available(f.authority, f.receipt.receiptId()), "already undone receipt remained actionable"); fails(f.run(target, plan));
            check(f.provider.applications == 1, "Undo retry replayed mutation");
            final var codec = new WeaverJournalCodec(f.types); final var yaml = new YamlWeaverJournalStorage(java.nio.file.Files.createTempDirectory("weaver-undo-regression").toFile(), codec, java.util.logging.Logger.getLogger("fixture"));
            yaml.writeState(f.journal.snapshot()); check(yaml.readState().equals(f.journal.snapshot()), "Undo claim/effect delta YAML round trip");
        }
        for (int boundary = 1; boundary <= 4; boundary++) for (final boolean afterWrite : List.of(false, true)) try (final Fixture f = new Fixture()) {
            final var target = f.undo.target(f.authority, f.receipt.receiptId(), f.snapshot); final var plan = f.undo.prepare(f.context, target.claim(), f.snapshot);
            f.storage.failWrite = f.storage.writes + boundary; f.storage.afterWrite = afterWrite; fails(f.run(target, plan));
            f.storage.failWrite = 0; final WeaverJournal restarted = new WeaverJournal(f.storage, f.providers.projectionConsumers()::validate); await(restarted.load());
            final boolean applied = boundary > 2 || boundary == 2 && afterWrite;
            check((restarted.snapshot().receipts().get(f.receipt.receiptId()).status() == ReceiptStatus.UNDONE) == applied
                    && restarted.snapshot().projections().isEmpty() == applied, "crash split Undo receipt from effect state");
            if (boundary == 1) check(f.provider.applications == 0, "failed Undo preparation still mutated");
            await(restarted.close());
        }
        pendingReservation(); compensatingSever(); severConflict(); schemaTwoMigration(); changedSubjectEvidence();
        System.out.println("Weaver Undo passed: owner snapshot conflict, provider prepareUndo through normal durable execution, one-use receipt revision, preserved audit, eight crash boundaries and projection before-image compensation.");
    }
    private static void pendingReservation() throws Exception {
        try (final Fixture f = new Fixture()) {
            final var target = f.undo.target(f.authority, f.receipt.receiptId(), f.snapshot); final var plan = f.undo.prepare(f.context, target.claim(), f.snapshot); final long now = System.currentTimeMillis();
            final var pending = new WeaverOperationRecord(plan.operationId(), f.authority.actor(), "fixture", new ActionRequest("fixture.undo", Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX),
                    f.original.subject(), "after", Optional.empty(), plan.recoveryPayload(), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false, Optional.of(target.claim()));
            await(f.journal.prepare(pending)); final var other = f.undo.prepare(f.context, target.claim(), f.snapshot); fails(f.run(target, other));
            check(f.provider.applications == 0 && f.journal.snapshot().operations().get(pending.operationId()).equals(pending), "competing Undo settled or executed another reservation");
        }
    }
    private static void compensatingSever() throws Exception {
        try (final Fixture f = new Fixture()) {
            final WeaverProjection before = f.journal.snapshot().projections().get(f.provider.projection);
            final var sever = operation(f.original.subject(), Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM); await(f.journal.prepare(sever));
            final var applied = await(f.journal.applied(sever.operationId(), 0, receipt(sever), new WeaverEffectCommit(List.of(), Set.of(before.projectionId()), List.of(), Optional.empty()), 4));
            check(f.journal.snapshot().projections().isEmpty(), "sever fixture did not apply");
            await(f.journal.resolve(sever.operationId(), applied.revision(), OperationStatus.COMPENSATED, 5));
            check(f.journal.snapshot().projections().get(before.projectionId()).equals(before), "compensation lost removed projection before-image");
            check(f.journal.influenceIndex().quarantined(WeaverInfluenceTarget.subject(before.subject()).source(), 1_000_000), "restored projection lost active quarantine");
        }
    }
    private static void severConflict() throws Exception {
        try (final Fixture f = new Fixture()) {
            final WeaverProjection before = f.journal.snapshot().projections().get(f.provider.projection);
            final var sever = operation(f.original.subject(), Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM); await(f.journal.prepare(sever));
            final var applied = await(f.journal.applied(sever.operationId(), 0, receipt(sever), new WeaverEffectCommit(List.of(), Set.of(before.projectionId()), List.of(), Optional.empty()), 4));
            final var other = operation(f.original.subject(), Lifetime.PERSISTENT, IntegrityMode.SANDBOX); final var generated = projection(other, 2, 99, OptionalLong.empty());
            final var replacement = new WeaverProjection(before.projectionId(), 2, generated.providerId(), generated.actionId(), generated.subject(), generated.lifetime(), generated.influence(), generated.values(), generated.canonicalFingerprintAtApply(), generated.createdAt(), generated.expiresAt());
            apply(f.journal, other, effect(replacement));
            fails(f.journal.resolve(sever.operationId(), applied.revision(), OperationStatus.COMPENSATED, 5));
            check(f.journal.snapshot().projections().get(before.projectionId()).equals(replacement), "compensation overwrote externally replaced projection");
        }
    }
    private static void schemaTwoMigration() throws Exception {
        try (final Fixture f = new Fixture()) {
            final var codec = new WeaverJournalCodec(f.types); final Map<String, Object> legacy = new HashMap<>(codec.encodeState(f.journal.snapshot()));
            legacy.put("schema-version", 2); legacy.remove("effect-deltas"); final Map<String, Object> operations = new HashMap<>();
            WeaverJournalCodec.map(legacy.get("operations")).forEach((id, value) -> { final Map<String, Object> row = new HashMap<>(WeaverJournalCodec.map(value)); row.remove("undo-claim"); operations.put(id, row); });
            legacy.put("operations", operations); final WeaverJournalState migrated = codec.decodeState(legacy);
            check(migrated.projections().equals(f.journal.snapshot().projections()) && migrated.influences().equals(f.journal.snapshot().influences()), "schema 2 migration lost active effects");
            check(migrated.effectDeltas().values().stream().noneMatch(WeaverEffectDelta::complete), "migration fabricated unavailable before-images");
            check(codec.decodeState(codec.encodeState(migrated)).equals(migrated), "schema 2 migration was unstable");
        }
    }

    private static void changedSubjectEvidence() throws Exception {
        final UUID holder = UUID.randomUUID(); final UUID instance = UUID.randomUUID();
        final ItemSlotRef oldItem = new ItemSlotRef(holder, WeaverSlot.named(WeaverSlot.Kind.OFF_HAND), Optional.of("fixture.item"), Optional.of(instance), OptionalLong.of(1), "a".repeat(64));
        final ItemSlotRef newItem = new ItemSlotRef(holder, oldItem.slot(), oldItem.logicalId(), oldItem.instanceId(), OptionalLong.of(2), "b".repeat(64));
        final LocationRef location = new LocationRef(UUID.randomUUID(), 0, 64, 0, 0, 0);
        for (final var pair : List.of(Map.entry(oldItem, newItem), Map.entry(location, new EntityRef(UUID.randomUUID())))) {
            final Provider provider = new Provider(); final var providers = WeaverContractRegressionSuite.registry(provider); providers.freezeAndValidate();
            final Storage storage = new Storage(); final WeaverJournal journal = new WeaverJournal(storage); await(journal.load());
            final var operation = operation(pair.getKey(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            final WeaverValue targetValue = new WeaverValue(WeaverUndoSubject.TYPE, SubjectKeyCodec.payload(pair.getValue()), "fixture", "fixture.state", Set.of(WeaverUndoSubject.CAPABILITY), 2);
            final WeaverReceipt receipt = new WeaverReceipt(UUID.randomUUID(), operation.operationId(), "fixture", "fixture.action", pair.getKey(), RiskLevel.MUTATING, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX,
                    "before", "after", Map.of(), Map.of("fixture.undo_subject", targetValue), Optional.of(new UndoSpec("fixture.undo", "after", Map.of())), 2, ReceiptStatus.COMMITTED);
            check(WeaverUndoSubject.resolve(receipt).equals(pair.getValue()), "changed Subject evidence lost revision/identity");
            rejects(() -> new WeaverReceipt(receipt.receiptId(), operation.operationId(), "fixture", "fixture.action", pair.getKey(), RiskLevel.MUTATING, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX,
                    "before", "after", Map.of(), Map.of("fixture.one", targetValue, "fixture.two", targetValue), receipt.undo(), 2, ReceiptStatus.COMMITTED));
            rejects(() -> new WeaverReceipt(receipt.receiptId(), operation.operationId(), "fixture", "fixture.action", pair.getKey(), RiskLevel.MUTATING, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX,
                    "before", "after", Map.of(), Map.of("other.target", targetValue), receipt.undo(), 2, ReceiptStatus.COMMITTED));
            await(journal.prepare(operation)); final var applied = await(journal.applied(operation.operationId(), 0, receipt, WeaverEffectCommit.none(), 2)); await(journal.finishAudit(applied.operationId(), applied.revision()));
            final var authority = new WeaverAuthorityToken(HiddenDevAuthority.PRIMARY_DEVELOPER, UUID.randomUUID(), Long.MAX_VALUE, () -> true, () -> 0L);
            final var context = new ProviderContext(authority, types(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX); final var undo = new WeaverUndoCoordinator(journal, providers);
            rejects(() -> undo.target(authority, receipt.receiptId(), new SubjectSnapshot(pair.getKey(), 3, "after", Map.of())));
            final var snapshot = new SubjectSnapshot(pair.getValue(), 3, "after", Map.of()); final var target = undo.target(authority, receipt.receiptId(), snapshot);
            final var plan = undo.prepare(context, target.claim(), snapshot); final var request = new ActionRequest("fixture.undo", Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            final var execution = new WeaverDurableExecutionCoordinator(new Router(), journal, types());
            await(execution.execute("fixture", context, snapshot, request, plan, provider.prepareEffects(context, snapshot, request, plan), Optional.of(target.claim()), () -> authority));
            check(journal.snapshot().receipts().get(receipt.receiptId()).status() == ReceiptStatus.UNDONE && provider.applications == 1, "changed Subject Undo failed");
            final var codec = new WeaverJournalCodec(types()); check(codec.decodeState(codec.encodeState(journal.snapshot())).equals(journal.snapshot()), "changed Subject lineage failed restart validation");
            execution.close(); await(journal.close());
        }
    }

}
