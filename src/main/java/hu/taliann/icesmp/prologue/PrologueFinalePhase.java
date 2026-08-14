package hu.taliann.icesmp.prologue;

/** Restart után visszaállítható Prologue-finálé checkpointok. */
public enum PrologueFinalePhase {
    IDLE,
    PREPARING,
    GATHERING,
    BREACH_1,
    BREACH_2,
    ELITE_WAVE,
    BOSS_INTRO,
    BOSS_FIGHT,
    FALSE_END,
    GATE_AWAKENING,
    EPILOGUE,
    COMPLETED,
    ABORTED;

    public boolean running() {
        return this != IDLE && this != COMPLETED && this != ABORTED;
    }

    public boolean irreversibleVictoryPath() {
        return ordinal() >= FALSE_END.ordinal() && this != ABORTED;
    }
}
