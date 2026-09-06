package hu.taliann.icesmp.dev.weaver.execution;

import java.util.Map;

public record OperationRecoveryPayload(int schemaVersion, Map<String, Object> fields) {
    public OperationRecoveryPayload {
        if (schemaVersion < 1 || schemaVersion > 1000) throw new IllegalArgumentException("Invalid recovery schema");
        fields = hu.taliann.icesmp.dev.artifact.ArtifactStateValue.freeze(fields);
        final byte[] canonical = hu.taliann.icesmp.dev.weaver.api.CanonicalValueBytes.encode(fields);
        if (canonical.length > 65536) throw new IllegalArgumentException("Recovery payload exceeds bounds");
        fields = hu.taliann.icesmp.dev.weaver.api.CanonicalValueBytes.decode(canonical);
    }
}
