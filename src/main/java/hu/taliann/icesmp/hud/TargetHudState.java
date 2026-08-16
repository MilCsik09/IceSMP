package hu.taliann.icesmp.hud;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Immutable combat-target projection; no live entity is read while composing the HUD. */
public record TargetHudState(UUID targetId, String name, Kind kind, Rank rank,
                             String factionTheme, String factionAccent, int level,
                             double health, double maximumHealth,
                             String resourceName, int resource, int resourceMaximum,
                             String status) {

    public enum Kind { PLAYER, PASSIVE, NEUTRAL, HOSTILE }
    public enum Rank { NORMAL, ELITE, BOSS }

    public TargetHudState {
        name = Objects.requireNonNullElse(name, "Célpont");
        kind = kind == null ? Kind.HOSTILE : kind;
        rank = rank == null ? Rank.NORMAL : rank;
        factionTheme = Objects.requireNonNullElse(factionTheme, "ice");
        factionAccent = Objects.requireNonNullElse(factionAccent, "8BE9FD");
        level = Math.max(0, level);
        maximumHealth = positive(maximumHealth, 20.0D);
        health = Math.max(0.0D, Math.min(maximumHealth, finite(health, 0.0D)));
        resourceName = Objects.requireNonNullElse(resourceName, "");
        resourceMaximum = Math.max(0, resourceMaximum);
        resource = Math.max(0, Math.min(resourceMaximum, resource));
        status = Objects.requireNonNullElse(status, "");
    }

    public int healthPercent() {
        return (int) Math.round(health / maximumHealth * 100.0D);
    }

    public int resourcePercent() {
        return resourceMaximum <= 0 ? 0
                : (int) Math.round(resource * 100.0D / resourceMaximum);
    }

    public boolean player() {
        return kind == Kind.PLAYER;
    }

    public String typeLabel() {
        if (player()) return status.isBlank() ? "Játékos" : status;
        if (rank == Rank.BOSS) return "Boss";
        if (rank == Rank.ELITE) return "Elit";
        return switch (kind) {
            case PASSIVE -> "Békés";
            case NEUTRAL -> "Semleges";
            case HOSTILE -> "Ellenséges";
            case PLAYER -> "Játékos";
        };
    }

    public static TargetHudState previewPlayer() {
        return new TargetHudState(UUID.randomUUID(), "Frakciótag", Kind.PLAYER, Rank.NORMAL,
                "ember", "E7683F", 42, 82, 100,
                "Düh", 72, 100, "Szövetséges • Harcos");
    }

    public static TargetHudState previewMob() {
        return new TargetHudState(UUID.randomUUID(), "Csontváz őr", Kind.HOSTILE, Rank.ELITE,
                "ice", "D65A55", 18, 145, 220, "", 0, 0, "Elit");
    }

    public String normalizedKind() {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    private static double positive(final double value, final double fallback) {
        final double safe = finite(value, fallback);
        return safe > 0.0D ? safe : fallback;
    }

    private static double finite(final double value, final double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
