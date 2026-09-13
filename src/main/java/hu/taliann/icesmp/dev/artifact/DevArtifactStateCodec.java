package hu.taliann.icesmp.dev.artifact;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class DevArtifactStateCodec {
    private DevArtifactStateCodec() {}

    public static Map<String, Object> encode(final Map<String, DevArtifactState> states) {
        final Map<String, Object> artifacts = new LinkedHashMap<>();
        states.forEach((id, state) -> artifacts.put(id, Map.of(
                "owner", state.owner().toString(), "instance", state.instanceId().toString(),
                "issued", state.issued(), "revision", state.revision(), "behavior-state", state.behaviorState())));
        return Map.of("schema-version", 2, "artifacts", artifacts);
    }

    public static Map<String, DevArtifactState> decode(final Map<String, Object> root,
                                                       final Function<Object, String> legacyItemEncoder) {
        if (!root.containsKey("schema-version")) return migrateLegacy(root, legacyItemEncoder);
        if (integer(root, "schema-version") != 2) throw new IllegalArgumentException("Unsupported DEV artifact schema");
        final Map<String, Object> artifacts = map(root, "artifacts");
        if (artifacts.isEmpty() || artifacts.size() > 32) throw new IllegalArgumentException("Invalid artifact state count");
        final Map<String, DevArtifactState> result = new LinkedHashMap<>();
        for (final String id : artifacts.keySet()) {
            if (!id.matches("[a-z0-9_]{1,64}")) throw new IllegalArgumentException("Invalid artifact state id");
            final Map<String, Object> state = map(artifacts, id);
            result.put(id, new DevArtifactState(uuid(state, "owner"), uuid(state, "instance"),
                    bool(state, "issued"), state.containsKey("revision") ? integer(state, "revision") : 0L,
                    map(state, "behavior-state")));
        }
        return Map.copyOf(result);
    }

    private static Map<String, DevArtifactState> migrateLegacy(final Map<String, Object> root,
                                                               final Function<Object, String> itemEncoder) {
        if (root.size() != 1 || !root.containsKey("bingulus")) throw new IllegalArgumentException("Invalid legacy DEV state");
        final Map<String, Object> legacy = map(root, "bingulus");
        final UUID owner = uuid(legacy, "owner");
        final UUID instance = uuid(legacy, "instance");
        final boolean issued = bool(legacy, "issued");
        final long progress = integer(legacy, "progress-millis");
        final Map<String, Object> pending = map(legacy, "pending");
        final Map<String, Object> pity = map(legacy, "pity");
        final String rarity = string(pending, "rarity");
        final String entry = string(pending, "entry");
        final Object item = pending.get("item");
        if (progress < 0 || rarity.isBlank() != entry.isBlank() || rarity.isBlank() != (item == null)) {
            throw new IllegalArgumentException("Invalid legacy DEV progress/pending reward");
        }
        final Map<String, Object> behavior = new LinkedHashMap<>();
        behavior.put("progress-millis", progress);
        final Map<String, Object> migratedPity = new LinkedHashMap<>();
        boolean zeroPity = true;
        for (final String key : new String[]{"since-rare", "since-epic", "since-legendary"}) {
            final long value = integer(pity, key);
            if (value < 0 || value > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid legacy DEV pity");
            migratedPity.put(key, (int) value);
            zeroPity &= value == 0;
        }
        behavior.put("pity", migratedPity);
        if (item != null) {
            if (!java.util.List.of("kozonseges", "nem_mindennapi", "ritka", "epikus", "legendas", "ereklye").contains(rarity)) {
                throw new IllegalArgumentException("Unknown legacy DEV reward rarity");
            }
            final String encoded = itemEncoder.apply(item);
            if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("Invalid exact legacy reward item");
            behavior.put("pending", Map.of("rarity", rarity, "entry", entry, "item", encoded));
        }
        if (!issued && (progress != 0 || item != null || !zeroPity)) {
            throw new IllegalArgumentException("Unissued legacy artifact contains reward progress");
        }
        return Map.of("csodalatos_bingulus", new DevArtifactState(owner, instance, issued, 0, behavior));
    }

    public static Map<String, Object> map(final Map<String, Object> parent, final String key) {
        if (!(parent.get(key) instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Missing artifact state map: " + key);
        final Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((name, value) -> {
            if (!(name instanceof String text)) throw new IllegalArgumentException("Non-string artifact state key");
            result.put(text, value);
        });
        return result;
    }

    public static long integer(final Map<String, Object> parent, final String key) {
        final Object value = parent.get(key);
        if (value instanceof Integer number) return number.longValue();
        if (value instanceof Long number) return number;
        throw new IllegalArgumentException("Missing artifact state integer: " + key);
    }

    public static String string(final Map<String, Object> parent, final String key) {
        if (parent.get(key) instanceof String value) return value;
        throw new IllegalArgumentException("Missing artifact state string: " + key);
    }

    private static UUID uuid(final Map<String, Object> parent, final String key) {
        return UUID.fromString(string(parent, key).trim());
    }

    private static boolean bool(final Map<String, Object> parent, final String key) {
        if (parent.get(key) instanceof Boolean value) return value;
        throw new IllegalArgumentException("Missing artifact state boolean: " + key);
    }
}
