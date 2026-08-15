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
    PLAYER_GROUP("player-group", false, false),
    PLAYER_FRAME("player-frame", true, true),
    PLAYER_NAME("player-name", true, true),
    PLAYER_HEALTH_BAR("player-health-bar", true, true),
    PLAYER_HEALTH_TEXT("player-health-text", true, true),
    PLAYER_HEALTH_PERCENT("player-health-percent", true, true),
    PLAYER_ABSORPTION("player-absorption", true, true),
    PLAYER_ARMOR("player-armor", true, true),
    PLAYER_FOOD("player-food", true, true),
    PLAYER_OXYGEN("player-oxygen", true, true),
    TARGET_GROUP("target-group", false, true),
    TARGET_FRAME("target-frame", true, true),
    TARGET_ICON("target-icon", true, true),
    TARGET_NAME("target-name", true, true),
    TARGET_LEVEL("target-level", true, true),
    TARGET_HEALTH_BAR("target-health-bar", true, true),
    TARGET_HEALTH_TEXT("target-health-text", true, true),
    TARGET_RESOURCE("target-resource", true, true),
    TARGET_STATUS("target-status", true, true),
    PARTY_GROUP("party-group", false, true),
    PARTY_FRAME("party-frame", true, true),
    PARTY_NAME("party-name", true, true),
    PARTY_HEALTH("party-health", true, true),
    PARTY_RESOURCE("party-resource", true, true),
    PARTY_STATUS("party-status", true, true);

    private static final List<HudComponent> EDITABLE = Arrays.stream(values())
            .filter(component -> component != GLOBAL
                    && (component.rendered() || component.isGroup())).toList();
    private static final List<HudComponent> TARGETS = Arrays.stream(values())
            .filter(component -> component == GLOBAL || EDITABLE.contains(component)).toList();

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

    public boolean isGroup() {
        return this == PLAYER_GROUP || this == TARGET_GROUP || this == PARTY_GROUP;
    }

    public HudComponent parentGroup() {
        return switch (this) {
            case PLAYER_FRAME, PLAYER_NAME, PLAYER_HEALTH_BAR, PLAYER_HEALTH_TEXT, PLAYER_HEALTH_PERCENT,
                    PLAYER_ABSORPTION,
                    PLAYER_ARMOR, PLAYER_FOOD, PLAYER_OXYGEN -> PLAYER_GROUP;
            case TARGET_FRAME, TARGET_ICON, TARGET_NAME, TARGET_LEVEL, TARGET_HEALTH_BAR,
                    TARGET_HEALTH_TEXT, TARGET_RESOURCE, TARGET_STATUS -> TARGET_GROUP;
            case PARTY_FRAME, PARTY_NAME, PARTY_HEALTH, PARTY_RESOURCE, PARTY_STATUS -> PARTY_GROUP;
            default -> null;
        };
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
