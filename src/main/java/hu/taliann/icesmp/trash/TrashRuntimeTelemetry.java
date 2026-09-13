package hu.taliann.icesmp.trash;

import java.util.concurrent.atomic.LongAdder;

/** Aggregate-only operational counters; no item identity, holder or hidden kind is retained. */
public final class TrashRuntimeTelemetry {

    private final LongAdder behaviorRuntimeErrors = new LongAdder();
    private final LongAdder inspectionsStarted = new LongAdder();
    private final LongAdder inspectionsCompleted = new LongAdder();
    private final LongAdder inspectionsCancelled = new LongAdder();
    private final LongAdder archaeologyUnlocks = new LongAdder();
    private final LongAdder tooltipTextFallbacks = new LongAdder();

    public void recordBehaviorRuntimeError() { behaviorRuntimeErrors.increment(); }
    public void recordInspectionStarted() { inspectionsStarted.increment(); }
    public void recordInspectionCompleted() { inspectionsCompleted.increment(); }
    public void recordInspectionCancelled() { inspectionsCancelled.increment(); }
    public void recordArchaeologyUnlock() { archaeologyUnlocks.increment(); }
    public void recordTooltipTextFallback() { tooltipTextFallbacks.increment(); }

    public Snapshot snapshot() {
        return new Snapshot(behaviorRuntimeErrors.sum(), inspectionsStarted.sum(),
                inspectionsCompleted.sum(), inspectionsCancelled.sum(),
                archaeologyUnlocks.sum(), tooltipTextFallbacks.sum());
    }

    public record Snapshot(long behaviorRuntimeErrors, long inspectionsStarted,
                           long inspectionsCompleted, long inspectionsCancelled,
                           long archaeologyUnlocks, long tooltipTextFallbacks) {
        public Snapshot {
            if (behaviorRuntimeErrors < 0L || inspectionsStarted < 0L
                    || inspectionsCompleted < 0L || inspectionsCancelled < 0L
                    || archaeologyUnlocks < 0L || tooltipTextFallbacks < 0L) {
                throw new IllegalArgumentException("negative Trash telemetry");
            }
        }
    }
}
