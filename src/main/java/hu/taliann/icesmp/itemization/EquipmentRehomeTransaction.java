package hu.taliann.icesmp.itemization;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;

/** Lossless equipped-slot to player-storage transfer after an equip mutation has settled. */
public final class EquipmentRehomeTransaction {

    public interface Adapter {
        ItemStack equipped();
        ItemStack[] storageContents();
        void setEquipped(ItemStack item);
        Map<Integer, ItemStack> addToStorage(ItemStack item);
        void restoreStorage(ItemStack[] snapshot);
    }

    interface AtomicStep {
        boolean preflight();
        void clearEquipped();
        boolean storeAll();
        void rollback();
    }

    private EquipmentRehomeTransaction() { }

    public static boolean rehome(final Adapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        final ItemStack equipped = adapter.equipped();
        if (equipped == null || equipped.getType().isAir() || equipped.getAmount() <= 0) {
            return false;
        }
        final ItemStack equippedSnapshot = equipped.clone();
        final ItemStack[] storageSnapshot = AtomicCursorRehome.cloneContents(
                adapter.storageContents());
        return executeAtomic(new AtomicStep() {
            @Override
            public boolean preflight() {
                return AtomicCursorRehome.canFitAll(storageSnapshot, equippedSnapshot);
            }

            @Override
            public void clearEquipped() {
                adapter.setEquipped(null);
            }

            @Override
            public boolean storeAll() {
                final Map<Integer, ItemStack> leftovers =
                        adapter.addToStorage(equippedSnapshot.clone());
                return leftovers == null || leftovers.isEmpty();
            }

            @Override
            public void rollback() {
                adapter.restoreStorage(AtomicCursorRehome.cloneContents(storageSnapshot));
                adapter.setEquipped(equippedSnapshot.clone());
            }
        });
    }

    static boolean executeAtomic(final AtomicStep step) {
        Objects.requireNonNull(step, "step");
        if (!step.preflight()) return false;
        try {
            step.clearEquipped();
            if (step.storeAll()) return true;
        } catch (final RuntimeException ignored) {
            // Exact rollback below is the item-conservation boundary.
        }
        step.rollback();
        return false;
    }
}
