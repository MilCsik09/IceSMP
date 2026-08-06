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
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()
                || !holder.ownerId().equals(viewer.getUniqueId())) {
            return;
        }

        final ModerationGuiHolder.Action action = holder.actionAt(slot);
        if (action != null) {
            handleAction(viewer, holder, action);
            return;
        }

        if (holder.page() == ModerationGuiHolder.Page.PLAYERS) {
            final ModerationGuiHolder.PlayerTarget selected = holder.playerAt(slot);
            if (selected == null) {
                return;
            }
            final Player target = Bukkit.getPlayer(selected.uniqueId());
            if (target == null || !visibleTo(viewer, target)) {
                viewer.sendMessage(messages.get("moderation.player-offline",
                        "&cA játékos nincs online: &f%s", selected.name()));
                ModerationGUI.openPlayers(viewer, messages, holder.listPage());
                return;
            }
            ModerationGUI.openPlayer(viewer, target, messages, holder.listPage());
            return;
        }

        final Player target = Bukkit.getPlayer(holder.targetId());
        if (target != null && !visibleTo(viewer, target)) {
            viewer.sendMessage(messages.get("moderation.player-offline",
                    "&cA játékos nincs online: &f%s", holder.targetName()));
            ModerationGUI.openPlayers(viewer, messages, holder.listPage());
            return;
        }
        if (target == null && slot != 29) {
            viewer.sendMessage(messages.get("moderation.player-offline",
                    "&cA játékos nincs online: &f%s", holder.targetName()));
            viewer.closeInventory();
            return;
        }
        if (slot == 22 && !hasInvseePermission(viewer)) {
            viewer.sendMessage(messages.get("moderation.permission-denied",
                    "&cNincs jogod ehhez a moderációs művelethez."));
            return;
        }
        final String requiredPermission = permissionForSlot(slot);
        if (requiredPermission != null && !viewer.hasPermission(requiredPermission)) {
            viewer.sendMessage(messages.get("moderation.permission-denied",
                    "&cNincs jogod ehhez a moderációs művelethez."));
            return;
        }
        final String name = target == null ? holder.targetName() : target.getName();
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
            case 22 -> viewer.performCommand("invsee " + name);
            case 28 -> teleportToOnline(viewer, target);
            case 29 -> viewer.performCommand("offlinetp " + name);
            case 30 -> viewer.performCommand("socialspy");
            case 31 -> viewer.performCommand("vanish " + name);
            default -> { }
        }
    }

    private void handleAction(final Player viewer, final ModerationGuiHolder holder,
                              final ModerationGuiHolder.Action action) {
        switch (action) {
            case PREVIOUS_PAGE -> ModerationGUI.openPlayers(viewer, messages, holder.listPage() - 1);
            case NEXT_PAGE -> ModerationGUI.openPlayers(viewer, messages, holder.listPage() + 1);
            case BACK -> ModerationGUI.openPlayers(viewer, messages, holder.listPage());
            case CLOSE -> viewer.closeInventory();
        }
    }

    private static boolean visibleTo(final Player viewer, final Player target) {
        return target.getUniqueId().equals(viewer.getUniqueId()) || viewer.canSee(target);
    }

    private static boolean hasInvseePermission(final Player viewer) {
        return viewer.hasPermission(Permissions.MODERATION_INVENTORY_READ)
                || viewer.hasPermission(Permissions.MODERATION_INVENTORY_EDIT);
    }

    private static String permissionForSlot(final int slot) {
        return switch (slot) {
            case 10 -> Permissions.MODERATION_WARN;
            case 11, 14 -> Permissions.MODERATION_MUTE;
            case 12, 15 -> Permissions.MODERATION_BAN;
            case 13 -> Permissions.MODERATION_KICK;
            case 19, 20 -> Permissions.MODERATION_HISTORY;
            case 21 -> Permissions.MODERATION;
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
