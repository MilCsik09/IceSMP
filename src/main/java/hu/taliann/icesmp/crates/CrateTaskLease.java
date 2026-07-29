package hu.taliann.icesmp.crates;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Race-safe task publication: retirement before publish cannot leave a stale live handle. */
public final class CrateTaskLease {
    private final AtomicReference<ScheduledTask> task = new AtomicReference<>();
    private final AtomicBoolean retired = new AtomicBoolean();

    public boolean publish(final ScheduledTask scheduledTask) {
        if (scheduledTask == null) {
            retire();
            return false;
        }
        if (!task.compareAndSet(null, scheduledTask)) {
            scheduledTask.cancel();
            return false;
        }
        if (retired.get() && task.compareAndSet(scheduledTask, null)) {
            scheduledTask.cancel();
            return false;
        }
        return true;
    }

    public void retire() {
        retired.set(true);
        final ScheduledTask current = task.getAndSet(null);
        if (current != null) {
            current.cancel();
        }
    }

    public boolean isRetired() {
        return retired.get();
    }
}
