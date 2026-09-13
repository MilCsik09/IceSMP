package hu.taliann.icesmp.dev.weaver.subject;

import java.util.UUID;

public record PlayerRef(UUID playerId) implements SubjectRef {
    public PlayerRef { java.util.Objects.requireNonNull(playerId); }
    @Override public WeaverSubjectKind kind() { return WeaverSubjectKind.PLAYER; }
}
