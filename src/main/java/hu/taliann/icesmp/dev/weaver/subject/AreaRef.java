package hu.taliann.icesmp.dev.weaver.subject;

import java.util.UUID;

public record AreaRef(UUID worldId, AreaShape shape) implements SubjectRef {
    public AreaRef {
        java.util.Objects.requireNonNull(worldId); java.util.Objects.requireNonNull(shape);
        if (shape.bounds().chunkCount() > 9) throw new IllegalArgumentException("AREA exceeds 9 chunks");
    }
    @Override public WeaverSubjectKind kind() { return WeaverSubjectKind.AREA; }
}
