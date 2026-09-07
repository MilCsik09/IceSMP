package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import java.util.concurrent.*;
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
public final class PvEWeaverProvider implements WorldWeaverProvider, WeaverSnapshotContributor, WeaverProjectionProvider, WeaverInfluenceObserverProvider {
    public static final String FACET = "pve.runtime";
    public static final WeaverTypeId ABILITY = WeaverTypeId.parse("icesmp:pve_ability_ref@1");
    public static final WeaverTypeId TEMPLATE = WeaverTypeId.parse("icesmp:pve_template_ref@1");
    public static final WeaverTypeId RANK = WeaverTypeId.parse("icesmp:pve_rank@1");
    public static final WeaverTypeId ARCHETYPE = WeaverTypeId.parse("icesmp:pve_archetype@1");
    private final Map<String, WeaverValueCatalog> catalogs;
    private final Function<SubjectRef, Map<String, WeaverValue>> snapshots;
    private final ProviderContribution contribution;
    private final Optional<PvEProjectionActions> mutations;
    private final Optional<PvERuntimeActions> controls;
    private final Optional<PvEInfluenceLifetimeObserver> lifetimeObserver;
    private final Function<Set<SubjectRef>, CompletionStage<Void>> reconcile;
    private record Ports(PvEMobProjectionSource source, Function<SubjectRef, Map<String, WeaverValue>> snapshots,
                         Function<Set<SubjectRef>, CompletionStage<Void>> reconcile, PvERuntimeActions.Port controls, PvEInfluenceLifetimeObserver observer) { }

