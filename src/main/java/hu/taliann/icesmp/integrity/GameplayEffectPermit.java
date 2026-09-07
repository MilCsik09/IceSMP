package hu.taliann.icesmp.integrity;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Single-use final admission, with freshly owner-captured sources immediately before native mutation. */
public final class GameplayEffectPermit {
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final Predicate<List<RewardSource>> admission;
    private GameplayEffectPermit(Predicate<List<RewardSource>> admission) { this.admission = Objects.requireNonNull(admission); }
    public static GameplayEffectPermit guarded(BooleanSupplier admission) { Objects.requireNonNull(admission); return new GameplayEffectPermit(ignored -> admission.getAsBoolean()); }
    public static GameplayEffectPermit guardedSources(Predicate<List<RewardSource>> admission) { return new GameplayEffectPermit(admission); }
    public static GameplayEffectPermit denied() { return new GameplayEffectPermit(ignored -> false); }
    public boolean claim() { return claim(List.of()); }
    public boolean claim(List<RewardSource> currentSources) {
        if (!claimed.compareAndSet(false, true)) return false;
        try {
            final var snapshot = List.copyOf(currentSources);
            return snapshot.size() <= 64 && admission.test(snapshot);
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }
}
