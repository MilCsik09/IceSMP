package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /quest — küldetések: list (elérhető), info (aktív + haladás), accept,
 * abandon; admin: complete <player> <quest>.
 */
public final class QuestCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "icesmp.admin.quest";

    private final QuestManager questManager;
    private final MessageManager messageManager;

    public QuestCommand(final QuestManager questManager, final MessageManager messageManager) {
        this.questManager = questManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender);
            case "accept" -> handleAccept(sender, args);
            case "abandon" -> handleAbandon(sender, args);
            case "complete" -> handleComplete(sender, args);
            default -> {
                sender.sendMessage(messageManager.get("quest-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    private void handleList(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        sender.sendMessage(messageManager.get("quest-list-header", "&6Elérhető küldetések:"));
        boolean any = false;
        for (final String questId : questManager.getQuestIds()) {
            if (questManager.getAcceptBlocker(player, questId) != null) {
                continue;
            }

            any = true;
            final ConfigurationSection quest = questManager.getQuestSection(questId);
            sender.sendMessage(messageManager.get(
                    "quest-list-line",
                    "&e%s &7(%s) - %s",
                    questManager.getDisplayName(questId),
                    questId,
                    quest == null ? "" : quest.getString("description", "")
            ));
        }

        if (!any) {
            sender.sendMessage(messageManager.get("quest-list-empty", "&7Jelenleg nincs felvehető küldetésed."));
        }
    }

    private void handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final List<String> active = questManager.getActiveQuests(player);
        sender.sendMessage(messageManager.get("quest-info-header", "&6Aktív küldetéseid (%s):", active.size()));
        for (final String questId : active) {
            sender.sendMessage(messageManager.get(
                    "quest-info-line",
                    "&e%s &7- haladás: &f%s&7/&f%s",
                    questManager.getDisplayName(questId),
                    questManager.getProgress(player, questId),
                    questManager.getObjectiveCount(questId)
            ));
        }

        sender.sendMessage(messageManager.get(
                "quest-info-completed",
                "&7Teljesített küldetések: &f%s",
                questManager.getCompletedQuests(player).size()
        ));
    }

    private void handleAccept(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("quest-accept-usage", "&cHasználat: /quest accept <küldetés>"));
            return;
        }

        final String blocker = questManager.getAcceptBlocker(player, args[1]);
        if (blocker != null) {
            sender.sendMessage(messageManager.get(blocker, defaultBlockerMessage(blocker)));
            return;
        }

        questManager.accept(player, args[1]);
        sender.sendMessage(messageManager.get(
                "quest-accept-success",
                "&aKüldetés felvéve: &e%s",
                questManager.getDisplayName(args[1])
        ));
    }

    private void handleAbandon(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 2 || !questManager.abandon(player, args[1])) {
            sender.sendMessage(messageManager.get("quest-abandon-failed", "&cNincs ilyen aktív küldetésed."));
            return;
        }

        sender.sendMessage(messageManager.get("quest-abandon-success", "&eKüldetés eldobva: &f%s", questManager.getDisplayName(args[1])));
    }

    private void handleComplete(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("quest-complete-usage", "&cHasználat: /quest complete <játékos> <küldetés>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("target-player-offline", "&cA célpont játékos nem elérhető."));
            return;
        }

        if (questManager.getQuestSection(args[2]) == null) {
            sender.sendMessage(messageManager.get("quest-unknown", "&cIsmeretlen küldetés: &f%s", args[2]));
            return;
        }

        questManager.complete(target, args[2]);
        sender.sendMessage(messageManager.get("quest-complete-success", "&aKüldetés lezárva: &f%s &7-> &e%s", target.getName(), args[2]));
    }

    private String defaultBlockerMessage(final String blocker) {
        return switch (blocker) {
            case "quest-unknown" -> "&cIsmeretlen küldetés.";
            case "quest-already-active" -> "&cEz a küldetés már aktív nálad.";
            case "quest-already-completed" -> "&cEzt a küldetést már teljesítetted.";
            case "quest-requires-job" -> "&cEhhez a küldetéshez másik kaszt szükséges.";
            case "quest-requires-faction" -> "&cEhhez a küldetéshez másik frakció tagjának kell lenned.";
            case "quest-requires-level" -> "&cMég nem vagy elég magas szintű ehhez a küldetéshez.";
            case "quest-requires-quest" -> "&cElőbb az előfeltétel-küldetést kell teljesítened.";
            default -> "&cA küldetés nem vehető fel.";
        };
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("quest-help-header", "&6/quest &7- Elérhető parancsok:"));
        sender.sendMessage(messageManager.get("quest-help-list", "&e/quest list &7- Felvehető küldetések."));
        sender.sendMessage(messageManager.get("quest-help-info", "&e/quest info &7- Aktív küldetéseid és haladásod."));
        sender.sendMessage(messageManager.get("quest-help-accept", "&e/quest accept <küldetés> &7- Küldetés felvétele."));
        sender.sendMessage(messageManager.get("quest-help-abandon", "&e/quest abandon <küldetés> &7- Küldetés eldobása."));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("quest-help-complete", "&e/quest complete <játékos> <küldetés> &7- Lezárás (Admin)."));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final List<String> subcommands = sender.hasPermission(ADMIN_PERMISSION)
                ? List.of("list", "info", "accept", "abandon", "complete")
                : List.of("list", "info", "accept", "abandon");

        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return subcommands.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && ("accept".equals(subcommand) || "abandon".equals(subcommand))) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("abandon".equals(subcommand) && sender instanceof Player player) {
                return questManager.getActiveQuests(player).stream().filter(id -> id.startsWith(prefix)).toList();
            }
            return questManager.getQuestIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }

        if (args.length == 2 && "complete".equals(subcommand)) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        if (args.length == 3 && "complete".equals(subcommand)) {
            final String prefix = args[2].toLowerCase(Locale.ROOT);
            return questManager.getQuestIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }

        return List.of();
    }

}
