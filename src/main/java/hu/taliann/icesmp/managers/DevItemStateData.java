package hu.taliann.icesmp.managers;

import java.util.Objects;
import java.util.UUID;

/** Immutable, Bukkit-free metadata validation for the DEV-item durable snapshot. */
record DevItemStateData(
        UUID owner,
        UUID instanceId,
        boolean issued,
        long progressMillis,
        String pendingRarity,
        String pendingEntry,
        boolean pendingItemPresent,
        int rollsSinceRare,
        int rollsSinceEpic,
        int rollsSinceLegendary
) {

    DevItemStateData {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(pendingRarity, "pendingRarity");
        Objects.requireNonNull(pendingEntry, "pendingEntry");
        if (progressMillis < 0L) {
            throw new IllegalArgumentException("progress-millis cannot be negative");
        }
        if (rollsSinceRare < 0 || rollsSinceEpic < 0 || rollsSinceLegendary < 0) {
            throw new IllegalArgumentException("pity counters cannot be negative");
        }

        final boolean hasRarity = !pendingRarity.isBlank();
        final boolean hasEntry = !pendingEntry.isBlank();
        if (hasRarity != hasEntry || hasRarity != pendingItemPresent) {
            throw new IllegalArgumentException(
                    "pending rarity, entry and exact item must either all be present or all be absent");
        }
        if (!issued && (progressMillis != 0L || hasRarity
                || rollsSinceRare != 0 || rollsSinceEpic != 0 || rollsSinceLegendary != 0)) {
            throw new IllegalArgumentException(
                    "an unissued DEV item cannot carry active time, pending reward or pity progress");
        }
    }

    boolean hasPendingReward() {
        return pendingItemPresent;
    }

    static UUID requireUuid(final String raw, final String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " must contain a UUID");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (final IllegalArgumentException malformed) {
            throw new IllegalArgumentException(field + " is not a valid UUID", malformed);
        }
    }
}