    public PvEWeaverProvider(final WeaverProviderServices services, final MobAbilityRegistry abilities, final MobTemplateRegistry templates,
            final MobScalingManager scaling, final MobAbilityRuntime runtime) {
        this(services.types(), abilities::all, templates::all, nativePorts(services, abilities, templates, scaling, runtime));
    }
    private PvEWeaverProvider(final WeaverTypeRegistry types, final Supplier<Map<String, MobAbilityDefinition>> abilities,
            final Supplier<Map<String, MobTemplate>> templates, final Ports ports) {
        this(types, abilities, templates, ports.snapshots(), Optional.of(ports.source()), ports.reconcile(), Optional.of(ports.controls()), Optional.of(ports.observer()));
    }
    PvEWeaverProvider(final WeaverTypeRegistry types, final Supplier<Map<String, MobAbilityDefinition>> abilities,
            final Supplier<Map<String, MobTemplate>> templates, final Function<SubjectRef, Map<String, WeaverValue>> snapshots) {
        this(types, abilities, templates, snapshots, Optional.empty(), subjects -> CompletableFuture.completedFuture(null));
    }
    PvEWeaverProvider(final WeaverTypeRegistry types, final Supplier<Map<String, MobAbilityDefinition>> abilities,
            final Supplier<Map<String, MobTemplate>> templates, final Function<SubjectRef, Map<String, WeaverValue>> snapshots,
            final Optional<PvEMobProjectionSource> source, final Function<Set<SubjectRef>, CompletionStage<Void>> reconcile) {
        this(types, abilities, templates, snapshots, source, reconcile, Optional.empty());
    }
    PvEWeaverProvider(final WeaverTypeRegistry types, final Supplier<Map<String, MobAbilityDefinition>> abilities,
            final Supplier<Map<String, MobTemplate>> templates, final Function<SubjectRef, Map<String, WeaverValue>> snapshots,
            final Optional<PvEMobProjectionSource> source, final Function<Set<SubjectRef>, CompletionStage<Void>> reconcile,
            final Optional<PvERuntimeActions.Port> controlPort) {
        this(types, abilities, templates, snapshots, source, reconcile, controlPort, Optional.empty());
    }
    private PvEWeaverProvider(final WeaverTypeRegistry types, final Supplier<Map<String, MobAbilityDefinition>> abilities,
            final Supplier<Map<String, MobTemplate>> templates, final Function<SubjectRef, Map<String, WeaverValue>> snapshots,
            final Optional<PvEMobProjectionSource> source, final Function<Set<SubjectRef>, CompletionStage<Void>> reconcile,
            final Optional<PvERuntimeActions.Port> controlPort, final Optional<PvEInfluenceLifetimeObserver> lifetimeObserver) {
        this.lifetimeObserver = lifetimeObserver;
        this.snapshots = Objects.requireNonNull(snapshots); this.reconcile = Objects.requireNonNull(reconcile);
        mutations = source.map(port -> new PvEProjectionActions(port, snapshots, abilities));
        controls = controlPort.map(port -> new PvERuntimeActions(types, port, snapshots));
        final Map<String, WeaverValueCatalog> values = new LinkedHashMap<>();
        register(types, values, "pve.abilities", ABILITY, "pve.ability", abilities, definition -> Component.text(definition.abilityId()));
        register(types, values, "pve.templates", TEMPLATE, "pve.template", templates, definition -> Component.text(definition.displayName()));
        register(types, values, "pve.ranks", RANK, "pve.rank", () -> enumValues(MobRank.values()), rank -> Component.text(rank.name()));
        register(types, values, "pve.archetypes", ARCHETYPE, "pve.archetype", () -> enumValues(MobArchetype.values()), archetype -> Component.text(archetype.name()));
        catalogs = Map.copyOf(values);
        final List<CatalogDescriptor> catalogDescriptors = new ArrayList<>(catalogs.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new CatalogDescriptor(entry.getKey(), FACET, Component.text(entry.getKey()), entry.getValue().type())).toList());
        mutations.ifPresent(actions -> catalogDescriptors.add(actions.catalogDescriptor()));
        final List<ActionDescriptor> actionDescriptors = new ArrayList<>(mutations.map(PvEProjectionActions::descriptors).orElse(List.of()));
        controls.ifPresent(actions -> actionDescriptors.addAll(actions.descriptors()));
        contribution = new ProviderContribution(List.of(new FacetDescriptor(FACET, Component.text("PvE"), Component.text("Canonical profil és aktív combat runtime"), 10)), actionDescriptors,
                catalogDescriptors,
                List.of(new ExportDescriptor("pve.export_rank", FACET, RANK, Set.of("pve.rank")), new ExportDescriptor("pve.export_archetype", FACET, ARCHETYPE, Set.of("pve.archetype")),
                        new ExportDescriptor("pve.export_template", FACET, TEMPLATE, Set.of("pve.template")), new ExportDescriptor("pve.export_ability", FACET, ABILITY, Set.of("pve.ability"))), mutations.map(PvEProjectionActions::imports).orElse(List.of()),
                actionDescriptors.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ActionDescriptor::id,
                        action -> controls.filter(c -> c.owns(action.id())).isPresent() ? "pve.native_control" : "pve.journal_projection")));
    }
    private static Ports nativePorts(final WeaverProviderServices services, final MobAbilityRegistry abilities, final MobTemplateRegistry templates,
            final MobScalingManager scaling, final MobAbilityRuntime runtime) {
        final PvEMobProjectionSource source = new PvEMobProjectionSource(services.projections(), abilities::all, templates::all, System::currentTimeMillis);
        runtime.bindProjectionSource((id, canonical) -> {
            try { return services.readConsumer("pve", () -> source.resolve(id, canonical)); }
            catch (final WeaverDomainRejection unavailable) {
                return new EffectiveMobProjection(canonical.templateId(), canonical.rank(), canonical.archetype(), List.of(), canonical.behavior(), Set.of());
            }
        });
        return new Ports(source, ref -> capture(ref, templates, scaling, runtime, source), subjects -> {
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (final SubjectRef ref : subjects) {
                if (!(ref instanceof EntityRef entity)) return CompletableFuture.failedFuture(new WeaverDomainRejection("PROJECTION_SUBJECT_UNSUPPORTED"));
                chain = chain.thenCompose(ignored -> services.owners().submit(new EntityOwner(entity.entityId()), hu.taliann.icesmp.security.HiddenDevAuthority.PRIMARY_DEVELOPER,
                        java.time.Duration.ofSeconds(5), () -> {
                            final var live = Bukkit.getEntity(entity.entityId());
                            if (live == null || !Bukkit.isOwnedByCurrentRegion(live)) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
                            if (live instanceof Mob mob && mob.isValid() && !mob.isDead()) runtime.reconcileProjection(mob);
                            return CompletableFuture.<Void>completedFuture(null);
                        }).handle((done, failure) -> {
                            if (failure != null) {
                                Throwable root = failure; while (root instanceof CompletionException && root.getCause() != null) root = root.getCause();
                                if (!(root instanceof WeaverDomainRejection rejected) || !Set.of("ENTITY_UNAVAILABLE", "OWNER_UNAVAILABLE", "OWNER_RETIRED").contains(rejected.code())) throw new CompletionException(root);
                            }
                            return (Void) null;
                        }));
            }
            return chain;
        }, (subject, request, admission) -> {
            final var entity = Bukkit.getEntity(subject.entityId());
            if (entity == null || !Bukkit.isOwnedByCurrentRegion(entity)) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
            if (!(entity instanceof Mob mob) || !mob.isValid() || mob.isDead()) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
            return runtime.control(mob, request, admission);
        }, new PvEInfluenceLifetimeObserver(services));
    }
    @Override public List<InfluenceLifetimeDescriptor> influenceLifetimes() {
        return lifetimeObserver.map(PvEInfluenceLifetimeObserver::influenceLifetimes).orElse(List.of());
    }
    @Override public CompletionStage<InfluenceObservation> observeInfluence(final WeaverInfluenceRecord influence) {
        return lifetimeObserver.map(observer -> observer.observeInfluence(influence)).orElseGet(() -> CompletableFuture.completedFuture(InfluenceObservation.unavailable()));
    }
    private static <T> void register(final WeaverTypeRegistry types, final Map<String, WeaverValueCatalog> catalogs, final String id, final WeaverTypeId type, final String capability,
            final Supplier<Map<String, T>> registry, final Function<T, Component> label) {
        types.register(ScalarTypeCodec.reference(type, key -> registry.get().containsKey(key)));
        catalogs.put(id, new RegistryValueCatalog<>(type, "pve", FACET, Set.of(capability), registry, label, System::currentTimeMillis));
    }
    private static <E extends Enum<E>> Map<String, E> enumValues(final E[] values) {
        final Map<String, E> result = new LinkedHashMap<>(); for (final E value : values) result.put(value.name().toLowerCase(Locale.ROOT), value); return Map.copyOf(result);
    }
    private static Map<String, WeaverValue> capture(final SubjectRef ref, final MobTemplateRegistry templates, final MobScalingManager scaling, final MobAbilityRuntime runtime, final PvEMobProjectionSource source) {
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
        facts.putAll(projectionFacts(entity.entityId(), runtime.canonicalProfile(mob), source, now));
        facts.put("pve.cast_state", scalar("text", runtime.activeStateSummary(mob), now));
        runtime.controlView(mob).ifPresent(view -> facts.putAll(PvERuntimeActions.facts(view, now)));
        return Map.copyOf(facts);
    }
    static Map<String, WeaverValue> projectionFacts(final UUID entityId, final CanonicalMobProfile canonical, final PvEMobProjectionSource source, final long now) {
        final Map<String, WeaverValue> facts = new HashMap<>();
        final List<WeaverProjection> projections = source.active(entityId);
        final EffectiveMobProjection effective = source.resolve(canonical, projections);
        facts.put(PvEProjectionActions.CANONICAL_REVISION, scalar("text", source.canonicalRevision(canonical), now));
        facts.put(PvEProjectionActions.PROJECTION_REVISION, scalar("text", WeaverProjectionFingerprint.of(projections), now));
        facts.put("pve.effective_rank", reference(RANK, effective.rank().name().toLowerCase(Locale.ROOT), "pve.rank", now));
        facts.put("pve.effective_archetype", scalar("text", effective.archetype().map(value -> value.name().toLowerCase(Locale.ROOT)).orElse(""), now));
        facts.put("pve.effective_template", scalar("text", effective.templateId(), now));
        facts.put("pve.effective_abilities", scalar("text", String.join(", ", effective.abilityIds()), now));
        facts.put("pve.effective_behavior", scalar("text", effective.behavior().toString(), now));
        facts.put("pve.projections", scalar("text", String.join(", ", projections.stream().map(projection -> projection.projectionId().toString()).toList()), now));
        return Map.copyOf(facts);
    }
    static WeaverValue scalar(final String type, final Object value, final long now) {
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
        final Set<String> covered = new HashSet<>(Set.of(FACET, "pve.abilities", "pve.templates", "pve.ranks", "pve.archetypes", "pve.export_rank", "pve.export_archetype", "pve.export_template", "pve.export_ability"));
        contribution.actions().forEach(action -> covered.add(action.id())); contribution.imports().forEach(importer -> covered.add(importer.id()));
        contribution.catalogs().forEach(catalog -> covered.add(catalog.id()));
        return new ProviderCoverage("pve.registered_surface", CoverageLevel.FULL_PROVIDER,
                "Registered catalog, owner snapshot, typed import/export and journal-backed combat projection surface. The independent WW-00 pve domain remains blocked until force ability, AREA/context, native evidence and reward integration are complete.", covered);
    }
    @Override public Map<String, WeaverValue> captureOnOwner(final SubjectRef ref) { return Map.copyOf(snapshots.apply(ref)); }
    @Override public ProviderDiscovery discover(final SubjectSnapshot snapshot) {
        if (!(snapshot.ref() instanceof AreaRef) && !snapshot.facts().containsKey("pve.rank")) return new ProviderDiscovery(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of());
        final Set<String> exports = new HashSet<>();
        for (final String field : List.of("rank", "archetype", "template", "ability")) if (snapshot.facts().containsKey("pve." + field)) exports.add("pve.export_" + field);
        final boolean actionable = snapshot.ref() instanceof EntityRef && snapshot.facts().containsKey(PvEProjectionActions.CANONICAL_REVISION);
        final Set<String> visibleCatalogs = new HashSet<>(catalogs.keySet());
        if (actionable && mutations.isPresent()) visibleCatalogs.add(PvEProjectionActions.CATALOG);
        final Set<String> visibleActions = new HashSet<>(actionable ? mutations.map(actions -> actions.visible(snapshot)).orElse(Set.of()) : Set.of());
        if (actionable) controls.ifPresent(control -> visibleActions.addAll(control.visible(snapshot)));
        return new ProviderDiscovery(Set.of(FACET), visibleActions, visibleCatalogs, exports,
                actionable ? mutations.map(actions -> actions.imports().stream().map(ImportDescriptor::id).collect(java.util.stream.Collectors.toUnmodifiableSet())).orElse(Set.of()) : Set.of(), Map.of());
    }
    @Override public InspectionResult inspect(final ProviderContext context, final SubjectSnapshot snapshot, final String facetId) {
        context.authority().requireValid(); if (!FACET.equals(facetId)) throw new WeaverDomainRejection("UNKNOWN_FACET");
        final Map<String, WeaverValue> facts = new TreeMap<>(); snapshot.facts().forEach((key, value) -> { if (key.startsWith("pve.")) facts.put(key, value); });
        return new InspectionResult(FACET, facts, List.of());
    }
    @Override public Optional<WeaverValueCatalog> catalog(final ProviderContext context, final SubjectSnapshot snapshot, final String id) {
        context.authority().requireValid();
        if (PvEProjectionActions.CATALOG.equals(id)) return mutations.flatMap(actions -> actions.catalog(snapshot));
        return Optional.ofNullable(catalogs.get(id));
    }
    @Override public ValueExportResult exportValue(final ProviderContext context, final SubjectSnapshot snapshot, final String id) {
        context.authority().requireValid();
        final var descriptor = contribution.exports().stream().filter(export -> export.id().equals(id)).findFirst().orElse(null);
        if (descriptor == null) return ValueExportResult.rejected("UNKNOWN_EXPORT");
        final WeaverValue value = snapshot.facts().get("pve." + id.substring("pve.export_".length()));
        return value != null && context.types().compatible(value, descriptor.outputType(), descriptor.capabilities()) ? ValueExportResult.exported(value) : ValueExportResult.rejected("EXPORT_UNAVAILABLE");
    }
    @Override public PreparedAction prepare(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request) {
        context.authority().requireValid();
        return controls.filter(c -> c.owns(request.actionId())).map(c -> c.prepare(context, snapshot, request))
                .orElseGet(() -> mutations.orElseThrow(() -> new WeaverDomainRejection("UNKNOWN_ACTION")).prepare(context, snapshot, request));
    }
    @Override public PreparedEffects prepareEffects(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request, final PreparedAction prepared) {
        return controls.filter(c -> c.owns(request.actionId())).map(c -> c.effects(context))
                .orElseGet(() -> mutations.orElseThrow(() -> new WeaverDomainRejection("UNKNOWN_ACTION")).effects(context, snapshot, request, prepared));
    }
    @Override public List<ProjectionConsumerDescriptor> projectionConsumers() { return mutations.map(PvEProjectionActions::consumers).orElse(List.of()); }
    @Override public CompletionStage<Void> reconcileProjections(final Set<SubjectRef> subjects) { return reconcile.apply(Set.copyOf(subjects)); }
    @Override public PreparedAction prepareUndo(final ProviderContext context, final SubjectSnapshot snapshot, final WeaverReceipt receipt) {
        context.authority().requireValid(); return mutations.orElseThrow(() -> new WeaverDomainRejection("UNKNOWN_UNDO_ACTION")).undo(context, snapshot, receipt);
    }
    @Override public ImportValidation validateImport(final ProviderContext context, final SubjectSnapshot snapshot, final String id, final WeaverValue value) {
        context.authority().requireValid(); return mutations.map(actions -> actions.validateImport(context, snapshot, id, value)).orElseGet(() -> ImportValidation.rejected("UNKNOWN_IMPORT"));
    }
    @Override public RecoveryAssessment assessRecovery(final RecoveryContext context, final SubjectSnapshot snapshot, final WeaverOperationRecord operation) {
        context.authority().require(operation);
        return controls.filter(control -> control.owns(operation.request().actionId())).map(control -> control.assess(context, snapshot, operation))
                .orElseGet(() -> mutations.map(actions -> actions.assess(context, snapshot, operation)).orElseGet(() -> new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), "UNREGISTERED_MUTATION")));
    }
}
