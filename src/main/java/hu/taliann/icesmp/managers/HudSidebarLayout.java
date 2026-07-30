package hu.taliann.icesmp.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dependency-free parser and row-budget helper for the native sidebar layout.
 *
 * <p>The live renderer stays in {@link HudManager}; this class only owns the editable layout
 * contract, token substitution and deterministic 15-row fitting so those rules can be covered by
 * lightweight regression tests without constructing Bukkit players or scoreboards.
 */
final class HudSidebarLayout {

    enum Type {
        TEXT,
        SPACER,
        SEPARATOR,
        TARGET,
        RESOURCE,
        INFO,
        PARTY;

        static Type parse(final Object raw) {
            if (raw == null) {
                return TEXT;
            }
            final String normalized = raw.toString().trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_');
            if ("BLANK".equals(normalized)) {
                return SPACER;
            }
            if ("ROTATING".equals(normalized)) {
                return INFO;
            }
            try {
                return Type.valueOf(normalized);
            } catch (final IllegalArgumentException ignored) {
                return TEXT;
            }
        }
    }

    record Entry(Type type, String section, String text) {
        Entry {
            type = type == null ? Type.TEXT : type;
            section = normalizeSection(section);
            if (section.isEmpty()) {
                section = defaultSection(type);
            }
            text = text == null ? "" : text;
        }
    }

    record Row<T>(String section, T value) {
        Row {
            section = normalizeSection(section);
        }
    }

    /**
     * Built-in layout used for upgrades whose existing {@code general.yml} predates the editable
     * layout block. It deliberately starts with one real spacer row: large bitmap title glyphs only
     * receive vanilla one-line title spacing, so without this row they overdraw the first separator.
     */
    private static final List<Entry> DEFAULT = List.of(
            new Entry(Type.SPACER, "", " "),
            new Entry(Type.SEPARATOR, "", ""),
            new Entry(Type.TARGET, "eroforras",
                    "&c⌖ &f{target_name} {target_health}"),
            new Entry(Type.TEXT, "frakcio",
                    "&8┃ &7ꜰʀᴀᴋᴄɪó &8› {faction}"),
            new Entry(Type.TEXT, "kaszt",
                    "&8┃ &7ᴋᴀꜱᴢᴛ &8› {class} &8• &fLv. {class_level}"),
            new Entry(Type.RESOURCE, "eroforras",
                    "&8┃ &7{resource_name} &8› {resource_bar}"),
            new Entry(Type.INFO, "esemeny",
                    "&8┃ &7{info_label} &8› {info_value}"),
            new Entry(Type.SEPARATOR, "valuta", ""),
            new Entry(Type.TEXT, "valuta",
                    "&6✦ &7ᴠᴀʟᴜᴛᴀ &8› &6{balance}"),
            new Entry(Type.PARTY, "csapat",
                    "&d✦ &7ᴄѕᴀᴘᴀᴛ"),
            new Entry(Type.SEPARATOR, "", "")
    );

    private HudSidebarLayout() {
    }

    static List<Entry> defaults() {
        return DEFAULT;
    }

    /**
     * Parses Bukkit/YAML's list-of-maps representation. An absent or wholly invalid list falls back
     * to the built-in layout so an old server config immediately receives the overlap fix.
     */
    static List<Entry> parse(final List<?> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return DEFAULT;
        }
        final List<Entry> parsed = new ArrayList<>(rawEntries.size());
        for (final Object raw : rawEntries) {
            if (!(raw instanceof Map<?, ?> map)) {
                continue;
            }
            parsed.add(new Entry(
                    Type.parse(map.get("type")),
                    string(map.get("section")),
                    string(map.get("text"))));
        }
        return parsed.isEmpty() ? DEFAULT : List.copyOf(parsed);
    }

    /** Replaces the documented {@code {token}} placeholders in one configured legacy-text row. */
    static String render(final String template, final Map<String, String> tokens) {
        String rendered = template == null ? "" : template;
        if (tokens == null || tokens.isEmpty() || !rendered.contains("{")) {
            return rendered;
        }
        for (final Map.Entry<String, String> token : tokens.entrySet()) {
            rendered = rendered.replace("{" + token.getKey() + "}",
                    token.getValue() == null ? "" : token.getValue());
        }
        return rendered;
    }

    /**
     * Fits rows into the scoreboard maximum by removing complete low-priority sections first.
     * Structural rows have an empty section and are never removed by section eviction.
     */
    static <T> List<Row<T>> fit(final List<Row<T>> source, final int maximum,
                                final List<String> evictionOrder) {
        if (source == null || source.isEmpty() || maximum <= 0) {
            return List.of();
        }
        final List<Row<T>> fitted = new ArrayList<>(source);
        if (evictionOrder != null) {
            for (final String section : evictionOrder) {
                if (fitted.size() <= maximum) {
                    break;
                }
                final String normalized = normalizeSection(section);
                fitted.removeIf(row -> normalized.equals(row.section()));
            }
        }
        return fitted.size() <= maximum
                ? List.copyOf(fitted)
                : List.copyOf(fitted.subList(0, maximum));
    }

    private static String string(final Object value) {
        return value == null ? "" : value.toString();
    }

    private static String normalizeSection(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultSection(final Type type) {
        return switch (type) {
            case TARGET, RESOURCE -> "eroforras";
            case INFO -> "esemeny";
            case PARTY -> "csapat";
            case TEXT, SPACER, SEPARATOR -> "";
        };
    }
}
