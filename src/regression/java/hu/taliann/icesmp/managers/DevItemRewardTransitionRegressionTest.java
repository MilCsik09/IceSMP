package hu.taliann.icesmp.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Behavioural regressions for DEV pending/retry and live-owner fencing. */
public final class DevItemRewardTransitionRegressionTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NEW_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Set<String> RARITIES = Set.of("kozonseges", "ritka", "epikus", "legendas");
    private static final DevItemRewardTransition.ExactItemPolicy<FakeItem> ITEMS =
            new DevItemRewardTransition.ExactItemPolicy<>() {
                public FakeItem copy(final FakeItem item) { return item == null ? null : item.copy(); }
                public boolean isValid(final FakeItem item) {
                    return item != null && !"AIR".equals(item.material()) && item.amount() > 0;
                }
                public boolean same(final FakeItem left, final FakeItem right) {
                    return left != null && left.equals(right);
                }
            };

    private DevItemRewardTransitionRegressionTest() {
    }

    public static void main(final String[] args) throws Exception {
        runAll();
        System.out.println("DevItem reward transition regression tests passed.");
    }

    static void runAll() throws Exception {
        prepareBeforeInventoryAndPrepareFailureLeavesNoGhost();
        fullInventoryPreservesExactPendingAndProgress();
        normalRestartReplaysExactItemOnce();
        completionWriteFailureRollsBackAndRetries();
        ownerTransferAfterDurablePrepareFencesStaleTick();
        ownerTransferAfterInventoryMutationRollsBackStaleDelivery();
        failedOwnerSnapshotWriteNeverPublishesNewOwner();
        overlappingTicksAreCoalesced();
        strictPendingItemValidation();
    }

    private static void prepareBeforeInventoryAndPrepareFailureLeavesNoGhost() {
        final Harness h = harness(9_000L, null, 4, 5, 6);
        h.writer.failNext.set(true);
        expectThrows(SimulatedWriteFailure.class, () -> h.prepare(reward()));
        check(h.inventory.items.isEmpty(), "prepare failure touched inventory");
        check(h.state.get().pending() == null, "prepare failure published ghost pending");
        check(h.state.get().progressMillis() == 9_000L, "prepare failure changed progress");
        check(h.writer.writes.get() == 0, "failed prepare counted as durable");

        final var pending = h.prepare(reward());
        check(h.writer.writes.get() == 1, "prepare was not durably written once");
        check(h.inventory.items.isEmpty(), "inventory changed before durable prepare");
        check(pending.exactItem().equals(reward()), "prepared reward changed");
    }

    private static void fullInventoryPreservesExactPendingAndProgress() {
        final Harness h = harness(42_000L, null, 7, 11, 13);
        final var pending = h.prepare(reward());
        h.inventory.capacity = 0;
        check(!h.deliver(OWNER, h.state.get().fence(), pending), "full inventory delivered");
        check(h.inventory.items.isEmpty(), "full inventory partially changed");
        samePending(h.state.get().pending(), pending, "full inventory changed pending");
        check(h.state.get().progressMillis() == 42_000L, "full inventory reset progress");
        check(h.state.get().pity().equals(pity(7, 11, 13)), "full inventory changed pity");

        h.inventory.capacity = 1;
        check(h.deliver(OWNER, h.state.get().fence(), pending), "retry did not deliver");
        check(h.inventory.items.equals(List.of(reward())), "retry did not use exact reward");
    }

    private static void normalRestartReplaysExactItemOnce() {
        final Harness original = harness(18_000L, null, 2, 3, 4);
        original.prepare(reward());
        final Harness restarted = new Harness(copy(original.writer.lastDurable.get()));
        check(restarted.rolls.get() == 0, "restart rerolled pending reward");
        final FakeItem restored = restarted.state.get().pending().exactItem();
        check(restored.amount() == 2, "restart changed amount");
        check(restored.meta().equals("Fagyott Korona"), "restart changed meta");
        check(restored.affix().equals("crit+7"), "restart changed affix");
        check(restored.stamp().equals("crafted-by:Milán"), "restart changed stamp");

        final var pending = restarted.state.get().pending();
        check(restarted.deliver(OWNER, restarted.state.get().fence(), pending), "restart delivery failed");
        check(restarted.state.get().pending() == null, "completion did not clear pending");
        check(restarted.writer.writes.get() == 1, "restart completion was not written once");
        check(!restarted.deliver(OWNER, restarted.state.get().fence(), pending), "reward delivered twice");
        check(restarted.inventory.items.size() == 1, "restart replay duplicated item");
    }

    private static void completionWriteFailureRollsBackAndRetries() {
        final Harness h = harness(55_000L, null, 8, 9, 10);
        final var pending = h.prepare(reward());
        final var before = copy(h.state.get());
        h.writer.failNext.set(true);
        expectThrows(SimulatedWriteFailure.class, () -> h.deliver(OWNER, before.fence(), pending));
        check(h.inventory.items.isEmpty(), "completion failure did not roll inventory back");
        sameState(h.state.get(), before, "completion failure changed durable state");
        check(h.announcements.get() == 0, "failed completion announced");
        check(h.deliver(OWNER, before.fence(), pending), "failed completion was not retryable");
        check(h.inventory.items.equals(List.of(reward())), "retry changed exact reward");
        check(h.announcements.get() == 1, "committed completion did not announce once");
    }

    private static void ownerTransferAfterDurablePrepareFencesStaleTick() throws Exception {
        final Harness h = harness(60_000L, null, 12, 14, 16);
        final CountDownLatch prepared = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicBoolean oldDelivered = new AtomicBoolean(true);
        final var oldFence = h.state.get().fence();
        final Thread oldTick = new Thread(() -> {
            try {
                final var pending = h.prepare(reward());
                prepared.countDown();
                await(resume, "old tick resume");
                oldDelivered.set(h.deliver(OWNER, oldFence, pending));
            } catch (final Throwable thrown) {
                failure.set(thrown);
            }
        }, "old-owner-after-prepare");
        oldTick.start();
        await(prepared, "durable prepare");
        final var exactPending = h.state.get().pending();
        h.transfer(NEW_OWNER);
        resume.countDown();
        join(oldTick);
        rethrow(failure.get());
        check(!oldDelivered.get(), "old owner retained reward after transfer");
        check(h.inventory.items.isEmpty(), "old owner inventory retained reward");
        samePending(h.state.get().pending(), exactPending, "transfer lost exact pending");
        check(h.state.get().progressMillis() == 60_000L, "stale tick reset progress");
        check(h.state.get().pity().equals(pity(12, 14, 16)), "stale tick changed pity");
        check(h.deliver(NEW_OWNER, h.state.get().fence(), h.state.get().pending()),
                "new owner could not receive exact pending reward");
    }

    private static void ownerTransferAfterInventoryMutationRollsBackStaleDelivery() throws Exception {
        final Harness h = harness(61_000L, null, 5, 6, 7);
        final var pending = h.prepare(reward());
        final var oldFence = h.state.get().fence();
        final CountDownLatch mutated = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicBoolean committed = new AtomicBoolean(true);
        final Thread oldTick = new Thread(() -> {
            try {
                committed.set(h.deliverPaused(OWNER, oldFence, pending, mutated, resume));
            } catch (final Throwable thrown) {
                failure.set(thrown);
            }
        }, "old-owner-after-inventory");
        oldTick.start();
        await(mutated, "inventory mutation");
        check(h.inventory.items.size() == 1, "fixture did not pause after mutation");
        h.transfer(NEW_OWNER);
        resume.countDown();
        join(oldTick);
        rethrow(failure.get());
        check(!committed.get(), "stale completion committed");
        check(h.inventory.items.isEmpty(), "stale inventory mutation did not roll back");
        samePending(h.state.get().pending(), pending, "stale completion cleared pending");
        check(h.state.get().progressMillis() == 61_000L, "stale completion reset progress");
        check(h.state.get().pity().equals(pity(5, 6, 7)), "stale completion changed pity");
        check(h.deliver(NEW_OWNER, h.state.get().fence(), pending),
                "new owner could not claim rolled-back reward");
    }

    private static void failedOwnerSnapshotWriteNeverPublishesNewOwner() {
        final Harness h = harness(27_000L, pending(reward()), 3, 4, 5);
        final var before = copy(h.state.get());
        h.writer.failNext.set(true);
        expectThrows(SimulatedWriteFailure.class, () -> h.transfer(NEW_OWNER));
        sameState(h.state.get(), before, "failed owner write changed live state");
        check(h.state.get().fence().owner().equals(OWNER), "failed write published new owner");
        check(h.refreshes.get() == 0, "failed write triggered new-owner refresh");
    }

    private static void overlappingTicksAreCoalesced() throws Exception {
        final DevItemRewardTransition.TickGate gate = new DevItemRewardTransition.TickGate();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean secondEntered = new AtomicBoolean(true);
        final Thread first = new Thread(() -> {
            check(gate.tryEnter(), "first tick could not enter");
            entered.countDown();
            await(release, "first tick release");
            gate.exit();
        }, "first-dev-tick");
        first.start();
        await(entered, "first tick entry");
        final Thread second = new Thread(() -> secondEntered.set(gate.tryEnter()), "second-dev-tick");
        second.start();
        join(second);
        check(!secondEntered.get(), "overlapping tick entered");
        release.countDown();
        join(first);
        check(!gate.isRunning(), "tick gate stayed closed");
        check(gate.tryEnter(), "next non-overlapping tick could not enter");
        gate.exit();
    }

    private static void strictPendingItemValidation() {
        expectThrows(IllegalArgumentException.class,
                () -> DevItemRewardTransition.pending("ritka", "material:stone",
                        new FakeItem("AIR", 1, "", "", ""), ITEMS, RARITIES::contains));
        expectThrows(IllegalArgumentException.class,
                () -> DevItemRewardTransition.pending("ritka", "material:stone",
                        new FakeItem("STONE", 0, "", "", ""), ITEMS, RARITIES::contains));
        expectThrows(IllegalArgumentException.class,
                () -> DevItemRewardTransition.pending("ismeretlen", "material:stone",
                        reward(), ITEMS, RARITIES::contains));
    }

    private static Harness harness(final long progress,
                                   final DevItemRewardTransition.Pending<FakeItem> pending,
                                   final int rare, final int epic, final int legendary) {
        return new Harness(DevItemRewardTransition.state(
                new DevItemRewardTransition.OwnerFence(OWNER, 0L), progress, pending,
                pity(rare, epic, legendary), ITEMS));
    }

    private static DevItemRewardTransition.Pending<FakeItem> pending(final FakeItem item) {
        return DevItemRewardTransition.pending("epikus", "unique:jegsziv:2", item,
                ITEMS, RARITIES::contains);
    }

    private static DevItemRewardTransition.PityCounters pity(
            final int rare, final int epic, final int legendary) {
        return new DevItemRewardTransition.PityCounters(rare, epic, legendary);
    }

    private static FakeItem reward() {
        return new FakeItem("DIAMOND", 2, "Fagyott Korona", "crit+7", "crafted-by:Milán");
    }

    private static DevItemRewardTransition.State<FakeItem> copy(
            final DevItemRewardTransition.State<FakeItem> state) {
        return DevItemRewardTransition.state(state.fence(), state.progressMillis(),
                state.pending(), state.pity(), ITEMS);
    }

    private static void samePending(final DevItemRewardTransition.Pending<FakeItem> actual,
                                    final DevItemRewardTransition.Pending<FakeItem> expected,
                                    final String message) {
        check(DevItemRewardTransition.samePending(actual, expected, ITEMS), message);
    }

    private static void sameState(final DevItemRewardTransition.State<FakeItem> actual,
                                  final DevItemRewardTransition.State<FakeItem> expected,
                                  final String message) {
        check(actual.fence().equals(expected.fence())
                        && actual.progressMillis() == expected.progressMillis()
                        && DevItemRewardTransition.samePending(actual.pending(), expected.pending(), ITEMS)
                        && actual.pity().equals(expected.pity()), message);
    }

    private static void await(final CountDownLatch latch, final String name) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for " + name);
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted waiting for " + name, interrupted);
        }
    }

    private static void join(final Thread thread) throws InterruptedException {
        thread.join(5_000L);
        check(!thread.isAlive(), "thread did not terminate: " + thread.getName());
    }

    private static void rethrow(final Throwable failure) {
        if (failure == null) return;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        throw new AssertionError(failure);
    }

    private static <T extends Throwable> T expectThrows(final Class<T> type, final ThrowingRunnable action) {
        try {
            action.run();
        } catch (final Throwable thrown) {
            if (type.isInstance(thrown)) return type.cast(thrown);
            throw new AssertionError("Expected " + type.getName() + " but got " + thrown, thrown);
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record FakeItem(String material, int amount, String meta, String affix, String stamp) {
        private FakeItem copy() { return new FakeItem(material, amount, meta, affix, stamp); }
    }

    private static final class Inventory {
        private final List<FakeItem> items = new ArrayList<>();
        private int capacity = 1;
        private List<FakeItem> snapshot() { return List.copyOf(items); }
        private boolean add(final FakeItem item) {
            if (items.size() >= capacity) return false;
            items.add(item.copy());
            return true;
        }
        private void restore(final List<FakeItem> snapshot) {
            items.clear();
            snapshot.forEach(item -> items.add(item.copy()));
        }
    }

    private static final class Writer implements DevItemRewardTransition.StateWriter<FakeItem> {
        private final AtomicBoolean failNext = new AtomicBoolean();
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicReference<DevItemRewardTransition.State<FakeItem>> lastDurable = new AtomicReference<>();
        public void write(final DevItemRewardTransition.State<FakeItem> candidate) {
            if (failNext.compareAndSet(true, false)) throw new SimulatedWriteFailure();
            lastDurable.set(copy(candidate));
            writes.incrementAndGet();
        }
    }

    private static final class Harness {
        private final Object lock = new Object();
        private final AtomicReference<DevItemRewardTransition.State<FakeItem>> state;
        private final Writer writer = new Writer();
        private final Inventory inventory = new Inventory();
        private final AtomicInteger announcements = new AtomicInteger();
        private final AtomicInteger rolls = new AtomicInteger();
        private final AtomicInteger refreshes = new AtomicInteger();
        private Harness(final DevItemRewardTransition.State<FakeItem> initial) {
            state = new AtomicReference<>(copy(initial));
        }
        private DevItemRewardTransition.Pending<FakeItem> prepare(final FakeItem item) {
            synchronized (lock) {
                final var current = state.get();
                rolls.incrementAndGet();
                final var result = DevItemRewardTransition.prepare(
                        current, current.fence(), pending(item), ITEMS, writer);
                if (result.prepared()) state.set(copy(result.state()));
                return result.pending();
            }
        }
        private void transfer(final UUID owner) {
            synchronized (lock) {
                final var candidate = DevItemRewardTransition.transfer(state.get(), owner, ITEMS, writer);
                state.set(copy(candidate));
                refreshes.incrementAndGet();
            }
        }
        private boolean deliver(final UUID actor, final DevItemRewardTransition.OwnerFence fence,
                                final DevItemRewardTransition.Pending<FakeItem> pending) {
            return deliverPaused(actor, fence, pending, null, null);
        }
        private boolean deliverPaused(final UUID actor, final DevItemRewardTransition.OwnerFence fence,
                                      final DevItemRewardTransition.Pending<FakeItem> pending,
                                      final CountDownLatch mutated, final CountDownLatch resume) {
            final List<FakeItem> before = inventory.snapshot();
            if (!inventory.add(pending.exactItem())) return false;
            if (mutated != null) {
                mutated.countDown();
                await(resume, "completion resume");
            }
            try {
                synchronized (lock) {
                    final var result = DevItemRewardTransition.complete(
                            state.get(), fence, actor, pending, ITEMS,
                            (rarity, current) -> new DevItemRewardTransition.PityCounters(
                                    RARITIES.contains(rarity) && !"kozonseges".equals(rarity)
                                            ? 0 : current.sinceRare() + 1,
                                    Set.of("epikus", "legendas").contains(rarity)
                                            ? 0 : current.sinceEpic() + 1,
                                    "legendas".equals(rarity) ? 0 : current.sinceLegendary() + 1),
                            writer);
                    if (!result.committed()) {
                        inventory.restore(before);
                        return false;
                    }
                    state.set(copy(result.state()));
                }
                announcements.incrementAndGet();
                return true;
            } catch (final RuntimeException | Error failure) {
                inventory.restore(before);
                throw failure;
            }
        }
    }

    private static final class SimulatedWriteFailure extends RuntimeException {
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Throwable; }
}
