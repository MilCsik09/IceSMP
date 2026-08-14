package hu.taliann.icesmp.archer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bounded, self-expiring ledger of in-flight arrows and the discipline of the shot that launched
 * them.
 *
 * <p>The Szélolvasás may only be built by a shot that was actually disciplined, so the verdict has
 * to travel with the arrow rather than with the archer: several arrows can be airborne at once, and
 * the archer's newest shot must not lend its discipline to an older one. Each entry is an immutable
 * record keyed by the projectile's id, dropped when the arrow resolves, when it ages out, or when
 * the owner's state is cleaned up. Nothing here is durable, nothing holds a live entity reference,
 * and the ledger is pruned on write instead of by any repeating task.</p>
 */
public final class ArcherShotLedger {

    /**
     * The immutable discipline verdict of one shot. Coordinates are plain numbers copied at launch
     * time — never a live location or entity handle, so reading them is region-thread safe.
     */
    public record ShotRecord(UUID ownerId, boolean fullDraw, boolean paced,
                             double originX, double originY, double originZ, long firedAt) {

        public double distanceTo(final double x, final double y, final double z) {
            final double dx = x - originX;
            final double dy = y - originY;
            final double dz = z - originZ;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /** A read may only be built by a full-draw, properly paced shot landing from real range. */
        public boolean buildsWindRead(final double hitX, final double hitY, final double hitZ,
                                      final double minimumDistance) {
            return fullDraw && paced && distanceTo(hitX, hitY, hitZ) >= minimumDistance;
        }
    }

    private final Map<UUID, ShotRecord> inFlight = new LinkedHashMap<>();

    /** Registers a launched arrow, pruning expired entries and enforcing the hard cap first. */
    public synchronized void record(final UUID projectileId, final ShotRecord record,
                                    final long now, final int maximum, final long expiryMillis) {
        if (projectileId == null || record == null) return;
        prune(now, expiryMillis);
        final int cap = Math.max(1, maximum);
        final List<UUID> oldest = new ArrayList<>(inFlight.keySet());
        for (int i = 0; inFlight.size() >= cap && i < oldest.size(); i++) {
            inFlight.remove(oldest.get(i));
        }
        inFlight.put(projectileId, record);
    }

    /** Resolves an arrow: the record is returned once and removed, so it can never pay twice. */
    public synchronized Optional<ShotRecord> consume(final UUID projectileId, final long now,
                                                     final long expiryMillis) {
        prune(now, expiryMillis);
        return Optional.ofNullable(inFlight.remove(projectileId));
    }

    public synchronized Optional<ShotRecord> peek(final UUID projectileId, final long now,
                                                  final long expiryMillis) {
        prune(now, expiryMillis);
        return Optional.ofNullable(inFlight.get(projectileId));
    }

    /** Player cleanup: every arrow still attributed to this archer is forgotten. */
    public synchronized int forgetOwner(final UUID ownerId) {
        if (ownerId == null) return 0;
        final int before = inFlight.size();
        inFlight.entrySet().removeIf(entry -> ownerId.equals(entry.getValue().ownerId()));
        return before - inFlight.size();
    }

    public synchronized int size() {
        return inFlight.size();
    }

    public synchronized void clear() {
        inFlight.clear();
    }

    private void prune(final long now, final long expiryMillis) {
        final long horizon = Math.max(1L, expiryMillis);
        inFlight.entrySet().removeIf(entry -> now - entry.getValue().firedAt() >= horizon);
    }
}
