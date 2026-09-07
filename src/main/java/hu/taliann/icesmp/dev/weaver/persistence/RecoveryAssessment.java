package hu.taliann.icesmp.dev.weaver.persistence;

import java.util.Optional;
import hu.taliann.icesmp.dev.weaver.api.WeaverReceipt;

public record RecoveryAssessment(ObservedOperationState observed, boolean exactCompensationObserved,
                                 Optional<WeaverReceipt> receipt, String reason, Optional<WeaverEffectCommit> effects) {
    public RecoveryAssessment(final ObservedOperationState observed, final boolean exactCompensationObserved,
                              final Optional<WeaverReceipt> receipt, final String reason) {
        this(observed, exactCompensationObserved, receipt, reason, Optional.empty());
    }
    public RecoveryAssessment {
        java.util.Objects.requireNonNull(observed); java.util.Objects.requireNonNull(receipt); java.util.Objects.requireNonNull(effects);
        if (reason == null || reason.length() > 256 || (exactCompensationObserved && observed != ObservedOperationState.BEFORE)
                || effects.isPresent() && (observed != ObservedOperationState.APPLIED || receipt.isEmpty())) {
            throw new IllegalArgumentException("Invalid observed recovery assessment");
        }
    }
}
