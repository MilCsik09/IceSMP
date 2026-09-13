package hu.taliann.icesmp.dev.weaver.api;

import java.util.Map;

public record UndoSpec(String actionId, String expectedCurrentFingerprint, Map<String, WeaverValue> parameters) {
    public UndoSpec {
        WeaverIds.descriptor(actionId); parameters = Map.copyOf(parameters);
        if (expectedCurrentFingerprint == null || expectedCurrentFingerprint.isBlank() || expectedCurrentFingerprint.length() > 256
                || parameters.size() > 16) throw new IllegalArgumentException("Invalid conditional undo specification");
    }
}
