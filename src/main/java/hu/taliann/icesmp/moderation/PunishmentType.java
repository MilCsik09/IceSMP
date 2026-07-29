package hu.taliann.icesmp.moderation;

/** Supported native moderation actions and their active restriction family. */
public enum PunishmentType {
    WARNING(Family.NONE, false),
    KICK(Family.NONE, false),
    MUTE(Family.MUTE, true),
    TEMPORARY_MUTE(Family.MUTE, true),
    BAN(Family.BAN, true),
    TEMPORARY_BAN(Family.BAN, true),
    UNMUTE(Family.NONE, false),
    UNBAN(Family.NONE, false);

    public enum Family { NONE, MUTE, BAN }

    private final Family family;
    private final boolean restriction;

    PunishmentType(final Family family, final boolean restriction) {
        this.family = family;
        this.restriction = restriction;
    }

    public Family family() {
        return family;
    }

    public boolean isRestriction() {
        return restriction;
    }

    public boolean isTemporary() {
        return this == TEMPORARY_MUTE || this == TEMPORARY_BAN;
    }

    public boolean isRevocationAction() {
        return this == UNMUTE || this == UNBAN;
    }
}
