package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.pve.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import java.time.Duration;
import static hu.taliann.icesmp.dev.weaver.provider.PvEWeaverProvider.*;

public final class PvEWeaverProjectionRegressionSuite {
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
    private static <T> T await(final CompletionStage<T> future) throws Exception { return future.toCompletableFuture().get(10, TimeUnit.SECONDS); }
    private static void fails(final CompletionStage<?> future) throws Exception { try { await(future); throw new AssertionError("Expected refusal"); } catch (final ExecutionException expected) { } }
    private static void rejects(final Runnable task) { try { task.run(); throw new AssertionError("Expected conflict"); } catch (final WeaverDomainRejection expected) { } }
    private static MobAbilityDefinition ability(final String id) { return new MobAbilityDefinition(id, MobAbilityDefinition.Kind.LUNGE, 40, 10, 4, 2, 0, Map.of()); }
    private static CanonicalMobProfile profile(final MobRank rank, final int level, final String ability) {
        final Map<MobRank, List<String>> kits = new EnumMap<>(MobRank.class); for (final MobRank member : MobRank.values()) kits.put(member, List.of(ability));
        return new CanonicalMobProfile("", rank, Optional.of(MobArchetype.BRUISER), level, kits, List.of(), MobBehaviorProfile.defaults(MobArchetype.BRUISER));
    }
    private static final class Storage implements WeaverJournalStorage {
        WeaverJournalCodec codec; Map<String, Object> encoded; Map<String, WeaverAuditEntry> audit = Map.of(); int writes, failAt; boolean afterWrite;
        @Override public WeaverJournalState readState() { return encoded == null ? WeaverJournalState.empty() : codec.decodeState(encoded); }
        @Override public Map<String, WeaverAuditEntry> readAudit() { return audit; }
        private void before() throws java.io.IOException { if (++writes == failAt && !afterWrite) throw new java.io.IOException("before fsync"); }
        private void after() throws java.io.IOException { if (writes == failAt && afterWrite) throw new java.io.IOException("after fsync"); }
        @Override public void writeState(final WeaverJournalState state) throws Exception { before(); encoded = codec.encodeState(state); after(); }
        @Override public void writeAudit(final Map<String, WeaverAuditEntry> values) throws Exception { before(); audit = codec.decodeAudit(codec.encodeAudit(values)); after(); }
    }
    private static final class Fixture implements AutoCloseable {
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); final WorldWeaverProviderRegistry registry = new WorldWeaverProviderRegistry(types, () -> 0L);
        final AtomicReference<Map<String, MobAbilityDefinition>> abilities = new AtomicReference<>(Map.of("old_charge", ability("old_charge")));
        final AtomicReference<Map<String, MobTemplate>> templates = new AtomicReference<>(Map.of());
        final Map<UUID, CanonicalMobProfile> canonical = new ConcurrentHashMap<>(); final EntityRef target;
        final Storage storage; final WeaverJournal journal; final PvEMobProjectionSource source; final PvEWeaverProvider provider;
        final ThreadLocal<Boolean> owner = ThreadLocal.withInitial(() -> false); final AtomicBoolean loaded = new AtomicBoolean(true); final AtomicInteger captures = new AtomicInteger();
        final WeaverOwnerRouter router = new WeaverOwnerRouter() {
            @Override public <T> CompletionStage<T> submit(final ExecutionOwner ownerRef, final UUID actor, final Duration timeout, final Supplier<CompletionStage<T>> task) {
                if (ownerRef instanceof EntityOwner && !loaded.get()) return CompletableFuture.failedFuture(new WeaverDomainRejection("ENTITY_UNAVAILABLE"));
                owner.set(ownerRef instanceof EntityOwner);
                try { return task.get(); } catch (final RuntimeException failure) { return CompletableFuture.failedFuture(failure); } finally { owner.remove(); }
            }
            @Override public void close() { }
        };
        final ProviderContext context; final WeaverDurableExecutionCoordinator execution;
        Fixture() throws Exception { this(new Storage(), new EntityRef(UUID.randomUUID())); }
        Fixture(final Storage storage, final EntityRef target) throws Exception {
            this.storage = storage; this.target = target; canonical.put(target.entityId(), profile(MobRank.NORMAL, 11, "old_charge"));
            ScalarTypeCodec.registerBuiltins(types); journal = new WeaverJournal(storage, projection -> registry.projectionConsumers().validate(projection));
            source = new PvEMobProjectionSource(new JournalProjectionSource(journal, registry::projectionConsumers), abilities::get, templates::get, System::currentTimeMillis);
            provider = new PvEWeaverProvider(types, abilities::get, templates::get, this::captureFacts, Optional.of(source), subjects -> CompletableFuture.completedFuture(null));
            registry.register(provider); registry.freezeAndValidate(); storage.codec = new WeaverJournalCodec(types); await(journal.load());
            final var authority = WeaverProviderTestContext.sandbox(types).authority(); context = new ProviderContext(authority, types, Lifetime.PERSISTENT, IntegrityMode.SANDBOX);
            execution = new WeaverDurableExecutionCoordinator(router, journal, types);
        }
        Map<String, WeaverValue> captureFacts(final SubjectRef ref) {
            check(owner.get(), "provider snapshot read on foreign owner"); captures.incrementAndGet();
            if (!loaded.get()) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
            final CanonicalMobProfile profile = canonical.get(((EntityRef) ref).entityId()); final long now = System.currentTimeMillis();
            final Map<String, WeaverValue> facts = new HashMap<>(PvEWeaverProvider.projectionFacts(((EntityRef) ref).entityId(), profile, source, now));
            facts.put("pve.rank", reference(RANK, profile.rank().name().toLowerCase(Locale.ROOT), "pve.rank", now));
            final List<String> kit = MobProjectionFixture.selected(source.resolve(((EntityRef) ref).entityId(), profile), abilities.get());
            if (kit.size() == 1) facts.put("pve.ability", reference(ABILITY, kit.getFirst(), "pve.ability", now));
            return Map.copyOf(facts);
        }
        SubjectSnapshot snapshot(final EntityRef ref) { owner.set(true); try { return PvEProjectionActions.SCOPE.apply(new SubjectSnapshot(ref, System.currentTimeMillis(), "fixture", provider.captureOnOwner(ref))); } finally { owner.remove(); } }
        SubjectSnapshot snapshot() { return snapshot(target); }
        ActionRequest request(final String id, final WeaverValue value) { return new ActionRequest(id, Map.of("value", value), Lifetime.PERSISTENT, IntegrityMode.SANDBOX); }
        WeaverReceipt apply(final String id, final WeaverValue value) throws Exception {
            final SubjectSnapshot snapshot = snapshot(); final ActionRequest request = request(id, value); final PreparedAction plan = provider.prepare(context, snapshot, request);
            return await(execution.execute("pve", context, snapshot, request, plan, provider.prepareEffects(context, snapshot, request, plan), context::authority));
        }
        WeaverReceipt remove(final String action, final Map<String, WeaverValue> parameters) throws Exception {
            final var removal = new ProviderContext(context.authority(), types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            final var snapshot = snapshot(); final var request = new ActionRequest(action, parameters, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            final var plan = provider.prepare(removal, snapshot, request);
            return await(execution.execute("pve", removal, snapshot, request, plan, provider.prepareEffects(removal, snapshot, request, plan), context::authority));
        }
        WeaverReceipt undo(final WeaverReceipt original) throws Exception {
            final SubjectSnapshot current = snapshot(); final var coordinator = new WeaverUndoCoordinator(journal, registry); final var claim = coordinator.target(context.authority(), original.receiptId(), current).claim();
            final ProviderContext undoContext = new ProviderContext(context.authority(), types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            final PreparedAction plan = coordinator.prepare(undoContext, claim, current); final var request = new ActionRequest(plan.descriptor().id(), original.undo().orElseThrow().parameters(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            return await(execution.execute("pve", undoContext, current, request, plan, provider.prepareEffects(undoContext, current, request, plan), Optional.of(claim), context::authority));
        }
        List<String> kit() { return MobProjectionFixture.selected(source.resolve(target.entityId(), canonical.get(target.entityId())), abilities.get()); }
        WeaverRecoveryCoordinator recovery() { return new WeaverRecoveryCoordinator(journal, (actor, ref) -> router.submit(new EntityOwner(((EntityRef) ref).entityId()), actor, Duration.ofSeconds(5),
                () -> CompletableFuture.completedFuture(new SubjectSnapshot(ref, System.currentTimeMillis(), "capture", captureFacts(ref)))), registry, types); }
        @Override public void close() throws Exception { execution.close(); if (journal.ready()) await(journal.close()); else fails(journal.close()); }
    }
    private static void imprintReference() throws Exception {
        try (final Fixture f = new Fixture()) {
            f.abilities.set(Map.of("old_charge", ability("old_charge"), "source_charge", ability("source_charge")));
            final EntityRef sourceMob = new EntityRef(UUID.randomUUID());
            final var originalSource = profile(MobRank.BOSS, 20, "source_charge");
            f.canonical.put(sourceMob.entityId(), originalSource);
            final var originalTarget = f.canonical.get(f.target.entityId());
            final var captured = f.provider.exportValue(f.context, f.snapshot(sourceMob), "pve.export_imprint").value().orElseThrow();
            check(captured.payload().keySet().equals(Set.of("rank", "archetype", "template", "abilities")), "Imprint leaked noncombat state");
            for (String forbidden : List.of("uuid", "owner", "history", "receipt", "profile_revision", "economy")) {
                final Map<String, Object> corrupt = new HashMap<>(captured.payload()); corrupt.put(forbidden, "test");
                check(!f.types.require(PvEImprintCodec.TYPE).validate(corrupt).valid(), "Imprint accepted " + forbidden);
            }
            final var session = new ProviderContext(f.context.authority(), f.types, Lifetime.SESSION, IntegrityMode.SANDBOX);
            final var snapshot = f.snapshot();
            check(f.provider.validateImport(session, snapshot, "pve.import_imprint", captured).compatible(), "Imprint importer rejected native recipe");
            final var request = new ActionRequest("pve.apply_imprint", Map.of("value", captured), Lifetime.SESSION, IntegrityMode.SANDBOX);
            final var prepared = f.provider.prepare(session, snapshot, request);
            final var receipt = await(f.execution.execute("pve", session, snapshot, request, prepared, f.provider.prepareEffects(session, snapshot, request, prepared), session::authority));
            final var effective = f.source.resolve(f.target.entityId(), originalTarget);
            check(effective.rank() == MobRank.BOSS && effective.abilityIds().equals(List.of("source_charge")), "Imprint did not reach effective combat profile");
            check(f.canonical.get(sourceMob.entityId()).equals(originalSource) && f.canonical.get(f.target.entityId()).equals(originalTarget), "Imprint changed canonical source/target");
            check(f.snapshot().revisionFingerprint().equals(receipt.afterFingerprint()), "Imprint receipt differs from effective state");
            f.remove("pve.clear_projection", Map.of());
            check(f.kit().equals(List.of("old_charge")), "Clear did not restore original target kit");
            check(new WeaverInfluenceLookup(f.journal, System::currentTimeMillis).source(new RewardSource.Entity(f.target.entityId())) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "Clear laundered sandbox Imprint");
            final var live = new ProviderContext(session.authority(), f.types, Lifetime.SESSION, IntegrityMode.LIVE_GM);
            rejects(() -> f.provider.prepare(live, f.snapshot(), new ActionRequest("pve.apply_imprint", Map.of("value", captured), Lifetime.SESSION, IntegrityMode.LIVE_GM)));
        }
    }
    public static void main(final String[] args) throws Exception {
        imprintReference();
        directProjectionControls();
        try (final Fixture f = new Fixture()) {
            final CanonicalMobProfile original = f.canonical.get(f.target.entityId());
            final EntityRef boss = new EntityRef(UUID.randomUUID());
            f.abilities.set(Map.of("old_charge", ability("old_charge"), "new_charge", ability("new_charge")));
            f.canonical.put(boss.entityId(), profile(MobRank.BOSS, 60, "new_charge"));
            final WeaverValue value = f.registry.resolveCatalog(f.context, f.snapshot(), "pve.abilities", "new_charge");
            final WeaverValue thread = f.provider.exportValue(f.context, f.snapshot(boss), "pve.export_ability").value().orElseThrow();
            final var threadCase = new WeaverThreadCase(); threadCase.add(thread);
            check(f.provider.validateImport(f.context, f.snapshot(), "pve.import_ability", threadCase.active().orElseThrow().value()).compatible(), "new ability could not import after registry freeze");
            final int captures = f.captures.get(); f.provider.discover(f.snapshot()); final int afterCapture = f.captures.get();
            f.provider.inspect(f.context, f.snapshot(), FACET); check(f.captures.get() == afterCapture + 1 && afterCapture == captures + 1, "immutable discovery/inspect performed owner reads");
            final WeaverReceipt receipt = f.apply("pve.add_ability", value);
            check(f.kit().equals(List.of("new_charge")), "new registry ability did not reach actual native rank-capped selection");
            check(f.canonical.get(f.target.entityId()).equals(original), "projection changed canonical rank, level or kit");
            check(f.snapshot().revisionFingerprint().equals(receipt.afterFingerprint()), "predicted projection receipt differs from observed effective state");
            check(new WeaverInfluenceLookup(f.journal, System::currentTimeMillis).source(new RewardSource.Entity(f.target.entityId())) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "projection source not quarantined");
            final WeaverReceipt undone = f.undo(receipt);
            check(f.kit().equals(List.of("old_charge")) && f.snapshot().revisionFingerprint().equals(undone.afterFingerprint()), "conditional sever failed to restore effective kit");
            check(f.journal.snapshot().receipts().get(receipt.receiptId()).status() == ReceiptStatus.UNDONE && f.storage.audit.size() == 2, "Undo erased or failed to mark immutable original receipt");
            check(new WeaverInfluenceLookup(f.journal, System::currentTimeMillis).source(new RewardSource.Entity(f.target.entityId())) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "Undo washed monotonic entity influence");
        }
        try (final Fixture f = new Fixture()) {
            final WeaverReceipt receipt = f.apply("pve.override_rank", reference(RANK, "boss", "pve.rank", 1));
            f.apply("pve.override_rank", reference(RANK, "elite", "pve.rank", 2));
            rejects(() -> new WeaverUndoCoordinator(f.journal, f.registry).target(f.context.authority(), receipt.receiptId(), f.snapshot()));
            check(f.source.resolve(f.target.entityId(), f.canonical.get(f.target.entityId())).rank() == MobRank.ELITE, "external projection drift was overwritten");
        }
        try (final Fixture f = new Fixture()) {
            f.abilities.set(Map.of("old_charge", ability("old_charge"), "new_charge", ability("new_charge")));
            final MobTemplate template = new MobTemplate("fixture_template", 1, "Fixture", "ZOMBIE", "", 1, 100, MobRank.BOSS, MobArchetype.RANGED,
                    MobTemplate.StatProfile.baseline(), List.of("new_charge"), Set.of(), Set.of(), "canonical_loot", Set.of(), "fixture", "canonical_bestiary", List.of());
            f.templates.set(Map.of(template.mobId(), template));
            f.apply("pve.apply_template_projection", reference(TEMPLATE, template.mobId(), "pve.template", 1));
            final EffectiveMobProjection projected = f.source.resolve(f.target.entityId(), f.canonical.get(f.target.entityId()));
            check(projected.templateId().equals(template.mobId()) && projected.behavior().equals(template.behavior()) && projected.rank() == MobRank.NORMAL
                    && projected.archetype().equals(Optional.of(MobArchetype.RANGED)) && f.kit().equals(List.of("new_charge")), "template combat profile did not reach effective consumer");
            f.apply("pve.override_archetype", reference(ARCHETYPE, "healer", "pve.archetype", 1));
            check(f.source.resolve(f.target.entityId(), f.canonical.get(f.target.entityId())).archetype().equals(Optional.of(MobArchetype.HEALER)), "explicit archetype lost to template default");
            f.apply("pve.remove_ability", reference(ABILITY, "new_charge", "pve.ability", 1)); check(f.kit().isEmpty(), "ability suppression not consumed");
            f.apply("pve.add_ability", reference(ABILITY, "new_charge", "pve.ability", 1)); check(f.kit().equals(List.of("new_charge")), "latest member add/remove precedence failed");
            final var restricted = new MobAbilityDefinition("boss_only", MobAbilityDefinition.Kind.LUNGE, 40, 10, 0, 4, 2, 0,
                    MobAbilityDefinition.TargetRule.CURRENT_TARGET, false, Set.of(MobRank.BOSS), Set.of(), Map.of());
            final var definitions = new HashMap<>(f.abilities.get()); definitions.put("boss_only", restricted); f.abilities.set(Map.copyOf(definitions));
            final WeaverValue value = reference(ABILITY, "boss_only", "pve.ability", 1);
            check(!f.provider.validateImport(f.context, f.snapshot(), "pve.import_ability", value).compatible(), "ineligible boss-only Thread accepted by normal rank");
            rejects(() -> f.provider.prepare(f.context, f.snapshot(), f.request("pve.add_ability", value)));
        }
        try (final Fixture f = new Fixture()) {
            final ProviderContext session = new ProviderContext(f.context.authority(), f.types, Lifetime.SESSION, IntegrityMode.SANDBOX);
            final SubjectSnapshot before = f.snapshot(); final var request = new ActionRequest("pve.override_rank", Map.of("value", reference(RANK, "boss", "pve.rank", 1)), Lifetime.SESSION, IntegrityMode.SANDBOX);
            final var plan = f.provider.prepare(session, before, request);
            await(f.execution.execute("pve", session, before, request, plan, f.provider.prepareEffects(session, before, request, plan), session::authority));
            await(f.journal.expireProjections(System.currentTimeMillis(), true));
            check(f.source.resolve(f.target.entityId(), f.canonical.get(f.target.entityId())).rank() == MobRank.NORMAL, "logout/disable session cleanup left an effective rank");
            check(new WeaverInfluenceLookup(f.journal, System::currentTimeMillis).source(new RewardSource.Entity(f.target.entityId())) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "session cleanup washed taint");
        }
        try (final Fixture f = new Fixture()) {
            final SubjectSnapshot stale = f.snapshot(); final var request = f.request("pve.override_rank", reference(RANK, "boss", "pve.rank", 1));
            final var plan = f.provider.prepare(f.context, stale, request); f.canonical.put(f.target.entityId(), profile(MobRank.NORMAL, 12, "old_charge"));
            fails(f.execution.execute("pve", f.context, stale, request, plan, f.provider.prepareEffects(f.context, stale, request, plan), f.context::authority));
            check(f.journal.snapshot().projections().isEmpty(), "stale GUI changed projection state");
        }
        try (final Fixture f = new Fixture()) {
            final SubjectSnapshot before = f.snapshot(); final var request = f.request("pve.override_rank", reference(RANK, "boss", "pve.rank", 1));
            final var plan = f.provider.prepare(f.context, before, request); final long now = System.currentTimeMillis();
            final var op = new WeaverOperationRecord(plan.operationId(), f.context.authority().actor(), "pve", request, before.ref(), before.revisionFingerprint(), Optional.empty(), plan.recoveryPayload(), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false);
            await(f.journal.prepare(op));
            final StageResult result = await(f.router.submit(plan.stages().getFirst().owner(), f.context.authority().actor(), Duration.ofSeconds(5), () -> plan.stages().getFirst().apply().execute(new ExecutionContext(f.context.authority(), before, List.of()), Map.of())));
            final WeaverReceipt receipt = plan.receiptFactory().create(plan, List.of(result), System.currentTimeMillis());
            final PreparedEffects plannedEffects = f.provider.prepareEffects(f.context, before, request, plan);
            f.apply("pve.override_archetype", reference(ARCHETYPE, "ranged", "pve.archetype", 1));
            final WeaverEffectCommit effects = plannedEffects.factory().create(plan, List.of(result), receipt, 2);
            fails(f.journal.applied(op.operationId(), 0, receipt, effects, System.currentTimeMillis()));
            check(f.journal.ready() && f.journal.snapshot().projections().size() == 1, "late publication conflict failed closed incorrectly");
        }
        for (int boundary = 1; boundary <= 4; boundary++) for (final boolean afterWrite : List.of(false, true)) {
            final Storage storage = new Storage(); final EntityRef target = new EntityRef(UUID.randomUUID());
            try (final Fixture f = new Fixture(storage, target)) {
                storage.failAt = boundary; storage.afterWrite = afterWrite;
                try { f.apply("pve.override_rank", reference(RANK, "boss", "pve.rank", 1)); throw new AssertionError("Expected crash"); } catch (final ExecutionException expected) { }
            }
            storage.failAt = 0;
            try (final Fixture restarted = new Fixture(storage, target)) {
                restarted.loaded.set(false); final var recovery = restarted.recovery(); await(recovery.start());
                if (boundary > 1 || afterWrite) check(!recovery.pending().isEmpty() || restarted.journal.snapshot().operations().values().iterator().next().status() == OperationStatus.COMMITTED, "unloaded entity was force resolved");
                restarted.loaded.set(true); await(recovery.entityAvailable(target.entityId()));
                final boolean applied = boundary > 2 || boundary == 2 && afterWrite;
                check((restarted.source.resolve(target.entityId(), restarted.canonical.get(target.entityId())).rank() == MobRank.BOSS) == applied, "restart replayed/lost projection at boundary " + boundary + "/" + afterWrite);
                check(restarted.journal.snapshot().operations().values().stream().allMatch(op -> op.status() == OperationStatus.COMMITTED || op.status() == OperationStatus.ABORTED), "observed recovery did not settle");
                check(storage.audit.size() <= 1, "crash recovery duplicated audit"); recovery.close();
            }
        }
        System.out.println("PvE Weaver projection passed: post-freeze boss export/compatible mob import into native kit selection, immutable canonical state, quarantine/Undo, drift, typed projection picker/selected sever/subject-scoped clear, atomic publication fence and sixteen create/clear crash boundaries.");
    }
    private static void directProjectionControls() throws Exception {
        try (final Fixture f = new Fixture()) {
            check(!f.registry.discover(f.snapshot()).providers().get("pve").actions().contains(PvEProjectionActions.CLEAR), "empty projection removal exposed");
            final var rank = f.apply("pve.override_rank", reference(RANK, "boss", "pve.rank", 1));
            f.apply("pve.override_archetype", reference(ARCHETYPE, "ranged", "pve.archetype", 1));
            final var discovery = f.registry.discover(f.snapshot()).providers().get("pve");
            check(discovery.actions().containsAll(Set.of(PvEProjectionActions.CLEAR, PvEProjectionActions.SEVER))
                    && discovery.catalogs().contains(PvEProjectionActions.CATALOG), "generic frontend cannot reach projection selector/removal");
            final var first = f.registry.catalogPage(f.context, f.snapshot(), PvEProjectionActions.CATALOG, new CatalogQuery("", 0, 1));
            final var second = f.registry.catalogPage(f.context, f.snapshot(), PvEProjectionActions.CATALOG, new CatalogQuery("", 1, 1));
            check(first.hasNext() && !second.hasNext() && first.entries().size() == 1 && second.entries().size() == 1, "projection selector pagination failed");
            final var selected = f.registry.resolveCatalog(f.context, f.snapshot(), PvEProjectionActions.CATALOG, rank.operationId().toString());
            check(selected.payload().get("provider").equals("pve") && selected.sourceCapabilities().contains("pve.projection"), "projection selector produced untyped reference");
            final var foreign = new WeaverValue(selected.type(), Map.of("id", rank.operationId().toString(), "provider", "foreign"), "pve", FACET, Set.of("pve.projection"), 1);
            final var oneShot = new ProviderContext(f.context.authority(), f.types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            rejects(() -> f.provider.prepare(oneShot, f.snapshot(), new ActionRequest(PvEProjectionActions.SEVER, Map.of("value", foreign), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX)));
            f.remove(PvEProjectionActions.SEVER, Map.of("value", selected));
            check(f.source.active(f.target.entityId()).size() == 1 && f.source.resolve(f.target.entityId(), f.canonical.get(f.target.entityId())).rank() == MobRank.NORMAL, "selected removal altered other projection");
            rejects(() -> f.registry.resolveCatalog(f.context, f.snapshot(), PvEProjectionActions.CATALOG, rank.operationId().toString()));
            rejects(() -> f.provider.prepare(oneShot, f.snapshot(), new ActionRequest(PvEProjectionActions.SEVER, Map.of("value", selected), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX)));
            final var other = new EntityRef(UUID.randomUUID()); f.canonical.put(other.entityId(), profile(MobRank.NORMAL, 12, "old_charge"));
            final var otherSnapshot = f.snapshot(other); final var request = f.request("pve.override_rank", reference(RANK, "elite", "pve.rank", 1));
            final var plan = f.provider.prepare(f.context, otherSnapshot, request);
            await(f.execution.execute("pve", f.context, otherSnapshot, request, plan, f.provider.prepareEffects(f.context, otherSnapshot, request, plan), f.context::authority));
            final var cleared = f.remove(PvEProjectionActions.CLEAR, Map.of());
            check(cleared.undo().isEmpty() && f.source.active(f.target.entityId()).isEmpty() && f.source.active(other.entityId()).size() == 1, "clear crossed subject boundary or advertised force Undo");
            check(f.journal.snapshot().receipts().containsKey(rank.receiptId()) && new WeaverInfluenceLookup(f.journal, System::currentTimeMillis)
                    .source(new RewardSource.Entity(f.target.entityId())) == InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED, "clear erased receipt or influence");
        }
        for (int boundary = 1; boundary <= 4; boundary++) for (boolean after : List.of(false, true)) {
            final var storage = new Storage(); final var target = new EntityRef(UUID.randomUUID());
            try (final Fixture f = new Fixture(storage, target)) {
                f.apply("pve.override_rank", reference(RANK, "boss", "pve.rank", 1));
                storage.failAt = storage.writes + boundary; storage.afterWrite = after;
                try { f.remove(PvEProjectionActions.CLEAR, Map.of()); throw new AssertionError("clear crash not injected"); } catch (ExecutionException expected) { }
            }
            storage.failAt = 0;
            try (final Fixture f = new Fixture(storage, target)) {
                final var recovery = f.recovery(); await(recovery.start());
                final boolean removed = boundary > 2 || boundary == 2 && after;
                check(f.source.active(target.entityId()).isEmpty() == removed, "clear recovery lost or repeated mutation at " + boundary + "/" + after);
                check(f.journal.snapshot().operations().values().stream().allMatch(op -> op.status() == OperationStatus.COMMITTED || op.status() == OperationStatus.ABORTED), "clear recovery did not reconcile");
                recovery.close();
            }
        }
    }
}
