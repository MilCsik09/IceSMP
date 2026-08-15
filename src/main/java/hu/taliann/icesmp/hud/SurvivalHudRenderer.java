package hu.taliann.icesmp.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Locale;

/** Pure top-left, faction-themed player frame and shared frame rendering primitives. */
public final class SurvivalHudRenderer {

    static final int HEALTH_SEGMENTS = 20;
    static final int MINI_SEGMENTS = 10;
    static final int HEALTH_SEGMENT_ADVANCE = 11;
    static final int MINI_SEGMENT_ADVANCE = 6;
    static final int PANEL_WIDTH = 252;
    static final int TEXT_ADVANCE = 7;

    static final Key SPACE_FONT = Key.key("icesmp_hud", "space");
    static final Key PANEL_FONT = Key.key("icesmp_hud", "survival/panel");
    static final Key HEALTH_SEGMENT_FONT = Key.key("icesmp_hud", "survival/health_segments");
    static final Key MINI_SEGMENT_FONT = Key.key("icesmp_hud", "survival/mini_segments");
    static final Key ICON_FONT = Key.key("icesmp_hud", "survival/icons");
    static final Key HEADER_FONT = Key.key("icesmp_hud", "survival/text_header");
    static final Key NAME_FONT = Key.key("icesmp_hud", "survival/player_name");
    static final Key PERCENT_FONT = Key.key("icesmp_hud", "survival/text_percent");
    static final Key STATS_FONT = Key.key("icesmp_hud", "survival/text_stats");

    static final char HEALTH_TRACK = '\uEB10';
    static final char HEALTH_FILL = '\uEB11';
    static final char HEALTH_WARN = '\uEB12';
    static final char HEALTH_CRITICAL = '\uEB13';
    static final char MINI_TRACK = '\uEB20';
    static final char MINI_ARMOR = '\uEB21';
    static final char MINI_FOOD = '\uEB22';
    static final char MINI_AIR = '\uEB23';
    static final char MINI_RESOURCE = '\uEB24';
    static final char MINI_HEALTH = '\uEB25';
    static final char MINI_HEALTH_WARN = '\uEB26';
    static final char MINI_HEALTH_CRITICAL = '\uEB27';
    static final char ICON_ARMOR = '\uEB30';
    static final char ICON_FOOD = '\uEB31';
    static final char ICON_AIR = '\uEB32';
    static final char ICON_PLAYER = '\uEB33';
    static final char ICON_PASSIVE = '\uEB34';
    static final char ICON_NEUTRAL = '\uEB35';
    static final char ICON_HOSTILE = '\uEB36';
    static final char ICON_BOSS = '\uEB37';
    static final TextColor EDITOR_HIGHLIGHT = TextColor.color(0xFFD740);

    private static final List<String> THEMES = List.of("ice", "ember", "frost", "guild", "lich");

