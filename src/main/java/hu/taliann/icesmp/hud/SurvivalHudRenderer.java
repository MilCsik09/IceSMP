package hu.taliann.icesmp.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;

/** Pure bottom-centred HP, armor, food and oxygen compositor. */
public final class SurvivalHudRenderer {

    static final int HEALTH_SEGMENTS = 20;
    static final int MINI_SEGMENTS = 10;
    static final int HEALTH_SEGMENT_ADVANCE = 11;
    static final int MINI_SEGMENT_ADVANCE = 6;
    static final int PANEL_WIDTH = 252;
    static final int TEXT_ADVANCE = 7;

    private static final int SPACE_MIN = IceSmpHudRenderer.SPACE_MIN;
    private static final int SPACE_MAX = IceSmpHudRenderer.SPACE_MAX;
    private static final int SPACE_FIRST = IceSmpHudRenderer.SPACE_FIRST;
    private static final int HEALTH_BAR_X = -110;
    private static final int[] NORMAL_STAT_CENTERS = {-63, 63};
    private static final int[] AIR_STAT_CENTERS = {-84, 0, 84};
    private static final int MINI_BAR_OFFSET = -22;
    private static final int MINI_ICON_OFFSET = -38;
    private static final int MINI_TEXT_OFFSET = 8;

    private static final Key SPACE_FONT = Key.key("icesmp_hud", "space");
    private static final Key PANEL_FONT = Key.key("icesmp_hud", "survival/panel");
    private static final Key HEALTH_SEGMENT_FONT = Key.key("icesmp_hud", "survival/health_segments");
    private static final Key MINI_SEGMENT_FONT = Key.key("icesmp_hud", "survival/mini_segments");
    private static final Key ICON_FONT = Key.key("icesmp_hud", "survival/icons");
    private static final Key HEADER_FONT = Key.key("icesmp_hud", "survival/text_header");
    private static final Key PERCENT_FONT = Key.key("icesmp_hud", "survival/text_percent");
    private static final Key STATS_FONT = Key.key("icesmp_hud", "survival/text_stats");

    private static final char PANEL = '\uEB00';
    private static final char PANEL_WITH_AIR = '\uEB01';
    private static final char HEALTH_TRACK = '\uEB10';
    private static final char HEALTH_FILL = '\uEB11';
    private static final char HEALTH_WARN = '\uEB12';
    private static final char HEALTH_CRITICAL = '\uEB13';
    private static final char MINI_TRACK = '\uEB20';
    private static final char MINI_ARMOR = '\uEB21';
    private static final char MINI_FOOD = '\uEB22';
    private static final char MINI_AIR = '\uEB23';
    private static final char ICON_ARMOR = '\uEB30';
    private static final char ICON_FOOD = '\uEB31';
    private static final char ICON_AIR = '\uEB32';
    private static final TextColor EDITOR_HIGHLIGHT = TextColor.color(0xFFD740);

    public Component render(final SurvivalHudState state, final SurvivalHudLayout layout) {
        return render(state, layout, false);
    }

    public Component render(final SurvivalHudState state, final SurvivalHudLayout layout,
                            final boolean highlighted) {
        final SurvivalHudLayout safeLayout = layout == null ? SurvivalHudLayout.defaults() : layout;
        final TextComponent.Builder output = Component.text().shadowColor(ShadowColor.none());
        final boolean airVisible = airVisible(state);
        output.append(glyph(-PANEL_WIDTH / 2, PANEL_FONT,
                airVisible ? PANEL_WITH_AIR : PANEL, PANEL_WIDTH,
                highlighted ? EDITOR_HIGHLIGHT : TextColor.color(0xFFFFFF), safeLayout));

        drawSegments(output, HEALTH_BAR_X, HEALTH_SEGMENT_FONT,
                HEALTH_TRACK, healthFill(state.healthPercent()), state.healthPercent(),
                HEALTH_SEGMENTS, HEALTH_SEGMENT_ADVANCE, safeLayout);
        final int[] centers = airVisible ? AIR_STAT_CENTERS : NORMAL_STAT_CENTERS;
        drawStat(output, centers[0], ICON_ARMOR, MINI_ARMOR, state.armorPercent(),
                compact(state.armor()) + "/" + compact(state.maximumArmor()),
                TextColor.color(0xCAD8E8), safeLayout);
        drawStat(output, centers[1], ICON_FOOD, MINI_FOOD, state.foodPercent(),
                state.food() + "/" + state.maximumFood(),
                TextColor.color(0xF0C878), safeLayout);
        if (airVisible) {
            drawStat(output, centers[2], ICON_AIR, MINI_AIR, state.airPercent(),
                    state.air() + "/" + state.maximumAir(),
                    TextColor.color(0x8DE8F4), safeLayout);
        }

        output.append(centeredText(0, HEADER_FONT, healthLine(state),
                healthColor(state.healthPercent()), 198, safeLayout));
        output.append(centeredText(0, PERCENT_FONT, state.healthPercent() + "%",
                TextColor.color(0xF7FBFF), 48, safeLayout));
        return output.build();
    }

