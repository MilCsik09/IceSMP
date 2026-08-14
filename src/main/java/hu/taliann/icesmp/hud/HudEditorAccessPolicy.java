package hu.taliann.icesmp.hud;

/** Pure command gate used before any editor session or preview is created. */
public final class HudEditorAccessPolicy {

    public enum Decision { ALLOWED, PLAYER_ONLY, NO_PERMISSION, CONFIG_DISABLED }

    private HudEditorAccessPolicy() {
    }

    public static Decision decide(final boolean player, final boolean globalScope,
                                  final boolean permission, final boolean configEnabled,
                                  final boolean personalEnabled) {
        if (!player) return Decision.PLAYER_ONLY;
        if (!configEnabled) return Decision.CONFIG_DISABLED;
        if (globalScope && !permission) return Decision.NO_PERMISSION;
        if (!globalScope && !personalEnabled) return Decision.CONFIG_DISABLED;
        return Decision.ALLOWED;
    }
}
