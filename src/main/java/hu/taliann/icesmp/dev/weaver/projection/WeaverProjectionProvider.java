package hu.taliann.icesmp.dev.weaver.projection;

import java.util.List;

/** Optional adapter contribution, validated against the executable action manifest. */
public interface WeaverProjectionProvider {
    List<ProjectionConsumerDescriptor> projectionConsumers();
}
