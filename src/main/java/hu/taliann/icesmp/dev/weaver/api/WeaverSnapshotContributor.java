package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import java.util.Map;

/** Optional adapter hook, invoked on SubjectRoute.owner(ref), before pure discovery. */
public interface WeaverSnapshotContributor {
    Map<String, WeaverValue> captureOnOwner(SubjectRef ref);

    /** Exact journal-operation observation; implementations must neither issue an action nor replay effects. */
    default Map<String, WeaverValue> captureRecoveryOnOwner(RecoveryContext context) {
        context.authority().require(context.operation());
        return captureOnOwner(context.operation().subject());
    }
}
