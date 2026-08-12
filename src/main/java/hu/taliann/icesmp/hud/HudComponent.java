package hu.taliann.icesmp.hud;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Stable ids for every independently editable first-party HUD draw group. */
public enum HudComponent {
    GLOBAL("global", "Teljes HUD", false),
    FRAME("frame", "Fő keret", true),
    CLASS_ICON("class-icon", "Kaszt ikon", true),
    CLASS_NAME("class-name", "Kaszt és specializáció", true),
    FACTION("faction", "Frakció", true),
    LEVEL_ICON("level-icon", "Szint ikon", true),
    LEVEL_TEXT("level-text", "Szint érték", true),
    WALLET_FRAME("wallet-frame", "Valuta keret", true),
    WALLET("wallet", "Valuták", true),
    RESOURCE_LABEL("resource-label", "Erőforrás felirat", true),
    RESOURCE_BAR("resource-bar", "Erőforrás csík", true),
    PRIMARY_MECHANIC("primary-mechanic", "Elsődleges mechanika", true),
    SECONDARY_MECHANIC("secondary-mechanic", "Másodlagos mechanika", true),
    CHARGES("charges", "Töltetek és rúnák", true),
    STATE_PROC("state-proc", "Állapot és proc", true),
    DETAIL_FRAME("detail-frame", "Részlet keret", true),
    DETAIL_METRICS("detail-metrics", "Részlet metrikák", true),
    EVENT_ICON("event-icon", "Esemény ikon", true),
    EVENT_TEXT("event-text", "Esemény szöveg", true);

    private static final List<HudComponent> EDITABLE = Arrays.stream(values())
            .filter(HudComponent::rendered).toList();
    private static final List<HudComponent> TARGETS = List.copyOf(Arrays.asList(values()));

    private final String id;
    private final String displayName;
    private final boolean rendered;

    HudComponent(final String id, final String displayName, final boolean rendered) {
        this.id = id;
        this.displayName = displayName;
        this.rendered = rendered;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean rendered() {
        return rendered;
    }

    public static List<HudComponent> editableValues() {
        return EDITABLE;
    }

    public static List<HudComponent> editorTargets() {
        return TARGETS;
    }

    public static Optional<HudComponent> find(final String raw) {
        if (raw == null) return Optional.empty();
        final String id = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return TARGETS.stream().filter(component -> component.id.equals(id)).findFirst();
    }

    public HudComponent cycle(final int direction) {
        final int index = TARGETS.indexOf(this);
        return TARGETS.get(Math.floorMod(index + Integer.signum(direction), TARGETS.size()));
    }
}
