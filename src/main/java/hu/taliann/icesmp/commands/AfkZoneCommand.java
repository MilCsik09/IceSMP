package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.selection.CuboidSelectionService;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Admin surface for native AFK zones; it consumes the shared claim-compatible 3D selection. */
public final class AfkZoneCommand implements BasicCommand {

    private final JavaPlugin plugin;
    private final AfkManager afkManager;
    private final CuboidSelectionService selectionService;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public AfkZoneCommand(final JavaPlugin plugin, final AfkManager afkManager,
                          final CuboidSelectionService selectionService,
                          final ConfigManager configManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.afkManager = afkManager;
        this.selectionService = selectionService;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack source, final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        if (!sender.hasPermission(Permissions.AFK)) {
            sender.sendMessage(messageManager.get("messages.permission-denied",
                    "&cNincs jogosultságod erre a parancsra."));
            return;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }
        final String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list", "lista" -> list(sender);
            case "status", "állapot", "allapot" -> status(sender, args);
            case "create", "letrehoz" -> create(requirePlayer(sender), args);
            case "replace", "terulet" -> replace(requirePlayer(sender), args);
            case "delete", "torol" -> delete(sender, args);
            case "tp", "teleport" -> teleport(requirePlayer(sender), args);
            case "show", "mutat" -> show(requirePlayer(sender), args);
            case "clear", "torles" -> clear(requirePlayer(sender));
            default -> sendHelp(sender);
        }
    }

    private void create(final Player player, final String[] args) {
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(messageManager.get("afk-zone-usage-create",
                    "&cHasználat: /afkzone create <id> [megjelenített név] — a közös /claim pos1|pos2 selectionből."));
            return;
        }
        final CuboidSelectionService.Result selection = selectionService.result(player.getUniqueId(),
                Math.max(1L, configManager.getLong("afk.max-zone-volume", 1_000_000L)));
        if (!selection.ready()) {
            sendSelectionProblem(player, selection);
            return;
        }
        final String displayName = args.length > 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : args[1];
        final String error = afkManager.createZone(args[1], displayName, selection.cuboid());
        if (error != null) {
            player.sendMessage(messageManager.get(error, defaultError(error), args[1]));
            return;
        }
        selectionService.clear(player.getUniqueId());
        player.sendMessage(messageManager.get("afk-zone-created",
                "&aAFK-zóna létrehozva: &f%s&a. A részletes jutalmak a configban állíthatók.", args[1]));
    }

    private void replace(final Player player, final String[] args) {
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(messageManager.get("afk-zone-usage-replace",
                    "&cHasználat: /afkzone replace <id> — az aktuális közös selectionre cseréli a cuboidot."));
            return;
        }
        final CuboidSelectionService.Result selection = selectionService.result(player.getUniqueId(),
                Math.max(1L, configManager.getLong("afk.max-zone-volume", 1_000_000L)));
        if (!selection.ready()) {
            sendSelectionProblem(player, selection);
            return;
        }
        final String error = afkManager.replaceZoneArea(args[1], selection.cuboid());
        if (error != null) {
            player.sendMessage(messageManager.get(error, defaultError(error), args[1]));
            return;
        }
        selectionService.clear(player.getUniqueId());
        player.sendMessage(messageManager.get("afk-zone-replaced",
                "&aAFK-zóna területe lecserélve: &f%s&a.", args[1]));
    }

    private void delete(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("afk-zone-usage-delete", "&cHasználat: /afkzone delete <id>"));
            return;
        }
        final String error = afkManager.deleteZone(args[1]);
        if (error != null) {
            sender.sendMessage(messageManager.get(error, defaultError(error), args[1]));
            return;
        }
        sender.sendMessage(messageManager.get("afk-zone-deleted", "&aAFK-zóna törölve: &f%s", args[1]));
    }

    private void list(final CommandSender sender) {
        if (afkManager.zoneIds().isEmpty() && afkManager.allZoneProblems().isEmpty()) {
            sender.sendMessage(messageManager.get("afk-zone-list-empty", "&7Nincs konfigurált AFK-zóna."));
            return;
        }
        sender.sendMessage(messageManager.get("afk-zone-list-header", "&6AFK-zónák:"));
        for (final String id : afkManager.zoneIds()) {
            sender.sendMessage(messageManager.get("afk-zone-list-entry", "&7- &f%s", afkManager.describeZone(id)));
        }
        for (final var invalid : afkManager.allZoneProblems().entrySet()) {
            sender.sendMessage(messageManager.get("afk-zone-list-invalid", "&c- %s: %s",
                    invalid.getKey(), String.join(" | ", invalid.getValue())));
        }
    }

    private void status(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("afk-zone-status-summary",
                    "&6AFK-zónák: &f%s érvényes&6, &f%s hibás&6.",
                    afkManager.zoneIds().size(), afkManager.allZoneProblems().size()));
            return;
        }
        final String description = afkManager.describeZone(args[1]);
        if (description == null) {
            sender.sendMessage(messageManager.get("afk-zone-unknown", "&cNincs ilyen AFK-zóna: &f%s", args[1]));
            return;
        }
        sender.sendMessage(messageManager.get("afk-zone-status", "&7%s", description));
    }

    private void teleport(final Player player, final String[] args) {
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(messageManager.get("afk-zone-usage-tp", "&cHasználat: /afkzone tp <id>"));
            return;
        }
        final Location target = afkManager.teleportTarget(args[1]);
        if (target == null) {
            player.sendMessage(messageManager.get("afk-zone-world-missing",
                    "&cA zóna nem található vagy a világa nincs betöltve: &f%s", args[1]));
            return;
        }
        player.teleportAsync(target).whenComplete((success, failure) ->
                player.getScheduler().run(plugin, task -> {
                    if (failure != null || !Boolean.TRUE.equals(success)) {
                        player.sendMessage(messageManager.get("afk-zone-tp-failed", "&cA teleport nem sikerült."));
                    } else {
                        player.sendMessage(messageManager.get("afk-zone-tp-success", "&aTeleport: &f%s", args[1]));
                    }
                }, null));
    }

    private void show(final Player player, final String[] args) {
        if (player == null) {
            return;
        }
        if (args.length < 2 || "selection".equalsIgnoreCase(args[1])) {
            final CuboidSelectionService.Result result = afkManager.showSelection(player);
            if (!result.ready()) {
                sendSelectionProblem(player, result);
            } else {
                player.sendMessage(messageManager.get("selection-preview", "&aA közös 3D kijelölés megjelenítve."));
            }
            return;
        }
        if (!afkManager.showZone(player, args[1])) {
            player.sendMessage(messageManager.get("afk-zone-unknown", "&cNincs ilyen AFK-zóna: &f%s", args[1]));
            return;
        }
        player.sendMessage(messageManager.get("afk-zone-preview", "&aAFK-zóna megjelenítve: &f%s", args[1]));
    }

    private void clear(final Player player) {
        if (player == null) {
            return;
        }
        selectionService.clear(player.getUniqueId());
        player.sendMessage(messageManager.get("selection-cleared", "&aA közös 3D kijelölés törölve."));
    }

    private void sendSelectionProblem(final Player player, final CuboidSelectionService.Result result) {
        if (result.status() == CuboidSelectionService.Status.TOO_LARGE) {
            player.sendMessage(messageManager.get("selection-too-large",
                    "&cA kijelölés túl nagy: &f%s&c blokk, maximum &f%s&c.",
                    result.cuboid() == null ? "?" : result.cuboid().volume(), result.maxVolume()));
        } else {
            player.sendMessage(messageManager.get("selection-incomplete",
                    "&cElőbb jelöld ki mindkét sarkot a meglévő /claim pos1 és /claim pos2 paranccsal vagy a birtokmérő pálcával."));
        }
    }

    private Player requirePlayer(final CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(messageManager.get("messages.player-only", "&cEhhez a művelethez játékos szükséges."));
        return null;
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("afk-zone-help-header", "&6/afkzone &7— natív AFK-zóna admin:"));
        sender.sendMessage(messageManager.get("afk-zone-help-main",
                "&ecreate <id> [név]&7, &ereplace <id>&7, &edelete <id>&7, &elist&7, &etp <id>&7, &eshow [id|selection]&7, &estatus [id]&7, &eclear"));
        sender.sendMessage(messageManager.get("afk-zone-help-selection",
                "&7A sarkokat a közös &e/claim pos1&7 és &e/claim pos2&7 (vagy a meglévő pálca) kezeli."));
    }

    private static String defaultError(final String key) {
        return switch (key) {
            case "afk-zone-invalid-id" -> "&cAz ID 2–32 karakteres kisbetű/szám/kötőjel/aláhúzás lehet.";
            case "afk-zone-exists" -> "&cMár létezik ilyen AFK-zóna: &f%s";
            case "afk-zone-unknown" -> "&cNincs ilyen AFK-zóna: &f%s";
            case "afk-zone-invalid-config" -> "&cA zóna biztonságosan nem tölthető be; lásd a konzolt.";
            case "afk-zone-save-failed" -> "&cA zóna nem menthető tartósan; a művelet megszakadt.";
            default -> "&cAz AFK-zóna művelet nem sikerült.";
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        if (!source.getSender().hasPermission(Permissions.AFK)) {
            return List.of();
        }
        final List<String> actions = List.of("create", "replace", "delete", "list", "tp", "show", "status", "clear");
        if (args.length <= 1) {
            final String prefix = prefixAt(args, 0);
            return actions.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        final String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("replace", "delete", "tp", "status").contains(action)) {
            final String prefix = prefixAt(args, 1);
            return afkManager.zoneIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }
        if (args.length == 2 && "show".equals(action)) {
            final String prefix = prefixAt(args, 1);
            final List<String> options = new ArrayList<>(afkManager.zoneIds());
            options.add("selection");
            return options.stream().filter(id -> id.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
