package hu.taliann.icesmp.progression;

import org.bukkit.GameMode;

import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Canonical quest acquisition policy shared by pickup producers and regressions.
 * Only a successful survival/adventure transfer of a non-player-derived ground item counts.
 */
public final class ItemAcquisitionPolicy {

    private ItemAcquisitionPolicy() {
    }

    public static int acceptedPickupAmount(final GameMode mode, final boolean cancelled,
                                           final boolean playerDerived, final int stackAmount,
                                           final int remaining) {
        if (cancelled || playerDerived || (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE)) {
            return 0;
        }
        if (stackAmount <= 0 || remaining < 0 || remaining > stackAmount) return 0;
        return stackAmount - remaining;
    }

    /**
     * Listener-owned bounded receipt window. Bukkit events are not durable replay units, so an
     * in-memory exact-event claim is sufficient and avoids coupling personal quests to the
     * community-goal persistence authority.
     */
    public static final class ReceiptWindow {
        private final int capacity;
        private final LinkedHashMap<String, Boolean> receipts = new LinkedHashMap<>();

        public ReceiptWindow(final int capacity) {
            if (capacity < 1) throw new IllegalArgumentException("receipt capacity must be positive");
            this.capacity = capacity;
        }

        public synchronized boolean claim(final UUID receipt) {
            if (receipt == null) return false;
            final String token = receipt.toString();
            if (receipts.containsKey(token)) return false;
            receipts.put(token, Boolean.TRUE);
            while (receipts.size() > capacity) {
                receipts.remove(receipts.keySet().iterator().next());
            }
            return true;
        }

        public synchronized int size() {
            return receipts.size();
        }
    }
}
