package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ConfigValidator;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Admin entry point: {@code /icesmp reload} plus the generic ingame config control
 * ({@code /icesmp config get|set|unset|list|find}). {@code set} writes into the data-folder
 * {@code config.yml} — the file the ConfigManager merges LAST, so its keys override every
 * bundled/per-subsystem default — then reloads and re-validates the merged config. Because
 * nearly every manager reads config values at use time, most changes apply instantly; values
 * baked in at startup (e.g. declarative spell defaults built in the SpellCatalog) still need
 * a restart, which the success message points out.
 */
public final class IceSMPCommand implements BasicCommand {

    private static final String PERMISSION = "icesmp.admin.reload";
    private static final String CONFIG_PERMISSION = "icesmp.admin.config";
    private static final int MAX_SUGGESTED_KEYS = 40;
    private static final int MAX_LISTED_KEYS = 30;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public IceSMPCommand(final JavaPlugin plugin, final ConfigManager configManager,
                         final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultsagod erre a parancsra."));
            return;
        }

        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            configManager.reload();
            messageManager.reload();
            ConfigValidator.validate(configManager, plugin.getLogger());
            sender.sendMessage(messageManager.get("admin.icesmp.reload.success", "<green>Plugin konfiguracio sikeresen ujratoltve!</green>"));
            return;
        }

        if (args.length >= 1 && "config".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission(CONFIG_PERMISSION)) {
                sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultsagod erre a parancsra."));
                return;
            }
            handleConfig(sender, args);
            return;
        }

        sendHelp(sender);
    }

    private void handleConfig(final CommandSender sender, final String[] args) {
        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "get" -> handleGet(sender, args);
            case "set" -> handleSet(sender, args);
            case "unset" -> handleUnset(sender, args);
            case "list" -> handleList(sender);
            case "find" -> handleFind(sender, args);
            default -> sendHelp(sender);
        }
    }

    private void handleGet(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.get-usage", "&cHasználat: /icesmp config get <kulcs>"));
            return;
        }
        final String key = args[2];
        final Object value = configManager.getConfiguration() == null ? null : configManager.getConfiguration().get(key);
        if (value == null) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.get-missing",
                    "&eNincs ilyen kulcs a betöltött configban: &f%s &7(a kódbeli inline default élhet).", key));
            return;
        }
        final boolean overridden = plugin.getConfig().isSet(key);
        sender.sendMessage(messageManager.get("admin.icesmp.config.get-value",
                "&6%s &7= &f%s &7(%s%s)", key, String.valueOf(value),
                value.getClass().getSimpleName(),
                overridden ? "&e, ingame felülbírálva" : ""));
    }

    private void handleSet(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.set-usage",
                    "&cHasználat: /icesmp config set <kulcs> <érték> &7(true/false, szám vagy szöveg; lista-kulcsnál vesszővel tagolva)"));
            return;
        }
        final String key = args[2];
        final String raw = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        final Object parsed = parseValue(key, raw);

        plugin.getConfig().set(key, parsed);
        plugin.saveConfig();
        configManager.reload();
        messageManager.reload();
        ConfigValidator.validate(configManager, plugin.getLogger());

        sender.sendMessage(messageManager.get("admin.icesmp.config.set-success",
                "&aBeállítva: &6%s &7= &f%s &7(%s). A legtöbb érték azonnal él; a szerverindításkor beépülők (pl. deklaratív spell-értékek) újraindítást igényelnek. A config-ellenőrző figyelmeztetései a konzolon.",
                key, String.valueOf(parsed), parsed.getClass().getSimpleName()));
    }

    private void handleUnset(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.unset-usage", "&cHasználat: /icesmp config unset <kulcs>"));
            return;
        }
        final String key = args[2];
        if (!plugin.getConfig().isSet(key)) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.unset-missing",
                    "&eNincs ilyen ingame felülbírálás: &f%s &7(csak a config.yml-ben tárolt kulcsok törölhetők innen).", key));
            return;
        }
        plugin.getConfig().set(key, null);
        plugin.saveConfig();
        configManager.reload();
        messageManager.reload();
        sender.sendMessage(messageManager.get("admin.icesmp.config.unset-success",
                "&aFelülbírálás törölve: &6%s &7— újra a config-fájlok/kódbeli default él.", key));
    }

    /** Lists the ingame overrides currently stored in the data-folder config.yml. */
    private void handleList(final CommandSender sender) {
        final List<String> keys = new ArrayList<>();
        for (final String key : plugin.getConfig().getKeys(true)) {
            if (!plugin.getConfig().isConfigurationSection(key)) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.list-empty",
                    "&7Nincs ingame config-felülbírálás (config.yml üres)."));
            return;
        }
        sender.sendMessage(messageManager.get("admin.icesmp.config.list-header",
                "&6Ingame felülbírálások (&f%s&6):", keys.size()));
        keys.stream().sorted().limit(MAX_LISTED_KEYS).forEach(key ->
                sender.sendMessage(messageManager.get("admin.icesmp.config.list-entry",
                        "&7- &6%s &7= &f%s", key, String.valueOf(plugin.getConfig().get(key)))));
        if (keys.size() > MAX_LISTED_KEYS) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.list-truncated",
                    "&7… és még &f%s &7kulcs (lásd config.yml).", keys.size() - MAX_LISTED_KEYS));
        }
    }

    /** Searches the MERGED keyspace (every config file together) for keys containing the text. */
    private void handleFind(final CommandSender sender, final String[] args) {
        if (args.length < 3 || configManager.getConfiguration() == null) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.find-usage", "&cHasználat: /icesmp config find <szövegrészlet>"));
            return;
        }
        final String needle = args[2].toLowerCase(Locale.ROOT);
        final List<String> hits = configManager.getConfiguration().getKeys(true).stream()
                .filter(key -> !configManager.getConfiguration().isConfigurationSection(key))
                .filter(key -> key.toLowerCase(Locale.ROOT).contains(needle))
                .sorted()
                .limit(MAX_LISTED_KEYS)
                .toList();
        if (hits.isEmpty()) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.find-empty", "&7Nincs találat: &f%s", needle));
            return;
        }
        sender.sendMessage(messageManager.get("admin.icesmp.config.find-header", "&6Kulcs-találatok (max %s):", MAX_LISTED_KEYS));
        hits.forEach(key -> sender.sendMessage(messageManager.get("admin.icesmp.config.find-entry",
                "&7- &6%s &7= &f%s", key, String.valueOf(configManager.getConfiguration().get(key)))));
    }

    /**
     * Type-inferred parse: true/false → Boolean, whole number → Integer, decimal → Double,
     * otherwise String — EXCEPT when the merged config already holds a list at the key, where a
     * comma-separated value keeps the list shape (so `materials`-style keys stay lists).
     */
    private Object parseValue(final String key, final String raw) {
        if (configManager.getConfiguration() != null && configManager.getConfiguration().isList(key)) {
            return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
            return Boolean.parseBoolean(raw);
        }
        try {
            return Integer.parseInt(raw);
        } catch (final NumberFormatException ignored) {
            // fall through
        }
        try {
            return Double.parseDouble(raw);
        } catch (final NumberFormatException ignored) {
            // fall through
        }
        return raw;
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("admin.icesmp.help-header", "&6/icesmp &7- admin parancsok:"));
        sender.sendMessage(messageManager.get("admin.icesmp.help-reload", "&e/icesmp reload &7- Config + üzenetek újratöltése."));
        sender.sendMessage(messageManager.get("admin.icesmp.help-config-get", "&e/icesmp config get <kulcs> &7- Kulcs aktuális értéke."));
        sender.sendMessage(messageManager.get("admin.icesmp.help-config-set", "&e/icesmp config set <kulcs> <érték> &7- Felülbírálás (azonnali reload)."));
        sender.sendMessage(messageManager.get("admin.icesmp.help-config-unset", "&e/icesmp config unset <kulcs> &7- Felülbírálás törlése."));
        sender.sendMessage(messageManager.get("admin.icesmp.help-config-list", "&e/icesmp config list &7- Aktív ingame felülbírálások."));
        sender.sendMessage(messageManager.get("admin.icesmp.help-config-find", "&e/icesmp config find <szöveg> &7- Kulcs-keresés a teljes configban."));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("reload", "config").stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (!"config".equalsIgnoreCase(args[0]) || !sender.hasPermission(CONFIG_PERMISSION)) {
            return List.of();
        }
        if (args.length == 2) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("get", "set", "unset", "list", "find").stream().filter(option -> option.startsWith(prefix)).toList();
        }
        if (args.length == 3) {
            final String action = args[1].toLowerCase(Locale.ROOT);
            final String prefix = args[2].toLowerCase(Locale.ROOT);
            if ("unset".equals(action)) {
                return plugin.getConfig().getKeys(true).stream()
                        .filter(key -> !plugin.getConfig().isConfigurationSection(key))
                        .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted().limit(MAX_SUGGESTED_KEYS).toList();
            }
            if (("get".equals(action) || "set".equals(action)) && configManager.getConfiguration() != null) {
                return configManager.getConfiguration().getKeys(true).stream()
                        .filter(key -> !configManager.getConfiguration().isConfigurationSection(key))
                        .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted().limit(MAX_SUGGESTED_KEYS).toList();
            }
        }
        // set <kulcs> <érték>: suggest the CURRENT value as a starting point.
        if (args.length == 4 && "set".equalsIgnoreCase(args[1]) && configManager.getConfiguration() != null) {
            final Object current = configManager.getConfiguration().get(args[2]);
            if (current != null && !(current instanceof org.bukkit.configuration.ConfigurationSection)) {
                final String asText = String.valueOf(current);
                return asText.toLowerCase(Locale.ROOT).startsWith(args[3].toLowerCase(Locale.ROOT))
                        ? List.of(asText) : List.of();
            }
        }
        return List.of();
    }
}
