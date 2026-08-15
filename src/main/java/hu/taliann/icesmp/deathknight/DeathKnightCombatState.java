package hu.taliann.icesmp.deathknight;

/**
 * Per-player transient Halállovag combat state.
 *
 * <p>The Rúnakör is the class layer: Vér and Fagy runes recharge lazily on their own, while the
 * Halál rune never does — it exists only where the knight transmutes a full Vér/Fagy rune into
 * it, so the wheel is charged as deliberately as it is spent. Vérlovag keeps a fixed-size ring
 * of recent taken damage (never an unbounded log) that converts into either a heal or a shield.
 * Fagylovag stacks Fagyjelek that Zúzás consumes partially or fully. Szentségtelen carries the
 * Dögvész stacks. Durable ghoul mutation state remains in PlayerProfile.</p>
 */
public final class DeathKnightCombatState {

    /** The three runes of the Rúnakör. */
    public enum Rune {
        VER,
        FAGY,
        HALAL
    }

    /** Fixed-size recent-damage ring: the Vér Emlékezete can never grow into a damage log. */
    private static final int MEMORY_SIZE = 8;

    private final int[] runes = new int[Rune.values().length];
    private final long[] runeLastRechargeAt = new long[Rune.values().length];
    private boolean runesPrimed;

    private final double[] memoryDamage = new double[MEMORY_SIZE];
    private final long[] memoryAt = new long[MEMORY_SIZE];
    private int memoryCursor;

    private int frostMarks;

    private int plague;

    // ===== Rúnakör (class core) =====

    /** Fills the self-recharging runes once, so a fresh knight starts with a usable wheel. */
    public synchronized void prime(final int naturalCapacity, final long now) {
        if (runesPrimed) return;
        runesPrimed = true;
        final int capacity = Math.max(1, naturalCapacity);
        runes[Rune.VER.ordinal()] = capacity;
        runes[Rune.FAGY.ordinal()] = capacity;
        runeLastRechargeAt[Rune.VER.ordinal()] = now;
        runeLastRechargeAt[Rune.FAGY.ordinal()] = now;
    }

    public synchronized int runes(final Rune rune, final int naturalCapacity, final long now,
                                  final long rechargeMillis) {
        recharge(naturalCapacity, now, rechargeMillis);
        return runes[rune.ordinal()];
    }

    /** Progress of the next naturally regenerating rune; ready/death runes report 100 or 0. */
    public synchronized int rechargePercent(final Rune rune, final int naturalCapacity,
                                            final long now, final long rechargeMillis) {
        recharge(naturalCapacity, now, rechargeMillis);
        final int index = rune.ordinal();
        if (rune == Rune.HALAL) return runes[index] > 0 ? 100 : 0;
        if (runes[index] >= Math.max(1, naturalCapacity)) return 100;
        final long since = runeLastRechargeAt[index];
        if (since <= 0L) return 0;
        final long step = Math.max(1L, rechargeMillis);
        return (int) Math.max(0L, Math.min(99L, (now - since) * 100L / step));
    }

    public synchronized boolean spendRune(final Rune rune, final int naturalCapacity,
                                          final long now, final long rechargeMillis) {
        recharge(naturalCapacity, now, rechargeMillis);
        final int index = rune.ordinal();
        if (runes[index] <= 0) return false;
        runes[index]--;
        if (rune != Rune.HALAL) runeLastRechargeAt[index] = now;
        return true;
    }

    /**
     * Transmutes one full natural rune into a Halál rune. The Halál rune has no natural
     * recharge, so this deliberate act is its only source.
     */
    public synchronized boolean transmuteToDeath(final int naturalCapacity, final int deathCapacity,
                                                 final long now, final long rechargeMillis) {
        recharge(naturalCapacity, now, rechargeMillis);
        if (runes[Rune.HALAL.ordinal()] >= Math.max(1, deathCapacity)) return false;
        for (final Rune source : new Rune[]{Rune.VER, Rune.FAGY}) {
            if (runes[source.ordinal()] > 0) {
                runes[source.ordinal()]--;
                runeLastRechargeAt[source.ordinal()] = now;
                runes[Rune.HALAL.ordinal()]++;
                return true;
            }
        }
        return false;
    }

