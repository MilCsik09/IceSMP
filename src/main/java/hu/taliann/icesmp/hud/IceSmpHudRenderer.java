package hu.taliann.icesmp.hud;

import hu.taliann.icesmp.classspec.integration.ClassHudSlot;
import hu.taliann.icesmp.classspec.integration.ClassHudMetric;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure first-party HUD compositor.
 *
 * <p>Every image/text draw returns to the same cursor origin. Resource values only choose how
 * many fixed cells are painted; they can never change the component's effective width.</p>
 */
public final class IceSmpHudRenderer {

    static final int SPACE_MIN = -512;
    static final int SPACE_MAX = 512;
    static final int SPACE_FIRST = 0xE400;
    static final int TEXT_ADVANCE = 9;
    static final int SEGMENTS = 12;

    private static final Key SPACE_FONT = Key.key("icesmp_hud", "space");
    private static final Key PANEL_FONT = Key.key("icesmp_hud", "panel");
    private static final Key WALLET_PANEL_FONT = Key.key("icesmp_hud", "wallet_panel");
    private static final Key DETAIL_PANEL_FONT = Key.key("icesmp_hud", "detail_panel");
    private static final Key CLASS_FONT = Key.key("icesmp_hud", "class_icon");
    private static final Key UTILITY_FONT = Key.key("icesmp_hud", "utility");
    private static final Key CURRENCY_FONT = Key.key("icesmp_hud", "currency");
    private static final Key RUNE_FONT = Key.key("icesmp_hud", "runes");
    private static final Key CHARGE_FONT = Key.key("icesmp_hud", "charges");
    private static final Key RESOURCE_FONT = Key.key("icesmp_hud", "resource_segments");
    private static final Key METRIC_FONT = Key.key("icesmp_hud", "metric_segments");
    private static final Key HEADER_FONT = Key.key("icesmp_hud", "text_header");
    private static final Key SUBHEADER_FONT = Key.key("icesmp_hud", "text_subheader");
    private static final Key RESOURCE_TEXT_FONT = Key.key("icesmp_hud", "text_resource");
    private static final Key MECHANIC_FONT = Key.key("icesmp_hud", "text_mechanic");
    private static final Key STATE_FONT = Key.key("icesmp_hud", "text_state");
    private static final Key EVENT_FONT = Key.key("icesmp_hud", "text_event");
    private static final Key WALLET_TEXT_FONT = Key.key("icesmp_hud", "text_wallet");
    private static final Key DETAIL_TEXT_FONT = Key.key("icesmp_hud", "text_detail");

    private static final List<String> THEMES = List.of("ice", "ember", "frost", "guild", "lich");
    private static final List<String> CLASSES = List.of("warrior", "evoker", "archer", "shaman", "monk",
            "paladin", "demon_hunter", "druid", "priest", "death_knight", "assassin", "warlock", "wizard");
    private static final Map<String, Integer> RUNE_KIND = Map.of("blood", 0, "frost", 1, "death", 2);
    private static final Map<String, Integer> RUNE_STATE = Map.of(
            "ready", 0, "spent", 1, "regenerating", 2, "locked", 3);

    private static final char SEGMENT_TRACK = '\uE180';
    private static final char SEGMENT_FILL = '\uE181';
    private static final char SEGMENT_WARM = '\uE182';
    private static final char SEGMENT_GOLD = '\uE183';

    public Component render(final IceSmpHudModel model) {
        final TextComponent.Builder output = Component.text();
        output.append(glyph(-274, PANEL_FONT, themeGlyph(model.factionTheme()), 261, null));
        output.append(glyph(-274, WALLET_PANEL_FONT, '\uE105', 261, null));
        output.append(glyph(-274, DETAIL_PANEL_FONT, '\uE106', 261, null));
        output.append(glyph(-256, CLASS_FONT, classGlyph(model.classHud().classId()), 37, null));
        output.append(glyph(-74, UTILITY_FONT, '\uE132', 16, null));
        output.append(glyph(-254, UTILITY_FONT, '\uE131', 16, null));

        final TextColor accent = color(model.factionAccent(), 0x77DDF2);
        final String spec = model.classHud().specName().isBlank() ? "" : " " + model.classHud().specName();
        output.append(text(-210, HEADER_FONT, model.className() + spec, accent, 25));
        output.append(text(-210, SUBHEADER_FONT, model.faction(), color("A9B7C6", 0xA9B7C6), 22));
        output.append(text(-52, HEADER_FONT, "Lv. " + model.classLevel(), color("EAF7FF", 0xEAF7FF), 8));
        drawCurrencies(output, model);
        if (model.hasClass()) {
            output.append(text(-222, RESOURCE_TEXT_FONT,
                    model.resourceName() + " " + model.resource() + "/" + model.resourceMax(),
                    color("C7D4EA", 0xC7D4EA), 25));
            drawSegments(output, -222, RESOURCE_FONT, model.resourcePercent(), resourceFill(model.factionTheme()));
            drawMechanics(output, model, accent);
            drawSupplementaryMetrics(output, model);
        }
        output.append(text(-234, EVENT_FONT, "ESEMÉNY " + stripLegacy(model.event()),
                color("F0D88D", 0xF0D88D), 24));
        return output.build();
    }

