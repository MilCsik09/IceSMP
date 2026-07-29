package hu.taliann.icesmp.crates;

import java.util.List;
import java.util.Objects;

/** Deterministic weighted selector; callers supply a unit roll so the domain is regression-testable. */
public final class WeightedSelector {

    private WeightedSelector() {
    }

    public static <T> T select(final List<Weighted<T>> entries, final double unitRoll) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty() || !Double.isFinite(unitRoll) || unitRoll < 0.0D || unitRoll >= 1.0D) {
            throw new IllegalArgumentException("Invalid weighted selection input");
        }
        double total = 0.0D;
        for (final Weighted<T> entry : entries) {
            total += entry.weight();
            if (!Double.isFinite(total)) {
                throw new IllegalArgumentException("Weight sum is not finite");
            }
        }
        if (total <= 0.0D) {
            throw new IllegalArgumentException("Weight sum must be positive");
        }
        double cursor = unitRoll * total;
        for (final Weighted<T> entry : entries) {
            cursor -= entry.weight();
            if (cursor < 0.0D) {
                return entry.value();
            }
        }
        return entries.get(entries.size() - 1).value();
    }

    public record Weighted<T>(double weight, T value) {
        public Weighted {
            if (!Double.isFinite(weight) || weight <= 0.0D || weight > CrateRules.MAX_WEIGHT) {
                throw new IllegalArgumentException("Weight must be finite and positive");
            }
            Objects.requireNonNull(value, "value");
        }
    }
}
