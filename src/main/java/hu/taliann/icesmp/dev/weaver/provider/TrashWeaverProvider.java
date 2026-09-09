package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.PreparedAction;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.itemization.ItemIdentityService;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverCausalSourceProvider;
import hu.taliann.icesmp.trash.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import java.util.*;
import java.util.function.*;

/** Hidden inspection adapter; native stores and authored catalogs remain the only authorities. */
public final class TrashWeaverProvider implements WorldWeaverProvider, WeaverSnapshotContributor, WeaverCausalSourceProvider {
    static final String FACET = "trash.inspection";
    static final WeaverTypeId IDENTITY = WeaverTypeId.parse("icesmp:trash_identity_ref@1");
    static final WeaverTypeId RULE = WeaverTypeId.parse("icesmp:trash_rule_ref@1");
    private final Function<SubjectRef, Map<String, WeaverValue>> capture;
    private final Map<String, WeaverValueCatalog> catalogs;
    private final ProviderContribution contribution;
    private final Function<GameplaySourceSubject, List<RewardSource>> sourceCapture;

    public TrashWeaverProvider(WeaverProviderServices services, TrashCatalog catalog,
            TrashHistoryService history, TrashAnomalyStateStore memory, TrashRuleFieldService fields,
            ItemIdentityService identity) {
        this(services.types(), catalog::snapshot, subject -> {
            if (subject instanceof WorldRef world) return fieldFacts(fields.snapshot(), world.worldId(), System.currentTimeMillis(),
                    history.tryInspectPendingProjectileWalls().orElseThrow(() -> new WeaverDomainRejection("TRASH_HISTORY_UNAVAILABLE")));
            if (!(subject instanceof ItemSlotRef item)) return Map.of();
            final var player = Bukkit.getPlayer(item.holderId());
            if (player == null) throw new WeaverDomainRejection("ITEM_HOLDER_UNAVAILABLE");
            if (!Bukkit.isOwnedByCurrentRegion(player)) throw new IllegalStateException("Foreign Trash item inspection");
            if (!player.isOnline()) throw new WeaverDomainRejection("ITEM_HOLDER_UNAVAILABLE");
            final var slots = new WeaverItemSlots(identity); slots.verify(player, item);
            final var stack = slots.require(player, item.slot());
            if (!history.isTrash(stack)) return Map.of();
            final TrashHistoryService.ItemInspection inspected;
            try { inspected = history.tryInspect(stack).orElseThrow(() -> new WeaverDomainRejection("TRASH_HISTORY_UNAVAILABLE")); }
            catch (IllegalStateException | IllegalArgumentException malformed) { throw new WeaverDomainRejection("TRASH_HISTORY_UNAVAILABLE"); }
            final Map<TrashAnomalyStateStore.MemoryKey, Long> counters = inspected.history().isEmpty() ? Map.of()
                    : memory.tryInspect(inspected.history().orElseThrow().instanceId())
                            .orElseThrow(() -> new WeaverDomainRejection("TRASH_MEMORY_UNAVAILABLE"));
            final long now = System.currentTimeMillis();
            final Map<String, WeaverValue> facts = new TreeMap<>(itemFacts(inspected, counters, now));
            facts.put("trash.prototype", text(Boolean.toString(hu.taliann.icesmp.itemization.ItemPrototypePolicy.direct(stack)), now));
            hu.taliann.icesmp.itemization.ItemPrototypePolicy.identity(stack).ifPresent(prototype -> {
                facts.put("trash.prototype_owner", text(prototype.owner().toString(), now));
                facts.put("trash.prototype_operation", text(prototype.operation().toString(), now));
            });
            return Map.copyOf(facts);
        }, subject -> {
            final var entity = Bukkit.getEntity(subject.id());
            if (entity == null || !Bukkit.isOwnedByCurrentRegion(entity)) throw new WeaverDomainRejection("TRASH_SOURCE_OWNER_UNAVAILABLE");
            final var location = entity.getLocation();
            return fieldSources(fields.snapshot(), new TrashRuleFieldService.Point(location.getWorld().getUID(),
                    location.getX(), location.getY(), location.getZ()), System.currentTimeMillis());
        });
    }

