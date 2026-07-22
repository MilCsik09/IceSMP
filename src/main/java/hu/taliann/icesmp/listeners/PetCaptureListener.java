package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.CaptureItemFactory;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Capture handler: right-clicking a valid mob with a spec-appropriate capture item
 * adopts it as the player's companion and consumes one item.
 */
public final class PetCaptureListener implements Listener {

    private final PetManager petManager;
    private final CaptureItemFactory captureItemFactory;
    private final MessageManager messageManager;

    public PetCaptureListener(final PetManager petManager, final CaptureItemFactory captureItemFactory,
                              final MessageManager messageManager) {
        this.petManager = petManager;
        this.captureItemFactory = captureItemFactory;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final Player player = event.getPlayer();
        final ItemStack hand = player.getInventory().getItemInMainHand();
        final boolean beast = captureItemFactory.isBeastCapture(hand);
        final boolean necro = captureItemFactory.isNecroCapture(hand);
        if (!beast && !necro) {
            return;
        }

        // Block the vanilla interaction (e.g. leashing) regardless of outcome.
        event.setCancelled(true);

        if ((beast && !petManager.isBeastMaster(player)) || (necro && !petManager.isDarkCapturer(player))) {
            player.sendActionBar(messageManager.getMessage("pet-wrong-spec", "<red>Ezt az itemet nem a te specializációd használja.</red>"));
            return;
        }

        final String error = petManager.capture(player, event.getRightClicked());
        if (error != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendActionBar(messageManager.getMessage(error, "<red>Ezt a lényt nem tudod befogni.</red>"));
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.3F);
        player.sendMessage(messageManager.getMessage("pet-captured", "<green>Új társat fogadtál be!</green>"));
    }
}
