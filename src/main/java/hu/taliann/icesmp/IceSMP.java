package hu.taliann.icesmp;

import hu.taliann.icesmp.core.IceSMPCore;
import hu.taliann.icesmp.listeners.ResourcePackListener;
import hu.taliann.icesmp.utils.TransientEntities;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the Bukkit/Paper plugin system. */
public final class IceSMP extends JavaPlugin {

    private IceSMPCore core;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        TransientEntities.install(this);
        core = new IceSMPCore(this);
        core.enable();

        final ResourcePackListener resourcePackListener = new ResourcePackListener(this);
        getServer().getPluginManager().registerEvents(resourcePackListener, this);
        getServer().getOnlinePlayers().forEach(resourcePackListener::send);
    }

    @Override
    public void onDisable() {
        try {
            if (core != null) {
                core.disable();
            }
        } finally {
            TransientEntities.shutdown();
        }
    }
}
