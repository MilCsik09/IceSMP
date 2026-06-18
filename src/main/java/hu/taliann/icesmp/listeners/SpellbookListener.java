package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.SpellbookGUI;
import hu.taliann.icesmp.gui.SpellbookHolder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles clicks in the spellbook GUI: clicking an unlocked spell selects it on the
 * catalyst; the arrows page through the book. All clicks are cancelled so nothing can
 * be removed from the menu.
 */
public final class SpellbookListener implements Listener {

    private static final int TOP_SIZE = 54;

    private final AbilityCatalystListener catalyst;

    public SpellbookListener(final AbilityCatalystListener catalyst) {
        this.catalyst = catalyst;
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpellbookHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.getOwnerUuid())) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= TOP_SIZE) {
            return; // click in the player's own inventory
        }

        if (slot == SpellbookGUI.PREV_SLOT) {
            catalyst.openSpellbook(player, Math.max(0, holder.getPage() - 1));
            return;
        }
        if (slot == SpellbookGUI.NEXT_SLOT) {
            catalyst.openSpellbook(player, holder.getPage() + 1);
            return;
        }

        final String spellId = holder.getSpellAt(slot);
        if (spellId == null) {
            return;
        }

        if (catalyst.selectSpell(player, spellId)) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.4F);
            catalyst.openSpellbook(player, holder.getPage()); // refresh the highlight
        }
    }
}
