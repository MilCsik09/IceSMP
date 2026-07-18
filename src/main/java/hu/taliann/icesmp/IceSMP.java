package hu.taliann.icesmp;

import hu.taliann.icesmp.core.IceSMPCore;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the Bukkit/Paper plugin system.
 */
public final class IceSMP extends JavaPlugin {

    private IceSMPCore core;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        core = new IceSMPCore(this);
        core.enable();
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.disable();
        }
    }
}

