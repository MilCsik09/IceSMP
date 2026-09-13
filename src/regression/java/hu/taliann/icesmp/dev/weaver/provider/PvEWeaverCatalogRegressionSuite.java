package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.gui.WeaverFacetView;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.pve.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class PvEWeaverCatalogRegressionSuite {
    private static MobAbilityDefinition ability(final String id) {
        return new MobAbilityDefinition(id, MobAbilityDefinition.Kind.LUNGE, 40, 10, 4, 2, 0, Map.of());
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
    public static void main(final String[] args) {
        final Map<String, MobAbilityDefinition> initial = new HashMap<>();
        for (int i = 0; i < 64; i++) initial.put("fixture_" + i, ability("fixture_" + i));
        final var abilities = new AtomicReference<>(Map.copyOf(initial)); final var ownerReads = new AtomicInteger();
        final var types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        final var rank = PvEWeaverProvider.reference(PvEWeaverProvider.RANK, "elite", "pve.rank", 1);
        final var provider = new PvEWeaverProvider(types, abilities::get, Map::of, ref -> { ownerReads.incrementAndGet(); return Map.of("pve.rank", rank); });
        final var registry = new WorldWeaverProviderRegistry(types, () -> 0L); registry.register(provider); registry.freezeAndValidate();
        final var ref = new EntityRef(UUID.randomUUID()); final var snapshot = new SubjectSnapshot(ref, 1, "fixture", registry.captureContributions(ref));
        final var context = WeaverProviderTestContext.sandbox(types);
        final var facets = WeaverFacetView.discover(registry, registry.discover(snapshot));
        check(facets.size() == 1 && facets.getFirst().catalogs().size() == 4 && facets.getFirst().exports().size() == 1, "generic facet view lost the registered PvE surface");
        check(provider.inspect(context, snapshot, PvEWeaverProvider.FACET).facts().get("pve.rank").equals(rank), "immutable inspect did not use canonical snapshot");
        final var catalog = provider.catalog(context, snapshot, "pve.abilities").orElseThrow();
        final var page = catalog.page(new CatalogQuery("", 45, 45)); check(catalog.page(new CatalogQuery("", 0, 45)).entries().size() == 45 && page.entries().size() == 19 && !page.hasNext(), "initial registry publication differs");
        final var added = new HashMap<>(abilities.get()); added.put("fixture_new_charge", ability("fixture_new_charge")); abilities.set(Map.copyOf(added));
        final var fresh = catalog.page(new CatalogQuery("fixture_new", 0, 45));
        check(fresh.entries().size() == 1 && fresh.entries().getFirst().stableId().equals("fixture_new_charge"), "new registry content required rebuilding provider/core/catalog");
        check(catalog.page(new CatalogQuery("", 45, 45)).entries().size() == 20 && page.entries().size() == 19, "publication changed an old catalog page or lost pagination");
        final WeaverValue value = registry.resolveCatalog(context, snapshot, "pve.abilities", "fixture_new_charge");
        check(types.compatible(value, PvEWeaverProvider.ABILITY, Set.of("pve.ability")) && !types.compatible(value, PvEWeaverProvider.RANK, Set.of("pve.rank")), "registry value lost type/capability separation");
        final var exportSnapshot = new SubjectSnapshot(ref, 2, "new_kit", Map.of("pve.rank", rank, "pve.ability", value));
        final WeaverValue exported = provider.exportValue(context, exportSnapshot, "pve.export_ability").value().orElseThrow();
        final var threads = new WeaverThreadCase(); threads.add(exported);
        check(threads.active().orElseThrow().value().equals(value), "new registry ability could not be exported into the typed Thread Case");
        final var stale = new HashMap<>(abilities.get()); stale.remove("fixture_new_charge"); abilities.set(Map.copyOf(stale));
        check(catalog.resolve("fixture_new_charge").isEmpty() && !types.compatible(exported, PvEWeaverProvider.ABILITY, Set.of("pve.ability")), "removed canonical content remained actionable through stale Thread");
        check(provider.exportValue(context, exportSnapshot, "pve.export_ability").value().isEmpty(), "export admitted a no-longer-canonical ability");
        check(ownerReads.get() == 1, "discovery/catalog/inspect/export performed a live owner read");
        check(provider.discover(new SubjectSnapshot(ref, 1, "not_a_mob", Map.of())).facets().isEmpty(), "non-mob was exposed as a PvE runtime subject");
        System.out.println("PvE Weaver catalogs passed: canonical-port 64-to-65 publication after registry freeze, generic facet discovery, owner snapshot isolation, typed export/Thread storage and stale-content rejection. Gameplay import/projection evidence remains required.");
    }
}
