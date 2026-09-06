package hu.taliann.icesmp.dev.weaver.projection;

import hu.taliann.icesmp.dev.weaver.api.*;
import java.util.*;

public final class ProjectionConsumerRegistry {
    private final Map<String, ProjectionConsumerDescriptor> consumers = new LinkedHashMap<>();
    private final WeaverTypeRegistry types;
    private boolean frozen;
    private Map<String, ActionDescriptor> actions = Map.of();
    public ProjectionConsumerRegistry(final WeaverTypeRegistry types) { this.types = Objects.requireNonNull(types); }
    public synchronized void register(final ProjectionConsumerDescriptor consumer) {
        if (frozen || consumers.size() >= 256 || consumers.putIfAbsent(consumer.id(), consumer) != null) throw new IllegalArgumentException("Projection consumer registration rejected");
    }
    public synchronized void freeze(final Map<String, ActionDescriptor> actions) {
        for (final ProjectionConsumerDescriptor consumer : consumers.values()) {
            consumer.fields().values().forEach(types::require);
            for (final String id : consumer.actions()) {
                final ActionDescriptor action = actions.get(id);
                if (action == null || action.lifetimes().equals(Set.of(Lifetime.ONE_SHOT)) || !action.undoable()
                        || !consumer.subjects().containsAll(action.subjects())) throw new IllegalArgumentException("Projection action lacks a compatible reversible manifest");
            }
        }
        this.actions = Map.copyOf(actions); frozen = true;
    }
    public synchronized ProjectionConsumerDescriptor require(final String id) {
        if (!frozen) throw new IllegalStateException("Projection consumers not validated");
        final ProjectionConsumerDescriptor descriptor = consumers.get(id);
        if (descriptor == null) throw new WeaverDomainRejection("PROJECTION_CONSUMER_UNAVAILABLE"); return descriptor;
    }
    public void validate(final WeaverProjection projection) {
        final List<ProjectionConsumerDescriptor> matching;
        synchronized (this) {
            if (!frozen) throw new IllegalStateException("Projection consumers not validated");
            final ActionDescriptor action = actions.get(projection.actionId());
            if (action == null || !action.lifetimes().contains(projection.lifetime()) || !action.integrityModes().contains(projection.influence().mode())) throw new WeaverDomainRejection("PROJECTION_MANIFEST_MISMATCH");
            matching = consumers.values().stream().filter(consumer -> consumer.providerId().equals(projection.providerId())
                    && consumer.actions().contains(projection.actionId()) && consumer.subjects().contains(projection.subject().kind())).toList();
        }
        for (final var entry : projection.values().entrySet()) {
            types.validate(entry.getValue());
            if (matching.stream().noneMatch(consumer -> entry.getValue().type().equals(consumer.fields().get(entry.getKey())))) throw new WeaverDomainRejection("PROJECTION_WITHOUT_CONSUMER");
        }
    }
    public synchronized List<ProjectionConsumerDescriptor> snapshot() { return List.copyOf(consumers.values()); }
}
