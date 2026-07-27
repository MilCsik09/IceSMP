package hu.taliann.icesmp.managers;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, Bukkit-free DEV-item state metadata used to validate durable snapshots before they
 * are applied to the live manager.
 */
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

    DevItemStateData transferTo(final UUID newOwner) {
        return new DevItemStateData(Objects.requireNonNull(newOwner, "newOwner"), instanceId, issued,
                progressMillis, pendingRarity, pendingEntry, pendingItemPresent,
                rollsSinceRare, rollsSinceEpic, rollsSinceLegendary);
    }

    DevItemStateData markIssued() {
        if (issued) {
            return this;
        }
        return new DevItemStateData(owner, instanceId, true, progressMillis,
                pendingRarity, pendingEntry, pendingItemPresent,
                rollsSinceRare, rollsSinceEpic, rollsSinceLegendary);
    }

    /**
     * PR #33 persisted a grant UUID and recipient while a playerdata receipt protocol was active.
     * A receipt-backed pending reward may already be present in the player's inventory even though
     * the YAML ACK is missing. The simpler pending/retry model therefore refuses to guess and asks
     * for one-time operator reconciliation instead of silently duplicating or deleting the reward.
     */
    static void validateLegacyReceiptMigration(final boolean hasPendingReward,
                                                final String rawGrantId,
                                                final String rawRecipient) {
        final String grantId = Objects.requireNonNull(rawGrantId, "rawGrantId").trim();
        final String recipient = Objects.requireNonNull(rawRecipient, "rawRecipient").trim();
        final boolean hasGrant = !grantId.isBlank();
        final boolean hasRecipient = !recipient.isBlank();
        if (hasGrant != hasRecipient) {
            throw new IllegalArgumentException("legacy grant-id and recipient must both be present or absent");
        }
        if (hasGrant) {
            if (!hasPendingReward) {
                throw new IllegalArgumentException("legacy DEV reward receipt metadata exists without a pending reward");
            }
            throw new IllegalArgumentException(
                    "legacy receipt-backed pending DEV reward requires one-time manual reconciliation");
        }
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