    TrashWeaverProvider(WeaverTypeRegistry types, Supplier<Map<String, TrashDefinition>> identities,
            Function<SubjectRef, Map<String, WeaverValue>> capture) {
        this(types, identities, capture, ignored -> List.of());
    }

    TrashWeaverProvider(WeaverTypeRegistry types, Supplier<Map<String, TrashDefinition>> identities,
            Function<SubjectRef, Map<String, WeaverValue>> capture,
            Function<GameplaySourceSubject, List<RewardSource>> sourceCapture) {
        this.capture = Objects.requireNonNull(capture);
        this.sourceCapture = Objects.requireNonNull(sourceCapture);
        types.register(ScalarTypeCodec.reference(IDENTITY, id -> identities.get().containsKey(id)));
        types.register(ScalarTypeCodec.reference(RULE, id -> rules().containsKey(id)));
        catalogs = Map.of(
                "trash.identities", new RegistryValueCatalog<>(IDENTITY, "trash", FACET, Set.of("trash.identity"),
                        identities, definition -> Component.text(definition.displayName()), System::currentTimeMillis),
                "trash.rules", new RegistryValueCatalog<>(RULE, "trash", FACET, Set.of("trash.rule"),
                        TrashWeaverProvider::rules, Component::text, System::currentTimeMillis));
        contribution = new ProviderContribution(List.of(new FacetDescriptor(FACET, Component.text("Lelet"),
                Component.text("Natív tárgytörténet, emlékezet és szabálymezők"), 40)), List.of(),
                catalogs.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new CatalogDescriptor(
                        entry.getKey(), FACET, Component.text(entry.getKey()), entry.getValue().type())).toList(),
                List.of(), List.of(), Map.of());
    }

    private static Map<String, String> rules() {
        final Map<String, String> result = new TreeMap<>();
        for (final var kind : TrashRuleFieldService.FieldKind.values()) result.put(kind.name().toLowerCase(Locale.ROOT), kind.name());
        return Map.copyOf(result);
    }
    static List<RewardSource> fieldSources(TrashRuleFieldService.Snapshot snapshot, TrashRuleFieldService.Point point, long now) {
        if (!snapshot.open()) throw new WeaverDomainRejection("TRASH_FIELDS_UNAVAILABLE");
        return snapshot.fields().stream().filter(field -> field.contains(point, now))
                .map(field -> (RewardSource) new RewardSource.Event("trash.rule_field", field.id())).toList();
    }
    @Override public Set<GameplaySourceSubject.Kind> causalSourceKinds() { return Set.of(GameplaySourceSubject.Kind.values()); }
    @Override public int maximumCausalSources() { return TrashRuleFieldService.MAX_FIELDS_PER_WORLD; }
    @Override public List<RewardSource> captureCausalSources(GameplaySourceSubject subject) { return List.copyOf(sourceCapture.apply(subject)); }
    private static WeaverValue text(String value, long now) {
        return new WeaverValue(WeaverTypeId.parse("weaver:text@1"), Map.of("value", value), "trash", FACET, Set.of(), now);
    }
    static Map<String, WeaverValue> itemFacts(TrashHistoryService.ItemInspection item,
            Map<TrashAnomalyStateStore.MemoryKey, Long> memory, long now) {
        final Map<String, WeaverValue> facts = new TreeMap<>(); final var definition = item.definition();
        facts.put("trash.identity", new WeaverValue(IDENTITY, Map.of("id", definition.id()), "trash", FACET, Set.of("trash.identity"), now));
        facts.put("trash.kind", text(definition.internalKind().name(), now));
        facts.put("trash.behavior", text(definition.behavior(), now));
        if (definition.internalKind() == TrashKind.ANOMALY) facts.put("trash.primitive", text(TrashAnomalyBehavior.parse(definition.behavior()).primitive().name(), now));
        facts.put("trash.phase", text(item.phase(), now));
        facts.put("trash.success_phase", text(definition.successPhase(), now));
        facts.put("trash.tracked", text(Boolean.toString(item.history().isPresent()), now));
        facts.put("trash.origin", text(item.origin().map(Enum::name).orElse("UNRECORDED"), now));
        item.history().ifPresent(history -> {
            facts.put("trash.instance", text(history.instanceId().toString(), now));
            facts.put("trash.revision", text(Long.toString(history.revision()), now));
            facts.put("trash.created_at", text(Long.toString(history.createdAt()), now));
            facts.put("trash.updated_at", text(Long.toString(history.updatedAt()), now));
            facts.put("trash.owners", text(history.owners().stream().map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining("\n")), now));
            facts.put("trash.retained_events", text(Integer.toString(history.events().size()), now));
            for (int index = 0; index < history.events().size(); index++) {
                final var event = history.events().get(index);
                facts.put("trash.history." + String.format(Locale.ROOT, "%02d", index), text(event.revision() + " | " + event.type().name()
                        + " | " + event.at() + " | " + (event.actor() == null ? "" : event.actor()) + " | " + event.detail(), now));
            }
            facts.put("trash.memory_scope", text("RUNTIME_OBSERVATION_NOT_DURABILITY_PROOF", now));
            memory.forEach((key, value) -> facts.put("trash.memory." + key.name().toLowerCase(Locale.ROOT), text(Long.toString(value), now)));
        });
        item.pendingWall().ifPresent(receipt -> {
            facts.put("trash.pending_effect", text("PROJECTILE_REMOVAL_UNOBSERVED", now));
            facts.put("trash.pending_operation", text(receipt.operationId().toString(), now));
            facts.put("trash.pending_projectile", text(receipt.projectileId().toString(), now));
            facts.put("trash.pending_before_revision", text(Long.toString(receipt.beforeRevision()), now));
            facts.put("trash.pending_scope", text("NATIVE_HISTORY_WAL_RECEIPT_NOT_EFFECT_COMPLETION_PROOF", now));
        });
        // A level-50 developer derivation is not the inspecting player's learned knowledge or an award.
        facts.put("trash.archaeology_level", text("50", now));
        TrashArchaeologyFactEngine.evaluate(definition, item.history(), 50).ifPresent(evaluation -> {
            facts.put("trash.archaeology_scope", text("DERIVED_ONLY_NO_DISCOVERY_OR_REWARD", now));
            for (final var fact : evaluation.facts()) facts.put("trash.archaeology." + fact.id(), text(fact.evidenceRevision() + " | " + fact.text(), now));
        });
        return Map.copyOf(facts);
    }
    static Map<String, WeaverValue> fieldFacts(TrashRuleFieldService.Snapshot snapshot, UUID world, long now) {
        if (!snapshot.open()) throw new WeaverDomainRejection("TRASH_FIELDS_UNAVAILABLE");
        final Map<String, WeaverValue> facts = new TreeMap<>();
        facts.put("trash.fields_revision", text(Long.toString(snapshot.revision()), now));
        final var fields = snapshot.fields().stream().filter(field -> field.center().world().equals(world)).sorted(Comparator.comparing(TrashRuleFieldService.RuleField::id)).toList();
        facts.put("trash.fields_count", text(Integer.toString(fields.size()), now));
        final var preparing = snapshot.preparing().stream().filter(field -> field.center().world().equals(world))
                .sorted(Comparator.comparing(TrashRuleFieldService.RuleField::id)).toList();
        facts.put("trash.preparing_count", text(Integer.toString(preparing.size()), now));
        for (int index = 0; index < preparing.size(); index++) {
            final var field = preparing.get(index);
            facts.put("trash.preparing." + String.format(Locale.ROOT, "%02d", index), text(field.id()
                    + " | " + field.kind() + " | owner=" + field.owner() + " | expires=" + field.expiresAt()
                    + " | PREPARING_NO_ACTIVE_EFFECT", now));
        }
        for (int index = 0; index < fields.size(); index++) {
            final var field = fields.get(index); final var center = field.center();
            facts.put("trash.field." + String.format(Locale.ROOT, "%02d", index), text(field.id() + " | " + field.kind().name()
                    + " | " + center.world() + " | " + center.x() + "," + center.y() + "," + center.z()
                    + " | radius=" + field.radius() + " | expires=" + field.expiresAt() + " | active=" + field.active(now)
                    + " | claimed=" + snapshot.claimed().contains(field.id()) + " | owner=" + field.owner(), now));
        }
        return Map.copyOf(facts);
    }
    static Map<String, WeaverValue> fieldFacts(TrashRuleFieldService.Snapshot snapshot, UUID world, long now,
                                              List<TrashHistoryStore.WallReceipt> pending) {
        final Map<String, WeaverValue> facts = new TreeMap<>(fieldFacts(snapshot, world, now));
        final var receipts = pending.stream().filter(receipt -> receipt.worldId().equals(world))
                .sorted(Comparator.comparing(TrashHistoryStore.WallReceipt::operationId)).toList();
        facts.put("trash.pending_count", text(Integer.toString(receipts.size()), now));
        facts.put("trash.pending_truncated", text(Boolean.toString(receipts.size() > 32), now));
        facts.put("trash.pending_scope", text("NATIVE_HISTORY_WAL_RECEIPTS_NOT_EFFECT_COMPLETION_PROOF", now));
        for (int index = 0; index < Math.min(32, receipts.size()); index++) {
            final var receipt = receipts.get(index);
            facts.put("trash.pending." + String.format(Locale.ROOT, "%02d", index), text(receipt.operationId()
                    + " | instance=" + receipt.instanceId() + " | revision=" + receipt.revision()
                    + " | projectile=" + receipt.projectileId() + " | owner=" + receipt.actor()
                    + " | consumed=" + receipt.consumedAt() + " | expires=" + receipt.field().expiresAt(), now));
        }
        return Map.copyOf(facts);
    }

    @Override public Map<String, WeaverValue> captureOnOwner(SubjectRef subject) { return Map.copyOf(capture.apply(subject)); }
    @Override public String id() { return "trash"; }
    @Override public int contractVersion() { return 1; }
    @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.ITEM_SLOT, WeaverSubjectKind.WORLD); }
    @Override public ProviderContribution contribution() { return contribution; }
    @Override public ProviderCoverage coverage() {
        return new ProviderCoverage("trash.inspection_subset", CoverageLevel.INSPECT_ONLY_BY_DESIGN,
                "Only the read-only inspection subset is registered. Trash actions and full WW-00 Trash coverage remain DEFERRED_BLOCKER.",
                Set.of(FACET, "trash.identities", "trash.rules"));
    }
    @Override public ProviderDiscovery discover(SubjectSnapshot snapshot) {
        final boolean available = snapshot.facts().containsKey("trash.identity") || snapshot.facts().containsKey("trash.fields_revision");
        return new ProviderDiscovery(available ? Set.of(FACET) : Set.of(), Set.of(), available ? catalogs.keySet() : Set.of(), Set.of(), Set.of(), Map.of());
    }
    @Override public InspectionResult inspect(ProviderContext context, SubjectSnapshot snapshot, String facetId) {
        context.authority().requireValid();
        if (!FACET.equals(facetId) || !discover(snapshot).facets().contains(FACET)) throw new WeaverDomainRejection("TRASH_INSPECTION_UNAVAILABLE");
        final Map<String, WeaverValue> facts = new TreeMap<>();
        snapshot.facts().forEach((key, value) -> { if (value.sourceProvider().equals(id()) && value.sourceFacet().equals(FACET)) facts.put(key, value); });
        return new InspectionResult(FACET, facts, List.of());
    }
    @Override public Optional<WeaverValueCatalog> catalog(ProviderContext context, SubjectSnapshot snapshot, String catalogId) {
        context.authority().requireValid(); return Optional.ofNullable(catalogs.get(catalogId));
    }
    @Override public PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid(); throw new WeaverDomainRejection("TRASH_ACTIONS_UNAVAILABLE");
    }
    @Override public PreparedAction prepareUndo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) {
        context.authority().requireValid(); throw new WeaverDomainRejection("TRASH_UNDO_UNAVAILABLE");
    }
    @Override public ValueExportResult exportValue(ProviderContext context, SubjectSnapshot snapshot, String exportId) {
        context.authority().requireValid(); return ValueExportResult.rejected("TRASH_EXPORT_UNAVAILABLE");
    }
    @Override public ImportValidation validateImport(ProviderContext context, SubjectSnapshot snapshot, String importId, WeaverValue value) {
        context.authority().requireValid(); return ImportValidation.rejected("TRASH_IMPORT_UNAVAILABLE");
    }
    @Override public RecoveryAssessment assessRecovery(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "TRASH_RECOVERY_UNAVAILABLE");
    }
}
