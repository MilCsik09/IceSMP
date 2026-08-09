package hu.taliann.icesmp.wizard;

/**
 * Per-player transient Varázsló combat state.
 *
 * <p>Rúnaszövés is the class layer: the runtime remembers only the last two schools cast, and a
 * short, explicitly enumerated table turns that ordered pair into one rune reaction. There is no
 * rule engine and no combo DSL — the pairs are a handful of concrete cases. Elementalista keeps
 * one three-slot attunement array (Tűz/Fagy/Vihar) read by two threshold checks, Konvergencia and
 * Elemi Korona, rather than three separate subsystems. Nekromanta keeps only the bounded Holtak
 * Udvara roster of raised kinds; the shard economy stays entirely with the existing Soulforge
 * authority. Durable state remains in PlayerProfile.</p>
 */
public final class WizardCombatState {

    /** The spell schools the weave can recognise. */
    public enum School {
        TUZ,
        FAGY,
        VIHAR,
        ARNY,
        ARKAN
    }

    /** The concrete rune reactions. Every one is an explicit ordered pair, never a derived rule. */
    public enum Reaction {
        GOZROBBANAS,
        JEGVIHAR,
        KOHO,
        ARNYVISSZHANG,
        ARKAN_EROSITES
    }

    /** The Holtak Udvara is a bounded court of raised kinds, never a live entity list. */
    public static final int COURT_SLOTS = 4;

    /** Tűz, Fagy, Vihar — one array, not three subsystems. */
    public static final int ATTUNEMENTS = 3;

    private School previousSchool;
    private School lastSchool;
    private Reaction reaction;
    private long reactionUntil;

    private final int[] attunement = new int[ATTUNEMENTS];
    private long attunementLastGainAt;
    private long attunementLastDecayAt;

    private final String[] court = new String[COURT_SLOTS];

    // ===== Rúnaszövés (class core) =====

    /**
     * Records one cast and returns the reaction its ordered pair produced, or null. Only the last
     * two schools are ever kept, so the weave can never grow into a combo history.
     */
    public synchronized Reaction weave(final School school, final long now,
                                       final long windowMillis) {
        if (school == null) return null;
        previousSchool = lastSchool;
        lastSchool = school;
        final Reaction produced = reactionFor(previousSchool, lastSchool);
        if (produced == null) return null;
        reaction = produced;
        reactionUntil = now + Math.max(1L, windowMillis);
        // The pair is consumed by its own reaction: the same weave cannot fire twice.
        previousSchool = null;
        lastSchool = null;
        return produced;
    }

    public synchronized Reaction armedReaction(final long now) {
        return reaction != null && reactionUntil > now ? reaction : null;
    }

    /** The empowered cast spends the reaction whole. */
    public synchronized Reaction consumeReaction(final long now) {
        final Reaction armed = armedReaction(now);
        reaction = null;
        reactionUntil = 0L;
        return armed;
    }

    public synchronized School lastSchool() {
        return lastSchool;
    }

    /** The whole reaction table. Five concrete ordered pairs — nothing is derived. */
    public static Reaction reactionFor(final School first, final School second) {
        if (first == null || second == null) return null;
        return switch (first) {
            case TUZ -> second == School.FAGY ? Reaction.GOZROBBANAS : null;
            case FAGY -> second == School.VIHAR ? Reaction.JEGVIHAR : null;
            case VIHAR -> second == School.TUZ ? Reaction.KOHO : null;
            case ARNY -> second == School.ARKAN ? Reaction.ARNYVISSZHANG : null;
            case ARKAN -> second == School.ARNY ? Reaction.ARKAN_EROSITES : null;
        };
    }

    // ===== Elementalista: ráhangolódások =====

    public synchronized int addAttunement(final int index, final int amount, final long now,
                                          final long decayDelayMillis,
                                          final double decayPerSecond) {
        if (index < 0 || index >= ATTUNEMENTS) return 0;
        decayAttunements(now, decayDelayMillis, decayPerSecond);
        attunement[index] = clampPercent(attunement[index] + Math.max(0, amount));
        attunementLastGainAt = now;
        attunementLastDecayAt = now;
        return attunement[index];
    }

