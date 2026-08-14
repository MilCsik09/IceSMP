package hu.taliann.icesmp.hud;

import java.util.List;

/** Independent synthetic-preview axes; none of these ids is a gameplay mutation target. */
public record HudPreviewSelection(String faction, String playerClass, String state) {

    public static final List<String> FACTIONS = List.of("guest", "red", "blue", "neutral", "dark");
    public static final List<String> CLASSES = List.of("warrior", "evoker", "archer", "shaman", "monk",
            "paladin", "demon_hunter", "druid", "priest", "death_knight", "assassin", "warlock", "wizard");
    public static final List<String> STATES = List.of("representative", "resource", "wallet", "event",
            "spec", "proc", "charges", "dk-runes", "wizard-attunement");

    public HudPreviewSelection {
        faction = accepted(faction, FACTIONS, "guest");
        playerClass = accepted(playerClass, CLASSES, "warrior");
        state = accepted(state, STATES, "representative");
    }

    public static HudPreviewSelection defaults() {
        return new HudPreviewSelection("guest", "warrior", "representative");
    }

    public HudPreviewSelection withFaction(final String value) {
        return new HudPreviewSelection(value, playerClass, state);
    }

    public HudPreviewSelection withClass(final String value) {
        return new HudPreviewSelection(faction, value, state);
    }

    public HudPreviewSelection withState(final String value) {
        return new HudPreviewSelection(faction, playerClass, value);
    }

    public static boolean validFaction(final String value) {
        return value != null && FACTIONS.contains(value.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean validClass(final String value) {
        return value != null && CLASSES.contains(value.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean validState(final String value) {
        return value != null && STATES.contains(value.toLowerCase(java.util.Locale.ROOT));
    }

    private static String accepted(final String raw, final List<String> allowed, final String fallback) {
        final String value = raw == null ? "" : raw.toLowerCase(java.util.Locale.ROOT);
        return allowed.contains(value) ? value : fallback;
    }
}
