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

    /**
     * Bukkit-independent transactional execution seam. Production {@link #rehome(Adapter, ItemStack)}
     * delegates to this exact algorithm; regression fixtures may exercise partial-commit and
     * persistence-failure behavior without bootstrapping a Paper registry.
     */
    interface AtomicStep {
        boolean preflight();
        boolean addAll();
        void clearCursor();
        void restoreMemory();
        void persist();
    }

    private AtomicCursorRehome() { }

    public static boolean rehome(final Adapter adapter, final ItemStack carried) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(carried, "carried");
        if (carried.getType().isAir() || carried.getAmount() <= 0) return false;

        final ItemStack[] beforeStorage = cloneContents(adapter.storageContents());
        final ItemStack beforeCursor = cloneOrNull(adapter.cursor());
        return executeAtomic(new AtomicStep() {
            @Override
            public boolean preflight() {
                return canFitAll(beforeStorage, carried);
            }

            @Override
            public boolean addAll() {
                final Map<Integer, ItemStack> leftovers = adapter.add(carried.clone());
                return leftovers == null || leftovers.isEmpty();
            }

            @Override
            public void clearCursor() {
                adapter.setCursor(null);
            }

            @Override
            public void restoreMemory() {
                adapter.restoreStorage(cloneContents(beforeStorage));
                adapter.setCursor(cloneOrNull(beforeCursor));
            }

            @Override
            public void persist() {
                adapter.persist();
            }
        });
    }

    static boolean executeAtomic(final AtomicStep step) {
        Objects.requireNonNull(step, "step");
        if (!step.preflight()) return false;

        try {
            if (!step.addAll()) {
                restore(step, null);
                return false;
            }
            step.clearCursor();
        } catch (final RuntimeException mutationFailure) {
            restore(step, mutationFailure);
            return false;
        }

        try {
            step.persist();
            return true;
        } catch (final RuntimeException persistenceFailure) {
            restore(step, persistenceFailure);
            try {
                step.persist();
            } catch (final RuntimeException rollbackPersistenceFailure) {
                persistenceFailure.addSuppressed(rollbackPersistenceFailure);
            }
            return false;
        }
    }

    static boolean canFitAll(final ItemStack[] storage, final ItemStack incoming) {
        if (storage == null || incoming == null || incoming.getType().isAir()) return false;
        int mergeCapacity = 0;
        int emptySlots = 0;
        for (final ItemStack current : storage) {
            if (current == null || current.getType().isAir()) {
                emptySlots++;
            } else if (current.isSimilar(incoming) && current.getAmount() < current.getMaxStackSize()) {
                mergeCapacity += current.getMaxStackSize() - current.getAmount();
            }
        }
        return hasCapacity(incoming.getAmount(), mergeCapacity, emptySlots,
                Math.max(1, incoming.getMaxStackSize()));
    }

    static boolean hasCapacity(final int incomingAmount, final int mergeCapacity,
                               final int emptySlots, final int maxStackSize) {
        if (incomingAmount <= 0 || mergeCapacity < 0 || emptySlots < 0 || maxStackSize <= 0) {
            return false;
        }
        final long capacity = (long) mergeCapacity + (long) emptySlots * maxStackSize;
        return capacity >= incomingAmount;
    }

    static ItemStack[] cloneContents(final ItemStack[] source) {
        if (source == null) return new ItemStack[0];
        final ItemStack[] result = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = cloneOrNull(source[index]);
        }
        return result;
    }

    private static void restore(final AtomicStep step, final RuntimeException primary) {
        try {
            step.restoreMemory();
        } catch (final RuntimeException rollbackFailure) {
            if (primary != null) rollbackFailure.addSuppressed(primary);
            throw new IllegalStateException("Atomic cursor rehome rollback failed", rollbackFailure);
        }
    }

    private static ItemStack cloneOrNull(final ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }
}
