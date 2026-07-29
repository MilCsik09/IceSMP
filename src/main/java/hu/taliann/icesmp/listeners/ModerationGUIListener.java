package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.gui.ModerationGUI;
import hu.taliann.icesmp.gui.ModerationGuiHolder;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Routes moderation GUI buttons to the same commands/services as text administration. */
public final class ModerationGUIListener implements Listener {
    private final JavaPlugin plugin;
    private final MessageManager messages;

    public ModerationGUIListener(final JavaPlugin plugin, final MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ModerationGuiHolder holder)
                || !(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        event.setCancelled(true);
        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (holder.page() == ModerationGuiHolder.Page.PLAYERS) {
            if (slot == 49) {
                viewer.closeInventory();
                return;
            }
            final String targetName = event.getCurrentItem() == null || event.getCurrentItem().getItemMeta() == null
                    ? null : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(event.getCurrentItem().getItemMeta().displayName());
            final Player target = targetName == null ? null : Bukkit.getPlayerExact(targetName);
            if (target != null) {
                ModerationGUI.openPlayer(viewer, target, messages);
            }
            return;
        }
        final Player target = Bukkit.getPlayer(holder.targetId());
        if (slot == 49) {
            ModerationGUI.openPlayers(viewer, messages);
            return;
        }
        if (slot == 53) {
            viewer.closeInventory();
            return;
        }
        if (target == null && slot != 29) {
            viewer.sendMessage(messages.get("moderation.player-offline", "&cA játékos nincs online: &f%s", holder.targetName()));
            viewer.closeInventory();
            return;
        }
        final String requiredPermission = permissionForSlot(slot);
        if (requiredPermission != null && !viewer.hasPermission(requiredPermission)) {
            viewer.sendMessage(messages.get("moderation.permission-denied",
                    "&cNincs jogod ehhez a moderációs művelethez."));
            return;
        }
        final String name = holder.targetName();
        switch (slot) {
            case 10 -> viewer.performCommand("warn " + name + " Moderációs GUI");
            case 11 -> viewer.performCommand("mute " + name + " 30m Moderációs GUI");
            case 12 -> viewer.performCommand("ban " + name + " Moderációs GUI");
            case 13 -> viewer.performCommand("kick " + name + " Moderációs GUI");
            case 14 -> viewer.performCommand("unmute " + name + " Moderációs GUI");
            case 15 -> viewer.performCommand("unban " + name + " Moderációs GUI");
            case 19 -> viewer.performCommand("history " + name);
            case 20 -> viewer.performCommand("punishments " + name);
            case 21 -> viewer.performCommand("reports");
            case 22 -> viewer.performCommand("invsee " + name + " read main");
            case 23 -> viewer.performCommand("invsee " + name + " edit main");
            case 24 -> viewer.performCommand("invsee " + name + " read ender");
            case 25 -> viewer.performCommand("invsee " + name + " edit ender");
            case 28 -> teleportToOnline(viewer, target);
            case 29 -> viewer.performCommand("offlinetp " + name);
            case 30 -> viewer.performCommand("socialspy");
            case 31 -> viewer.performCommand("vanish " + name);
            default -> { }
        }
    }

    private static String permissionForSlot(final int slot) {
        return switch (slot) {
            case 10 -> Permissions.MODERATION_WARN;
            case 11, 14 -> Permissions.MODERATION_MUTE;
            case 12, 15 -> Permissions.MODERATION_BAN;
            case 13 -> Permissions.MODERATION_KICK;
            case 19, 20 -> Permissions.MODERATION_HISTORY;
            case 21 -> Permissions.MODERATION;
            case 22, 24 -> Permissions.MODERATION_INVENTORY_READ;
            case 23, 25 -> Permissions.MODERATION_INVENTORY_EDIT;
            case 28, 29 -> Permissions.MODERATION_OFFLINE_TP;
            case 30 -> Permissions.MODERATION_SOCIALSPY;
            case 31 -> Permissions.MODERATION_VANISH;
            default -> null;
        };
    }

    private void teleportToOnline(final Player viewer, final Player target) {
        PaperEntityTaskSubmission.run(plugin, target.getScheduler(), () -> {
            final Location snapshot = target.getLocation().clone();
            PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(),
                    () -> viewer.teleportAsync(snapshot), () -> { });
        }, () -> PaperEntityTaskSubmission.run(plugin, viewer.getScheduler(),
                () -> viewer.sendMessage(messages.get(
                        "moderation.player-offline", "&cA céljátékos kilépett.")), () -> { }));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ModerationGuiHolder) {
            event.setCancelled(true);
        }
    }
}
