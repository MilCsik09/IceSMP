package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.dev.artifact.ArtifactStateValue;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Explicit type tags and sorted map keys avoid locale, map order and Java-serialization identities. */
public final class CanonicalValueBytes {
    private CanonicalValueBytes() {}
    public static byte[] encode(final Map<String, Object> payload) {
        final Map<String, Object> safe = ArtifactStateValue.freeze(payload);
        try {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final DataOutputStream data = new DataOutputStream(bytes);
            write(data, safe);
            data.flush();
            return bytes.toByteArray();
        } catch (final IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static Map<String, Object> decode(final byte[] bytes) {
        if (bytes.length > 1_048_576) throw new IllegalArgumentException("Canonical payload exceeds bounds");
        try {
            final java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));
            final Object value = read(in, 0, new int[]{0});
            if (!(value instanceof Map<?, ?> map) || in.available() != 0) throw new IllegalArgumentException("Invalid canonical root");
            final Map<String, Object> result = new TreeMap<>();
            map.forEach((key, item) -> result.put((String) key, item));
            final Map<String, Object> frozen = ArtifactStateValue.freeze(result);
            if (!java.util.Arrays.equals(bytes, encode(frozen))) throw new IllegalArgumentException("Noncanonical encoding");
            return frozen;
        } catch (final IOException malformed) { throw new IllegalArgumentException("Truncated canonical payload", malformed); }
    }
    private static String readText(final java.io.DataInputStream in) throws IOException {
        final int size = in.readInt();
        if (size < 0 || size > 1_048_576 || size > in.available()) throw new IllegalArgumentException("Invalid canonical string length");
        return new String(in.readNBytes(size), StandardCharsets.UTF_8);
    }
    private static Object read(final java.io.DataInputStream in, final int depth, final int[] count) throws IOException {
        if (depth > 16 || ++count[0] > 4096) throw new IllegalArgumentException("Canonical payload exceeds nesting/count limit");
        final int tag = in.readUnsignedByte();
        return switch (tag) {
            case 1 -> readText(in);
            case 2 -> in.readBoolean();
            case 3 -> in.readLong();
            case 4 -> Double.longBitsToDouble(in.readLong());
            case 5, 6 -> {
                final int size = in.readInt();
                if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid canonical container length");
                if (tag == 5) {
                    final java.util.ArrayList<Object> items = new java.util.ArrayList<>();
                    for (int i = 0; i < size; i++) items.add(read(in, depth + 1, count));
                    yield List.copyOf(items);
                }
                final Map<String, Object> items = new TreeMap<>();
                for (int i = 0; i < size; i++) {
                    final String key = readText(in);
                    if (items.putIfAbsent(key, read(in, depth + 1, count)) != null) throw new IllegalArgumentException("Duplicate canonical map key");
                }
                yield Map.copyOf(items);
            }
            default -> throw new IllegalArgumentException("Unknown canonical type tag");
        };
    }
    private static void text(final DataOutputStream out, final String text) throws IOException {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static void write(final DataOutputStream out, final Object value) throws IOException {
        if (value instanceof String s) { out.writeByte(1); text(out, s); }
        else if (value instanceof Boolean b) { out.writeByte(2); out.writeBoolean(b); }
        else if (value instanceof Integer n) { out.writeByte(3); out.writeLong(n.longValue()); }
        else if (value instanceof Long n) { out.writeByte(3); out.writeLong(n); }
        else if (value instanceof Double n) { out.writeByte(4); out.writeLong(Double.doubleToLongBits(n)); }
        else if (value instanceof List<?> values) {
            out.writeByte(5); out.writeInt(values.size());
            for (final Object item : values) write(out, item);
        } else if (value instanceof Map<?, ?> values) {
            final Map<String, Object> sorted = new TreeMap<>();
            values.forEach((key, item) -> sorted.put((String) key, item));
            out.writeByte(6); out.writeInt(sorted.size());
            for (final var item : sorted.entrySet()) { text(out, item.getKey()); write(out, item.getValue()); }
        } else throw new IllegalArgumentException("Unsupported canonical value");
    }
}
