package hu.taliann.icesmp.pve;

import java.util.Locale;

/** Reusable behavior layer; abilities remain registry entries instead of bespoke engines. */
public enum MobArchetype {
    BRUISER,
    CHARGER,
    SKIRMISHER,
    RANGED,
    ARTILLERY,
    DEFENDER,
    SUPPORT,
    HEALER,
    SUMMONER,
    ASSASSIN,
    CONTROLLER,
    FLYING;

    public static MobArchetype parse(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("mob archetype required");
        return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
