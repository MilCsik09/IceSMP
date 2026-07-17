package hu.taliann.icesmp.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Read-only {@code /invsee} snapshot holder (IDEAS C12, InvSee++ kiváltás első lépcsője): a
 * célpont fő inventoryjáról/páncéljáról/off-handjéről/ender-ládájáról a CÉLPONT szálán készült
 * {@link ItemStack#clone()} pillanatkép — NEM élő nézet, a GUI bezárása után eldobódik. A holder
 * hordozza mindkét aloldalhoz (fő nézet + ender-láda nézet) szükséges adatot, hogy a "vissza"/
 * "ender-láda" gomb új entitás-hozzáférés (és újabb Folia scheduler-hop) nélkül válthasson nézetet.
 */
public final class InvseeHolder implements InventoryHolder {

    /** Which aloldal is currently rendered from this holder's snapshot. */
    public enum View { MAIN, ENDER }

    private final String targetName;
    private final ItemStack[] mainSnapshot;
    private final ItemStack[] armorSnapshot;
    private final ItemStack offHandSnapshot;
    private final ItemStack[] enderSnapshot;
    /** {@link System#currentTimeMillis()} at which this snapshot was captured (A55 — refresh button/age display). */
    private final long snapshotAtMillis;
    private final View view;
    private Inventory inventory;

    public InvseeHolder(final String targetName, final ItemStack[] mainSnapshot, final ItemStack[] armorSnapshot,
                        final ItemStack offHandSnapshot, final ItemStack[] enderSnapshot, final long snapshotAtMillis,
                        final View view) {
        this.targetName = targetName;
        this.mainSnapshot = mainSnapshot;
        this.armorSnapshot = armorSnapshot;
        this.offHandSnapshot = offHandSnapshot;
        this.enderSnapshot = enderSnapshot;
        this.snapshotAtMillis = snapshotAtMillis;
        this.view = view;
    }

    public String getTargetName() {
        return targetName;
    }

    public ItemStack[] getMainSnapshot() {
        return mainSnapshot;
    }

    public ItemStack[] getArmorSnapshot() {
        return armorSnapshot;
    }

    public ItemStack getOffHandSnapshot() {
        return offHandSnapshot;
    }

    public ItemStack[] getEnderSnapshot() {
        return enderSnapshot;
    }

    public long getSnapshotAtMillis() {
        return snapshotAtMillis;
    }

    public View getView() {
        return view;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 9) : inventory;
    }
}
