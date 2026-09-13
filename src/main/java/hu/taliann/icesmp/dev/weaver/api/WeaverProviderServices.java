package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.dev.weaver.WorldWeaverProviderRegistry;
import hu.taliann.icesmp.dev.weaver.execution.WeaverOwnerRouter;
import hu.taliann.icesmp.dev.weaver.projection.WeaverProjectionSource;
import java.util.Objects;
import java.util.function.Supplier;

/** Composition-time read and scheduling ports. Providers receive no journal writer or authority issuer. */
public final class WeaverProviderServices {
    private final WeaverTypeRegistry types;
    private final WeaverProjectionSource projections;
    private final WeaverOwnerRouter owners;
    private final WorldWeaverProviderRegistry registry;
    public WeaverProviderServices(final WeaverTypeRegistry types, final WeaverProjectionSource projections,
            final WeaverOwnerRouter owners, final WorldWeaverProviderRegistry registry) {
        this.types = Objects.requireNonNull(types); this.projections = Objects.requireNonNull(projections);
        this.owners = Objects.requireNonNull(owners); this.registry = Objects.requireNonNull(registry);
    }
    public WeaverTypeRegistry types() { return types; }
    public WeaverProjectionSource projections() { return projections; }
    public WeaverOwnerRouter owners() { return owners; }
    public <T> T readConsumer(final String providerId, final Supplier<T> read) { return registry.readConsumer(providerId, read); }
}
