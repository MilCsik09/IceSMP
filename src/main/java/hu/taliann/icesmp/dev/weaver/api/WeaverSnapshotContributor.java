package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import java.util.Map;

/** Optional adapter hook, invoked on SubjectRoute.owner(ref), before pure discovery. */
public interface WeaverSnapshotContributor {
    Map<String, WeaverValue> captureOnOwner(SubjectRef ref);
}