    public Component render(final PlayerHudState state, final HudLayoutSnapshot layout,
                            final HudComponent highlighted) {
        final PlayerHudState safeState = state == null ? PlayerHudState.preview() : state;
        final SurvivalHudState survival = safeState.survival();
        final HudLayoutSnapshot safeLayout = layout == null ? HudLayoutSnapshot.defaults() : layout;
        if (!safeLayout.componentLayout(HudComponent.PLAYER_GROUP).visible()) return Component.empty();
        final TextComponent.Builder output = Component.text().shadowColor(ShadowColor.none());

        output.append(glyph(HudComponent.PLAYER_FRAME, 0, PANEL_FONT,
                playerPanelGlyph(safeState.factionTheme()), PANEL_WIDTH,
                accent(safeState.factionAccent(), 0x8BE9FD), safeLayout, 0, highlighted));
        output.append(text(HudComponent.PLAYER_NAME, 14, NAME_FONT, safeState.name(),
                accent(safeState.factionAccent(), 0x8BE9FD), 150,
                safeLayout, 0, highlighted));
        drawSegments(output, HudComponent.PLAYER_HEALTH_BAR, 16, HEALTH_SEGMENT_FONT,
                HEALTH_TRACK, healthFill(survival.healthPercent()), survival.healthPercent(),
                HEALTH_SEGMENTS, HEALTH_SEGMENT_ADVANCE, safeLayout, 0, highlighted);
        output.append(centeredText(HudComponent.PLAYER_HEALTH_TEXT, 126, HEADER_FONT,
                healthLine(survival), healthColor(survival.healthPercent()), 210,
                safeLayout, 0, highlighted));
        final boolean absorptionVisible = survival.absorption() > 0.0D;
        output.append(centeredText(HudComponent.PLAYER_HEALTH_PERCENT,
                absorptionVisible ? 92 : 126, PERCENT_FONT,
                survival.healthPercent() + "%", TextColor.color(0xF7FBFF), 48,
                safeLayout, 0, highlighted));
        if (absorptionVisible) {
            output.append(centeredText(HudComponent.PLAYER_ABSORPTION, 184, PERCENT_FONT,
                    "+" + compact(survival.absorption()) + " pajzs",
                    TextColor.color(0xF0D36F), 92, safeLayout, 0, highlighted));
        }
        drawStat(output, HudComponent.PLAYER_ARMOR, 51, ICON_ARMOR, MINI_ARMOR,
                survival.armorPercent(), compact(survival.armor()) + "/"
                        + compact(survival.maximumArmor()), TextColor.color(0xCAD8E8),
                safeLayout, highlighted);
        drawStat(output, HudComponent.PLAYER_FOOD, 127, ICON_FOOD, MINI_FOOD,
                survival.foodPercent(), survival.food() + "/" + survival.maximumFood(),
                TextColor.color(0xF0C878), safeLayout, highlighted);
        if (airVisible(survival)) {
            drawStat(output, HudComponent.PLAYER_OXYGEN, 203, ICON_AIR, MINI_AIR,
                    survival.airPercent(), survival.air() + "/" + survival.maximumAir(),
                    TextColor.color(0x8DE8F4), safeLayout, highlighted);
        }
        return output.build();
    }

    public Component render(final SurvivalHudState state, final HudLayoutSnapshot layout) {
        return render(new PlayerHudState("Játékos", "ice", "8BE9FD", state), layout, null);
    }

    /** Vanilla-font emergency text remains readable if a custom player-frame glyph fails. */
    public Component fallback(final PlayerHudState state) {
        final SurvivalHudState survival = state == null ? PlayerHudState.preview().survival() : state.survival();
        final String air = airVisible(survival)
                ? " • O2 " + survival.air() + "/" + survival.maximumAir() : "";
        return Component.text(healthLine(survival) + " • " + survival.healthPercent() + "%"
                        + " • Páncél " + compact(survival.armor())
                        + " • Étel " + survival.food() + "/" + survival.maximumFood() + air,
                healthColor(survival.healthPercent())).shadowColor(ShadowColor.none());
    }

    private static void drawStat(final TextComponent.Builder output, final HudComponent component,
                                 final int center, final char icon, final char fill,
                                 final int percent, final String value, final TextColor color,
                                 final HudLayoutSnapshot layout, final HudComponent highlighted) {
        drawSegments(output, component, center - 22, MINI_SEGMENT_FONT,
                MINI_TRACK, fill, percent, MINI_SEGMENTS, MINI_SEGMENT_ADVANCE,
                layout, 0, highlighted);
        output.append(glyph(component, center - 38, ICON_FONT, icon, 14,
                TextColor.color(0xFFFFFF), layout, 0, highlighted));
        output.append(centeredText(component, center + 8, STATS_FONT,
                value, color, 68, layout, 0, highlighted));
    }

    static boolean airVisible(final SurvivalHudState state) {
        return state != null && state.air() < state.maximumAir();
    }

    static void drawSegments(final TextComponent.Builder output, final HudComponent component,
                             final int x, final Key font, final char track, final char fill,
                             final int percent, final int count, final int advance,
                             final HudLayoutSnapshot layout, final int additionalY,
                             final HudComponent highlighted) {
        if (!layout.visible(component)) return;
        int active = (int) Math.round(Math.max(0, Math.min(100, percent)) * count / 100.0D);
        if (percent > 0) active = Math.max(1, active);
        for (int index = 0; index < count; index++) {
            output.append(glyph(component, x + index * advance, font, track, advance,
                    TextColor.color(0xFFFFFF), layout, additionalY, highlighted));
            if (index < active) {
                output.append(glyph(component, x + index * advance, font, fill, advance,
                        TextColor.color(0xFFFFFF), layout, additionalY, highlighted));
            }
        }
    }

