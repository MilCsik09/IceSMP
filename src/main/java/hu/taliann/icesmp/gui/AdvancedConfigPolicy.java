package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.crates.CrateSoundResolver;
import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Shared validation for advanced scalar, text and string-list menu writes. */
public final class AdvancedConfigPolicy {

    private static final Set<String> MAJOR_EVENTS = Set.of(
            "world-boss", "invasion", "wild-hunt", "escort", "cultists");
    private static final Set<String> HUD_SECTIONS = Set.of(
            "frakcio", "valuta", "kaszt", "eroforras", "esemeny", "csapat");

    private AdvancedConfigPolicy() {
    }

    /** @return null when accepted, otherwise a Hungarian player-facing error. */
    public static String validate(final AdvancedConfigEntry entry, final Object value,
                                  final ConfigManager configManager) {
        return validate(entry, value, configManager, ignored -> null);
    }

    public static String validate(final AdvancedConfigEntry entry, final Object value,
                                  final ConfigManager configManager,
                                  final ConfigEditSession.Snapshot snapshot) {
        return validate(entry, value, configManager,
                snapshot == null ? ignored -> null : snapshot::resolvedValue);
    }

    private static String validate(final AdvancedConfigEntry entry, final Object value,
                                   final ConfigManager configManager,
                                   final Function<String, Object> stagedValue) {
        if (entry == null || value == null) {
            return "A szerkesztett érték hiányzik.";
        }
        final String generic = validateGeneric(entry, value);
        if (generic != null) {
            return generic;
        }

        final String key = entry.key();
        if (value instanceof Number number) {
            final double proposed = number.doubleValue();
            if (!Double.isFinite(proposed)) {
                return "Az értéknek véges számnak kell lennie.";
            }
            if (key.equals("world-tweaks.warden-death-xp.min")
                    && proposed > stagedDouble("world-tweaks.warden-death-xp.max", 125.0D,
                    configManager, stagedValue)) {
                return "A Warden minimum XP-je nem lehet nagyobb a maximum XP-nél.";
            }
            if (key.equals("world-tweaks.warden-death-xp.max")
                    && proposed < stagedDouble("world-tweaks.warden-death-xp.min", 80.0D,
                    configManager, stagedValue)) {
                return "A Warden maximum XP-je nem lehet kisebb a minimum XP-nél.";
            }
        }

        if (key.equals("world-events.orchestration.major-events")) {
            return requireAllowedList(value, MAJOR_EVENTS,
                    "Ismeretlen major event. Engedélyezett: " + String.join(", ", MAJOR_EVENTS));
        }
        if (key.equals("hud.dynamic.combat-visible-sections")) {
            return requireAllowedList(value, HUD_SECTIONS,
                    "Ismeretlen HUD-szekció. Engedélyezett: " + String.join(", ", HUD_SECTIONS));
        }
        if (key.equals("world-events.intro.lines")) {
            for (final String line : strings(value)) {
                final int separator = line.indexOf("||");
                if (separator <= 0 || separator >= line.length() - 2
                        || line.indexOf("||", separator + 2) >= 0) {
                    return "Minden intro-sor pontosan egy 'cím||alcím' pár legyen.";
                }
            }
        }

        if (key.startsWith("crates.")) {
            if (key.endsWith(".key-material")) {
                final Material material = Material.matchMaterial(String.valueOf(value));
                if (material == null || material.isAir()) {
                    return "A kulcs anyaga csak létező, nem AIR Bukkit material lehet.";
                }
            }
            if (key.endsWith(".permission")) {
                final String permission = String.valueOf(value).strip();
                if (!permission.isEmpty() && (!permission.startsWith("icesmp.")
                        || permission.contains(" ") || permission.length() > 96)) {
                    return "A crate permission üres vagy szóköz nélküli icesmp.* node lehet.";
                }
            }
            if (key.endsWith(".worlds")) {
                for (final String world : strings(value)) {
                    if (Bukkit.getWorld(world) == null) {
                        return "A crate worlds listában nincs betöltött ilyen világ: " + world;
                    }
                }
            }
            if (key.endsWith(".opening-sound.sound")
                    && CrateSoundResolver.resolve(String.valueOf(value)) == null) {
                return "Ismeretlen Minecraft/Bukkit hang: " + value;
            }
        }
        return null;
    }

