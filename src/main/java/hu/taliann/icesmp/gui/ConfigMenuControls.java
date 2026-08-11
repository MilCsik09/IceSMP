package hu.taliann.icesmp.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Shared bottom-row controls for every staged admin config view. */
public final class ConfigMenuControls {

    private ConfigMenuControls() {
    }

    public static void add(final Inventory inventory, final ConfigMenuHolder holder,
                           final ConfigEditSession session, final boolean showBack) {
        inventory.setItem(45, GuiUtil.item(Material.BARRIER, "&cElvetés",
                List.of("&7Az egész munkamenet minden staged", "&7módosítását elveti; nem ír config.yml-t.")));
        holder.bind(45, "CANCEL");
        if (showBack) {
            inventory.setItem(48, GuiUtil.item(Material.ARROW, "&7Vissza", List.of()));
            holder.bind(48, "BACK");
        }
        inventory.setItem(49, GuiUtil.item(session != null && session.dirty()
                        ? Material.LIME_DYE : Material.GRAY_DYE,
                session != null && session.dirty() ? "&aMentés" : "&7Nincs módosítás",
                List.of("&7Az összes staged értéket egyetlen",
                        "&7optimistic-concurrency tranzakcióban menti.")));
        holder.bind(49, "SAVE");
        inventory.setItem(53, GuiUtil.item(Material.BARRIER, "&cBezárás",
                List.of("&7A nem mentett módosítások elvesznek.")));
        holder.bind(53, "CLOSE");
    }
}
