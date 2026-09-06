package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.UUID;

public record DeveloperInfluence(UUID operationId, IntegrityMode mode, String actionId, UUID actorId, long appliedAt) {
    public DeveloperInfluence {
        java.util.Objects.requireNonNull(operationId); java.util.Objects.requireNonNull(mode); WeaverIds.descriptor(actionId);
        if (!HiddenDevAuthority.isDeveloper(actorId) || appliedAt < 0) throw new IllegalArgumentException("Invalid developer influence origin");
    }
    public boolean quarantinesRewards() { return mode == IntegrityMode.SANDBOX; }
    public DeveloperInfluence retainSandboxOrigin(final DeveloperInfluence later) {
        java.util.Objects.requireNonNull(later);
        return quarantinesRewards() ? this : later;
    }
}
