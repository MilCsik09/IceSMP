package hu.taliann.icesmp.client;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-játékos, kategóriánkénti fix ablakos csomag-korlát. Túllépésnél a csomag
 * eldobódik és számlálóra kerül — szándékosan nincs automatikus büntetés/kick,
 * csak diagnosztika (a packet-burst önmagában nem abuse-bizonyíték).
 *
 * <p>Az időt paraméterként kapja, hogy a regressziós suite determinisztikusan
 * tesztelhesse az ablak-átfordulást.</p>
 */
public final class ClientRateLimiter {

    public enum Category { CONTROL, RESYNC, CAST, UI }

    private record Key(UUID playerId, Category category) {
    }

    private static final class Window {
        private long windowStartMillis;
        private int count;
    }

    private final ConcurrentHashMap<Key, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(final UUID playerId, final Category category, final int limit,
                              final long windowMillis, final long nowMillis) {
        if (limit <= 0 || windowMillis <= 0L) {
            return false;
        }
        final Window window = windows.computeIfAbsent(new Key(playerId, category), key -> new Window());
        synchronized (window) {
            if (nowMillis - window.windowStartMillis >= windowMillis || nowMillis < window.windowStartMillis) {
                window.windowStartMillis = nowMillis;
                window.count = 0;
            }
            if (window.count >= limit) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    public void clearPlayer(final UUID playerId) {
        windows.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void clear() {
        windows.clear();
    }
}
