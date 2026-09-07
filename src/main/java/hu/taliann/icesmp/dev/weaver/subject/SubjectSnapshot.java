package hu.taliann.icesmp.dev.weaver.subject;

import java.util.Map;
import hu.taliann.icesmp.dev.weaver.api.WeaverValue;
import hu.taliann.icesmp.dev.weaver.api.WeaverIds;

public record SubjectSnapshot(SubjectRef ref, long capturedAt, String revisionFingerprint,
                              Map<String, WeaverValue> facts) {
    public SubjectSnapshot {
        java.util.Objects.requireNonNull(ref);
        if (capturedAt < 0 || revisionFingerprint == null || revisionFingerprint.isBlank() || revisionFingerprint.length() > 256) {
            throw new IllegalArgumentException("Invalid subject snapshot revision");
        }
        facts = Map.copyOf(facts);
        if (facts.size() > 256) throw new IllegalArgumentException("Subject snapshot exceeds fact cap");
        facts.keySet().forEach(WeaverIds::descriptor);
    }
}