    public synchronized int attunement(final int index, final long now,
                                       final long decayDelayMillis,
                                       final double decayPerSecond) {
        if (index < 0 || index >= ATTUNEMENTS) return 0;
        decayAttunements(now, decayDelayMillis, decayPerSecond);
        return attunement[index];
    }

    /** Konvergencia: at least two attunements stand at or above the threshold. */
    public synchronized boolean isConvergent(final int threshold, final long now,
                                             final long decayDelayMillis,
                                             final double decayPerSecond) {
        return attunedCount(threshold, now, decayDelayMillis, decayPerSecond) >= 2;
    }

    /** Elemi Korona: all three attunements stand at or above the threshold. */
    public synchronized boolean isCrowned(final int threshold, final long now,
                                          final long decayDelayMillis,
                                          final double decayPerSecond) {
        return attunedCount(threshold, now, decayDelayMillis, decayPerSecond) == ATTUNEMENTS;
    }

    public synchronized int attunedCount(final int threshold, final long now,
                                         final long decayDelayMillis,
                                         final double decayPerSecond) {
        decayAttunements(now, decayDelayMillis, decayPerSecond);
        final int bar = Math.max(1, threshold);
        int count = 0;
        for (final int value : attunement) {
            if (value >= bar) count++;
        }
        return count;
    }

    // ===== Nekromanta: Holtak Udvara =====

    /** Registers one raised kind. The court is bounded and holds kinds, never entity handles. */
    public synchronized boolean raise(final String kindId, final int capacity) {
        if (kindId == null || kindId.isBlank()) return false;
        final int cap = Math.max(1, Math.min(COURT_SLOTS, capacity));
        int occupied = 0;
        for (final String kind : court) {
            if (kind != null) {
                if (kindId.equals(kind)) return false;
                occupied++;
            }
        }
        if (occupied >= cap) return false;
        for (int i = 0; i < COURT_SLOTS; i++) {
            if (court[i] == null) {
                court[i] = kindId;
                return true;
            }
        }
        return false;
    }

    public synchronized int courtSize() {
        int count = 0;
        for (final String kind : court) {
            if (kind != null) count++;
        }
        return count;
    }

    public synchronized boolean holds(final String kindId) {
        for (final String kind : court) {
            if (kindId != null && kindId.equals(kind)) return true;
        }
        return false;
    }

    /** Harvesting releases the whole court at once and reports what it held. */
    public synchronized int harvestCourt() {
        final int harvested = courtSize();
        java.util.Arrays.fill(court, null);
        return harvested;
    }

    /** Spec switch cleanup: the weave, the attunements and the court are all dropped. */
    public synchronized void clearSpecializationState() {
        previousSchool = null;
        lastSchool = null;
        reaction = null;
        reactionUntil = 0L;
        java.util.Arrays.fill(attunement, 0);
        attunementLastGainAt = 0L;
        attunementLastDecayAt = 0L;
        java.util.Arrays.fill(court, null);
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }

    private void decayAttunements(final long now, final long decayDelayMillis,
                                  final double decayPerSecond) {
        if (attunementLastGainAt <= 0L || decayPerSecond <= 0.0D) return;
        final long decayStartsAt = attunementLastGainAt + Math.max(0L, decayDelayMillis);
        if (now <= decayStartsAt) return;
        final long from = Math.max(decayStartsAt, attunementLastDecayAt);
        if (now <= from) return;
        final int decay = (int) Math.floor((now - from) / 1000.0D * decayPerSecond);
        if (decay <= 0) return;
        for (int i = 0; i < ATTUNEMENTS; i++) {
            attunement[i] = clampPercent(attunement[i] - decay);
        }
        attunementLastDecayAt = now;
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }
}
