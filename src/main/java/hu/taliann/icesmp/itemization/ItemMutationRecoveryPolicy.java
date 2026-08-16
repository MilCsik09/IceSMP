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
        if (before.equals(after)) return Decision.MANUAL_REVIEW;
        if (current.equals(after)) return Decision.COMMIT_AFTER;
        if (current.equals(before)) return Decision.ABORT_BEFORE;
        return Decision.MANUAL_REVIEW;
    }
}
