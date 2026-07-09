package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Manager for localized and configurable messages throughout the plugin.
 * Provides centralized access to all user-facing strings with configuration support.
 */
public final class MessageManager {

    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    /** Detects legacy colour codes (&a, §c, …); if present, the message is treated as legacy, not MiniMessage. */
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");
    /** Bundled per-subsystem message files under messages/ (extracted on first run). */
    private static final String[] MESSAGE_GROUPS = {
            "claim", "currency", "faction", "job", "market", "party", "pet", "profession",
            "quest", "relic", "spec", "spell", "system", "world"
    };

    private final JavaPlugin plugin;
    private final File messagesFile;
    private YamlConfiguration messagesConfiguration;

    /**
     * Constructs a new MessageManager.
     *
     * @param plugin the plugin instance
     * @param configManager the configuration manager
     */
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

    /**
     * Gets a message from configuration with a fallback default.
     *
     * @param key the message key (dot notation)
     * @param defaultValue the fallback value if not found
     * @return the message with fallback
     */
    public String get(final String key, final String defaultValue) {
        return colorize(resolveMessage(key, defaultValue));
    }

    /**
     * Gets a message from configuration with multiple format arguments.
     *
     * @param key the message key
     * @param defaultValue the fallback value
     * @param args format arguments (%s substitutions)
     * @return formatted message
     */
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

    /**
     * Whether a message should be parsed as MiniMessage: it has {@code <...>} tags and
     * carries no legacy {@code &}/{@code §} colour codes (those mark a legacy message).
     */
    private boolean isMiniMessage(final String message) {
        return message.indexOf('<') >= 0 && message.indexOf('>') >= 0 && !LEGACY_CODE.matcher(message).find();
    }

    /** Renders a raw message to a legacy (§) string, parsing MiniMessage when applicable. */
    private String colorize(final String message) {
        if (isMiniMessage(message)) {
            try {
                return SECTION_SERIALIZER.serialize(MINI_MESSAGE.deserialize(message));
            } catch (final Exception ignored) {
                // Not valid MiniMessage — fall through to legacy colouring.
            }
        }
        return TextUtil.color(message);
    }

    /** Renders a raw message to a Component, parsing MiniMessage when applicable, else legacy. */
    private Component renderComponent(final String message) {
        if (isMiniMessage(message)) {
            try {
                return MINI_MESSAGE.deserialize(message);
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

