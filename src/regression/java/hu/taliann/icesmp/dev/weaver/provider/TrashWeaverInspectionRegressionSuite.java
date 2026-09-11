package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.gui.WeaverFacetView;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.trash.*;
import hu.taliann.icesmp.integrity.*;
import org.bukkit.Material;
import java.util.*;
import java.util.concurrent.atomic.*;
import static hu.taliann.icesmp.dev.weaver.provider.TrashWeaverProvider.*;

/** Native detached records, generic registry and dynamic catalog publication; no simulated player event. */
public final class TrashWeaverInspectionRegressionSuite {
    private static int assertions;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
    private static void refuses(Runnable action) {
        try { action.run(); throw new AssertionError("refusal expected"); }
        catch (WeaverDomainRejection | IllegalArgumentException | IllegalStateException | SecurityException expected) { assertions++; }
    }
    private static TrashDefinition definition(String id) {
        return new TrashDefinition(id, "Tesztlelet", "Trash", Material.STICK, "test:" + id, "test/" + id, 1,
                List.of(), new TrashSourceBias("GLOBAL", Set.of()), TrashKind.STORY, "", "", new TrashArchaeologyEvidence("wood", "records", "Farostok.",
                        List.of("Kézzel rótt jel.", "Hivatali forma."), ""));
    }
    public static void main(String[] args) {
        TrashWeaverActionRegressionSuite.main(args);
        final var definitions = new AtomicReference<>(Map.of("first", definition("first")));
        final var types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        final var reads = new AtomicInteger(); final var provider = new TrashWeaverProvider(types, definitions::get, ref -> {
            reads.incrementAndGet(); return itemFacts(new TrashHistoryService.ItemInspection(definitions.get().values().iterator().next(), "base", Optional.empty(), Optional.empty(), Optional.empty()), Map.of(), 1);
        });
        final var registry = new WorldWeaverProviderRegistry(types, () -> 1L); registry.register(provider); registry.freezeAndValidate();
        final var context = WeaverProviderTestContext.sandbox(types);
        final var ref = new ItemSlotRef(UUID.randomUUID(), WeaverSlot.named(WeaverSlot.Kind.MAIN_HAND), Optional.empty(), Optional.empty(), OptionalLong.empty(), "a".repeat(64));
        final var snapshot = new SubjectSnapshot(ref, 1, "capture", provider.captureOnOwner(ref));
        final var views = WeaverFacetView.discover(registry, registry.discover(snapshot));
        check(views.size() == 1 && views.getFirst().catalogs().size() == 2, "generic frontend discovers native Trash facet/catalogs");
        check(views.getFirst().actions().isEmpty() && views.getFirst().imports().isEmpty() && views.getFirst().exports().isEmpty(), "unfinished mutation/clone paths are absent");
        final var inspection = provider.inspect(context, snapshot, FACET);
        check(inspection.facts().containsKey("trash.archaeology.authored_1") && !inspection.facts().containsKey("trash.instance"), "fresh story evidence derives without inventing tracked identity");
        final var catalog = provider.catalog(context, snapshot, "trash.identities").orElseThrow();
        check(catalog.resolve("first").isPresent(), "canonical identity discovered");
        definitions.set(Map.of("second", definition("second")));
        check(catalog.resolve("first").isEmpty() && catalog.resolve("second").isPresent(), "existing catalog observes canonical replacement without copied roster");
        check(!types.require(IDENTITY).validate(Map.of("id", "first")).valid() && types.require(IDENTITY).validate(Map.of("id", "second")).valid(), "typed identity rejects removed authority");
        check(provider.catalog(context, snapshot, "trash.rules").orElseThrow().page(new CatalogQuery("", 0, 45)).entries().size() == TrashRuleFieldService.FieldKind.values().length, "rule vocabulary is native enum derived");
        check(reads.get() == 1, "discovery, inspection and catalog never reread a live item");
        check(provider.discover(new SubjectSnapshot(ref, 1, "empty", Map.of())).facets().isEmpty(), "non-Trash item gets no false facet");
        final var valid = new AtomicBoolean(true); final var revoked = WeaverProviderTestContext.sandbox(types, valid::get); valid.set(false);
        refuses(() -> provider.inspect(revoked, snapshot, FACET));
        refuses(() -> provider.catalog(revoked, snapshot, "trash.identities"));
        refuses(() -> provider.prepare(context, snapshot, new ActionRequest("trash.individualize_unit", Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX)));
        check(provider.coverage().domains().get("trash.inspection_subset").level() == CoverageLevel.INSPECT_ONLY_BY_DESIGN, "no full-domain completion claim");
        boundedHistory(types); fields(types); pendingEffects(types); causalFields();
        System.out.println("Trash Weaver inspection passed. assertions=" + assertions);
    }
    private static void boundedHistory(WeaverTypeRegistry types) {
        final var events = new ArrayList<TrashHistoryStore.HistoryEntry>(); final Set<UUID> owners = new HashSet<>();
        for (int index = 0; index < 64; index++) { owners.add(UUID.randomUUID()); events.add(new TrashHistoryStore.HistoryEntry(index + 1, TrashHistoryEvent.REPAIRED, index, UUID.randomUUID(), "x".repeat(96))); }
        final var history = new TrashHistoryStore.Snapshot(UUID.randomUUID(), "second", "base", 64, 0, 64, events, owners);
        final var item = new TrashHistoryService.ItemInspection(definition("second"), "base", Optional.of(TrashHistoryEvent.CREATED_MOB_DROP), Optional.of(history), Optional.empty());
        final var facts = itemFacts(item, Map.of(TrashAnomalyStateStore.MemoryKey.LOCAL_PLAYER_DEATHS, 1_000_000_000L), 1);
        final var bounded = new InspectionResult(FACET, facts, List.of());
        check(bounded.facts().keySet().stream().filter(key -> key.startsWith("trash.history.")).count() == 64, "all retained history entries fit inspection without truncation");
        check(facts.get("trash.owners").payload().get("value").toString().split("\n").length == 64, "all native bounded owners retained");
        for (final var value : facts.values()) { types.require(value.type()).validate(value.payload()).requireValid(); assertions++; }
        check(facts.containsKey("trash.archaeology.repaired") && facts.get("trash.memory_scope").payload().get("value").equals("RUNTIME_OBSERVATION_NOT_DURABILITY_PROOF"), "derived repair and honest runtime-memory scope");
        check(TrashArchaeologyFactEngine.evaluate(definition("different"), Optional.of(history), 50).isEmpty(), "cross-identity archaeology evidence refused");
        check(TrashArchaeologyFactEngine.evaluate(definition("second"), Optional.of(history), 51).isEmpty(), "out-of-range derivation refused");
    }
    private static void fields(WeaverTypeRegistry types) {
        final var clock = new AtomicLong(100); final var service = new TrashRuleFieldService(clock::get);
        final UUID world = UUID.randomUUID(), other = UUID.randomUUID(), actor = UUID.randomUUID();
        for (int index = 0; index < 32; index++) check(service.add(new TrashRuleFieldService.RuleField(UUID.randomUUID(), TrashRuleFieldService.FieldKind.CEASEFIRE,
                new TrashRuleFieldService.Point(world, index, 64, 0), 1, 200, actor, null)), "native field admitted");
        check(service.add(new TrashRuleFieldService.RuleField(UUID.randomUUID(), TrashRuleFieldService.FieldKind.ACOUSTIC_NULL,
                new TrashRuleFieldService.Point(other, 0, 64, 0), 1, 200, actor, null)), "other-world field admitted");
        service.claim(new TrashRuleFieldService.Point(world, 0, 64, 0), TrashRuleFieldService.FieldKind.CEASEFIRE).orElseThrow();
        final var before = service.snapshot(); final var facts = fieldFacts(before, world, 100);
        check(facts.size() == 35 && facts.values().stream().noneMatch(value -> value.payload().toString().contains(other.toString())), "world-local bounded field view excludes other world");
        check(facts.values().stream().anyMatch(value -> value.payload().toString().contains("claimed=true")), "native in-flight claim visible");
        for (final var value : facts.values()) { types.require(value.type()).validate(value.payload()).requireValid(); assertions++; }
        new InspectionResult(FACET, facts, List.of());
        check(fieldFacts(before, world, 201).values().stream().noneMatch(value -> value.payload().toString().contains("active=true")), "expired retained fields not reported active");
        check(service.snapshot().equals(before), "inspection neither expires nor claims fields");
        service.close(); refuses(() -> fieldFacts(service.snapshot(), world, 100));
        final var pendingService = new TrashRuleFieldService(clock::get);
        final var reserved = new TrashRuleFieldService.RuleField(UUID.randomUUID(), TrashRuleFieldService.FieldKind.PROJECTILE_WALL,
                new TrashRuleFieldService.Point(world, 0, 64, 0), 1, 200, actor, UUID.randomUUID().toString());
        pendingService.reserveCreation(reserved).orElseThrow();
        pendingService.reserveCreation(new TrashRuleFieldService.RuleField(UUID.randomUUID(), TrashRuleFieldService.FieldKind.CEASEFIRE,
                new TrashRuleFieldService.Point(other, 0, 64, 0), 1, 200, actor, null)).orElseThrow();
        final var pendingSnapshot = pendingService.snapshot(); final var pendingFacts = fieldFacts(pendingSnapshot, world, 100);
        check(pendingFacts.get("trash.fields_count").payload().get("value").equals("0")
                && pendingFacts.get("trash.preparing_count").payload().get("value").equals("1"), "preparation reported as active field");
        check(pendingFacts.values().stream().anyMatch(value -> value.payload().toString().contains("PREPARING_NO_ACTIVE_EFFECT"))
                && pendingFacts.values().stream().noneMatch(value -> value.payload().toString().contains(other.toString())), "pending field scope leaks another world");
        check(pendingService.snapshot().equals(pendingSnapshot), "inspection changed preparation");
        for (final var value : pendingFacts.values()) { types.require(value.type()).validate(value.payload()).requireValid(); assertions++; }
    }

