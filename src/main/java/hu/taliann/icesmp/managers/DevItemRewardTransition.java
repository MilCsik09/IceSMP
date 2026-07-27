package hu.taliann.icesmp.managers;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Bukkit-free transition model for the single DEV-item pending reward.
 *
 * <p>The caller serializes state publication. Durable writers receive an immutable candidate and
 * must complete before the caller publishes that candidate as live state. Inventory mutation stays
 * outside this component; completion uses an owner fence so a stale entity-scheduler tick cannot
 * acknowledge another owner's reward.</p>
 */
final class DevItemRewardTransition {

    interface ExactItemPolicy<T> {
        T copy(T item);

        boolean isValid(T item);

        boolean same(T left, T right);
    }

    @FunctionalInterface
    interface StateWriter<T> {
        void write(State<T> candidate);
    }

    @FunctionalInterface
    interface PityRule {
        PityCounters after(String rarity, PityCounters current);
    }

    record OwnerFence(UUID owner, long generation) {
        OwnerFence {
            Objects.requireNonNull(owner, "owner");
            if (generation < 0L) {
                throw new IllegalArgumentException("owner generation cannot be negative");
            }
        }
    }

    record PityCounters(int sinceRare, int sinceEpic, int sinceLegendary) {
        PityCounters {
            if (sinceRare < 0 || sinceEpic < 0 || sinceLegendary < 0) {
                throw new IllegalArgumentException("pity counters cannot be negative");
            }
        }
    }

    record Pending<T>(String rarity, String entry, T exactItem) {
        Pending {
            Objects.requireNonNull(rarity, "rarity");
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(exactItem, "exactItem");
            if (rarity.isBlank() || entry.isBlank()) {
                throw new IllegalArgumentException("pending rarity and entry cannot be blank");
            }
        }
    }

    record State<T>(OwnerFence fence, long progressMillis, Pending<T> pending, PityCounters pity) {
        State {
            Objects.requireNonNull(fence, "fence");
            Objects.requireNonNull(pity, "pity");
            if (progressMillis < 0L) {
                throw new IllegalArgumentException("progress cannot be negative");
            }
        }
    }

    record Preparation<T>(State<T> state, Pending<T> pending, boolean prepared) {
    }

    record Completion<T>(State<T> state, boolean committed) {
    }

    /** Minimal coalescing gate shared by the runtime and deterministic overlap regression. */
    static final class TickGate {
        private final AtomicBoolean running = new AtomicBoolean();

        boolean tryEnter() {
            return running.compareAndSet(false, true);
        }

        void exit() {
            running.set(false);
        }

        boolean isRunning() {
            return running.get();
        }
    }

    private DevItemRewardTransition() {
    }

    static <T> Pending<T> pending(final String rarity, final String entry, final T exactItem,
                                  final ExactItemPolicy<T> itemPolicy,
                                  final Predicate<String> knownRarity) {
        Objects.requireNonNull(itemPolicy, "itemPolicy");
        Objects.requireNonNull(knownRarity, "knownRarity");
        if (!knownRarity.test(rarity)) {
            throw new IllegalArgumentException("unknown pending rarity: " + rarity);
        }
        if (!itemPolicy.isValid(exactItem)) {
            throw new IllegalArgumentException("pending exact item is empty or invalid");
        }
        return new Pending<>(rarity, entry, itemPolicy.copy(exactItem));
    }

    static <T> State<T> state(final OwnerFence fence, final long progressMillis,
                              final Pending<T> pending, final PityCounters pity,
                              final ExactItemPolicy<T> itemPolicy) {
        Objects.requireNonNull(itemPolicy, "itemPolicy");
        return new State<>(fence, progressMillis, copyPending(pending, itemPolicy), pity);
    }

    static <T> State<T> advanceProgress(final State<T> current, final OwnerFence expectedFence,
                                        final long elapsedMillis, final long intervalMillis,
                                        final ExactItemPolicy<T> itemPolicy) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(expectedFence, "expectedFence");
        if (!current.fence().equals(expectedFence) || elapsedMillis <= 0L) {
            return current;
        }
        final long limit = Math.max(1L, intervalMillis);
        final long remaining = Math.max(0L, limit - current.progressMillis());
        final long increment = Math.min(remaining, elapsedMillis);
        return state(current.fence(), current.progressMillis() + increment,
                current.pending(), current.pity(), itemPolicy);
    }

    static <T> Preparation<T> prepare(final State<T> current, final OwnerFence expectedFence,
                                      final Pending<T> proposed,
                                      final ExactItemPolicy<T> itemPolicy,
                                      final StateWriter<T> writer) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(expectedFence, "expectedFence");
        Objects.requireNonNull(proposed, "proposed");
        Objects.requireNonNull(writer, "writer");
        if (!current.fence().equals(expectedFence) || current.pending() != null) {
            return new Preparation<>(current, current.pending(), false);
        }
        final State<T> candidate = state(current.fence(), current.progressMillis(),
                proposed, current.pity(), itemPolicy);
        writer.write(candidate);
        return new Preparation<>(candidate, candidate.pending(), true);
    }

    static <T> Completion<T> complete(final State<T> current, final OwnerFence expectedFence,
                                      final UUID actor, final Pending<T> expectedPending,
                                      final ExactItemPolicy<T> itemPolicy,
                                      final PityRule pityRule,
                                      final StateWriter<T> writer) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(expectedFence, "expectedFence");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(expectedPending, "expectedPending");
        Objects.requireNonNull(pityRule, "pityRule");
        Objects.requireNonNull(writer, "writer");
        if (!current.fence().equals(expectedFence)
                || !expectedFence.owner().equals(actor)
                || !samePending(current.pending(), expectedPending, itemPolicy)) {
            return new Completion<>(current, false);
        }
        final PityCounters updatedPity = Objects.requireNonNull(
                pityRule.after(expectedPending.rarity(), current.pity()), "updated pity");
        final State<T> candidate = state(current.fence(), 0L, null, updatedPity, itemPolicy);
        writer.write(candidate);
        return new Completion<>(candidate, true);
    }

    static <T> State<T> transfer(final State<T> current, final UUID newOwner,
                                 final ExactItemPolicy<T> itemPolicy,
                                 final StateWriter<T> writer) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(newOwner, "newOwner");
        Objects.requireNonNull(writer, "writer");
        if (current.fence().owner().equals(newOwner)) {
            return current;
        }
        final OwnerFence nextFence = new OwnerFence(newOwner,
                Math.addExact(current.fence().generation(), 1L));
        final State<T> candidate = state(nextFence, current.progressMillis(),
                current.pending(), current.pity(), itemPolicy);
        writer.write(candidate);
        return candidate;
    }

    static <T> boolean samePending(final Pending<T> left, final Pending<T> right,
                                   final ExactItemPolicy<T> itemPolicy) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.rarity().equals(right.rarity())
                && left.entry().equals(right.entry())
                && itemPolicy.same(left.exactItem(), right.exactItem());
    }

    private static <T> Pending<T> copyPending(final Pending<T> pending,
                                              final ExactItemPolicy<T> itemPolicy) {
        if (pending == null) {
            return null;
        }
        if (!itemPolicy.isValid(pending.exactItem())) {
            throw new IllegalArgumentException("pending exact item is empty or invalid");
        }
        return new Pending<>(pending.rarity(), pending.entry(), itemPolicy.copy(pending.exactItem()));
    }
}
