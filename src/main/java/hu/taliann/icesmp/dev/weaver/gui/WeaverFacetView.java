package hu.taliann.icesmp.dev.weaver.gui;

import hu.taliann.icesmp.dev.weaver.WorldWeaverProviderRegistry;
import hu.taliann.icesmp.dev.weaver.api.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Shared view data is derived exclusively from registry descriptors and pure provider discovery. */
public record WeaverFacetView(String providerId, FacetDescriptor facet, List<ActionDescriptor> actions,
                              List<CatalogDescriptor> catalogs, List<ExportDescriptor> exports,
                              List<ImportDescriptor> imports, Map<String, String> blockedActions) {
    public WeaverFacetView {
        actions = List.copyOf(actions); catalogs = List.copyOf(catalogs); exports = List.copyOf(exports);
        imports = List.copyOf(imports); blockedActions = Map.copyOf(blockedActions);
    }
    public static List<WeaverFacetView> discover(final WorldWeaverProviderRegistry registry,
                                               final WorldWeaverProviderRegistry.Discovery discovery) {
        final java.util.ArrayList<WeaverFacetView> views = new java.util.ArrayList<>();
        discovery.providers().forEach((provider, contribution) -> {
            for (final String facet : contribution.facets()) {
                final List<ActionDescriptor> actions = contribution.actions().stream().map(registry.actions()::get)
                        .filter(action -> action.facetId().equals(facet)).sorted(Comparator.comparing(ActionDescriptor::id)).toList();
                views.add(new WeaverFacetView(provider, registry.facets().get(facet), actions,
                        contribution.catalogs().stream().map(registry.catalogs()::get).filter(c -> c.facetId().equals(facet)).sorted(Comparator.comparing(CatalogDescriptor::id)).toList(),
                        contribution.exports().stream().map(registry.exports()::get).filter(e -> e.facetId().equals(facet)).sorted(Comparator.comparing(ExportDescriptor::id)).toList(),
                        contribution.imports().stream().map(registry.imports()::get).filter(i -> actions.stream().anyMatch(a -> a.id().equals(i.actionId())))
                                .sorted(Comparator.comparing(ImportDescriptor::id)).toList(), contribution.blockedActions()));
            }
        });
        return views.stream().sorted(Comparator.comparingInt((WeaverFacetView view) -> view.facet().order()).thenComparing(view -> view.facet().id())).toList();
    }
}
