package hu.taliann.icesmp.managers;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Immutable runtime and persisted gameplay state for the single Bingulus DEV item. */
record DevItemStateData<T>(
        UUID owner,
        UUID instanceId,
        boolean issued,
        long progressMillis,
        PendingReward<T> pending,
        PityCounters pity
) {

    DevItemStateData {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(pity, "pity");
        if (progressMillis < 0L) {
            throw new IllegalArgumentException("progress-millis cannot be negative");
        }
        if (!issued && (progressMillis != 0L || pending != null || !pity.isZero())) {
            throw new IllegalArgumentException(
                    "an unissued DEV item cannot carry active time, pending reward or pity progress");
        }
    }

    static <T> DevItemStateData<T> fresh(final UUID owner, final UUID instanceId) {
        return new DevItemStateData<>(owner, instanceId, false, 0L, null, PityCounters.ZERO);
    }

    DevItemStateData<T> copy(final UnaryOperator<T> itemCopier) {
        return new DevItemStateData<>(owner, instanceId, issued, progressMillis,
                pending == null ? null : pending.copy(itemCopier), pity);
    }

    DevItemStateData<T> withOwner(final UUID newOwner, final UnaryOperator<T> itemCopier) {
        if (owner.equals(newOwner)) {
            return copy(itemCopier);
        }
        return new DevItemStateData<>(Objects.requireNonNull(newOwner, "newOwner"), instanceId,
                issued, progressMillis, pending == null ? null : pending.copy(itemCopier), pity);
    }

    DevItemStateData<T> issued(final UnaryOperator<T> itemCopier) {
        if (issued) {
            return copy(itemCopier);
        }
        return new DevItemStateData<>(owner, instanceId, true, progressMillis,
                pending == null ? null : pending.copy(itemCopier), pity);
    }

    DevItemStateData<T> advanceProgress(final long elapsedMillis, final long intervalMillis,
                                        final UnaryOperator<T> itemCopier) {
        if (elapsedMillis <= 0L) {
            return copy(itemCopier);
        }
        final long limit = Math.max(1L, intervalMillis);
        final long remaining = Math.max(0L, limit - progressMillis);
        final long increment = Math.min(remaining, elapsedMillis);
        return new DevItemStateData<>(owner, instanceId, issued, progressMillis + increment,
                pending == null ? null : pending.copy(itemCopier), pity);
    }

    DevItemStateData<T> withPending(final PendingReward<T> reward,
                                    final UnaryOperator<T> itemCopier) {
        if (pending != null) {
            throw new IllegalStateException("a pending DEV reward already exists");
        }
        return new DevItemStateData<>(owner, instanceId, issued, progressMillis,
                Objects.requireNonNull(reward, "reward").copy(itemCopier), pity);
    }

    DevItemStateData<T> completed(final PityCounters updatedPity,
                                  final UnaryOperator<T> itemCopier) {
        if (pending == null) {
            throw new IllegalStateException("there is no pending DEV reward to complete");
        }
        return new DevItemStateData<>(owner, instanceId, issued, 0L, null,
                Objects.requireNonNull(updatedPity, "updatedPity"));
    }

    boolean ownerIs(final UUID expectedOwner) {
        return owner.equals(expectedOwner);
    }

    static UUID requireUuid(final String raw, final String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " must contain a UUID");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (final IllegalArgumentException malformed) {
            throw new IllegalArgumentException(field + " is not a valid UUID", malformed);
        }
    }

    static final class PendingReward<T> {
        private final String rarity;
        private final String entry;
        private final T exactItem;

        private PendingReward(final String rarity, final String entry, final T exactItem) {
            this.rarity = rarity;
            this.entry = entry;
            this.exactItem = exactItem;
        }

        static <T> PendingReward<T> of(final String rarity, final String entry, final T exactItem,
                                       final UnaryOperator<T> itemCopier,
                                       final Predicate<T> validItem,
                                       final Predicate<String> knownRarity) {
            Objects.requireNonNull(rarity, "rarity");
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(exactItem, "exactItem");
            Objects.requireNonNull(itemCopier, "itemCopier");
            Objects.requireNonNull(validItem, "validItem");
            Objects.requireNonNull(knownRarity, "knownRarity");
            if (rarity.isBlank() || entry.isBlank()) {
                throw new IllegalArgumentException("pending rarity and entry cannot be blank");
            }
            if (!knownRarity.test(rarity)) {
                throw new IllegalArgumentException("unknown pending rarity: " + rarity);
            }
            if (!validItem.test(exactItem)) {
                throw new IllegalArgumentException("pending exact item is empty or invalid");
            }
            final T copy = Objects.requireNonNull(itemCopier.apply(exactItem), "copied exact item");
            if (!validItem.test(copy)) {
                throw new IllegalArgumentException("copied pending exact item is empty or invalid");
            }
            return new PendingReward<>(rarity, entry, copy);
        }

        String rarity() {
            return rarity;
        }

        String entry() {
            return entry;
        }

        T itemCopy(final UnaryOperator<T> itemCopier) {
            return Objects.requireNonNull(itemCopier.apply(exactItem), "copied exact item");
        }

        PendingReward<T> copy(final UnaryOperator<T> itemCopier) {
            return new PendingReward<>(rarity, entry, itemCopy(itemCopier));
        }

        boolean same(final PendingReward<T> other, final BiPredicate<T, T> sameItem) {
            return other != null
                    && rarity.equals(other.rarity)
                    && entry.equals(other.entry)
                    && sameItem.test(exactItem, other.exactItem);
        }
    }

    record PityCounters(int sinceRare, int sinceEpic, int sinceLegendary) {
        static final PityCounters ZERO = new PityCounters(0, 0, 0);

        PityCounters {
            if (sinceRare < 0 || sinceEpic < 0 || sinceLegendary < 0) {
                throw new IllegalArgumentException("pity counters cannot be negative");
            }
        }

        boolean isZero() {
            return sinceRare == 0 && sinceEpic == 0 && sinceLegendary == 0;
        }
    }

    /** Minimal per-manager coalescing gate for entity-scheduler callbacks. */
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
}
