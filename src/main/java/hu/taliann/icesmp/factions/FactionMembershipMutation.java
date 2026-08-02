package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Small, Bukkit-free transaction primitive for durable faction membership changes.
 *
 * <p>The manager captures one player's current assignment/history, applies the candidate state,
 * persists the complete snapshot, and restores this record if persistence rejects the write.
 */
public final class FactionMembershipMutation {

    public record Snapshot(
            UUID playerId,
            boolean hadAssignment,
            FactionType assignment,
            boolean hadHistory,
            FactionType lastChosenFaction) {
        public Snapshot {
            Objects.requireNonNull(playerId, "playerId");
            if (hadAssignment != (assignment != null)) {
                throw new IllegalArgumentException("Assignment presence and value disagree");
            }
            if (hadHistory != (lastChosenFaction != null)) {
                throw new IllegalArgumentException("History presence and value disagree");
            }
        }
    }

    private FactionMembershipMutation() {
    }

    public static Snapshot capture(final Map<UUID, FactionType> assignments,
                                   final Map<UUID, FactionType> history,
                                   final UUID playerId) {
        Objects.requireNonNull(assignments, "assignments");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(playerId, "playerId");
        return new Snapshot(
                playerId,
                assignments.containsKey(playerId),
                assignments.get(playerId),
                history.containsKey(playerId),
                history.get(playerId));
    }

    public static void assign(final Map<UUID, FactionType> assignments,
                              final Map<UUID, FactionType> history,
                              final UUID playerId,
                              final FactionType target) {
        Objects.requireNonNull(assignments, "assignments");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(target, "target");
        assignments.put(playerId, target);
        history.put(playerId, target);
    }

    /** Removes only current citizenship; durable last-choice history intentionally survives. */
    public static void removeAssignment(final Map<UUID, FactionType> assignments,
                                        final UUID playerId) {
        Objects.requireNonNull(assignments, "assignments");
        Objects.requireNonNull(playerId, "playerId");
        assignments.remove(playerId);
    }

    public static void restore(final Map<UUID, FactionType> assignments,
                               final Map<UUID, FactionType> history,
                               final Snapshot snapshot) {
        Objects.requireNonNull(assignments, "assignments");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(snapshot, "snapshot");
        restoreEntry(assignments, snapshot.playerId(),
                snapshot.hadAssignment(), snapshot.assignment());
        restoreEntry(history, snapshot.playerId(),
                snapshot.hadHistory(), snapshot.lastChosenFaction());
    }

    private static void restoreEntry(final Map<UUID, FactionType> map,
                                     final UUID playerId,
                                     final boolean wasPresent,
                                     final FactionType value) {
        if (wasPresent) {
            map.put(playerId, value);
        } else {
            map.remove(playerId);
        }
    }
}
