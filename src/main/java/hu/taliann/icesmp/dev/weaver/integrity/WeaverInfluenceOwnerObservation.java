package hu.taliann.icesmp.dev.weaver.integrity;

import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** A started owner timeout discards ordinary task results, but must release a later observation's fence. */
public final class WeaverInfluenceOwnerObservation {
    private WeaverInfluenceOwnerObservation() { }
    public static CompletionStage<InfluenceObservation> submit(final WeaverOwnerRouter router, final UUID target,
            final Supplier<InfluenceObservation> observeOwned) {
        Objects.requireNonNull(router); Objects.requireNonNull(target); Objects.requireNonNull(observeOwned);
        final var delivered = new CompletableFuture<InfluenceObservation>();
        try {
            router.submit(new EntityOwner(target), HiddenDevAuthority.PRIMARY_DEVELOPER, Duration.ofSeconds(5), () -> {
                final var observation = Objects.requireNonNull(observeOwned.get());
                if (!delivered.complete(observation)) observation.close();
                return CompletableFuture.<Void>completedFuture(null);
            }).whenComplete((ignored, failure) -> { if (failure != null) delivered.completeExceptionally(failure); });
        } catch (RuntimeException | LinkageError failure) { delivered.completeExceptionally(failure); }
        return delivered.minimalCompletionStage();
    }
}
