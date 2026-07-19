package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dedicated claim-trust manager GUI (the /menu → Birtok sub-menu links here since
 * trust/untrust need a target name that plain RUN: tiles can't supply). Top rows
 * list the viewer's currently trusted players — click revokes. The bottom row
 * lists nearby ONLINE players who aren't trusted yet — click grants. Every click
 * is pure delegation: it runs the existing /claim trust|untrust command (which
 * owns all the real ClaimManager state changes) and then rebuilds this GUI.
 */
public final class ClaimTrustGUI {

    private static final int SIZE = 54;
    public static final int TRUSTED_START = 0;
    public static final int TRUSTED_END = 35;
    public static final int NEARBY_START = 45;
    public static final int NEARBY_END = 52;
    public static final int BACK_SLOT = 53;
    private static final double NEARBY_RADIUS = 15.0D;

    private ClaimTrustGUI() {
    }

    public static void open(final Player viewer, final ClaimManager claimManager, final MessageManager messageManager) {
        final Component title = messageManager.getComponent("messages.claim-trust-title", "&6» Megbízottak «");
        final ClaimTrustHolder holder = new ClaimTrustHolder(viewer.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);
        GuiUtil.fill(inventory);

        final Set<UUID> trusted = claimManager.trustedPlayers(viewer.getUniqueId());
        int slot = TRUSTED_START;
        for (final UUID trustedId : trusted) {
            if (slot > TRUSTED_END) {
                break;
            }
            inventory.setItem(slot, trustedTile(trustedId));
            holder.mapTrusted(slot, trustedId);
            slot++;
        }
        if (slot == TRUSTED_START) {
            inventory.setItem(TRUSTED_START, emptyTile("Nincs megbízottad",
                    "A megbízottaid itt fognak megjelenni."));
        }

        int nearbySlot = NEARBY_START;
        for (final Player online : viewer.getLocation().getNearbyPlayers(NEARBY_RADIUS)) {
            if (nearbySlot > NEARBY_END) {
                break;
            }
            if (online.getUniqueId().equals(viewer.getUniqueId()) || trusted.contains(online.getUniqueId())) {
                continue;
            }
            inventory.setItem(nearbySlot, nearbyTile(online));
            holder.mapNearby(nearbySlot, online.getUniqueId());
            nearbySlot++;
        }
        if (nearbySlot == NEARBY_START) {
            inventory.setItem(NEARBY_START, emptyTile("Nincs közeli játékos",
                    "Állj " + (int) NEARBY_RADIUS + " blokkon belül ahhoz, akit megbíznál."));
        }

        inventory.setItem(BACK_SLOT, nav(Material.BARRIER, "Bezárás"));

        viewer.openInventory(inventory);
    }

    private static ItemStack trustedTile(final UUID trustedId) {
        return GuiUtil.playerHead(Bukkit.getOfflinePlayer(trustedId),
                Component.text(nameOf(trustedId), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                List.of(GuiUtil.grey("Kattints: megbízás visszavonása")));
    }

    private static ItemStack nearbyTile(final Player online) {
        return GuiUtil.playerHead(online,
                Component.text(online.getName(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(GuiUtil.grey("Kattints: megbízás")));
    }

    private static ItemStack emptyTile(final String name, final String lore) {
        return GuiUtil.icon(Material.GRAY_DYE,
                Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                List.of(GuiUtil.grey(lore)));
    }

    private static ItemStack nav(final Material material, final String label) {
        return GuiUtil.icon(material, Component.text(label, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of());
    }

    private static String nameOf(final UUID uuid) {
        final String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }
}
