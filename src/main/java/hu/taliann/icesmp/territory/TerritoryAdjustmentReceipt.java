package hu.taliann.icesmp.territory;

import java.util.Objects;
import java.util.UUID;

/** Real canonical operation acknowledgement persisted atomically with the territory state. */
public record TerritoryAdjustmentReceipt(UUID operationId, String territoryId,
                                         String beforeFingerprint, String afterFingerprint, String adjustmentFingerprint,
                                         long committedAt) {
    public TerritoryAdjustmentReceipt {
        Objects.requireNonNull(operationId); Objects.requireNonNull(territoryId);
        if (territoryId.isBlank() || territoryId.length() > 256 || committedAt < 0
                || beforeFingerprint == null || !beforeFingerprint.matches("[0-9a-f]{64}")
                || afterFingerprint == null || !afterFingerprint.matches("[0-9a-f]{64}")
                || beforeFingerprint.equals(afterFingerprint)
                || adjustmentFingerprint == null || !adjustmentFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid territory adjustment receipt");
        }
    }
}
