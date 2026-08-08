package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.QuestLogGUI;
import hu.taliann.icesmp.gui.QuestLogHolder;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Drives the quest log GUI: tab switches, page navigation, and accept/abandon
 * clicks. Every mutation goes through the QuestManager. The click runs on the
 * clicking player's own region thread (inventory events do), so quest-state
 * PDC writes are Folia-safe without a hop.
 */
public final class QuestLogListener implements Listener {

    private final QuestManager questManager;
    private final MessageManager messageManager;

    public QuestLogListener(final QuestManager questManager, final MessageManager messageManager) {
        this.questManager = questManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof QuestLogHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.getOwnerUuid().equals(player.getUniqueId())) {
            return;
        }

        final int slot = event.getRawSlot();
        switch (slot) {
            case QuestLogGUI.TAB_ACTIVE_SLOT -> reopen(player, 0, QuestLogHolder.Tab.ACTIVE);
            case QuestLogGUI.TAB_READY_SLOT -> reopen(player, 0, QuestLogHolder.Tab.READY);
            case QuestLogGUI.TAB_BOARD_SLOT -> reopen(player, 0, QuestLogHolder.Tab.BOARD);
            case QuestLogGUI.TAB_AVAILABLE_SLOT -> reopen(player, 0, QuestLogHolder.Tab.AVAILABLE);
            case QuestLogGUI.TAB_COMPLETED_SLOT -> reopen(player, 0, QuestLogHolder.Tab.COMPLETED);
            case QuestLogGUI.PREV_SLOT -> reopen(player, holder.getPage() - 1, holder.getTab());
            case QuestLogGUI.NEXT_SLOT -> reopen(player, holder.getPage() + 1, holder.getTab());
            case QuestLogGUI.MENU_SLOT -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                player.performCommand("menu");
            }
            default -> handleQuestClick(player, holder, event, slot);
        }
    }

    private void handleQuestClick(final Player player, final QuestLogHolder holder,
                                  final InventoryClickEvent event, final int slot) {
        final String questId = holder.getQuestAt(slot);
        final QuestLogHolder.Action action = holder.getActionAt(slot);
        if (questId == null || action == null) {
            return;
        }

        switch (action) {
            case ACCEPT -> {
                // Csak a Megbízások-fül ad felvételt, QUEST_BOARD forrás-kontextussal —
                // a napló nem távoli accept-felület más forrású questekhez.
                final String blocker = questManager.acceptWithSource(player, questId,
                        hu.taliann.icesmp.quest.QuestSourceContext.board());
                if (blocker != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0F, 1.1F);
                    player.sendMessage(messageManager.get("quest-accept-success", "&aKüldetés felvéve: &e%s",
                            questManager.getDisplayName(questId)));
                }
                reopen(player, holder.getPage(), holder.getTab());
            }
            case TRACK, ABANDON -> {
                if (event.isShiftClick()) {
                    if (questManager.abandon(player, questId)) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
                        player.sendMessage(messageManager.get("quest-abandon-success", "&eKüldetés eldobva: &f%s",
                                questManager.getDisplayName(questId)));
                    }
                    reopen(player, holder.getPage(), holder.getTab());
                    return;
                }
                final boolean untrack = questId.equals(questManager.getTracked(player));
                questManager.setTracked(player, untrack ? null : questId);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.3F);
                player.sendMessage(untrack
                        ? messageManager.get("quest-untracked", "&7Küldetés-követés kikapcsolva.")
                        : messageManager.get("quest-tracked", "&aKövetett küldetés: &e%s",
                                questManager.getDisplayName(questId)));
                reopen(player, holder.getPage(), holder.getTab());
            }
        }
    }

    private void reopen(final Player player, final int page, final QuestLogHolder.Tab tab) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
        QuestLogGUI.open(player, questManager, messageManager, Math.max(0, page), tab);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof QuestLogHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof QuestLogHolder holder) {
            holder.setInventory(null);
        }
    }
}
