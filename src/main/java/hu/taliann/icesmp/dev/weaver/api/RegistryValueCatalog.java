package hu.taliann.icesmp.dev.weaver.api;

import net.kyori.adventure.text.Component;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Each request reads the canonical registry's immutable publication; no copied content list lives here. */
public final class RegistryValueCatalog<T> implements WeaverValueCatalog {
    private final WeaverTypeId type;
    private final String provider;
    private final String facet;
    private final Set<String> capabilities;
    private final Supplier<Map<String, T>> registry;
    private final Function<T, Component> label;
    private final LongSupplier clock;
    public RegistryValueCatalog(final WeaverTypeId type, final String provider, final String facet,
            final Set<String> capabilities, final Supplier<Map<String, T>> registry,
            final Function<T, Component> label, final LongSupplier clock) {
        this.type = Objects.requireNonNull(type); this.provider = WeaverIds.descriptor(provider); this.facet = WeaverIds.descriptor(facet);
        this.capabilities = Set.copyOf(capabilities); this.registry = Objects.requireNonNull(registry);
        this.label = Objects.requireNonNull(label); this.clock = Objects.requireNonNull(clock);
    }
    @Override public WeaverTypeId type() { return type; }
    private WeaverValue value(final String id, final long captured) {
        return new WeaverValue(type, Map.of("id", id), provider, facet, capabilities, captured);
    }
    @Override public CatalogPage page(final CatalogQuery query) {
        final Map<String, T> snapshot = Map.copyOf(registry.get());
        final var matches = snapshot.keySet().stream().filter(id -> id.contains(query.search())).sorted().toList();
        final long captured = clock.getAsLong();
        final var entries = matches.stream().skip(query.offset()).limit(query.limit())
                .map(id -> new CatalogEntry(id, label.apply(snapshot.get(id)), value(id, captured))).toList();
        return new CatalogPage(entries, query.offset(), (long) query.offset() + entries.size() < matches.size());
    }
    @Override public Optional<WeaverValue> resolve(final String stableId) {
        WeaverIds.content(stableId);
        return registry.get().containsKey(stableId) ? Optional.of(value(stableId, clock.getAsLong())) : Optional.empty();
    }
}
