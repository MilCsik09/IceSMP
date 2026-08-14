package hu.taliann.icesmp.prologue;

/** Tartós Season 0 világállapot; a normál SeasonManager ettől függetlenül Season 1+-ot kezel. */
public enum PrologueState {
    DORMANT,
    UNSTABLE,
    BREACHING,
    FINALE,
    GATE_OPEN,
    COMPLETED;

    public boolean gateOpen() {
        return this == GATE_OPEN || this == COMPLETED;
    }

    public boolean completed() {
        return this == COMPLETED;
    }
}
