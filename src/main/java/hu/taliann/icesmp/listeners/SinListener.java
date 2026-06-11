package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;

/**
 * Records sins for player murder: the killer gains a sin point, and the
 * MetelytepoManager exiles repeat offenders to the Dark faction once the
 * configured threshold is reached.
 */
public final class SinListener implements Listener {

    private final MetelytepoManager metelytepoManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public SinListener(final MetelytepoManager metelytepoManager, final ConfigManager configManager,
                       final MessageManager messageManager) {
        this.metelytepoManager = metelytepoManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        if (!configManager.getBoolean("factions.sins.murder-counts", true)) {
            return;
        }

        final Player victim = event.getEntity();
        final Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        final int sinCount = metelytepoManager.addSin(killer, 1);
        final int threshold = Math.max(0, configManager.getInt("factions.sins.exile-threshold", 4));
        killer.sendMessage(messageManager.getMessage(
                "sinner.sin-recorded",
                "<dark_purple>Bűnt követtél el: gyilkosság. <gray>Bűneid: <white>{count}</white>/<white>{threshold}</white></gray></dark_purple>",
                Map.of("count", String.valueOf(sinCount), "threshold", String.valueOf(threshold))
        ));
    }
}
