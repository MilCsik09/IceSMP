package hu.taliann.icesmp.itemization;

import java.util.Objects;

/** Native developer request; a request describes work and grants no execution authority. */
public record ItemDeveloperMutation(Kind kind, String lockedStat, double minimumQuality,
                                    boolean stabilitySeal, String runeId, int socketIndex) {
    public enum Kind {
        CLONE_PROTOTYPE, REROLL_PROTOTYPE, ASCEND_PROTOTYPE, ADD_RUNE_PROTOTYPE,
        REMOVE_RUNE_PROTOTYPE, REROLL_CANONICAL, ASCEND_CANONICAL,
        ADD_RUNE_CANONICAL, REMOVE_RUNE_CANONICAL, REFRESH_PRESENTATION;

        public boolean prototype() { return name().endsWith("_PROTOTYPE"); }
        public boolean reroll() { return this == REROLL_PROTOTYPE || this == REROLL_CANONICAL; }
        public boolean ascend() { return this == ASCEND_PROTOTYPE || this == ASCEND_CANONICAL; }
        public boolean addRune() { return this == ADD_RUNE_PROTOTYPE || this == ADD_RUNE_CANONICAL; }
        public boolean removeRune() { return this == REMOVE_RUNE_PROTOTYPE || this == REMOVE_RUNE_CANONICAL; }
    }

    public ItemDeveloperMutation {
        Objects.requireNonNull(kind);
        lockedStat = lockedStat == null || lockedStat.isBlank() ? "" : ItemStatCatalog.normalizeId(lockedStat);
        runeId = runeId == null || runeId.isBlank() ? "" : ItemStatCatalog.normalizeId(runeId);
        if (!Double.isFinite(minimumQuality) || minimumQuality < 0 || minimumQuality > 1
                || (!kind.reroll() && (!lockedStat.isEmpty() || minimumQuality != 0 || stabilitySeal))
                || (kind.addRune() ? !runeId.startsWith("runa_") : !runeId.isEmpty())
                || (kind.removeRune() ? socketIndex < 0 || socketIndex > 1 : socketIndex != -1)) {
            throw new IllegalArgumentException("Invalid native developer item request");
        }
    }

    public static ItemDeveloperMutation simple(Kind kind) {
        return new ItemDeveloperMutation(kind, "", 0, false, "", -1);
    }
}
