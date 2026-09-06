package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.dev.weaver.gui.WeaverFacetView;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class WeaverContractRegressionSuite {
    static class FixtureProvider implements WorldWeaverProvider {
        final String id;
        final ProviderContribution contribution;
        final CoverageLevel level;
        boolean broken;
        FixtureProvider(final String id, final ActionDescriptor action, final CoverageLevel level, final Map<String, String> recovery) {
            this.id = id; this.level = level;
            contribution = new ProviderContribution(List.of(new FacetDescriptor(id + ".state", Component.text(id), Component.text("fixture"), 0)),
                    List.of(action), List.of(), List.of(), List.of(), recovery);
        }
        @Override public String id() { return id; }
        @Override public int contractVersion() { return 1; }
        @Override public Set<WeaverSubjectKind> supportedKinds() { return Set.of(WeaverSubjectKind.PLAYER); }
        @Override public ProviderContribution contribution() { return contribution; }
        @Override public ProviderCoverage coverage() { return new ProviderCoverage(id + ".domain", level, "Isolated fixture domain", Set.of(id + ".state", id + ".action")); }
        @Override public ProviderDiscovery discover(final SubjectSnapshot snapshot) {
            if (broken) throw new IllegalStateException("private data must not escape through errors");
            return ProviderDiscovery.all(contribution);
        }
        @Override public InspectionResult inspect(final ProviderContext context, final SubjectSnapshot snapshot, final String facet) {
            return new InspectionResult(facet, Map.of("fixture.fact", new WeaverValue(WeaverTypeId.parse("weaver:int@1"), Map.of("value", 7), id, facet, Set.of(), 1)), List.of());
        }
        @Override public PreparedAction prepare(final ProviderContext context, final SubjectSnapshot snapshot, final ActionRequest request) { throw new WeaverDomainRejection("FIXTURE_NO_MUTATION"); }
        @Override public PreparedAction prepareUndo(final ProviderContext context, final SubjectSnapshot snapshot, final WeaverReceipt receipt) { throw new WeaverDomainRejection("FIXTURE_NO_UNDO"); }
        @Override public Optional<WeaverValueCatalog> catalog(final ProviderContext context, final SubjectSnapshot snapshot, final String catalog) { return Optional.empty(); }
        @Override public ValueExportResult exportValue(final ProviderContext context, final SubjectSnapshot snapshot, final String export) { return ValueExportResult.rejected("FIXTURE_NO_EXPORT"); }
        @Override public ImportValidation validateImport(final ProviderContext context, final SubjectSnapshot snapshot, final String importer, final WeaverValue value) { return ImportValidation.rejected("FIXTURE_NO_IMPORT"); }
        @Override public RecoveryAssessment assessRecovery(final ProviderContext context, final SubjectSnapshot snapshot, final WeaverOperationRecord operation) {
            return new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "fixture");
        }
    }
    static ActionDescriptor action(final String provider, final RiskLevel risk, final Set<Lifetime> lifetimes,
                                   final Set<IntegrityMode> modes, final Set<IntegrityImpact> impacts, final boolean undoable, final int cost) {
        return new ActionDescriptor(provider + ".action", provider + ".state", Component.text("fixture action"), risk, lifetimes, modes, impacts,
                Set.of(WeaverSubjectKind.PLAYER), List.of(), AreaSupport.NONE, Optional.empty(), undoable, Optional.empty(), cost);
    }
    static ActionDescriptor safe(final String provider) {
        return action(provider, RiskLevel.SAFE, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.NONE), false, 1);
    }
    static WorldWeaverProviderRegistry registry(final WorldWeaverProvider... providers) {
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        final WorldWeaverProviderRegistry registry = new WorldWeaverProviderRegistry(types, () -> 0L);
        for (final WorldWeaverProvider provider : providers) registry.register(provider);
        return registry;
    }
    public static void main(final String[] args) {
        final FixtureProvider first = new FixtureProvider("first", safe("first"), CoverageLevel.FULL_PROVIDER, Map.of());
        final FixtureProvider added = new FixtureProvider("added", safe("added"), CoverageLevel.FULL_PROVIDER, Map.of());
        final WorldWeaverProviderRegistry registry = registry(first, added); registry.freezeAndValidate();
        final SubjectSnapshot snapshot = new SubjectSnapshot(new PlayerRef(UUID.randomUUID()), 1, "revision", Map.of());
        final var views = WeaverFacetView.discover(registry, registry.discover(snapshot));
        WeaverTypeCompatibilityRegressionSuite.check(views.size() == 2 && views.stream().allMatch(v -> v.actions().size() == 1), "new subsystem facet/action absent from generic view");
        final WeaverTypeRegistry contextTypes = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(contextTypes);
        final ProviderContext context = new ProviderContext(new WeaverAuthorityToken(hu.taliann.icesmp.security.HiddenDevAuthority.PRIMARY_DEVELOPER,
                UUID.randomUUID(), 10_000_000_000L, () -> true, () -> 0L), contextTypes, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX);
        final InspectionResult inspected = registry.invoke("added", context, provider -> provider.inspect(context, snapshot, "added.state"));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> registry.invoke("added", null, provider -> provider.inspect(null, snapshot, "added.state")));
        WeaverTypeCompatibilityRegressionSuite.check(inspected.facts().containsKey("fixture.fact"), "new subsystem inspect absent");
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> registry.register(first));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> registry(first, first));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> registry(new FixtureProvider("blocked", safe("blocked"), CoverageLevel.DEFERRED_BLOCKER, Map.of())).freezeAndValidate());
        rejectAction(action("canon", RiskLevel.CANONICAL, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT), false, 10), Map.of("canon.action", "canon.assess"));
        rejectAction(action("persist", RiskLevel.MUTATING, Set.of(Lifetime.PERSISTENT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT), false, 1), Map.of("persist.action", "persist.assess"));
        rejectAction(action("leak", RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.NONE), true, 1), Map.of("leak.action", "leak.assess"));
        rejectAction(action("journal", RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.TAINT_SUBJECT), true, 1), Map.of());
        rejectAction(action("event", RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.EVENT_ORIGIN), true, 1), Map.of("event.action", "event.assess"));
        first.broken = true;
        for (int i = 0; i < 3; i++) {
            final var discovery = registry.discover(snapshot);
            WeaverTypeCompatibilityRegressionSuite.check(discovery.providers().containsKey("added") && discovery.errors().containsKey("first"), "provider failure escaped isolation");
        }
        WeaverTypeCompatibilityRegressionSuite.check(registry.quarantined("first"), "provider not quarantined after three errors");
        WeaverTypeCompatibilityRegressionSuite.check(registry.discover(snapshot).errors().get("first").equals("PROVIDER_QUARANTINED"), "quarantined provider still callable");
        final AtomicLong clock = new AtomicLong(); final ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(clock::get);
        for (int i = 0; i < 5; i++) try { breaker.call(() -> { throw new WeaverDomainRejection("EXPECTED_REFUSAL"); }, false); } catch (final WeaverDomainRejection expected) { }
        WeaverTypeCompatibilityRegressionSuite.check(!breaker.quarantined() && breaker.recentFailures() == 0, "domain refusal quarantined provider");
        final WeaverRateLimiter rate = new WeaverRateLimiter(clock::get);
        WeaverTypeCompatibilityRegressionSuite.check(rate.tryAcquire(10) && rate.tryAcquire(10) && !rate.tryAcquire(1), "global rate capacity");
        clock.set(500); WeaverTypeCompatibilityRegressionSuite.check(rate.tryAcquire(1) && !rate.tryAcquire(1), "rate refill");
        WeaverTypeCompatibilityRegressionSuite.check(WeaverRateLimiter.areaCost(128, 1) == 9, "AREA cost cap");
        System.out.println("Weaver contract regression suite passed (generic new-provider facet/inspect/action and failure isolation).");
    }
    private static void rejectAction(final ActionDescriptor action, final Map<String, String> recovery) {
        final String id = action.id().substring(0, action.id().indexOf('.'));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> registry(new FixtureProvider(id, action, CoverageLevel.FULL_PROVIDER, recovery)).freezeAndValidate());
    }
}
