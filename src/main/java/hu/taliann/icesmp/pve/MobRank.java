package hu.taliann.icesmp.pve;

import java.util.Locale;

/** Canonical PvE rank vocabulary shared by authored and promoted mobs. */
public enum MobRank {
    NORMAL,
    VETERAN,
    ELITE,
    CHAMPION,
    MINIBOSS,
    BOSS,
    WORLD_BOSS;

    public static MobRank parse(final String raw) {
        if (raw == null || raw.isBlank()) return NORMAL;
        return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public boolean naturalPromotion() {
        return this == VETERAN || this == ELITE;
    }

    public boolean bossLike() {
        return ordinal() >= MINIBOSS.ordinal();
    }
}