    private static void pendingEffects(WeaverTypeRegistry types) {
        final UUID world = UUID.randomUUID(), other = UUID.randomUUID(), actor = UUID.randomUUID();
        final var service = new TrashRuleFieldService(() -> 100);
        final List<TrashHistoryStore.WallReceipt> receipts = new ArrayList<>();
        for (int index = 0; index < 34; index++) {
            final var field = new TrashRuleFieldService.RuleField(UUID.randomUUID(),
                    TrashRuleFieldService.FieldKind.PROJECTILE_WALL,
                    new TrashRuleFieldService.Point(index == 33 ? other : world, index, 64, 0),
                    2, 200, actor, UUID.randomUUID().toString());
            receipts.add(new TrashHistoryStore.WallReceipt(field.id(), actor, field.center().world(),
                    UUID.randomUUID(), UUID.randomUUID(), 3, "second", "consumed", 100, field, 1));
        }
        final var receipt = receipts.getFirst();
        final var history = new TrashHistoryStore.Snapshot(receipt.instanceId(), "second", "consumed", 3,
                1, 100, List.of(new TrashHistoryStore.HistoryEntry(3, TrashHistoryEvent.TRANSFORMED, 100, actor, "")), Set.of(actor));
        final var item = new TrashHistoryService.ItemInspection(definition("second"), "consumed", Optional.empty(),
                Optional.of(history), Optional.of(receipt));
        final var facts = itemFacts(item, Map.of(), 100);
        check(facts.get("trash.pending_effect").payload().get("value").equals("PROJECTILE_REMOVAL_UNOBSERVED"),
                "pending consumption became effect completion");
        check(facts.get("trash.pending_operation").payload().get("value").equals(receipt.operationId().toString())
                        && facts.get("trash.revision").payload().get("value").equals("3"),
                "item view lost exact native pending operation/revision");
        check(facts.get("trash.pending_scope").payload().get("value").toString().contains("NOT_EFFECT_COMPLETION_PROOF"),
                "item inspection claims physical effect completion");
        refuses(() -> new TrashHistoryService.ItemInspection(definition("second"), "consumed", Optional.empty(),
                Optional.empty(), Optional.of(receipt)));
        refuses(() -> new TrashHistoryService.ItemInspection(definition("second"), "base", Optional.empty(),
                Optional.of(history), Optional.of(receipt)));
        refuses(() -> new TrashHistoryService.ItemInspection(definition("second"), "consumed", Optional.empty(),
                Optional.of(history), Optional.of(receipts.get(1))));
        final var worldFacts = fieldFacts(service.snapshot(), world, 300, receipts);
        check(worldFacts.get("trash.pending_count").payload().get("value").equals("33")
                        && worldFacts.get("trash.pending_truncated").payload().get("value").equals("true"),
                "bounded world view conceals pending operation truncation");
        check(worldFacts.keySet().stream().filter(key -> key.startsWith("trash.pending.")).count() == 32,
                "pending operation rows exceed facet budget");
        check(worldFacts.values().stream().noneMatch(value -> value.payload().toString().contains(receipts.getLast().operationId().toString())),
                "world view leaks another world's pending operation");
        check(worldFacts.get("trash.pending_scope").payload().get("value").toString().contains("NOT_EFFECT_COMPLETION_PROOF"),
                "expired operation becomes completed or disappears without observation");
        for (final var value : facts.values()) { types.require(value.type()).validate(value.payload()).requireValid(); assertions++; }
        for (final var value : worldFacts.values()) { types.require(value.type()).validate(value.payload()).requireValid(); assertions++; }
        new InspectionResult(FACET, facts, List.of()); new InspectionResult(FACET, worldFacts, List.of());
        check(fieldFacts(service.snapshot(), world, 300, List.of()).get("trash.pending_count").payload().get("value").equals("0"),
                "loaded empty recovery inventory was reported as unavailable");
    }

