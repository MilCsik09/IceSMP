package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.pve.MobAbilityDefinition;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class WeaverDynamicCatalogRegressionSuite {
    public static void main(final String[] args) {
        final AtomicReference<Map<String, MobAbilityDefinition>> canonicalFixture = new AtomicReference<>(Map.of());
        final WeaverTypeId abilityType = WeaverTypeId.parse("icesmp:pve_ability_ref@1");
        final WeaverTypeRegistry types = new WeaverTypeRegistry();
        types.register(ScalarTypeCodec.reference(abilityType, id -> canonicalFixture.get().containsKey(id)));
        final RegistryValueCatalog<MobAbilityDefinition> catalog = new RegistryValueCatalog<>(abilityType, "pve", "pve.abilities",
                Set.of("pve.ability.copy"), canonicalFixture::get, ability -> Component.text(ability.abilityId()), () -> 123L);
        types.freeze();
        WeaverTypeCompatibilityRegressionSuite.check(catalog.page(new CatalogQuery("", 0, 45)).entries().isEmpty(), "empty initial registry");
        final Map<String, MobAbilityDefinition> content = new LinkedHashMap<>();
        for (int i = 63; i >= 0; i--) {
            final String id = "fixture_ability_" + String.format(Locale.ROOT, "%03d", i);
            content.put(id, new MobAbilityDefinition(id, MobAbilityDefinition.Kind.LUNGE, 40, 10, 3, 1, 0, Map.of()));
        }
        canonicalFixture.set(Map.copyOf(content));
        final CatalogPage first = catalog.page(new CatalogQuery("", 0, 45));
        WeaverTypeCompatibilityRegressionSuite.check(first.entries().size() == 45 && first.hasNext(), "bounded dynamic catalog page");
        WeaverTypeCompatibilityRegressionSuite.check(first.entries().getFirst().stableId().equals("fixture_ability_000"), "registry insertion order leaked into catalog");
        WeaverTypeCompatibilityRegressionSuite.check(catalog.page(new CatalogQuery("", 45, 45)).entries().size() == 19, "second catalog page");
        final String added = "new_registry_ability";
        content.put(added, new MobAbilityDefinition(added, MobAbilityDefinition.Kind.GROUND_SLAM, 60, 20, 4, 2, 0, Map.of()));
        canonicalFixture.set(Map.copyOf(content));
        final WeaverValue exported = catalog.resolve(added).orElseThrow();
        types.validate(exported);
        final ImportDescriptor importer = new ImportDescriptor("other.import_ability", "other.apply_ability", abilityType,
                Set.of("pve.ability.copy"), "ability");
        WeaverTypeCompatibilityRegressionSuite.check(types.compatible(exported, importer.acceptedType(), importer.requiredCapabilities()), "new content did not export/import without frontend changes");
        WeaverTypeCompatibilityRegressionSuite.check(catalog.page(new CatalogQuery("new_registry", 0, 45)).entries().getFirst().stableId().equals(added), "new content did not appear");
        content.remove(added); canonicalFixture.set(Map.copyOf(content));
        WeaverTypeCompatibilityRegressionSuite.check(catalog.resolve(added).isEmpty() && !types.require(abilityType).validate(exported.payload()).valid(), "removed registry content remained actionable");
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new CatalogQuery("", 0, 46));
        System.out.println("Weaver dynamic catalog regression suite passed (canonical ability-definition fixture publication).");
    }
}
