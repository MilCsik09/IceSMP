package hu.taliann.icesmp.dev.weaver.api;

import java.util.Map;
import java.util.Set;

public record WeaverValue(WeaverTypeId type, Map<String, Object> payload, String sourceProvider,
                          String sourceFacet, Set<String> sourceCapabilities, long capturedAt) {
    public WeaverValue {
        java.util.Objects.requireNonNull(type);
        payload = hu.taliann.icesmp.dev.artifact.ArtifactStateValue.freeze(payload);
        WeaverIds.descriptor(sourceProvider);
        WeaverIds.descriptor(sourceFacet);
        sourceCapabilities = Set.copyOf(sourceCapabilities);
        if (sourceCapabilities.size() > 32 || capturedAt < 0) throw new IllegalArgumentException("Invalid value metadata");
        sourceCapabilities.forEach(WeaverIds::descriptor);
        final byte[] canonical = CanonicalValueBytes.encode(payload);
        if (canonical.length > 32768) throw new IllegalArgumentException("Weaver value exceeds 32 KiB");
        payload = CanonicalValueBytes.decode(canonical);
    }
}
