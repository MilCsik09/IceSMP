package hu.taliann.icesmp.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free regression suite executed by the Gradle {@code check} lifecycle. */
public final class PersistentStoreCoordinatorRegressionTest {

    private PersistentStoreCoordinatorRegressionTest() {
    }

    public static void main(final String[] args) throws Exception {
        loadFailureAbortsAndBlocksPartialSave();
        fatalLoadErrorRemainsFatal();
        successfulLoadSerializesBestEffortRuntimeSaves();
        shutdownWaitsForRunningAutosaveAndClosesTheGate();
        duplicateRegistrationIsRejectedByIdentity();
        System.out.println("PersistentStoreCoordinator regression tests passed.");
    }

    private static void loadFailureAbortsAndBlocksPartialSave() {
        final ProbeStore first = new ProbeStore();
        final ProbeStore failed = new ProbeStore();
        final ProbeStore neverReached = new ProbeStore();
        final RuntimeException failure = new IllegalStateException("broken state");
        failed.loadFailure = failure;

        final PersistentStoreCoordinator coordinator =
                new PersistentStoreCoordinator(List.of(first, failed, neverReached));
        final PersistentStoreCoordinator.StoreLoadException thrown = expectThrows(
                PersistentStoreCoordinator.StoreLoadException.class, coordinator::loadAll);

        check(thrown.store() == failed, "load exception must identify the failed store");
        check(thrown.getCause() == failure, "load exception must retain the original cause");
        check(first.loadCount.get() == 1, "the first store must load once");
        check(failed.loadCount.get() == 1, "the failed store must be attempted once");
        check(neverReached.loadCount.get() == 0, "stores after a failure must not load");
        check(coordinator.loadedStoreCount() == 1, "only completed loads may be tracked");
        check(!coordinator.isReady(), "a partial load must never become ready");

        check(!coordinator.saveAll(ignored -> { }), "partial startup must refuse common saves");
        check(first.saveCount.get() == 0, "partial startup must not save even completed stores");
        expectThrows(IllegalStateException.class, coordinator::loadAll);
        check(first.loadCount.get() == 1, "a failed startup must not be replayed on live managers");
    }

    private static void fatalLoadErrorRemainsFatal() {
        final ProbeStore first = new ProbeStore();
        final ProbeStore failed = new ProbeStore();
        final ProbeStore neverReached = new ProbeStore();
        final AssertionError fatal = new AssertionError("fatal persistence signal");
        failed.loadError = fatal;

        final PersistentStoreCoordinator coordinator =
                new PersistentStoreCoordinator(List.of(first, failed, neverReached));
        final AssertionError thrown = expectThrows(AssertionError.class, coordinator::loadAll);

        check(thrown == fatal, "fatal errors must propagate unchanged");
        check(!coordinator.isReady(), "fatal load failure must leave the coordinator unready");
        check(neverReached.loadCount.get() == 0, "fatal failure must abort later loads");
        check(!coordinator.saveAll(ignored -> { }), "fatal startup must refuse common saves");
    }

    private static void successfulLoadSerializesBestEffortRuntimeSaves() {
        final ProbeStore first = new ProbeStore();
        final ProbeStore failedSave = new ProbeStore();
        final ProbeStore last = new ProbeStore();
        final RuntimeException failure = new IllegalStateException("disk full");
        failedSave.saveFailure = failure;

        final PersistentStoreCoordinator coordinator =
                new PersistentStoreCoordinator(List.of(first, failedSave, last));
        coordinator.loadAll();
        final List<PersistentStoreCoordinator.SaveFailure> failures = new ArrayList<>();
        check(coordinator.saveAll(failures::add), "a ready coordinator must accept autosave");

        check(coordinator.isReady(), "all successful loads must make the coordinator ready");
        check(first.saveCount.get() == 1, "the first store must save");
        check(failedSave.saveCount.get() == 1, "the failing save must be attempted");
        check(last.saveCount.get() == 1, "a runtime save failure must not hide later stores");
        check(failures.size() == 1, "one runtime save failure must be reported once");
        check(failures.getFirst().store() == failedSave, "save failure must identify its store");
        check(failures.getFirst().cause() == failure, "save failure must retain its cause");

        check(coordinator.saveAll(ignored -> { }), "a ready coordinator must support repeated autosaves");
        check(last.saveCount.get() == 2, "a ready coordinator must support repeated autosaves");

        check(coordinator.beginShutdown(), "shutdown must atomically close a ready autosave gate");
        check(!coordinator.isReady(), "shutdown must make the coordinator non-ready");
        check(!coordinator.saveAll(ignored -> { }), "autosave must be refused after shutdown begins");
        coordinator.saveForShutdown(ignored -> { });
        check(last.saveCount.get() == 3, "shutdown must persist one final snapshot");
        check(!coordinator.beginShutdown(), "shutdown transition must be one-shot");
        expectThrows(IllegalStateException.class, () -> coordinator.saveForShutdown(ignored -> { }));
    }

