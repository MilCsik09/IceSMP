package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@SuppressWarnings("deprecation")
public final class JobGUI {

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 49;
    private static final int CATALYST_SLOT = 51;
    private static final int SKILL_TREE_SLOT = 47;
    // Up to 16 classes: a 4×4 grid (rows 1–4, columns 1/3/5/7), clear of the row-5 buttons (47/49/51).
    // resolveJobType/placeJobItems both index this array, so layout and click-mapping stay in sync.
    private static final int[] JOB_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34, 37, 39, 41, 43};

    private JobGUI() {
    }

    public static void openJobMenu(final Player viewer, final JobManager jobManager,
                                   final CatalystItemFactory catalystItemFactory, final MessageManager messageManager) {
        if (viewer == null || jobManager == null || catalystItemFactory == null || messageManager == null) {
            return;
        }

        final Component title = messageManager.getComponent("messages.job-gui-title", "&3\u00bb Kasztok es Szakmak \u00ab");
        final JobGUIHolder holder = new JobGUIHolder(viewer.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        fillBackground(inventory);
        placeJobItems(inventory, viewer, jobManager, messageManager);
        inventory.setItem(BACK_SLOT, createBackButton(messageManager));
        inventory.setItem(CATALYST_SLOT, createCatalystButton(viewer, jobManager, catalystItemFactory, messageManager));
        inventory.setItem(SKILL_TREE_SLOT, createSkillTreeButton(messageManager));

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

    public static int getCatalystSlot() {
        return CATALYST_SLOT;
    }

    public static int getSkillTreeSlot() {
        return SKILL_TREE_SLOT;
    }

    private static ItemStack createSkillTreeButton(final MessageManager messageManager) {
        final ItemStack button = new ItemStack(Material.KNOWLEDGE_BOOK);
        final ItemMeta meta = button.getItemMeta();
        meta.displayName(messageManager.getComponent("messages.job-gui-skilltree", "&5Képesség-fa"));
        meta.lore(List.of(messageManager.getComponent("messages.job-gui-skilltree-lore", "&7A kasztod képességei és feloldási szintjei.")));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        button.setItemMeta(meta);
        return button;
    }

    private static ItemStack createCatalystButton(final Player viewer, final JobManager jobManager,
                                                  final CatalystItemFactory catalystItemFactory,
                                                  final MessageManager messageManager) {
        final JobType primaryJob = jobManager.getPrimaryJob(viewer);
        if (primaryJob == null) {
            final ItemStack lockedButton = new ItemStack(Material.BARRIER);
            final ItemMeta lockedMeta = lockedButton.getItemMeta();
            lockedMeta.displayName(messageManager.getComponent("messages.job-gui-catalyst-locked", "&cLélekkapocs"));
            lockedMeta.lore(List.of(messageManager.getComponent("messages.job-gui-catalyst-locked-lore", "&7Előbb válassz elsődleges kasztot!")));
            lockedMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
            lockedButton.setItemMeta(lockedMeta);
            return lockedButton;
        }

        final ItemStack button = new ItemStack(catalystItemFactory.getMaterial(primaryJob));
        final ItemMeta meta = button.getItemMeta();
        meta.displayName(catalystItemFactory.getDisplayName(primaryJob));
        meta.lore(List.of(
                messageManager.getComponent("messages.job-gui-catalyst-lore", "&7A kasztod Lélekkapcsa."),
                messageManager.getComponent("messages.job-gui-catalyst-claim", "&eKattints az igényléshez, ha elveszett!")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        button.setItemMeta(meta);
        return button;
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
        final JobType primary = jobManager.getPrimaryJob(viewer);
        final boolean selected = primary == jobType;

        // Selected jobs glow; the flag set matches GuiUtil.icon exactly, so it is a render-equivalent build.
        return GuiUtil.icon(
                Material.ENCHANTED_BOOK,
                jobType.getDisplayName(),
                resolveJobLore(jobType, primary, jobManager.getPrimaryLevel(viewer), messageManager),
                selected);
    }

    private static List<Component> resolveJobLore(final JobType jobType, final JobType primary,
                                                  final int primaryLevel, final MessageManager messageManager) {
        // Gameplay-audit: a kaszt visszafordíthatatlan döntés — a szerep-címke már a
        // választó felületen látsszon, ne csak a spec-oldalon.
        final Component roleLine = messageManager.getComponent(
                "messages.job-gui-role-" + jobType.getId(), "&b" + defaultRoleTag(jobType));
        if (primary == jobType) {
            return List.of(
                    roleLine,
                    messageManager.getComponent("messages.job-gui-lore-primary", "&aElsodleges kasztod"),
                    messageManager.getComponent(
                            "messages.job-gui-lore-level-line",
                            "&7Szint: &f%s&7/&f%s",
                            primaryLevel,
                            JobManager.MAX_JOB_LEVEL
                    )
            );
        }

        if (primary == null) {
            return List.of(
                    roleLine,
                    messageManager.getComponent("messages.job-gui-lore-click", "&7Kattints a kivalasztashoz!"));
        }

        // A class is already chosen (and it isn't this one) — the player can't change it anymore.
        return List.of(
                roleLine,
                messageManager.getComponent("messages.job-gui-lore-already-have", "&cMar van kasztod."),
                messageManager.getComponent("messages.job-gui-lore-no-change", "&7Jelenleg nem modosithato.")
        );
    }

    /** A kaszt szerep-címkéje (a spec-roster szerint: tank/gyógyító ág, ha van). */
    private static String defaultRoleTag(final JobType jobType) {
        return switch (jobType) {
            case WIZARD -> "Szerep: Távolsági sebző";
            case WARRIOR -> "Szerep: Sebző / Tank";
            case ARCHER -> "Szerep: Távolsági sebző";
            case ASSASSIN -> "Szerep: Közelharci sebző";
            case DRUID -> "Szerep: Sebző / Tank / Gyógyító";
            case PALADIN -> "Szerep: Sebző / Tank / Gyógyító";
            case DEATH_KNIGHT -> "Szerep: Sebző / Tank";
            case SHAMAN -> "Szerep: Sebző / Gyógyító";
            case MONK -> "Szerep: Sebző / Tank / Gyógyító";
            case PRIEST -> "Szerep: Gyógyító / Sebző";
            case WARLOCK -> "Szerep: Távolsági sebző";
            case DEMON_HUNTER -> "Szerep: Sebző / Tank";
            case EVOKER -> "Szerep: Sebző / Gyógyító";
        };
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

