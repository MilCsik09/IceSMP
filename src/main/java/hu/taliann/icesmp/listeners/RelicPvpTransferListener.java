package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * PvP weapon-relic transfer (todo.md rule): when a player is slain, weapon
 * relics among their drops change owner to the killer — passive relics stay
 * protected and keep their owner.
 */
public final class RelicPvpTransferListener implements Listener {

    private final RelicManager relicManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public RelicPvpTransferListener(final RelicManager relicManager, final ConfigManager configManager,
                                    final MessageManager messageManager) {
        this.relicManager = relicManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        if (!configManager.getBoolean("relics.pvp-transfer.enabled", true)) {
            return;
        }

        final Player victim = event.getEntity();
        final Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        for (final ItemStack drop : event.getDrops()) {
            final RelicDefinition definition = relicManager.identify(drop);
            if (definition == null || !relicManager.isWeaponRelic(definition.id())) {
                continue;
            }

            relicManager.transferOwnership(definition.id(), drop, killer);
            killer.sendMessage(messageManager.getMessage(
                    "relic.pvp-claimed",
                    "<gold>⚔ A(z) <white>{relic}</white> új gazdát választott: mostantól téged szolgál!</gold>",
                    Map.of("relic", definition.displayName())
            ));
            victim.sendMessage(messageManager.getMessage(
                    "relic.pvp-lost",
                    "<red>A(z) <white>{relic}</white> elhagyott — legyőződ kezébe került.</red>",
                    Map.of("relic", definition.displayName())
            ));
        }
    }
}
