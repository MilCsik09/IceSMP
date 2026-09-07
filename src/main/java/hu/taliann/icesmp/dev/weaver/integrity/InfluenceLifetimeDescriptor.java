package hu.taliann.icesmp.dev.weaver.integrity;

import hu.taliann.icesmp.dev.weaver.api.*;
import java.util.*;

/** Typed provider-owned observation of a derived effect's actual lifetime. */
public record InfluenceLifetimeDescriptor(String providerId, String facetId, WeaverTypeId type, Set<InfluenceScope> scopes) {
    public InfluenceLifetimeDescriptor {
        WeaverIds.descriptor(providerId); WeaverIds.descriptor(facetId); Objects.requireNonNull(type); scopes = Set.copyOf(scopes);
        if (scopes.isEmpty() || !Set.of(InfluenceScope.PLAYER, InfluenceScope.SPATIAL, InfluenceScope.WORLD).containsAll(scopes))
            throw new IllegalArgumentException("Observed lifetime scope");
    }
}
