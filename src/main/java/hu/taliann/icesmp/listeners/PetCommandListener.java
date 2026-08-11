package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.MinionManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;

/**
 * Pet command control: sneak + right-click your own minion to cycle its stance
 * (ACTIVE → PASSIVE → STAY → ACTIVE), like the WoW hunter pet bar.
 */
public final class PetCommandListener implements Listener {

    private final MinionManager minionManager;
    private final hu.taliann.icesmp.managers.PetManager petManager;
    private final MessageManager messageManager;

    public PetCommandListener(final MinionManager minionManager, final hu.taliann.icesmp.managers.PetManager petManager,
                              final MessageManager messageManager) {
        this.minionManager = minionManager;
        this.petManager = petManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        // main+off kéz duplán tüzel — a stance-váltás különben kettőt ugrana.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        if (!player.isSneaking() || !(event.getRightClicked() instanceof Mob minion)) {
            return;
        }

        if (!minionManager.isOwnedBy(minion, player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        // A tartós companion állásmódja a Profile v2 aggregátumban él; a spell-idézett
        // minion állásmódja továbbra is az entitás-PDC-ben. Tartós petmutáció után
        // minden Bukkit-visszajelzés visszaugrik a játékos schedulerére.
        if (petManager.isActivePetEntity(player, minion)) {
            petManager.cycleStanceV2(player).whenComplete((stance, failure) ->
                    petManager.runOnPlayer(player, () -> {
                        if (failure != null || stance == null) {
                            player.sendActionBar(messageManager.get("pet-persistence-failed",
                                    "&cA társ parancsát nem sikerült menteni."));
                            return;
                        }
                        showStance(player, stance);
                    }));
            return;
        }
        showStance(player, minionManager.cycleStance(minion));
    }

    private void showStance(final Player player, final MinionManager.Stance stance) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.3F);
        player.sendActionBar(messageManager.getMessage(
                "pet-stance",
                "<gray>A társ parancsa: <gold>{stance}</gold></gray>",
                Map.of("stance", stanceLabel(stance))
        ));
    }

    private String stanceLabel(final MinionManager.Stance stance) {
        return switch (stance) {
            case ACTIVE -> "Támadás";
            case PASSIVE -> "Passzív";
            case STAY -> "Maradj";
        };
    }
}
