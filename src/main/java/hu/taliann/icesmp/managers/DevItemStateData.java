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
        UUID pendingGrantId,
        UUID pendingRecipient,
        int rollsSinceRare,
        int rollsSinceEpic,
        int rollsSinceLegendary
) {

    enum DeliveryDecision {
        /** The caller is not the recipient recorded in the durable delivery intent. */
        WAIT_FOR_RECORDED_RECIPIENT,
        /** No durable receipt exists for this grant, so the exact pending item must be delivered. */
        DELIVER,
        /** The playerdata receipt proves that this grant was already delivered and may be acknowledged. */
        ACKNOWLEDGE
    }

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
        final boolean hasGrant = pendingGrantId != null;
        final boolean hasRecipient = pendingRecipient != null;
        if (hasRarity != hasEntry || hasRarity != pendingItemPresent
                || hasRarity != hasGrant || hasRarity != hasRecipient) {
            throw new IllegalArgumentException(
                    "pending rarity, entry, exact item, grant id and recipient must either all be present or all be absent");
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

    /**
     * Owner transfer changes authorization, but an already prepared grant remains bound to its
     * recorded recipient until that player's durable receipt can be reconciled. Rebinding it
     * blindly could duplicate a reward that was saved to the previous owner's playerdata just
     * before a crash.
     */
    DevItemStateData transferTo(final UUID newOwner) {
        return new DevItemStateData(Objects.requireNonNull(newOwner, "newOwner"), instanceId, issued,
                progressMillis, pendingRarity, pendingEntry, pendingItemPresent,
                pendingGrantId, pendingRecipient,
                rollsSinceRare, rollsSinceEpic, rollsSinceLegendary);
    }

    DevItemStateData reassignPendingRecipient(final UUID expectedGrantId, final UUID newRecipient) {
        Objects.requireNonNull(expectedGrantId, "expectedGrantId");
        Objects.requireNonNull(newRecipient, "newRecipient");
        if (!hasPendingReward() || !expectedGrantId.equals(pendingGrantId)) {
            throw new IllegalStateException("the pending grant changed before recipient reassignment");
        }
        return new DevItemStateData(owner, instanceId, issued, progressMillis,
                pendingRarity, pendingEntry, true, pendingGrantId, newRecipient,
                rollsSinceRare, rollsSinceEpic, rollsSinceLegendary);
    }

    DevItemStateData markIssued() {
        if (issued) {
            return this;
        }
        return new DevItemStateData(owner, instanceId, true, progressMillis,
                pendingRarity, pendingEntry, pendingItemPresent,
                pendingGrantId, pendingRecipient,
                rollsSinceRare, rollsSinceEpic, rollsSinceLegendary);
    }

    static DeliveryDecision deliveryDecision(final UUID grantId, final UUID recordedRecipient,
                                               final UUID actor, final String durableReceipt) {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(recordedRecipient, "recordedRecipient");
        Objects.requireNonNull(actor, "actor");
        if (!recordedRecipient.equals(actor)) {
            return DeliveryDecision.WAIT_FOR_RECORDED_RECIPIENT;
        }
        return grantId.toString().equals(durableReceipt)
                ? DeliveryDecision.ACKNOWLEDGE
                : DeliveryDecision.DELIVER;
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
