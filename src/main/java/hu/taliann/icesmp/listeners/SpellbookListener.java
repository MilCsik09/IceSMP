package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.SpellbookGUI;
import hu.taliann.icesmp.gui.SpellbookHolder;
import hu.taliann.icesmp.managers.SpellFavoritesManager;
import hu.taliann.icesmp.managers.SpellMasteryManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Handles spellbook selection and the existing favorites-backed active kit. */
public final class SpellbookListener implements Listener {

    private static final int TOP_SIZE = 54;

    private final AbilityCatalystListener catalyst;
    private final SpellFavoritesManager favoritesManager;
    private final SpellMasteryManager masteryManager;
    private final MessageManager messages;

    public SpellbookListener(final AbilityCatalystListener catalyst,
                             final SpellFavoritesManager favoritesManager,
                             final SpellMasteryManager masteryManager,
                             final MessageManager messages) {
        this.catalyst = catalyst;
        this.favoritesManager = favoritesManager;
        this.masteryManager = masteryManager;
        this.messages = messages;
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpellbookHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.getOwnerUuid())) return;

        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= TOP_SIZE) return;
        if (slot == SpellbookGUI.PREV_SLOT) {
            catalyst.openSpellbook(player, Math.max(0, holder.getPage() - 1),
                    holder.isOnlyUnlocked());
            return;
        }
        if (slot == SpellbookGUI.NEXT_SLOT) {
            catalyst.openSpellbook(player, holder.getPage() + 1, holder.isOnlyUnlocked());
            return;
        }
        if (slot == SpellbookGUI.FILTER_SLOT) {
            catalyst.openSpellbook(player, 0, !holder.isOnlyUnlocked());
            return;
        }

        final String spellId = holder.getSpellAt(slot);
        if (spellId == null) return;
        if (event.isRightClick() && !event.isShiftClick()) {
            masteryManager.upgrade(player, spellId).whenComplete((result, failure) ->
                    masteryManager.runOnOwnerThread(player, () -> {
                        if (!player.isOnline()) return;
                        if (failure != null) {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8F, 0.8F);
                            player.sendMessage(messages.getComponent("spell-mastery-failed",
                                    "&cA spell-mastery tartós mentése sikertelen; az állapot nem változott."));
                        } else if (result == SpellMasteryManager.UpgradeResult.SUCCESS) {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9F, 1.4F);
                            player.sendMessage(messages.getComponent("spell-mastery-upgraded-gui",
                                    "&aSpell-mastery fejlesztve."));
                        } else if (result == SpellMasteryManager.UpgradeResult.MAX_RANK) {
                            player.sendMessage(messages.getComponent("spell-mastery-max",
                                    "&7Ez a képesség már maximális mesterségű."));
                        } else {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8F, 0.8F);
                            player.sendMessage(messages.getComponent("spell-mastery-poor",
                                    "&cNincs elég frakcióvalutád."));
                        }
                        catalyst.openSpellbook(player, holder.getPage(), holder.isOnlyUnlocked());
                    }));
            return;
        }
        if (event.isShiftClick()) {
            final int maximum = catalyst.activeKitLimit(player);
            favoritesManager.toggleCapped(player, spellId, maximum)
                    .whenComplete((result, failure) -> favoritesManager.runOnOwnerThread(player, () -> {
                        if (!player.isOnline()) return;
                        if (failure != null) {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,
                                    0.8F, 0.8F);
                            player.sendMessage(Component.text(
                                    "A kedvenc tartós mentése sikertelen; az állapot nem változott.",
                                    NamedTextColor.RED));
                            return;
                        }
                        if (result == SpellFavoritesManager.ToggleResult.LIMIT_REACHED) {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,
                                    0.8F, 0.8F);
                            player.sendMessage(Component.text(
                                    "Az aktív készleted legfeljebb " + maximum
                                            + " spell lehet. Vegyél ki előbb egy kedvencet.",
                                    NamedTextColor.RED));
                            return;
                        }
                        player.playSound(player.getLocation(),
                                result == SpellFavoritesManager.ToggleResult.ADDED
                                        ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP
                                        : Sound.UI_BUTTON_CLICK,
                                0.8F, 1.2F);
                        catalyst.openSpellbook(player, holder.getPage(), holder.isOnlyUnlocked());
                    }));
            return;
        }

        if (catalyst.selectSpell(player, spellId)) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.4F);
            catalyst.openSpellbook(player, holder.getPage(), holder.isOnlyUnlocked());
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SpellbookHolder) event.setCancelled(true);
    }
}
