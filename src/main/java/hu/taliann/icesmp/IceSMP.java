package hu.taliann.icesmp;

import hu.taliann.icesmp.core.IceSMPCore;
import hu.taliann.icesmp.integration.ProtectionBridge;
import hu.taliann.icesmp.listeners.ResourcePackListener;
import hu.taliann.icesmp.utils.TransientEntities;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the Bukkit/Paper plugin system. */
public final class IceSMP extends JavaPlugin {

    private IceSMPCore core;
    private ResourcePackListener resourcePackListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        resourcePackListener = new ResourcePackListener(this);
        getServer().getPluginManager().registerEvents(resourcePackListener, this);

        TransientEntities.install(this);
        core = new IceSMPCore(this, resourcePackListener::reloadAndResend);
        core.enable();

        if (getServer().getPluginManager().getPlugin("WorldGuard") != null
                && !ProtectionBridge.isHealthy()) {
            getLogger().warning("WorldGuard észlelve, de a ProtectionBridge nem üzemképes — "
                    + "az események fail-open módon továbbindulnak, az új claimek pedig "
                    + "biztonsági okból elutasítódnak. A kiváltó ok a közvetlenül előtte lévő "
                    + "WorldGuard-híd stack trace-ben látható.");
        }

        // Hot plugin reloads may enable while players are already online. Every actual call is
        // scheduled onto the player's owning region thread by the listener.
        resourcePackListener.resendCurrent();
    }

    @Override
    public void onDisable() {
        try {
            if (core != null) {
                core.disable();
            }
        } finally {
            if (resourcePackListener != null) {
                resourcePackListener.close();
            }
            TransientEntities.shutdown();
        }
    }
}
