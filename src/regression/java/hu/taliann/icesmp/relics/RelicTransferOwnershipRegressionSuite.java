package hu.taliann.icesmp.relics;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Expected-owner, stale-copy, rollback and concurrent PvP transfer regressions. */
public final class RelicTransferOwnershipRegressionSuite {

    private static final Logger LOGGER = Logger.getLogger("RelicTransferOwnershipRegressionSuite");
    private static int assertions;

    private RelicTransferOwnershipRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        staleCallerProofCannotOverwriteCurrentOwner();
        successfulTransferPublishesAfterDurableCommit();
        persistenceFailureLeavesOwnershipUntouched();
        concurrentTransfersHaveExactlyOneWinner();
        System.out.println("Relic transfer ownership regression suite passed. assertions=" + assertions);
    }

    private static void staleCallerProofCannotOverwriteCurrentOwner() {
        final UUID currentOwner = UUID.fromString("00000000-0000-0000-0000-000000009201");
        final UUID stalePhysicalOwner = UUID.fromString("00000000-0000-0000-0000-000000009202");
        final UUID killer = UUID.fromString("00000000-0000-0000-0000-000000009203");
        final RelicWorldStateStore store = store(yaml -> { });
        store.recordOwnership("metelytepo", currentOwner, 10L);

        // Simulates the legacy manager re-reading currentOwner while the death-event itself
        // proves that the stale physical copy belongs to stalePhysicalOwner.
        expect(IllegalStateException.class, () -> RelicTransferExpectation.withExpectedOwner(
                stalePhysicalOwner,
                () -> store.beginTransfer("metelytepo", currentOwner, killer, 20L)));

        check(store.ownership("metelytepo").owner().equals(currentOwner),
                "stale physical copy cannot overwrite central owner");
        check(store.pendingOperation("metelytepo") == null,
                "stale transfer creates no durable pending operation");
    }

    private static void successfulTransferPublishesAfterDurableCommit() {
        final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000009211");
        final UUID killer = UUID.fromString("00000000-0000-0000-0000-000000009212");
        final AtomicInteger durableWrites = new AtomicInteger();
        final RelicWorldStateStore store = store(yaml -> durableWrites.incrementAndGet());
        store.recordOwnership("metelytepo", owner, 10L);
        final int before = durableWrites.get();

        final RelicWorldStateStore.TransferResult result =
                RelicTransferExpectation.withExpectedOwner(owner,
                        () -> store.beginTransfer("metelytepo", owner, killer, 20L));

        check(result == RelicWorldStateStore.TransferResult.TRANSFERRED,
                "valid expected-owner transfer succeeds");
        check(durableWrites.get() == before + 1,
                "transfer performs exactly one durable write");
        check(store.ownership("metelytepo").owner().equals(killer),
                "central ownership changes after durable transfer");
        final var pending = store.pendingOperation("metelytepo");
        check(pending != null && pending.fromOwner().equals(owner)
                        && pending.toOwner().equals(killer),
                "pending transfer receipt preserves proven from/to owners");
    }

    private static void persistenceFailureLeavesOwnershipUntouched() {
        final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000009221");
        final UUID killer = UUID.fromString("00000000-0000-0000-0000-000000009222");
        final AtomicBoolean failWrites = new AtomicBoolean();
        final RelicWorldStateStore store = store(yaml -> {
            if (failWrites.get()) throw new IOException("injected transfer write failure");
        });
        store.recordOwnership("metelytepo", owner, 10L);
        failWrites.set(true);

        expect(java.io.UncheckedIOException.class, () -> RelicTransferExpectation.withExpectedOwner(
                owner, () -> store.beginTransfer("metelytepo", owner, killer, 20L)));

        check(store.ownership("metelytepo").owner().equals(owner),
                "persistence failure keeps published ownership unchanged");
        check(store.pendingOperation("metelytepo") == null,
                "persistence failure publishes no transfer receipt");
    }

    private static void concurrentTransfersHaveExactlyOneWinner() throws Exception {
        final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000009231");
        final UUID killerA = UUID.fromString("00000000-0000-0000-0000-000000009232");
        final UUID killerB = UUID.fromString("00000000-0000-0000-0000-000000009233");
        final RelicWorldStateStore store = store(yaml -> { });
        store.recordOwnership("metelytepo", owner, 10L);

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger winners = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();

        final Thread first = Thread.ofVirtual().start(() -> transferRace(
                store, owner, killerA, ready, start, winners, rejected));
        final Thread second = Thread.ofVirtual().start(() -> transferRace(
                store, owner, killerB, ready, start, winners, rejected));
        ready.await();
        start.countDown();
        first.join();
        second.join();

        check(winners.get() == 1, "exactly one concurrent expected-owner transfer wins");
        check(rejected.get() == 1, "losing concurrent transfer fails closed");
        final UUID finalOwner = store.ownership("metelytepo").owner();
        check(finalOwner.equals(killerA) || finalOwner.equals(killerB),
                "central owner equals the sole winning transfer target");
        final var pending = store.pendingOperation("metelytepo");
        check(pending != null && pending.fromOwner().equals(owner)
                        && pending.toOwner().equals(finalOwner),
                "durable pending operation belongs to the sole winner");
    }

    private static void transferRace(final RelicWorldStateStore store,
                                     final UUID owner,
                                     final UUID target,
                                     final CountDownLatch ready,
                                     final CountDownLatch start,
                                     final AtomicInteger winners,
                                     final AtomicInteger rejected) {
        ready.countDown();
        try {
            start.await();
            RelicTransferExpectation.withExpectedOwner(owner,
                    () -> store.beginTransfer("metelytepo", owner, target, 30L));
            winners.incrementAndGet();
        } catch (final IllegalStateException expected) {
            rejected.incrementAndGet();
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static RelicWorldStateStore store(final RelicWorldStateStore.DurableWriter writer) {
        return new RelicWorldStateStore(writer, LOGGER);
    }

    private static void expect(final Class<? extends Throwable> expected, final Throwing action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (final Throwable failure) {
            if (!expected.isInstance(failure)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + failure, failure);
            }
        }
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }
}
