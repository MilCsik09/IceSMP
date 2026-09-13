package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.gui.WeaverFacetView;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.territory.TerritoryProtectionPolicy;
import hu.taliann.icesmp.territory.TerritoryProtectionPolicy.*;
import hu.taliann.icesmp.dev.weaver.provider.TerritoryWeaverProjectionRegressionSuite.Fixture;
import java.util.*;
import java.util.concurrent.atomic.*;
import static hu.taliann.icesmp.dev.weaver.provider.TerritoryWeaverProvider.*;

/** Native pure policy decisions flow into typed snapshots and the unchanged generic Facet model. */
public final class TerritorySubjectInspectionRegressionSuite {
    private static int assertions;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
    public static void main(String[] args) throws Exception {
        try (final var fixture = new Fixture()) {
            final var types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
            final var registry = new WorldWeaverProviderRegistry(types, System::currentTimeMillis);
            final var owner = new AtomicBoolean(); final var reads = new AtomicInteger();
            final var location = new LocationRef(fixture.world.worldId(), 2, 4, 2, 0, 0);
            final var provider = new TerritoryWeaverProvider(types, fixture.manager, fixture.source, world -> {
                throw new AssertionError("local inspection must not call the global world capture");
            }, (source, ref) -> {
                if (!owner.get()) throw new WeaverDomainRejection("OWNER_UNAVAILABLE"); reads.incrementAndGet();
                return TerritorySubjectInspection.facts(location, Optional.of(fixture.manager.getById("first")), decisions(ref instanceof PlayerRef, true), ref instanceof PlayerRef, "fixture-revision", 100);
            });
            registry.register(provider); registry.freezeAndValidate(); final var context = WeaverProviderTestContext.sandbox(types);
            for (final var ref : List.<SubjectRef>of(new BlockRef(fixture.world.worldId(), 2, 4, 2), location, new PlayerRef(UUID.randomUUID()), new EntityRef(UUID.randomUUID()))) {
                owner.set(true); final var facts = provider.captureOnOwner(ref); owner.set(false);
                facts.values().forEach(types::validate); final int captured = reads.get();
                final var snapshot = new SubjectSnapshot(ref, 100, "local-snapshot", facts);
                final var view = WeaverFacetView.discover(registry, registry.discover(snapshot)).getFirst();
                check(view.facet().id().equals(FACET) && view.actions().isEmpty(), "generic local facet is inspect-only; world actions do not leak to local subjects");
                final var inspection = provider.inspect(context, snapshot, FACET);
                check(!inspection.facts().isEmpty() && reads.get() == captured, "discovery/inspection never recapture live state");
                check(SubjectKeyCodec.decodePayload(facts.get("territory.location").payload()).equals(location), "location is a typed stable value");
                for (final var rule : Rule.values()) {
                    final String prefix = "territory." + rule.name().toLowerCase(Locale.ROOT);
                    final var expected = decisions(ref instanceof PlayerRef, true).get(rule);
                    check(facts.get(prefix + ".reason").payload().get("value").equals(expected.reason().name()), "decision reason comes from native evaluator");
                    check(((String) facts.get(prefix + ".trace").payload().get("value")).endsWith(expected.reason().name() + "=true"), "ordered trace preserves native terminal decision");
                }
                try { provider.captureOnOwner(ref); throw new AssertionError("foreign owner accepted"); } catch (WeaverDomainRejection expected) { assertions++; }
            }
            final var mutable = new EnumMap<>(decisions(false, false));
            final var wilderness = TerritorySubjectInspection.facts(location, Optional.empty(), mutable, false, "none", 100); mutable.clear();
            check(wilderness.get("territory.zone_revision").payload().get("value").equals("wilderness") && !wilderness.containsKey("territory.id"), "wilderness does not retain previous zone identity");
            check(wilderness.get("territory.fire.trace").payload().get("value") instanceof String, "snapshot detaches mutable decision collection");
            try { TerritorySubjectInspection.facts(location, Optional.empty(), Map.of(), false, "none", 100); throw new AssertionError("incomplete trace accepted"); }
            catch (WeaverDomainRejection expected) { assertions++; }
        }
        System.out.println("Territory subject inspection passed. assertions=" + assertions);
    }
    private static Map<Rule, Decision> decisions(boolean actor, boolean zone) {
        final Map<Rule, Decision> result = new EnumMap<>(Rule.class);
        for (final var rule : Rule.values()) result.put(rule, TerritoryProtectionPolicy.evaluate(
                new Facts(rule, zone, zone, zone, false, actor, true, false, false, false, false, false, false), Overlay.INHERIT, true));
        return Map.copyOf(result);
    }
}
