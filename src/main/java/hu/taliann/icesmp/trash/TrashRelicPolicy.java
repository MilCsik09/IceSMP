package hu.taliann.icesmp.trash;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Pure transaction and bounded-work decisions shared by the Phase E runtime/tests. */
final class TrashRelicPolicy {

    private TrashRelicPolicy() { }

    static boolean consumptionCommitted(final boolean sameStackStillInHand,
                                        final int amountBefore, final int amountAfter) {
        if (amountBefore < 1 || amountAfter < 0) return false;
        return !sameStackStillInHand || amountAfter < amountBefore;
    }

    static boolean mayTrackProjectile(final boolean projectileWallActive,
                                      final int trackedProjectiles, final int maximum) {
        return projectileWallActive && maximum > 0
                && trackedProjectiles >= 0 && trackedProjectiles < maximum;
    }

    static boolean completeProjectileWall(final boolean ownsBothEntities,
                                          final BooleanSupplier admitted,
                                          final BooleanSupplier consume,
                                          final Runnable remove) {
        // Co-owned fast path only; cross-owner receipt coordination belongs to the native activation service.
        if (!ownsBothEntities || !admitted.getAsBoolean() || !consume.getAsBoolean()) return false;
        remove.run();
        return true;
    }

    /** Bounded task admission only; fields and item history retain their native authorities. */
    static final class ProjectileTracking {
        private final int maximum;
        private final Set<Ticket> tickets = new HashSet<>();
        private boolean open = true;

        ProjectileTracking(final int maximum) {
            if (maximum < 1) throw new IllegalArgumentException("invalid projectile task cap");
            this.maximum = maximum;
        }

        synchronized Ticket admit(final UUID projectileId) {
            Objects.requireNonNull(projectileId, "projectileId");
            if (!mayTrackProjectile(open, tickets.size(), maximum)
                    || tickets.stream().anyMatch(ticket -> ticket.projectileId.equals(projectileId))) return null;
            final Ticket ticket = new Ticket(projectileId);
            tickets.add(ticket);
            return ticket;
        }

        synchronized boolean active(final Ticket ticket) { return open && tickets.contains(ticket); }
        synchronized void release(final Ticket ticket) { tickets.remove(ticket); }
        synchronized void close() { open = false; tickets.clear(); }
        synchronized TrackingSnapshot snapshot() { return new TrackingSnapshot(open, tickets.size(), maximum); }

        // Identity equality prevents a late retired callback releasing a later admission of the same entity UUID.
        static final class Ticket {
            private final UUID projectileId;
            private Ticket(final UUID projectileId) { this.projectileId = projectileId; }
        }
    }

    record TrackingSnapshot(boolean open, int active, int maximum) { }
}
