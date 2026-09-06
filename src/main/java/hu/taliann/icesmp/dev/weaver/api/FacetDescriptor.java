package hu.taliann.icesmp.dev.weaver.api;

import net.kyori.adventure.text.Component;

public record FacetDescriptor(String id, Component label, Component description, int order) {
    public FacetDescriptor { WeaverIds.descriptor(id); java.util.Objects.requireNonNull(label); java.util.Objects.requireNonNull(description); }
}
