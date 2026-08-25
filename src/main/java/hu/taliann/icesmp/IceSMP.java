package hu.taliann.icesmp;

import hu.taliann.icesmp.core.IceSMPCore;
import hu.taliann.icesmp.integration.ProtectionBridge;
import hu.taliann.icesmp.listeners.ResourcePackListener;
import hu.taliann.icesmp.prologue.PrologueRuntime;
import hu.taliann.icesmp.prologue.PrologueRuntimeConfigOverlay;
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
        core = new IceSMPCore(this, resourcePackListener::reloadAndResend, resourcePackListener::isLoaded);
        core.enable();
        PrologueRuntime.install(this);
        PrologueRuntimeConfigOverlay.install(this);

        if (getServer().getPluginManager().getPlugin("WorldGuard") != null
                && !ProtectionBridge.isHealthy()) {
            getLogger().warning("WorldGuard észlelve, de a ProtectionBridge nem üzemképes — "
                    + "az események fail-open módon továbbindulnak, az új claimek pedig "
                    + "biztonsági okból elutasítódnak. A kiváltó ok a közvetlenül előtte lévő "
                    + "WorldGuard-híd stack trace-ben látható.");
        }

        resourcePackListener.resendCurrent();
        hu.taliann.icesmp.professions.ProfessionsPaperRuntimeProbe.maybeRun(this, core);
        hu.taliann.icesmp.itemization.PaperSourceIntegrityRuntimeProbe.maybeRun(this, core);
        hu.taliann.icesmp.quest.QuestItemContentIntegrityPaperRuntimeProbe.maybeRun(this, core);
    }

    @Override
    public void onDisable() {
        try {
            PrologueRuntimeConfigOverlay.shutdown();
            PrologueRuntime.shutdown();
            if (core != null) core.disable();
        } finally {
            if (resourcePackListener != null) resourcePackListener.close();
            TransientEntities.shutdown();
            hu.taliann.icesmp.itemization.PaperSourceIntegrityRuntimeProbe
                    .verifyFacadesClearedAfterDisable(this);
        }
    }
}
