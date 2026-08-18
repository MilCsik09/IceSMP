package hu.taliann.icesmp.progression;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Durable block-origin authority for profession/gathering rewards. */
public final class BlockRewardOriginTracker {
    private static final NamespacedKey PLACED = NamespacedKey.fromString("icesmp:reward_origin_placed");
    private static final NamespacedKey SYNTHETIC = NamespacedKey.fromString("icesmp:reward_origin_synthetic");
    private static final int MIN_TRACKED_Y = -2048;
    private static final int MAX_TRACKED_Y = 2047;

    private BlockRewardOriginTracker() { }

    public static boolean isRewardEligible(final Block block) {
        if (block == null || !trackable(block)) return false;
        final int packed = pack(block);
        final PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        return !containsOrCorrupt(pdc.get(PLACED, PersistentDataType.BYTE_ARRAY), packed)
                && !containsOrCorrupt(pdc.get(SYNTHETIC, PersistentDataType.BYTE_ARRAY), packed);
    }

    public static void markPlayerPlaced(final Block block) {
        mark(block, PLACED);
    }

    public static void markSynthetic(final Block block) {
        mark(block, SYNTHETIC);
    }

    /**
     * Delay cleanup until every MONITOR block-break consumer observed the same durable pre-state.
     * Both placed and regenerated provenance are one physical-block witnesses: once that block was
     * successfully consumed, the coordinate may become natural again. A later regeneration marks
     * its replacement synthetic again.
     */
    public static void clearAfterBreak(final Block block) {
        if (block == null || !trackable(block)) return;
        final int x = block.getX();
        final int y = block.getY();
        final int z = block.getZ();
        final JavaPlugin plugin = JavaPlugin.getProvidingPlugin(BlockRewardOriginTracker.class);
        block.getWorld().getRegionScheduler().runDelayed(plugin, block.getLocation(), task -> {
            final Block current = block.getWorld().getBlockAt(x, y, z);
            remove(current, PLACED);
            remove(current, SYNTHETIC);
        }, 1L);
    }

    public static boolean isSynthetic(final Block block) {
        if (block == null || !trackable(block)) return true;
        return containsOrCorrupt(block.getChunk().getPersistentDataContainer()
                .get(SYNTHETIC, PersistentDataType.BYTE_ARRAY), pack(block));
    }

    private static void mark(final Block block, final NamespacedKey key) {
        if (block == null || !trackable(block)) return;
        final PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        final byte[] encoded = pdc.get(key, PersistentDataType.BYTE_ARRAY);
        if (corrupt(encoded)) return;
        final int packed = pack(block);
        final int[] values = decode(encoded);
        if (Arrays.binarySearch(values, packed) >= 0) return;
        final int[] next = Arrays.copyOf(values, values.length + 1);
        next[next.length - 1] = packed;
        Arrays.sort(next);
        pdc.set(key, PersistentDataType.BYTE_ARRAY, encode(next));
    }

    private static void remove(final Block block, final NamespacedKey key) {
        if (block == null || !trackable(block)) return;
        final PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        final byte[] encoded = pdc.get(key, PersistentDataType.BYTE_ARRAY);
        if (corrupt(encoded)) return;
        final int packed = pack(block);
        final int[] values = decode(encoded);
        final int index = Arrays.binarySearch(values, packed);
        if (index < 0) return;
        if (values.length == 1) {
            pdc.remove(key);
            return;
        }
        final int[] next = new int[values.length - 1];
        System.arraycopy(values, 0, next, 0, index);
        System.arraycopy(values, index + 1, next, index, values.length - index - 1);
        pdc.set(key, PersistentDataType.BYTE_ARRAY, encode(next));
    }

    private static boolean containsOrCorrupt(final byte[] bytes, final int packed) {
        return corrupt(bytes) || Arrays.binarySearch(decode(bytes), packed) >= 0;
    }

    private static boolean corrupt(final byte[] bytes) {
        return bytes != null && bytes.length % Integer.BYTES != 0;
    }

    private static int[] decode(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new int[0];
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final int[] result = new int[bytes.length / Integer.BYTES];
        for (int i = 0; i < result.length; i++) result[i] = buffer.getInt();
        Arrays.sort(result);
        return result;
    }

    private static byte[] encode(final int[] values) {
        final ByteBuffer buffer = ByteBuffer.allocate(values.length * Integer.BYTES);
        for (final int value : values) buffer.putInt(value);
        return buffer.array();
    }

    private static boolean trackable(final Block block) {
        return block.getY() >= MIN_TRACKED_Y && block.getY() <= MAX_TRACKED_Y;
    }

    private static int pack(final Block block) {
        return ((block.getY() - MIN_TRACKED_Y) << 8)
                | ((block.getZ() & 15) << 4) | (block.getX() & 15);
    }
}
