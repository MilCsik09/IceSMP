package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.ProviderCircuitBreaker;
import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;
import hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind;
import java.util.*;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** The frontend consumes validated immutable descriptors and discovery, never subsystem names. */
public final class WorldWeaverProviderRegistry {
    private record Entry(WorldWeaverProvider provider, String id, Set<WeaverSubjectKind> kinds,
                         ProviderContribution contribution, ProviderCoverage coverage, ProviderCircuitBreaker breaker) {}
    public record Discovery(Map<String, ProviderDiscovery> providers, Map<String, String> errors) {
        public Discovery { providers = Map.copyOf(providers); errors = Map.copyOf(errors); }
    }
    private final WeaverTypeRegistry types;
    private final LongSupplier clock;
    private final Map<String, Entry> providers = new LinkedHashMap<>();
    private Map<String, FacetDescriptor> facets = Map.of();
    private Map<String, ActionDescriptor> actions = Map.of();
    private Map<String, CatalogDescriptor> catalogs = Map.of();
    private Map<String, ExportDescriptor> exports = Map.of();
    private Map<String, ImportDescriptor> imports = Map.of();
    private Map<String, String> owners = Map.of();
    private volatile boolean frozen;
    public WorldWeaverProviderRegistry(final WeaverTypeRegistry types, final LongSupplier monotonicMillis) {
        this.types = Objects.requireNonNull(types); clock = Objects.requireNonNull(monotonicMillis);
    }
    public synchronized void register(final WorldWeaverProvider provider) {
        Objects.requireNonNull(provider);
        if (frozen || providers.size() >= 128) throw new IllegalStateException("Provider registration closed or full");
        final String id = WeaverIds.descriptor(provider.id());
        if (providers.containsKey(id) || provider.contractVersion() != 1) throw new IllegalArgumentException("Duplicate or incompatible provider");
        final Entry entry = new Entry(provider, id, Set.copyOf(provider.supportedKinds()), Objects.requireNonNull(provider.contribution()),
                Objects.requireNonNull(provider.coverage()), new ProviderCircuitBreaker(clock));
        if (entry.coverage().level() != CoverageLevel.NO_RUNTIME_SURFACE && entry.kinds().isEmpty()) throw new IllegalArgumentException("Provider has no subject kinds");
        providers.put(id, entry);
    }
    public synchronized void freezeAndValidate() {
        if (frozen) return;
        final Map<String, FacetDescriptor> facetMap = new LinkedHashMap<>();
        final Map<String, ActionDescriptor> actionMap = new LinkedHashMap<>();
        final Map<String, CatalogDescriptor> catalogMap = new LinkedHashMap<>();
        final Map<String, ExportDescriptor> exportMap = new LinkedHashMap<>();
        final Map<String, ImportDescriptor> importMap = new LinkedHashMap<>();
        final Map<String, String> ownerMap = new LinkedHashMap<>();
        final Set<String> ids = new HashSet<>(providers.keySet());
        for (final Entry entry : providers.values()) {
            if (entry.coverage().level() == CoverageLevel.DEFERRED_BLOCKER) throw new IllegalArgumentException("Provider coverage blocker: " + entry.id());
            final ProviderContribution contribution = entry.contribution();
            add(entry, contribution.facets(), FacetDescriptor::id, facetMap, ownerMap, ids);
            add(entry, contribution.actions(), ActionDescriptor::id, actionMap, ownerMap, ids);
            add(entry, contribution.catalogs(), CatalogDescriptor::id, catalogMap, ownerMap, ids);
            add(entry, contribution.exports(), ExportDescriptor::id, exportMap, ownerMap, ids);
            add(entry, contribution.imports(), ImportDescriptor::id, importMap, ownerMap, ids);
            if (entry.coverage().level() == CoverageLevel.INSPECT_ONLY_BY_DESIGN && !contribution.actions().isEmpty()) {
                throw new IllegalArgumentException("Inspect-only provider publishes actions");
            }
            if (entry.coverage().level() == CoverageLevel.NO_RUNTIME_SURFACE && (!contribution.facets().isEmpty() || !contribution.actions().isEmpty()
                    || !contribution.catalogs().isEmpty() || !contribution.exports().isEmpty() || !contribution.imports().isEmpty())) {
                throw new IllegalArgumentException("No-runtime provider publishes runtime surface");
            }
        }
        if (actionMap.size() > 4096) throw new IllegalArgumentException("Global action cap");
        for (final Entry entry : providers.values()) {
            final ProviderContribution contribution = entry.contribution();
            for (final CatalogDescriptor catalog : contribution.catalogs()) {
                owned(entry, catalog.facetId(), facetMap, ownerMap); types.require(catalog.type());
            }
            for (final ActionDescriptor action : contribution.actions()) {
                owned(entry, action.facetId(), facetMap, ownerMap);
                if (!entry.kinds().containsAll(action.subjects())) throw new IllegalArgumentException("Unsupported action subject");
                validatePolicy(action);
                for (final ActionParameter parameter : action.parameters()) {
                    if (!types.require(parameter.type()).supportedInputs().contains(parameter.input())) {
                        throw new IllegalArgumentException("Parameter input is incompatible with its type codec");
                    }
                    if (parameter.catalogId().isPresent()) {
                        final CatalogDescriptor catalog = owned(entry, parameter.catalogId().get(), catalogMap, ownerMap);
                        if (!catalog.type().equals(parameter.type())) throw new IllegalArgumentException("Catalog parameter type mismatch");
                    }
                    parameter.defaultValue().ifPresent(value -> parameter.validate(value, types).requireValid());
                }
                if (action.requiresJournal() && !contribution.recoveryCapabilitiesByAction().containsKey(action.id())) {
                    throw new IllegalArgumentException("Journal action lacks observed-state recovery capability");
                }
            }
            for (final String action : contribution.recoveryCapabilitiesByAction().keySet()) owned(entry, action, actionMap, ownerMap);
            for (final ExportDescriptor export : contribution.exports()) {
                owned(entry, export.facetId(), facetMap, ownerMap); types.require(export.outputType());
            }
            for (final ImportDescriptor importer : contribution.imports()) {
                final ActionDescriptor action = owned(entry, importer.actionId(), actionMap, ownerMap);
                types.require(importer.acceptedType());
                final ActionParameter parameter = action.parameters().stream().filter(p -> p.id().equals(importer.parameterId())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Importer references absent parameter"));
                if (!parameter.type().equals(importer.acceptedType()) || !importer.requiredCapabilities().containsAll(parameter.requiredCapabilities())) {
                    throw new IllegalArgumentException("Importer parameter type/capability mismatch");
                }
            }
        }
        types.freeze(); facets = Map.copyOf(facetMap); actions = Map.copyOf(actionMap); catalogs = Map.copyOf(catalogMap);
        exports = Map.copyOf(exportMap); imports = Map.copyOf(importMap); owners = Map.copyOf(ownerMap); frozen = true;
    }
    private static void validatePolicy(final ActionDescriptor action) {
        if (action.risk() == RiskLevel.CANONICAL && (!action.integrityModes().equals(Set.of(IntegrityMode.LIVE_GM))
                || !action.lifetimes().equals(Set.of(Lifetime.ONE_SHOT)))) throw new IllegalArgumentException("Canonical action integrity/lifetime violation");
        if (action.lifetimes().contains(Lifetime.PERSISTENT) && !action.undoable()) throw new IllegalArgumentException("Persistent action must be conditionally undoable");
        if (action.integrityImpacts().contains(IntegrityImpact.EVENT_ORIGIN) && action.rateCost() != 10) throw new IllegalArgumentException("Event-origin action must cost 10");
        if (action.risk().ordinal() >= RiskLevel.DESTRUCTIVE.ordinal()) {
            if (!action.undoable() && action.irreversibleReason().isEmpty()) throw new IllegalArgumentException("Irreversible action requires reason");
            if (action.rateCost() != 10) throw new IllegalArgumentException("Destructive/canonical action must cost 10");
            if (action.areaSupport() != AreaSupport.NONE || action.subjects().contains(WeaverSubjectKind.AREA)) throw new IllegalArgumentException("Mass canonical/destructive edit forbidden");
        }
        if (action.areaSupport() != AreaSupport.NONE && action.areaLimits().isEmpty()) throw new IllegalArgumentException("AREA descriptor lacks limits");
        if (action.subjects().contains(WeaverSubjectKind.AREA) && action.areaSupport() == AreaSupport.NONE) throw new IllegalArgumentException("AREA subject without fanout policy");
        if (action.integrityImpacts().contains(IntegrityImpact.NONE) && action.integrityImpacts().size() != 1) throw new IllegalArgumentException("Ambiguous integrity impact");
        if (action.risk().ordinal() >= RiskLevel.MUTATING.ordinal() && action.integrityModes().contains(IntegrityMode.SANDBOX)
                && action.integrityImpacts().equals(Set.of(IntegrityImpact.NONE))) throw new IllegalArgumentException("Sandbox mutation without influence");
    }
    private static <T> void add(final Entry entry, final List<T> values, final Function<T, String> id,
                               final Map<String, T> destination, final Map<String, String> owners, final Set<String> allIds) {
        for (final T value : values) {
            final String key = id.apply(value);
            if (!key.startsWith(entry.id() + ".") || !allIds.add(key)) throw new IllegalArgumentException("Duplicate or foreign descriptor id");
            destination.put(key, value); owners.put(key, entry.id());
        }
    }
    private static <T> T owned(final Entry entry, final String id, final Map<String, T> values, final Map<String, String> owners) {
        final T result = values.get(id);
        if (result == null || !entry.id().equals(owners.get(id))) throw new IllegalArgumentException("Missing or foreign descriptor reference");
        return result;
    }
    private void requireFrozen() { if (!frozen) throw new IllegalStateException("Provider registry is not validated"); }
    public Discovery discover(final SubjectSnapshot snapshot) {
        requireFrozen();
        final Map<String, ProviderDiscovery> discovered = new TreeMap<>();
        final Map<String, String> errors = new TreeMap<>();
        for (final Entry entry : providers.values()) {
            if (!entry.kinds().contains(snapshot.ref().kind())) continue;
            try {
                final ProviderDiscovery result = entry.breaker().call(() -> {
                    final ProviderDiscovery found = Objects.requireNonNull(entry.provider().discover(snapshot));
                    found.facets().forEach(id -> owned(entry, id, facets, owners)); found.actions().forEach(id -> owned(entry, id, actions, owners));
                    found.catalogs().forEach(id -> owned(entry, id, catalogs, owners)); found.exports().forEach(id -> owned(entry, id, exports, owners));
                    found.imports().forEach(id -> owned(entry, id, imports, owners)); found.blockedActions().keySet().forEach(id -> owned(entry, id, actions, owners));
                    for (final String action : found.actions()) if (!found.facets().contains(actions.get(action).facetId())) throw new IllegalArgumentException("Action without discovered facet");
                    for (final String catalog : found.catalogs()) if (!found.facets().contains(catalogs.get(catalog).facetId())) throw new IllegalArgumentException("Catalog without discovered facet");
                    for (final String export : found.exports()) if (!found.facets().contains(exports.get(export).facetId())) throw new IllegalArgumentException("Export without discovered facet");
                    for (final String importer : found.imports()) if (!found.actions().contains(imports.get(importer).actionId())) throw new IllegalArgumentException("Importer without discovered action");
                    return found;
                }, false);
                discovered.put(entry.id(), result);
            } catch (final WeaverDomainRejection failure) { errors.put(entry.id(), failure.code()); }
        }
        return new Discovery(discovered, errors);
    }
    public <T> T invoke(final String providerId, final ProviderContext context, final Function<WorldWeaverProvider, T> action) {
        requireFrozen();
        final Entry entry = providers.get(providerId);
        if (entry == null) throw new WeaverDomainRejection("UNKNOWN_PROVIDER");
        Objects.requireNonNull(context).authority().requireValid();
        return entry.breaker().call(() -> action.apply(entry.provider()), false);
    }
    public Map<String, WeaverValue> captureContributions(final hu.taliann.icesmp.dev.weaver.subject.SubjectRef subject) {
        requireFrozen();
        final Map<String, WeaverValue> facts = new TreeMap<>();
        for (final Entry entry : providers.values()) {
            if (!entry.kinds().contains(subject.kind()) || !(entry.provider() instanceof WeaverSnapshotContributor contributor)) continue;
            try {
                final Map<String, WeaverValue> captured = entry.breaker().call(() -> {
                    final Map<String, WeaverValue> values = Map.copyOf(contributor.captureOnOwner(subject));
                    if (values.size() > 128) throw new IllegalArgumentException("Provider snapshot fact cap");
                    values.forEach((key, value) -> {
                        if (!key.startsWith(entry.id() + ".") || !value.sourceProvider().equals(entry.id())) throw new IllegalArgumentException("Foreign snapshot fact");
                        types.validate(value);
                    });
                    return values;
                }, false);
                if (facts.size() + captured.size() > 200) throw new WeaverDomainRejection("SNAPSHOT_FACT_CAP");
                facts.putAll(captured);
            } catch (final WeaverDomainRejection failure) {
                if (failure.code().equals("SNAPSHOT_FACT_CAP")) throw failure;
            }
        }
        return Map.copyOf(facts);
    }
    public hu.taliann.icesmp.dev.weaver.persistence.RecoveryAssessment assessRecovery(final String providerId, final ProviderContext context,
            final SubjectSnapshot snapshot, final hu.taliann.icesmp.dev.weaver.persistence.WeaverOperationRecord operation) {
        requireFrozen();
        final Entry entry = providers.get(providerId);
        if (entry == null || !operation.providerId().equals(providerId)) throw new WeaverDomainRejection("UNKNOWN_PROVIDER");
        return entry.breaker().call(() -> entry.provider().assessRecovery(context, snapshot, operation), true);
    }
    public Map<String, FacetDescriptor> facets() { requireFrozen(); return facets; }
    public Map<String, ActionDescriptor> actions() { requireFrozen(); return actions; }
    public Map<String, CatalogDescriptor> catalogs() { requireFrozen(); return catalogs; }
    public Map<String, ExportDescriptor> exports() { requireFrozen(); return exports; }
    public Map<String, ImportDescriptor> imports() { requireFrozen(); return imports; }
    public String owner(final String descriptorId) { requireFrozen(); return Optional.ofNullable(owners.get(descriptorId)).orElseThrow(() -> new WeaverDomainRejection("UNKNOWN_DESCRIPTOR")); }
    public List<ProviderCoverage> coverage() { requireFrozen(); return providers.values().stream().map(Entry::coverage).toList(); }
    public boolean quarantined(final String providerId) {
        requireFrozen(); final Entry entry = providers.get(providerId);
        if (entry == null) throw new WeaverDomainRejection("UNKNOWN_PROVIDER");
        return entry.breaker().quarantined();
    }
}
