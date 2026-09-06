package hu.taliann.icesmp.dev.weaver.execution;

import java.util.Map;
import java.util.Optional;

public record ExecutionStage(String id, ExecutionOwner owner, Map<String, Object> payload,
                             StageOperation apply, Optional<StageCompensation> compensate, long timeoutMillis) {
    public ExecutionStage {
        hu.taliann.icesmp.dev.weaver.api.WeaverIds.descriptor(id); java.util.Objects.requireNonNull(owner);
        payload = hu.taliann.icesmp.dev.artifact.ArtifactStateValue.freeze(payload);
        java.util.Objects.requireNonNull(apply); java.util.Objects.requireNonNull(compensate);
        final long limit = owner instanceof AsyncIoOwner || owner instanceof ProfileOwner ? 10000 : 5000;
        if (timeoutMillis < 1 || timeoutMillis > limit) throw new IllegalArgumentException("Execution timeout exceeds owner limit");
    }
}
