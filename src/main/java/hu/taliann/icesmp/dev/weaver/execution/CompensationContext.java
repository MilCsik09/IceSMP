package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;
import java.util.Objects;

public record CompensationContext(WeaverCompensationAuthority authority, String stageId, SubjectSnapshot before, StageResult applied) {
    public CompensationContext { Objects.requireNonNull(authority); Objects.requireNonNull(stageId); Objects.requireNonNull(before); Objects.requireNonNull(applied); }
    public void requireCurrentFingerprint(final String observed) { authority.require(stageId, observed); }
}
