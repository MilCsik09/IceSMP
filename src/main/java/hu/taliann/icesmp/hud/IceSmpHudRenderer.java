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
    static final int TEXT_ADVANCE = 7;
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
    private static final Key MECHANIC_ICON_FONT = Key.key("icesmp_hud", "mechanic_icons");
    private static final Key MECHANIC_SLOT_FONT = Key.key("icesmp_hud", "mechanic_slots");
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
    private static final List<String> MECHANICS = List.of(
            "warrior:battle_tempo", "evoker:empower", "archer:wind_read", "shaman:totem_wheel",
            "monk:flow", "paladin:conviction", "demon_hunter:load", "druid:harmony",
            "priest:litany", "death_knight:rune_wheel", "assassin:opening", "warlock:soul_debt",
            "wizard:runewaving", "warrior:blood_frenzy", "warrior:guard", "evoker:resonance",
            "evoker:imprint", "archer:precision_chain", "archer:bond", "shaman:resonance",
            "shaman:maelstrom", "shaman:tide", "monk:combo_chain", "monk:stagger",
            "monk:mist_threads", "paladin:beacon", "paladin:judgement_marks",
            "paladin:shield_charge", "demon_hunter:fragments", "demon_hunter:pain",
            "demon_hunter:sigil", "druid:combo", "druid:balance", "druid:bark", "druid:seeds",
            "priest:shield_web", "priest:marrow", "priest:madness", "death_knight:blood_memory",
            "death_knight:frost_marks", "death_knight:plague", "assassin:toxin",
            "assassin:detection", "assassin:infection", "warlock:curses", "warlock:embers",
            "warlock:demons", "wizard:attunement", "wizard:court");

    private static final char SEGMENT_TRACK = '\uE180';
    private static final char SEGMENT_FILL = '\uE181';
    private static final char SEGMENT_WARM = '\uE182';
    private static final char SEGMENT_GOLD = '\uE183';

    public Component render(final IceSmpHudModel model) {
        return render(model, HudLayoutSnapshot.defaults());
    }

    public Component render(final IceSmpHudModel model, final HudLayoutSnapshot layout) {
        final HudLayoutSnapshot safeLayout = layout == null ? HudLayoutSnapshot.defaults() : layout;
        final TextComponent.Builder output = Component.text();
        output.append(glyph(HudComponent.FRAME, -254, PANEL_FONT,
                themeGlyph(model.factionTheme()), 241, null, safeLayout));
        output.append(glyph(HudComponent.WALLET_FRAME, -254, WALLET_PANEL_FONT,
                '\uE105', 241, null, safeLayout));
        output.append(glyph(HudComponent.DETAIL_FRAME, -254, DETAIL_PANEL_FONT,
                '\uE106', 241, null, safeLayout));
        output.append(glyph(HudComponent.CLASS_ICON, -236, CLASS_FONT,
                classGlyph(model.classHud().classId()), 37, null, safeLayout));
        output.append(glyph(HudComponent.LEVEL_ICON, -88, UTILITY_FONT,
                '\uE132', 16, null, safeLayout));
        output.append(glyph(HudComponent.EVENT_ICON, -234, UTILITY_FONT,
                '\uE131', 16, null, safeLayout));

        final TextColor accent = color(model.factionAccent(), 0x77DDF2);
        final String spec = model.classHud().specName().isBlank() ? "" : " " + model.classHud().specName();
        output.append(text(HudComponent.CLASS_NAME, -190, HEADER_FONT,
                model.className() + spec, accent, 25, safeLayout));
        output.append(text(HudComponent.FACTION, -190, SUBHEADER_FONT, model.faction(),
                color("A9B7C6", 0xA9B7C6), 22, safeLayout));
        output.append(text(HudComponent.LEVEL_TEXT, -70, HEADER_FONT, "Lv. " + model.classLevel(),
                color("EAF7FF", 0xEAF7FF), 8, safeLayout));
        drawCurrencies(output, model, safeLayout);
        if (model.hasClass()) {
            output.append(text(HudComponent.RESOURCE_LABEL, -202, RESOURCE_TEXT_FONT,
                    model.resourceName() + " " + model.resource() + "/" + model.resourceMax(),
                    color("C7D4EA", 0xC7D4EA), 25, safeLayout));
            drawSegments(output, HudComponent.RESOURCE_BAR, -202, RESOURCE_FONT,
                    model.resourcePercent(), resourceFill(model.factionTheme()), safeLayout);
            drawMechanics(output, model, accent, safeLayout);
            drawSupplementaryMetrics(output, model, safeLayout);
        }
        output.append(text(HudComponent.EVENT_TEXT, -214, EVENT_FONT,
                "ESEMÉNY " + stripLegacy(model.event()),
                color("F0D88D", 0xF0D88D), 24, safeLayout));
        return output.build();
    }

    private void drawMechanics(final TextComponent.Builder output, final IceSmpHudModel model,
                               final TextColor accent, final HudLayoutSnapshot layout) {
        if ("death_knight".equals(model.classHud().classId())) {
            drawMetricIcon(output, HudComponent.PRIMARY_MECHANIC, model.classHud().classId(),
                    model.classHud().metric(0), -234, layout);
            output.append(text(HudComponent.PRIMARY_MECHANIC, -217, MECHANIC_FONT,
                    model.classHud().mechanicPrimary(), accent, 22, layout));
            int index = 0;
            for (final ClassHudSlot slot : model.classHud().slots()) {
                if (index >= 8) break;
                output.append(glyph(HudComponent.CHARGES, -234 + index * 27, RUNE_FONT,
                        runeGlyph(slot), 19, null, layout));
                index++;
            }
            output.append(text(HudComponent.STATE_PROC, -234, STATE_FONT,
                    joinState(model.classHud().state(), model.classHud().proc()),
                    color("A9B7C6", 0xA9B7C6), 25, layout));
            return;
        }

        final ClassHudMetric primary = model.classHud().metric(0);
        final ClassHudMetric secondary = model.classHud().metric(1);
        drawMetricIcon(output, HudComponent.PRIMARY_MECHANIC, model.classHud().classId(),
                primary, -234, layout);
        drawMetricIcon(output, HudComponent.SECONDARY_MECHANIC, model.classHud().classId(),
                secondary, -113, layout);
        output.append(text(HudComponent.PRIMARY_MECHANIC, -217, MECHANIC_FONT,
                model.classHud().mechanicPrimary(), accent, 20, layout));
        final boolean specializationMissing = model.classHud().specName().isBlank();
        final String secondaryText = model.classHud().mechanicSecondary().isBlank()
                && specializationMissing ? "Spec: nincs" : model.classHud().mechanicSecondary();
        output.append(text(HudComponent.SECONDARY_MECHANIC, -96, MECHANIC_FONT, secondaryText,
                color("A9B7C6", 0xA9B7C6), 14, layout));
        if (primary != null && primary.maximum() > 0.0D) {
            drawSegments(output, HudComponent.PRIMARY_MECHANIC, -234, METRIC_FONT,
                    primary.percent(), SEGMENT_FILL, layout);
        }
        if (secondary != null && secondary.maximum() > 0.0D) {
            drawSegments(output, HudComponent.SECONDARY_MECHANIC, -113, METRIC_FONT,
                    secondary.percent(), SEGMENT_GOLD, layout);
        }
        drawCharges(output, model.classHud().classId(), model.classHud().slots(), layout);
        final String stateText = joinState(model.classHud().state(), model.classHud().proc());
        output.append(text(HudComponent.STATE_PROC, -113, STATE_FONT,
                stateText.isBlank() && specializationMissing
                        ? "Válassz profilt" : stateText,
                color("A9B7C6", 0xA9B7C6), 16, layout));
    }

    private static void drawCharges(final TextComponent.Builder output, final String classId,
                                    final List<ClassHudSlot> slots, final HudLayoutSnapshot layout) {
        int index = 0;
        for (final ClassHudSlot slot : slots) {
            if (index >= 9) break;
            final Character mechanic = mechanicGlyph(classId, slotKind(classId, slot.kind()),
                    "ready".equals(slot.state()) ? 1 : 3);
            if (mechanic == null) {
                final char fallback = "ready".equals(slot.state()) ? '\uE170' : '\uE171';
                output.append(glyph(HudComponent.CHARGES, -234 + index * 12, CHARGE_FONT,
                        fallback, 11, null, layout));
            } else {
                output.append(glyph(HudComponent.CHARGES, -234 + index * 12,
                        MECHANIC_SLOT_FONT, mechanic, 11, null, layout));
            }
            index++;
        }
    }

    private static void drawMetricIcon(final TextComponent.Builder output,
                                       final HudComponent component, final String classId,
                                       final ClassHudMetric metric, final int x,
                                       final HudLayoutSnapshot layout) {
        if (metric == null || metric.id().isBlank()) return;
        final Character glyph = mechanicGlyph(classId, metric.id(), metricVariant(metric.state()));
        if (glyph != null) output.append(glyph(component, x, MECHANIC_ICON_FONT,
                glyph, 15, null, layout));
    }

    private static int metricVariant(final String rawState) {
        return switch (visualState(rawState)) {
            case "ready" -> 1;
            case "alert" -> 2;
            case "spent" -> 3;
            default -> 0;
        };
    }

    /** Shared first-party/native-fallback state normalization for the four reviewed icon variants. */
    public static String visualState(final String rawState) {
        final String state = rawState == null ? "" : rawState.toLowerCase(Locale.ROOT);
        if (List.of("ready", "full", "recited", "crowned", "ripe", "stored", "atonement",
                "hidden", "high_tide", "roots", "scent").contains(state)) return "ready";
        if (List.of("alert", "overheated", "overloaded", "tulterhelt", "beyond", "capped",
                "exposed", "locked", "low_tide", "aftermath").contains(state)) return "alert";
        if (List.of("spent", "empty", "idle").contains(state)) return "spent";
        return "active";
    }

    private static Character mechanicGlyph(final String classId, final String mechanicId,
                                            final int variant) {
        final int index = MECHANICS.indexOf((classId == null ? "" : classId) + ":"
                + (mechanicId == null ? "" : mechanicId));
        return index < 0 ? null : (char) (0xE200 + index * 4 + Math.max(0, Math.min(3, variant)));
    }

    private static String slotKind(final String classId, final String kind) {
        if ("priest".equals(classId) && "ossuary".equals(kind)) return "marrow";
        return kind;
    }

    private static void drawSupplementaryMetrics(final TextComponent.Builder output,
                                                  final IceSmpHudModel model,
                                                  final HudLayoutSnapshot layout) {
        for (int index = 2; index < 5; index++) {
            final ClassHudMetric metric = model.classHud().metric(index);
            if (metric == null || metric.id().isBlank()) continue;
            final String value = metric.label().isBlank() ? metric.text()
                    : metric.label() + " " + metric.text();
            output.append(text(HudComponent.DETAIL_METRICS, -234 + (index - 2) * 80,
                    DETAIL_TEXT_FONT, value,
                    color("C7D4EA", 0xC7D4EA), 9, layout));
        }
    }

    private static void drawCurrencies(final TextComponent.Builder output, final IceSmpHudModel model,
                                       final HudLayoutSnapshot layout) {
        int index = 0;
        for (final hu.taliann.icesmp.managers.HudManager.HudCurrency currency : model.currencies()) {
            if (index >= 4) break;
            final int x = -234 + index * 54;
            output.append(glyph(HudComponent.WALLET, x, CURRENCY_FONT,
                    currencyGlyph(currency.id()), 16, null, layout));
            output.append(text(HudComponent.WALLET, x + 17, WALLET_TEXT_FONT, currency.amount(),
                    currency.primary() ? color("F0D88D", 0xF0D88D) : color("C7D4EA", 0xC7D4EA), 5,
                    layout));
            index++;
        }
    }

    private static void drawSegments(final TextComponent.Builder output, final HudComponent component,
                                     final int x, final Key font,
                                     final int percent, final char fill,
                                     final HudLayoutSnapshot layout) {
        final int active = Math.max(0, Math.min(SEGMENTS,
                (int) Math.round(percent * SEGMENTS / 100.0D)));
        for (int index = 0; index < SEGMENTS; index++) {
            output.append(glyph(component, x + index * 13, font,
                    SEGMENT_TRACK, 13, null, layout));
            if (index < active) output.append(glyph(component, x + index * 13, font,
                    fill, 13, null, layout));
        }
    }

    private static Component glyph(final HudComponent component, final int x,
                                   final Key font, final char glyph,
                                   final int width, final TextColor color,
                                   final HudLayoutSnapshot layout) {
        if (!layout.visible(component)) return Component.empty();
        final int anchoredX = layout.anchoredX(component, x);
        final TextColor encoded = encodeLayoutColor(
                color == null ? TextColor.color(0xFFFFFF) : color, layout, component);
        final Component value = Component.text(glyph).font(font).color(encoded);
        return Component.text().append(space(anchoredX)).append(value)
                .append(space(-anchoredX - width)).build();
    }

    private static Component text(final HudComponent component, final int x,
                                  final Key font, final String raw,
                                  final TextColor color, final int maximumCharacters,
                                  final HudLayoutSnapshot layout) {
        if (!layout.visible(component)) return Component.empty();
        final String value = sanitize(raw, maximumCharacters);
        if (value.isBlank()) return Component.empty();
        final int width = value.codePointCount(0, value.length()) * TEXT_ADVANCE;
        final int anchoredX = layout.anchoredX(component, x);
        return Component.text().append(space(anchoredX)).append(Component.text(value).font(font)
                        .color(encodeLayoutColor(color, layout, component)))
                .append(space(-anchoredX - width)).build();
    }

    static TextColor encodeLayoutColor(final TextColor desired, final HudLayoutSnapshot layout) {
        return encodeLayoutColor(desired, layout, HudComponent.GLOBAL);
    }

    static TextColor encodeLayoutColor(final TextColor desired, final HudLayoutSnapshot layout,
                                       final HudComponent component) {
        final int source = desired == null ? 0xFFFFFF : desired.value();
        final int code = component == null || component == HudComponent.GLOBAL
                ? layout.shaderCode() : layout.shaderCode(component);
        return TextColor.color((source & 0xF00000) | ((code & 0xF) << 16)
                | (source & 0x00F000) | (((code >> 4) & 0xF) << 8)
                | (source & 0x0000F0) | ((code >> 8) & 0xF));
    }

    static int decodeLayoutCode(final TextColor encoded) {
        final int value = encoded.value();
        return ((value >> 16) & 0xF) | (((value >> 8) & 0xF) << 4) | ((value & 0xF) << 8);
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
