package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Quest log GUI (ROADMAP quest-package C): a paginated book of the player's
 * quests split into three tabs — active (progress, shift-click to abandon),
 * available (click to accept) and completed. The bottom row holds the tab
 * buttons and page navigation. Purely a view over {@link QuestManager}; all
 * mutations go through it from {@link hu.taliann.icesmp.listeners.QuestLogListener}.
 */
public final class QuestLogGUI {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    public static final int TAB_ACTIVE_SLOT = 45;
    public static final int TAB_AVAILABLE_SLOT = 46;
    public static final int TAB_COMPLETED_SLOT = 47;
    public static final int PREV_SLOT = 48;
    public static final int PAGE_INFO_SLOT = 49;
    public static final int NEXT_SLOT = 50;
    public static final int MENU_SLOT = 53;

    private QuestLogGUI() {
    }

    public static void open(final Player viewer, final QuestManager questManager, final MessageManager messageManager) {
        open(viewer, questManager, messageManager, 0, QuestLogHolder.Tab.ACTIVE);
    }

    public static void open(final Player viewer, final QuestManager questManager, final MessageManager messageManager,
                            final int page, final QuestLogHolder.Tab tab) {
        final List<String> quests = collectQuests(viewer, questManager, tab);
        final int totalPages = Math.max(1, (int) Math.ceil(quests.size() / (double) PER_PAGE));
        final int safePage = Math.max(0, Math.min(page, totalPages - 1));

        final Component title = messageManager.getComponent("messages.quest-log-title", "&6» Küldetésnapló «");
        final QuestLogHolder holder = new QuestLogHolder(viewer.getUniqueId());
        holder.setPage(safePage);
        holder.setTab(tab);
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        final int start = safePage * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < quests.size(); i++) {
            final String questId = quests.get(start + i);
            inventory.setItem(i, createQuestItem(viewer, questManager, questId, tab));
            if (tab == QuestLogHolder.Tab.ACTIVE) {
                holder.mapSlot(i, questId, QuestLogHolder.Action.ABANDON);
            } else if (tab == QuestLogHolder.Tab.AVAILABLE) {
                holder.mapSlot(i, questId, QuestLogHolder.Action.ACCEPT);
            }
        }

        for (int slot = PER_PAGE; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }
        inventory.setItem(TAB_ACTIVE_SLOT, tabButton(Material.WRITABLE_BOOK, "Aktív", tab == QuestLogHolder.Tab.ACTIVE));
        inventory.setItem(TAB_AVAILABLE_SLOT, tabButton(Material.BOOK, "Felvehető", tab == QuestLogHolder.Tab.AVAILABLE));
        inventory.setItem(TAB_COMPLETED_SLOT, tabButton(Material.ENCHANTED_BOOK, "Teljesített", tab == QuestLogHolder.Tab.COMPLETED));
        if (safePage > 0) {
            inventory.setItem(PREV_SLOT, nav(Material.ARROW, "« Előző oldal"));
        }
        if (safePage < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, nav(Material.ARROW, "Következő oldal »"));
        }
        inventory.setItem(PAGE_INFO_SLOT, nav(Material.PAPER,
                "Oldal " + (safePage + 1) + "/" + totalPages + " — " + quests.size() + " küldetés"));
        inventory.setItem(MENU_SLOT, nav(Material.ARROW, "Főmenü (/menu)"));

        viewer.openInventory(inventory);
    }

    private static List<String> collectQuests(final Player viewer, final QuestManager questManager,
                                              final QuestLogHolder.Tab tab) {
        final List<String> result = new ArrayList<>();
        switch (tab) {
            case ACTIVE -> result.addAll(questManager.getActiveQuests(viewer));
            case COMPLETED -> result.addAll(questManager.getCompletedQuests(viewer));
            case AVAILABLE -> {
                for (final String questId : questManager.getQuestIds()) {
                    if (!questManager.isActive(viewer, questId)
                            && questManager.getAcceptBlocker(viewer, questId) == null) {
                        result.add(questId);
                    }
                }
            }
        }
        return result;
    }

    private static ItemStack createQuestItem(final Player viewer, final QuestManager questManager,
                                             final String questId, final QuestLogHolder.Tab tab) {
        final Material material = switch (tab) {
            case ACTIVE -> Material.WRITABLE_BOOK;
            case AVAILABLE -> Material.BOOK;
            case COMPLETED -> Material.ENCHANTED_BOOK;
        };
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(questManager.getDisplayName(questId),
                tab == QuestLogHolder.Tab.AVAILABLE ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        final List<Component> lore = new ArrayList<>();
        final ConfigurationSection quest = questManager.getQuestSection(questId);
        final String description = quest == null ? "" : quest.getString("description", "");
        if (!description.isBlank()) {
            lore.add(GuiUtil.grey(description));
        }
        lore.add(Component.empty());
        switch (tab) {
            case ACTIVE -> {
                lore.add(GuiUtil.label("Haladás", Component.text(
                        questManager.describeProgress(viewer, questId), NamedTextColor.WHITE)));
                lore.add(Component.empty());
                lore.add(Component.text("» Shift-kattintás: feladás", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
            case AVAILABLE -> {
                lore.add(GuiUtil.label("Feladatok", Component.text(
                        questManager.getObjectiveTotal(questId), NamedTextColor.WHITE)));
                lore.add(Component.empty());
                lore.add(Component.text("» Kattints a felvételhez", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            }
            case COMPLETED -> lore.add(Component.text("✔ Teljesítve", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack tabButton(final Material material, final String label, final boolean selected) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text((selected ? "▶ " : "") + label,
                selected ? NamedTextColor.GOLD : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (selected) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack nav(final Material material, final String label) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
