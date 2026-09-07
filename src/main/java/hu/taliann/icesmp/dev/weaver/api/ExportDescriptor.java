package hu.taliann.icesmp.dev.weaver.api;

import java.util.Set;

public record ExportDescriptor(String id, String facetId, WeaverTypeId outputType, Set<String> capabilities) {
    public ExportDescriptor {
        WeaverIds.descriptor(id); WeaverIds.descriptor(facetId); java.util.Objects.requireNonNull(outputType);
        capabilities = Set.copyOf(capabilities); capabilities.forEach(WeaverIds::descriptor);
        if (capabilities.size() > 32) throw new IllegalArgumentException("Export capability cap");
    }
}
