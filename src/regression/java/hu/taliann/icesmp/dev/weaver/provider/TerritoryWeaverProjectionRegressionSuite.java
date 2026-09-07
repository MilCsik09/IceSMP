package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.*;
import hu.taliann.icesmp.dev.weaver.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.gui.WeaverFacetView;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.territory.TerritoryProtectionPolicy.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import static hu.taliann.icesmp.dev.weaver.provider.TerritoryWeaverProvider.*;

/** Real canonical manager plus typed journal serialization, generic discovery, Undo and owner fixtures. */
public final class TerritoryWeaverProjectionRegressionSuite {
    private static int assertions;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(10, TimeUnit.SECONDS); }
    private static void rejects(Runnable action) { try { action.run(); throw new AssertionError("refusal expected"); } catch (IllegalArgumentException | WeaverDomainRejection expected) { assertions++; } }
    private static final class Storage implements WeaverJournalStorage {
        final WeaverJournalCodec codec = new WeaverJournalCodec(); Map<String, Object> state;
        int writes, failAt; boolean afterWrite;
        Map<String, WeaverAuditEntry> audit = Map.of();
        public WeaverJournalState readState() { return state == null ? WeaverJournalState.empty() : codec.decodeState(state); }
        public Map<String, WeaverAuditEntry> readAudit() { return audit; }
        void before() throws java.io.IOException { if (++writes == failAt && !afterWrite) throw new java.io.IOException("injected before journal write"); }
        void after() throws java.io.IOException { if (writes == failAt && afterWrite) throw new java.io.IOException("injected after journal write"); }
        public void writeState(WeaverJournalState value) throws Exception { before(); state = codec.encodeState(value); after(); }
        public void writeAudit(Map<String, WeaverAuditEntry> value) throws Exception { before(); audit = codec.decodeAudit(codec.encodeAudit(value)); after(); }
    }
    private static final class Fixture implements AutoCloseable {
        final TerritoryManager manager;
        final WorldRef world;
        final WeaverTypeRegistry types = new WeaverTypeRegistry();
        final WorldWeaverProviderRegistry registry = new WorldWeaverProviderRegistry(types, System::currentTimeMillis);
        final ThreadLocal<Boolean> owned = ThreadLocal.withInitial(() -> false);
        final AtomicBoolean loaded = new AtomicBoolean(true); final AtomicInteger captures = new AtomicInteger();
        final WeaverJournal journal; final TerritoryRuntimeProjectionSource source; final TerritoryWeaverProvider provider;
        final ProviderContext context; final WeaverDurableExecutionCoordinator execution;
        final WeaverOwnerRouter router = new WeaverOwnerRouter() {
            public <T> CompletionStage<T> submit(ExecutionOwner target, UUID actor, Duration timeout, Supplier<CompletionStage<T>> work) {
                if (!(target instanceof GlobalOwner) && !(target instanceof ActorOwner)) return CompletableFuture.failedFuture(new AssertionError("wrong owner route"));
                if (target instanceof GlobalOwner && !loaded.get()) return CompletableFuture.failedFuture(new WeaverDomainRejection("WORLD_UNAVAILABLE"));
                owned.set(target instanceof GlobalOwner);
                try { return work.get(); } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); } finally { owned.remove(); }
            }
            public void close() { }
        };
        Fixture() throws Exception { this(new Storage(), UUID.randomUUID(), Files.createTempDirectory("territory-weaver-").resolve("territories.yml")); }
        Fixture(Storage storage, UUID worldId, Path file) throws Exception {
            world = new WorldRef(worldId); manager = new TerritoryManager(file.toFile(), Logger.getAnonymousLogger(), YamlStore::saveAtomic); manager.load();
            if (manager.all().isEmpty()) zone("first", "world");
            ScalarTypeCodec.registerBuiltins(types);
            journal = new WeaverJournal(storage, p -> registry.projectionConsumers().validate(p));
            source = new TerritoryRuntimeProjectionSource(new JournalProjectionSource(journal, registry::projectionConsumers), manager::getById, System::currentTimeMillis);
            provider = new TerritoryWeaverProvider(types, manager, source, ref -> {
                if (!owned.get()) throw new WeaverDomainRejection("OWNER_UNAVAILABLE");
                captures.incrementAndGet();
                if (!loaded.get()) throw new WeaverDomainRejection("WORLD_UNAVAILABLE"); return "world";
            });
            registry.register(provider); registry.freezeAndValidate(); await(journal.load());
            context = new ProviderContext(WeaverProviderTestContext.sandbox(types).authority(), types, Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
            execution = new WeaverDurableExecutionCoordinator(router, journal, types);
        }
        void zone(String id, String worldName) { manager.defineCuboid(id, FactionType.NEUTRAL, id, TerritoryType.PROTECTED_CITY, BlockCuboid.between(worldName, 0, 0, 0, 4, 8, 4)); }
        SubjectSnapshot snapshot() { owned.set(true); try { return SCOPE.apply(new SubjectSnapshot(world, System.currentTimeMillis(), "capture", provider.captureOnOwner(world))); } finally { owned.remove(); } }
        WeaverValue value(String catalog, String id) { return provider.catalog(context, snapshot(), catalog).orElseThrow().resolve(id).orElseThrow(); }
        WeaverReceipt apply(String territory, String rule) throws Exception {
            return execute(new ActionRequest(TerritoryProjectionActions.APPLY, Map.of("territory", value("territory.zones", zoneKey(territory)), "value", value("territory.rules", rule)), Lifetime.PERSISTENT, IntegrityMode.SANDBOX));
        }
        WeaverReceipt execute(ActionRequest request) throws Exception {
            final var ctx = new ProviderContext(context.authority(), types, request.lifetime(), request.integrityMode()); final var snapshot = snapshot();
            final var plan = provider.prepare(ctx, snapshot, request);
            return await(execution.execute("territory", ctx, snapshot, request, plan, provider.prepareEffects(ctx, snapshot, request, plan), ctx::authority));
        }
        WeaverReceipt undo(WeaverReceipt original) throws Exception {
            final var snapshot = snapshot(); final var coordinator = new WeaverUndoCoordinator(journal, registry);
            final var claim = coordinator.target(context.authority(), original.receiptId(), snapshot).claim();
            final var ctx = new ProviderContext(context.authority(), types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            final var plan = coordinator.prepare(ctx, claim, snapshot);
            final var request = new ActionRequest(plan.descriptor().id(), original.undo().orElseThrow().parameters(), ctx.lifetime(), ctx.integrityMode());
            return await(execution.execute("territory", ctx, snapshot, request, plan, provider.prepareEffects(ctx, snapshot, request, plan), Optional.of(claim), ctx::authority));
        }
        WeaverRecoveryCoordinator recovery() {
            return new WeaverRecoveryCoordinator(journal, (actor, ref) -> router.submit(new GlobalOwner(), actor, Duration.ofSeconds(5),
                    () -> CompletableFuture.completedFuture(new SubjectSnapshot(ref, System.currentTimeMillis(), "capture", provider.captureOnOwner(ref)))), registry, types);
        }
        public void close() throws Exception {
            execution.close();
            if (journal.ready()) await(journal.close());
            else { try { await(journal.close()); throw new AssertionError("failed journal close must report failure"); } catch (ExecutionException expected) { assertions++; } }
        }
    }
    public static void main(String[] args) throws Exception {
        discoveryAndNativeIsolation(); precedenceUndoAndDrift(); restart(); staleAndUnavailable(); crashBoundaries(); allowRequiresLiveGm();
        System.out.println("Territory Weaver projection passed. assertions=" + assertions);
    }
    private static void discoveryAndNativeIsolation() throws Exception {
        try (final var f = new Fixture()) {
            final var snapshot = f.snapshot(); final int captures = f.captures.get();
            check(WeaverFacetView.discover(f.registry, f.registry.discover(snapshot)).getFirst().actions().size() == 2, "generic facet/actions discover provider");
            f.provider.inspect(f.context, snapshot, FACET);
            final var catalog = f.provider.catalog(f.context, snapshot, "territory.zones").orElseThrow();
            check(catalog.resolve(zoneKey("later")).isEmpty(), "dynamic fixture initially absent"); f.zone("later", "world");
            check(catalog.resolve(zoneKey("later")).isPresent(), "new native zone appears without provider/core/GUI rebuild");
            check(f.captures.get() == captures, "discovery/inspect/catalog do not capture live world");
            final var before = List.copyOf(f.manager.all()); final var receipt = f.apply("first", "build/deny");
            check(f.source.resolve(f.world.worldId(), "first", Rule.BUILD) == Overlay.DENY, "journal projection reaches native read port");
            check(f.source.resolve(f.world.worldId(), "later", Rule.BUILD) == Overlay.INHERIT, "other territory unaffected");
            check(f.source.resolve(f.world.worldId(), "first", Rule.PVP) == Overlay.INHERIT, "other rule unaffected");
            check(f.source.resolve(UUID.randomUUID(), "first", Rule.BUILD) == Overlay.INHERIT, "other world unaffected");
            check(new HashSet<>(before).equals(new HashSet<>(f.manager.all())), "projection does not modify canonical territory");
            final var thread = f.provider.exportValue(f.context, f.snapshot(), "territory.export_rule").value().orElseThrow();
            check(f.provider.validateImport(f.context, f.snapshot(), "territory.import_rule", thread).compatible(), "rule Thread compatible");
            check(thread.payload().keySet().equals(Set.of("id")), "Thread exports no territory identity/revision/history");
            final var draft = WeaverActionDraft.start(f.registry.actions().get(TerritoryProjectionActions.APPLY), f.snapshot(), IntegrityMode.SANDBOX).with("value", thread, f.types);
            check(!draft.validate(f.types).valid(), "Quick Apply asks for the target territory through generic parameters");
            check(draft.with("territory", f.value("territory.zones", zoneKey("later")), f.types).validate(f.types).valid(), "generic Thread draft accepts another canonical territory");
            final var lookup = new WeaverInfluenceLookup(f.journal, System::currentTimeMillis);
            check(lookup.source(new RewardSource.World(f.world.worldId())) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "SANDBOX world influence published atomically");
            f.undo(receipt);
            check(f.source.resolve(f.world.worldId(), "first", Rule.BUILD) == Overlay.INHERIT, "Undo severs only projection");
            check(lookup.source(new RewardSource.World(f.world.worldId())) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "Undo cannot wash influence");
            f.zone("foreign", "elsewhere");
            final var request = new ActionRequest(TerritoryProjectionActions.APPLY, Map.of("territory", f.value("territory.zones", zoneKey("foreign")), "value", thread), Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
            rejects(() -> f.provider.prepare(f.context, f.snapshot(), request));
            rejects(() -> f.provider.captureOnOwner(f.world));
        }
    }
    private static void precedenceUndoAndDrift() throws Exception {
        try (final var f = new Fixture()) {
            final var first = f.apply("first", "build/deny"); final var second = f.apply("first", "build/inherit");
            check(f.source.resolve(f.world.worldId(), "first", Rule.BUILD) == Overlay.INHERIT, "latest per territory/rule wins");
            rejects(() -> new WeaverUndoCoordinator(f.journal, f.registry).target(f.context.authority(), first.receiptId(), f.snapshot()));
            f.undo(second); check(f.source.resolve(f.world.worldId(), "first", Rule.BUILD) == Overlay.DENY, "sever reveals preceding rule");
            f.manager.rename("first", "External drift");
            rejects(() -> f.source.resolve(f.world.worldId(), "first", Rule.BUILD));
            rejects(() -> new WeaverUndoCoordinator(f.journal, f.registry).target(f.context.authority(), first.receiptId(), f.snapshot()));
            f.execute(new ActionRequest(TerritoryProjectionActions.CLEAR, Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX));
            check(f.source.resolve(f.world.worldId(), "first", Rule.BUILD) == Overlay.INHERIT, "explicit sever recovers drift without overwriting canonical data");
            check(f.manager.getById("first").name().equals("External drift"), "sever preserves external canonical changes");
        }
    }
    private static void restart() throws Exception {
        final Storage storage = new Storage(); final UUID world = UUID.randomUUID(); final Path file = Files.createTempDirectory("territory-restart-").resolve("territories.yml");
        try (final var f = new Fixture(storage, world, file)) { f.apply("first", "fire/deny"); }
        try (final var f = new Fixture(storage, world, file)) {
            check(f.source.resolve(world, "first", Rule.FIRE) == Overlay.DENY, "persistent projection survives journal codec/reload");
            check(f.manager.getById("first").type() == TerritoryType.PROTECTED_CITY, "restart preserves native type");
            check(new WeaverInfluenceLookup(f.journal, System::currentTimeMillis).source(new RewardSource.World(world)) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED,
                    "restart preserves world quarantine");
        }
    }
    private static void staleAndUnavailable() throws Exception {
        for (boolean unavailable : List.of(false, true)) try (final var f = new Fixture()) {
            final var request = new ActionRequest(TerritoryProjectionActions.APPLY,
                    Map.of("territory", f.value("territory.zones", zoneKey("first")), "value", f.value("territory.rules", "build/deny")), Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
            final var snapshot = f.snapshot(); final var plan = f.provider.prepare(f.context, snapshot, request);
            if (unavailable) f.loaded.set(false); else f.manager.rename("first", "Concurrent change");
            try { await(f.execution.execute("territory", f.context, snapshot, request, plan, f.provider.prepareEffects(f.context, snapshot, request, plan), () -> f.context.authority()));
                throw new AssertionError("stale/unavailable operation must fail"); } catch (ExecutionException expected) { assertions++; }
            check(f.journal.snapshot().projections().isEmpty(), "stale/unavailable owner publishes no projection");
        }
    }
    private static void crashBoundaries() throws Exception {
        for (int boundary = 1; boundary <= 4; boundary++) for (boolean afterWrite : List.of(false, true)) {
            final Storage storage = new Storage(); final UUID world = UUID.randomUUID();
            final Path file = Files.createTempDirectory("territory-crash-").resolve("territories.yml");
            try (final var f = new Fixture(storage, world, file)) {
                storage.failAt = boundary; storage.afterWrite = afterWrite;
                try { f.apply("first", "fire/deny"); throw new AssertionError("injected crash expected"); } catch (ExecutionException expected) { assertions++; }
            }
            storage.failAt = 0;
            try (final var f = new Fixture(storage, world, file)) {
                f.loaded.set(false); final var recovery = f.recovery(); await(recovery.start());
                if (boundary > 1 || afterWrite) check(!recovery.pending().isEmpty() || f.journal.snapshot().operations().values().iterator().next().status() == OperationStatus.COMMITTED,
                        "unavailable world remains pending without loading");
                f.loaded.set(true); await(recovery.worldAvailable(world, OptionalLong.empty()));
                final boolean applied = boundary > 2 || boundary == 2 && afterWrite;
                check((f.source.resolve(world, "first", Rule.FIRE) == Overlay.DENY) == applied, "observed recovery does not replay or lose projection");
                check(f.journal.snapshot().operations().values().stream().allMatch(o -> o.status() == OperationStatus.COMMITTED || o.status() == OperationStatus.ABORTED), "observed operation settles");
                check(storage.audit.size() <= 1, "recovery does not duplicate audit"); recovery.close();
            }
        }
    }
    private static void allowRequiresLiveGm() throws Exception {
        try (final var f = new Fixture()) {
            final var live = new ProviderContext(f.context.authority(), f.types, Lifetime.PERSISTENT, IntegrityMode.LIVE_GM);
            final var allow = f.provider.catalog(live, f.snapshot(), "territory.rules").orElseThrow().resolve("build/allow").orElseThrow();
            check(f.provider.catalog(f.context, f.snapshot(), "territory.rules").orElseThrow().resolve("build/allow").isEmpty(), "sandbox catalog does not offer ALLOW");
            check(!f.provider.validateImport(f.context, f.snapshot(), "territory.import_rule", allow).compatible(), "sandbox Thread cannot import ALLOW");
            final var values = Map.of("territory", f.value("territory.zones", zoneKey("first")), "value", allow);
            rejects(() -> f.provider.prepare(f.context, f.snapshot(), new ActionRequest(TerritoryProjectionActions.APPLY, values, Lifetime.PERSISTENT, IntegrityMode.SANDBOX)));
            f.execute(new ActionRequest(TerritoryProjectionActions.APPLY, values, Lifetime.PERSISTENT, IntegrityMode.LIVE_GM));
            check(f.source.resolve(f.world.worldId(), "first", Rule.BUILD) == Overlay.ALLOW, "explicit LIVE_GM may allow protection overlay");
            final var deny = f.apply("first", "build/deny");
            final var undo = deny.undo().orElseThrow();
            final var sandbox = new ProviderContext(f.context.authority(), f.types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            rejects(() -> f.provider.prepare(sandbox, f.snapshot(), new ActionRequest(TerritoryProjectionActions.SEVER, undo.parameters(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX)));
            f.execute(new ActionRequest(TerritoryProjectionActions.SEVER, undo.parameters(), Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM));
            check(f.source.resolve(f.world.worldId(), "first", Rule.BUILD) == Overlay.ALLOW, "revealing earlier ALLOW also requires LIVE_GM");
            final var projection = f.source.active(f.world).getFirst();
            final var tainted = new WeaverProjection(projection.projectionId(), projection.sequence(), projection.providerId(), projection.actionId(), projection.subject(), projection.lifetime(),
                    new DeveloperInfluence(projection.influence().operationId(), IntegrityMode.SANDBOX, projection.actionId(), projection.influence().actorId(), projection.createdAt()),
                    projection.values(), projection.canonicalFingerprintAtApply(), projection.createdAt(), projection.expiresAt());
            final var invalid = new TerritoryRuntimeProjectionSource((consumer, subject, now) -> List.of(tainted), f.manager::getById, System::currentTimeMillis);
            rejects(() -> invalid.resolve(f.world.worldId(), "first", Rule.BUILD));
        }
    }
}
