package hu.taliann.icesmp.pve;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Bounded aggregate telemetry seam for future log-based combat tuning; no player data is kept. */
public final class CombatTelemetry {
    private static final int MAX_KEYS = 512;
    private static final ConcurrentHashMap<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();

    private CombatTelemetry() { }

    public static void record(final String category, final String id) {
        final String key = normalize(category) + ':' + normalize(id);
        LongAdder counter = COUNTERS.get(key);
        if (counter == null) {
            synchronized (COUNTERS) {
                counter = COUNTERS.get(key);
                if (counter == null) {
                    if (COUNTERS.size() >= MAX_KEYS) return;
                    counter = new LongAdder();
                    COUNTERS.put(key, counter);
                }
            }
        }
        counter.increment();
    }

    public static Map<String, Long> snapshot() {
        final java.util.TreeMap<String, Long> result = new java.util.TreeMap<>();
        COUNTERS.forEach((key, value) -> result.put(key, value.sum()));
        return Map.copyOf(result);
    }

    public static void clear() {
        COUNTERS.clear();
    }

    private static String normalize(final String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        final String value = raw.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        return value.substring(0, Math.min(64, value.length()));
    }
}
