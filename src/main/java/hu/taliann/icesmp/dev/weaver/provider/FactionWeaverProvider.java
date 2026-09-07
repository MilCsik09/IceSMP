package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.factions.*;
import hu.taliann.icesmp.factions.FactionPassivePolicy.ContentContext;
import hu.taliann.icesmp.managers.FactionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.*;

/** Canonical faction adapters and typed effective projections through the unchanged generic frontend. */
public final class FactionWeaverProvider implements WorldWeaverProvider, WeaverSnapshotContributor, WeaverProjectionProvider {
    static final String FACET = "faction.runtime";
    static final WeaverTypeId FACTION = WeaverTypeId.parse("icesmp:faction_ref@1"), CONTEXT = WeaverTypeId.parse("icesmp:faction_context_ref@1");
    private final Map<String, WeaverValueCatalog> catalogs;
    private final Function<SubjectRef, Map<String, WeaverValue>> snapshots;
    private final FactionProjectionActions mutations;
    private final ProviderContribution contribution;
    private record Ports(FactionRuntimeProjectionSource source, Function<SubjectRef, Map<String, WeaverValue>> snapshots) { }

    public FactionWeaverProvider(WeaverProviderServices services, FactionManager factions, FactionMobContextResolver contexts, FactionPassiveConfig config) {
        this(services.types(), nativePorts(services, factions, contexts, config));
    }
    private FactionWeaverProvider(WeaverTypeRegistry types, Ports ports) { this(types, ports.source(), ports.snapshots()); }
    FactionWeaverProvider(WeaverTypeRegistry types, FactionRuntimeProjectionSource source, Function<SubjectRef, Map<String, WeaverValue>> snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots); mutations = new FactionProjectionActions(source, snapshots);
        final Map<String, WeaverValueCatalog> all = new LinkedHashMap<>();
        register(types, all, "faction.memberships", FACTION, "faction.membership", () -> enums(Set.of(FactionType.values())), value -> Component.text(value.getDisplayName()));
        register(types, all, "faction.contexts", CONTEXT, "faction.context", () -> enums(FactionContextProjectionSource.projectable()), value -> Component.text(value.name()));
        catalogs = Map.copyOf(all);
        contribution = new ProviderContribution(List.of(new FacetDescriptor(FACET, Component.text("Frakció"), Component.text("Tagság és szemantikus mobkontextus"), 20)),
                mutations.descriptors(), all.entrySet().stream().map(e -> new CatalogDescriptor(e.getKey(), FACET, Component.text(e.getKey()), e.getValue().type())).toList(),
                List.of(new ExportDescriptor("faction.export_membership", FACET, FACTION, Set.of("faction.membership")),
                        new ExportDescriptor("faction.export_context", FACET, CONTEXT, Set.of("faction.context"))), mutations.imports(),
                mutations.descriptors().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ActionDescriptor::id, a -> "faction.journal_projection")));
    }
    private static Ports nativePorts(WeaverProviderServices services, FactionManager factions, FactionMobContextResolver contexts, FactionPassiveConfig config) {
        final var source = new FactionRuntimeProjectionSource(services.projections(), System::currentTimeMillis);
        factions.bindMembershipProjection((id, canonical) -> services.readConsumer("faction", () -> source.resolve(id, canonical)));
        contexts.bindContextProjection((id, canonical) -> services.readConsumer("faction", () -> source.resolve(id, canonical)));
        return new Ports(source, ref -> {
            final UUID id = ref instanceof PlayerRef player ? player.playerId() : ref instanceof EntityRef entity ? entity.entityId() : null;
            if (id == null) return Map.of();
            final var live = Bukkit.getEntity(id);
            if (live == null || !Bukkit.isOwnedByCurrentRegion(live)) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
            if (!live.isValid() || live.isDead()) throw new WeaverDomainRejection("ENTITY_UNAVAILABLE");
            if (ref instanceof PlayerRef) {
                if (!(live instanceof Player) || !factions.isMembershipReady(id)) throw new WeaverDomainRejection("PROFILE_UNAVAILABLE");
                return membershipFacts(ref, factions.getMembership(id), source, System.currentTimeMillis());
            }
            if (!(live instanceof Mob)) return Map.of();
            return contextFacts(ref, contexts.contentContexts(live, config.snapshot()), source, System.currentTimeMillis());
        });
    }
    private static <T> void register(WeaverTypeRegistry types, Map<String, WeaverValueCatalog> catalogs, String id, WeaverTypeId type, String capability,
            Supplier<Map<String, T>> values, Function<T, Component> label) {
        types.register(ScalarTypeCodec.reference(type, key -> values.get().containsKey(key)));
        catalogs.put(id, new RegistryValueCatalog<>(type, "faction", FACET, Set.of(capability), values, label, System::currentTimeMillis));
    }
    private static <E extends Enum<E>> Map<String, E> enums(Set<E> values) {
        final Map<String, E> result = new TreeMap<>(); values.forEach(v -> result.put(v.name().toLowerCase(Locale.ROOT), v)); return Map.copyOf(result);
    }
    static Map<String, WeaverValue> membershipFacts(SubjectRef ref, FactionMembership canonical, FactionRuntimeProjectionSource source, long now) {
        final var projections = source.active(ref); final var effective = FactionRuntimeProjectionSource.membership(canonical, projections);
        final String chosen = canonical.chosenFactionOptional().map(v -> v.name().toLowerCase(Locale.ROOT)).orElse("guest");
        final Map<String, WeaverValue> facts = revisions(Map.of("membership", chosen), projections, now);
        facts.put("faction.canonical_membership", scalar(chosen, now));
        facts.put("faction.effective_membership", scalar(effective.chosenFactionOptional().map(Enum::name).orElse("GUEST"), now));
        effective.chosenFactionOptional().ifPresent(f -> facts.put("faction.membership", reference(FACTION, f.name().toLowerCase(Locale.ROOT), "faction.membership", now)));
        return Map.copyOf(facts);
    }
    static Map<String, WeaverValue> contextFacts(SubjectRef ref, Set<ContentContext> canonical, FactionRuntimeProjectionSource source, long now) {
        final var projections = source.active(ref); final Set<ContentContext> effective = FactionRuntimeProjectionSource.contexts(canonical, projections);
        final var names = canonical.stream().map(Enum::name).sorted().toList();
        final Map<String, WeaverValue> facts = revisions(Map.of("contexts", names), projections, now);
        facts.put("faction.canonical_contexts", scalar(String.join(", ", names), now));
        facts.put("faction.effective_contexts", scalar(String.join(", ", effective.stream().map(Enum::name).sorted().toList()), now));
        final var exportable = effective.stream().filter(FactionContextProjectionSource.projectable()::contains).toList();
        if (exportable.size() == 1) facts.put("faction.context", reference(CONTEXT, exportable.getFirst().name().toLowerCase(Locale.ROOT), "faction.context", now));
        return Map.copyOf(facts);
    }
    private static Map<String, WeaverValue> revisions(Map<String, Object> canonical, List<WeaverProjection> projections, long now) {
        final Map<String, WeaverValue> facts = new HashMap<>();
        facts.put(FactionProjectionActions.CANONICAL_REVISION, scalar(FactionRuntimeProjectionSource.fingerprint(canonical), now));
        facts.put(FactionProjectionActions.PROJECTION_REVISION, scalar(WeaverProjectionFingerprint.of(projections), now));
        facts.put("faction.projections", scalar(String.join(", ", projections.stream().map(p -> p.projectionId().toString()).toList()), now)); return facts;
    }
    static WeaverValue scalar(Object value, long now) { return new WeaverValue(WeaverTypeId.parse("weaver:text@1"), Map.of("value", value), "faction", FACET, Set.of(), now); }
    static WeaverValue reference(WeaverTypeId type, String id, String capability, long now) { return new WeaverValue(type, Map.of("id", id), "faction", FACET, Set.of(capability), now); }
    @Override public String id() { return "faction"; }
    @Override public int contractVersion() { return 1; }
    @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.PLAYER, WeaverSubjectKind.ENTITY); }
    @Override public ProviderContribution contribution() { return contribution; }
    @Override public ProviderCoverage coverage() {
        final Set<String> covered = new HashSet<>(Set.of(FACET)); contribution.actions().forEach(a -> covered.add(a.id()));
        contribution.catalogs().forEach(c -> covered.add(c.id())); contribution.exports().forEach(e -> covered.add(e.id())); contribution.imports().forEach(i -> covered.add(i.id()));
        return new ProviderCoverage("faction.registered_surface", CoverageLevel.FULL_PROVIDER,
                "Typed membership/context projection, canonical/effective inspect, Thread and conditional sever. WW-00 remains blocked pending canonical transactions, scripted target controls and native evidence.", covered);
    }
    @Override public Map<String, WeaverValue> captureOnOwner(SubjectRef ref) { return Map.copyOf(snapshots.apply(ref)); }
    @Override public ProviderDiscovery discover(SubjectSnapshot snapshot) {
        if (!snapshot.facts().containsKey(FactionProjectionActions.CANONICAL_REVISION)) return new ProviderDiscovery(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of());
        final Set<String> exports = new HashSet<>();
        if (snapshot.facts().containsKey("faction.membership")) exports.add("faction.export_membership");
        if (snapshot.facts().containsKey("faction.context")) exports.add("faction.export_context");
        final var visible = mutations.visible(snapshot.ref().kind());
        return new ProviderDiscovery(Set.of(FACET), visible, Set.of(snapshot.ref() instanceof PlayerRef ? "faction.memberships" : "faction.contexts"), exports,
                mutations.imports().stream().filter(i -> visible.contains(i.actionId())).map(ImportDescriptor::id).collect(java.util.stream.Collectors.toUnmodifiableSet()), Map.of());
    }
    @Override public InspectionResult inspect(ProviderContext context, SubjectSnapshot snapshot, String facetId) {
        context.authority().requireValid(); if (!FACET.equals(facetId)) throw new WeaverDomainRejection("UNKNOWN_FACET");
        final Map<String, WeaverValue> facts = new TreeMap<>(); snapshot.facts().forEach((key, value) -> { if (key.startsWith("faction.")) facts.put(key, value); });
        return new InspectionResult(FACET, facts, List.of());
    }
    @Override public Optional<WeaverValueCatalog> catalog(ProviderContext context, SubjectSnapshot snapshot, String id) { context.authority().requireValid(); return Optional.ofNullable(catalogs.get(id)); }
    @Override public ValueExportResult exportValue(ProviderContext context, SubjectSnapshot snapshot, String id) {
        context.authority().requireValid(); final var descriptor = contribution.exports().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
        if (descriptor == null) return ValueExportResult.rejected("UNKNOWN_EXPORT");
        final var value = snapshot.facts().get(id.equals("faction.export_membership") ? "faction.membership" : "faction.context");
        return value != null && context.types().compatible(value, descriptor.outputType(), descriptor.capabilities()) ? ValueExportResult.exported(value) : ValueExportResult.rejected("EXPORT_UNAVAILABLE");
    }
    @Override public PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) { return mutations.prepare(context, snapshot, request); }
    @Override public PreparedEffects prepareEffects(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request, PreparedAction prepared) { return mutations.effects(context, snapshot, request, prepared); }
    @Override public List<ProjectionConsumerDescriptor> projectionConsumers() { return mutations.consumers(); }
    @Override public PreparedAction prepareUndo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) { context.authority().requireValid(); return mutations.undo(context, snapshot, receipt); }
    @Override public ImportValidation validateImport(ProviderContext context, SubjectSnapshot snapshot, String id, WeaverValue value) { context.authority().requireValid(); return mutations.validateImport(context, snapshot, id, value); }
    @Override public RecoveryAssessment assessRecovery(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) { return mutations.assess(context, snapshot, operation); }
}