    /** Vanilla-font emergency text remains readable even if a custom glyph fails to compose. */
    public Component fallback(final SurvivalHudState state) {
        final String air = airVisible(state)
                ? " • O2 " + state.air() + "/" + state.maximumAir() : "";
        return Component.text(healthLine(state) + " • " + state.healthPercent() + "%"
                        + " • Páncél " + compact(state.armor())
                        + " • Étel " + state.food() + "/" + state.maximumFood() + air,
                healthColor(state.healthPercent())).shadowColor(ShadowColor.none());
    }

    private static void drawStat(final TextComponent.Builder output, final int center,
                                 final char icon, final char fill, final int percent,
                                 final String value, final TextColor color,
                                 final SurvivalHudLayout layout) {
        drawSegments(output, center + MINI_BAR_OFFSET, MINI_SEGMENT_FONT,
                MINI_TRACK, fill, percent, MINI_SEGMENTS, MINI_SEGMENT_ADVANCE, layout);
        output.append(glyph(center + MINI_ICON_OFFSET, ICON_FONT, icon, 14,
                TextColor.color(0xFFFFFF), layout));
        output.append(centeredText(center + MINI_TEXT_OFFSET, STATS_FONT,
                value, color, 70, layout));
    }

    static boolean airVisible(final SurvivalHudState state) {
        return state != null && state.air() < state.maximumAir();
    }

    private static void drawSegments(final TextComponent.Builder output, final int x,
                                     final Key font, final char track, final char fill,
                                     final int percent, final int count, final int advance,
                                     final SurvivalHudLayout layout) {
        int active = (int) Math.round(Math.max(0, Math.min(100, percent)) * count / 100.0D);
        if (percent > 0) active = Math.max(1, active);
        for (int index = 0; index < count; index++) {
            output.append(glyph(x + index * advance, font, track, advance,
                    TextColor.color(0xFFFFFF), layout));
            if (index < active) {
                output.append(glyph(x + index * advance, font, fill, advance,
                        TextColor.color(0xFFFFFF), layout));
            }
        }
    }

    private static Component glyph(final int x, final Key font, final char value,
                                   final int width, final TextColor color,
                                   final SurvivalHudLayout layout) {
        final int anchoredX = layout.anchoredX(x);
        return Component.text().append(space(anchoredX))
                .append(Component.text(value).font(font).color(encode(color, layout.shaderCode())))
                .append(space(-anchoredX - width)).build();
    }

    private static Component centeredText(final int centerX, final Key font, final String raw,
                                          final TextColor color, final int maximumWidth,
                                          final SurvivalHudLayout layout) {
        final String value = sanitize(raw, Math.max(0, maximumWidth / TEXT_ADVANCE));
        final int width = value.codePointCount(0, value.length()) * TEXT_ADVANCE;
        final int anchoredX = layout.anchoredX(centerX - width / 2);
        return Component.text().append(space(anchoredX))
                .append(Component.text(value).font(font).color(encode(color, layout.shaderCode())))
                .append(space(-anchoredX - width)).build();
    }

    private static Component space(final int requested) {
        int remaining = requested;
        final TextComponent.Builder result = Component.text();
        while (remaining != 0) {
            final int step = Math.max(SPACE_MIN, Math.min(SPACE_MAX, remaining));
            result.append(Component.text(Character.toString(
                    SPACE_FIRST + step - SPACE_MIN)).font(SPACE_FONT));
            remaining -= step;
        }
        return result.build();
    }

    private static TextColor encode(final TextColor desired, final int code) {
        final int source = desired == null ? 0xFFFFFF : desired.value();
        return TextColor.color((source & 0xF00000) | ((code & 0xF) << 16)
                | (source & 0x00F000) | (((code >> 4) & 0xF) << 8)
                | (source & 0x0000E0) | (((code >> 12) & 0x1) << 4)
                | ((code >> 8) & 0xF));
    }

    private static String healthLine(final SurvivalHudState state) {
        final String shield = state.absorption() > 0.0D
                ? " (+" + compact(state.absorption()) + ")" : "";
        return compact(state.health()) + " / " + compact(state.maximumHealth()) + " HP" + shield;
    }

    private static String compact(final double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-3D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static char healthFill(final int percent) {
        if (percent <= 30) return HEALTH_CRITICAL;
        if (percent <= 60) return HEALTH_WARN;
        return HEALTH_FILL;
    }

    private static TextColor healthColor(final int percent) {
        if (percent <= 30) return TextColor.color(0xFF665C);
        if (percent <= 60) return TextColor.color(0xF2B45F);
        return TextColor.color(0x9AF2C2);
    }

    private static String sanitize(final String raw, final int maximumCharacters) {
        final String plain = (raw == null ? "" : raw).replace('\n', ' ')
                .replace('\r', ' ').trim();
        final StringBuilder result = new StringBuilder();
        plain.codePoints().limit(Math.max(0, maximumCharacters)).forEach(result::appendCodePoint);
        if (plain.codePointCount(0, plain.length()) > maximumCharacters && maximumCharacters > 0) {
            final int last = result.offsetByCodePoints(0,
                    Math.max(0, result.codePointCount(0, result.length()) - 1));
            result.replace(last, result.length(), "…");
        }
        return result.toString();
    }
}
