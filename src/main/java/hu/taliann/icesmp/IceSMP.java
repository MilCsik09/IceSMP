package hu.taliann.icesmp;

import hu.taliann.icesmp.core.IceSMPCore;
import hu.taliann.icesmp.utils.TransientEntities;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the Bukkit/Paper plugin system. */
public final class IceSMP extends JavaPlugin {

    private IceSMPCore core;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Must precede manager construction/spawns: CUSTOM entities publish an ownership-safe
        // lifecycle handle before any global world-event tick can observe their UUID.
        TransientEntities.install(this);
        core = new IceSMPCore(this);
        core.enable();
    }

    @Override
    public void onDisable() {
        try {
            if (core != null) {
                core.disable();
            }
        } finally {
            // Manager shutdowns requested their known entities first; finish any remaining registered
            // custom entity while schedulers are still available, then release all strong references.
            // finally: a core.disable() bármely hibája sem hagyhat élő entity-referenciákat hátra.
            TransientEntities.shutdown();
        }
    }
}
