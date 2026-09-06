package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.persistence.WeaverOperationRecord;
import java.util.Objects;
import java.util.function.Supplier;

/** This operation-bound token cannot authorize an interactive provider call or a new mutation. */
public final class WeaverRecoveryAuthority {
    private final WeaverOperationRecord operation;
    private final Supplier<WeaverOperationRecord> current;
    private final long expiresAt;
    WeaverRecoveryAuthority(final WeaverOperationRecord operation, final Supplier<WeaverOperationRecord> current) {
        this.operation = Objects.requireNonNull(operation); this.current = Objects.requireNonNull(current);
        expiresAt = System.nanoTime() + 30_000_000_000L;
    }
    public void require(final WeaverOperationRecord expected) {
        if (!operation.equals(expected) || !operation.equals(current.get()) || System.nanoTime() >= expiresAt) throw new SecurityException("Stale operation recovery authority");
    }
}
