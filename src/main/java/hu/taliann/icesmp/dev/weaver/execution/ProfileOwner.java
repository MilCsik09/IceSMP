package hu.taliann.icesmp.dev.weaver.execution;

import java.util.UUID;

public record ProfileOwner(UUID playerId) implements ExecutionOwner {
    public ProfileOwner { java.util.Objects.requireNonNull(playerId); }
}
