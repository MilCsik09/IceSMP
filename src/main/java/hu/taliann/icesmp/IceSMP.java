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
    private final hu.taliann.icesmp.core.CommandLifecycle commands =
            new hu.taliann.icesmp.core.CommandLifecycle(this::isEnabled);
    private final java.util.concurrent.atomic.AtomicBoolean disableRequested =
            new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    public void registerCommand(final String label, final String description,
                                final java.util.Collection<String> aliases,
                                final io.papermc.paper.command.brigadier.BasicCommand command) {
        super.registerCommand(label, description, aliases, commands.wrap(command));
    }

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

        commands.open();
        resourcePackListener.resendCurrent();
        hu.taliann.icesmp.professions.ProfessionsPaperRuntimeProbe.maybeRun(this, core);
        hu.taliann.icesmp.itemization.PaperSourceIntegrityRuntimeProbe.maybeRun(this, core);
        hu.taliann.icesmp.quest.QuestItemContentIntegrityPaperRuntimeProbe.maybeRun(this, core);
        hu.taliann.icesmp.trash.TrashProductionRuntimeProbe.maybeRun(this, core);
    }

    @Override
    public void onDisable() {
        commands.close();
        try {
            PrologueRuntimeConfigOverlay.shutdown();
            PrologueRuntime.shutdown();
            if (core != null) {
                core.disable();
                hu.taliann.icesmp.trash.TrashProductionRuntimeProbe
                        .verifyCleanShutdown(this, core);
            }
        } finally {
            if (resourcePackListener != null) resourcePackListener.close();
            TransientEntities.shutdown();
            hu.taliann.icesmp.itemization.PaperSourceIntegrityRuntimeProbe
                    .verifyFacadesClearedAfterDisable(this);
        }
    }

    /** Closes admission immediately, then cleans up owners before retiring their schedulers. */
    public static void requestDisable(final JavaPlugin plugin) {
        if (plugin instanceof IceSMP iceSmp) {
            iceSmp.requestDisable();
        } else {
            plugin.getServer().getGlobalRegionScheduler().run(plugin,
                    task -> plugin.getServer().getPluginManager().disablePlugin(plugin));
        }
    }

    private void requestDisable() {
        final java.util.concurrent.CompletableFuture<Void> drained = commands.close();
        if (!disableRequested.compareAndSet(false, true)) return;
        try {
            getServer().getGlobalRegionScheduler().run(this, task -> {
                if (!isEnabled()) return;
                try {
                    if (core != null) core.beginPresentationShutdown();
                    final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                    getServer().getGlobalRegionScheduler().runAtFixedRate(this, poll -> {
                        if (!drained.isDone() && System.nanoTime() < deadline) return;
                        poll.cancel();
                        if (!drained.isDone()) getLogger().severe(
                                "IceSMP command drain deadline exceeded; shutdown remains fail-closed.");
                        try {
                            prepareOwnedShutdown();
                        } catch (final RuntimeException | Error failure) {
                            disableAfterPreparationFailure(failure);
                        }
                    }, 1L, 1L);
                } catch (final RuntimeException | Error failure) {
                    disableAfterPreparationFailure(failure);
                }
            });
        } catch (final RuntimeException failure) {
            getLogger().severe("IceSMP fail-closed shutdown scheduling failed: " + failure);
        }
    }

    private void disableAfterPreparationFailure(final Throwable failure) {
        getLogger().severe("IceSMP shutdown preparation failed; owner cleanup is not confirmed: " + failure);
        getServer().getPluginManager().disablePlugin(this);
    }

    private void prepareOwnedShutdown() {
        if (!isEnabled()) return;
        java.util.concurrent.CompletableFuture<Void> presentation =
                java.util.concurrent.CompletableFuture.completedFuture(null);
        try {
            if (resourcePackListener != null) presentation = resourcePackListener.prepareClose(
                    player -> { if (core != null) core.cleanupPresentation(player); });
        } catch (final RuntimeException | Error failure) {
            getLogger().severe("IceSMP client cleanup could not start: " + failure);
            presentation = java.util.concurrent.CompletableFuture.failedFuture(failure);
        }
        try {
            if (core != null) core.prepareDisable();
        } catch (final RuntimeException | Error failure) {
            getLogger().severe("IceSMP native shutdown failed: " + failure);
        }
        final var cleanup = java.util.concurrent.CompletableFuture.allOf(presentation,
                core == null ? java.util.concurrent.CompletableFuture.completedFuture(null)
                        : core.playerShutdownCompletion());
        final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            if (!cleanup.isDone() && System.nanoTime() < deadline) return;
            task.cancel();
            if (!cleanup.isDone() || cleanup.isCompletedExceptionally()) {
                getLogger().warning("IceSMP owner cleanup is incomplete; client restoration is not confirmed.");
            }
            getServer().getPluginManager().disablePlugin(this);
        }, 1L, 1L);
    }
}
