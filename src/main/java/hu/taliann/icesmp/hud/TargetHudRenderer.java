package hu.taliann.icesmp.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.key.Key;

import java.util.List;

/** Player- and bestiary-specific target frames in the top-left frame cluster. */
public final class TargetHudRenderer {
    static final int PANEL_WIDTH = 240;
    static final int PANEL_ADVANCE = PANEL_WIDTH + 1;
    static final int BAR_SEGMENTS = 16;
    private static final Key HEADER_FONT = Key.key("icesmp_hud", "survival/target_header");
    private static final Key STATUS_FONT = Key.key("icesmp_hud", "survival/target_status");
    private static final Key HEALTH_SEGMENT_FONT = Key.key("icesmp_hud", "survival/target_health_segments");
    private static final Key HEALTH_FONT = Key.key("icesmp_hud", "survival/target_health");
    private static final Key RESOURCE_SEGMENT_FONT = Key.key("icesmp_hud", "survival/target_resource_segments");
    private static final Key STATS_FONT = Key.key("icesmp_hud", "survival/target_stats");
    private static final List<String> THEMES = List.of("ice", "ember", "frost", "guild", "lich");

    public Component render(final TargetHudState state, final HudLayoutSnapshot layout,
                            final HudComponent highlighted) {
        if (state == null) return Component.empty();
        final HudLayoutSnapshot safeLayout = layout == null ? HudLayoutSnapshot.defaults() : layout;
        if (!safeLayout.componentLayout(HudComponent.TARGET_GROUP).visible()) return Component.empty();
        final TextComponent.Builder output = Component.text().shadowColor(ShadowColor.none());
        final TextColor accent = state.player()
                ? SurvivalHudRenderer.accent(state.factionAccent(), 0x8BE9FD)
                : mobAccent(state);

        output.append(SurvivalHudRenderer.glyph(HudComponent.TARGET_FRAME, 0,
                SurvivalHudRenderer.PANEL_FONT, panelGlyph(state), PANEL_ADVANCE, accent,
                safeLayout, 0, highlighted));
        output.append(SurvivalHudRenderer.glyph(HudComponent.TARGET_ICON, 12,
                SurvivalHudRenderer.ICON_FONT, iconGlyph(state), SurvivalHudRenderer.ICON_ADVANCE,
                TextColor.color(0xFFFFFF), safeLayout, 0, highlighted));
        output.append(SurvivalHudRenderer.text(HudComponent.TARGET_NAME, 32,
                HEADER_FONT, state.name(), accent, 142,
                safeLayout, 0, highlighted));
        output.append(SurvivalHudRenderer.centeredText(HudComponent.TARGET_LEVEL, 214,
                HEADER_FONT, state.level() <= 0 ? "—" : Integer.toString(state.level()),
                TextColor.color(0xF2E7CF), 28, safeLayout, 0, highlighted));
        output.append(SurvivalHudRenderer.centeredText(HudComponent.TARGET_STATUS, 120,
                STATUS_FONT, state.status().isBlank() ? state.typeLabel() : state.status(),
                TextColor.color(0xC5D0DB), 190, safeLayout, 0, highlighted));
        SurvivalHudRenderer.drawSegments(output, HudComponent.TARGET_HEALTH_BAR, 16,
                HEALTH_SEGMENT_FONT, SurvivalHudRenderer.HEALTH_TRACK,
                SurvivalHudRenderer.healthFill(state.healthPercent()), state.healthPercent(),
                BAR_SEGMENTS, 13, SurvivalHudRenderer.HEALTH_SEGMENT_ADVANCE,
                safeLayout, 0, highlighted);
        output.append(SurvivalHudRenderer.centeredText(HudComponent.TARGET_HEALTH_TEXT, 120,
                HEALTH_FONT,
                SurvivalHudRenderer.compact(state.health()) + " / "
                        + SurvivalHudRenderer.compact(state.maximumHealth()) + " HP • "
                        + state.healthPercent() + "%",
                SurvivalHudRenderer.healthColor(state.healthPercent()), 210,
                safeLayout, 0, highlighted));
        if (state.player() && state.resourceMaximum() > 0) {
            SurvivalHudRenderer.drawSegments(output, HudComponent.TARGET_RESOURCE, 16,
                    RESOURCE_SEGMENT_FONT, SurvivalHudRenderer.MINI_TRACK,
                    SurvivalHudRenderer.MINI_RESOURCE, state.resourcePercent(), 16, 13,
                    SurvivalHudRenderer.MINI_SEGMENT_ADVANCE, safeLayout, 0, highlighted);
            output.append(SurvivalHudRenderer.centeredText(HudComponent.TARGET_RESOURCE, 120,
                    STATS_FONT, state.resourceName() + " "
                            + state.resource() + "/" + state.resourceMaximum(),
                    TextColor.color(0x9FE7F2), 190, safeLayout, 0, highlighted));
        }
        return output.build();
    }

    private static char panelGlyph(final TargetHudState state) {
        if (state.player()) {
            final int theme = THEMES.indexOf(state.factionTheme());
            return (char) (0xEB05 + Math.max(0, theme));
        }
        if (state.rank().bossLike()) return '\uEB0E';
        if (state.rank().eliteLike() || state.rank() == TargetHudState.Rank.VETERAN) return '\uEB0D';
        return switch (state.kind()) {
            case PASSIVE -> '\uEB0A';
            case NEUTRAL -> '\uEB0B';
            case HOSTILE, PLAYER -> '\uEB0C';
        };
    }

    private static char iconGlyph(final TargetHudState state) {
        if (state.player()) return SurvivalHudRenderer.ICON_PLAYER;
        if (state.rank().bossLike()) return SurvivalHudRenderer.ICON_BOSS;
        return switch (state.kind()) {
            case PASSIVE -> SurvivalHudRenderer.ICON_PASSIVE;
            case NEUTRAL -> SurvivalHudRenderer.ICON_NEUTRAL;
            case HOSTILE, PLAYER -> SurvivalHudRenderer.ICON_HOSTILE;
        };
    }

    private static TextColor mobAccent(final TargetHudState state) {
        if (state.rank().bossLike()) return TextColor.color(0xD96EF5);
        if (state.rank().eliteLike() || state.rank() == TargetHudState.Rank.VETERAN) {
            return TextColor.color(0xE8B14E);
        }
        return switch (state.kind()) {
            case PASSIVE -> TextColor.color(0x7DD69A);
            case NEUTRAL -> TextColor.color(0xE0BF62);
            case HOSTILE, PLAYER -> TextColor.color(0xE7685F);
        };
    }
}
