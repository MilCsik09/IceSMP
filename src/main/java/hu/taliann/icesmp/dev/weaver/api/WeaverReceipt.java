package hu.taliann.icesmp.dev.weaver.api;

import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;

public record WeaverReceipt(UUID receiptId, UUID operationId, String providerId, String actionId, SubjectRef subject,
                            RiskLevel risk, Lifetime lifetime, IntegrityMode integrityMode, String beforeFingerprint, String afterFingerprint,
                            Map<String, WeaverValue> before, Map<String, WeaverValue> after, Optional<UndoSpec> undo,
                            long createdAt, ReceiptStatus status) {
    public WeaverReceipt {
        java.util.Objects.requireNonNull(receiptId); java.util.Objects.requireNonNull(operationId); WeaverIds.descriptor(providerId); WeaverIds.descriptor(actionId);
        java.util.Objects.requireNonNull(subject); java.util.Objects.requireNonNull(risk); java.util.Objects.requireNonNull(lifetime);
        java.util.Objects.requireNonNull(integrityMode); before = Map.copyOf(before); after = Map.copyOf(after);
        java.util.Objects.requireNonNull(undo); java.util.Objects.requireNonNull(status);
        if (beforeFingerprint == null || beforeFingerprint.isBlank() || beforeFingerprint.length() > 256
                || afterFingerprint == null || afterFingerprint.isBlank() || afterFingerprint.length() > 256 || createdAt < 0
                || before.size() > 128 || after.size() > 128) throw new IllegalArgumentException("Invalid receipt bounds");
    }
}
