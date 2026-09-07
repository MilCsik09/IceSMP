package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.persistence.WeaverOperationRecord;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** This authority can compensate one acknowledged stage; it cannot mint interactive actions or override drift. */
public final class WeaverCompensationAuthority {
    private final WeaverOperationRecord operation;
    private final String stage;
    private final String fingerprint;
    private final Supplier<WeaverOperationRecord> current;
    private final BooleanSupplier active;
    private final long deadline = System.nanoTime() + 30_000_000_000L;
    WeaverCompensationAuthority(final WeaverOperationRecord operation, final String stage, final String fingerprint,
                                final Supplier<WeaverOperationRecord> current, final BooleanSupplier active) {
        this.operation = Objects.requireNonNull(operation); this.stage = Objects.requireNonNull(stage); this.fingerprint = Objects.requireNonNull(fingerprint);
        this.current = Objects.requireNonNull(current); this.active = Objects.requireNonNull(active);
    }
    public void require(final String expectedStage, final String observedFingerprint) {
        if (!active.getAsBoolean() || System.nanoTime() - deadline >= 0 || !operation.equals(current.get()) || !stage.equals(expectedStage)) throw new SecurityException("Compensation authority unavailable");
        if (!fingerprint.equals(observedFingerprint)) throw new hu.taliann.icesmp.dev.weaver.api.WeaverDomainRejection("CONFLICT");
    }
}
