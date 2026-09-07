package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.dev.weaver.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.gui.WeaverFacetView;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.factions.*;
import hu.taliann.icesmp.factions.FactionPassivePolicy.ContentContext;
import hu.taliann.icesmp.integrity.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import static hu.taliann.icesmp.dev.weaver.provider.FactionWeaverProvider.*;

/** Real provider/journal/Undo/recovery and canonical passive policy, with explicit owner fixtures. */
public final class FactionWeaverProjectionRegressionSuite {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(10, TimeUnit.SECONDS); }
    private static void fails(CompletionStage<?> stage) throws Exception { try { await(stage); throw new AssertionError("Expected refusal"); } catch (ExecutionException expected) { } }
    private static void rejects(Runnable action) { try { action.run(); throw new AssertionError("Expected refusal"); } catch (IllegalArgumentException | WeaverDomainRejection expected) { } }
    private static final class Storage implements WeaverJournalStorage {
        final WeaverJournalCodec codec = new WeaverJournalCodec(); Map<String, Object> encoded;
        Map<String, WeaverAuditEntry> audit = Map.of(); int writes, failAt; boolean afterWrite;
        public WeaverJournalState readState() { return encoded == null ? WeaverJournalState.empty() : codec.decodeState(encoded); }
        public Map<String, WeaverAuditEntry> readAudit() { return audit; }
        void before() throws java.io.IOException { if (++writes == failAt && !afterWrite) throw new java.io.IOException("before fsync"); }
        void after() throws java.io.IOException { if (writes == failAt && afterWrite) throw new java.io.IOException("after fsync"); }
        public void writeState(WeaverJournalState state) throws Exception { before(); encoded = codec.encodeState(state); after(); }
        public void writeAudit(Map<String, WeaverAuditEntry> entries) throws Exception { before(); audit = codec.decodeAudit(codec.encodeAudit(entries)); after(); }
    }
    private static final class Fixture implements AutoCloseable {
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); final WorldWeaverProviderRegistry registry = new WorldWeaverProviderRegistry(types, () -> 0L);
        final PlayerRef player; final EntityRef mob; final Storage storage;
        FactionMembership canonicalMembership = FactionMembership.citizen(FactionType.NEUTRAL);
        Set<ContentContext> canonicalContexts = Set.of();
        final ThreadLocal<Boolean> owner = ThreadLocal.withInitial(() -> false); final AtomicBoolean loaded = new AtomicBoolean(true); final AtomicInteger captures = new AtomicInteger();
        final WeaverJournal journal; final FactionRuntimeProjectionSource source; final FactionWeaverProvider provider;
        final ProviderContext context; final WeaverDurableExecutionCoordinator execution;
        final WeaverOwnerRouter router = new WeaverOwnerRouter() {
            public <T> CompletionStage<T> submit(ExecutionOwner target, UUID actor, Duration timeout, Supplier<CompletionStage<T>> action) {
                if (target instanceof EntityOwner && !loaded.get()) return CompletableFuture.failedFuture(new WeaverDomainRejection("ENTITY_UNAVAILABLE"));
                owner.set(target instanceof EntityOwner);
                try { return action.get(); } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); } finally { owner.remove(); }
            }
            public void close() { }
        };
        Fixture() throws Exception { this(new Storage(), new PlayerRef(UUID.randomUUID()), new EntityRef(UUID.randomUUID())); }
        Fixture(Storage storage, PlayerRef player, EntityRef mob) throws Exception {
            this.storage = storage; this.player = player; this.mob = mob; ScalarTypeCodec.registerBuiltins(types);
            journal = new WeaverJournal(storage, projection -> registry.projectionConsumers().validate(projection));
            source = new FactionRuntimeProjectionSource(new JournalProjectionSource(journal, registry::projectionConsumers), System::currentTimeMillis);
            provider = new FactionWeaverProvider(types, source, this::capture); registry.register(provider); registry.freezeAndValidate(); await(journal.load());
            context = new ProviderContext(WeaverProviderTestContext.sandbox(types).authority(), types, Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
            execution = new WeaverDurableExecutionCoordinator(router, journal, types);
        }
        Map<String, WeaverValue> capture(SubjectRef ref) {
            check(owner.get(), "live snapshot captured outside its owner"); captures.incrementAndGet();
            if (!loaded.get()) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
            return ref instanceof PlayerRef ? membershipFacts(ref, canonicalMembership, source, System.currentTimeMillis())
                    : contextFacts(ref, canonicalContexts, source, System.currentTimeMillis());
        }
        SubjectSnapshot snapshot(SubjectRef ref) { owner.set(true); try { return FactionProjectionActions.SCOPE.apply(new SubjectSnapshot(ref, System.currentTimeMillis(), "capture", provider.captureOnOwner(ref))); } finally { owner.remove(); } }
        WeaverValue value(String catalog, String id, SubjectRef subject) { return provider.catalog(context, snapshot(subject), catalog).orElseThrow().resolve(id).orElseThrow(); }
        WeaverReceipt apply(SubjectRef target, String action, WeaverValue value, Lifetime lifetime) throws Exception {
            final var ctx = new ProviderContext(context.authority(), types, lifetime, IntegrityMode.SANDBOX);
            final var snapshot = snapshot(target); final var request = new ActionRequest(action, value == null ? Map.of() : Map.of("value", value), lifetime, IntegrityMode.SANDBOX);
            final var plan = provider.prepare(ctx, snapshot, request);
            return await(execution.execute("faction", ctx, snapshot, request, plan, provider.prepareEffects(ctx, snapshot, request, plan), ctx::authority));
        }
        WeaverReceipt undo(WeaverReceipt original) throws Exception {
            final var snapshot = snapshot(original.subject()); final var coordinator = new WeaverUndoCoordinator(journal, registry);
            final var claim = coordinator.target(context.authority(), original.receiptId(), snapshot).claim();
            final var ctx = new ProviderContext(context.authority(), types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            final var plan = coordinator.prepare(ctx, claim, snapshot); final var request = new ActionRequest(plan.descriptor().id(), original.undo().orElseThrow().parameters(), ctx.lifetime(), ctx.integrityMode());
            return await(execution.execute("faction", ctx, snapshot, request, plan, provider.prepareEffects(ctx, snapshot, request, plan), Optional.of(claim), ctx::authority));
        }
        WeaverRecoveryCoordinator recovery() {
            return new WeaverRecoveryCoordinator(journal, (actor, ref) -> router.submit(new EntityOwner(ref instanceof PlayerRef p ? p.playerId() : ((EntityRef) ref).entityId()), actor, Duration.ofSeconds(5),
                    () -> CompletableFuture.completedFuture(new SubjectSnapshot(ref, System.currentTimeMillis(), "capture", capture(ref)))), registry, types);
        }
        public void close() throws Exception { execution.close(); if (journal.ready()) await(journal.close()); else fails(journal.close()); }
    }
    public static void main(String[] args) throws Exception {
        final var settings = settings(); final var policy = new FactionPassivePolicy();
        try (final Fixture f = new Fixture()) {
            final var snapshot = f.snapshot(f.player); final int captures = f.captures.get();
            check(WeaverFacetView.discover(f.registry, f.registry.discover(snapshot)).getFirst().actions().size() == 2, "generic player action view missing");
            f.provider.inspect(f.context, snapshot, FACET); f.provider.catalog(f.context, snapshot, "faction.memberships").orElseThrow().resolve("red");
            check(f.captures.get() == captures, "discovery/inspect/catalog read live state");
            final var red = f.value("faction.memberships", "red", f.player);
            check(!f.provider.validateImport(f.context, f.snapshot(f.mob), "faction.import_membership", red).compatible(), "player Thread accepted on entity");
            final var original = f.apply(f.player, "faction.project_player_faction", red, Lifetime.PERSISTENT);
            final var effective = f.source.resolve(f.player.playerId(), f.canonicalMembership);
            check(policy.damageMultiplier(effective, FactionPassivePolicy.DamageChannel.RED_FIRE, settings) == 0.25, "native passive policy ignored projected RED membership");
            check(f.canonicalMembership.isMember(FactionType.NEUTRAL) && policy.damageMultiplier(f.canonicalMembership, FactionPassivePolicy.DamageChannel.RED_FIRE, settings) == 1,
                    "projection replaced canonical membership");
            final var exported = f.provider.exportValue(f.context, f.snapshot(f.player), "faction.export_membership").value().orElseThrow();
            check(f.provider.validateImport(f.context, f.snapshot(f.player), "faction.import_membership", exported).compatible(), "effective membership Thread cannot import");
            check(new WeaverInfluenceLookup(f.journal, System::currentTimeMillis).recipient(f.player.playerId()) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "SANDBOX player projection did not quarantine");
            f.undo(original); check(f.source.resolve(f.player.playerId(), f.canonicalMembership).isMember(FactionType.NEUTRAL), "conditional Undo failed to restore canonical membership");
            check(f.journal.snapshot().receipts().get(original.receiptId()).status() == ReceiptStatus.UNDONE, "Undo history not marked");
            final var drift = f.apply(f.player, "faction.project_player_faction", red, Lifetime.PERSISTENT);
            f.canonicalMembership = FactionMembership.citizen(FactionType.BLUE);
            rejects(() -> new WeaverUndoCoordinator(f.journal, f.registry).target(f.context.authority(), drift.receiptId(), f.snapshot(f.player)));
        }
        try (final Fixture f = new Fixture()) {
            final var boss = f.value("faction.contexts", "world_boss", f.mob);
            final var dark = FactionMembership.citizen(FactionType.DARK);
            check(policy.resolveTarget(dark, target(f.source.resolve(f.mob.entityId(), f.canonicalContexts)), settings, 0) == FactionPassivePolicy.TargetDecision.CANCEL_DARK_AMBIENT, "baseline passive fixture invalid");
            f.apply(f.mob, "faction.add_entity_context", boss, Lifetime.PERSISTENT);
            check(policy.resolveTarget(dark, target(f.source.resolve(f.mob.entityId(), f.canonicalContexts)), settings, 0) == FactionPassivePolicy.TargetDecision.ALLOW, "projected context did not reach native target policy");
            check(f.canonicalContexts.isEmpty(), "semantic projection changed canonical event identity");
            final var thread = f.provider.exportValue(f.context, f.snapshot(f.mob), "faction.export_context").value().orElseThrow();
            check(f.provider.validateImport(f.context, f.snapshot(f.mob), "faction.import_context", thread).compatible(), "semantic Thread import unavailable");
            f.apply(f.mob, "faction.remove_entity_context", boss, Lifetime.PERSISTENT);
            check(f.source.resolve(f.mob.entityId(), f.canonicalContexts).isEmpty(), "latest remove did not override add");
            f.apply(f.mob, "faction.add_entity_context", boss, Lifetime.PERSISTENT);
            check(f.source.resolve(f.mob.entityId(), f.canonicalContexts).contains(ContentContext.WORLD_BOSS), "latest add did not override remove");
            f.canonicalContexts = Set.of(ContentContext.CROWN_CURSE);
            check(f.source.resolve(f.mob.entityId(), f.canonicalContexts).contains(ContentContext.CROWN_CURSE), "projection removed canonical crown curse");
            check(f.provider.catalog(f.context, f.snapshot(f.mob), "faction.contexts").orElseThrow().resolve("crown_curse").isEmpty(), "canonical crown curse exported as projectable catalog content");
            final var forbidden = reference(CONTEXT, "crown_curse", "faction.context", 1);
            rejects(() -> f.types.validate(forbidden));
            rejects(() -> FactionContextProjectionSource.validate(Set.of(ContentContext.CROWN_CURSE), Set.of()));
            rejects(() -> FactionContextProjectionSource.validate(Set.of(), Set.of(ContentContext.CROWN_CURSE)));
            f.apply(f.mob, FactionProjectionActions.CLEAR_ENTITY, null, Lifetime.ONE_SHOT);
            check(f.source.resolve(f.mob.entityId(), f.canonicalContexts).equals(f.canonicalContexts), "clear changed canonical contexts");
            check(new WeaverInfluenceLookup(f.journal, System::currentTimeMillis).source(new RewardSource.Entity(f.mob.entityId())) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "clear washed entity quarantine");
        }
        try (final Fixture f = new Fixture()) {
            f.apply(f.player, "faction.project_player_faction", f.value("faction.memberships", "red", f.player), Lifetime.SESSION);
            await(f.journal.expireProjections(System.currentTimeMillis(), true));
            check(f.source.resolve(f.player.playerId(), f.canonicalMembership).equals(f.canonicalMembership), "session cleanup left faction projection");
            final var snapshot = f.snapshot(f.player); final var request = new ActionRequest("faction.project_player_faction", Map.of("value", reference(FACTION, "red", "faction.membership", 1)), Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
            final var plan = f.provider.prepare(f.context, snapshot, request); f.canonicalMembership = FactionMembership.citizen(FactionType.DARK);
            fails(f.execution.execute("faction", f.context, snapshot, request, plan, f.provider.prepareEffects(f.context, snapshot, request, plan), f.context::authority));
            check(f.journal.snapshot().projections().isEmpty(), "stale owner snapshot published faction projection");
        }
        for (int boundary = 1; boundary <= 4; boundary++) for (boolean afterWrite : List.of(false, true)) {
            final Storage storage = new Storage(); final PlayerRef player = new PlayerRef(UUID.randomUUID()); final EntityRef mob = new EntityRef(UUID.randomUUID());
            try (final Fixture f = new Fixture(storage, player, mob)) {
                storage.failAt = boundary; storage.afterWrite = afterWrite;
                try { f.apply(player, "faction.project_player_faction", reference(FACTION, "red", "faction.membership", 1), Lifetime.PERSISTENT); throw new AssertionError("Expected injected crash"); }
                catch (ExecutionException expected) { }
            }
            storage.failAt = 0;
            try (final Fixture f = new Fixture(storage, player, mob)) {
                f.loaded.set(false); final var recovery = f.recovery(); await(recovery.start());
                if (boundary > 1 || afterWrite) check(!recovery.pending().isEmpty() || f.journal.snapshot().operations().values().iterator().next().status() == OperationStatus.COMMITTED, "offline player was force resolved");
                f.loaded.set(true); await(recovery.entityAvailable(player.playerId()));
                final boolean applied = boundary > 2 || boundary == 2 && afterWrite;
                check(f.source.resolve(player.playerId(), f.canonicalMembership).isMember(FactionType.RED) == applied, "crash replayed or lost faction projection");
                check(f.journal.snapshot().operations().values().stream().allMatch(o -> o.status() == OperationStatus.COMMITTED || o.status() == OperationStatus.ABORTED), "observed faction recovery did not settle");
                check(storage.audit.size() <= 1, "faction recovery duplicated audit"); recovery.close();
            }
        }
        System.out.println("Faction Weaver projection passed: " + assertions + " assertions; real provider/native policy, owner capture, typed Thread, canonical identity, Crown boundary, quarantine, Undo/drift, session cleanup and eight offline restart boundaries.");
    }
    private static FactionPassivePolicy.TargetContext target(Set<ContentContext> contexts) {
        return new FactionPassivePolicy.TargetContext(false, contexts, false, false, true, true, true, false, false, false, false, true, false);
    }
    private static FactionPassiveSettings settings() {
        return new FactionPassiveSettings(true, new FactionPassiveSettings.Red(true, .25, .25, .75, .5, .25, false, .75, false),
                new FactionPassiveSettings.Blue(true, 0, .5, .25, Set.of("SPRINT")),
                new FactionPassiveSettings.Neutral(true, .5, true, true, Set.of(), true, 60000, true, true),
                new FactionPassiveSettings.Dark(true, true, .5, true, .5, new FactionPassiveSettings.AmbientUndead(true, true, 60000, 16, true),
                        new FactionPassiveSettings.WildUndead(true, true, .5, true), new FactionPassiveSettings.Exclusions(true, true, true, true, true, true, true), Set.of(), Set.of()),
                new FactionPassiveSettings.Whisper(true, true, .35, true, true, 60000, 16));
    }
}
