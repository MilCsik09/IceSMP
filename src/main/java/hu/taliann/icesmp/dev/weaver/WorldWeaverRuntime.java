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
    private final hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceDispatcher influenceDispatcher;
    private final hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryCoordinator recovery;
    private final hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryListener recoveryListener;
    private final JavaPlugin plugin;
    private final hu.taliann.icesmp.dev.weaver.projection.WeaverProjectionDispatcher projectionDispatcher;
    private final java.util.concurrent.atomic.AtomicBoolean maintaining = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile io.papermc.paper.threadedregions.scheduler.ScheduledTask maintenance;
    private volatile boolean started;
    private volatile boolean closed;
    public WorldWeaverRuntime(final JavaPlugin plugin, final DevItemManager artifacts, final ItemIdentityService identity,
            final java.util.List<java.util.function.Function<hu.taliann.icesmp.dev.weaver.api.WeaverProviderServices, WorldWeaverProvider>> providerFactories) {
        this.plugin = java.util.Objects.requireNonNull(plugin);
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        providers = new WorldWeaverProviderRegistry(types, () -> System.nanoTime() / 1_000_000L);
        router = new FoliaWeaverOwnerRouter(plugin);
        final WeaverItemSlots slots = new WeaverItemSlots(identity);
        final SubjectSnapshotFactory snapshots = new SubjectSnapshotFactory(router, slots, providers);
        journal = new hu.taliann.icesmp.dev.weaver.persistence.WeaverJournal(new hu.taliann.icesmp.dev.weaver.persistence.YamlWeaverJournalStorage(
                plugin.getDataFolder(), new hu.taliann.icesmp.dev.weaver.persistence.WeaverJournalCodec(types), plugin.getLogger()), projection -> providers.projectionConsumers().validate(projection),
                System::currentTimeMillis, providers::resolveInfluenceLifetime);
        influenceDispatcher = new hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceDispatcher(journal, providers::observeInfluence);
        final var services = new hu.taliann.icesmp.dev.weaver.api.WeaverProviderServices(types,
                new hu.taliann.icesmp.dev.weaver.projection.JournalProjectionSource(journal, providers::projectionConsumers), router, providers);
        for (final var factory : java.util.List.copyOf(providerFactories)) providers.register(factory.apply(services));
        projectionDispatcher = new hu.taliann.icesmp.dev.weaver.projection.WeaverProjectionDispatcher(providers::reconcileProjections);
        final var areas = new hu.taliann.icesmp.dev.weaver.area.WeaverAreaEngine(router, new hu.taliann.icesmp.dev.weaver.area.FoliaWeaverAreaAccess(snapshots));
        recovery = new hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryCoordinator(journal, snapshots, providers, types, areas);
        recoveryListener = new hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryListener(recovery);
        kernel = new WorldWeaverKernel(artifacts, providers, types, snapshots, slots,
                new WorldWeaverGUI(), new WeaverExecutionCoordinator(router, types, journal, () -> false), new hu.taliann.icesmp.dev.weaver.execution.WeaverUndoCoordinator(journal, providers, areas), areas, () -> started && !closed && journal.ready());
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
            journal.expireProjections(System.currentTimeMillis(), false)
                    .thenCompose(ignored -> closed ? java.util.concurrent.CompletableFuture.completedFuture(null) : projectionDispatcher.pulse(journal.snapshot().projections()))
                    .thenCompose(ignored -> closed ? java.util.concurrent.CompletableFuture.completedFuture(null) : recovery.profilesAvailable())
                    .thenCompose(ignored -> closed ? java.util.concurrent.CompletableFuture.completedFuture(null) : influenceDispatcher.pulse())
                    .whenComplete((ignored, failure) -> maintaining.set(false));
        }
    }
    public WorldWeaverGUIListener listener() { return listener; }
    public hu.taliann.icesmp.integrity.RewardEligibilityPolicy rewardEligibility() {
        return new hu.taliann.icesmp.integrity.InfluenceRewardEligibilityPolicy(
                new hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceLookup(journal, System::currentTimeMillis));
    }
    public java.util.concurrent.CompletionStage<hu.taliann.icesmp.integrity.GameplayEffectPermit> prepareEffect(
            final hu.taliann.icesmp.integrity.GameplayEffectContext context) {
        return journal.prepareDerivedEffect(context);
    }
    public java.util.List<hu.taliann.icesmp.integrity.RewardSource> captureCausalSources(final hu.taliann.icesmp.integrity.GameplaySourceSubject subject) {
        if (closed || !started || !journal.ready()) throw new IllegalStateException("Source capture unavailable");
        return providers.captureCausalSources(subject);
    }
    public hu.taliann.icesmp.dev.weaver.execution.WeaverRecoveryListener recoveryListener() { return recoveryListener; }
    public void shutdown() {
        closed = true; started = false;
        final var task = maintenance; if (task != null) task.cancel();
        kernel.shutdown(); recovery.close(); influenceDispatcher.close(); router.close();
        journal.expireProjections(System.currentTimeMillis(), true).handle((ignored, failure) -> null).thenCompose(ignored -> journal.close()).whenComplete((ignored, failure) -> {
            if (failure != null) plugin.getLogger().severe("Internal developer state shutdown requires recovery.");
        });
    }
}
