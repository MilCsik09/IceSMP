package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Scheduler-owning, generation-fenced runtime integration. */
public interface ClassSpecRuntimePort {
    CompletionStage<Void> profileCommitted(UUID playerId,UUID sessionToken,ClassSpecSection previous,ClassSpecSection durable,MutationKind kind);
    CompletionStage<Void> failClosed(UUID playerId,UUID sessionToken,String reason);
    static boolean requiresRuntimeReconciliation(MutationKind kind){return switch(Objects.requireNonNull(kind)){case CLASS_EXPERIENCE,SOULFORGE_UPGRADE,SOUL_SHARD_MUTATION,COMPANION_MUTATION,COMPANION_PROGRESS->false;default->true;};}
    static ClassSpecRuntimePort noop(){return new ClassSpecRuntimePort(){public CompletionStage<Void> profileCommitted(UUID p,UUID t,ClassSpecSection a,ClassSpecSection b,MutationKind k){return CompletableFuture.completedFuture(null);}public CompletionStage<Void> failClosed(UUID p,UUID t,String r){return CompletableFuture.completedFuture(null);}};}
    enum MutationKind{SELECT,RESPEC_RESET,ADMIN_RESET,EXPLICIT_SEAL,GATE_RECONCILE,CLASS_ASSIGN,CLASS_EXPERIENCE,SOULFORGE_UPGRADE,SOUL_SHARD_MUTATION,COMPANION_MUTATION,COMPANION_PROGRESS,RECOVERY}
}