    private static void causalFields() {
        final var fields = new TrashRuleFieldService(() -> 100L);
        final UUID world = UUID.randomUUID(), other = UUID.randomUUID(), actor = UUID.randomUUID();
        final var point = new TrashRuleFieldService.Point(world, 0, 64, 0);
        final var active = new TrashRuleFieldService.RuleField(UUID.randomUUID(), TrashRuleFieldService.FieldKind.SPATIAL_ANCHOR,
                point, 3, 200, actor, null);
        final var pending = new TrashRuleFieldService.RuleField(UUID.randomUUID(), TrashRuleFieldService.FieldKind.CEASEFIRE,
                point, 3, 200, actor, null);
        check(fields.add(active), "active causal field refused"); fields.reserveCreation(pending).orElseThrow();
        check(fieldSources(fields.snapshot(), point, 100).equals(List.of(new RewardSource.Event("trash.rule_field", active.id()))),
                "native causal sources included a preparation or lost active field identity");
        check(fieldSources(fields.snapshot(), new TrashRuleFieldService.Point(other, 0, 64, 0), 100).isEmpty()
                        && fieldSources(fields.snapshot(), new TrashRuleFieldService.Point(world, 10, 64, 0), 100).isEmpty()
                        && fieldSources(fields.snapshot(), point, 200).isEmpty(), "native causal sources crossed geometry, world or expiry");
        final var types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        final var reads = new AtomicInteger();
        final var provider = new TrashWeaverProvider(types, Map::of, ignored -> Map.of(), subject -> {
            reads.incrementAndGet(); return fieldSources(fields.snapshot(), point, 100);
        });
        final var registry = new WorldWeaverProviderRegistry(types, () -> 1L); registry.register(provider); registry.freezeAndValidate();
        for (var kind : GameplaySourceSubject.Kind.values()) check(registry.captureCausalSources(new GameplaySourceSubject(actor, kind))
                .equals(List.of(new RewardSource.Event("trash.rule_field", active.id()))), "generic source registry lost a native subject kind");
        check(reads.get() == GameplaySourceSubject.Kind.values().length, "source discovery captured a live subject");
        fields.cancelCreationsForOwner(actor);
        final Set<RewardSource> expected = new HashSet<>(List.of(new RewardSource.Event("trash.rule_field", active.id())));
        for (int index = 1; index < TrashRuleFieldService.MAX_FIELDS_PER_WORLD; index++) {
            final var overlap = new TrashRuleFieldService.RuleField(UUID.randomUUID(), TrashRuleFieldService.FieldKind.CEASEFIRE,
                    point, 3, 200, actor, null);
            check(fields.add(overlap), "native overlapping field refused below canonical world capacity");
            expected.add(new RewardSource.Event("trash.rule_field", overlap.id()));
        }
        final var captured = registry.captureCausalSources(new GameplaySourceSubject(actor, GameplaySourceSubject.Kind.PLAYER));
        check(captured.size() == TrashRuleFieldService.MAX_FIELDS_PER_WORLD && new HashSet<>(captured).equals(expected),
                "generic source capture truncated actual overlapping native fields");
        fields.close(); refuses(() -> registry.captureCausalSources(new GameplaySourceSubject(actor, GameplaySourceSubject.Kind.PLAYER)));
    }
}
