package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@SuppressWarnings("deprecation")
public final class JobGUI {

    private static final int SIZE = 36;
    private static final int BACK_SLOT = 31;
    private static final int[] JOB_SLOTS = {11, 13, 15, 20, 22, 24};

    private JobGUI() {
    }

    public static void openJobMenu(final Player viewer, final JobManager jobManager, final MessageManager messageManager) {
        if (viewer == null || jobManager == null || messageManager == null) {
            return;
        }

        final Component title = messageManager.getComponent("messages.job-gui-title", "&3\u00bb Kasztok es Szakmak \u00ab");
        final JobGUIHolder holder = new JobGUIHolder(viewer.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        fillBackground(inventory);
        placeJobItems(inventory, viewer, jobManager, messageManager);
        inventory.setItem(BACK_SLOT, createBackButton(messageManager));

        viewer.openInventory(inventory);
    }

    public static JobType resolveJobType(final int rawSlot) {
        for (int index = 0; index < Math.min(JOB_SLOTS.length, JobType.values().length); index++) {
            if (JOB_SLOTS[index] == rawSlot) {
                return JobType.values()[index];
            }
        }

        return null;
    }

    public static int getBackSlot() {
        return BACK_SLOT;
    }

    private static void fillBackground(final Inventory inventory) {
        final ItemStack filler = createFiller();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private static void placeJobItems(final Inventory inventory, final Player viewer, final JobManager jobManager,
                                      final MessageManager messageManager) {
        final JobType[] jobTypes = JobType.values();
        for (int index = 0; index < Math.min(jobTypes.length, JOB_SLOTS.length); index++) {
            inventory.setItem(JOB_SLOTS[index], createJobItem(jobTypes[index], viewer, jobManager, messageManager));
        }
    }

    private static ItemStack createJobItem(final JobType jobType, final Player viewer, final JobManager jobManager,
                                           final MessageManager messageManager) {
        final ItemStack itemStack = new ItemStack(Material.ENCHANTED_BOOK);
        final ItemMeta meta = itemStack.getItemMeta();
        final JobType primary = jobManager.getPrimaryJob(viewer);
        final JobType secondary = jobManager.getSecondaryJob(viewer);
        final boolean selected = primary == jobType || secondary == jobType;

        meta.displayName(jobType.getDisplayName());
        meta.lore(resolveJobLore(jobType, primary, secondary, jobManager.getPrimaryLevel(viewer), jobManager.isPrimaryJobAtMaxLevel(viewer), messageManager));
        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private static List<Component> resolveJobLore(final JobType jobType, final JobType primary, final JobType secondary,
                                                  final int primaryLevel, final boolean primaryMaxed,
                                                  final MessageManager messageManager) {
        if (primary == jobType) {
            return List.of(
                    messageManager.getComponent("messages.job-gui-lore-primary", "&aElsodleges kasztod"),
                    messageManager.getComponent(
                            "messages.job-gui-lore-level-line",
                            "&7Szint: &f%s&7/&f%s",
                            primaryLevel,
                            JobManager.MAX_JOB_LEVEL
                    )
            );
        }

        if (secondary == jobType) {
            return List.of(
                    messageManager.getComponent("messages.job-gui-lore-secondary", "&bMasodlagos kasztod"),
                    messageManager.getComponent("messages.job-gui-lore-selected", "&7Mar kivalasztva.")
            );
        }

        if (primary == null) {
            return List.of(messageManager.getComponent("messages.job-gui-lore-click", "&7Kattints a kivalasztashoz!"));
        }

        if (secondary != null) {
            return List.of(
                    messageManager.getComponent("messages.job-gui-lore-both-filled", "&cMindket kaszt hely foglalt."),
                    messageManager.getComponent("messages.job-gui-lore-no-change", "&7Jelenleg nem modosithato.")
            );
        }

        if (!primaryMaxed) {
            return List.of(
                    messageManager.getComponent("messages.job-gui-lore-secondary-locked", "&cMasodlagos kaszthoz elobb max szint kell."),
                    messageManager.getComponent(
                            "messages.job-gui-lore-level-line",
                            "&7Szint: &f%s&7/&f%s",
                            primaryLevel,
                            JobManager.MAX_JOB_LEVEL
                    )
            );
        }

        return List.of(messageManager.getComponent("messages.job-gui-lore-click", "&7Kattints a kivalasztashoz!"));
    }

    private static ItemStack createBackButton(final MessageManager messageManager) {
        final ItemStack itemStack = new ItemStack(Material.ARROW);
        final ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(messageManager.getComponent("messages.job-gui-back", "&cVissza a Profilhoz"));
        meta.lore(List.of());
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private static ItemStack createFiller() {
        final ItemStack itemStack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        final ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(Component.empty());
        meta.lore(List.of());
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(meta);
        return itemStack;
    }
}

