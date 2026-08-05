package hu.taliann.icesmp.gui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pure staged config edit transaction; no click mutates disk until SAVE. */
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
    public boolean dirty() { return !pending.isEmpty(); }
    public boolean hasPending(final String key) { return pending.containsKey(key); }
    public Set<String> changedKeys() { return Set.copyOf(pending.keySet()); }

    public Object value(final String key) {
        if (pending.containsKey(key)) {
            final Object staged = pending.get(key);
            return staged == null ? defaults.get(key) : staged;
        }
        return openingValues.get(key);
    }

    public Object defaultValue(final String key) { return defaults.get(key); }
    public void stage(final String key, final Object value) { pending.put(key, value); }
    /** Null means remove the override so the packaged/subsystem default becomes authoritative. */
    public void reset(final String key) { pending.put(key, null); }

    public Map<String, Object> pendingChanges() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(pending));
    }
}
