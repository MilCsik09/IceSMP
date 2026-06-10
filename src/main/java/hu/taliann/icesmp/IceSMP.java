package hu.taliann.icesmp;

import hu.taliann.icesmp.core.IceSMPCore;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for IceSMP.
 * Entry point for the Bukkit/Paper plugin system.
 */
public final class IceSMP extends JavaPlugin {

    private IceSMPCore core;

    /**
     * Called when the plugin is enabled.
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();

        core = new IceSMPCore(this);
        core.enable();
    }

    /**
     * Called when the plugin is disabled.
     */
    @Override
    public void onDisable() {
        if (core != null) {
            core.disable();
        }
    }
}

