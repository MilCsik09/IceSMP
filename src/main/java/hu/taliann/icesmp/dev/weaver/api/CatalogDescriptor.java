package hu.taliann.icesmp.dev.weaver.api;

import net.kyori.adventure.text.Component;

public record CatalogDescriptor(String id, String facetId, Component label, WeaverTypeId type) {
    public CatalogDescriptor { WeaverIds.descriptor(id); WeaverIds.descriptor(facetId); java.util.Objects.requireNonNull(label); java.util.Objects.requireNonNull(type); }
}
