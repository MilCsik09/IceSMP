package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.artifact.WorldWeaverArtifactBehavior;
import hu.taliann.icesmp.dev.weaver.api.ScalarTypeCodec;
import hu.taliann.icesmp.dev.weaver.api.WeaverTypeRegistry;
import hu.taliann.icesmp.dev.weaver.execution.FoliaWeaverOwnerRouter;
import hu.taliann.icesmp.dev.weaver.execution.WeaverExecutionCoordinator;
import hu.taliann.icesmp.dev.weaver.gui.WorldWeaverGUI;
import hu.taliann.icesmp.dev.weaver.gui.WorldWeaverGUIListener;
import hu.taliann.icesmp.dev.weaver.api.WorldWeaverProvider;
import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshotFactory;
import hu.taliann.icesmp.dev.weaver.subject.WeaverItemSlots;
import hu.taliann.icesmp.itemization.ItemIdentityService;
import hu.taliann.icesmp.managers.DevItemManager;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import org.bukkit.plugin.java.JavaPlugin;

/** Provider factories are injected by IceSMP composition; this runtime knows no named subsystem adapter. */
public final class WorldWeaverRuntime {
    private final FoliaWeaverOwnerRouter router;
    private final WorldWeaverKernel kernel;
    private final WorldWeaverGUIListener listener;
    private final WorldWeaverProviderRegistry providers;
    private final hu.taliann.icesmp.dev.weaver.persistence.WeaverJournal journal;
    private final hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryCoordinator recovery;
    private final hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryListener recoveryListener;
    private final JavaPlugin plugin;
    private final java.util.concurrent.atomic.AtomicBoolean maintaining = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile io.papermc.paper.threadedregions.scheduler.ScheduledTask maintenance;
    private volatile boolean started;
    private volatile boolean closed;
    public WorldWeaverRuntime(final JavaPlugin plugin, final DevItemManager artifacts, final ItemIdentityService identity,
            final java.util.List<java.util.function.Function<WeaverTypeRegistry, WorldWeaverProvider>> providerFactories) {
        this.plugin = java.util.Objects.requireNonNull(plugin);
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        providers = new WorldWeaverProviderRegistry(types, () -> System.nanoTime() / 1_000_000L);
        for (final var factory : java.util.List.copyOf(providerFactories)) providers.register(factory.apply(types));
        router = new FoliaWeaverOwnerRouter(plugin);
        final WeaverItemSlots slots = new WeaverItemSlots(identity);
        final SubjectSnapshotFactory snapshots = new SubjectSnapshotFactory(router, slots, providers);
        journal = new hu.taliann.icesmp.dev.weaver.persistence.WeaverJournal(new hu.taliann.icesmp.dev.weaver.persistence.YamlWeaverJournalStorage(
                plugin.getDataFolder(), new hu.taliann.icesmp.dev.weaver.persistence.WeaverJournalCodec(types), plugin.getLogger()), projection -> providers.projectionConsumers().validate(projection));
        recovery = new hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryCoordinator(journal, snapshots, providers, types);
        recoveryListener = new hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryListener(recovery);
        kernel = new WorldWeaverKernel(artifacts, providers, types, snapshots, slots,
                new WorldWeaverGUI(), new WeaverExecutionCoordinator(router, types, journal, () -> false), new hu.taliann.icesmp.dev.weaver.execution.WeaverUndoCoordinator(journal, providers), () -> started && !closed && journal.ready());
        listener = new WorldWeaverGUIListener(kernel);
        hu.taliann.icesmp.dev.artifact.DevArtifactRuntimeProbe.registerReadinessCheck("world_weaver_journal", () -> started && !closed && journal.ready());
        artifacts.bindInteractions(WorldWeaverArtifactBehavior.ID, kernel::interact, this::clearSession);
    }
    public void start() {
        providers.freezeAndValidate();
        journal.load().thenCompose(ignored -> recovery.start()).thenCompose(ignored -> journal.expireProjections(System.currentTimeMillis(), true)).whenComplete((ignored, failure) -> {
            if (failure == null && journal.ready() && !closed) {
                maintenance = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> maintainEffects(), 20L, 20L);
                if (closed) maintenance.cancel(); else started = true;
            }
            else if (!closed) plugin.getLogger().severe("Internal developer runtime unavailable; recovery required.");
        });
    }
    private void clearSession() {
        kernel.clearPlayerState(HiddenDevAuthority.PRIMARY_DEVELOPER);
        if (journal.ready()) journal.expireProjections(System.currentTimeMillis(), true).whenComplete((ignored, failure) -> {
            if (failure != null && !closed) plugin.getLogger().severe("Internal developer session cleanup requires recovery.");
        });
    }
    private void maintainEffects() {
        if (!closed && journal.ready() && maintaining.compareAndSet(false, true)) {
            journal.expireProjections(System.currentTimeMillis(), false).whenComplete((ignored, failure) -> maintaining.set(false));
        }
    }
    public WorldWeaverGUIListener listener() { return listener; }
    public hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryListener recoveryListener() { return recoveryListener; }
    public void shutdown() {
        closed = true; started = false;
        final var task = maintenance; if (task != null) task.cancel();
        kernel.shutdown(); recovery.close(); router.close();
        journal.expireProjections(System.currentTimeMillis(), true).handle((ignored, failure) -> null).thenCompose(ignored -> journal.close()).whenComplete((ignored, failure) -> {
            if (failure != null) plugin.getLogger().severe("Internal developer state shutdown requires recovery.");
        });
    }
}
