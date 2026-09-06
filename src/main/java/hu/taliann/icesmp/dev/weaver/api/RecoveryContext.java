package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryAuthority;
import hu.taliann.icesmp.dev.weaver.persistence.WeaverOperationRecord;

public record RecoveryContext(WeaverRecoveryAuthority authority, WeaverTypeRegistry types, WeaverOperationRecord operation) {
    public RecoveryContext {
        java.util.Objects.requireNonNull(authority); java.util.Objects.requireNonNull(types); java.util.Objects.requireNonNull(operation);
        authority.require(operation);
    }
}
