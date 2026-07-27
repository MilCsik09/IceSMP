package hu.taliann.icesmp.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Owns the common persistent-store lifecycle.
 *
 * <p>Loading is fail-closed: the first runtime failure aborts the startup attempt and no partial
 * store set becomes save-eligible. Saving is available only after every registered store loaded
 * successfully. Runtime save failures are reported individually so one ordinary store failure does
 * not hide later failures; {@link Error}s such as {@link CriticalPersistenceWriteError} still
 * propagate and retain their process-stopping semantics.</p>
 */
public final class PersistentStoreCoordinator {

    private enum State {
        NEW,
        LOADING,
        READY,
        SHUTTING_DOWN,
        STOPPED,
        FAILED
    }

    private final List<PersistentStore> stores;
    private final Set<PersistentStore> loadedStores =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private State state = State.NEW;

    public PersistentStoreCoordinator(final List<? extends PersistentStore> stores) {
        Objects.requireNonNull(stores, "stores");
        final List<PersistentStore> copy = new ArrayList<>(stores.size());
        final Set<PersistentStore> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int index = 0; index < stores.size(); index++) {
            final PersistentStore store = Objects.requireNonNull(stores.get(index),
                    "stores[" + index + "]");
            if (!identities.add(store)) {
                throw new IllegalArgumentException("PersistentStore instance registered more than once: "
                        + store.getClass().getName());
            }
            copy.add(store);
        }
        this.stores = List.copyOf(copy);
    }

    /**
     * Loads every registered store exactly once for this coordinator.
     *
     * @throws StoreLoadException when a store throws a {@link RuntimeException}
     * @throws Error unchanged when a store raises a fatal persistence error
     */
    public synchronized void loadAll() {
        if (state != State.NEW) {
            throw new IllegalStateException("Persistent stores already have a startup result: " + state);
        }
        state = State.LOADING;
        for (final PersistentStore store : stores) {
            try {
                store.load();
                loadedStores.add(store);
            } catch (final RuntimeException failure) {
                state = State.FAILED;
                throw new StoreLoadException(store, failure);
            } catch (final Error failure) {
                state = State.FAILED;
                throw failure;
            }
        }
        state = State.READY;
    }

    /**
     * Saves every store after a fully successful load. Calls are serialized so autosave and
     * shutdown cannot write overlapping snapshots through this common lifecycle.
     */
    public synchronized boolean saveAll(final Consumer<SaveFailure> failureHandler) {
        Objects.requireNonNull(failureHandler, "failureHandler");
        if (state != State.READY || loadedStores.size() != stores.size()) {
            return false;
        }
        saveStores(failureHandler);
        return true;
    }

    /**
     * Atomically closes the autosave gate before stateful manager shutdown starts. This call waits
     * for an already-running common save to finish; later autosaves observe a non-ready state.
     */
    public synchronized boolean beginShutdown() {
        if (state != State.READY || loadedStores.size() != stores.size()) {
            return false;
        }
        state = State.SHUTTING_DOWN;
        return true;
    }

    /** Saves the final snapshots after the stateful shutdown hooks have completed. */
    public synchronized void saveForShutdown(final Consumer<SaveFailure> failureHandler) {
        Objects.requireNonNull(failureHandler, "failureHandler");
        if (state != State.SHUTTING_DOWN || loadedStores.size() != stores.size()) {
            throw new IllegalStateException("Refusing shutdown save in persistent-store state: " + state);
        }
        saveStores(failureHandler);
        state = State.STOPPED;
    }

    private void saveStores(final Consumer<SaveFailure> failureHandler) {
        for (final PersistentStore store : stores) {
            try {
                store.save();
            } catch (final RuntimeException failure) {
                failureHandler.accept(new SaveFailure(store, failure));
            }
        }
    }

    /** Returns whether every registered store completed its startup load and autosave is open. */
    public synchronized boolean isReady() {
        return state == State.READY && loadedStores.size() == stores.size();
    }

    /** Visible for lifecycle diagnostics and dependency-free regression tests. */
    synchronized int loadedStoreCount() {
        return loadedStores.size();
    }

    public record SaveFailure(PersistentStore store, RuntimeException cause) {
        public SaveFailure {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(cause, "cause");
        }
    }

    public static final class StoreLoadException extends RuntimeException {
        private final PersistentStore store;

        private StoreLoadException(final PersistentStore store, final RuntimeException cause) {
            super("Persistent store failed to load: " + store.getClass().getName(), cause);
            this.store = store;
        }

        public PersistentStore store() {
            return store;
        }
    }
}
