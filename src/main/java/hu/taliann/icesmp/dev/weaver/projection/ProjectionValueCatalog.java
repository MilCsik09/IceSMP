package hu.taliann.icesmp.dev.weaver.projection;

import hu.taliann.icesmp.dev.weaver.api.*;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.function.Supplier;

/** Bounded provider-owned projection references; every query reads an immutable journal publication. */
public final class ProjectionValueCatalog implements WeaverValueCatalog {
    public static final WeaverTypeId TYPE = WeaverTypeId.parse("weaver:projection_ref@1");
    private final String provider, facet, capability;
    private final Supplier<List<WeaverProjection>> projections;
    public ProjectionValueCatalog(String provider, String facet, String capability, Supplier<List<WeaverProjection>> projections) {
        this.provider = WeaverIds.descriptor(provider); this.facet = WeaverIds.descriptor(facet);
        this.capability = WeaverIds.descriptor(capability); this.projections = Objects.requireNonNull(projections);
    }
    @Override public WeaverTypeId type() { return TYPE; }
    private List<WeaverProjection> current() {
        final var values = List.copyOf(projections.get());
        if (values.size() > 32 || values.stream().anyMatch(p -> !provider.equals(p.providerId()))
                || values.stream().map(WeaverProjection::projectionId).distinct().count() != values.size())
            throw new IllegalArgumentException("Projection catalog source violates provider scope or bounds");
        return values.stream().sorted(Comparator.comparingLong(WeaverProjection::sequence)).toList();
    }
    private WeaverValue value(WeaverProjection projection) {
        return new WeaverValue(TYPE, Map.of("id", projection.projectionId().toString(), "provider", provider),
                provider, facet, Set.of(capability), System.currentTimeMillis());
    }
    @Override public CatalogPage page(CatalogQuery query) {
        final var matches = current().stream().filter(p -> (p.actionId() + " " + p.lifetime() + " " + p.projectionId())
                .toLowerCase(Locale.ROOT).contains(query.search())).toList();
        final var entries = matches.stream().skip(query.offset()).limit(query.limit()).map(p -> new CatalogEntry(p.projectionId().toString(),
                Component.text(p.actionId() + " · " + p.lifetime() + " · " + p.projectionId()), value(p))).toList();
        return new CatalogPage(entries, query.offset(), (long) query.offset() + entries.size() < matches.size());
    }
    @Override public Optional<WeaverValue> resolve(String stableId) {
        WeaverIds.content(stableId);
        return current().stream().filter(p -> p.projectionId().toString().equals(stableId)).findFirst().map(this::value);
    }
}
