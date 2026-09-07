package hu.taliann.icesmp.dev.weaver.subject;

import java.util.UUID;

public record BlockRef(UUID worldId, int x, int y, int z) implements SubjectRef {
    public BlockRef { java.util.Objects.requireNonNull(worldId); Coordinates.block(x, y, z); }
    @Override public WeaverSubjectKind kind() { return WeaverSubjectKind.BLOCK; }
}
