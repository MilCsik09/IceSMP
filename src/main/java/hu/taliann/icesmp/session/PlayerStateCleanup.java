package hu.taliann.icesmp.session;

import java.util.UUID;

/**
 * Contract for any component that holds per-player in-memory session state (caches, cooldowns,
 * temporary attribute/inventory stashes) and must release it when the player leaves.
 *
 * <p>Implementers register themselves once; {@code PlayerSessionCleanupListener} iterates them on
 * quit/kick (and on shutdown for online players), so cleanup is one uniform pass instead of a
 * hand-maintained call list — adding a new stateful manager only means implementing this interface
 * and adding it to that single list.
 */
public interface PlayerStateCleanup {

    /**
     * Releases all in-memory state held for the given player.
     *
     * @param playerId the player whose session state should be cleared
     */
    void clearPlayerState(UUID playerId);
}
