package hu.taliann.icesmp.prologue;

/** Olethropyla Season 0 eszkalációs fázisai. */
public enum PrologueStage {
    SILENCE("Hallgatás", 92),
    CRACKS("Repedések", 78),
    LEAK("Szivárgás", 43),
    COLLAPSE("Összeomlás", 17);

    private final String displayName;
    private final int defaultStability;

    PrologueStage(final String displayName, final int defaultStability) {
        this.displayName = displayName;
        this.defaultStability = defaultStability;
    }

    public String displayName() { return displayName; }
    public int defaultStability() { return defaultStability; }
}
