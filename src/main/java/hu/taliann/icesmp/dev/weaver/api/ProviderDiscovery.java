package hu.taliann.icesmp.dev.weaver.api;

import java.util.Set;
import java.util.Map;

public record ProviderDiscovery(Set<String> facets, Set<String> actions, Set<String> catalogs,
                                Set<String> exports, Set<String> imports, Map<String, String> blockedActions) {
    public ProviderDiscovery {
        facets = Set.copyOf(facets); actions = Set.copyOf(actions); catalogs = Set.copyOf(catalogs);
        exports = Set.copyOf(exports); imports = Set.copyOf(imports); blockedActions = Map.copyOf(blockedActions);
        if (facets.size() > 64 || actions.size() > 256 || catalogs.size() > 128 || exports.size() > 128 || imports.size() > 128
                || blockedActions.size() > 256 || blockedActions.values().stream().anyMatch(s -> s.isBlank() || s.length() > 256)) {
            throw new IllegalArgumentException("Provider discovery exceeds manifest bounds");
        }
    }
    public static ProviderDiscovery all(final ProviderContribution contribution) {
        return new ProviderDiscovery(ids(contribution.facets(), FacetDescriptor::id), ids(contribution.actions(), ActionDescriptor::id),
                ids(contribution.catalogs(), CatalogDescriptor::id), ids(contribution.exports(), ExportDescriptor::id),
                ids(contribution.imports(), ImportDescriptor::id), Map.of());
    }
    private static <T> Set<String> ids(final java.util.List<T> items, final java.util.function.Function<T, String> id) {
        return items.stream().map(id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
