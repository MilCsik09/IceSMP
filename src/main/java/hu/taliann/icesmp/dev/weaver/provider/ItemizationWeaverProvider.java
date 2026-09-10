package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.itemization.*;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import net.kyori.adventure.text.Component;
import java.util.*;

/** Native managed-item inspection and mutation surfaces for the single developer artifact. */
public final class ItemizationWeaverProvider implements WorldWeaverProvider, WeaverSnapshotContributor {
    static final String FACET = "item.inspection";
    static final WeaverTypeId TEMPLATE = WeaverTypeId.parse("icesmp:item_template_ref@1"), RUNE = WeaverTypeId.parse("icesmp:item_rune_ref@1");
    private final ItemIdentityService identity;
    private final WeaverItemSlots slots;
    private final ItemizationWeaverActions actions;
    private final Map<String, WeaverValueCatalog> catalogs;
    private final ProviderContribution contribution;
    public ItemizationWeaverProvider(WeaverProviderServices services, ItemIdentityService identity,
            ItemTemplateRegistry templates, UniqueMaterialFactory materials, ItemMutationCoordinator coordinator) {
        this.identity = Objects.requireNonNull(identity); slots = new WeaverItemSlots(identity);
        services.types().register(ScalarTypeCodec.reference(TEMPLATE, id -> templates.snapshot().containsKey(id)));
        services.types().register(ScalarTypeCodec.reference(RUNE, id -> id.startsWith("runa_") && materials.isDefined(id)));
        catalogs = Map.of("item.templates", new RegistryValueCatalog<>(TEMPLATE, "item", FACET, Set.of("item.template"),
                templates::snapshot, template -> Component.text(template.displayName()), System::currentTimeMillis),
                "item.runes", new RegistryValueCatalog<>(RUNE, "item", FACET, Set.of("item.rune"), () -> {
                    final var runes = new TreeMap<String, String>();
                    for (String id : materials.allIds()) if (id.startsWith("runa_")) runes.put(id, materials.displayName(id));
                    return Map.copyOf(runes);
                }, Component::text, System::currentTimeMillis));
        actions = new ItemizationWeaverActions(services.types(), coordinator, identity);
        contribution = new ProviderContribution(List.of(new FacetDescriptor(FACET, Component.text("Felszerelés"),
                Component.text("Tárgyazonosító, tulajdonságok, rúnák és történet"), 35)), actions.descriptors(),
                catalogs.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new CatalogDescriptor(entry.getKey(), FACET,
                        Component.text(entry.getKey().equals("item.runes") ? "Rúnák" : "Felszereléssablonok"), entry.getValue().type())).toList(),
                List.of(), List.of(), ItemizationWeaverActions.KINDS.keySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        java.util.function.Function.identity(), ignored -> "item.observe_native_mutation")));
    }
    @Override public String id() { return "item"; }
    @Override public int contractVersion() { return 1; }
    @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.ITEM_SLOT); }
    @Override public ProviderContribution contribution() { return contribution; }
    @Override public ProviderCoverage coverage() {
        final var surfaces = new HashSet<>(Set.of(FACET, "item.templates", "item.runes")); surfaces.addAll(ItemizationWeaverActions.KINDS.keySet());
        return new ProviderCoverage("item.registered_surface", CoverageLevel.FULL_PROVIDER,
                "Native inspection, ten owner-bound actions, nine conditional compensations and read-only durable receipt recovery. Independent Itemization domain coverage remains deferred for imports/exports, connected custody/playerdata and complete acceptance.", surfaces);
    }
    @Override public Map<String, WeaverValue> captureOnOwner(SubjectRef ref) {
        if (!(ref instanceof ItemSlotRef subject)) return Map.of();
        final var actor = ItemizationWeaverActions.owner(subject.holderId());
        final var item = slots.peek(actor, subject.slot()).orElse(null); final var inspected = identity.inspect(item);
        if (inspected.status() == ItemIdentityService.Status.NOT_MANAGED) return Map.of();
        if (inspected.status() != ItemIdentityService.Status.VALID) throw new WeaverDomainRejection("ITEM_IDENTITY_INVALID");
        slots.verify(actor, subject);
        final var facts = new TreeMap<>(itemFacts(inspected.instance(), inspected.template(), identity.isEquipmentSuppressed(item)));
        if (HiddenDevAuthority.isDeveloper(subject.holderId()) && subject.slot().kind() != WeaverSlot.Kind.CURSOR)
            facts.putAll(ItemizationWeaverActions.inventoryFacts(actor.getInventory().getContents()));
        return Map.copyOf(facts);
    }
    static Map<String, WeaverValue> itemFacts(ItemInstance item, ItemTemplate template, boolean suppressed) {
        final var facts = new TreeMap<String, WeaverValue>();
        facts.put("item.template", new WeaverValue(TEMPLATE, Map.of("id", template.templateId()), "item", FACET, Set.of("item.template"), 0));
        final Map<String, String> values = new TreeMap<>();
        values.put("uuid", item.itemId().toString()); values.put("schema", Integer.toString(item.schemaVersion()));
        values.put("template_version", Integer.toString(item.templateVersion())); values.put("level", Integer.toString(item.itemLevel()));
        values.put("rarity", template.rarity().name()); values.put("revision", Long.toString(item.mutationRevision()));
        values.put("ascension", item.ascension().stageId()); values.put("ascension_index", Integer.toString(item.ascension().stageIndex()));
        values.put("signature", template.signatureEffectId()); values.put("signature_tier", Integer.toString(template.signatureTierAt(item.ascension().stageId())));
        values.put("source", item.origin().sourceTag()); values.put("source_id", item.origin().sourceId());
        values.put("crafter", Objects.toString(item.origin().crafterId(), "")); values.put("created_at", Long.toString(item.origin().createdAt()));
        values.put("creation_location", item.origin().creationLocation()); values.put("profession", item.origin().professionId());
        values.put("masterwork", Boolean.toString(item.origin().masterwork())); values.put("prototype", Boolean.toString(ItemPrototypePolicy.isPrototype(item)));
        values.put("suppressed", Boolean.toString(suppressed)); values.put("rune_capacity", Integer.toString(template.runeSocketCountAt(item.ascension().stageId())));
        values.put("rune_count", Integer.toString(item.runes().size())); values.put("reroll_count", Integer.toString(item.mutation().rerollCount()));
        values.put("reroll_cost_step", Integer.toString(item.mutation().rerollCostStep()));
        values.put("states", item.states().stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(", ")));
        values.put("can_ascend", Boolean.toString(item.ascension().stageIndex() < template.ascensionPath().size()));
        values.put("has_rolls", Boolean.toString(!template.rolledStatsAt(item.ascension().stageId()).isEmpty()));
        item.rolls().forEach((stat, roll) -> { values.put("roll." + stat, Double.toString(roll.value())); values.put("quality." + stat, Double.toString(roll.quality())); });
        for (int i = 0; i < item.runes().size(); i++) values.put("rune." + i, item.runes().get(i));
        for (int i = 0; i < item.history().size(); i++) {
            final var event = item.history().get(i); values.put("history." + String.format(Locale.ROOT, "%02d", i), event.occurredAt() + " | " + event.type() + " | " + event.detail());
        }
        values.forEach((key, value) -> facts.put("item." + key, ItemizationWeaverActions.scalar(value)));
        return Map.copyOf(facts);
    }
    @Override public Map<String, WeaverValue> captureRecoveryOnOwner(RecoveryContext context) { return actions.captureRecovery(context); }
    @Override public ProviderDiscovery discover(SubjectSnapshot snapshot) {
        final boolean available = snapshot.facts().containsKey("item.template");
        return new ProviderDiscovery(available ? Set.of(FACET) : Set.of(), available ? actions.discover(snapshot) : Set.of(),
                available ? catalogs.keySet() : Set.of(), Set.of(), Set.of(), Map.of());
    }
    @Override public InspectionResult inspect(ProviderContext context, SubjectSnapshot snapshot, String facet) {
        context.authority().requireValid();
        if (!FACET.equals(facet) || !discover(snapshot).facets().contains(facet)) throw new WeaverDomainRejection("ITEM_INSPECTION_UNAVAILABLE");
        final var facts = new TreeMap<String, WeaverValue>();
        snapshot.facts().forEach((key, value) -> { if (value.sourceProvider().equals(id()) && value.sourceFacet().equals(FACET)) facts.put(key, value); });
        return new InspectionResult(FACET, facts, List.of());
    }
    @Override public Optional<WeaverValueCatalog> catalog(ProviderContext context, SubjectSnapshot snapshot, String id) {
        context.authority().requireValid(); return Optional.ofNullable(catalogs.get(id));
    }
    @Override public PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) { return actions.prepare(context, snapshot, request); }
    @Override public PreparedEffects prepareEffects(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request, PreparedAction action) { return actions.effects(context, action); }
    @Override public PreparedAction prepareUndo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) {
        return actions.undo(context, snapshot, receipt);
    }
    @Override public ValueExportResult exportValue(ProviderContext context, SubjectSnapshot snapshot, String id) {
        context.authority().requireValid(); return ValueExportResult.rejected("ITEM_EXPORT_UNAVAILABLE");
    }
    @Override public ImportValidation validateImport(ProviderContext context, SubjectSnapshot snapshot, String id, WeaverValue value) {
        context.authority().requireValid(); return ImportValidation.rejected("ITEM_IMPORT_UNAVAILABLE");
    }
    @Override public RecoveryAssessment assessRecovery(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        return actions.assess(context, snapshot, operation);
    }
}
