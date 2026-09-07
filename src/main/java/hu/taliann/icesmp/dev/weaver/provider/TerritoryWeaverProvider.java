package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.managers.TerritoryProtectionService;
import hu.taliann.icesmp.territory.*;
import hu.taliann.icesmp.territory.TerritoryProtectionPolicy.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import java.util.*;
import java.util.function.*;

/** Descriptor-only adapter. TerritoryProtectionService remains the effective protection authority. */
public final class TerritoryWeaverProvider implements WorldWeaverProvider, WeaverSnapshotContributor, WeaverProjectionProvider {
    static final String FACET = "territory.runtime", CANONICAL = "territory.canonical_revision", PROJECTIONS = "territory.projection_revision";
    static final String WORLD = "territory.world", RULE_VALUE = "territory.latest_projected_rule";
    static final WeaverTypeId TERRITORY = WeaverTypeId.parse("icesmp:territory_ref@1"), RULE = WeaverTypeId.parse("icesmp:territory_rule_ref@1");
    static final WeaverRevisionScope SCOPE = new WeaverRevisionScope(1, Set.of(CANONICAL, PROJECTIONS, WORLD));
    private final TerritoryManager manager;
    private final Function<WorldRef, String> worldNames;
    private final TerritoryRuntimeProjectionSource source;
    private final TerritoryProjectionActions actions;
    private final TerritoryCanonicalActions canonical;
    private final Map<String, WeaverValueCatalog> catalogs;
    private final ProviderContribution contribution;

