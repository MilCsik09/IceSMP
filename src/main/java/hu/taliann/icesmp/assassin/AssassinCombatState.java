package hu.taliann.icesmp.assassin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player transient Orgyilkos combat state.
 *
 * <p>Lehetőség is the class layer: four distinct openings (position, dodge, interrupt, unseen
 * approach) each arm one window that a finisher consumes, and the window remembers which opening
 * paid for it. Méregkeverő carries exactly three Toxinkészlet slots with per-slot doses — a fixed
 * trio, not a socket engine. Fantom carries the Árnyéknyom echo window and the Észleltség meter
 * that makes stealth strictly finite. Pestishozó carries the mutating strain plus a hard-capped
 * infection registry whose entries expire and are dropped on cleanup. Durable state remains in
 * PlayerProfile.</p>
 */
public final class AssassinCombatState {

    /** Which opening armed the current Lehetőség. */
    public enum Opening {
        POZICIO,
        KITERES,
        INTERRUPT,
        ESZREVETLEN
    }

    /** The Toxinkészlet holds exactly three slots. */
    public static final int TOXIN_SLOTS = 3;

    private Opening opening;
    private long opportunityUntil;

    private final String[] toxins = new String[TOXIN_SLOTS];
    private final int[] doses = new int[TOXIN_SLOTS];

    private long trailUntil;
    private boolean echoSpent;
    private int detection;
    private long detectionLastGainAt;
    private long detectionLastDecayAt;
    private long stealthUntil;

    private int strainStage;
    private final Map<UUID, Long> infected = new LinkedHashMap<>();

    // ===== Lehetőség (class core) =====

    public synchronized void armOpportunity(final Opening source, final long now,
                                            final long windowMillis) {
        if (source == null) return;
        opening = source;
        opportunityUntil = now + Math.max(1L, windowMillis);
    }

    public synchronized boolean isOpportunityOpen(final long now) {
        return opening != null && opportunityUntil > now;
    }

    public synchronized Opening opening(final long now) {
        return isOpportunityOpen(now) ? opening : null;
    }

    /** A finisher spends the whole window; a second finisher finds nothing. */
    public synchronized Opening consumeOpportunity(final long now) {
        if (!isOpportunityOpen(now)) {
            opening = null;
            return null;
        }
        final Opening consumed = opening;
        opening = null;
        opportunityUntil = 0L;
        return consumed;
    }

    // ===== Méregkeverő: Toxinkészlet (három hely) + Dózis =====

    /**
     * Applies one dose. An already-held toxin deepens its dose; a new toxin takes a free slot.
     * With all three slots taken by other toxins the kit is full and refuses — it must be
     * catalysed first.
     */
    public synchronized boolean applyToxin(final String toxinId, final int maxDose) {
        if (toxinId == null || toxinId.isBlank()) return false;
        for (int i = 0; i < TOXIN_SLOTS; i++) {
            if (toxinId.equals(toxins[i])) {
                doses[i] = Math.min(Math.max(1, maxDose), doses[i] + 1);
                return true;
            }
        }
        for (int i = 0; i < TOXIN_SLOTS; i++) {
            if (toxins[i] == null) {
                toxins[i] = toxinId;
                doses[i] = 1;
                return true;
            }
        }
        return false;
    }

    public synchronized int filledToxinSlots() {
        int count = 0;
        for (final String toxin : toxins) {
            if (toxin != null) count++;
        }
        return count;
    }

    public synchronized int dose(final String toxinId) {
        for (int i = 0; i < TOXIN_SLOTS; i++) {
            if (toxins[i] != null && toxins[i].equals(toxinId)) return doses[i];
        }
        return 0;
    }

    public synchronized int totalDose() {
        int total = 0;
        for (final int dose : doses) total += dose;
        return total;
    }

    public synchronized List<String> heldToxins() {
        final List<String> held = new ArrayList<>(TOXIN_SLOTS);
        for (final String toxin : toxins) {
            if (toxin != null) held.add(toxin);
        }
        return List.copyOf(held);
    }

    /** Catalysing burns the whole kit at once and reports the total dose it carried. */
    public synchronized int catalyse() {
        final int total = totalDose();
        for (int i = 0; i < TOXIN_SLOTS; i++) {
            toxins[i] = null;
            doses[i] = 0;
        }
        return total;
    }

    // ===== Fantom: Árnyéknyom + Visszhang + Észleltség =====

    public synchronized void armTrail(final long now, final long windowMillis) {
        trailUntil = now + Math.max(1L, windowMillis);
        echoSpent = false;
    }

