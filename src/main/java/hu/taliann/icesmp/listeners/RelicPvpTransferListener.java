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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PvP weapon-relic transfer: when a player is slain, weapon relics among
 * their drops change owner to the killer — passive relics stay protected
 * and keep their owner.
 */
public final class RelicPvpTransferListener implements Listener {

    private final JavaPlugin plugin;
    private final RelicManager relicManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public RelicPvpTransferListener(final JavaPlugin plugin, final RelicManager relicManager,
                                    final ConfigManager configManager,
                                    final MessageManager messageManager) {
        this.plugin = plugin;
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

        // The drops list and the victim are the death event's own entity (this runs on the victim's
        // region thread), so transferring ownership (only reads killer.getUniqueId()) and messaging
        // the victim are safe here. The killer is a different entity, so collect the claimed names and
        // deliver the killer's messages on the killer's own scheduler (Folia cross-entity rule).
        final List<String> claimedNames = new ArrayList<>();
        for (final ItemStack drop : event.getDrops()) {
            final RelicDefinition definition = relicManager.identify(drop);
            if (definition == null || !relicManager.isWeaponRelic(definition.id())) {
                continue;
            }

            relicManager.transferOwnership(definition.id(), drop, killer);
            claimedNames.add(definition.displayName());
            victim.sendMessage(messageManager.getMessage(
                    "relic.pvp-lost",
                    "<red>A(z) <white>{relic}</white> elhagyott — legyőződ kezébe került.</red>",
                    Map.of("relic", definition.displayName())
            ));
        }

        if (claimedNames.isEmpty()) {
            return;
        }
        killer.getScheduler().run(plugin, task -> {
            for (final String relicName : claimedNames) {
                killer.sendMessage(messageManager.getMessage(
                        "relic.pvp-claimed",
                        "<gold>⚔ A(z) <white>{relic}</white> új gazdát választott: mostantól téged szolgál!</gold>",
                        Map.of("relic", relicName)
                ));
            }
        }, null);
    }
}
