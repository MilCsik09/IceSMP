package hu.taliann.icesmp.dev.weaver.execution;

import java.util.List;
import hu.taliann.icesmp.dev.weaver.WeaverAuthorityToken;
import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;

public record ExecutionContext(WeaverAuthorityToken authority, SubjectSnapshot snapshot, List<StageResult> previousResults) {
    public ExecutionContext {
        java.util.Objects.requireNonNull(authority); java.util.Objects.requireNonNull(snapshot);
        previousResults = List.copyOf(previousResults);
        if (previousResults.size() > 8) throw new IllegalArgumentException("Stage lineage exceeds plan cap");
        authority.requireValid();
    }
}
