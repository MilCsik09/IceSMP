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
                if (action == null || action.lifetimes().equals(Set.of(Lifetime.ONE_SHOT)) || !action.requiresJournal()
                        || action.lifetimes().contains(Lifetime.PERSISTENT) && !action.undoable()
                        || Collections.disjoint(consumer.subjects(), targetKinds(action))) throw new IllegalArgumentException("Projection action lacks a compatible durable manifest");
            }
        }
        this.actions = Map.copyOf(actions); frozen = true;
    }
    public synchronized ProjectionConsumerDescriptor require(final String id) {
        if (!frozen) throw new IllegalStateException("Projection consumers not validated");
        final ProjectionConsumerDescriptor descriptor = consumers.get(id);
        if (descriptor == null) throw new WeaverDomainRejection("PROJECTION_CONSUMER_UNAVAILABLE"); return descriptor;
    }
    private static Set<hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind> targetKinds(final ActionDescriptor action) {
        final var result = new HashSet<>(action.subjects());
        if (result.contains(hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind.AREA)) {
            if (action.areaSupport() == AreaSupport.ENTITY_FANOUT) {
                result.remove(hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind.AREA);
                result.add(hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind.ENTITY);
                result.add(hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind.PLAYER);
            } else if (action.areaSupport() == AreaSupport.BLOCK_FANOUT) {
                result.remove(hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind.AREA);
                result.add(hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind.BLOCK);
            }
        }
        return Set.copyOf(result);
    }
    public void validate(final WeaverProjection projection) {
        final List<ProjectionConsumerDescriptor> matching;
        synchronized (this) {
            if (!frozen) throw new IllegalStateException("Projection consumers not validated");
            final ActionDescriptor action = actions.get(projection.actionId());
            if (action == null || !action.lifetimes().contains(projection.lifetime()) || !action.integrityModes().contains(projection.influence().mode())
                    || !targetKinds(action).contains(projection.subject().kind())) throw new WeaverDomainRejection("PROJECTION_MANIFEST_MISMATCH");
            if (!action.undoable() && (projection.lifetime() != Lifetime.SESSION || projection.expiresAt().isEmpty()
                    || projection.expiresAt().getAsLong() - projection.createdAt() > 120_000)) throw new WeaverDomainRejection("BOUNDED_PROJECTION_EXPIRY_REQUIRED");
            matching = consumers.values().stream().filter(consumer -> consumer.providerId().equals(projection.providerId())
                    && consumer.actions().contains(projection.actionId()) && consumer.subjects().contains(projection.subject().kind())).toList();
        }
        for (final var entry : projection.values().entrySet()) {
            try { types.validate(entry.getValue()); }
            catch (final IllegalArgumentException unavailable) { throw new WeaverDomainRejection("PROJECTION_CONTENT_UNAVAILABLE"); }
            if (matching.stream().noneMatch(consumer -> entry.getValue().type().equals(consumer.fields().get(entry.getKey())))) throw new WeaverDomainRejection("PROJECTION_WITHOUT_CONSUMER");
        }
    }
    public synchronized List<ProjectionConsumerDescriptor> snapshot() { return List.copyOf(consumers.values()); }
}
