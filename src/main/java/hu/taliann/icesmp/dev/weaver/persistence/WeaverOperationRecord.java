package hu.taliann.icesmp.dev.weaver.persistence;

import java.util.UUID;
import java.util.Optional;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import hu.taliann.icesmp.dev.weaver.execution.OperationRecoveryPayload;

public record WeaverOperationRecord(UUID operationId, UUID actorId, String providerId, ActionRequest request,
                                    SubjectRef subject, String beforeFingerprint, Optional<String> afterFingerprint,
                                    OperationRecoveryPayload recoveryPayload, OperationStatus status, long revision,
                                    long preparedAt, long updatedAt, Optional<WeaverReceipt> receipt, boolean pendingAudit) {
    public WeaverOperationRecord {
        java.util.Objects.requireNonNull(operationId); java.util.Objects.requireNonNull(actorId); WeaverIds.descriptor(providerId);
        java.util.Objects.requireNonNull(request); java.util.Objects.requireNonNull(subject); java.util.Objects.requireNonNull(afterFingerprint);
        java.util.Objects.requireNonNull(recoveryPayload); java.util.Objects.requireNonNull(status); java.util.Objects.requireNonNull(receipt);
        if (!hu.taliann.icesmp.security.HiddenDevAuthority.isDeveloper(actorId) || !request.actionId().startsWith(providerId + ".")) {
            throw new IllegalArgumentException("Invalid operation authority or action owner");
        }
        if ((status == OperationStatus.APPLIED || status == OperationStatus.COMMITTED) && (receipt.isEmpty() || afterFingerprint.isEmpty())) {
            throw new IllegalArgumentException("Applied operation requires a receipt and fingerprint");
        }
        if (status == OperationStatus.PREPARED && (receipt.isPresent() || afterFingerprint.isPresent() || pendingAudit)
                || status == OperationStatus.COMMITTED && pendingAudit || status == OperationStatus.APPLIED && !pendingAudit) throw new IllegalArgumentException("Invalid operation state");
        if (receipt.isPresent()) {
            final WeaverReceipt value = receipt.get();
            if (!value.operationId().equals(operationId) || !value.providerId().equals(providerId) || !value.actionId().equals(request.actionId())
                    || !value.subject().equals(subject) || value.lifetime() != request.lifetime() || value.integrityMode() != request.integrityMode()
                    || !value.beforeFingerprint().equals(beforeFingerprint) || !afterFingerprint.orElse("").equals(value.afterFingerprint())) {
                throw new IllegalArgumentException("Operation receipt manifest mismatch");
            }
        }
        if (beforeFingerprint == null || beforeFingerprint.isBlank() || beforeFingerprint.length() > 256
                || afterFingerprint.filter(s -> s.isBlank() || s.length() > 256).isPresent()
                || revision < 0 || preparedAt < 0 || updatedAt < preparedAt) throw new IllegalArgumentException("Invalid durable operation bounds");
    }
}
