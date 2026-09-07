package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.dev.weaver.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.gui.WeaverFacetView;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.factions.*;
import hu.taliann.icesmp.playerprofile.application.*;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionStore.*;
import hu.taliann.icesmp.playerprofile.persistence.*;
import hu.taliann.icesmp.playerprofile.transaction.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/** Actual provider, native membership continuation, real profile WAL and generic journal/recovery. */
public final class FactionWeaverCanonicalRegressionSuite {
    private static final PlayerRef PLAYER = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000972"));
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(10, TimeUnit.SECONDS); }
    private static void rejects(Runnable task) { try { task.run(); throw new AssertionError("expected refusal"); } catch (WeaverDomainRejection expected) { } }
    private static final class Storage implements WeaverJournalStorage {
        final WeaverJournalCodec codec = new WeaverJournalCodec(); Map<String, Object> state; Map<String, WeaverAuditEntry> audit = Map.of();
        int writes, failAt; boolean after;
        public WeaverJournalState readState() { return state == null ? WeaverJournalState.empty() : codec.decodeState(state); }
        public Map<String, WeaverAuditEntry> readAudit() { return audit; }
        private void before() throws java.io.IOException { if (++writes == failAt && !after) throw new java.io.IOException("before journal write"); }
        private void after() throws java.io.IOException { if (writes == failAt && after) throw new java.io.IOException("after journal write"); }
        public void writeState(WeaverJournalState next) throws Exception { before(); state = codec.encodeState(next); after(); }
        public void writeAudit(Map<String, WeaverAuditEntry> next) throws Exception { before(); audit = codec.decodeAudit(codec.encodeAudit(next)); after(); }
    }
    private static final class Fixture implements AutoCloseable {
        final ExecutorService io = Executors.newSingleThreadExecutor();
        final YamlPlayerProfileRepository repository; final PlayerProfileAuthority authority;
        final PlayerProfileFactionStore store = new PlayerProfileFactionStore();
        final AtomicInteger mutations = new AtomicInteger(), cleanup = new AtomicInteger();
        final AtomicBoolean profileReady = new AtomicBoolean(true), loaded = new AtomicBoolean(true);
        final ThreadLocal<ExecutionOwner> owner = new ThreadLocal<>();
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); final WorldWeaverProviderRegistry registry = new WorldWeaverProviderRegistry(types, () -> 0L);
        final WeaverJournal journal; final FactionMembershipAdjustmentRuntime nativeRuntime;
        final FactionWeaverProvider provider; final ProviderContext context; final WeaverDurableExecutionCoordinator execution;
        final WeaverOwnerRouter router = new WeaverOwnerRouter() {
            public <T> CompletionStage<T> submit(ExecutionOwner target, UUID actor, Duration timeout, Supplier<CompletionStage<T>> task) {
                if (target instanceof EntityOwner && !loaded.get()) return CompletableFuture.failedFuture(new WeaverDomainRejection("PLAYER_UNAVAILABLE"));
                owner.set(target);
                try { return task.get(); } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); } finally { owner.remove(); }
            }
            public void close() { }
        };
        Fixture(Path path, Storage storage) throws Exception {
            repository = new YamlPlayerProfileRepository(path, Clock.systemUTC(), io);
            final var transactions = new YamlPlayerProfileTransactionManager(repository);
            authority = PlayerProfileAuthority.install(new PlayerProfileService(repository, transactions), repository, transactions);
            await(repository.loadSnapshot(PLAYER.playerId())); ScalarTypeCodec.registerBuiltins(types);
            journal = new WeaverJournal(storage, projection -> registry.projectionConsumers().validate(projection));
            nativeRuntime = new FactionMembershipAdjustmentRuntime(store, Runnable::run, id -> cleanup.incrementAndGet());
            final var source = new FactionRuntimeProjectionSource(new JournalProjectionSource(journal, registry::projectionConsumers), System::currentTimeMillis);
            final var port = new FactionCanonicalActions.Port() {
                public CompletionStage<AdjustmentResult> adjust(UUID id, MembershipAdjustment request, Runnable admission) {
                    check(owner.get() instanceof ProfileOwner profile && profile.playerId().equals(id), "canonical mutation escaped ProfileOwner");
                    check(journal.snapshot().operations().get(request.operationId()).status() == OperationStatus.PREPARED, "native WAL started before durable PREPARED");
                    mutations.incrementAndGet(); return nativeRuntime.adjust(id, request, admission);
                }
                public AdjustmentObservation observe(UUID id, MembershipAdjustment request) { return store.observeAdjustment(id, request); }
                public boolean completed(UUID id, MembershipAdjustment request) { return store.adjustmentEffectsCompleted(id, request); }
            };
            provider = new FactionWeaverProvider(types, source, ref -> {
                check(owner.get() instanceof EntityOwner, "live provider facts captured outside player owner");
                if (!profileReady.get()) throw new WeaverDomainRejection("PROFILE_UNAVAILABLE");
                final var view = store.membershipView(PLAYER.playerId()); final long now = System.currentTimeMillis();
                final var member = view.state().membership().map(FactionMembership::citizen).orElseGet(FactionMembership::guest);
                final Map<String, WeaverValue> facts = new HashMap<>(FactionWeaverProvider.membershipFacts(ref, member, source, now));
                facts.putAll(FactionCanonicalActions.facts(view, nativeRuntime.pending(PLAYER.playerId()), now)); return Map.copyOf(facts);
            }, Optional.of(port));
            registry.register(provider); registry.freezeAndValidate(); await(journal.load());
            context = new ProviderContext(WeaverProviderTestContext.sandbox(types).authority(), types, Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM);
            execution = new WeaverDurableExecutionCoordinator(router, journal, types);
        }
        SubjectSnapshot capture() {
            owner.set(new EntityOwner(PLAYER.playerId()));
            try { return new SubjectSnapshot(PLAYER, System.currentTimeMillis(), "owner", registry.captureContributions(PLAYER)); }
            finally { owner.remove(); }
        }
        SubjectSnapshot snapshot() { return FactionCanonicalActions.SCOPE.apply(capture()); }
        ActionRequest request(FactionType faction) {
            return new ActionRequest(faction == null ? FactionCanonicalActions.REMOVE : FactionCanonicalActions.SET,
                    faction == null ? Map.of() : Map.of("value", provider.catalog(context, snapshot(), "faction.memberships").orElseThrow()
                            .resolve(faction.name().toLowerCase(Locale.ROOT)).orElseThrow()), Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM);
        }
        WeaverReceipt apply(FactionType faction) throws Exception {
            final var snapshot = snapshot(); final var request = request(faction); final var plan = provider.prepare(context, snapshot, request);
            return await(execution.execute("faction", context, snapshot, request, plan, provider.prepareEffects(context, snapshot, request, plan), context::authority));
        }
        WeaverOperationRecord prepared(ActionRequest request) throws Exception {
            final var snapshot = snapshot(); final var plan = provider.prepare(context, snapshot, request); final long now = System.currentTimeMillis();
            return await(journal.prepare(new WeaverOperationRecord(plan.operationId(), context.authority().actor(), "faction", request, PLAYER,
                    snapshot.revisionFingerprint(), Optional.empty(), plan.recoveryPayload(), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false)));
        }
        WeaverRecoveryCoordinator recovery() {
            return new WeaverRecoveryCoordinator(journal, (actor, ref) -> router.submit(new EntityOwner(PLAYER.playerId()), actor, Duration.ofSeconds(5),
                    () -> CompletableFuture.completedFuture(capture())), registry, types);
        }
        public void close() throws Exception {
            nativeRuntime.close(); execution.close();
            try { await(journal.close()); } catch (ExecutionException expected) { }
            check(await(repository.shutdown(Duration.ofSeconds(3))).drained(), "profile shutdown failed"); authority.uninstall();
        }
    }
    public static void main(String[] args) throws Exception {
        final Path root = Files.createTempDirectory("weaver-canonical-faction-");
        try (final Fixture f = new Fixture(root, new Storage())) {
            final var views = WeaverFacetView.discover(f.registry, f.registry.discover(f.snapshot()));
            check(views.getFirst().actions().stream().anyMatch(a -> a.id().equals(FactionCanonicalActions.SET)), "generic facet omitted canonical action");
            final var descriptor = f.registry.actions().get(FactionCanonicalActions.SET);
            check(descriptor.risk() == RiskLevel.CANONICAL && descriptor.lifetimes().equals(Set.of(Lifetime.ONE_SHOT))
                    && descriptor.integrityModes().equals(Set.of(IntegrityMode.LIVE_GM)) && descriptor.areaSupport() == AreaSupport.NONE
                    && descriptor.rateCost() == 10 && !descriptor.undoable() && descriptor.irreversibleReason().isPresent(), "canonical manifest bypassed arming/area/Undo policy");
            final var request = f.request(FactionType.RED);
            rejects(() -> f.provider.prepare(WeaverProviderTestContext.sandbox(f.types), f.snapshot(), request));
            rejects(() -> f.provider.prepare(f.context, f.snapshot(), new ActionRequest(request.actionId(), request.parameters(), Lifetime.PERSISTENT, IntegrityMode.LIVE_GM)));
            final var first = f.apply(FactionType.RED);
            check(first.undo().isEmpty() && f.store.membershipView(PLAYER.playerId()).state().membership().orElseThrow() == FactionType.RED, "canonical membership or irreversible receipt missing");
            check(first.createdAt() >= f.journal.snapshot().operations().get(first.operationId()).preparedAt(), "receipt predates PREPARED");
            check(f.cleanup.get() == 1 && f.store.pendingAdjustmentEffects(PLAYER.playerId()).isEmpty(), "receipt preceded acknowledged native cleanup");
            check(f.journal.snapshot().projections().isEmpty() && f.journal.snapshot().influences().isEmpty(), "LIVE_GM adjustment fabricated projection or quarantine");
            final long revision = f.store.membershipView(PLAYER.playerId()).sectionRevision();
            final var stale = f.snapshot(); final var blue = f.request(FactionType.BLUE); final var plan = f.provider.prepare(f.context, stale, blue);
            await(f.store.assign(PLAYER.playerId(), FactionType.NEUTRAL));
            try { await(f.execution.execute("faction", f.context, stale, blue, plan, f.provider.prepareEffects(f.context, stale, blue, plan), f.context::authority)); throw new AssertionError("stale mutation accepted"); }
            catch (ExecutionException expected) { }
            await(f.nativeRuntime.pulse());
            check(f.store.membershipView(PLAYER.playerId()).sectionRevision() == revision + 1
                    && f.store.membershipView(PLAYER.playerId()).state().membership().orElseThrow() == FactionType.NEUTRAL, "stale action overwrote external membership");
            f.apply(null); check(f.store.membershipView(PLAYER.playerId()).state().membership().isEmpty(), "canonical removal absent");
        } finally { delete(root); }
        profileAndOutboxPending();
        requestRecoveryMismatch();
        for (int boundary = 1; boundary <= 4; boundary++) for (boolean after : List.of(false, true)) crash(boundary, after);
        System.out.println("Faction canonical Weaver passed: " + assertions + " assertions; actual provider/profile WAL, generic discovery, ProfileOwner, durable PREPARED, cleanup, drift, pending profile/outbox and eight journal crash boundaries.");
    }
    private static void profileAndOutboxPending() throws Exception {
        final Path root = Files.createTempDirectory("weaver-faction-pending-");
        try (final Fixture f = new Fixture(root, new Storage())) {
            final var operation = f.prepared(f.request(FactionType.BLUE));
            final var adjustment = FactionCanonicalActions.decode(operation.recoveryPayload());
            await(f.store.adjustMembership(PLAYER.playerId(), adjustment));
            final var recovery = f.recovery(); f.profileReady.set(false); await(recovery.start());
            check(recovery.pending().get(operation.operationId()) == WeaverRecoveryCoordinator.PendingReason.PENDING_PROFILE
                    && f.journal.snapshot().operations().get(operation.operationId()).status() == OperationStatus.PREPARED, "profile-not-ready was wrongly finalized/reviewed");
            f.profileReady.set(true); await(recovery.profilesAvailable());
            check(recovery.pending().get(operation.operationId()) == WeaverRecoveryCoordinator.PendingReason.PENDING_PROFILE
                    && f.mutations.get() == 0 && f.cleanup.get() == 0, "pending outbox was replayed or acknowledged");
            await(f.nativeRuntime.pulse()); await(recovery.profilesAvailable());
            check(recovery.pending().isEmpty() && f.journal.snapshot().operations().get(operation.operationId()).status() == OperationStatus.COMMITTED
                    && f.mutations.get() == 0 && f.cleanup.get() == 1, "native cleanup did not unlock observed journal reconciliation");
            await(recovery.start()); check(f.cleanup.get() == 1 && f.mutations.get() == 0, "settled recovery repeated membership/cleanup");
            recovery.close();
        } finally { delete(root); }
    }
    private static void requestRecoveryMismatch() throws Exception {
        final Path root = Files.createTempDirectory("weaver-faction-payload-drift-");
        try (final Fixture f = new Fixture(root, new Storage())) {
            final var request = f.request(FactionType.RED); final var snapshot = f.snapshot();
            final var plan = f.provider.prepare(f.context, snapshot, request);
            final var intended = FactionCanonicalActions.decode(plan.recoveryPayload());
            final var wrong = new MembershipAdjustment(intended.operationId(), intended.expectedRevision(), intended.expectedMembership(), Optional.of(FactionType.BLUE), intended.occurredAt());
            final long now = System.currentTimeMillis();
            final var operation = await(f.journal.prepare(new WeaverOperationRecord(plan.operationId(), f.context.authority().actor(), "faction", request, PLAYER,
                    snapshot.revisionFingerprint(), Optional.empty(), FactionCanonicalActions.encode(wrong), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false)));
            await(f.store.adjustMembership(PLAYER.playerId(), wrong)); await(f.nativeRuntime.pulse());
            final var recovery = f.recovery(); await(recovery.start());
            check(f.journal.snapshot().operations().get(operation.operationId()).status() == OperationStatus.NEEDS_REVIEW
                    && f.mutations.get() == 0, "recovery blessed a different requested membership"); recovery.close();
        } finally { delete(root); }
    }
    private static void crash(int boundary, boolean after) throws Exception {
        final Path root = Files.createTempDirectory("weaver-faction-crash-"); final Storage storage = new Storage();
        try {
            try (final Fixture f = new Fixture(root, storage)) {
                storage.failAt = boundary; storage.after = after;
                try { f.apply(FactionType.RED); throw new AssertionError("crash not injected"); } catch (ExecutionException expected) { }
            }
            storage.failAt = 0;
            try (final Fixture f = new Fixture(root, storage)) {
                f.loaded.set(false); final var recovery = f.recovery(); await(recovery.start());
                if (boundary > 1 || after) check(!recovery.pending().isEmpty() || f.journal.snapshot().operations().values().stream().allMatch(o -> o.status() == OperationStatus.COMMITTED), "offline player forcibly resolved");
                await(f.nativeRuntime.pulse()); f.loaded.set(true); await(recovery.entityAvailable(PLAYER.playerId()));
                check(f.store.membershipView(PLAYER.playerId()).state().membership().isPresent() == (boundary > 1), "crash lost or replayed canonical membership");
                check(f.mutations.get() == 0 && f.cleanup.get() == 0, "recovery resubmitted canonical action or accepted cleanup");
                check(f.journal.snapshot().operations().values().stream().allMatch(o -> o.status() == OperationStatus.COMMITTED || o.status() == OperationStatus.ABORTED),
                        "journal did not reconcile observed profile receipt at " + boundary + "/" + after + ": "
                                + f.journal.snapshot().operations().values().stream().map(o -> o.status() + "/" + o.revision()).toList() + "; pending=" + recovery.pending());
                check(storage.audit.size() <= 1, "recovery duplicated canonical audit"); recovery.close();
            }
        } finally { delete(root); }
    }
    private static void delete(Path root) throws Exception {
        try (var paths = Files.walk(root)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
}
