package hu.taliann.icesmp.integrity;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Single-use final admission, invoked on the native target owner immediately before its mutation. */
public final class GameplayEffectPermit {
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final BooleanSupplier admission;
    private GameplayEffectPermit(BooleanSupplier admission) { this.admission = Objects.requireNonNull(admission); }
    public static GameplayEffectPermit guarded(BooleanSupplier admission) { return new GameplayEffectPermit(admission); }
    public static GameplayEffectPermit denied() { return new GameplayEffectPermit(() -> false); }
    public boolean claim() {
        if (!claimed.compareAndSet(false, true)) return false;
        try { return admission.getAsBoolean(); }
        catch (RuntimeException | LinkageError unavailable) { return false; }
    }
}
