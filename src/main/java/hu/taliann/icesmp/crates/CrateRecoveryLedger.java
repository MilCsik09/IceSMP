package hu.taliann.icesmp.crates;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable key-compensation records for orderly scheduler/reload/disable failures. */
public final class CrateRecoveryLedger {

    public enum Disposition {
        /** Ledger/cooldown exists, but no key was consumed yet. */
        ROLLBACK_ONLY,
        /** A key was consumed and may be refunded once the player owner scheduler is available. */
        REFUND_KEYS,
        /** Durable claim written before delivering a refund; restart requires manual review. */
        REFUND_CLAIMED,
        /** At least one non-compensable side effect may have occurred. */
        MANUAL_REVIEW
    }

    public record KeySpec(String material, String displayName, String itemModel) {
        public KeySpec {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(displayName, "displayName");
        }
    }

    public record Recovery(UUID openingId, UUID playerId, String playerName, String crateId,
                           int keyCount, KeySpec keySpec, CrateLedger.Mutation ledgerMutation,
                           Disposition disposition, String reason) {
        public Recovery {
            Objects.requireNonNull(openingId, "openingId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(crateId, "crateId");
            Objects.requireNonNull(keySpec, "keySpec");
            Objects.requireNonNull(ledgerMutation, "ledgerMutation");
            Objects.requireNonNull(disposition, "disposition");
            reason = reason == null ? "" : reason;
            if (keyCount <= 0 || keyCount > CrateRules.MAX_KEY_AMOUNT) {
                throw new IllegalArgumentException("Invalid recovery key count");
            }
        }

        public Recovery withDisposition(final Disposition next, final String nextReason) {
            return new Recovery(openingId, playerId, playerName, crateId, keyCount, keySpec,
                    ledgerMutation, next, nextReason);
        }
    }

    private final Map<UUID, Recovery> recoveries = new LinkedHashMap<>();

    public void add(final Recovery recovery) {
        if (recoveries.putIfAbsent(recovery.openingId(), recovery) != null) {
            throw new IllegalStateException("Duplicate crate recovery opening id");
        }
        if (recoveries.values().stream().filter(value -> value.playerId().equals(recovery.playerId())).count() > 1L) {
            recoveries.remove(recovery.openingId());
            throw new IllegalStateException("A player may have only one pending crate recovery");
        }
    }

    public Recovery get(final UUID openingId) {
        return recoveries.get(openingId);
    }

    public Recovery forPlayer(final UUID playerId) {
        for (final Recovery recovery : recoveries.values()) {
            if (recovery.playerId().equals(playerId)) {
                return recovery;
            }
        }
        return null;
    }

    public boolean containsPlayer(final UUID playerId) {
        return forPlayer(playerId) != null;
    }

    public Recovery transition(final UUID openingId, final Disposition disposition, final String reason) {
        final Recovery current = recoveries.get(openingId);
        if (current == null) {
            return null;
        }
        final Recovery updated = current.withDisposition(disposition, reason);
        recoveries.put(openingId, updated);
        return updated;
    }

    public Recovery transition(final UUID openingId, final Disposition expected,
                               final Disposition disposition, final String reason) {
        final Recovery current = recoveries.get(openingId);
        if (current == null || current.disposition() != expected) {
            return null;
        }
        final Recovery updated = current.withDisposition(disposition, reason);
        recoveries.put(openingId, updated);
        return updated;
    }

    public Recovery remove(final UUID openingId) {
        return recoveries.remove(openingId);
    }

    public Map<UUID, Recovery> snapshot() {
        return Map.copyOf(recoveries);
    }

    public void replace(final Map<UUID, Recovery> snapshot) {
        recoveries.clear();
        for (final Recovery recovery : snapshot.values()) {
            add(recovery);
        }
    }
}