    private static void shutdownWaitsForRunningAutosaveAndClosesTheGate() throws Exception {
        final ProbeStore blocking = new ProbeStore();
        blocking.saveEntered = new CountDownLatch(1);
        blocking.releaseSave = new CountDownLatch(1);
        final PersistentStoreCoordinator coordinator =
                new PersistentStoreCoordinator(List.of(blocking));
        coordinator.loadAll();

        final AtomicBoolean autosaveResult = new AtomicBoolean();
        final Thread autosave = Thread.ofPlatform().name("store-autosave-test").start(
                () -> autosaveResult.set(coordinator.saveAll(ignored -> { })));
        check(blocking.saveEntered.await(5, TimeUnit.SECONDS), "autosave must enter the store");

        final AtomicBoolean shutdownResult = new AtomicBoolean();
        final CountDownLatch shutdownReturned = new CountDownLatch(1);
        final Thread shutdown = Thread.ofPlatform().name("store-shutdown-test").start(() -> {
            shutdownResult.set(coordinator.beginShutdown());
            shutdownReturned.countDown();
        });

        check(!shutdownReturned.await(100, TimeUnit.MILLISECONDS),
                "shutdown gate must wait for an in-flight common autosave");
        blocking.releaseSave.countDown();
        autosave.join(5_000L);
        shutdown.join(5_000L);

        check(!autosave.isAlive() && !shutdown.isAlive(), "lifecycle threads must complete");
        check(autosaveResult.get(), "the in-flight autosave must complete before shutdown");
        check(shutdownResult.get(), "shutdown must acquire the gate after autosave");
        check(!coordinator.saveAll(ignored -> { }), "no new autosave may start after the gate closes");
        coordinator.saveForShutdown(ignored -> { });
    }

    private static void duplicateRegistrationIsRejectedByIdentity() {
        final ProbeStore shared = new ProbeStore();
        expectThrows(IllegalArgumentException.class,
                () -> new PersistentStoreCoordinator(List.of(shared, shared)));
    }

    private static <T extends Throwable> T expectThrows(final Class<T> type, final ThrowingRunnable action) {
        try {
            action.run();
        } catch (final Throwable thrown) {
            if (type.isInstance(thrown)) {
                return type.cast(thrown);
            }
            throw new AssertionError("Expected " + type.getName() + " but got " + thrown, thrown);
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static final class ProbeStore implements PersistentStore {
        private final AtomicInteger loadCount = new AtomicInteger();
        private final AtomicInteger saveCount = new AtomicInteger();
        private RuntimeException loadFailure;
        private Error loadError;
        private RuntimeException saveFailure;
        private CountDownLatch saveEntered;
        private CountDownLatch releaseSave;

        @Override
        public void load() {
            loadCount.incrementAndGet();
            if (loadFailure != null) {
                throw loadFailure;
            }
            if (loadError != null) {
                throw loadError;
            }
        }

        @Override
        public void save() {
            saveCount.incrementAndGet();
            if (saveEntered != null) {
                saveEntered.countDown();
            }
            if (releaseSave != null) {
                try {
                    if (!releaseSave.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out waiting to release the test save");
                    }
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("test save interrupted", interrupted);
                }
            }
            if (saveFailure != null) {
                throw saveFailure;
            }
        }
    }
}
