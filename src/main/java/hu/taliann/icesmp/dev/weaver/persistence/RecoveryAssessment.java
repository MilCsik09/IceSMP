package hu.taliann.icesmp.dev.weaver.persistence;

import java.util.Optional;
import hu.taliann.icesmp.dev.weaver.api.WeaverReceipt;

public record RecoveryAssessment(ObservedOperationState observed, boolean exactCompensationObserved,
                                 Optional<WeaverReceipt> receipt, String reason) {
    public RecoveryAssessment {
        java.util.Objects.requireNonNull(observed); java.util.Objects.requireNonNull(receipt);
        if (reason == null || reason.length() > 256 || (exactCompensationObserved && observed != ObservedOperationState.BEFORE)) {
            throw new IllegalArgumentException("Invalid observed recovery assessment");
        }
    }
}
