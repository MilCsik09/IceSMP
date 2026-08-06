package hu.taliann.icesmp.session;

import java.util.UUID;

/**
 * Contract for components that hold rebuildable per-player session state.
 *
 * <p>{@link #clearPlayerState(UUID)} is the lifecycle entry point used by the centralized
 * quit/kick/disable listener. {@link #cleanup(UUID)} is a compatibility hook for managers that
 * expose their internal projection cleanup separately; by default it delegates to the lifecycle
 * entry point.</p>
 */
public interface PlayerStateCleanup {

    /** Releases all in-memory state held for the given player. */
    void clearPlayerState(UUID playerId);

    /** Optional internal alias used by projection-backed managers. */
    default void cleanup(final UUID playerId) {
        clearPlayerState(playerId);
    }
}
