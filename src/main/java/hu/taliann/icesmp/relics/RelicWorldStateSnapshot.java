package hu.taliann.icesmp.relics;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A világ-szintű relic aggregátum immutable, teljes pillanatképe. A runtime olvasók
 * mindig EGY publikált verziót látnak (volatile csere), sosem félkész vagy commit
 * előtti állapotot — a candidate pillanatkép csak sikeres durable írás UTÁN válik
 * publikálttá. A lost-jelölés invariánsa: lost(relic) → ownership(relic) létezik
 * (árva lost állapot se memóriában, se a durable fájlban nem élhet).
 *
 * @param operations relic-id → függő fizikai művelet (kézbesítés/PDC-átírás) receiptje;
 *        a világ-oldali commit és a fizikai mellékhatás közti crash-ablak recovery-je
 *        ebből determinisztikus
 */
public record RelicWorldStateSnapshot(
        Map<String, RelicOwnership> ownerships,
        Map<String, Long> lostSince,
        Map<String, Long> awakeningReadyAt,
        Map<String, PendingRelicOperation> operations) {

    public static final RelicWorldStateSnapshot EMPTY =
            new RelicWorldStateSnapshot(Map.of(), Map.of(), Map.of(), Map.of());

    public RelicWorldStateSnapshot {
        ownerships = Map.copyOf(Objects.requireNonNull(ownerships, "ownerships"));
        lostSince = Map.copyOf(Objects.requireNonNull(lostSince, "lostSince"));
        awakeningReadyAt = Map.copyOf(Objects.requireNonNull(awakeningReadyAt, "awakeningReadyAt"));
        operations = Map.copyOf(Objects.requireNonNull(operations, "operations"));
        for (final String relicId : lostSince.keySet()) {
            if (!ownerships.containsKey(relicId)) {
                throw new IllegalArgumentException("orphan lost state without ownership: " + relicId);
            }
        }
    }

    /** Függő fizikai művelet: a világ-commit már megtörtént, a mellékhatás lezárása hátravan. */
    public record PendingRelicOperation(Type type, UUID fromOwner, UUID toOwner) {
        public enum Type { CLAIM, RECLAIM, TRANSFER }

        public PendingRelicOperation {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(toOwner, "toOwner");
        }
    }
}
