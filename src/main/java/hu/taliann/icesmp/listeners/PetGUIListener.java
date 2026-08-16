package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.PetGUI;
import hu.taliann.icesmp.gui.PetGUIHolder;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Drives the companion GUI through PetManager and rebuilds it only after the
 * asynchronous durable mutation has completed.
 */
public final class PetGUIListener implements Listener {

    private final PetManager petManager;
    private final MessageManager messageManager;

    public PetGUIListener(final PetManager petManager, final MessageManager messageManager) {
        this.petManager = petManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PetGUIHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.getOwnerUuid().equals(player.getUniqueId())) {
            return;
        }

        String action = holder.getActionAt(event.getRawSlot());
        if (action == null) {
            return;
        }
        if (event.isRightClick() && action.startsWith("SELECT:")) {
            action = "REQUEST_RELEASE:" + action.substring("SELECT:".length());
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);

        if ("CLOSE".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("BACK".equals(action)) {
            PetGUI.open(player, petManager, messageManager);
            return;
        }
        if (action.startsWith("REQUEST_RELEASE:")) {
            final UUID companionId = parseId(action.substring("REQUEST_RELEASE:".length()));
            if (companionId != null) {
                PetGUI.openReleaseConfirmation(player, petManager, messageManager, companionId);
            }
            return;
        }
        if (action.startsWith("HINT:")) {
            player.closeInventory();
            player.sendMessage(messageManager.get("pet-name-usage", "&cHasználat: /pet name <név>"));
            return;
        }
        player.closeInventory();
        if ("SUMMON".equals(action)) {
            petManager.summonV2(player).whenComplete((error, failure) -> refresh(player, () -> {
                if (failure != null) {
                    player.sendMessage(messageManager.get("pet-persistence-failed",
                            "&cA társ idézését nem sikerült tartósan menteni."));
                } else if (error != null) {
                    player.sendMessage(messageManager.get(error, "&cMost nem tudsz társat idézni."));
                } else {
                    player.sendMessage(messageManager.get("pet-summoned", "&aA társad megjelent melletted."));
                }
            }));
            return;
        }
        if ("DISMISS".equals(action)) {
            petManager.dismissV2(player).whenComplete((committed, failure) -> refresh(player, () ->
                    player.sendMessage(failure == null && Boolean.TRUE.equals(committed)
                            ? messageManager.get("pet-dismissed", "&7A társad eltűnt.")
                            : messageManager.get("pet-none", "&7Nincs aktív társad, vagy a mentés sikertelen."))));
            return;
        }
        if (action.startsWith("SELECT:")) {
            final UUID companionId = parseId(action.substring("SELECT:".length()));
            if (companionId == null) return;
            petManager.selectV2(player, companionId).whenComplete((error, failure) -> refresh(player, () -> {
                if (failure != null) {
                    player.sendMessage(messageManager.get("pet-persistence-failed",
                            "&cA társválasztást nem sikerült tartósan menteni."));
                } else if (error != null) {
                    player.sendMessage(messageManager.get(error, "&cMost nem választhatod ezt a társat."));
                } else {
                    player.sendMessage(messageManager.get("pet-selected",
                            "&aKiválasztottad és magad mellé hívtad a társadat."));
                }
            }));
            return;
        }
        if (action.startsWith("CONFIRM_RELEASE:")) {
            final UUID companionId = parseId(action.substring("CONFIRM_RELEASE:".length()));
            if (companionId == null || holder.getMode() != PetGUIHolder.Mode.RELEASE_CONFIRM
                    || !companionId.equals(holder.getCompanionId())) return;
            petManager.releaseV2(player, companionId).whenComplete((committed, failure) -> refresh(player, () ->
                    player.sendMessage(failure == null && Boolean.TRUE.equals(committed)
                            ? messageManager.get("pet-released",
                                    "&7A társad visszatért a vadonba; a helye felszabadult.")
                            : messageManager.get("pet-none", "&7A társ elengedése nem sikerült."))));
            return;
        }
        if (action.startsWith("STANCE:")) {
            final hu.taliann.icesmp.managers.MinionManager.Stance stance;
            try {
                stance = hu.taliann.icesmp.managers.MinionManager.Stance.valueOf(
                        action.substring("STANCE:".length()));
            } catch (final IllegalArgumentException invalid) {
                return;
            }
            petManager.setStanceV2(player, stance).whenComplete((committed, failure) -> refresh(player, () -> {
                if (failure != null || !Boolean.TRUE.equals(committed)) {
                    player.sendMessage(messageManager.get("pet-persistence-failed",
                            "&cA társ parancsát nem sikerült tartósan menteni."));
                    return;
                }
                player.sendMessage(messageManager.getMessage("pet-stance",
                        "<gray>Társ parancs: <gold>{stance}</gold></gray>", Map.of("stance", switch (stance) {
                            case ACTIVE -> "Támadás";
                            case PASSIVE -> "Passzív";
                            case STAY -> "Maradj";
                        })));
            }));
        }
    }

    private void refresh(final Player player, final Runnable feedback) {
        petManager.runOnPlayer(player, () -> {
            feedback.run();
            if (player.isOnline()) {
                PetGUI.open(player, petManager, messageManager);
            }
        });
    }

    private static UUID parseId(final String raw) {
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException invalid) {
            return null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PetGUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PetGUIHolder holder) {
            holder.setInventory(null);
        }
    }
}
