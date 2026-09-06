package hu.taliann.icesmp.dev.weaver.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProjectionReferenceTypeCodec implements WeaverTypeCodec {
    @Override public WeaverTypeId type() { return new WeaverTypeId("weaver", "projection_ref", 1); }
    @Override public ValidationResult validate(final Map<String, Object> payload) {
        try {
            if (!payload.keySet().equals(Set.of("id", "provider"))
                    || !(payload.get("id") instanceof String id) || !UUID.fromString(id).toString().equals(id)
                    || !(payload.get("provider") instanceof String provider) || !WeaverIds.descriptor(provider).equals(provider)) {
                return ValidationResult.rejected("INVALID_PROJECTION_REFERENCE");
            }
            return ValidationResult.accepted();
        } catch (final RuntimeException invalid) { return ValidationResult.rejected("INVALID_PROJECTION_REFERENCE"); }
    }
    @Override public byte[] canonicalBytes(final Map<String, Object> payload) { validate(payload).requireValid(); return CanonicalValueBytes.encode(payload); }
}
