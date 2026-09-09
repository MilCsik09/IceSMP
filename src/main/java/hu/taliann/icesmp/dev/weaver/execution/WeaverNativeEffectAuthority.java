package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.WeaverAuthorityToken;
import hu.taliann.icesmp.dev.weaver.api.WeaverDomainRejection;
import hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceTarget;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.integrity.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Issued only by the durable executor for a running, acknowledged PREPARED stage. */
public final class WeaverNativeEffectAuthority {
    private final WeaverJournal journal;
    private final WeaverOperationRecord operation;
    private final WeaverAuthorityToken actor;
    private final BooleanSupplier active;
    private final Set<WeaverInfluenceTarget> targets;
    private final long issued = System.nanoTime();

    WeaverNativeEffectAuthority(WeaverJournal journal, WeaverOperationRecord operation,
            WeaverAuthorityToken actor, BooleanSupplier active) {
        this.journal = Objects.requireNonNull(journal); this.operation = Objects.requireNonNull(operation);
        this.actor = Objects.requireNonNull(actor); this.active = Objects.requireNonNull(active);
        final var intent = journal.snapshot().intents().get(operation.operationId());
        targets = intent == null ? Set.of() : Set.copyOf(intent.targets());
        requireValid();
    }

    public void requireValid() {
        actor.requireValid();
        if (!active.getAsBoolean() || !journal.ready() || System.nanoTime() - issued >= TimeUnit.SECONDS.toNanos(5)
                || !actor.actor().equals(operation.actorId()) || operation.status() != OperationStatus.PREPARED
                || !operation.equals(journal.snapshot().operations().get(operation.operationId()))) {
            throw new WeaverDomainRejection("NATIVE_OPERATION_AUTHORITY_UNAVAILABLE");
        }
    }

    /** A data-only operation UUID can never request this exclusion. Every other pending intent still refuses effects. */
    public UUID pendingOperation() { requireValid(); return operation.operationId(); }

    public void requireFor(WeaverJournal expected, GameplayEffectContext context) {
        requireValid(); Objects.requireNonNull(context);
        if (journal != expected || context.durationMillis() != 0 || context.lifetime().isPresent()
                || context.targets().stream().anyMatch(target -> {
                    final var exact = WeaverInfluenceTarget.exact(target);
                    return !exact.monotonic() || !targets.contains(exact);
                })) throw new WeaverDomainRejection("NATIVE_OPERATION_TARGET_OUTSIDE_INTENT");
    }

    public CompletionStage<GameplayEffectPermit> prepare(GameplayEffectContext context) {
        try {
            requireFor(journal, context);
            return GameplayEffectGate.guardNativeAdmission(context, () -> journal.prepareNativeEffect(this, context));
        } catch (RuntimeException | LinkageError unavailable) {
            return CompletableFuture.completedFuture(GameplayEffectPermit.denied());
        }
    }
}
