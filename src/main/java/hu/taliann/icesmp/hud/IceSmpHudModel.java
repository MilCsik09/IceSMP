package hu.taliann.icesmp.hud;

import hu.taliann.icesmp.classspec.integration.ClassHudState;
import hu.taliann.icesmp.managers.HudManager;

import java.util.Objects;
import java.util.List;

/** Immutable display-only input for the first-party HUD renderer. */
public record IceSmpHudModel(String faction, String factionTheme, String factionAccent,
                             String className, int classLevel, String balance,
                             boolean hasClass,
                             int resource, int resourceMax, int resourcePercent,
                             String resourceName, String event,
                             List<HudManager.HudCurrency> currencies, ClassHudState classHud) {

    public IceSmpHudModel {
        faction = Objects.requireNonNullElse(faction, "");
        factionTheme = Objects.requireNonNullElse(factionTheme, "ice");
        factionAccent = Objects.requireNonNullElse(factionAccent, "8BE9FD");
        className = Objects.requireNonNullElse(className, "nincs");
        balance = Objects.requireNonNullElse(balance, "0");
        resourceName = Objects.requireNonNullElse(resourceName, "Erő");
        event = Objects.requireNonNullElse(event, "nyugalom");
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
        resourceMax = Math.max(1, resourceMax);
        resourcePercent = Math.max(0, Math.min(100, resourcePercent));
    }

    public static IceSmpHudModel from(final HudManager.HudSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new IceSmpHudModel(snapshot.faction(), snapshot.factionTheme(), snapshot.factionAccent(),
                snapshot.className(), snapshot.classLevel(), snapshot.balance(), snapshot.hasClass(), snapshot.resource(),
                snapshot.resourceMax(), snapshot.resourcePercent(), snapshot.resourceName(),
                snapshot.event(), snapshot.currencies(), snapshot.classHud());
    }
}
