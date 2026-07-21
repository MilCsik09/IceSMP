package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.CrateKeyFactory;
import hu.taliann.icesmp.managers.CrateManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Native crate opening: right-clicking a registered crate block with its matching key
 * consumes one key and rolls the reward ({@link CrateManager#open}); without (or with
 * the wrong) key it just shows info. Also protects crate keys from being consumed as
 * craft ingredients or furnace fuel (mirrors {@link CatalystCraftSafetyListener} —
 * TRIPWIRE_HOOK is a plain, otherwise-inert vanilla item so this only matters if a
 * server's resource pack repurposes the base material of some future crate).
 *
 * <p>Folia: the interact fires on the clicked block's region thread, which is the
 * player's own thread for a block they are standing next to — {@link CrateManager#open}
 * and the key consumption run there directly, no scheduler hop needed.
 */
public final class CrateListener implements Listener {

    private final CrateManager crateManager;
    private final CrateKeyFactory crateKeyFactory;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public CrateListener(final CrateManager crateManager, final CrateKeyFactory crateKeyFactory,
                         final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.crateManager = crateManager;
        this.crateKeyFactory = crateKeyFactory;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Egy fizikai katt main+off kézzel KÉTSZER tüzel — kulcs-duplafogyasztás lenne.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        final Block block = event.getClickedBlock();
        final String crateId = block == null ? null : crateManager.crateAt(block.getLocation());
        if (crateId == null) {
            return;
        }
        event.setCancelled(true);

        final Player player = event.getPlayer();
        final ItemStack hand = player.getInventory().getItemInMainHand();
        final String heldCrateId = crateKeyFactory.keyCrateId(hand);

        if (heldCrateId == null) {
            sendInfo(player, crateId);
            return;
        }
        if (!heldCrateId.equals(crateId)) {
            player.sendMessage(messageManager.get("crate-wrong-key", "&cEz a kulcs nem ehhez a ládához való."));
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        if (!crateManager.open(player, crateId, block.getLocation())) {
            // Extremely unlikely (config is broken between the click and the roll) — refund the key.
            player.getInventory().addItem(crateKeyFactory.createKey(crateId, 1));
            player.sendMessage(messageManager.get("crate-broken", "&cEnnek a ládának jelenleg nincs beállítva jutalom-táblája — a kulcsod visszakaptad."));
        }
    }

    private void sendInfo(final Player player, final String crateId) {
        final double price = crateManager.keyPriceAmount(crateId);
        player.sendMessage(messageManager.get("crate-need-key",
                "&e%s &7— kulcs kell hozzá! Ár: &f%s %s &7(&e/crate buy %s&7)",
                crateManager.displayName(crateId),
                currencyManager.formatBalance(price),
                crateManager.keyPriceCurrency(crateId).getDisplayName(),
                crateId));
    }

    /** Crate keys never get consumed as crafting ingredients. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        for (final ItemStack itemStack : event.getInventory().getMatrix()) {
            if (crateKeyFactory.isKey(itemStack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Crate keys never get burned as furnace fuel. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(final FurnaceBurnEvent event) {
        if (crateKeyFactory.isKey(event.getFuel())) {
            event.setCancelled(true);
        }
    }
}
