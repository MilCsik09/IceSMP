package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Comparator;
import java.util.List;

/** Rendering of the permission-aware native moderation menu using existing GuiUtil icons. */
public final class ModerationGUI {
    private ModerationGUI() {
    }

    public static void openPlayers(final Player viewer, final MessageManager messages) {
        final ModerationGuiHolder holder = new ModerationGuiHolder(ModerationGuiHolder.Page.PLAYERS, null, null);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.getComponent("moderation.gui.players-title", "&6Moderáció — online játékosok"));
        holder.setInventory(inventory);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }
        final List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).limit(45).toList();
        for (int slot = 0; slot < players.size(); slot++) {
            final Player target = players.get(slot);
            inventory.setItem(slot, GuiUtil.icon(Material.PLAYER_HEAD, GuiUtil.accent(target.getName()),
                    List.of(GuiUtil.grey(messages.get("moderation.gui.choose-player",
                            "Kattints a moderációs műveletekhez.")))));
        }
        inventory.setItem(49, GuiUtil.icon(Material.BARRIER,
                messages.getComponent("moderation.gui.close", "&cBezárás"),
                List.of()));
        viewer.openInventory(inventory);
    }

    public static void openPlayer(final Player viewer, final Player target, final MessageManager messages) {
        final ModerationGuiHolder holder = new ModerationGuiHolder(ModerationGuiHolder.Page.PLAYER,
                target.getUniqueId(), target.getName());
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.getComponent("moderation.gui.player-title", "&6Moderáció — %s", target.getName()));
        holder.setInventory(inventory);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }
        put(viewer, inventory, 10, Permissions.MODERATION_WARN, Material.PAPER, messages.get("moderation.gui.warn", "Figyelmeztetés"), "/warn");
        put(viewer, inventory, 11, Permissions.MODERATION_MUTE, Material.CLOCK, messages.get("moderation.gui.mute", "Némítás 30 percre"), "/mute");
        put(viewer, inventory, 12, Permissions.MODERATION_BAN, Material.REDSTONE_BLOCK, messages.get("moderation.gui.ban", "Végleges kitiltás"), "/ban");
        put(viewer, inventory, 13, Permissions.MODERATION_KICK, Material.IRON_BOOTS, messages.get("moderation.gui.kick", "Kirúgás"), "/kick");
        put(viewer, inventory, 14, Permissions.MODERATION_MUTE, Material.MILK_BUCKET, messages.get("moderation.gui.unmute", "Némítás feloldása"), "/unmute");
        put(viewer, inventory, 15, Permissions.MODERATION_BAN, Material.LIME_DYE, messages.get("moderation.gui.unban", "Kitiltás feloldása"), "/unban");
        put(viewer, inventory, 19, Permissions.MODERATION_HISTORY, Material.BOOK, messages.get("moderation.gui.history", "Teljes előzmény"), "/history");
        put(viewer, inventory, 20, Permissions.MODERATION_HISTORY, Material.WRITABLE_BOOK, messages.get("moderation.gui.active", "Aktív büntetések"), "/punishments");
        put(viewer, inventory, 21, Permissions.MODERATION, Material.MAP, messages.get("moderation.gui.reports", "Reportok"), "/reports");
        put(viewer, inventory, 22, Permissions.MODERATION_INVENTORY_READ, Material.CHEST, messages.get("moderation.gui.inventory-read", "Inventory — olvasás"), "/invsee read");
        put(viewer, inventory, 23, Permissions.MODERATION_INVENTORY_EDIT, Material.ANVIL, messages.get("moderation.gui.inventory-edit", "Inventory — szerkesztés"), "/invsee edit");
        put(viewer, inventory, 24, Permissions.MODERATION_INVENTORY_READ, Material.ENDER_CHEST, messages.get("moderation.gui.ender-read", "Ender chest — olvasás"), "/invsee read ender");
        put(viewer, inventory, 25, Permissions.MODERATION_INVENTORY_EDIT, Material.ENDER_EYE, messages.get("moderation.gui.ender-edit", "Ender chest — szerkesztés"), "/invsee edit ender");
        put(viewer, inventory, 28, Permissions.MODERATION_OFFLINE_TP, Material.ENDER_PEARL, messages.get("moderation.gui.teleport-online", "Teleport az online játékoshoz"), "online teleport");
        put(viewer, inventory, 29, Permissions.MODERATION_OFFLINE_TP, Material.COMPASS, messages.get("moderation.gui.teleport-offline", "Utolsó kijelentkezési hely"), "/offlinetp");
        put(viewer, inventory, 30, Permissions.MODERATION_SOCIALSPY, Material.SPYGLASS, messages.get("moderation.gui.socialspy", "SocialSpy kapcsoló"), "/socialspy");
        put(viewer, inventory, 31, Permissions.MODERATION_VANISH, Material.GLASS, messages.get("moderation.gui.vanish", "Vanish kapcsoló"), "/vanish");
        inventory.setItem(49, GuiUtil.icon(Material.ARROW, messages.getComponent("moderation.gui.back", "&eVissza"), List.of()));
        inventory.setItem(53, GuiUtil.icon(Material.BARRIER,
                messages.getComponent("moderation.gui.close", "&cBezárás"),
                List.of()));
        viewer.openInventory(inventory);
    }

    private static void put(final Player viewer, final Inventory inventory, final int slot,
                            final String permission, final Material material, final String title,
                            final String route) {
        if (!viewer.hasPermission(permission)) {
            return;
        }
        inventory.setItem(slot, GuiUtil.icon(material, GuiUtil.accent(title), List.of(GuiUtil.grey(route))));
    }
}
