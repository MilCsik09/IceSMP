package hu.taliann.icesmp.dev.weaver.subject;

import java.util.UUID;

public record EntityRef(UUID entityId) implements SubjectRef {
    public EntityRef { java.util.Objects.requireNonNull(entityId); }
    @Override public WeaverSubjectKind kind() { return WeaverSubjectKind.ENTITY; }
}
