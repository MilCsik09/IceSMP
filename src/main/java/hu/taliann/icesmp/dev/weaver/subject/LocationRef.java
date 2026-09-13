package hu.taliann.icesmp.dev.weaver.subject;

import java.util.UUID;

public record LocationRef(UUID worldId, double x, double y, double z, float yaw, float pitch) implements SubjectRef {
    public LocationRef {
        java.util.Objects.requireNonNull(worldId);
        Coordinates.position(x, y, z);
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) throw new IllegalArgumentException("Invalid view angles");
    }
    @Override public WeaverSubjectKind kind() { return WeaverSubjectKind.LOCATION; }
}
