package hu.taliann.icesmp.integrity;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Neutral derived-effect admission. No gameplay receipt, canonical state or influence is owned here. */
public final class GameplayEffectGate {
    private static final AtomicReference<Binding> POLICY = new AtomicReference<>();
    private static final Map<RewardSource, UUID> FENCES = new HashMap<>();
    private GameplayEffectGate() { }
    public static final class Binding implements AutoCloseable {
        private final Function<GameplayEffectContext, CompletionStage<GameplayEffectPermit>> policy;
        private Binding(Function<GameplayEffectContext, CompletionStage<GameplayEffectPermit>> policy) { this.policy = Objects.requireNonNull(policy); }
        @Override public void close() { POLICY.compareAndSet(this, null); }
    }
    /** Held until durable acknowledgement, including policy replacement; never expires during an in-flight write. */
    public static final class ObservationFence implements AutoCloseable {
        private final Binding binding;
        private final RewardSource target;
        private final UUID id;
        private ObservationFence(Binding binding, RewardSource target, UUID id) { this.binding = binding; this.target = target; this.id = id; }
        public boolean activeFor(RewardSource source) {
            synchronized (FENCES) { return POLICY.get() == binding && target.equals(normalize(source)) && id.equals(FENCES.get(target)); }
        }
        @Override public void close() { synchronized (FENCES) { FENCES.remove(target, id); } }
    }
    /** Caller must own the native target and observe it only after acquiring this fence. No blocking wait occurs. */
    public static Optional<ObservationFence> fence(RewardSource source) {
        final var binding = POLICY.get(); if (binding == null) return Optional.empty();
        final RewardSource target = normalize(Objects.requireNonNull(source));
        synchronized (FENCES) {
            if (POLICY.get() != binding || FENCES.size() >= 128 || FENCES.containsKey(target)) return Optional.empty();
            final UUID id = UUID.randomUUID(); FENCES.put(target, id); return Optional.of(new ObservationFence(binding, target, id));
        }
    }
    private static RewardSource normalize(RewardSource source) {
        if (source instanceof RewardSource.Player player) return new RewardSource.Entity(player.id());
        if (source instanceof RewardSource.Location point) return new RewardSource.Location(point.world(), Math.floor(point.x()), Math.floor(point.y()), Math.floor(point.z()));
        return source;
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
                return GameplayEffectPermit.guardedSources(currentSources -> {
                    synchronized (FENCES) {
                        return POLICY.get() == binding && context.targets().stream().noneMatch(target -> FENCES.containsKey(normalize(target)))
                                && permit.claim(currentSources) && POLICY.get() == binding;
                    }
                });
            });
        } catch (RuntimeException | LinkageError unavailable) { return CompletableFuture.completedFuture(GameplayEffectPermit.denied()); }
    }
}
