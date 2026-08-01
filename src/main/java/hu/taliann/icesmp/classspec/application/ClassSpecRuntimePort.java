package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.ClassProfile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Scheduler-owning integration port. Implementations diff the two immutable
 * profiles and, on the correct player/entity scheduler, revoke only grants with
 * the deactivated {@code SPEC:<id>} provenance, remove that loadout's pet/minion
 * runtime, and clear forms/transient spell state. Quest, talent, base and admin
 * grants must remain untouched.
 */
public interface ClassSpecRuntimePort {

    CompletionStage<Void> profileCommitted(
            UUID playerId,
            ClassProfile previous,
            ClassProfile durable,
            MutationKind kind);

    CompletionStage<Void> failClosed(UUID playerId, String reason);

    /** Pure side-effect policy: mirror-only commits must not despawn companions or reset casts. */
    static boolean requiresRuntimeReconciliation(final MutationKind kind) {
        return switch (java.util.Objects.requireNonNull(kind, "kind")) {
            case CLASS_LEVEL_MIRROR, SOULFORGE_UPGRADE -> false;
            default -> true;
        };
    }

    static ClassSpecRuntimePort noop() {
        return new ClassSpecRuntimePort() {
            @Override
            public CompletionStage<Void> profileCommitted(final UUID playerId,
                                                           final ClassProfile previous,
                                                           final ClassProfile durable,
                                                           final MutationKind kind) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> failClosed(final UUID playerId, final String reason) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    enum MutationKind {
        SELECT,
        RESPEC_RESET,
        ADMIN_RESET,
        EXPLICIT_SEAL,
        GATE_RECONCILE,
        CLASS_ASSIGN,
        CLASS_LEVEL_MIRROR,
        SOULFORGE_UPGRADE
    }
}