    public synchronized boolean isTrailLive(final long now) {
        return trailUntil > now;
    }

    public synchronized boolean isEchoArmed(final long now) {
        return isTrailLive(now) && !echoSpent;
    }

    /** The Árnyéknyom carries exactly one Visszhang; the second call inside a trail fails. */
    public synchronized boolean consumeEcho(final long now) {
        if (!isTrailLive(now) || echoSpent) return false;
        echoSpent = true;
        return true;
    }

    public synchronized int addDetection(final int amount, final long now,
                                         final long decayDelayMillis,
                                         final double decayPerSecond) {
        decayDetection(now, decayDelayMillis, decayPerSecond);
        detection = clampPercent(detection + Math.max(0, amount));
        detectionLastGainAt = now;
        detectionLastDecayAt = now;
        return detection;
    }

    public synchronized int detection(final long now, final long decayDelayMillis,
                                      final double decayPerSecond) {
        decayDetection(now, decayDelayMillis, decayPerSecond);
        return detection;
    }

    /** Stealth is always time-boxed; it is never an open-ended untargetable state. */
    public synchronized void enterStealth(final long now, final long durationMillis) {
        stealthUntil = now + Math.max(1L, durationMillis);
    }

    public synchronized boolean isStealthed(final long now, final int breakThreshold,
                                            final long decayDelayMillis,
                                            final double decayPerSecond) {
        if (stealthUntil <= now) return false;
        if (detection(now, decayDelayMillis, decayPerSecond) >= Math.max(1, breakThreshold)) {
            stealthUntil = 0L;
            return false;
        }
        return true;
    }

    public synchronized void breakStealth() {
        stealthUntil = 0L;
    }

    /** Deliberate vent (a doctrine effect). Returns the vented amount. */
    public synchronized int ventDetection(final int amount) {
        final int vented = Math.max(0, Math.min(amount, detection));
        detection -= vented;
        return vented;
    }

    // ===== Pestishozó: Járványtörzs + korlátos fertőzés =====

    public synchronized int mutateStrain(final int maximum) {
        strainStage = Math.max(0, Math.min(Math.max(1, maximum), strainStage + 1));
        return strainStage;
    }

    public synchronized int strainStage() {
        return strainStage;
    }

    /**
     * Registers one infection under a hard entity cap. Carriers never hand the strain on: every
     * infection is seeded by the plaguebringer's own strike, so the spread is finite by
     * construction and can never grow exponentially mob to mob.
     */
    public synchronized boolean infect(final UUID entityId, final int entityCap, final long now,
                                       final long durationMillis) {
        if (entityId == null) return false;
        expireInfections(now);
        if (infected.containsKey(entityId)) return false;
        if (infected.size() >= Math.max(1, entityCap)) return false;
        infected.put(entityId, now + Math.max(1L, durationMillis));
        return true;
    }

    public synchronized boolean isInfected(final UUID entityId, final long now) {
        expireInfections(now);
        return infected.containsKey(entityId);
    }

    public synchronized int infectionCount(final long now) {
        expireInfections(now);
        return infected.size();
    }

    public synchronized List<UUID> infectedIds(final long now) {
        expireInfections(now);
        return List.copyOf(infected.keySet());
    }

    public synchronized void cure(final UUID entityId) {
        if (entityId == null) return;
        infected.remove(entityId);
    }

    /** Spec switch cleanup: every pool, the kit and the whole infection registry are dropped. */
    public synchronized void clearSpecializationState() {
        opening = null;
        opportunityUntil = 0L;
        for (int i = 0; i < TOXIN_SLOTS; i++) {
            toxins[i] = null;
            doses[i] = 0;
        }
        trailUntil = 0L;
        echoSpent = false;
        detection = 0;
        detectionLastGainAt = 0L;
        detectionLastDecayAt = 0L;
        stealthUntil = 0L;
        strainStage = 0;
        infected.clear();
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }

    private void expireInfections(final long now) {
        infected.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private void decayDetection(final long now, final long decayDelayMillis,
                                final double decayPerSecond) {
        if (detection <= 0 || detectionLastGainAt <= 0L || decayPerSecond <= 0.0D) return;
        final long decayStartsAt = detectionLastGainAt + Math.max(0L, decayDelayMillis);
        if (now <= decayStartsAt) return;
        final long from = Math.max(decayStartsAt, detectionLastDecayAt);
        if (now <= from) return;
        final int decay = (int) Math.floor((now - from) / 1000.0D * decayPerSecond);
        if (decay > 0) {
            detection = clampPercent(detection - decay);
            detectionLastDecayAt = now;
        }
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }
}
