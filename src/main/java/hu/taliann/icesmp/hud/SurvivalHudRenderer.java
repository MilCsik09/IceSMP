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
    static final int HEALTH_SEGMENT_ADVANCE = 9;
    static final int MINI_SEGMENT_ADVANCE = 5;
    static final int PANEL_WIDTH = 228;
    static final int TEXT_ADVANCE = 6;

    private static final int SPACE_MIN = IceSmpHudRenderer.SPACE_MIN;
    private static final int SPACE_MAX = IceSmpHudRenderer.SPACE_MAX;
    private static final int SPACE_FIRST = IceSmpHudRenderer.SPACE_FIRST;
    private static final int HEALTH_BAR_X = -90;
    private static final int[] MINI_BAR_X = {-91, -28, 35};
    private static final int[] MINI_ICON_X = {-107, -44, 19};

    private static final Key SPACE_FONT = Key.key("icesmp_hud", "space");
    private static final Key PANEL_FONT = Key.key("icesmp_hud", "survival/panel");
    private static final Key HEALTH_SEGMENT_FONT = Key.key("icesmp_hud", "survival/health_segments");
    private static final Key MINI_SEGMENT_FONT = Key.key("icesmp_hud", "survival/mini_segments");
    private static final Key ICON_FONT = Key.key("icesmp_hud", "survival/icons");
    private static final Key HEADER_FONT = Key.key("icesmp_hud", "survival/text_header");
    private static final Key PERCENT_FONT = Key.key("icesmp_hud", "survival/text_percent");
    private static final Key STATS_FONT = Key.key("icesmp_hud", "survival/text_stats");

    private static final char PANEL = '\uEB00';
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

    public Component render(final SurvivalHudState state, final SurvivalHudLayout layout) {
        final SurvivalHudLayout safeLayout = layout == null ? SurvivalHudLayout.defaults() : layout;
        final TextComponent.Builder output = Component.text().shadowColor(ShadowColor.none());
        output.append(glyph(-PANEL_WIDTH / 2, PANEL_FONT, PANEL, PANEL_WIDTH,
                TextColor.color(0xFFFFFF), safeLayout));

        drawSegments(output, HEALTH_BAR_X, HEALTH_SEGMENT_FONT,
                HEALTH_TRACK, healthFill(state.healthPercent()), state.healthPercent(),
                HEALTH_SEGMENTS, HEALTH_SEGMENT_ADVANCE, safeLayout);
        drawSegments(output, MINI_BAR_X[0], MINI_SEGMENT_FONT,
                MINI_TRACK, MINI_ARMOR, state.armorPercent(),
                MINI_SEGMENTS, MINI_SEGMENT_ADVANCE, safeLayout);
        drawSegments(output, MINI_BAR_X[1], MINI_SEGMENT_FONT,
                MINI_TRACK, MINI_FOOD, state.foodPercent(),
                MINI_SEGMENTS, MINI_SEGMENT_ADVANCE, safeLayout);
        drawSegments(output, MINI_BAR_X[2], MINI_SEGMENT_FONT,
                MINI_TRACK, MINI_AIR, state.airPercent(),
                MINI_SEGMENTS, MINI_SEGMENT_ADVANCE, safeLayout);

        output.append(glyph(MINI_ICON_X[0], ICON_FONT, ICON_ARMOR, 12,
                TextColor.color(0xFFFFFF), safeLayout));
        output.append(glyph(MINI_ICON_X[1], ICON_FONT, ICON_FOOD, 12,
                TextColor.color(0xFFFFFF), safeLayout));
        output.append(glyph(MINI_ICON_X[2], ICON_FONT, ICON_AIR, 12,
                TextColor.color(0xFFFFFF), safeLayout));

        output.append(centeredText(0, HEADER_FONT, healthLine(state),
                healthColor(state.healthPercent()), 198, safeLayout));
        output.append(centeredText(0, PERCENT_FONT, state.healthPercent() + "%",
                TextColor.color(0xF7FBFF), 48, safeLayout));
        output.append(centeredText(MINI_BAR_X[0] + 25, STATS_FONT,
                compact(state.armor()) + "/" + compact(state.maximumArmor()),
                TextColor.color(0xCAD8E8), 54, safeLayout));
        output.append(centeredText(MINI_BAR_X[1] + 25, STATS_FONT,
                state.food() + "/" + state.maximumFood(),
                TextColor.color(0xF0C878), 54, safeLayout));
        output.append(centeredText(MINI_BAR_X[2] + 25, STATS_FONT,
                state.air() + "/" + state.maximumAir(),
                TextColor.color(0x8DE8F4), 54, safeLayout));
        return output.build();
    }

    /** Vanilla-font emergency text remains readable even if a custom glyph fails to compose. */
    public Component fallback(final SurvivalHudState state) {
        return Component.text(healthLine(state) + " • " + state.healthPercent() + "%"
                        + " • Páncél " + compact(state.armor())
                        + " • Étel " + state.food() + "/" + state.maximumFood()
                        + " • O2 " + state.air() + "/" + state.maximumAir(),
                healthColor(state.healthPercent())).shadowColor(ShadowColor.none());
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
