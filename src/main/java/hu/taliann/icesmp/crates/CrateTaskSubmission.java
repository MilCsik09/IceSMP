package hu.taliann.icesmp.crates;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;

/** Exception/null/retirement-safe scheduler adapters for crate lifecycle work. */
public final class CrateTaskSubmission {

    private CrateTaskSubmission() {
    }

    public static boolean entity(final Plugin plugin, final EntityScheduler scheduler,
                                 final Runnable task, final Runnable rejected) {
        final CrateCallbackGate gate = new CrateCallbackGate();
        try {
            final ScheduledTask handle = scheduler.run(plugin,
                    ignored -> gate.runTask(task), () -> gate.runRejected(rejected));
            if (handle == null) {
                gate.runRejected(rejected);
                return false;
            }
            return gate.winner() != CrateCallbackGate.Winner.REJECTED;
        } catch (final RuntimeException failure) {
            gate.runRejected(rejected);
            return false;
        }
    }

    public static ScheduledTask entityDelayed(final Plugin plugin, final EntityScheduler scheduler,
                                               final Runnable task, final Runnable rejected,
                                               final long delayTicks) {
        final CrateCallbackGate gate = new CrateCallbackGate();
        try {
            final ScheduledTask handle = scheduler.runDelayed(plugin,
                    ignored -> gate.runTask(task), () -> gate.runRejected(rejected), delayTicks);
            if (handle == null) {
                gate.runRejected(rejected);
                return null;
            }
            return gate.winner() == CrateCallbackGate.Winner.REJECTED ? null : handle;
        } catch (final RuntimeException failure) {
            gate.runRejected(rejected);
            return null;
        }
    }

    public static boolean global(final Plugin plugin, final GlobalRegionScheduler scheduler,
                                 final Runnable task, final Runnable rejected) {
        final CrateCallbackGate gate = new CrateCallbackGate();
        try {
            final ScheduledTask handle = scheduler.run(plugin, ignored -> gate.runTask(task));
            if (handle == null) {
                gate.runRejected(rejected);
                return false;
            }
            return true;
        } catch (final RuntimeException failure) {
            gate.runRejected(rejected);
            return false;
        }
    }

    public static boolean async(final Plugin plugin, final AsyncScheduler scheduler,
                                final Runnable task, final Runnable rejected) {
        final CrateCallbackGate gate = new CrateCallbackGate();
        try {
            final ScheduledTask handle = scheduler.runNow(plugin, ignored -> gate.runTask(task));
            if (handle == null) {
                gate.runRejected(rejected);
                return false;
            }
            return true;
        } catch (final RuntimeException failure) {
            gate.runRejected(rejected);
            return false;
        }
    }
}
