package hu.taliann.icesmp.dev.weaver.api;

import java.util.Map;
import java.util.Set;

/** One subsystem adapter may cover several explicitly audited gameplay domains. */
public record ProviderCoverage(Map<String, Domain> domains) {
    public record Domain(CoverageLevel level, String rationale, Set<String> surfaces) {
        public Domain {
            java.util.Objects.requireNonNull(level); surfaces = Set.copyOf(surfaces);
            if (rationale == null || rationale.isBlank() || rationale.length() > 2048 || surfaces.size() > 256) {
                throw new IllegalArgumentException("Coverage rationale/bounds required");
            }
            surfaces.forEach(WeaverIds::descriptor);
        }
    }
    public ProviderCoverage {
        domains = Map.copyOf(domains); domains.keySet().forEach(WeaverIds::descriptor);
        if (domains.isEmpty() || domains.size() > 256) throw new IllegalArgumentException("Invalid provider coverage domains");
    }
    public ProviderCoverage(final String domain, final CoverageLevel level, final String rationale, final Set<String> surfaces) {
        this(Map.of(domain, new Domain(level, rationale, surfaces)));
    }
    public CoverageLevel level() {
        for (final CoverageLevel level : new CoverageLevel[]{CoverageLevel.DEFERRED_BLOCKER, CoverageLevel.FULL_PROVIDER, CoverageLevel.INSPECT_ONLY_BY_DESIGN}) {
            if (domains.values().stream().anyMatch(domain -> domain.level() == level)) return level;
        }
        return CoverageLevel.NO_RUNTIME_SURFACE;
    }
}