    // ===== Vérlovag: Vér Emlékezete =====

    /** Records one hit into the fixed ring; the oldest entry is overwritten, never appended. */
    public synchronized void rememberDamage(final double amount, final long now) {
        if (amount <= 0.0D) return;
        memoryDamage[memoryCursor] = amount;
        memoryAt[memoryCursor] = now;
        memoryCursor = (memoryCursor + 1) % MEMORY_SIZE;
    }

    public synchronized double recentDamage(final long now, final long windowMillis) {
        final long oldest = now - Math.max(1L, windowMillis);
        double total = 0.0D;
        for (int i = 0; i < MEMORY_SIZE; i++) {
            if (memoryAt[i] > oldest) total += memoryDamage[i];
        }
        return total;
    }

    /** The memory is spent whole: one conversion empties it, so it cannot be double-cashed. */
    public synchronized double consumeMemory(final long now, final long windowMillis) {
        final double total = recentDamage(now, windowMillis);
        java.util.Arrays.fill(memoryDamage, 0.0D);
        java.util.Arrays.fill(memoryAt, 0L);
        memoryCursor = 0;
        return total;
    }

    public static int memoryCapacity() {
        return MEMORY_SIZE;
    }

    // ===== Fagylovag: Fagyjelek =====

    public synchronized int addFrostMarks(final int amount, final int maximum) {
        frostMarks = Math.max(0, Math.min(Math.max(1, maximum),
                frostMarks + Math.max(0, amount)));
        return frostMarks;
    }

    public synchronized int frostMarks() {
        return frostMarks;
    }

    /** Partial consume: takes what it can up to the requested amount. */
    public synchronized int consumeFrostMarks(final int amount) {
        final int consumed = Math.max(0, Math.min(frostMarks, Math.max(0, amount)));
        frostMarks -= consumed;
        return consumed;
    }

    /** Full consume: Zúzás takes the whole stack at once. */
    public synchronized int consumeAllFrostMarks() {
        final int consumed = frostMarks;
        frostMarks = 0;
        return consumed;
    }

    // ===== Szentségtelen: Dögvész + ghúl-mutáció =====

    public synchronized int addPlague(final int amount, final int maximum) {
        plague = Math.max(0, Math.min(Math.max(1, maximum), plague + Math.max(0, amount)));
        return plague;
    }

    public synchronized int plague() {
        return plague;
    }

    public synchronized int burstPlague() {
        final int burst = plague;
        plague = 0;
        return burst;
    }

    /** Spec switch cleanup: the wheel is re-primed and every spec pool is dropped. */
    public synchronized void clearSpecializationState() {
        java.util.Arrays.fill(runes, 0);
        java.util.Arrays.fill(runeLastRechargeAt, 0L);
        runesPrimed = false;
        java.util.Arrays.fill(memoryDamage, 0.0D);
        java.util.Arrays.fill(memoryAt, 0L);
        memoryCursor = 0;
        frostMarks = 0;
        plague = 0;
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }

    private void recharge(final int naturalCapacity, final long now, final long rechargeMillis) {
        final int capacity = Math.max(1, naturalCapacity);
        final long step = Math.max(1L, rechargeMillis);
        for (final Rune rune : new Rune[]{Rune.VER, Rune.FAGY}) {
            final int index = rune.ordinal();
            if (runes[index] >= capacity) {
                runeLastRechargeAt[index] = now;
                continue;
            }
            final long since = runeLastRechargeAt[index];
            if (since <= 0L) {
                runeLastRechargeAt[index] = now;
                continue;
            }
            final long elapsed = now - since;
            if (elapsed < step) continue;
            final int restored = (int) Math.min(capacity - runes[index], elapsed / step);
            runes[index] += restored;
            runeLastRechargeAt[index] = since + restored * step;
        }
    }
}
