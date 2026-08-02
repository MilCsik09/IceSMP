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

    /** Rituálé-idézés: kellékkel a kézben jobb-klikk (levegő/blokk) — éjjel. */
    @EventHandler
    public void onRitual(final org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Egy fizikai jobb kattintás MAIN- és OFF-HAND eventet is ad, a rítus viszont mindkettőben
        // a main-handet olvasta: elég nagy stacknél ugyanaz a rítus kétszer futott és KÉT ritka
        // kelléket fogyasztott.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Player player = event.getPlayer();
        final ItemStack hand = player.getInventory().getItemInMainHand();
        final boolean heart = captureItemFactory.isHeartItem(hand);
        final boolean seal = captureItemFactory.isSealItem(hand);
        if (!heart && !seal) {
            return;
        }
        event.setCancelled(true);
        if ((heart && !petManager.isUnholy(player)) || (seal && !petManager.isWarlock(player))) {
            player.sendActionBar(messageManager.getMessage("pet-wrong-spec",
                    "<red>Ezt az itemet nem a te specializációd használja.</red>"));
            return;
        }
        final ItemStack reserved = reserveOne(hand);
        petManager.ritualSummonV2(player).whenComplete((result, failure) ->
                petManager.runOnPlayer(player, () -> {
                    if (failure != null || result == null || !result.committed()) {
                        refund(player, reserved);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                        player.sendActionBar(messageManager.getMessage(result == null || result.error().isEmpty()
                                ? "pet-persistence-failed" : result.error(),
                                "<red>A rituálé most nem végezhető el.</red>"));
                        return;
                    }
                    if (!result.error().isEmpty()) {
                        player.sendActionBar(messageManager.getMessage(result.error(),
                                "<yellow>A társ tartósan létrejött, de a runtime aktiválás újrapróbálást igényel.</yellow>"));
                        return;
                    }
                    player.getWorld().spawnParticle(org.bukkit.Particle.SOUL, player.getLocation().add(0, 1, 0),
                            40, 0.8D, 0.8D, 0.8D, 0.03D);
                    player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.0F, 0.5F);
                    player.sendActionBar(messageManager.getMessage("pet-ritual-done",
                            "<dark_green>A rituálé beteljesült — a társad a hívásodra vár.</dark_green>"));
                }));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final Player player = event.getPlayer();
        final ItemStack hand = player.getInventory().getItemInMainHand();

        if (captureItemFactory.isPetArmorItem(hand)) {
            event.setCancelled(true);
            final ItemStack reserved = reserveOne(hand);
            petManager.equipArmorV2(player, event.getRightClicked()).whenComplete((result, failure) ->
                    petManager.runOnPlayer(player, () -> {
                        if (failure != null || result == null || !result.committed()) {
                            refund(player, reserved);
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                            player.sendActionBar(messageManager.getMessage(result == null || result.error().isEmpty()
                                    ? "pet-persistence-failed" : result.error(),
                                    "<red>A Társvértet csak a saját, kint lévő társadra adhatod fel.</red>"));
                            return;
                        }
                        if (!result.error().isEmpty()) {
                            player.sendActionBar(messageManager.getMessage(result.error(),
                                    "<yellow>A Társvért tartósan elment, de a runtime frissítés újrapróbálást igényel.</yellow>"));
                            return;
                        }
                        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
                        player.sendMessage(messageManager.getMessage("pet-armor-equipped",
                                "<gold>A társad felöltötte a Társvértet — páncélt és életerőt kapott.</gold>"));
                    }));
            return;
        }

        final boolean beast = captureItemFactory.isBeastCapture(hand);
        final boolean necro = captureItemFactory.isNecroCapture(hand);
        if (!beast && !necro) {
            return;
        }

        // Block the vanilla interaction (e.g. leashing) regardless of outcome.
        event.setCancelled(true);

        if ((beast && !petManager.isBeastMaster(player)) || (necro && !petManager.isNecromancer(player))) {
            player.sendActionBar(messageManager.getMessage("pet-wrong-spec", "<red>Ezt az itemet nem a te specializációd használja.</red>"));
            return;
        }

        final ItemStack reserved = reserveOne(hand);
        petManager.captureV2(player, event.getRightClicked()).whenComplete((result, failure) ->
                petManager.runOnPlayer(player, () -> {
                    if (failure != null || result == null || !result.committed()) {
                        refund(player, reserved);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                        player.sendActionBar(messageManager.getMessage(result == null || result.error().isEmpty()
                                ? "pet-persistence-failed" : result.error(),
                                "<red>Ezt a lényt nem tudod befogni.</red>"));
                        return;
                    }
                    if (!result.error().isEmpty()) {
                        player.sendActionBar(messageManager.getMessage(result.error(),
                                "<yellow>A társ tartósan elment, de a runtime aktiválás újrapróbálást igényel.</yellow>"));
                        return;
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.3F);
                    player.sendMessage(messageManager.getMessage("pet-captured", "<green>Új társat fogadtál be!</green>"));
                }));
    }

    private static ItemStack reserveOne(final ItemStack hand) {
        final ItemStack reserved = hand.clone();
        reserved.setAmount(1);
        hand.setAmount(hand.getAmount() - 1);
        return reserved;
    }

    private static void refund(final Player player, final ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
