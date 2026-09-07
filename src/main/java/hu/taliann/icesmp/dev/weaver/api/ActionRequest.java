package hu.taliann.icesmp.dev.weaver.api;

import java.util.Map;

public record ActionRequest(String actionId, Map<String, WeaverValue> parameters, Lifetime lifetime, IntegrityMode integrityMode) {
    public ActionRequest {
        WeaverIds.descriptor(actionId); parameters = Map.copyOf(parameters); parameters.keySet().forEach(WeaverIds::parameter);
        java.util.Objects.requireNonNull(lifetime); java.util.Objects.requireNonNull(integrityMode);
        if (parameters.size() > 16) throw new IllegalArgumentException("Action parameter cap");
    }
}
