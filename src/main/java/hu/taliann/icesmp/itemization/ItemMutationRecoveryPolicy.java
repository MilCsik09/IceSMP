package hu.taliann.icesmp.itemization;

import java.util.List;
import java.util.Objects;

/** Exact-snapshot recovery: never guesses across an ambiguous cross-domain crash state. */
public final class ItemMutationRecoveryPolicy {
    public enum Decision { ABORT_BEFORE, COMMIT_AFTER, MANUAL_REVIEW }

    private ItemMutationRecoveryPolicy() { }

    public static Decision decide(final List<String> current, final List<String> before,
                                  final List<String> after) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        // A no-op witness is benign only when the current inventory is still that exact witness.
        // There is no payment/item side to choose between, so closing it as exact-before is safe.
        if (before.equals(after)) {
            return current.equals(before) ? Decision.ABORT_BEFORE : Decision.MANUAL_REVIEW;
        }
        if (current.equals(after)) return Decision.COMMIT_AFTER;
        if (current.equals(before)) return Decision.ABORT_BEFORE;
        return Decision.MANUAL_REVIEW;
    }
}
