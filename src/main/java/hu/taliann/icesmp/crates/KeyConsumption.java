package hu.taliann.icesmp.crates;

import java.util.ArrayList;
import java.util.List;

/** Pure planning step for exact, multi-stack key consumption. */
public final class KeyConsumption {

    private KeyConsumption() {
    }

    public static List<Take> plan(final List<Integer> stackAmounts, final int required) {
        if (stackAmounts == null || required <= 0) {
            return List.of();
        }
        int remaining = required;
        final List<Take> plan = new ArrayList<>();
        for (int index = 0; index < stackAmounts.size() && remaining > 0; index++) {
            final int available = Math.max(0, stackAmounts.get(index));
            if (available == 0) {
                continue;
            }
            final int take = Math.min(available, remaining);
            plan.add(new Take(index, take));
            remaining -= take;
        }
        return remaining == 0 ? List.copyOf(plan) : List.of();
    }

    public record Take(int index, int amount) {
        public Take {
            if (index < 0 || amount <= 0) {
                throw new IllegalArgumentException("Invalid key take");
            }
        }
    }
}
