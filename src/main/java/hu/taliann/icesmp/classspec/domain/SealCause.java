package hu.taliann.icesmp.classspec.domain;

/** Why a specialization is retained but unavailable. */
public enum SealCause {
    FACTION_MISSING(true),
    SINNER_MARK_MISSING(true),
    QUEST_REQUIREMENT_MISSING(true),
    ADMINISTRATIVE(false),
    PERSISTENCE_FAILURE(false),
    RECOVERY_BLOCK(false);

    private final boolean gateRestorable;

    SealCause(final boolean gateRestorable) {
        this.gateRestorable = gateRestorable;
    }

    public boolean gateRestorable() {
        return gateRestorable;
    }
}
