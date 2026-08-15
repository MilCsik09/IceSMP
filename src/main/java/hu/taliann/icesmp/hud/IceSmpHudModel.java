package hu.taliann.icesmp.hud;

import hu.taliann.icesmp.classspec.integration.ClassHudState;
import hu.taliann.icesmp.managers.HudManager;

import java.util.Objects;
import java.util.List;

/** Immutable display-only input for the first-party HUD renderer. */
public record IceSmpHudModel(String faction, String factionTheme, String factionAccent,
                             String className, int classLevel, ClassXpProgress classXp, String balance,
                             boolean hasClass,
                             int resource, int resourceMax, int resourcePercent,
                             String resourceName, String event,
                             List<HudManager.HudCurrency> currencies, ClassHudState classHud) {

    public IceSmpHudModel {
        faction = Objects.requireNonNullElse(faction, "");
        factionTheme = Objects.requireNonNullElse(factionTheme, "ice");
        factionAccent = Objects.requireNonNullElse(factionAccent, "8BE9FD");
        className = Objects.requireNonNullElse(className, "nincs");
        classXp = classXp == null ? ClassXpProgress.empty() : classXp;
        balance = Objects.requireNonNullElse(balance, "0");
        resourceName = Objects.requireNonNullElse(resourceName, "Erő");
        event = Objects.requireNonNullElse(event, "nyugalom");
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
        resourceMax = Math.max(1, resourceMax);
        resourcePercent = Math.max(0, Math.min(100, resourcePercent));
    }

    public IceSmpHudModel(final String faction, final String factionTheme,
                          final String factionAccent, final String className,
                          final int classLevel, final String balance, final boolean hasClass,
                          final int resource, final int resourceMax, final int resourcePercent,
                          final String resourceName, final String event,
                          final List<HudManager.HudCurrency> currencies,
                          final ClassHudState classHud) {
        this(faction, factionTheme, factionAccent, className, classLevel,
                ClassXpProgress.empty(), balance, hasClass, resource, resourceMax,
                resourcePercent, resourceName, event, currencies, classHud);
    }

    public static IceSmpHudModel from(final HudManager.HudSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new IceSmpHudModel(snapshot.faction(), snapshot.factionTheme(), snapshot.factionAccent(),
                snapshot.className(), snapshot.classLevel(), snapshot.classXp(), snapshot.balance(), snapshot.hasClass(), snapshot.resource(),
                snapshot.resourceMax(), snapshot.resourcePercent(), snapshot.resourceName(),
                snapshot.event(), snapshot.currencies(), snapshot.classHud());
    }
}
