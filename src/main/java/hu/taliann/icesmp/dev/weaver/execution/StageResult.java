package hu.taliann.icesmp.dev.weaver.execution;

import java.util.Map;
import hu.taliann.icesmp.dev.weaver.api.WeaverValue;

public record StageResult(String afterFingerprint, Map<String, WeaverValue> facts, Map<String, Object> compensationState) {
    public StageResult {
        if (afterFingerprint == null || afterFingerprint.isBlank() || afterFingerprint.length() > 256) throw new IllegalArgumentException("Missing stage fingerprint");
        facts = Map.copyOf(facts); compensationState = hu.taliann.icesmp.dev.artifact.ArtifactStateValue.freeze(compensationState);
        if (facts.size() > 128) throw new IllegalArgumentException("Stage result exceeds fact cap");
    }
}
