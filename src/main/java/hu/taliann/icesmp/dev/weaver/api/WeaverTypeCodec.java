package hu.taliann.icesmp.dev.weaver.api;

import java.util.Map;

public interface WeaverTypeCodec {
    WeaverTypeId type();
    ValidationResult validate(Map<String, Object> payload);
    byte[] canonicalBytes(Map<String, Object> payload);
    default java.util.Set<ActionParameter.InputKind> supportedInputs() {
        return java.util.Set.of(ActionParameter.InputKind.CATALOG, ActionParameter.InputKind.THREAD);
    }
}
