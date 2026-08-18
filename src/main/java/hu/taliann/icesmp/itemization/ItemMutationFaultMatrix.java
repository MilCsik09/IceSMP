package hu.taliann.icesmp.itemization;

import java.util.List;
import java.util.Objects;

/**
 * Dependency-free crash matrix for item mutations that publish an exact before/after inventory
 * snapshot through the durable mutation journal. The operation kind is intentionally irrelevant:
 * reroll, rune and ascension must all recover from the same observable evidence.
 */
public final class ItemMutationFaultMatrix {
    public enum Operation { REROLL, RUNE, ASCENSION }
    public enum FailurePoint {
        BEFORE_PREPARE,
        AFTER_PREPARE,
        AFTER_INVENTORY_PUBLISH,
        AFTER_JOURNAL_COMMIT,
        MIXED_INVENTORY
    }

    public record Evidence(List<String> current, List<String> before, List<String> after,
                           boolean pendingJournal, boolean committedJournal) {
        public Evidence {
            current = List.copyOf(Objects.requireNonNull(current, "current"));
            before = List.copyOf(Objects.requireNonNull(before, "before"));
            after = List.copyOf(Objects.requireNonNull(after, "after"));
            if (pendingJournal && committedJournal) {
                throw new IllegalArgumentException("journal cannot be pending and committed");
            }
        }
    }

    private ItemMutationFaultMatrix() { }

    public static Evidence simulate(final Operation operation, final FailurePoint point,
                                    final List<String> before, final List<String> after) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(point, "point");
        final List<String> mixed = before.isEmpty() || after.isEmpty()
                ? List.of("ambiguous") : List.of(before.get(0), after.get(after.size() - 1));
        return switch (point) {
            case BEFORE_PREPARE -> new Evidence(before, before, after, false, false);
            case AFTER_PREPARE -> new Evidence(before, before, after, true, false);
            case AFTER_INVENTORY_PUBLISH -> new Evidence(after, before, after, true, false);
            case AFTER_JOURNAL_COMMIT -> new Evidence(after, before, after, false, true);
            case MIXED_INVENTORY -> new Evidence(mixed, before, after, true, false);
        };
    }

    public static ItemMutationRecoveryPolicy.Decision recover(final Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.committedJournal()) {
            return evidence.current().equals(evidence.after())
                    ? ItemMutationRecoveryPolicy.Decision.COMMIT_AFTER
                    : ItemMutationRecoveryPolicy.Decision.MANUAL_REVIEW;
        }
        if (!evidence.pendingJournal()) {
            return evidence.current().equals(evidence.before())
                    ? ItemMutationRecoveryPolicy.Decision.ABORT_BEFORE
                    : ItemMutationRecoveryPolicy.Decision.MANUAL_REVIEW;
        }
        return ItemMutationRecoveryPolicy.decide(
                evidence.current(), evidence.before(), evidence.after());
    }
}
