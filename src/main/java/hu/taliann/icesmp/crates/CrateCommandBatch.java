package hu.taliann.icesmp.crates;

/** Classification for non-transactional console-command reward batches. */
public final class CrateCommandBatch {

    public enum Outcome {
        SUCCESS,
        COMPENSATABLE_FAILURE,
        PARTIAL_FAILURE
    }

    private CrateCommandBatch() {
    }

    public static Outcome classify(final int attempted, final int successful, final boolean allSucceeded) {
        if (attempted < 0 || successful < 0 || successful > attempted) {
            throw new IllegalArgumentException("Invalid command batch counters");
        }
        if (allSucceeded && successful == attempted) {
            return Outcome.SUCCESS;
        }
        return successful == 0 ? Outcome.COMPENSATABLE_FAILURE : Outcome.PARTIAL_FAILURE;
    }
}
