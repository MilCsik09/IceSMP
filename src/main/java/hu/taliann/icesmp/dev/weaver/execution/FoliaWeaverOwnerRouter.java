package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.api.WeaverDomainRejection;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class FoliaWeaverOwnerRouter implements WeaverOwnerRouter {
    private final JavaPlugin plugin;
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128), runnable -> new Thread(runnable, "IceSMP-Weaver-IO"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1,
            runnable -> new Thread(runnable, "IceSMP-Weaver-deadline"));
    private final Set<OwnerTaskAdmission<?>> pending = ConcurrentHashMap.newKeySet();
    private final Semaphore permits = new Semaphore(128);
    private volatile boolean closed;
    public FoliaWeaverOwnerRouter(final JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin); deadlines.setRemoveOnCancelPolicy(true);
    }
    @Override public <T> CompletionStage<T> submit(final ExecutionOwner owner, final UUID actor, final Duration timeout,
                                                   final Supplier<CompletionStage<T>> task) {
        java.util.Objects.requireNonNull(owner); java.util.Objects.requireNonNull(actor); java.util.Objects.requireNonNull(task);
        final long millis = timeout.toMillis();
        if (millis < 1 || millis > (owner instanceof AsyncIoOwner || owner instanceof ProfileOwner ? 10000 : 5000)) {
            throw new IllegalArgumentException("Owner task timeout outside design bounds");
        }
        if (closed || !permits.tryAcquire()) return CompletableFuture.failedFuture(new WeaverDomainRejection("OWNER_QUEUE_CLOSED_OR_FULL"));
        final OwnerTaskAdmission<T> admission = new OwnerTaskAdmission<>(); pending.add(admission);
        admission.result().whenComplete((ignored, failure) -> { pending.remove(admission); permits.release(); });
        try {
            final ScheduledFuture<?> deadline = deadlines.schedule(admission::timeout, millis, TimeUnit.MILLISECONDS);
            admission.result().whenComplete((ignored, failure) -> deadline.cancel(false));
            final Runnable run = () -> {
                if (closed) admission.shutdown(); else admission.run(task);
            };
            switch (owner) {
                case ActorOwner ignored -> entity(actor, run, admission::unavailable);
                case EntityOwner target -> entity(target.entityId(), run, admission::unavailable);
                case RegionOwner region -> {
                    final World locator = Bukkit.getWorld(region.worldId());
                    if (locator == null) admission.unavailable();
                    else Bukkit.getRegionScheduler().run(plugin, locator, region.chunkX(), region.chunkZ(), ignored -> {
                        final World world = Bukkit.getWorld(region.worldId());
                        if (world == null || !Bukkit.isOwnedByCurrentRegion(world, region.chunkX(), region.chunkZ())
                                || !world.isChunkLoaded(region.chunkX(), region.chunkZ())) admission.unavailable();
                        else run.run();
                    });
                }
                case GlobalOwner ignored -> Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> {
                    if (!Bukkit.isGlobalTickThread()) admission.unavailable(); else run.run();
                });
                case AsyncIoOwner ignored -> io.execute(run);
                case ProfileOwner ignored -> io.execute(run);
            }
        } catch (final RuntimeException failure) { admission.unavailable(); }
        if (closed) admission.shutdown();
        return admission.result();
    }
    private void entity(final UUID id, final Runnable task, final Runnable unavailable) {
        final Entity locator = Bukkit.getEntity(id);
        if (locator == null) { unavailable.run(); return; }
        final var scheduled = locator.getScheduler().run(plugin, ignored -> {
            final Entity target = Bukkit.getEntity(id);
            if (target == null || !Bukkit.isOwnedByCurrentRegion(target) || !target.isValid()) unavailable.run();
            else task.run();
        }, unavailable);
        if (scheduled == null) unavailable.run();
    }
    @Override public void close() {
        closed = true; pending.forEach(OwnerTaskAdmission::shutdown); deadlines.shutdownNow(); io.shutdown();
    }
}
