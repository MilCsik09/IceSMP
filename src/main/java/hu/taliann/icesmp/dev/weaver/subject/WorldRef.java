package hu.taliann.icesmp.dev.weaver.subject;

import java.util.UUID;

public record WorldRef(UUID worldId) implements SubjectRef {
    public WorldRef { java.util.Objects.requireNonNull(worldId); }
    @Override public WeaverSubjectKind kind() { return WeaverSubjectKind.WORLD; }
}
