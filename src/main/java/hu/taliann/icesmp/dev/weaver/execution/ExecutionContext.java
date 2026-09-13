package hu.taliann.icesmp.dev.weaver.execution;

import java.util.List;
import hu.taliann.icesmp.dev.weaver.WeaverAuthorityToken;
import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;

public record ExecutionContext(WeaverAuthorityToken authority, SubjectSnapshot snapshot, List<StageResult> previousResults,
                               java.util.Optional<WeaverNativeEffectAuthority> nativeEffects) {
    public ExecutionContext(WeaverAuthorityToken authority, SubjectSnapshot snapshot, List<StageResult> previousResults) {
        this(authority, snapshot, previousResults, java.util.Optional.empty());
    }
    public ExecutionContext {
        java.util.Objects.requireNonNull(authority); java.util.Objects.requireNonNull(snapshot);
        previousResults = List.copyOf(previousResults);
        java.util.Objects.requireNonNull(nativeEffects);
        if (previousResults.size() > (snapshot.ref() instanceof hu.taliann.icesmp.dev.weaver.subject.AreaRef ? 4096 : 8)) throw new IllegalArgumentException("Stage lineage exceeds plan cap");
        authority.requireValid();
    }
}
