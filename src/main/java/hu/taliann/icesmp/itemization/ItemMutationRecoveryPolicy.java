package hu.taliann.icesmp.itemization;

import java.util.Base64;
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
        // A real journal no-op witness is benign only when all three snapshots are byte-for-byte
        // identical and the witness has the production inventory encoding shape. Malformed or
        // synthetic equal witnesses remain quarantined instead of being treated as evidence.
        if (before.equals(after)) {
            return current.equals(before) && looksLikeEncodedInventory(before)
                    ? Decision.ABORT_BEFORE : Decision.MANUAL_REVIEW;
        }
        if (current.equals(after)) return Decision.COMMIT_AFTER;
        if (current.equals(before)) return Decision.ABORT_BEFORE;
        return Decision.MANUAL_REVIEW;
    }

    private static boolean looksLikeEncodedInventory(final List<String> snapshot) {
        if (snapshot.isEmpty() || snapshot.size() > 64) return false;
        for (final String slot : snapshot) {
            if (slot == null) return false;
            if ("-".equals(slot)) continue;
            if (slot.isBlank()) return false;
            try {
                if (Base64.getDecoder().decode(slot).length == 0) return false;
            } catch (final IllegalArgumentException malformed) {
                return false;
            }
        }
        return true;
    }
}
