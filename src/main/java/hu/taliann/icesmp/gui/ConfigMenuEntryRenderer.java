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

/** Shared renderer and staged value resolver for every scalar admin-config icon. */
public final class ConfigMenuEntryRenderer {

    private enum EffectMode { LIVE, RELOAD_HOOK, RESTART_REQUIRED }

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
        return render(entry, configManager, null);
    }

    public static ItemStack render(final ConfigMenuGUI.Entry entry,
                                   final ConfigManager configManager,
                                   final ConfigEditSession session) {
        final Object defaultValue = defaultValue(entry, configManager, session);
        final Object currentValue = currentValue(entry, configManager, session, defaultValue);

        final List<String> lore = new ArrayList<>();
        lore.add("&8" + entry.key());
        lore.add("");
        for (final String line : wrap(ConfigMenuHelp.describe(entry.key(), entry.label()), 43)) {
            lore.add("&7" + line);
        }
        lore.add("");
        lore.add("&fJelenleg: &b" + formatValue(entry, currentValue));
        lore.add("&fAlapérték: &a" + formatValue(entry, defaultValue));
        appendSource(lore, entry.key(), configManager, session);
        lore.add(switch (effectMode(entry.key())) {
            case LIVE -> "&aHatás: mentés után azonnal él";
            case RELOAD_HOOK -> "&eHatás: mentés utáni élő reload-hook";
            case RESTART_REQUIRED -> "&cHatás: szerver-újraindítás szükséges";
        });

        final Material material;
        final String name;
        switch (entry.type()) {
            case TOGGLE -> {
                final boolean enabled = Boolean.TRUE.equals(currentValue);
                material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
                name = (enabled ? "&a" : "&c") + entry.label();
                lore.add("");
                lore.add("&eBal/jobb katt: staged be-/kikapcsolás");
            }
            case CYCLE -> {
                material = Material.COMPARATOR;
                name = "&b" + entry.label();
                lore.add("&7Választható: &f" + String.join(" / ", entry.options()));
                lore.add("");
                lore.add("&eKattintás: következő staged lehetőség");
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
        lore.add("&dGörgőkatt/Q: staged reset az alapértékre");
        return tile(material, name, lore);
    }

    public static Object defaultValue(final ConfigMenuGUI.Entry entry,
                                      final ConfigManager configManager) {
        return defaultValue(entry, configManager, null);
    }

    public static Object defaultValue(final ConfigMenuGUI.Entry entry,
                                      final ConfigManager configManager,
                                      final ConfigEditSession session) {
        final Object configured = session == null
                ? configManager.getBaseValue(entry.key()) : session.defaultValue(entry.key());
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
        return currentValue(entry, configManager, null);
    }

    public static Object currentValue(final ConfigMenuGUI.Entry entry,
                                      final ConfigManager configManager,
                                      final ConfigEditSession session) {
        return currentValue(entry, configManager, session,
                defaultValue(entry, configManager, session));
    }

    private static Object currentValue(final ConfigMenuGUI.Entry entry,
                                       final ConfigManager configManager,
                                       final ConfigEditSession session,
                                       final Object fallback) {
        if (session != null) {
            final Object staged = session.value(entry.key());
            return staged == null ? fallback : normalize(entry, staged);
        }
        return switch (entry.type()) {
            case TOGGLE -> configManager.getBoolean(entry.key(), Boolean.TRUE.equals(fallback));
            case CYCLE -> configManager.getString(entry.key(), String.valueOf(fallback));
            case INTEGER -> configManager.getInt(entry.key(), ((Number) fallback).intValue());
            case NUMBER -> configManager.getDouble(entry.key(), ((Number) fallback).doubleValue());
        };
    }

    public static double currentDouble(final ConfigMenuGUI.Entry entry,
                                       final ConfigManager configManager) {
        return currentDouble(entry, configManager, null);
    }

    public static double currentDouble(final ConfigMenuGUI.Entry entry,
                                       final ConfigManager configManager,
                                       final ConfigEditSession session) {
        final Object current = currentValue(entry, configManager, session);
        return current instanceof Number number ? number.doubleValue() : entry.min();
    }

    public static String formatCurrent(final ConfigMenuGUI.Entry entry,
                                       final ConfigManager configManager) {
        return formatValue(entry, currentValue(entry, configManager));
    }

    private static void appendSource(final List<String> lore, final String key,
                                     final ConfigManager configManager,
                                     final ConfigEditSession session) {
        if (session != null && session.hasPending(key)) {
            lore.add(session.pendingChanges().get(key) == null
                    ? "&dNem mentett reset: subsystem alapérték"
                    : "&eNem mentett staged módosítás");
            return;
        }
        lore.add(configManager.hasOverride(key)
                ? "&eForrás: config.yml felülbírálás"
                : "&aForrás: subsystem alapkonfiguráció");
    }

    private static EffectMode effectMode(final String key) {
        if (key.equals("territory.protection.regen.restore-interval-ticks")) {
            return EffectMode.RESTART_REQUIRED;
        }
        if (key.startsWith("motd.") || key.startsWith("sit.")
                || key.startsWith("crates-settings.") || key.startsWith("crates.")
                || key.startsWith("resource-pack.") || key.startsWith("factions.passives.")
                || key.startsWith("factions.whisper.") || key.startsWith("professions.recipes.")
                || key.startsWith("moderation.") || key.startsWith("hud.")
                || key.startsWith("tablist.") || key.startsWith("mob-scaling.")
                || key.equals("world-events.check-interval-seconds")
                || key.equals("settings.disable-locator-bar")
                || key.equals("pets.companion.tick-ticks")
                || key.equals("currency.economy-event.check-interval-minutes")
                || key.equals("factions.tax.enabled")
                || key.equals("factions.tax.interval-minutes")) {
            return EffectMode.RELOAD_HOOK;
        }
        return EffectMode.LIVE;
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
