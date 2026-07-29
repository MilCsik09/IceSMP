package hu.taliann.icesmp.managers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic, Bukkit-free ownership ledger for active native seats. */
final class SitState {

    enum ReserveResult {
        RESERVED,
        PLAYER_BUSY,
        BLOCK_OCCUPIED
    }

    record SeatKey(UUID worldId, int x, int y, int z) {
        SeatKey {
            if (worldId == null) {
                throw new IllegalArgumentException("seat worldId is required");
            }
        }
    }

    record SeatLease(SeatKey key, UUID standId) {
        SeatLease {
            if (key == null) {
                throw new IllegalArgumentException("seat key is required");
            }
        }

        boolean active() {
            return standId != null;
        }
    }

    private final Map<UUID, SeatLease> seatsByPlayer = new ConcurrentHashMap<>();
    private final Map<SeatKey, UUID> playersBySeat = new ConcurrentHashMap<>();

    ReserveResult reserve(final UUID playerId, final SeatKey key) {
        if (playerId == null || key == null) {
            throw new IllegalArgumentException("playerId and key are required");
        }
        if (seatsByPlayer.containsKey(playerId)) {
            return ReserveResult.PLAYER_BUSY;
        }

        final UUID existingOwner = playersBySeat.putIfAbsent(key, playerId);
        if (existingOwner != null) {
            return existingOwner.equals(playerId) ? ReserveResult.PLAYER_BUSY : ReserveResult.BLOCK_OCCUPIED;
        }

        final SeatLease previous = seatsByPlayer.putIfAbsent(playerId, new SeatLease(key, null));
        if (previous != null) {
            playersBySeat.remove(key, playerId);
            return ReserveResult.PLAYER_BUSY;
        }
        return ReserveResult.RESERVED;
    }

    boolean activate(final UUID playerId, final SeatKey key, final UUID standId) {
        if (playerId == null || key == null || standId == null) {
            return false;
        }
        final boolean[] activated = {false};
        seatsByPlayer.computeIfPresent(playerId, (ignored, current) -> {
            if (!current.key().equals(key) || current.active()) {
                return current;
            }
            activated[0] = true;
            return new SeatLease(key, standId);
        });
        return activated[0];
    }

    SeatLease get(final UUID playerId) {
        return playerId == null ? null : seatsByPlayer.get(playerId);
    }

    boolean isSeated(final UUID playerId) {
        final SeatLease lease = get(playerId);
        return lease != null && lease.active();
    }

    UUID occupant(final SeatKey key) {
        return key == null ? null : playersBySeat.get(key);
    }

    SeatLease release(final UUID playerId) {
        final SeatLease lease = playerId == null ? null : seatsByPlayer.remove(playerId);
        if (lease != null) {
            playersBySeat.remove(lease.key(), playerId);
        }
        return lease;
    }

    List<UUID> playerIds() {
        return List.copyOf(seatsByPlayer.keySet());
    }

    List<SeatLease> clear() {
        final List<SeatLease> leases = List.copyOf(seatsByPlayer.values());
        seatsByPlayer.clear();
        playersBySeat.clear();
        return leases;
    }

    int size() {
        return seatsByPlayer.size();
    }
}
