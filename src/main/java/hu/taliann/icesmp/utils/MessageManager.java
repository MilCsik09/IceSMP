package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

/** Centralized access to all user-facing, configurable messages. */
public final class MessageManager {

    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    /** Legacy (&/§) colour code → MiniMessage tag, so mixed-format messages parse as one format. */
    private static final Map<Character, String> LEGACY_TO_TAG = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset"));
    /** Bundled per-subsystem message files under messages/ (extracted on first run). */
    private static final String[] MESSAGE_GROUPS = {
            "afk", "claim", "currency", "faction", "job", "market", "party", "pet", "profession",
            "quest", "relic", "sit", "spec", "spell", "system", "territory", "world", "devitem",
            "hud", "moderation"
    };

    private final JavaPlugin plugin;
    private final File messagesFile;
    /** volatile: reload() swaps it on one thread while region threads read it concurrently. */
    private volatile YamlConfiguration messagesConfiguration;

    public MessageManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        load();
    }

    /**
     * Loads messages from the per-subsystem files in {@code messages/} plus the optional
     * {@code messages.yml} override file, merging them into one keyspace. The per-subsystem files
     * are the defaults; {@code messages.yml} is loaded LAST so an admin can override any key there.
     */
    public void load() {
        plugin.getDataFolder().mkdirs();
        final YamlConfiguration merged = new YamlConfiguration();

        // Per-subsystem defaults: messages/<subsystem>.yml. Extract the bundled set on first run,
        // then merge every .yml present (so a newly added group file is picked up automatically).
        final File dir = new File(plugin.getDataFolder(), "messages");
        dir.mkdirs();
        for (final String group : MESSAGE_GROUPS) {
            if (!new File(dir, group + ".yml").exists()) {
                plugin.saveResource("messages/" + group + ".yml", false);
            }
        }
        final File[] files = dir.listFiles((directory, name) -> name.endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files); // deterministic merge order
            for (final File file : files) {
                mergeInto(merged, YamlConfiguration.loadConfiguration(file));
            }
        }

        // Optional override file (loaded last so its keys win).
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        mergeInto(merged, YamlConfiguration.loadConfiguration(messagesFile));

        messagesConfiguration = merged;
    }

    /** Copies every leaf (non-section) key from {@code source} into {@code target}. */
    private void mergeInto(final YamlConfiguration target, final YamlConfiguration source) {
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                target.set(key, source.get(key));
            }
        }
    }

    public void reload() {
        load();
    }

    // ===== GENERIC HELPER METHODS =====

    public String get(final String key, final String defaultValue) {
        return colorize(resolveMessage(key, defaultValue));
    }

    public String get(final String key, final String defaultValue, final Object... args) {
        final String template = resolveMessage(key, defaultValue);
        try {
            return colorize(String.format(template, args));
        } catch (final Exception e) {
            plugin.getLogger().warning("Message format error for key: " + key);
            return colorize(defaultValue);
        }
    }

    public String get(final String key, final String defaultValue, final Map<String, String> placeholders) {
        String message = resolveMessage(key, defaultValue);
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return colorize(message);
    }

    public String required(final String key, final Object... args) {
        final String template = requiredTemplate(key);
        try {
            return colorize(String.format(template, args));
        } catch (final Exception failure) {
            throw new IllegalStateException("Invalid required message format: " + key, failure);
        }
    }

    public Component getMessage(final String key) {
        return getMessage(key, "", Map.of());
    }

    public Component getMessage(final String key, final String defaultValue) {
        return getMessage(key, defaultValue, Map.of());
    }

    public Component getMessage(final String key, final String defaultValue, final Map<String, String> placeholders) {
        String message = resolveMessage(key, defaultValue);
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return renderComponent(message);
    }

    public Component requiredMessage(final String key) {
        return renderComponent(requiredTemplate(key));
    }

    public Component requiredMessage(final String key, final Map<String, String> placeholders) {
        String message = requiredTemplate(key);
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return renderComponent(message);
    }

    public Component getComponent(final String key, final String defaultValue) {
        return renderComponent(resolveMessage(key, defaultValue));
    }

    public Component getComponent(final String key, final String defaultValue, final Object... args) {
        final String template = resolveMessage(key, defaultValue);
        try {
            return renderComponent(String.format(template, args));
        } catch (final Exception e) {
            plugin.getLogger().warning("Message format error for key: " + key);
            return renderComponent(defaultValue);
        }
    }

    public Component requiredComponent(final String key, final Object... args) {
        final String template = requiredTemplate(key);
        try {
            return renderComponent(String.format(template, args));
        } catch (final Exception failure) {
            throw new IllegalStateException("Invalid required message format: " + key, failure);
        }
    }

    /**
     * Whether a message should go through the MiniMessage parser: it carries {@code <...>}
     * tags. Mixed messages (tags + legacy codes) are normalized first via
     * {@link #legacyToTags(String)} — before this, one legacy {@code &7} in a MiniMessage
     * template pushed the whole string onto the legacy path and the tags leaked raw to chat.
     */
    private boolean hasMiniTags(final String message) {
        return message.indexOf('<') >= 0 && message.indexOf('>') >= 0;
    }

    /**
     * Converts legacy (&/§) colour codes to MiniMessage tags. Placeholder values substituted
     * from {@link #get} return legacy §-strings, so a MiniMessage template can end up mixed
     * even when its source YAML is clean — normalizing here covers both cases.
     */
    private static String legacyToTags(final String message) {
        final StringBuilder out = new StringBuilder(message.length() + 16);
        for (int i = 0; i < message.length(); i++) {
            final char c = message.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < message.length()) {
                final String tag = LEGACY_TO_TAG.get(Character.toLowerCase(message.charAt(i + 1)));
                if (tag != null) {
                    out.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Renders a raw message to a legacy (§) string, parsing MiniMessage when applicable. */
    private String colorize(final String message) {
        if (hasMiniTags(message)) {
            try {
                return SECTION_SERIALIZER.serialize(MINI_MESSAGE.deserialize(legacyToTags(message)));
            } catch (final Exception ignored) {
                // Not valid MiniMessage — fall through to legacy colouring.
            }
        }
        return TextUtil.color(message);
    }

    /** Renders a raw message to a Component, parsing MiniMessage when applicable, else legacy. */
    private Component renderComponent(final String message) {
        if (hasMiniTags(message)) {
            try {
                return MINI_MESSAGE.deserialize(legacyToTags(message));
            } catch (final Exception ignored) {
                // Fall back to legacy parsing if MiniMessage tags are invalid.
            }
        }
        return SECTION_SERIALIZER.deserialize(TextUtil.color(message));
    }

    private String resolveMessage(final String key, final String defaultValue) {
        if (messagesConfiguration == null) {
            load();
        }

        final String normalized = normalizeKey(key);
        return messagesConfiguration.getString(normalized, defaultValue);
    }

    private String requiredTemplate(final String key) {
        final String value = resolveMessage(key, null);
        if (value == null) throw new IllegalStateException("Missing required message key: " + key);
        return value;
    }

    private String normalizeKey(final String key) {
        if (key == null || key.isBlank()) {
            return "messages.unknown";
        }

        if (key.startsWith("messages.")) {
            return key;
        }

        return "messages." + key;
    }
}
