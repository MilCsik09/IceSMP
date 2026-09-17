package hu.taliann.icesmp.trash;

import java.util.concurrent.atomic.LongAdder;

/** Bounded operational diagnostics; no item identity, holder or exception message is retained. */
public final class TrashRuntimeTelemetry {

    private final LongAdder behaviorRuntimeErrors = new LongAdder();
    private final LongAdder inspectionsStarted = new LongAdder();
    private final LongAdder inspectionsCompleted = new LongAdder();
    private final LongAdder inspectionsCancelled = new LongAdder();
    private final LongAdder archaeologyUnlocks = new LongAdder();
    private final LongAdder tooltipTextFallbacks = new LongAdder();
    private volatile String lastFailure = "none";

    public void recordBehaviorRuntimeError() { behaviorRuntimeErrors.increment(); }
    public void recordBehaviorRuntimeError(final String operation, final Throwable failure) {
        recordBehaviorRuntimeError();
        Throwable cause = failure;
        for (int depth = 0; depth < 16 && cause.getCause() != null && cause.getCause() != cause; depth++)
            cause = cause.getCause();
        // Exception messages can contain hidden item identities or player paths.
        final String location = java.util.Arrays.stream(cause.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("hu.taliann.icesmp."))
                .findFirst().map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1)
                        + "." + frame.getMethodName() + ":" + frame.getLineNumber()).orElse("external");
        lastFailure = operation + "/" + cause.getClass().getSimpleName() + "@" + location;
    }
    public String lastFailure() { return lastFailure; }
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
