package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renderer and value resolver for {@link AdvancedConfigEntry}. */
public final class AdvancedConfigEntryRenderer {

    private AdvancedConfigEntryRenderer() {
    }

    public static ItemStack render(final AdvancedConfigEntry entry,
                                   final ConfigManager configManager) {
        final Object current = currentValue(entry, configManager);
        final Object fallback = defaultValue(entry, configManager);
        final List<String> lore = new ArrayList<>();
        lore.add("&8" + entry.key());
        lore.add("");
        for (final String line : wrap(entry.description(), 43)) {
            lore.add("&7" + line);
        }
        lore.add("");
        lore.add("&fJelenleg: &b" + format(entry, current));
        lore.add("&fAlapérték: &a" + format(entry, fallback));
        lore.add(configManager.hasOverride(entry.key())
                ? "&eForrás: config.yml felülbírálás"
                : "&aForrás: subsystem alapkonfiguráció");
        lore.add("&aAzonnal, restart nélkül alkalmazódik");

        final Material icon;
        final String name;
        switch (entry.type()) {
            case TOGGLE -> {
                final boolean enabled = Boolean.TRUE.equals(current);
                icon = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
                name = (enabled ? "&a" : "&c") + entry.label();
                lore.add("");
                lore.add("&eBal/jobb katt: be- vagy kikapcsolás");
            }
            case CYCLE -> {
                icon = Material.COMPARATOR;
                name = "&b" + entry.label();
                lore.add("&7Választható: &f" + String.join(" / ", entry.options()));
                lore.add("");
                lore.add("&eKattintás: következő lehetőség");
            }
            case NUMBER, INTEGER -> {
                icon = Material.PAPER;
                name = "&b" + entry.label();
                lore.add("&7Tartomány: &f" + bound(entry, entry.min())
                        + " &7– &f" + bound(entry, entry.max()));
                lore.add("");
                lore.add("&eBal katt: &f+" + step(entry)
                        + " &7| &eJobb katt: &f−" + step(entry));
                lore.add("&7SHIFT = ötszörös lépés");
            }
            case TEXT -> {
                icon = Material.NAME_TAG;
                name = "&b" + entry.label();
                lore.add("&7Maximális hossz: &f" + entry.maxLength() + " karakter");
                lore.add("");
                lore.add("&eKattintás: biztonságos chat-bevitel");
            }
            case STRING_LIST -> {
                icon = Material.WRITABLE_BOOK;
                name = "&b" + entry.label();
                lore.add("&7Legfeljebb &f" + entry.maxItems() + " elem"
                        + "&7, elemenként &f" + entry.maxLength() + " karakter");
                lore.add("&7Az elemeket a chatben &f;; &7jellel válaszd el.");
                lore.add("");
                lore.add("&eKattintás: biztonságos lista-bevitel");
            }
            default -> throw new IllegalStateException("Ismeretlen advanced config típus");
        }
        lore.add("&dGörgőkatt/Q: visszaállítás az alapértékre");
        return GuiUtil.item(icon, name, lore);
    }

    public static Object defaultValue(final AdvancedConfigEntry entry,
                                      final ConfigManager configManager) {
        final Object value = configManager.getBaseValue(entry.key());
        if (value != null) {
            return normalize(entry, value);
        }
        return switch (entry.type()) {
            case TOGGLE -> false;
            case NUMBER -> entry.min();
            case INTEGER -> (int) Math.round(entry.min());
            case CYCLE -> entry.options().get(0);
            case TEXT -> "";
            case STRING_LIST -> List.of();
        };
    }

    public static Object currentValue(final AdvancedConfigEntry entry,
                                      final ConfigManager configManager) {
        final Object fallback = defaultValue(entry, configManager);
        return switch (entry.type()) {
            case TOGGLE -> configManager.getBoolean(entry.key(), Boolean.TRUE.equals(fallback));
            case NUMBER -> configManager.getDouble(entry.key(), ((Number) fallback).doubleValue());
            case INTEGER -> configManager.getInt(entry.key(), ((Number) fallback).intValue());
            case CYCLE, TEXT -> configManager.getString(entry.key(), String.valueOf(fallback));
            case STRING_LIST -> configManager.getStringList(entry.key());
        };
    }

    public static double currentDouble(final AdvancedConfigEntry entry,
                                       final ConfigManager configManager) {
        final Object value = currentValue(entry, configManager);
        return value instanceof Number number ? number.doubleValue() : entry.min();
    }

    public static String formatCurrent(final AdvancedConfigEntry entry,
                                       final ConfigManager configManager) {
        return format(entry, currentValue(entry, configManager));
    }

    private static Object normalize(final AdvancedConfigEntry entry, final Object value) {
        return switch (entry.type()) {
            case TOGGLE -> value instanceof Boolean bool
                    ? bool : Boolean.parseBoolean(String.valueOf(value));
            case NUMBER -> value instanceof Number number ? number.doubleValue() : entry.min();
            case INTEGER -> value instanceof Number number
                    ? number.intValue() : (int) Math.round(entry.min());
            case CYCLE, TEXT -> String.valueOf(value);
            case STRING_LIST -> value instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList() : List.of();
        };
    }

    private static String format(final AdvancedConfigEntry entry, final Object value) {
        return switch (entry.type()) {
            case TOGGLE -> Boolean.TRUE.equals(value) ? "bekapcsolva" : "kikapcsolva";
            case INTEGER -> String.valueOf(((Number) value).longValue());
            case NUMBER -> String.format(Locale.ROOT, "%.3f", ((Number) value).doubleValue());
            case CYCLE -> String.valueOf(value);
            case TEXT -> compact(String.valueOf(value), 56);
            case STRING_LIST -> {
                final List<?> list = value instanceof List<?> values ? values : List.of();
                yield list.isEmpty() ? "üres lista" : list.size() + " elem: "
                        + compact(String.join(" | ", list.stream().map(String::valueOf).toList()), 48);
            }
        };
    }

    private static String compact(final String raw, final int max) {
        final String value = raw == null || raw.isBlank() ? "(üres)" : raw.replace('\n', ' ');
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static String bound(final AdvancedConfigEntry entry, final double value) {
        return entry.type() == AdvancedConfigEntry.Type.INTEGER
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String step(final AdvancedConfigEntry entry) {
        return entry.type() == AdvancedConfigEntry.Type.INTEGER
                ? String.valueOf((long) entry.step())
                : String.format(Locale.ROOT, "%.3f", entry.step());
    }

    private static List<String> wrap(final String text, final int width) {
        final List<String> result = new ArrayList<>();
        for (final String paragraph : text.split("\\n")) {
            final StringBuilder line = new StringBuilder();
            for (final String word : paragraph.trim().split("\\s+")) {
                if (line.length() > 0 && line.length() + word.length() + 1 > width) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
            if (line.length() > 0) {
                result.add(line.toString());
            }
        }
        return result;
    }
}