    private static double stagedDouble(final String key, final double fallback,
                                       final ConfigManager configManager,
                                       final Function<String, Object> stagedValue) {
        final Object staged = stagedValue.apply(key);
        return staged instanceof Number number ? number.doubleValue()
                : configManager.getDouble(key, fallback);
    }

    private static String validateGeneric(final AdvancedConfigEntry entry, final Object value) {
        return switch (entry.type()) {
            case TOGGLE -> value instanceof Boolean ? null : "A kapcsoló csak boolean értéket fogad.";
            case NUMBER, INTEGER -> {
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    yield "A mező csak véges számot fogad.";
                }
                if (number.doubleValue() < entry.min() || number.doubleValue() > entry.max()) {
                    yield "Az érték kívül esik a megengedett tartományon.";
                }
                yield null;
            }
            case CYCLE -> entry.options().contains(String.valueOf(value))
                    ? null : "A kiválasztott érték nincs az engedélyezett opciók között.";
            case TEXT -> validateText(entry, String.valueOf(value));
            case STRING_LIST -> validateList(entry, value);
        };
    }

    private static String validateText(final AdvancedConfigEntry entry, final String raw) {
        final String value = raw == null ? "" : raw.strip();
        if (!entry.allowBlank() && value.isBlank()) {
            return "A szöveges érték nem lehet üres.";
        }
        if (value.length() > entry.maxLength()) {
            return "A szöveg legfeljebb " + entry.maxLength() + " karakter lehet.";
        }
        if (containsControl(value)) {
            return "A szöveg nem tartalmazhat sortörést vagy vezérlőkaraktert.";
        }
        return patternProblem(entry.itemPattern(), value,
                "A szöveg formátuma nem felel meg a mező szabályainak.");
    }

    private static String validateList(final AdvancedConfigEntry entry, final Object raw) {
        if (!(raw instanceof List<?> list)) {
            return "A mező csak string listát fogad.";
        }
        if (!entry.allowBlank() && list.isEmpty()) {
            return "A lista nem lehet üres.";
        }
        if (list.size() > entry.maxItems()) {
            return "A lista legfeljebb " + entry.maxItems() + " elemet tartalmazhat.";
        }
        final Set<String> unique = new HashSet<>();
        for (final Object item : list) {
            if (!(item instanceof String value) || value.isBlank()) {
                return "A lista minden eleme nem üres szöveg kell legyen.";
            }
            if (value.length() > entry.maxLength()) {
                return "Egy listaelem legfeljebb " + entry.maxLength() + " karakter lehet: " + value;
            }
            if (containsControl(value)) {
                return "A listaelem nem tartalmazhat sortörést vagy vezérlőkaraktert.";
            }
            if (!unique.add(value.toLowerCase(Locale.ROOT))) {
                return "A lista nem tartalmazhat duplikált elemet: " + value;
            }
            final String pattern = patternProblem(entry.itemPattern(), value,
                    "Érvénytelen listaelem: " + value);
            if (pattern != null) {
                return pattern;
            }
        }
        return null;
    }

    private static String patternProblem(final String expression, final String value,
                                         final String message) {
        if (expression == null || expression.isBlank() || value.isBlank()) {
            return null;
        }
        try {
            return Pattern.matches(expression, value) ? null : message;
        } catch (final PatternSyntaxException invalidPattern) {
            return "A mező belső validációs mintája hibás.";
        }
    }

    private static String requireAllowedList(final Object raw, final Set<String> allowed,
                                             final String message) {
        for (final String value : strings(raw)) {
            if (!allowed.contains(value.toLowerCase(Locale.ROOT))) {
                return message + " (kapott: " + value + ")";
            }
        }
        return null;
    }

    private static List<String> strings(final Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static boolean containsControl(final String value) {
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if ((Character.isISOControl(character) && character != '\t')
                    || character == '\n' || character == '\r') {
                return true;
            }
        }
        return false;
    }
}
