package hu.taliann.icesmp.moderation;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Paper/Folia adapter over the dependency-free nullable scheduler submission gate. */
public final class PaperEntityTaskSubmission {
    private PaperEntityTaskSubmission() {
    }

    public static boolean run(final JavaPlugin plugin, final EntityScheduler scheduler,
                              final Runnable task, final Runnable rejected) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(scheduler, "scheduler");
        return EntityTaskSubmission.submit(
                (scheduled, retired) -> scheduler.run(plugin, ignored -> scheduled.run(), retired),
                task, rejected);
    }

    public static boolean runDelayed(final JavaPlugin plugin, final EntityScheduler scheduler,
                                     final Runnable task, final Runnable rejected, final long delayTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(scheduler, "scheduler");
        return EntityTaskSubmission.submit(
                (scheduled, retired) -> scheduler.runDelayed(plugin, ignored -> scheduled.run(), retired, delayTicks),
                task, rejected);
    }
}
