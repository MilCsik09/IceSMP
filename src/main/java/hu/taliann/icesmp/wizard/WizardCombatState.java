package hu.taliann.icesmp.wizard;

/**
 * Per-player transient Varázsló combat state.
 *
 * <p>Rúnaszövés is the class layer: the runtime remembers only the last two schools cast, and a
 * short, explicitly enumerated table turns that ordered pair into one rune reaction. There is no
 * rule engine and no combo DSL — the pairs are a handful of concrete cases. Elementalista keeps
 * one three-slot attunement array (Tűz/Fagy/Vihar) read by two threshold checks, Konvergencia and
 * Elemi Korona, rather than three separate subsystems. Nekromanta keeps NOTHING here: the Holtak
 * Udvara lives solely in the durable Profile v2 necromancer.court companion roster, and the shard
 * economy stays entirely with the existing Soulforge authority. Durable state remains in
 * PlayerProfile.</p>
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

    /** Tűz, Fagy, Vihar — one array, not three subsystems. */
    public static final int ATTUNEMENTS = 3;

    private School previousSchool;
    private School lastSchool;
    private Reaction reaction;
    private long reactionUntil;

    private final int[] attunement = new int[ATTUNEMENTS];
    private long attunementLastGainAt;
    private int decayApplied;

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
        decayApplied = 0;
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

    /** Spec switch cleanup: the weave and the attunements are dropped. */
    public synchronized void clearSpecializationState() {
        previousSchool = null;
        lastSchool = null;
        reaction = null;
        reactionUntil = 0L;
        java.util.Arrays.fill(attunement, 0);
        attunementLastGainAt = 0L;
        decayApplied = 0;
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }

    /**
     * Lazy decay that cannot drift with how often it is polled.
     *
     * <p>The total decay owed is computed from the fixed anchor — the moment decay began after the
     * last gain — so it is a pure function of elapsed time. Each call subtracts only the part not yet
     * applied, and the sub-point remainder is therefore carried instead of being truncated away: ten
     * reads in a second decay exactly as much as one read after that second.</p>
     */
    private void decayAttunements(final long now, final long decayDelayMillis,
                                  final double decayPerSecond) {
        if (attunementLastGainAt <= 0L || decayPerSecond <= 0.0D) return;
        final long decayStartsAt = attunementLastGainAt + Math.max(0L, decayDelayMillis);
        if (now <= decayStartsAt) return;
        final int owed = (int) Math.floor((now - decayStartsAt) / 1000.0D * decayPerSecond);
        final int pending = owed - decayApplied;
        if (pending <= 0) return;
        decayApplied = owed;
        for (int i = 0; i < ATTUNEMENTS; i++) {
            attunement[i] = clampPercent(attunement[i] - pending);
        }
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }
}
