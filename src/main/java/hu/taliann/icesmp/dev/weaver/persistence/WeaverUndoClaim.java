package hu.taliann.icesmp.dev.weaver.persistence;

import java.util.Objects;
import java.util.UUID;

/** Reserves one committed receipt revision; the normal operation remains the sole mutation authority. */
public record WeaverUndoClaim(UUID receiptId, long operationRevision, String expectedFingerprint) {
    public WeaverUndoClaim {
        Objects.requireNonNull(receiptId);
        if (operationRevision < 0 || expectedFingerprint == null || expectedFingerprint.isBlank() || expectedFingerprint.length() > 256) throw new IllegalArgumentException("Invalid conditional Undo claim");
    }
}
