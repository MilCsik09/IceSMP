package hu.taliann.icesmp.dev.artifact;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DevArtifactState(UUID owner, UUID instanceId, boolean issued, long revision,
                                Map<String, Object> behaviorState) {
    public DevArtifactState {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(instanceId, "instanceId");
        if (revision < 0) throw new IllegalArgumentException("Negative artifact revision");
        behaviorState = ArtifactStateValue.freeze(behaviorState);
    }

    public DevArtifactState next(final UUID newOwner, final UUID newInstance, final boolean newIssued,
                                  final Map<String, Object> behavior) {
        return new DevArtifactState(newOwner, newInstance, newIssued, Math.incrementExact(revision), behavior);
    }

    public boolean matches(final UUID actor, final UUID markedOwner, final UUID markedInstance) {
        return issued && owner.equals(actor) && owner.equals(markedOwner) && instanceId.equals(markedInstance);
    }
}
