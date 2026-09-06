package hu.taliann.icesmp.dev.weaver.api;

import java.util.List;
import java.util.Map;

public record ProviderContribution(List<FacetDescriptor> facets, List<ActionDescriptor> actions,
                                   List<CatalogDescriptor> catalogs, List<ExportDescriptor> exports,
                                   List<ImportDescriptor> imports, Map<String, String> recoveryCapabilitiesByAction) {
    public ProviderContribution {
        facets = List.copyOf(facets); actions = List.copyOf(actions); catalogs = List.copyOf(catalogs);
        exports = List.copyOf(exports); imports = List.copyOf(imports); recoveryCapabilitiesByAction = Map.copyOf(recoveryCapabilitiesByAction);
        if (facets.size() > 64 || actions.size() > 256 || catalogs.size() > 128 || exports.size() > 128 || imports.size() > 128
                || recoveryCapabilitiesByAction.size() > 256) throw new IllegalArgumentException("Provider contribution exceeds caps");
        recoveryCapabilitiesByAction.forEach((action, capability) -> { WeaverIds.descriptor(action); WeaverIds.descriptor(capability); });
    }
}
