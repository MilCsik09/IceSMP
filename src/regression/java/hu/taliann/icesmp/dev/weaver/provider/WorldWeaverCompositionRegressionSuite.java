package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.RewardSource;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import hu.taliann.icesmp.managers.GatheringBuffManager;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.time.Duration;
import java.lang.reflect.Proxy;

public final class WorldWeaverCompositionRegressionSuite {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(10, TimeUnit.SECONDS); }
    private static final class Storage implements WeaverJournalStorage {
        private final WeaverJournalCodec codec;
        private Map<String, Object> state;
        private Map<String, WeaverAuditEntry> audit = Map.of();
        Storage(WeaverJournalCodec codec) { this.codec = codec; }
        public WeaverJournalState readState() { return state == null ? WeaverJournalState.empty() : codec.decodeState(state); }
        public Map<String, WeaverAuditEntry> readAudit() { return audit; }
        public void writeState(WeaverJournalState next) { state = codec.encodeState(next); }
        public void writeAudit(Map<String, WeaverAuditEntry> next) { audit = codec.decodeAudit(codec.encodeAudit(next)); }
    }
    private static final class Fixture implements AutoCloseable {
        final WeaverTypeRegistry types = new WeaverTypeRegistry();
        final WorldWeaverProviderRegistry registry = new WorldWeaverProviderRegistry(types, () -> 0L);
        final WeaverAuthorityToken authority = WeaverProviderTestContext.sandbox(types).authority();
        final Storage storage; final WeaverJournal journal; final DeveloperWeaverProvider bindings; final JournalProjectionSource source;
        final WeaverOwnerRouter router = new WeaverOwnerRouter() {
            public <T> CompletionStage<T> submit(ExecutionOwner owner, UUID actor, Duration timeout, Supplier<CompletionStage<T>> operation) {
                try { return operation.get(); } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
            }
            public void close() { }
        };
        final WeaverDurableExecutionCoordinator executor;
        Fixture() throws Exception {
            ScalarTypeCodec.registerBuiltins(types);
            types.register(ScalarTypeCodec.reference(TrashWeaverProvider.RULE, TrashWeaverFieldActions.rules()::containsKey));
            storage = new Storage(new WeaverJournalCodec(types));
            journal = new WeaverJournal(storage, projection -> registry.projectionConsumers().validate(projection));
            source = new JournalProjectionSource(journal, registry::projectionConsumers); bindings = new DeveloperWeaverProvider(source);
            registry.register(bindings); registry.freezeAndValidate(); await(journal.load()); executor = new WeaverDurableExecutionCoordinator(router, journal, types);
        }
        WeaverReceipt bind(AreaRef area, String action, Lifetime lifetime) throws Exception {
            final var context = new ProviderContext(authority, types, lifetime, IntegrityMode.SANDBOX);
            final var descriptor = registry.actions().get(action);
            final var snapshot = descriptor.revisionScope().apply(new SubjectSnapshot(area, System.currentTimeMillis(), "fixture", bindings.captureOnOwner(area)));
            final var request = new ActionRequest(action, Map.of(), lifetime, IntegrityMode.SANDBOX);
            final var prepared = bindings.prepare(context, snapshot, request);
            return await(executor.execute("developer", context, snapshot, request, prepared, bindings.prepareEffects(context, snapshot, request, prepared), () -> authority));
        }
        public void close() throws Exception { executor.close(); await(journal.close()); }
    }
    private static void binding() throws Exception {
        final var budget = new WeaverBindingBudget(1000);
        check(!budget.tryFire(999, 0) && !budget.tryFire(1000, 1), "Binding accepted early or recursive signal");
        for (int i = 0; i < 16; i++) { check(budget.tryFire(1000 + i * 2000, 0), "Binding budget lost execution"); check(!budget.tryFire(1001 + i * 2000, 0), "Binding bypassed cooldown"); }
        check(!budget.tryFire(40_000, 0), "Binding exceeded count"); check(!new WeaverBindingBudget(1000).tryFire(121_000, 0), "Binding survived expiry");
        try (final var f = new Fixture()) {
            final var area = new AreaRef(UUID.randomUUID(), new RadiusArea(0, 64, 0, 8));
            final var receipt = f.bind(area, "developer.bind_area_vfx", Lifetime.SESSION);
            check(f.source.active("developer.area_enter_vfx", area, System.currentTimeMillis()).size() == 1, "Binding missing from committed journal");
            check(f.storage.readState().receipts().containsKey(receipt.receiptId()), "Binding failed codec round trip");
            await(f.bindings.reconcileProjections(Set.of(area)));
            final var count = new AtomicInteger();
            final var world = (org.bukkit.World) Proxy.newProxyInstance(org.bukkit.World.class.getClassLoader(), new Class<?>[]{org.bukkit.World.class}, (proxy, method, args) -> {
                if (method.getName().equals("getUID")) return area.worldId(); throw new AssertionError("Unexpected world access: " + method.getName());
            });
            final var player = (org.bukkit.entity.Player) Proxy.newProxyInstance(org.bukkit.entity.Player.class.getClassLoader(), new Class<?>[]{org.bukkit.entity.Player.class}, (proxy, method, args) -> {
                if (method.getName().equals("spawnParticle")) { count.incrementAndGet(); return null; } throw new AssertionError("Unexpected player access: " + method.getName());
            });
            final var entered = new org.bukkit.event.player.PlayerMoveEvent(player, new org.bukkit.Location(world, 30, 64, 0), new org.bukkit.Location(world, 0, 64, 0));
            f.bindings.entered(entered); f.bindings.entered(entered); check(count.get() == 1, "Committed Binding did not emit exactly one cooldown-limited VFX");
            f.bindings.clearSession(); f.bindings.entered(entered); check(count.get() == 1, "Session close left Binding signal active");
            f.bind(area, "developer.clear_area_bindings", Lifetime.ONE_SHOT);
            check(f.source.active("developer.area_enter_vfx", area, System.currentTimeMillis()).isEmpty(), "Sever left Binding persisted");
        }
    }
    private static void createdQuarantine(String scenario) throws Exception {
        try (final var f = new Fixture()) {
            final EntityRef input = new EntityRef(UUID.randomUUID()); final UUID created = scenario.equals("source") ? input.entityId() : UUID.randomUUID(), event = UUID.randomUUID();
            final var eventTarget = WeaverInfluenceTarget.exact(new RewardSource.Event("fixture", event));
            final var context = new ProviderContext(f.authority, f.types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            final var descriptor = new ActionDescriptor("fixture.fork", "fixture.combat", Component.text("Fork"), RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX),
                    Set.of(IntegrityImpact.TAINT_CREATED), Set.of(WeaverSubjectKind.ENTITY), List.of(), AreaSupport.NONE, Optional.empty(), false, Optional.empty(), 10);
            final var snapshot = new SubjectSnapshot(input, System.currentTimeMillis(), "source-unchanged", Map.of());
            final var output = new WeaverValue(WeaverTypeId.parse("weaver:uuid@1"), Map.of("value", created.toString()), "fixture", "fixture.combat", Set.of(), System.currentTimeMillis());
            final var stage = new ExecutionStage("fixture.spawn", new EntityOwner(input.entityId()), Map.of(), (execution, payload) -> {
                check(f.journal.influenceIndex().quarantined(eventTarget.source(), System.currentTimeMillis()), "Spawn entered before durable quarantine");
                check(!f.journal.influenceIndex().quarantined(new RewardSource.Entity(input.entityId()), System.currentTimeMillis()), "Read-only source became quarantined");
                return CompletableFuture.completedFuture(new StageResult("source-unchanged", scenario.equals("unproven") ? Map.of() : Map.of(WeaverOperationScope.CREATED_ENTITY, output), Map.of()));
            }, Optional.empty(), 5000);
            final var prepared = new PreparedAction(UUID.randomUUID(), descriptor, input, "source-unchanged", List.of(stage), new OperationRecoveryPayload(1, Map.of()),
                    (plan, results, now) -> new WeaverReceipt(UUID.randomUUID(), plan.operationId(), "fixture", descriptor.id(), input, descriptor.risk(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX,
                            "source-unchanged", "source-unchanged", Map.of(), scenario.equals("missing") ? Map.of() : Map.of(WeaverOperationScope.CREATED_ENTITY, output), Optional.empty(), now, ReceiptStatus.COMMITTED));
            final var request = new ActionRequest(descriptor.id(), Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            try {
                await(f.executor.execute("fixture", context, snapshot, request, prepared, new PreparedEffects(new WeaverEffectIntent(scenario.equals("empty") ? Set.of() : Set.of(eventTarget)), (plan, results, receipt, sequence) -> WeaverEffectCommit.none()), () -> f.authority));
                check(scenario.equals("valid"), "Invalid created entity evidence committed: " + scenario);
            } catch (ExecutionException rejected) {
                if (scenario.equals("valid")) throw rejected;
                check(f.journal.snapshot().receipts().isEmpty(), "Rejected creation published a receipt");
                check(!f.journal.influenceIndex().quarantined(new RewardSource.Entity(input.entityId()), System.currentTimeMillis()), "Invalid output tainted read-only source");
                return;
            }
            check(f.journal.influenceIndex().quarantined(new RewardSource.Entity(created), System.currentTimeMillis()), "Created UUID lacks durable reward denial");
            check(!f.journal.influenceIndex().quarantined(new RewardSource.Entity(input.entityId()), System.currentTimeMillis()), "Committed Fork changed original source quarantine");
            check(f.storage.readState().influences().size() == 2, "Created event/entity evidence failed restart codec");
        }
    }
    private static void nativeFields() throws Exception {
        try (final var f = new Fixture()) {
            final var nativeFields = new hu.taliann.icesmp.trash.TrashRuleFieldService();
            final var adapter = new TrashWeaverFieldActions(nativeFields);
            final var area = new AreaRef(UUID.randomUUID(), new RadiusArea(0, 64, 0, 8));
            final var context = new ProviderContext(f.authority, f.types, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
            for (final String rule : TrashWeaverFieldActions.rules().keySet()) {
                final var action = adapter.descriptors().stream().filter(a -> a.id().equals(TrashWeaverFieldActions.CREATE)).findFirst().orElseThrow();
                final var snapshot = action.revisionScope().apply(new SubjectSnapshot(area, System.currentTimeMillis(), "fields", adapter.capture(area)));
                final var value = new WeaverValue(TrashWeaverProvider.RULE, Map.of("id", rule), "trash", TrashWeaverProvider.FACET, Set.of("trash.rule"), System.currentTimeMillis());
                final var request = new ActionRequest(action.id(), Map.of("rule", value), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
                final var prepared = adapter.prepare(context, snapshot, request);
                final var receipt = await(f.executor.execute("trash", context, snapshot, request, prepared, adapter.effects(prepared), () -> f.authority));
                check(nativeFields.snapshot().fields().size() == 1, "Native field missing after commit");
                check(f.journal.influenceIndex().quarantined(new RewardSource.Event("trash.rule_field", receipt.operationId()), System.currentTimeMillis()), "Native field has no quarantine");
                adapter.clearSession(); check(nativeFields.snapshot().fields().isEmpty(), "Session leaked native field");
                final var fresh = action.revisionScope().apply(new SubjectSnapshot(area, System.currentTimeMillis(), "fields", adapter.capture(area)));
                final var stale = adapter.prepare(context, fresh, request); adapter.clearSession();
                try { await(f.executor.execute("trash", context, fresh, request, stale, adapter.effects(stale), () -> f.authority)); throw new AssertionError("Retired session created field"); }
                catch (ExecutionException expected) { check(nativeFields.snapshot().fields().isEmpty(), "Retired callback leaked field"); }
            }
            check(!hu.taliann.icesmp.trash.TrashAnomalyActivationService.sandboxPreviewable("unknown"), "Unknown preview accepted");
            check(Arrays.stream(hu.taliann.icesmp.trash.TrashAnomalyBehavior.values()).filter(b -> hu.taliann.icesmp.trash.TrashAnomalyActivationService.sandboxPreviewable(b.name())).count() == 5, "Unsafe preview whitelist expanded");
        }
    }
    public static void main(String[] args) throws Exception {
        binding(); nativeFields();
        for (String scenario : List.of("valid", "empty", "source", "unproven", "missing")) createdQuarantine(scenario);
        for (final var kind : GatheringBuffManager.GatheringBuff.values()) {
            check(!new GatheringBuffManager.Window(UUID.randomUUID(), kind, 1000, true).rewardsEnabled(1), "Sandbox event enabled rewards");
            check(new GatheringBuffManager.Window(UUID.randomUUID(), kind, 1000, false).rewardsEnabled(1), "Live event lost native rewards");
            check(!new GatheringBuffManager.Window(UUID.randomUUID(), kind, 1000, false).rewardsEnabled(1000), "Expired event enabled rewards");
        }
        System.out.println("WorldWeaver composition passed: journal-to-Binding signal, limits/sever/session cleanup; read-only Fork input and durable created event/entity denial; native window reward policy.");
    }
}