    private void drawMechanics(final TextComponent.Builder output, final IceSmpHudModel model,
                               final TextColor accent) {
        if ("death_knight".equals(model.classHud().classId())) {
            output.append(text(-254, MECHANIC_FONT, model.classHud().mechanicPrimary(), accent, 24));
            int index = 0;
            for (final ClassHudSlot slot : model.classHud().slots()) {
                if (index >= 8) break;
                output.append(glyph(-254 + index * 27, RUNE_FONT, runeGlyph(slot), 19, null));
                index++;
            }
            output.append(text(-254, STATE_FONT,
                    joinState(model.classHud().state(), model.classHud().proc()),
                    color("A9B7C6", 0xA9B7C6), 25));
            return;
        }

        output.append(text(-254, MECHANIC_FONT, model.classHud().mechanicPrimary(), accent, 22));
        output.append(text(-133, MECHANIC_FONT, model.classHud().mechanicSecondary(),
                color("A9B7C6", 0xA9B7C6), 16));
        final ClassHudMetric primary = model.classHud().metric(0);
        final ClassHudMetric secondary = model.classHud().metric(1);
        if (primary != null && primary.maximum() > 0.0D) {
            drawSegments(output, -254, METRIC_FONT, primary.percent(), SEGMENT_FILL);
        }
        if (secondary != null && secondary.maximum() > 0.0D) {
            drawSegments(output, -133, METRIC_FONT, secondary.percent(), SEGMENT_GOLD);
        }
        drawCharges(output, model.classHud().slots());
        output.append(text(-133, STATE_FONT, joinState(model.classHud().state(), model.classHud().proc()),
                color("A9B7C6", 0xA9B7C6), 16));
    }

    private static void drawCharges(final TextComponent.Builder output, final List<ClassHudSlot> slots) {
        int index = 0;
        for (final ClassHudSlot slot : slots) {
            if (index >= 9) break;
            final char glyph = "ready".equals(slot.state()) ? '\uE170' : '\uE171';
            output.append(glyph(-254 + index * 12, CHARGE_FONT, glyph, 11, null));
            index++;
        }
    }

    private static void drawSupplementaryMetrics(final TextComponent.Builder output,
                                                  final IceSmpHudModel model) {
        for (int index = 2; index < 5; index++) {
            final ClassHudMetric metric = model.classHud().metric(index);
            if (metric == null || metric.id().isBlank()) continue;
            final String value = metric.label().isBlank() ? metric.text()
                    : metric.label() + " " + metric.text();
            output.append(text(-254 + (index - 2) * 86, DETAIL_TEXT_FONT, value,
                    color("C7D4EA", 0xC7D4EA), 9));
        }
    }

    private static void drawCurrencies(final TextComponent.Builder output, final IceSmpHudModel model) {
        int index = 0;
        for (final hu.taliann.icesmp.managers.HudManager.HudCurrency currency : model.currencies()) {
            if (index >= 4) break;
            final int x = -254 + index * 59;
            output.append(glyph(x, CURRENCY_FONT, currencyGlyph(currency.id()), 16, null));
            output.append(text(x + 17, WALLET_TEXT_FONT, currency.amount(),
                    currency.primary() ? color("F0D88D", 0xF0D88D) : color("C7D4EA", 0xC7D4EA), 5));
            index++;
        }
    }

