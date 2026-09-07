package hu.taliann.icesmp.dev.weaver.api;

import net.kyori.adventure.text.Component;

public record CatalogEntry(String stableId, Component label, WeaverValue value) {
    public CatalogEntry { WeaverIds.content(stableId); java.util.Objects.requireNonNull(label); java.util.Objects.requireNonNull(value); }
}
