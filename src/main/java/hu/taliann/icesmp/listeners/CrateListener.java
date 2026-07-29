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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Physical crate interaction and crate-key craft/fuel protection. */
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return; // One physical click also emits an off-hand event; never process it twice.
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
        final CrateManager.CrateDefinition definition = crateManager.definition(crateId);
        final int requested = player.isSneaking() && definition != null && definition.massOpenEnabled()
                ? definition.massOpenMaximum() : 1;
        crateManager.requestOpen(player, crateId, block.getLocation(), requested);
    }

    private void sendInfo(final Player player, final String crateId) {
        final CrateManager.CrateDefinition definition = crateManager.definition(crateId);
        final CrateManager.AccessDecision access = crateManager.accessDecision(player, definition);
        if (!access.allowed()) {
            player.sendMessage(messageManager.get(access.errorKey(), switch (access.errorKey()) {
                case "crate-no-permission" -> "&cEhhez a ládához nincs jogosultságod.";
                case "crate-world-disabled" -> "&cEz a láda ebben a világban nem használható.";
                default -> "&cEz a láda jelenleg hibás vagy le van tiltva.";
            }));
            return;
        }
        player.sendMessage(messageManager.get("crate-need-key",
                "&e%s &7— %s kulcs kell nyitásonként. Ár: &f%s %s &7(&e/crate buy %s&7)",
                crateManager.displayName(crateId), definition.requiredKeys(),
                currencyManager.formatBalance(definition.keyPriceAmount()),
                definition.keyPriceCurrency().getDisplayName(), crateId));
        player.sendMessage(messageManager.get("crate-preview-hint",
                "&7Jutalmak: &e/crate preview %s&7. Lopakodva kattintva többszörös nyitás kérhető.", crateId));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        for (final ItemStack itemStack : event.getInventory().getMatrix()) {
            if (crateKeyFactory.isKey(itemStack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(final FurnaceBurnEvent event) {
        if (crateKeyFactory.isKey(event.getFuel())) {
            event.setCancelled(true);
        }
    }
}
