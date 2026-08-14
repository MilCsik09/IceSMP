package hu.taliann.icesmp.hud;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Stable ids for every independently editable first-party HUD draw group. */
public enum HudComponent {
    GLOBAL("global", false),
    FRAME("frame", true),
    CLASS_ICON("class-icon", true),
    CLASS_NAME("class-name", true),
    FACTION("faction", true),
    LEVEL_ICON("level-icon", false),
    LEVEL_TEXT("level-text", true),
    WALLET_FRAME("wallet-frame", true),
    WALLET("wallet", true),
    RESOURCE_LABEL("resource-label", true),
    RESOURCE_BAR("resource-bar", true),
    PRIMARY_MECHANIC("primary-mechanic", true),
    SECONDARY_MECHANIC("secondary-mechanic", true),
    CHARGES("charges", true),
    STATE_PROC("state-proc", true),
    DETAIL_FRAME("detail-frame", true),
    DETAIL_METRICS("detail-metrics", true),
    EVENT_ICON("event-icon", false),
    EVENT_TEXT("event-text", true);

    private static final List<HudComponent> EDITABLE = Arrays.stream(values())
            .filter(HudComponent::rendered).toList();
    private static final List<HudComponent> TARGETS = Arrays.stream(values())
            .filter(component -> component == GLOBAL || component.rendered()).toList();

    private final String id;
    private final boolean rendered;

    HudComponent(final String id, final boolean rendered) {
        this.id = id;
        this.rendered = rendered;
    }

    public String id() {
        return id;
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
