package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.CoverageLevel;
import hu.taliann.icesmp.dev.weaver.api.ProviderCoverage;
import hu.taliann.icesmp.dev.weaver.api.WeaverIds;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The audited build supplies expected domains; only explicit provider evidence can close each one. */
public final class WorldWeaverCoverageRegistry {
    private final Set<String> expectedDomains;
    public WorldWeaverCoverageRegistry(final Set<String> expectedDomains) {
        this.expectedDomains = Set.copyOf(expectedDomains); expectedDomains.forEach(WeaverIds::descriptor);
        if (expectedDomains.isEmpty() || expectedDomains.size() > 256) throw new IllegalArgumentException("Invalid audited coverage denominator");
    }
    public Map<String, ProviderCoverage.Domain> requireComplete(final Collection<ProviderCoverage> contributions) {
        final Map<String, ProviderCoverage.Domain> result = new TreeMap<>();
        for (final ProviderCoverage provider : contributions) {
            for (final var entry : provider.domains().entrySet()) {
                final ProviderCoverage.Domain coverage = entry.getValue();
                if (result.putIfAbsent(entry.getKey(), coverage) != null) throw new IllegalArgumentException("Duplicate coverage claim");
                if (coverage.level() == CoverageLevel.DEFERRED_BLOCKER) throw new IllegalStateException("Deferred coverage blocker");
                if (coverage.level() == CoverageLevel.FULL_PROVIDER && coverage.surfaces().isEmpty()) throw new IllegalStateException("Full provider lacks capability evidence");
            }
        }
        if (!result.keySet().equals(expectedDomains)) throw new IllegalStateException("Audited domain coverage mismatch");
        return Map.copyOf(result);
    }
}