    static Component glyph(final HudComponent component, final int x, final Key font,
                           final char value, final int width, final TextColor color,
                           final HudLayoutSnapshot layout, final int additionalY,
                           final HudComponent highlighted) {
        if (!layout.visible(component)) return Component.empty();
        final int anchoredX = layout.anchoredX(component, x);
        final TextColor effective = isHighlighted(component, highlighted)
                ? EDITOR_HIGHLIGHT : color;
        return Component.text().append(space(anchoredX))
                .append(Component.text(value).font(font)
                        .color(encode(effective, layout.shaderCode(component, additionalY))))
                .append(space(-anchoredX - width)).build();
    }

    static Component text(final HudComponent component, final int x, final Key font,
                          final String raw, final TextColor color, final int maximumWidth,
                          final HudLayoutSnapshot layout, final int additionalY,
                          final HudComponent highlighted) {
        if (!layout.visible(component)) return Component.empty();
        final String value = sanitize(raw, Math.max(0, maximumWidth / TEXT_ADVANCE));
        final int width = value.codePointCount(0, value.length()) * TEXT_ADVANCE;
        final int anchoredX = layout.anchoredX(component, x);
        final TextColor effective = isHighlighted(component, highlighted)
                ? EDITOR_HIGHLIGHT : color;
        return Component.text().append(space(anchoredX))
                .append(Component.text(value).font(font)
                        .color(encode(effective, layout.shaderCode(component, additionalY))))
                .append(space(-anchoredX - width)).build();
    }

    static Component centeredText(final HudComponent component, final int centerX,
                                  final Key font, final String raw, final TextColor color,
                                  final int maximumWidth, final HudLayoutSnapshot layout,
                                  final int additionalY, final HudComponent highlighted) {
        final String value = sanitize(raw, Math.max(0, maximumWidth / TEXT_ADVANCE));
        final int width = value.codePointCount(0, value.length()) * TEXT_ADVANCE;
        return text(component, centerX - width / 2, font, value, color,
                maximumWidth, layout, additionalY, highlighted);
    }

    static Component space(final int requested) {
        int remaining = requested;
        final TextComponent.Builder result = Component.text();
        while (remaining != 0) {
            final int step = Math.max(IceSmpHudRenderer.SPACE_MIN,
                    Math.min(IceSmpHudRenderer.SPACE_MAX, remaining));
            result.append(Component.text(Character.toString(
                    IceSmpHudRenderer.SPACE_FIRST + step - IceSmpHudRenderer.SPACE_MIN))
                    .font(SPACE_FONT));
            remaining -= step;
        }
        return result.build();
    }

    static TextColor encode(final TextColor desired, final int code) {
        final int source = desired == null ? 0xFFFFFF : desired.value();
        return TextColor.color((source & 0xF00000) | ((code & 0xF) << 16)
                | (source & 0x00F000) | (((code >> 4) & 0xF) << 8)
                | (source & 0x0000E0) | (((code >> 12) & 0x1) << 4)
                | ((code >> 8) & 0xF));
    }

    static String healthLine(final SurvivalHudState state) {
        return compact(state.health()) + " / " + compact(state.maximumHealth()) + " HP";
    }

    static String compact(final double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-3D) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.1f", value);
    }

    static char healthFill(final int percent) {
        if (percent <= 30) return HEALTH_CRITICAL;
        if (percent <= 60) return HEALTH_WARN;
        return HEALTH_FILL;
    }

    static char miniHealthFill(final int percent) {
        if (percent <= 30) return MINI_HEALTH_CRITICAL;
        if (percent <= 60) return MINI_HEALTH_WARN;
        return MINI_HEALTH;
    }

    static TextColor healthColor(final int percent) {
        if (percent <= 30) return TextColor.color(0xFF665C);
        if (percent <= 60) return TextColor.color(0xF2B45F);
        return TextColor.color(0x9AF2C2);
    }

    static TextColor accent(final String hexadecimal, final int fallback) {
        try {
            return TextColor.color(Integer.parseInt(hexadecimal.replace("#", ""), 16));
        } catch (final RuntimeException ignored) {
            return TextColor.color(fallback);
        }
    }

    static boolean isHighlighted(final HudComponent component, final HudComponent highlighted) {
        return highlighted == HudComponent.GLOBAL || highlighted == component
                || highlighted != null && highlighted == component.parentGroup();
    }

    private static char playerPanelGlyph(final String theme) {
        final int index = THEMES.indexOf(theme);
        return (char) (0xEB00 + Math.max(0, index));
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