    public TerritoryWeaverProvider(WeaverProviderServices services, TerritoryManager manager, TerritoryProtectionService protection) {
        this(services.types(), manager, new TerritoryRuntimeProjectionSource(services.projections(), manager::getById, System::currentTimeMillis), ref -> {
            if (!Bukkit.isGlobalTickThread()) throw new IllegalStateException("Foreign territory world access");
            final var world = Bukkit.getWorld(ref.worldId());
            if (world == null) throw new WeaverDomainRejection("WORLD_UNAVAILABLE");
            return world.getName();
        });
        protection.bindRuleProjection((world, territory, rule) -> services.readConsumer("territory", () -> source.resolve(world, territory, rule)));
    }
    TerritoryWeaverProvider(WeaverTypeRegistry types, TerritoryManager manager, TerritoryRuntimeProjectionSource source, Function<WorldRef, String> worldNames) {
        this.manager = Objects.requireNonNull(manager); this.source = Objects.requireNonNull(source); this.worldNames = Objects.requireNonNull(worldNames);
        types.register(ScalarTypeCodec.reference(TERRITORY, key -> territories().containsKey(key)));
        types.register(ScalarTypeCodec.reference(RULE, key -> rules().containsKey(key)));
        types.register(TerritoryRuntimeProjectionSource.codec());
        types.register(ScalarTypeCodec.reference(TerritoryCanonicalActions.ZONE_TYPE, key -> enums(hu.taliann.icesmp.data.TerritoryType.values()).containsKey(key)));
        types.register(ScalarTypeCodec.reference(TerritoryCanonicalActions.ZONE_OWNER, key -> enums(hu.taliann.icesmp.data.FactionType.values()).containsKey(key)));
        catalogs = Map.of("territory.zones", new RegistryValueCatalog<>(TERRITORY, "territory", FACET, Set.of("territory.zone"), this::territories,
                        zone -> Component.text(zone.name() + " [" + zone.id() + "] — " + zone.world()), System::currentTimeMillis),
                "territory.rules", new RegistryValueCatalog<>(RULE, "territory", FACET, Set.of("territory.rule"), TerritoryWeaverProvider::rules, Component::text, System::currentTimeMillis),
                "territory.types", new RegistryValueCatalog<>(TerritoryCanonicalActions.ZONE_TYPE, "territory", FACET, Set.of("territory.type"),
                        () -> enums(hu.taliann.icesmp.data.TerritoryType.values()), v -> Component.text(v.name()), System::currentTimeMillis),
                "territory.owners", new RegistryValueCatalog<>(TerritoryCanonicalActions.ZONE_OWNER, "territory", FACET, Set.of("territory.owner"),
                        () -> enums(hu.taliann.icesmp.data.FactionType.values()), v -> Component.text(v.getDisplayName()), System::currentTimeMillis));
        actions = new TerritoryProjectionActions(source, this::captureOnOwner, this::territories);
        canonical = new TerritoryCanonicalActions(manager, this::territories, this::captureOnOwner);
        final List<ActionDescriptor> descriptors = new ArrayList<>(actions.descriptors()); descriptors.addAll(canonical.descriptors());
        contribution = new ProviderContribution(List.of(new FacetDescriptor(FACET, Component.text("Terület"), Component.text("Kanonikus zónák és védelmi rávetítések"), 30)),
                descriptors, catalogs.entrySet().stream().map(e -> new CatalogDescriptor(e.getKey(), FACET, Component.text(e.getKey()), e.getValue().type())).toList(),
                List.of(new ExportDescriptor("territory.export_rule", FACET, RULE, Set.of("territory.rule"))),
                List.of(new ImportDescriptor("territory.import_rule", TerritoryProjectionActions.APPLY, RULE, Set.of("territory.rule"), "value")),
                descriptors.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ActionDescriptor::id, a -> canonical.owns(a.id()) ? "territory.native_transaction" : "territory.journal_projection")));
    }
    private static <E extends Enum<E>> Map<String, E> enums(E[] values) {
        final Map<String, E> result = new TreeMap<>(); for (final var value : values) result.put(value.name().toLowerCase(Locale.ROOT), value); return Map.copyOf(result);
    }
    Map<String, Territory> territories() {
        if (!manager.adjustmentStateAvailable()) throw new WeaverDomainRejection("TERRITORY_UNAVAILABLE");
        final Map<String, Territory> result = new TreeMap<>();
        for (final var zone : manager.all()) if (result.putIfAbsent(zoneKey(zone.id()), zone) != null) throw new WeaverDomainRejection("TERRITORY_ID_COLLISION");
        return Map.copyOf(result);
    }
    static String zoneKey(String id) { return digest(List.of(id)); }
    static Map<String, String> rules() {
        final Map<String, String> result = new TreeMap<>();
        for (final var rule : TerritoryProtectionPolicy.Rule.values()) for (final var overlay : Overlay.values()) {
            result.put(rule.name().toLowerCase(Locale.ROOT) + "/" + overlay.name().toLowerCase(Locale.ROOT), rule.name() + " → " + overlay.name());
        }
        return Map.copyOf(result);
    }
    static String digest(Collection<String> values) {
        try {
            final var hash = java.security.MessageDigest.getInstance("SHA-256");
            for (final String value : values) { final byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); hash.update(bytes); }
            return HexFormat.of().formatHex(hash.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    @Override public Map<String, WeaverValue> captureOnOwner(SubjectRef subject) {
        if (!(subject instanceof WorldRef world)) return Map.of();
        final String name = worldNames.apply(world); final var zones = territories(); final var active = source.active(world); final long now = System.currentTimeMillis();
        final var revisions = zones.values().stream().filter(z -> z.world().equals(name)).sorted(Comparator.comparing(Territory::id)).map(TerritoryRevision::fingerprint).toList();
        final Map<String, WeaverValue> facts = new HashMap<>();
        facts.put(CANONICAL, scalar(digest(revisions), now)); facts.put(PROJECTIONS, scalar(WeaverProjectionFingerprint.of(active), now));
        facts.put(WORLD, scalar(name, now)); facts.put("territory.count", scalar(Integer.toString(revisions.size()), now));
        facts.put("territory.projection_count", scalar(Integer.toString(active.size()), now));
        active.stream().max(Comparator.comparingLong(WeaverProjection::sequence)).ifPresent(p -> {
            final var value = p.values().get(TerritoryRuntimeProjectionSource.FIELD);
            TerritoryRuntimeProjectionSource.codec().validate(value.payload()).requireValid();
            final String key = ((String) value.payload().get("rule")).toLowerCase(Locale.ROOT) + "/" + ((String) value.payload().get("overlay")).toLowerCase(Locale.ROOT);
            facts.put(RULE_VALUE, new WeaverValue(RULE, Map.of("id", key), "territory", FACET, Set.of("territory.rule"), now));
        });
        return Map.copyOf(facts);
    }
    static WeaverValue scalar(String value, long now) { return new WeaverValue(WeaverTypeId.parse("weaver:text@1"), Map.of("value", value), "territory", FACET, Set.of(), now); }
    static String text(SubjectSnapshot snapshot, String field) {
        final var value = snapshot.facts().get(field);
        if (value == null || !(value.payload().get("value") instanceof String text)) throw new WeaverDomainRejection("REVISION_FIELD_UNAVAILABLE");
        return text;
    }
    @Override public String id() { return "territory"; }
    @Override public int contractVersion() { return 1; }
    @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.WORLD); }
    @Override public ProviderContribution contribution() { return contribution; }
    @Override public ProviderCoverage coverage() {
        final Set<String> surfaces = new HashSet<>(Set.of(FACET, "territory.export_rule", "territory.import_rule"));
        surfaces.addAll(catalogs.keySet()); contribution.actions().forEach(a -> surfaces.add(a.id()));
        return new ProviderCoverage("territory.registered_surface", CoverageLevel.FULL_PROVIDER,
                "World-owned protection projection, typed rule import/export and native LIVE_GM conditional transactions. WW-00 remains blocked pending location trace and native runtime evidence.", surfaces);
    }
    @Override public ProviderDiscovery discover(SubjectSnapshot snapshot) {
        if (!(snapshot.ref() instanceof WorldRef) || !snapshot.facts().containsKey(CANONICAL)) return new ProviderDiscovery(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of());
        final Set<String> visible = new HashSet<>(Set.of(TerritoryProjectionActions.APPLY, TerritoryProjectionActions.CLEAR)); canonical.descriptors().forEach(a -> visible.add(a.id()));
        return new ProviderDiscovery(Set.of(FACET), visible, catalogs.keySet(),
                snapshot.facts().containsKey(RULE_VALUE) ? Set.of("territory.export_rule") : Set.of(), Set.of("territory.import_rule"), Map.of());
    }
    @Override public InspectionResult inspect(ProviderContext context, SubjectSnapshot snapshot, String facetId) {
        context.authority().requireValid(); if (!FACET.equals(facetId)) throw new WeaverDomainRejection("UNKNOWN_FACET");
        final Map<String, WeaverValue> facts = new TreeMap<>(); snapshot.facts().forEach((key, value) -> { if (key.startsWith("territory.")) facts.put(key, value); });
        return new InspectionResult(FACET, facts, List.of());
    }
    @Override public Optional<WeaverValueCatalog> catalog(ProviderContext context, SubjectSnapshot snapshot, String id) {
        context.authority().requireValid();
        if ("territory.rules".equals(id) && context.integrityMode() == IntegrityMode.SANDBOX) {
            return Optional.of(new RegistryValueCatalog<>(RULE, "territory", FACET, Set.of("territory.rule"),
                    () -> rules().entrySet().stream().filter(e -> !e.getKey().endsWith("/allow"))
                            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)), Component::text, System::currentTimeMillis));
        }
        return Optional.ofNullable(catalogs.get(id));
    }
    @Override public ValueExportResult exportValue(ProviderContext context, SubjectSnapshot snapshot, String id) {
        context.authority().requireValid(); final var value = snapshot.facts().get(RULE_VALUE);
        return "territory.export_rule".equals(id) && value != null && context.types().compatible(value, RULE, Set.of("territory.rule"))
                ? ValueExportResult.exported(value) : ValueExportResult.rejected("EXPORT_UNAVAILABLE");
    }
    @Override public ImportValidation validateImport(ProviderContext context, SubjectSnapshot snapshot, String id, WeaverValue value) {
        context.authority().requireValid();
        if (context.integrityMode() == IntegrityMode.SANDBOX && value.payload().get("id") instanceof String key && key.endsWith("/allow"))
            return ImportValidation.rejected("ALLOW_REQUIRES_LIVE_GM");
        return "territory.import_rule".equals(id) && snapshot.ref() instanceof WorldRef && snapshot.facts().containsKey(CANONICAL)
                && context.types().compatible(value, RULE, Set.of("territory.rule")) ? ImportValidation.accepted() : ImportValidation.rejected("THREAD_INCOMPATIBLE");
    }
    @Override public PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) { return canonical.owns(request.actionId()) ? canonical.prepare(context, snapshot, request) : actions.prepare(context, snapshot, request); }
    @Override public PreparedEffects prepareEffects(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request, PreparedAction prepared) { return canonical.owns(request.actionId()) ? canonical.effects(context) : actions.effects(context, snapshot, request, prepared); }
    @Override public PreparedAction prepareUndo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) { return canonical.owns(receipt.actionId()) ? canonical.undo(context, snapshot, receipt) : actions.undo(context, snapshot, receipt); }
    @Override public RecoveryAssessment assessRecovery(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) { return canonical.owns(operation.request().actionId()) ? canonical.assess(context, snapshot, operation) : actions.assess(context, snapshot, operation); }
    @Override public List<ProjectionConsumerDescriptor> projectionConsumers() { return actions.consumers(); }
}
