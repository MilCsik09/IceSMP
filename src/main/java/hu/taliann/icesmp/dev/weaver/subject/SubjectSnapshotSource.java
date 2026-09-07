package hu.taliann.icesmp.dev.weaver.subject;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SubjectSnapshotSource {
    CompletionStage<SubjectSnapshot> capture(UUID actor, SubjectRef subject);
}
