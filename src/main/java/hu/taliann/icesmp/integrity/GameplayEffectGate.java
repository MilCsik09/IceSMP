package hu.taliann.icesmp.integrity;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Neutral derived-effect admission. No gameplay receipt, canonical state or influence is owned here. */
public final class GameplayEffectGate {
    private static final AtomicReference<Binding> POLICY = new AtomicReference<>();
    private GameplayEffectGate() { }
    public static final class Binding implements AutoCloseable {
        private final Function<GameplayEffectContext, CompletionStage<GameplayEffectPermit>> policy;
        private Binding(Function<GameplayEffectContext, CompletionStage<GameplayEffectPermit>> policy) { this.policy = Objects.requireNonNull(policy); }
        @Override public void close() { POLICY.compareAndSet(this, null); }
    }
    public static Binding install(Function<GameplayEffectContext, CompletionStage<GameplayEffectPermit>> policy) {
        final var binding = new Binding(policy);
        if (!POLICY.compareAndSet(null, binding)) throw new IllegalStateException("Effect eligibility already installed");
        return binding;
    }
    public static CompletionStage<GameplayEffectPermit> prepare(GameplayEffectContext context) {
        Objects.requireNonNull(context);
        final var binding = POLICY.get();
        if (binding == null) return CompletableFuture.completedFuture(GameplayEffectPermit.denied());
        try {
            return Objects.requireNonNull(binding.policy.apply(context)).handle((permit, failure) -> {
                if (failure != null || permit == null) return GameplayEffectPermit.denied();
                return GameplayEffectPermit.guarded(() -> POLICY.get() == binding && permit.claim() && POLICY.get() == binding);
            });
        } catch (RuntimeException | LinkageError unavailable) { return CompletableFuture.completedFuture(GameplayEffectPermit.denied()); }
    }
}
