package hu.taliann.icesmp.warlock;

import java.util.UUID;

/**
 * Per-player transient Boszorkánymester combat state.
 *
 * <p>Paktum és Lélekadósság is the class layer: a pact cast buys power and books the debt that
 * pays for it. The debt never decays on its own — it must be worked off — and past the ceiling
 * the pacts are refused outright. It is a combat meter and nothing else: no balance is stored,
 * transferred or spent anywhere outside this transient state. Átok carries a three-slot
 * Átokgrimoár with per-slot expiry plus one Lélekfonal that can be re-tied. Pusztítás carries
 * Izzó Parázs and the Túlhevülés lockout it can buy. Demonológus keeps NOTHING here: the pact's
 * demons live solely in the durable Profile v2 demonologist.roster companion roster, so this state
 * has no demon container of any kind to drift from it. Durable state remains in PlayerProfile.</p>
 */
public final class WarlockCombatState {

    /** The Átokgrimoár holds exactly three curses. */
    public static final int CURSE_SLOTS = 3;

    private int debt;

    private final String[] curses = new String[CURSE_SLOTS];
    private final long[] curseUntil = new long[CURSE_SLOTS];
    private UUID threadTarget;
    private long threadUntil;

    private int embers;
    private long overheatUntil;

    // ===== Paktum és Lélekadósság (class core) =====

    public synchronized int addDebt(final int amount, final int ceiling) {
        debt = Math.max(0, Math.min(Math.max(1, ceiling), debt + Math.max(0, amount)));
        return debt;
    }

    public synchronized int debt() {
        return debt;
    }

    /** Repayment is always deliberate work: the debt never fades on its own. */
    public synchronized int repayDebt(final int amount) {
        final int repaid = Math.max(0, Math.min(debt, Math.max(0, amount)));
        debt -= repaid;
        return repaid;
    }

    public synchronized boolean isDebtCeilingReached(final int ceiling) {
        return debt >= Math.max(1, ceiling);
    }

    // ===== Átok: Átokgrimoár + Lélekfonal =====

    /** Applies or refreshes a curse. A fourth distinct curse finds no room in the grimoire. */
    public synchronized boolean inscribeCurse(final String curseId, final long now,
                                              final long durationMillis) {
        if (curseId == null || curseId.isBlank()) return false;
        final long until = now + Math.max(1L, durationMillis);
        for (int i = 0; i < CURSE_SLOTS; i++) {
            if (curseId.equals(curses[i])) {
                curseUntil[i] = until;
                return true;
            }
        }
        for (int i = 0; i < CURSE_SLOTS; i++) {
            if (curses[i] == null || curseUntil[i] <= now) {
                curses[i] = curseId;
                curseUntil[i] = until;
                return true;
            }
        }
        return false;
    }

    public synchronized int activeCurses(final long now) {
        int count = 0;
        for (int i = 0; i < CURSE_SLOTS; i++) {
            if (curses[i] != null && curseUntil[i] > now) count++;
        }
        return count;
    }

    public synchronized boolean hasCurse(final String curseId, final long now) {
        for (int i = 0; i < CURSE_SLOTS; i++) {
            if (curseId != null && curseId.equals(curses[i]) && curseUntil[i] > now) return true;
        }
        return false;
    }

    /** Átkötés: the thread always names exactly one target, and re-tying moves it. */
    public synchronized void tieThread(final UUID targetId, final long now,
                                       final long windowMillis) {
        if (targetId == null) return;
        threadTarget = targetId;
        threadUntil = now + Math.max(1L, windowMillis);
    }

    public synchronized UUID threadTarget(final long now) {
        return threadTarget != null && threadUntil > now ? threadTarget : null;
    }

    public synchronized void cutThread() {
        threadTarget = null;
        threadUntil = 0L;
    }

    // ===== Pusztítás: Izzó Parázs + Túlhevülés =====

    public synchronized int addEmbers(final int amount, final int maximum) {
        embers = Math.max(0, Math.min(Math.max(1, maximum), embers + Math.max(0, amount)));
        return embers;
    }

    public synchronized int embers() {
        return embers;
    }

    /** The burst always takes every ember; the count it took is what it pays out on. */
    public synchronized int spendEmbers() {
        final int spent = embers;
        embers = 0;
        return spent;
    }

    public synchronized void enterOverheat(final long now, final long durationMillis) {
        overheatUntil = now + Math.max(1L, durationMillis);
    }

    public synchronized boolean isOverheated(final long now) {
        return overheatUntil > now;
    }

    /** Spec switch cleanup: the debt is written off with everything else it belonged to. */
    public synchronized void clearSpecializationState() {
        debt = 0;
        for (int i = 0; i < CURSE_SLOTS; i++) {
            curses[i] = null;
            curseUntil[i] = 0L;
        }
        threadTarget = null;
        threadUntil = 0L;
        embers = 0;
        overheatUntil = 0L;
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }
}
