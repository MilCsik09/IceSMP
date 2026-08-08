package hu.taliann.icesmp.archer;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player transient Íjász combat state.
 *
 * <p>Not a Warrior or Evoker clone: the class core is a single armed/broken Szélolvasás read on
 * the next shot, Mesterlövész tracks one prey target with a bounded precision chain and Vadmester
 * tracks one Kötelék percentage. Durable class/spec/doctrine/mastery and the companion stable
 * remain in PlayerProfile.</p>
 */
public final class ArcherCombatState {

    private long lastShotAt;
    private double lastShotX;
    private double lastShotY;
    private double lastShotZ;
    private boolean windRead;
    private long windReadExpiresAt;

    private UUID preyTargetId;
    private int precisionChain;
    private long chainLastHitAt;

    private int bond;

    // ===== Szélolvasás (class core) =====

    /**
     * Records a bow shot. A shot is "paced" when it respects the minimum draw rhythm; firing
     * faster breaks an armed read. Returns whether the shot was paced.
     */
    public synchronized boolean recordShot(final long now, final boolean fullDraw,
                                           final long minIntervalMillis,
                                           final double shotX, final double shotY,
                                           final double shotZ) {
        final boolean paced = fullDraw
                && (lastShotAt == 0L || now - lastShotAt >= Math.max(0L, minIntervalMillis));
        if (!paced) windRead = false;
        lastShotAt = now;
        lastShotX = shotX;
        lastShotY = shotY;
        lastShotZ = shotZ;
        return paced;
    }

    /** Distance from the recorded shot origin — plain coordinates, no cross-region entity access. */
    public synchronized double distanceFromLastShot(final double x, final double y,
                                                    final double z) {
        if (lastShotAt == 0L) return 0.0D;
        final double dx = x - lastShotX;
        final double dy = y - lastShotY;
        final double dz = z - lastShotZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** A paced full-draw hit from real distance arms the read for the next shot. */
    public synchronized void armWindRead(final long now, final long windowMillis) {
        windRead = true;
        windReadExpiresAt = now + Math.max(1L, windowMillis);
    }

    public synchronized boolean isWindReadArmed(final long now) {
        return windRead && windReadExpiresAt > now;
    }

    /** Single-use: the empowered shot consumes the read. */
    public synchronized boolean consumeWindRead(final long now) {
        if (!isWindReadArmed(now)) {
            windRead = false;
            return false;
        }
        windRead = false;
        return true;
    }

    // ===== Mesterlövész: Préda-jel + Pontossági lánc =====

    /**
     * Records a full-draw hit for the precision chain. Hitting the same prey inside the chain
     * window builds the chain; a new target or a stale window restarts it at one on that target.
     */
    public synchronized int recordPreyHit(final UUID targetId, final long now,
                                          final long chainWindowMillis, final int maximumChain) {
        Objects.requireNonNull(targetId, "targetId");
        final boolean samePrey = targetId.equals(preyTargetId)
                && now - chainLastHitAt <= Math.max(1L, chainWindowMillis);
        precisionChain = samePrey
                ? Math.min(Math.max(1, maximumChain), precisionChain + 1)
                : 1;
        preyTargetId = targetId;
        chainLastHitAt = now;
        return precisionChain;
    }

    public synchronized int precisionChain(final long now, final long chainWindowMillis) {
        if (precisionChain > 0 && now - chainLastHitAt > Math.max(1L, chainWindowMillis)) {
            precisionChain = 0;
            preyTargetId = null;
        }
        return precisionChain;
    }

    public synchronized Optional<UUID> preyTargetId() {
        return Optional.ofNullable(preyTargetId);
    }

    /**
     * Weak-point finisher: consumes a chain at or above the threshold. The retained value
     * supports the level-50 doctrine variant.
     */
    public synchronized boolean consumeWeakPoint(final int threshold, final int retainedChain) {
        if (precisionChain < Math.max(1, threshold)) return false;
        precisionChain = Math.max(0, Math.min(precisionChain, retainedChain));
        if (precisionChain == 0) preyTargetId = null;
        return true;
    }

    // ===== Vadmester: Kötelék =====

    public synchronized int addBond(final int amount) {
        bond = clampPercent(bond + Math.max(0, amount));
        return bond;
    }

    public synchronized boolean spendBond(final int amount) {
        final int cost = Math.max(0, amount);
        if (bond < cost) return false;
        bond -= cost;
        return true;
    }

    public synchronized int bond() {
        return bond;
    }

    /** Pet death consequence; the level-50 doctrine may retain part of the bond. */
    public synchronized void collapseBond(final int retainedBond) {
        bond = Math.max(0, Math.min(bond, retainedBond));
    }

    /** Spec switch cleanup: no archer state is class-common enough to survive it. */
    public synchronized void clearSpecializationState() {
        lastShotAt = 0L;
        windRead = false;
        windReadExpiresAt = 0L;
        preyTargetId = null;
        precisionChain = 0;
        chainLastHitAt = 0L;
        bond = 0;
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }
}
