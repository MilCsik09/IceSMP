package hu.taliann.icesmp.gui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe staged config edit transaction. Inventory clicks normally run on the player's
 * region thread, while private chat input arrives asynchronously; synchronization prevents a
 * chat commit, GUI reset and SAVE snapshot from racing each other.
 */
public final class ConfigEditSession {
    private final long expectedGeneration;
    private final String expectedFingerprint;
    private final Map<String, Object> openingValues;
    private final Map<String, Object> defaults;
    private final Map<String, Object> pending = new LinkedHashMap<>();

    public ConfigEditSession(final long expectedGeneration, final String expectedFingerprint,
                             final Map<String, Object> openingValues, final Map<String, Object> defaults) {
        this.expectedGeneration = expectedGeneration;
        this.expectedFingerprint = expectedFingerprint == null ? "" : expectedFingerprint;
        this.openingValues = Collections.unmodifiableMap(new LinkedHashMap<>(openingValues));
        this.defaults = Collections.unmodifiableMap(new LinkedHashMap<>(defaults));
    }

    public long expectedGeneration() { return expectedGeneration; }
    public String expectedFingerprint() { return expectedFingerprint; }

    public synchronized boolean dirty() { return !pending.isEmpty(); }
    public synchronized boolean hasPending(final String key) { return pending.containsKey(key); }
    public synchronized Set<String> changedKeys() { return Set.copyOf(pending.keySet()); }

    public synchronized Object value(final String key) {
        if (pending.containsKey(key)) {
            final Object staged = pending.get(key);
            return staged == null ? defaults.get(key) : staged;
        }
        return openingValues.get(key);
    }

    public Object defaultValue(final String key) { return defaults.get(key); }

    public synchronized void stage(final String key, final Object value) {
        pending.put(key, value);
    }

    /** Null means remove the override so the packaged/subsystem default becomes authoritative. */
    public synchronized void reset(final String key) {
        pending.put(key, null);
    }

    public synchronized Map<String, Object> pendingChanges() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(pending));
    }
}
