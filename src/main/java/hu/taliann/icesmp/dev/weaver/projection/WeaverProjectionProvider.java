package hu.taliann.icesmp.dev.weaver.projection;

import java.util.List;

/** Optional adapter contribution, validated against the executable action manifest. */
public interface WeaverProjectionProvider {
    List<ProjectionConsumerDescriptor> projectionConsumers();
    /** Reconcile already durable effective state; never author another projection or canonical mutation here. */
    default java.util.concurrent.CompletionStage<Void> reconcileProjections(final java.util.Set<hu.taliann.icesmp.dev.weaver.subject.SubjectRef> subjects) {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
}
