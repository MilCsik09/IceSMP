package hu.taliann.icesmp.ux;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Locale;
import java.util.Objects;

/**
 * Shared visual primitives for player-facing IceSMP tooltips.
 *
 * <p>The private-use glyphs are decorative only. Every icon is paired with readable text so the
 * tooltip remains usable if the resource pack or custom font is unavailable.</p>
 */
public final class TooltipPresentation {

    public static final Key FONT = Key.key("icesmp", "tooltip");

    public enum Glyph {
        TYPE('\uE100'),
        STATS('\uE101'),
        REQUIREMENTS('\uE102'),
        EFFECT('\uE103'),
        SOCKETS('\uE104'),
        EQUIPMENT('\uE105'),
        ASCENSION('\uE106'),
        ORIGIN('\uE107'),
        STORY('\uE108'),
        ARCHAEOLOGY('\uE109'),
        BLUEPRINT('\uE10A'),
        PROFESSION('\uE10B'),
        CURRENCY('\uE10C'),
        POUCH('\uE10D'),
        RELIC('\uE10E'),
        DEVELOPER('\uE10F'),
        QUEST('\uE110'),
        TOKEN('\uE111'),
        KEY('\uE112'),
        UPGRADE('\uE113'),
        UTILITY('\uE114'),
        COMPANION('\uE115'),
        SIEGE('\uE116'),
        CATALYST('\uE117');

        private final char character;

        Glyph(final char character) {
            this.character = character;
        }

        public char character() {
            return character;
        }
    }

    private TooltipPresentation() {
    }

    public static Component icon(final Glyph glyph) {
        Objects.requireNonNull(glyph, "glyph");
        /*
         * The first live acceptance pass proved that client/resource-pack font failures turn the
         * private-use code points into tofu squares. Keep semantic icons decorative, but render
         * them through glyphs that the vanilla client font already knows. The private font stays
         * available for a later art pass without being required for readable production tooltips.
         */
        final String symbol = switch (glyph) {
            case TYPE -> "◆";
            case STATS -> "✦";
            case REQUIREMENTS -> "◇";
            case EFFECT -> "✦";
            case SOCKETS -> "◇";
            case EQUIPMENT -> "◆";
            case ASCENSION -> "↑";
            case ORIGIN -> "•";
            case STORY -> "◆";
            case ARCHAEOLOGY -> "◇";
            case BLUEPRINT -> "◆";
            case PROFESSION -> "◆";
            case CURRENCY -> "◆";
            case POUCH -> "◆";
            case RELIC -> "◆";
            case DEVELOPER -> "◆";
            case QUEST -> "◆";
            case TOKEN -> "◆";
            case KEY -> "◆";
            case UPGRADE -> "◆";
            case UTILITY -> "◆";
            case COMPANION -> "◆";
            case SIEGE -> "◆";
            case CATALYST -> "◆";
        };
        return Component.text(symbol, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component withIcon(final Glyph glyph, final Component content) {
        Objects.requireNonNull(content, "content");
        return Component.empty()
                .append(icon(glyph))
                .append(Component.text(" "))
                .append(content);
    }

    public static Component sectionHeading(final Glyph glyph, final String label,
                                           final TextColor color) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(color, "color");
        return withIcon(glyph, Component.text(label.toUpperCase(Locale.ROOT), color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
    }

    public static Component classification(final String label, final String detail,
                                           final TextColor color) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(color, "color");
        Component result = Component.text(label.toUpperCase(Locale.ROOT), color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
        if (detail != null && !detail.isBlank()) {
            result = result
                    .append(Component.text("  •  ", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(detail, NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
        }
        return result;
    }

    public static Component line(final String text, final TextColor color) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(color, "color");
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
