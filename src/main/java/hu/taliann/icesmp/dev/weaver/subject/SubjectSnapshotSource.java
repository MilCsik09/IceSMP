package hu.taliann.icesmp.dev.weaver.subject;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SubjectSnapshotSource {
    CompletionStage<SubjectSnapshot> capture(UUID actor, SubjectRef subject);
    default CompletionStage<SubjectSnapshot> captureRecovery(hu.taliann.icesmp.dev.weaver.api.RecoveryContext context) {
        context.authority().require(context.operation());
        return capture(context.operation().actorId(), context.operation().subject());
    }
}
