package hu.taliann.icesmp.itemization;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;

/**
 * All-or-nothing cursor-to-storage transfer used before a durable item mutation.
 *
 * <p>Bukkit's {@code Inventory.addItem} may partially merge a stack before returning leftovers.
 * This boundary therefore preflights capacity and also keeps an exact storage/cursor snapshot so
 * an unexpected partial add or persistence failure is rolled back without materializing items.</p>
 */
public final class AtomicCursorRehome {

    public interface Adapter {
        ItemStack[] storageContents();
        Map<Integer, ItemStack> add(ItemStack stack);
        void restoreStorage(ItemStack[] snapshot);
        ItemStack cursor();
        void setCursor(ItemStack cursor);
        void persist();
    }

    private AtomicCursorRehome() { }

    public static boolean rehome(final Adapter adapter, final ItemStack carried) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(carried, "carried");
        if (carried.getType().isAir() || carried.getAmount() <= 0) return false;

        final ItemStack[] beforeStorage = cloneContents(adapter.storageContents());
        final ItemStack beforeCursor = cloneOrNull(adapter.cursor());
        if (!canFitAll(beforeStorage, carried)) return false;

        final Map<Integer, ItemStack> leftovers;
        try {
            leftovers = adapter.add(carried.clone());
        } catch (final RuntimeException addFailure) {
            rollbackMemory(adapter, beforeStorage, beforeCursor, addFailure);
            return false;
        }
        if (leftovers != null && !leftovers.isEmpty()) {
            rollbackMemory(adapter, beforeStorage, beforeCursor, null);
            return false;
        }

        adapter.setCursor(null);
        try {
            adapter.persist();
            return true;
        } catch (final RuntimeException persistenceFailure) {
            rollbackMemory(adapter, beforeStorage, beforeCursor, persistenceFailure);
            try {
                adapter.persist();
            } catch (final RuntimeException rollbackPersistenceFailure) {
                persistenceFailure.addSuppressed(rollbackPersistenceFailure);
            }
            return false;
        }
    }

    static boolean canFitAll(final ItemStack[] storage, final ItemStack incoming) {
        if (storage == null || incoming == null || incoming.getType().isAir()) return false;
        int capacity = 0;
        for (final ItemStack current : storage) {
            if (current == null || current.getType().isAir()) {
                capacity += incoming.getMaxStackSize();
            } else if (current.isSimilar(incoming) && current.getAmount() < current.getMaxStackSize()) {
                capacity += current.getMaxStackSize() - current.getAmount();
            }
            if (capacity >= incoming.getAmount()) return true;
        }
        return false;
    }

    static ItemStack[] cloneContents(final ItemStack[] source) {
        if (source == null) return new ItemStack[0];
        final ItemStack[] result = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = cloneOrNull(source[index]);
        }
        return result;
    }

    private static void rollbackMemory(final Adapter adapter, final ItemStack[] storage,
                                       final ItemStack cursor, final RuntimeException primary) {
        try {
            adapter.restoreStorage(cloneContents(storage));
            adapter.setCursor(cloneOrNull(cursor));
        } catch (final RuntimeException rollbackFailure) {
            if (primary != null) rollbackFailure.addSuppressed(primary);
            throw new IllegalStateException("Atomic cursor rehome rollback failed", rollbackFailure);
        }
    }

    private static ItemStack cloneOrNull(final ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }
}
