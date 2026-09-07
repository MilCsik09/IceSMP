package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.PreparedAction;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.pve.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** Registry publications are immutable; native mob reads occur only through the owner snapshot hook. */
public final class PvEWeaverProvider implements WorldWeaverProvider, WeaverSnapshotContributor {
    public static final String FACET = "pve.runtime";
    public static final WeaverTypeId ABILITY = WeaverTypeId.parse("icesmp:pve_ability_ref@1");
    public static final WeaverTypeId TEMPLATE = WeaverTypeId.parse("icesmp:pve_template_ref@1");
    public static final WeaverTypeId RANK = WeaverTypeId.parse("icesmp:pve_rank@1");
    public static final WeaverTypeId ARCHETYPE = WeaverTypeId.parse("icesmp:pve_archetype@1");
    private final Map<String, WeaverValueCatalog> catalogs;
    private final Function<SubjectRef, Map<String, WeaverValue>> snapshots;
    private final ProviderContribution contribution;
    public PvEWeaverProvider(final WeaverTypeRegistry types, final MobAbilityRegistry abilities, final MobTemplateRegistry templates,
            final MobScalingManager scaling, final MobAbilityRuntime runtime) {
        this(types, abilities::all, templates::all, ref -> capture(ref, templates, scaling, runtime));
    }
    PvEWeaverProvider(final WeaverTypeRegistry types, final Supplier<Map<String, MobAbilityDefinition>> abilities,
            final Supplier<Map<String, MobTemplate>> templates, final Function<SubjectRef, Map<String, WeaverValue>> snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots);
        final Map<String, WeaverValueCatalog> values = new LinkedHashMap<>();
        register(types, values, "pve.abilities", ABILITY, "pve.ability", abilities, definition -> Component.text(definition.abilityId()));
        register(types, values, "pve.templates", TEMPLATE, "pve.template", templates, definition -> Component.text(definition.displayName()));
        register(types, values, "pve.ranks", RANK, "pve.rank", () -> enumValues(MobRank.values()), rank -> Component.text(rank.name()));
        register(types, values, "pve.archetypes", ARCHETYPE, "pve.archetype", () -> enumValues(MobArchetype.values()), archetype -> Component.text(archetype.name()));
        catalogs = Map.copyOf(values);
        contribution = new ProviderContribution(List.of(new FacetDescriptor(FACET, Component.text("PvE"), Component.text("Canonical profil és aktív combat runtime"), 10)), List.of(),
                catalogs.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new CatalogDescriptor(entry.getKey(), FACET, Component.text(entry.getKey()), entry.getValue().type())).toList(),
                List.of(new ExportDescriptor("pve.export_rank", FACET, RANK, Set.of("pve.rank")), new ExportDescriptor("pve.export_archetype", FACET, ARCHETYPE, Set.of("pve.archetype")),
                        new ExportDescriptor("pve.export_template", FACET, TEMPLATE, Set.of("pve.template")), new ExportDescriptor("pve.export_ability", FACET, ABILITY, Set.of("pve.ability"))), List.of(), Map.of());
    }
    private static <T> void register(final WeaverTypeRegistry types, final Map<String, WeaverValueCatalog> catalogs, final String id, final WeaverTypeId type, final String capability,
            final Supplier<Map<String, T>> registry, final Function<T, Component> label) {
        types.register(ScalarTypeCodec.reference(type, key -> registry.get().containsKey(key)));
        catalogs.put(id, new RegistryValueCatalog<>(type, "pve", FACET, Set.of(capability), registry, label, System::currentTimeMillis));
    }
    private static <E extends Enum<E>> Map<String, E> enumValues(final E[] values) {
        final Map<String, E> result = new LinkedHashMap<>(); for (final E value : values) result.put(value.name().toLowerCase(Locale.ROOT), value); return Map.copyOf(result);
    }
    private static Map<String, WeaverValue> capture(final SubjectRef ref, final MobTemplateRegistry templates, final MobScalingManager scaling, final MobAbilityRuntime runtime) {
        if (!(ref instanceof EntityRef entity)) return Map.of();
        final var live = Bukkit.getEntity(entity.entityId());
        if (live == null || !Bukkit.isOwnedByCurrentRegion(live)) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
        if (!(live instanceof Mob mob)) return Map.of();
        if (!mob.isValid() || mob.isDead()) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
        final long now = System.currentTimeMillis(); final Map<String, WeaverValue> facts = new TreeMap<>();
        facts.put("pve.rank", reference(RANK, scaling.getRank(mob).name().toLowerCase(Locale.ROOT), "pve.rank", now));
        facts.put("pve.level", scalar("int", scaling.getLevel(mob), now));
        final String rawArchetype = scaling.getArchetypeId(mob);
        if (rawArchetype != null && !rawArchetype.isBlank()) facts.put("pve.archetype", reference(ARCHETYPE, MobArchetype.parse(rawArchetype).name().toLowerCase(Locale.ROOT), "pve.archetype", now));
        final String templateId = scaling.getTemplateId(mob);
        facts.put("pve.template_id", scalar("text", Objects.requireNonNullElse(templateId, ""), now));
        templates.find(templateId).ifPresent(template -> {
            facts.put("pve.template", reference(TEMPLATE, template.mobId(), "pve.template", now));
            facts.put("pve.resistances", scalar("text", String.join(", ", new TreeSet<>(template.resistances())), now));
            facts.put("pve.weaknesses", scalar("text", String.join(", ", new TreeSet<>(template.weaknesses())), now));
            facts.put("pve.source_tags", scalar("text", String.join(", ", new TreeSet<>(template.sourceTags())), now));
            facts.put("pve.canonical_abilities", scalar("text", String.join(", ", template.abilityIdsFor(scaling.getRank(mob))), now));
            facts.put("pve.behavior", scalar("text", template.behavior().toString(), now));
            facts.put("pve.resistance_profile", scalar("text", template.stats().toString(), now));
        });
        facts.put("pve.affixes", scalar("text", String.join(", ", scaling.getAffixes(mob).stream().map(Enum::name).toList()), now));
        final List<String> active = runtime.activeAbilityIds(mob);
        facts.put("pve.active_abilities", scalar("text", String.join(", ", active), now));
        if (active.size() == 1) facts.put("pve.ability", reference(ABILITY, active.getFirst(), "pve.ability", now));
        facts.put("pve.cast_state", scalar("text", runtime.activeStateSummary(mob), now));
        return Map.copyOf(facts);
    }
    private static WeaverValue scalar(final String type, final Object value, final long now) {
        return new WeaverValue(new WeaverTypeId("weaver", type, 1), Map.of("value", value), "pve", FACET, Set.of(), now);
    }
    static WeaverValue reference(final WeaverTypeId type, final String id, final String capability, final long now) {
        return new WeaverValue(type, Map.of("id", id), "pve", FACET, Set.of(capability), now);
    }
    @Override public String id() { return "pve"; }
    @Override public int contractVersion() { return 1; }
    @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.ENTITY, WeaverSubjectKind.AREA); }
    @Override public ProviderContribution contribution() { return contribution; }
    @Override public ProviderCoverage coverage() {
        return new ProviderCoverage("pve.catalog_inspection", CoverageLevel.FULL_PROVIDER,
                "Registered catalog, owner snapshot and typed export surface only. The independent WW-00 pve domain remains blocked until projection consumers, mutation, imports and integrity integration are complete.",
                Set.of(FACET, "pve.abilities", "pve.templates", "pve.ranks", "pve.archetypes", "pve.export_rank", "pve.export_archetype", "pve.export_template", "pve.export_ability"));
    }
    @Override public Map<String, WeaverValue> captureOnOwner(final SubjectRef ref) { return Map.copyOf(snapshots.apply(ref)); }
    @Override public ProviderDiscovery discover(final SubjectSnapshot snapshot) {
        if (!(snapshot.ref() instanceof AreaRef) && !snapshot.facts().containsKey("pve.rank")) return new ProviderDiscovery(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of());
        final Set<String> exports = new HashSet<>();
        for (final String field : List.of("rank", "archetype", "template", "ability")) if (snapshot.facts().containsKey("pve." + field)) exports.add("pve.export_" + field);
        return new ProviderDiscovery(Set.of(FACET), Set.of(), catalogs.keySet(), exports, Set.of(), Map.of());
    }
    @Override public InspectionResult inspect(final ProviderContext context, final SubjectSnapshot snapshot, final String facetId) {
        context.authority().requireValid(); if (!FACET.equals(facetId)) throw new WeaverDomainRejection("UNKNOWN_FACET");
        final Map<String, WeaverValue> facts = new TreeMap<>(); snapshot.facts().forEach((key, value) -> { if (key.startsWith("pve.")) facts.put(key, value); });
        return new InspectionResult(FACET, facts, List.of());
    }
    @Override public Optional<WeaverValueCatalog> catalog(final ProviderContext context, final SubjectSnapshot snapshot, final String id) {
        context.authority().requireValid(); return Optional.ofNullable(catalogs.get(id));
    }
    @Override public ValueExportResult exportValue(final ProviderContext context, final SubjectSnapshot snapshot, final String id) {
        context.authority().requireValid();
        final var descriptor = contribution.exports().stream().filter(export -> export.id().equals(id)).findFirst().orElse(null);
        if (descriptor == null) return ValueExportResult.rejected("UNKNOWN_EXPORT");
        final WeaverValue value = snapshot.facts().get("pve." + id.substring("pve.export_".length()));
        return value != null && context.types().compatible(value, descriptor.outputType(), descriptor.capabilities()) ? ValueExportResult.exported(value) : ValueExportResult.rejected("EXPORT_UNAVAILABLE");
    }
    @Override public PreparedAction prepare(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request) {
        context.authority().requireValid(); throw new WeaverDomainRejection("UNKNOWN_ACTION");
    }
    @Override public PreparedAction prepareUndo(final ProviderContext context, final SubjectSnapshot snapshot, final WeaverReceipt receipt) {
        context.authority().requireValid(); throw new WeaverDomainRejection("UNKNOWN_UNDO_ACTION");
    }
    @Override public ImportValidation validateImport(final ProviderContext context, final SubjectSnapshot snapshot, final String id, final WeaverValue value) {
        context.authority().requireValid(); return ImportValidation.rejected("UNKNOWN_IMPORT");
    }
    @Override public RecoveryAssessment assessRecovery(final RecoveryContext context, final SubjectSnapshot snapshot, final WeaverOperationRecord operation) {
        context.authority().require(operation); return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "UNREGISTERED_MUTATION");
    }
}
