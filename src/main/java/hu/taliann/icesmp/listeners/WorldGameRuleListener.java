package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.core.IceSMPCore;
import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Keeps the configured client gamerules applied to worlds that load AFTER startup
 * (e.g. Multiverse-created worlds). The startup pass in {@code IceSMPCore.enable()}
 * only covers worlds already loaded; this catches the rest.
 */
public final class WorldGameRuleListener implements Listener {

    private final ConfigManager configManager;

    public WorldGameRuleListener(final ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler
    public void onWorldLoad(final WorldLoadEvent event) {
        if (configManager.getBoolean("settings.disable-locator-bar", true)) {
            IceSMPCore.disableLocatorBar(event.getWorld());
        }
    }
}
