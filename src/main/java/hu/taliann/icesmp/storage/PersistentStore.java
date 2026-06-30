package hu.taliann.icesmp.storage;

/**
 * A manager whose state is persisted to a data file. The plugin core keeps one registered list of
 * these and iterates it to {@link #load()} every store on enable and {@link #save()} every store on
 * disable — so adding a new persistent manager means implementing this interface and adding it to
 * that single list, instead of editing two hand-maintained call lists.
 */
public interface PersistentStore {

    /** Loads this store's state from disk (called once on plugin enable). */
    void load();

    /** Persists this store's state to disk (called on plugin disable). */
    void save();
}
