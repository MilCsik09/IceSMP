package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Read-only inventory-betekintő (InvSee++ kiváltás első lépcsője): a
 * {@code /invsee <név>} által a CÉLPONT szálán készített pillanatképet (36 fő + 4 páncél +
 * off-hand + 27 ender-láda) jeleníti meg a NÉZŐ szálán nyitott GUI-ban. MINDEN kattintás
 * cancel-elve ({@link hu.taliann.icesmp.listeners.InvseeGUIListener}) — ez kizárólag
 * megtekintésre szolgál, tárgy nem mozgatható/vehető el. Két aloldal (fő + ender-láda), mindkettő
 * ugyanabból a snapshotból épül újra, tehát váltáskor nincs újabb entitás-hozzáférés/scheduler-hop.
 */
public final class InvseeGUI {

    private static final int MAIN_SIZE = 54;
    private static final int ENDER_SIZE = 36;
    /** Fő nézet: páncél (csizma/lábvért/mellvért/sisak) + off-hand slotjai az elválasztó sorban. */
    private static final int[] ARMOR_OFFHAND_SLOTS = {36, 37, 38, 39, 40};
    private static final String[] ARMOR_OFFHAND_LABELS = {"Csizma", "Lábvért", "Mellvért", "Sisak", "Balkéz (off-hand)"};

    public static final int ENDER_BUTTON_SLOT = 49;
    public static final int BACK_SLOT = 31;
    public static final int REFRESH_SLOT = 45;

    private InvseeGUI() {
    }

    /** Reopens the main view from an existing holder's snapshot (no new item access). */
    public static void openMain(final Player viewer, final InvseeHolder source, final MessageManager messageManager) {
        openMain(viewer, source.getTargetName(), source.getMainSnapshot(), source.getArmorSnapshot(),
                source.getOffHandSnapshot(), source.getEnderSnapshot(), source.getSnapshotAtMillis(), messageManager);
    }

    public static void openMain(final Player viewer, final String targetName, final ItemStack[] mainSnapshot,
                                final ItemStack[] armorSnapshot, final ItemStack offHandSnapshot,
                                final ItemStack[] enderSnapshot, final long snapshotAtMillis,
                                final MessageManager messageManager) {
        final Component title = messageManager.getComponent("admin.icesmp.invsee.title-main",
                "&6» %s inventoryja (csak olvasás%s) «", targetName, ageSuffix(snapshotAtMillis));
        final InvseeHolder holder = new InvseeHolder(targetName, mainSnapshot, armorSnapshot, offHandSnapshot,
                enderSnapshot, snapshotAtMillis, InvseeHolder.View.MAIN);
        final Inventory inventory = Bukkit.createInventory(holder, MAIN_SIZE, title);
        holder.setInventory(inventory);

        for (int slot = 0; slot < 36 && mainSnapshot != null && slot < mainSnapshot.length; slot++) {
            inventory.setItem(slot, mainSnapshot[slot]);
        }

        for (int slot = 36; slot < 45; slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }
        for (int i = 0; i < ARMOR_OFFHAND_SLOTS.length; i++) {
            final ItemStack piece = resolveArmorOffhandPiece(i, armorSnapshot, offHandSnapshot);
            inventory.setItem(ARMOR_OFFHAND_SLOTS[i], piece != null && !piece.getType().isAir()
                    ? piece : emptySlotMarker(ARMOR_OFFHAND_LABELS[i]));
        }

        for (int slot = 45; slot < MAIN_SIZE; slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }
        inventory.setItem(ENDER_BUTTON_SLOT, enderButton());
        inventory.setItem(REFRESH_SLOT, refreshButton(snapshotAtMillis));

        viewer.openInventory(inventory);
    }

    public static void openEnder(final Player viewer, final InvseeHolder source, final MessageManager messageManager) {
        final Component title = messageManager.getComponent("admin.icesmp.invsee.title-ender",
                "&6» %s ender-ládája (csak olvasás%s) «", source.getTargetName(),
                ageSuffix(source.getSnapshotAtMillis()));
        final InvseeHolder holder = new InvseeHolder(source.getTargetName(), source.getMainSnapshot(),
                source.getArmorSnapshot(), source.getOffHandSnapshot(), source.getEnderSnapshot(),
                source.getSnapshotAtMillis(), InvseeHolder.View.ENDER);
        final Inventory inventory = Bukkit.createInventory(holder, ENDER_SIZE, title);
        holder.setInventory(inventory);

        final ItemStack[] enderSnapshot = source.getEnderSnapshot();
        for (int slot = 0; slot < 27 && enderSnapshot != null && slot < enderSnapshot.length; slot++) {
            inventory.setItem(slot, enderSnapshot[slot]);
        }
        for (int slot = 27; slot < ENDER_SIZE; slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }
        inventory.setItem(BACK_SLOT, backButton());

        viewer.openInventory(inventory);
    }

    private static ItemStack resolveArmorOffhandPiece(final int index, final ItemStack[] armorSnapshot,
                                                       final ItemStack offHandSnapshot) {
        if (index == ARMOR_OFFHAND_SLOTS.length - 1) {
            return offHandSnapshot;
        }
        return armorSnapshot != null && index < armorSnapshot.length ? armorSnapshot[index] : null;
    }

    private static ItemStack emptySlotMarker(final String label) {
        return GuiUtil.icon(Material.GRAY_STAINED_GLASS_PANE, GuiUtil.grey(label + " (üres)"), List.of());
    }

    private static ItemStack enderButton() {
        return GuiUtil.icon(Material.ENDER_CHEST, GuiUtil.accent("Ender-láda"),
                List.of(GuiUtil.grey("Kattints a megtekintéshez"), GuiUtil.grey("Pillanatkép — nem élő nézet")));
    }

    private static ItemStack backButton() {
        return GuiUtil.icon(Material.ARROW, GuiUtil.accent("« Vissza a fő nézethez"),
                List.of(GuiUtil.grey("Pillanatkép — nem élő nézet")));
    }

    /** A pillanatkép kora a címben („ — 12 mp-es kép"), friss képnél üres. */
    private static String ageSuffix(final long snapshotAtMillis) {
        final long ageSeconds = Math.max(0L, (System.currentTimeMillis() - snapshotAtMillis) / 1000L);
        return ageSeconds < 5L ? " — pillanatkép" : " — " + ageSeconds + " mp-es kép";
    }

    private static ItemStack refreshButton(final long snapshotAtMillis) {
        return GuiUtil.icon(Material.CLOCK, GuiUtil.accent("🔄 Frissítés"),
                List.of(GuiUtil.grey("Új pillanatkép kérése a célpontról."),
                        GuiUtil.grey(ageSuffix(snapshotAtMillis).replaceFirst("^ — ", "Kora: "))));
    }
}
