package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import java.util.List;
import java.util.Set;
import java.util.Map;

public final class WorldWeaverCoverageRegressionSuite {
    public static void main(final String[] args) {
        final WorldWeaverCoverageRegistry coverage = new WorldWeaverCoverageRegistry(Set.of("first.domain", "second.domain"));
        final ProviderCoverage combined = new ProviderCoverage(Map.of(
                "first.domain", new ProviderCoverage.Domain(CoverageLevel.FULL_PROVIDER, "Canonical fixture surface", Set.of("first.inspect", "first.mutate")),
                "second.domain", new ProviderCoverage.Domain(CoverageLevel.NO_RUNTIME_SURFACE, "Future subsystem is absent from this fixture build", Set.of())));
        WeaverTypeCompatibilityRegressionSuite.check(coverage.requireComplete(List.of(combined)).size() == 2, "combined adapter coverage rejected");
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> coverage.requireComplete(List.of()));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> coverage.requireComplete(List.of(combined, combined)));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> coverage.requireComplete(List.of(new ProviderCoverage("first.domain", CoverageLevel.DEFERRED_BLOCKER, "Missing canonical surface", Set.of()))));
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> coverage.requireComplete(List.of(new ProviderCoverage("first.domain", CoverageLevel.FULL_PROVIDER, "Unsupported claim", Set.of()))));
        System.out.println("WorldWeaver coverage regression suite passed; current repository blockers remain independently audited.");
    }
}
