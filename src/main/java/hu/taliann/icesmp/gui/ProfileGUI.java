package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

@SuppressWarnings("deprecation")
public final class ProfileGUI {

    private static final int SIZE = 27;
    private static final int SLOT_LEFT = 11;
    private static final int SLOT_CENTER = 13;
    private static final int SLOT_RIGHT = 15;

    private ProfileGUI() {
    }

    public static void openProfile(final Player viewer, final Player target, final MetelytepoManager manager,
                                   final MessageManager messageManager) {
        if (viewer == null) {
            return;
        }

        final Player profileTarget = target == null ? viewer : target;
        final Component title = messageManager.getMessage("system.profile.gui.title", "<dark_gray>» Karakterlap «</dark_gray>");
        final ProfileHolder holder = new ProfileHolder();
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        fillBackground(inventory);
        inventory.setItem(SLOT_CENTER, createHead(profileTarget, manager, messageManager));
        inventory.setItem(SLOT_LEFT, createBookIcon(messageManager));
        inventory.setItem(SLOT_RIGHT, createStatsIcon(messageManager));

        viewer.openInventory(inventory);
    }

    public static void closeAll() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (isProfileInventory(player.getOpenInventory().getTopInventory())) {
                player.closeInventory();
            }
        }
    }

    public static boolean isProfileInventory(final Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof ProfileHolder;
    }

    private static void fillBackground(final Inventory inventory) {
        final ItemStack filler = createFiller();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private static ItemStack createHead(final Player target, final MetelytepoManager manager,
                                        final MessageManager messageManager) {
        final ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = itemStack.getItemMeta();
        final boolean sinner = manager != null && manager.isSinner(target);
        final Component statusLine = sinner
                ? messageManager.getMessage("system.profile.gui.status.sinner", "<red>Allapot: Bunos</red>")
                : messageManager.getMessage("system.profile.gui.status.clean", "<green>Allapot: Tiszta</green>");

        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(target);
            applyDisplay(skullMeta,
                    messageManager.getMessage("system.profile.gui.head.name", "<gold><b>[{player}]</b></gold>", java.util.Map.of("player", target.getName())),
                    List.of(
                            messageManager.getMessage("system.profile.gui.head.lore.job", "<gray>Aktualis kaszt es szint</gray>"),
                            statusLine,
                            messageManager.getMessage("system.profile.gui.head.lore.summary", "<dark_gray>Egy gyors attekintes a karakteredrol.</dark_gray>")
                    ));
            itemStack.setItemMeta(skullMeta);
            return itemStack;
        }

        applyDisplay(meta,
                messageManager.getMessage("system.profile.gui.head.name", "<gold><b>[{player}]</b></gold>", java.util.Map.of("player", target.getName())),
                List.of(
                        messageManager.getMessage("system.profile.gui.head.lore.job", "<gray>Aktualis kaszt es szint</gray>"),
                        statusLine,
                        messageManager.getMessage("system.profile.gui.head.lore.summary", "<dark_gray>Egy gyors attekintes a karakteredrol.</dark_gray>")
                ));
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private static ItemStack createBookIcon(final MessageManager messageManager) {
        return createIcon(
                Material.KNOWLEDGE_BOOK,
                messageManager.getMessage("system.profile.gui.menu.spells.name", "<light_purple>Varazslatok es Kepessegek</light_purple>"),
                List.of(
                        messageManager.getMessage("system.profile.gui.menu.spells.lore.1", "<gray>Nezd meg a magikus kepessegeidet.</gray>"),
                        messageManager.getMessage("system.profile.gui.menu.spells.lore.2", "<dark_gray>Kesobb ide johetnek a skill panelek.</dark_gray>")
                )
        );
    }

    private static ItemStack createStatsIcon(final MessageManager messageManager) {
        return createIcon(
                Material.GOLD_NUGGET,
                messageManager.getMessage("system.profile.gui.menu.stats.name", "<yellow>Statisztikak</yellow>"),
                List.of(
                        messageManager.getMessage("system.profile.gui.menu.stats.lore.1", "<gray>Gyors osszegzes a fontos adataidrol.</gray>"),
                        messageManager.getMessage("system.profile.gui.menu.stats.lore.2", "<dark_gray>Kesobb ide johetnek a reszletes statok.</dark_gray>")
                )
        );
    }

    @SuppressWarnings("deprecation")
    private static ItemStack createFiller() {
        final ItemStack itemStack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        final ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(Component.empty());
        meta.lore(List.of());
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private static ItemStack createIcon(final Material material, final Component name, final List<Component> lore) {
        final ItemStack itemStack = new ItemStack(material);
        final ItemMeta meta = itemStack.getItemMeta();
        applyDisplay(meta, name, lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    @SuppressWarnings("deprecation")
    private static void applyDisplay(final ItemMeta meta, final Component name, final List<Component> lore) {
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
    }
}


