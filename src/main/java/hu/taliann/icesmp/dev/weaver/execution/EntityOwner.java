package hu.taliann.icesmp.dev.weaver.execution;

import java.util.UUID;

public record EntityOwner(UUID entityId) implements ExecutionOwner {
    public EntityOwner { java.util.Objects.requireNonNull(entityId); }
}
