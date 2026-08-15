package hu.taliann.icesmp.hud;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Stable ids for every independently editable first-party HUD draw group. */
public enum HudComponent {
    GLOBAL("global", false, false),
    FRAME("frame", true, true),
    CLASS_ICON("class-icon", true, true),
    CLASS_NAME("class-name", true, true),
    FACTION("faction", true, true),
    LEVEL_ICON("level-icon", false, false),
    LEVEL_TEXT("level-text", true, true),
    CLASS_XP("class-xp", true, true),
    WALLET_FRAME("wallet-frame", true, true),
    WALLET("wallet", true, true),
    RESOURCE_LABEL("resource-label", true, true),
    RESOURCE_BAR("resource-bar", true, true),
    PRIMARY_MECHANIC("primary-mechanic", true, true),
    SECONDARY_MECHANIC("secondary-mechanic", true, true),
    CHARGES("charges", true, true),
    DK_RUNES("dk-runes", true, true),
    STATE_PROC("state-proc", true, true),
    DETAIL_FRAME("detail-frame", true, true),
    DETAIL_METRICS("detail-metrics", true, true),
    EVENT_ICON("event-icon", false, false),
    EVENT_TEXT("event-text", true, true),
    SURVIVAL_HUD("survival-hud", true, false);

    private static final List<HudComponent> EDITABLE = Arrays.stream(values())
            .filter(HudComponent::rendered).toList();
    private static final List<HudComponent> TARGETS = Arrays.stream(values())
            .filter(component -> component == GLOBAL || component.rendered()).toList();

    private final String id;
    private final boolean rendered;
    private final boolean hideable;

    HudComponent(final String id, final boolean rendered, final boolean hideable) {
        this.id = id;
        this.rendered = rendered;
        this.hideable = hideable;
    }

    public String id() {
        return id;
    }

    public boolean rendered() {
        return rendered;
    }

    public boolean hideable() {
        return hideable;
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
