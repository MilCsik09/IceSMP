package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.quest.QuestSourcePolicy;
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
 * Küldetésnapló GUI, öt füllel: Aktív (követés kattintásra, shift = feladás), Kész
 * (leadható — a leadás helyét mutatja, a lezárás a jogosult forrásnál történik),
 * Megbízások (QUEST_BOARD-forrású questek — a napló az EGYETLEN felület, ahol a
 * tábla-megbízás kattintva felvehető), Elérhető (más forrású látható questek, csak
 * infó + forrás-útmutató, felvenni a forrásnál lehet) és Teljesített. A napló tiszta
 * nézet a {@link QuestManager} fölött; minden mutáció a
 * {@link hu.taliann.icesmp.listeners.QuestLogListener}-en át, a központi
 * authority-útvonalakon fut.
 */
public final class QuestLogGUI {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    public static final int TAB_ACTIVE_SLOT = 45;
    public static final int TAB_READY_SLOT = 46;
    public static final int TAB_BOARD_SLOT = 47;
    public static final int TAB_AVAILABLE_SLOT = 48;
    public static final int TAB_COMPLETED_SLOT = 49;
    public static final int PREV_SLOT = 50;
    public static final int PAGE_INFO_SLOT = 51;
    public static final int NEXT_SLOT = 52;
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

        final String tracked = questManager.getTracked(viewer);
        final int start = safePage * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < quests.size(); i++) {
            final String questId = quests.get(start + i);
            inventory.setItem(i, createQuestItem(viewer, questManager, questId, tab, tracked));
            switch (tab) {
                case ACTIVE -> holder.mapSlot(i, questId, QuestLogHolder.Action.TRACK);
                case BOARD -> holder.mapSlot(i, questId, QuestLogHolder.Action.ACCEPT);
                default -> {
                }
            }
        }

        for (int slot = PER_PAGE; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }
        inventory.setItem(TAB_ACTIVE_SLOT, tabButton(Material.WRITABLE_BOOK, "Aktív", tab == QuestLogHolder.Tab.ACTIVE));
        inventory.setItem(TAB_READY_SLOT, tabButton(Material.GLOW_INK_SAC, "Kész", tab == QuestLogHolder.Tab.READY));
        inventory.setItem(TAB_BOARD_SLOT, tabButton(Material.OAK_HANGING_SIGN, "Megbízások", tab == QuestLogHolder.Tab.BOARD));
        inventory.setItem(TAB_AVAILABLE_SLOT, tabButton(Material.BOOK, "Elérhető", tab == QuestLogHolder.Tab.AVAILABLE));
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

    /** A fülek tartalma kizárólag látható questekből áll — HIDDEN ide sem szivárog. */
    private static List<String> collectQuests(final Player viewer, final QuestManager questManager,
                                              final QuestLogHolder.Tab tab) {
        final List<String> result = new ArrayList<>();
        switch (tab) {
            case ACTIVE -> {
                for (final String questId : questManager.getActiveQuests(viewer)) {
                    if (!questManager.isReadyToTurnIn(viewer, questId)) {
                        result.add(questId);
                    }
                }
            }
            case READY -> result.addAll(questManager.getReadyQuests(viewer));
            case COMPLETED -> result.addAll(questManager.getCompletedQuests(viewer));
            case BOARD, AVAILABLE -> {
                for (final String questId : questManager.getVisibleQuestIds(viewer)) {
                    if (questManager.isActive(viewer, questId)
                            || questManager.getAcceptBlocker(viewer, questId) != null) {
                        continue;
                    }
                    final QuestSourcePolicy policy = questManager.getSourcePolicy(questId);
                    final boolean board = policy != null
                            && policy.startType() == QuestSourcePolicy.StartType.QUEST_BOARD;
                    if (board == (tab == QuestLogHolder.Tab.BOARD)) {
                        result.add(questId);
                    }
                }
            }
        }
        return result;
    }

    private static ItemStack createQuestItem(final Player viewer, final QuestManager questManager,
                                             final String questId, final QuestLogHolder.Tab tab,
                                             final String tracked) {
        final Material material = switch (tab) {
            case ACTIVE -> Material.WRITABLE_BOOK;
            case READY -> Material.GLOW_INK_SAC;
            case BOARD -> Material.PAPER;
            case AVAILABLE -> Material.BOOK;
            case COMPLETED -> Material.ENCHANTED_BOOK;
        };
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        final boolean isTracked = questId.equals(tracked);
        meta.displayName(Component.text((isTracked ? "★ " : "") + questManager.getDisplayName(questId),
                switch (tab) {
                    case READY -> NamedTextColor.GOLD;
                    case BOARD, AVAILABLE -> NamedTextColor.GREEN;
                    default -> NamedTextColor.YELLOW;
                })
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
                lore.add(Component.text(isTracked ? "» Kattints: követés ki" : "» Kattints: követés",
                        NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("» Shift-kattintás: feladás", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
            case READY -> {
                lore.add(Component.text("❖ Kész a leadásra!", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                final String hint = questManager.describeTurnInHint(questId);
                if (!hint.isBlank()) {
                    lore.add(GuiUtil.grey(hint));
                }
            }
            case BOARD -> {
                lore.add(GuiUtil.label("Feladatok", Component.text(
                        questManager.getObjectiveTotal(questId), NamedTextColor.WHITE)));
                lore.add(Component.empty());
                lore.add(Component.text("» Kattints az elvállaláshoz", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            }
            case AVAILABLE -> {
                final String hint = questManager.describeStartHint(questId);
                if (!hint.isBlank()) {
                    lore.add(GuiUtil.grey(hint));
                }
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
