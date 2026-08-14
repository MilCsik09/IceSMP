package hu.taliann.icesmp.gui;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe marker shared by the async moderation and admin config listeners.
 * While marked, a player's next chat messages are private editor input, not public chat.
 */
public final class ConfigChatInputGate {

    private static final Set<UUID> CAPTURED = ConcurrentHashMap.newKeySet();

    private ConfigChatInputGate() {
    }

    public static void open(final UUID playerId) {
        if (playerId != null) {
            CAPTURED.add(playerId);
        }
    }

    public static void close(final UUID playerId) {
        if (playerId != null) {
            CAPTURED.remove(playerId);
        }
    }

    public static boolean isOpen(final UUID playerId) {
        return playerId != null && CAPTURED.contains(playerId);
    }

    public static int activeCount() {
        return CAPTURED.size();
    }
}
