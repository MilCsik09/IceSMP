package hu.taliann.icesmp.dev.artifact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps storage snapshots independent of live objects and caller-owned collections. */
public final class ArtifactStateValue {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_VALUES = 4096;

    private ArtifactStateValue() {}

    public static Map<String, Object> freeze(final Map<String, Object> value) {
        return freezeMap(value, 0, new int[]{0, 0});
    }

    private static Map<String, Object> freezeMap(final Map<?, ?> value, final int depth, final int[] count) {
        final Map<String, Object> copy = new LinkedHashMap<>();
        for (final var entry : value.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isEmpty() || key.length() > 128) {
                throw new IllegalArgumentException("Invalid artifact state key");
            }
            copy.put(key, freezeValue(entry.getValue(), depth + 1, count));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object freezeValue(final Object value, final int depth, final int[] count) {
        if (depth > MAX_DEPTH || ++count[0] > MAX_VALUES) {
            throw new IllegalArgumentException("Artifact behavior state exceeds bounds");
        }
        if (value instanceof String text) {
            count[1] = Math.addExact(count[1], text.length());
            if (count[1] > 1_048_576) throw new IllegalArgumentException("Artifact state text exceeds bounds");
            return text;
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) return value;
        if (value instanceof Double number && Double.isFinite(number)) return number;
        if (value instanceof Map<?, ?> map) return freezeMap(map, depth, count);
        if (value instanceof List<?> list) {
            return list.stream().map(item -> freezeValue(item, depth + 1, count)).toList();
        }
        throw new IllegalArgumentException("Artifact behavior state is not YAML-safe");
    }
}
