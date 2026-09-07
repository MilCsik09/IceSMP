package hu.taliann.icesmp.integrity;

import java.util.*;

/** Detached causal inputs and already identified targets for a bounded native effect. */
public record GameplayEffectContext(List<RewardSource> sources, Set<RewardSource> targets, long durationMillis, Optional<GameplayEffectLifetime> lifetime) {
    public GameplayEffectContext(List<RewardSource> sources, Set<RewardSource> targets, long durationMillis) { this(sources, targets, durationMillis, Optional.empty()); }
    public GameplayEffectContext {
        sources = List.copyOf(sources); targets = Set.copyOf(targets);
        Objects.requireNonNull(lifetime);
        if (sources.isEmpty() || sources.size() > 64 || targets.isEmpty() || targets.size() > 32
                || durationMillis < 0 || durationMillis > 86_400_000L) throw new IllegalArgumentException("Native effect bounds");
    }
}
