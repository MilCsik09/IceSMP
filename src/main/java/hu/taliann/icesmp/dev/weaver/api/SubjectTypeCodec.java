package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.Map;
import java.util.Set;

public final class SubjectTypeCodec implements WeaverTypeCodec {
    private final String kind;
    public SubjectTypeCodec(final String kind) {
        if (!Set.of("subject_ref", "location", "area").contains(kind)) throw new IllegalArgumentException("Unknown builtin subject type");
        this.kind = kind;
    }
    @Override public WeaverTypeId type() { return new WeaverTypeId("weaver", kind, 1); }
    @Override public ValidationResult validate(final Map<String, Object> payload) {
        try {
            final SubjectRef ref = SubjectKeyCodec.decodePayload(payload);
            if (kind.equals("location") && !(ref instanceof LocationRef) || kind.equals("area") && !(ref instanceof AreaRef)) {
                return ValidationResult.rejected("SUBJECT_KIND_MISMATCH");
            }
            return ValidationResult.accepted();
        } catch (final RuntimeException invalid) { return ValidationResult.rejected("INVALID_SUBJECT_REFERENCE"); }
    }
    @Override public byte[] canonicalBytes(final Map<String, Object> payload) { validate(payload).requireValid(); return CanonicalValueBytes.encode(payload); }
    @Override public Set<ActionParameter.InputKind> supportedInputs() {
        return Set.of(kind.equals("area") ? ActionParameter.InputKind.AREA : ActionParameter.InputKind.SUBJECT, ActionParameter.InputKind.THREAD);
    }
}