    private static void drawSegments(final TextComponent.Builder output, final int x, final Key font,
                                     final int percent, final char fill) {
        final int active = Math.max(0, Math.min(SEGMENTS,
                (int) Math.round(percent * SEGMENTS / 100.0D)));
        for (int index = 0; index < SEGMENTS; index++) {
            output.append(glyph(x + index * 13, font, SEGMENT_TRACK, 13, null));
            if (index < active) output.append(glyph(x + index * 13, font, fill, 13, null));
        }
    }

    private static Component glyph(final int x, final Key font, final char glyph,
                                   final int width, final TextColor color) {
        Component value = Component.text(glyph).font(font);
        if (color != null) value = value.color(color);
        return Component.text().append(space(x)).append(value).append(space(-x - width)).build();
    }

    private static Component text(final int x, final Key font, final String raw,
                                  final TextColor color, final int maximumCharacters) {
        final String value = sanitize(raw, maximumCharacters);
        if (value.isBlank()) return Component.empty();
        final int width = value.codePointCount(0, value.length()) * TEXT_ADVANCE;
        return Component.text().append(space(x)).append(Component.text(value).font(font).color(color))
                .append(space(-x - width)).build();
    }

    private static Component space(final int requested) {
        int remaining = requested;
        final TextComponent.Builder result = Component.text();
        while (remaining != 0) {
            final int step = Math.max(SPACE_MIN, Math.min(SPACE_MAX, remaining));
            final int codepoint = SPACE_FIRST + step - SPACE_MIN;
            result.append(Component.text(Character.toString(codepoint)).font(SPACE_FONT));
            remaining -= step;
        }
        return result.build();
    }

    private static String sanitize(final String value, final int maximumCharacters) {
        final String plain = stripLegacy(value == null ? "" : value).replace('\n', ' ').replace('\r', ' ').trim();
        final StringBuilder result = new StringBuilder();
        plain.codePoints().limit(Math.max(0, maximumCharacters)).forEach(codepoint ->
                result.appendCodePoint(supported(codepoint) ? codepoint : '?'));
        if (plain.codePointCount(0, plain.length()) > maximumCharacters && maximumCharacters > 0) {
            final int last = result.offsetByCodePoints(0, Math.max(0, result.codePointCount(0, result.length()) - 1));
            result.replace(last, result.length(), "…");
        }
        return result.toString();
    }

    private static boolean supported(final int codepoint) {
        return codepoint >= 32 && codepoint <= 126
                || "ÁÉÍÓÖŐÚÜŰáéíóöőúüű•—…".codePoints().anyMatch(value -> value == codepoint);
    }

    private static String stripLegacy(final String value) {
        return value.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }

    private static String joinState(final String state, final String proc) {
        if (state == null || state.isBlank()) return proc == null ? "" : proc;
        if (proc == null || proc.isBlank()) return state;
        return state + " • " + proc;
    }

    private static char themeGlyph(final String rawTheme) {
        final int index = THEMES.indexOf(rawTheme);
        return (char) (0xE100 + Math.max(0, index));
    }

    private static char classGlyph(final String classId) {
        final int index = CLASSES.indexOf(classId == null ? "" : classId.toLowerCase(Locale.ROOT));
        return (char) (0xE110 + (index < 0 ? CLASSES.size() : index));
    }

    private static char currencyGlyph(final String currencyId) {
        final int index = List.of("red", "blue", "neutral", "dark")
                .indexOf(currencyId == null ? "" : currencyId.toLowerCase(Locale.ROOT));
        return (char) (0xE160 + Math.max(0, index));
    }

    private static char runeGlyph(final ClassHudSlot slot) {
        final int kind = RUNE_KIND.getOrDefault(slot.kind(), 2);
        final int state = RUNE_STATE.getOrDefault(slot.state(), 1);
        return (char) (0xE140 + kind * 4 + state);
    }

    private static char resourceFill(final String theme) {
        return switch (theme == null ? "" : theme) {
            case "ember" -> SEGMENT_WARM;
            case "guild" -> SEGMENT_GOLD;
            default -> SEGMENT_FILL;
        };
    }

    private static TextColor color(final String hexadecimal, final int fallback) {
        try {
            return TextColor.color(Integer.parseInt(hexadecimal.replace("#", ""), 16));
        } catch (final NumberFormatException exception) {
            return TextColor.color(fallback);
        }
    }
}
