package hu.taliann.icesmp.classspec.domain;

import java.util.Objects;

/** Persisted seal cause plus the exact gate/recovery discriminator that may clear it. */
public record SealReason(SealCause cause, String gateId, String detail) {

    public SealReason {
        Objects.requireNonNull(cause, "cause");
        gateId = clean(gateId);
        detail = clean(detail);
        if (cause.gateRestorable() && gateId.isEmpty()) {
            throw new IllegalArgumentException("A restorable seal requires a gate id");
        }
    }

    private static String clean(final String value) {
        return value == null ? "" : value.trim();
    }
}
