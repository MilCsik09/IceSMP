package hu.taliann.icesmp.hud;

import java.util.Objects;

/** Immutable owner-thread snapshot rendered by the top-left player frame. */
public record PlayerHudState(String name, String factionTheme, String factionAccent,
                             SurvivalHudState survival) {
    public PlayerHudState {
        name = Objects.requireNonNullElse(name, "Játékos");
        factionTheme = Objects.requireNonNullElse(factionTheme, "ice");
        factionAccent = Objects.requireNonNullElse(factionAccent, "8BE9FD");
        survival = survival == null
                ? new SurvivalHudState(20, 20, 0, 0, 30, 20, 20, 300, 300)
                : survival;
    }

    public static PlayerHudState preview() {
        return new PlayerHudState("Játékos", "frost", "8BE9FD",
                new SurvivalHudState(78.5, 120, 6, 28, 30, 17, 20, 180, 300));
    }
}
