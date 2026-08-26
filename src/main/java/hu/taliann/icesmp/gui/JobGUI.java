package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.ClassUiAssets;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Character class selector with permanent-choice affordances. */
public final class JobGUI {

    public static final int SIZE = 54;
    public static final int BACK_SLOT = 49;

    private JobGUI() {
    }

    public static void openJobMenu(final Player player, final JobManager jobManager,
                                   final CatalystItemFactory catalystItemFactory,
                                   final FactionManager factionManager,
                                   final MessageManager messageManager) {
        final JobHolder holder = new JobHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE,
                messageManager.getComponent("messages.job-gui-title", "&6» Kasztválasztás «"));
        holder.setInventory(inventory);

        final JobType primary = jobManager.getPrimaryJob(player);
        final FactionType faction = factionManager.getChosenFaction(player.getUniqueId()).orElse(null);
        int slot = 10;
        for (final JobType jobType : JobType.values()) {
            if (slot == BACK_SLOT) slot++;
            if (slot >= SIZE) break;
            inventory.setItem(slot, jobTile(player, jobType, primary, jobManager,
                    catalystItemFactory, faction, messageManager));
            holder.map(slot, jobType);
            slot += slot % 9 == 7 ? 3 : 2;
        }
        inventory.setItem(BACK_SLOT, GuiUtil.icon(Material.ARROW,
                messageManager.getComponent("messages.job-gui-back", "&eVissza a profilhoz"), List.of()));
        holder.mapBack(BACK_SLOT);
        GuiUtil.fill(inventory);
        player.openInventory(inventory);
    }

    private static ItemStack jobTile(final Player viewer, final JobType jobType,
                                     final JobType primary, final JobManager jobManager,
                                     final CatalystItemFactory catalystItemFactory,
                                     final FactionType faction,
                                     final MessageManager messageManager) {
        final ItemStack base = catalystItemFactory.createClassCatalyst(jobType);
        final ItemStack stack = base == null || base.getType().isAir()
                ? new ItemStack(Material.ENCHANTED_BOOK) : base.clone();
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(ClassUiAssets.classBadgeName(jobType));
        final List<Component> lore = new ArrayList<>(resolveJobLore(
                jobType, primary, jobManager.getPrimaryLevel(viewer), messageManager));
        final boolean selected = primary == jobType;
        if (selected) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        }
        final FactionType required = jobType.getRequiredFaction();
        if (required != null && required != faction) {
            lore.add(messageManager.getComponent("messages.job-gui-lore-faction",
                    "&cEhhez a kaszthoz a(z) %s frakció tagsága szükséges.", required.getDisplayName()));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private static List<Component> resolveJobLore(final JobType jobType, final JobType primary,
                                                  final int primaryLevel, final MessageManager messageManager) {
        final Component roleLine = messageManager.getComponent(
                "messages.job-gui-role-" + jobType.getId(), "&b" + defaultRoleTag(jobType));
        if (primary == jobType) {
            return List.of(
                    roleLine,
                    messageManager.getComponent("messages.job-gui-lore-primary", "&aElsődleges kasztod"),
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
                    messageManager.getComponent("messages.job-gui-lore-click", "&7Kattints a kiválasztáshoz!"));
        }

        return List.of(
                roleLine,
                messageManager.getComponent("messages.job-gui-lore-already-have", "&cMár van kasztod."),
                messageManager.getComponent("messages.job-gui-lore-no-change", "&7Jelenleg nem módosítható.")
        );
    }

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
            case PRIEST -> "Szerep: Gyógyító";
            case WARLOCK -> "Szerep: Távolsági sebző";
            case DEMON_HUNTER -> "Szerep: Közelharci sebző";
            case EVOKER -> "Szerep: Távolsági sebző / Gyógyító";
        };
    }
}
