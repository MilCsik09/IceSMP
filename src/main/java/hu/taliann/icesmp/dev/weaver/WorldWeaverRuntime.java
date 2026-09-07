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
    public WorldWeaverRuntime(final JavaPlugin plugin, final DevItemManager artifacts, final ItemIdentityService identity,
            final java.util.List<java.util.function.Function<WeaverTypeRegistry, WorldWeaverProvider>> providerFactories) {
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        providers = new WorldWeaverProviderRegistry(types, () -> System.nanoTime() / 1_000_000L);
        for (final var factory : java.util.List.copyOf(providerFactories)) providers.register(factory.apply(types));
        router = new FoliaWeaverOwnerRouter(plugin);
        final WeaverItemSlots slots = new WeaverItemSlots(identity);
        kernel = new WorldWeaverKernel(artifacts, providers, types, new SubjectSnapshotFactory(router, slots, providers), slots,
                new WorldWeaverGUI(), new WeaverExecutionCoordinator(router, types));
        listener = new WorldWeaverGUIListener(kernel);
        artifacts.bindInteractions(WorldWeaverArtifactBehavior.ID, kernel::interact, () -> kernel.clearPlayerState(HiddenDevAuthority.PRIMARY_DEVELOPER));
    }
    public void start() { providers.freezeAndValidate(); }
    public WorldWeaverGUIListener listener() { return listener; }
    public void shutdown() { kernel.shutdown(); router.close(); }
}
