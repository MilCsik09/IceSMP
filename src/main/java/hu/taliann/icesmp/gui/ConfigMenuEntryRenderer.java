package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared renderer and value resolver for every editable admin-config icon. */
public final class ConfigMenuEntryRenderer {

    private static final Map<String, Object> CODE_DEFAULTS = Map.ofEntries(
            Map.entry("territory.protection.regen.debris-horizontal-multiplier", 1.0D),
            Map.entry("territory.protection.regen.debris-vertical-multiplier", 1.0D),
            Map.entry("territory.protection.regen.debris-horizontal-spread", 0.0D),
            Map.entry("territory.protection.regen.debris-extra-upward-velocity", 0.0D),
            Map.entry("territory.protection.regen.debris-gravity-enabled", true)
    );

    private ConfigMenuEntryRenderer() {
    }

    public static ItemStack render(final ConfigMenuGUI.Entry entry,
                                   final ConfigManager configManager) {
        final Object defaultValue = defaultValue(entry, configManager);
        final Object currentValue = currentValue(entry, configManager, defaultValue);
        final boolean overridden = configManager.hasOverride(entry.key());

        final List<String> lore = new ArrayList<>();
        lore.add("&8" + entry.key());
        lore.add("");
        for (final String line : wrap(ConfigMenuHelp.describe(entry.key(), entry.label()), 43)) {
            lore.add("&7" + line);
        }
        lore.add("");
        lore.add("&fJelenleg: &b" + formatValue(entry, currentValue));
        lore.add("&fAlapérték: &a" + formatValue(entry, defaultValue));
        lore.add(overridden
                ? "&eForrás: config.yml felülbírálás"
                : "&aForrás: subsystem alapkonfiguráció");
        lore.add("&aAzonnal, restart nélkül alkalmazódik");

        final Material material;
        final String name;
        switch (entry.type()) {
            case TOGGLE -> {
                final boolean enabled = Boolean.TRUE.equals(currentValue);
                material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
                name = (enabled ? "&a" : "&c") + entry.label();
                lore.add("");
                lore.add("&eBal/jobb katt: be- vagy kikapcsolás");
            }
            case CYCLE -> {
                material = Material.COMPARATOR;
                name = "&b" + entry.label();
                lore.add("&7Választható: &f" + String.join(" / ", entry.options()));
                lore.add("");
                lore.add("&eBal/jobb katt: következő lehetőség");
            }
            default -> {
                material = Material.PAPER;
                name = "&b" + entry.label();
                lore.add("&7Tartomány: &f" + formatBound(entry, entry.min())
                        + " &7– &f" + formatBound(entry, entry.max()));
                lore.add("");
                lore.add("&eBal katt: &f+" + formatStep(entry)
                        + " &7| &eJobb katt: &f−" + formatStep(entry));
                lore.add("&7SHIFT = ötszörös lépés");
            }
        }
        lore.add("&dGörgőkatt: visszaállítás az alapértékre");
        return tile(material, name, lore);
    }

    public static Object defaultValue(final ConfigMenuGUI.Entry entry,
                                      final ConfigManager configManager) {
        final Object configured = configManager.getBaseValue(entry.key());
        if (configured != null) {
            return normalize(entry, configured);
        }
        final Object codeDefault = CODE_DEFAULTS.get(entry.key());
        if (codeDefault != null) {
            return normalize(entry, codeDefault);
        }
        return switch (entry.type()) {
            case TOGGLE -> false;
            case CYCLE -> entry.options().isEmpty() ? "" : entry.options().get(0);
            case INTEGER -> (int) Math.round(entry.min());
            case NUMBER -> entry.min();
        };
    }

    public static Object currentValue(final ConfigMenuGUI.Entry entry,
                                      final ConfigManager configManager) {
        return currentValue(entry, configManager, defaultValue(entry, configManager));
    }

    private static Object currentValue(final ConfigMenuGUI.Entry entry,
                                       final ConfigManager configManager,
                                       final Object fallback) {
        return switch (entry.type()) {
            case TOGGLE -> configManager.getBoolean(entry.key(), Boolean.TRUE.equals(fallback));
            case CYCLE -> configManager.getString(entry.key(), String.valueOf(fallback));
            case INTEGER -> configManager.getInt(entry.key(), ((Number) fallback).intValue());
            case NUMBER -> configManager.getDouble(entry.key(), ((Number) fallback).doubleValue());
        };
    }

    public static double currentDouble(final ConfigMenuGUI.Entry entry,
                                       final ConfigManager configManager) {
        final Object current = currentValue(entry, configManager);
        return current instanceof Number number ? number.doubleValue() : entry.min();
    }

    public static String formatCurrent(final ConfigMenuGUI.Entry entry,
                                       final ConfigManager configManager) {
        return formatValue(entry, currentValue(entry, configManager));
    }

    private static Object normalize(final ConfigMenuGUI.Entry entry, final Object value) {
        return switch (entry.type()) {
            case TOGGLE -> value instanceof Boolean bool
                    ? bool : Boolean.parseBoolean(String.valueOf(value));
            case CYCLE -> String.valueOf(value);
            case INTEGER -> value instanceof Number number
                    ? number.intValue() : (int) Math.round(entry.min());
            case NUMBER -> value instanceof Number number
                    ? number.doubleValue() : entry.min();
        };
    }

    private static String formatValue(final ConfigMenuGUI.Entry entry, final Object value) {
        return switch (entry.type()) {
            case TOGGLE -> Boolean.TRUE.equals(value) ? "bekapcsolva" : "kikapcsolva";
            case CYCLE -> String.valueOf(value);
            case INTEGER -> String.valueOf(((Number) value).longValue());
            case NUMBER -> String.format(Locale.ROOT, "%.2f", ((Number) value).doubleValue());
        };
    }

    private static String formatBound(final ConfigMenuGUI.Entry entry, final double value) {
        if (!Double.isFinite(value) || value == Double.MAX_VALUE) {
            return "nincs gyakorlati plafon";
        }
        return entry.type() == ConfigMenuGUI.EntryType.INTEGER
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatStep(final ConfigMenuGUI.Entry entry) {
        return entry.type() == ConfigMenuGUI.EntryType.INTEGER
                ? String.valueOf((long) entry.step())
                : String.format(Locale.ROOT, "%.2f", entry.step());
    }

    private static List<String> wrap(final String text, final int width) {
        final List<String> lines = new ArrayList<>();
        final String[] explicit = text.split("\\n");
        for (final String paragraph : explicit) {
            final StringBuilder line = new StringBuilder();
            for (final String word : paragraph.trim().split("\\s+")) {
                if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    private static ItemStack tile(final Material material, final String name,
                                  final List<String> loreLines) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));
            final List<Component> lore = new ArrayList<>();
            for (final String line : loreLines) {
                lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(line)
                        .colorIfAbsent(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
