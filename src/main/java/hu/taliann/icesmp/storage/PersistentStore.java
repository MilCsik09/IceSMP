package hu.taliann.icesmp.storage;

/**
 * A manager whose state is persisted to a data file. The plugin core keeps one registered list of
 * these behind a {@link PersistentStoreCoordinator}, which fail-closed loads the full set on enable
 * and serializes common autosave/shutdown writes. Adding a new persistent manager therefore means
 * implementing this interface and adding it to that single list, instead of editing independent
 * load/save call sites.
 */
public interface PersistentStore {

    /** Loads this store's state from disk (called once on plugin enable). */
    void load();

    /** Persists this store's state to disk (called on plugin disable). */
    void save();
}
