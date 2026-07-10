package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RelicCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "icesmp.relic.admin";

    private final RelicManager relicManager;
    private final MessageManager messageManager;

    public RelicCommand(final RelicManager relicManager, final MessageManager messageManager) {
        this.relicManager = relicManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (!relicManager.isEnabled()) {
            sender.sendMessage(messageManager.get("messages.relic-disabled", "&cRelic system is disabled."));
            return;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender);
            case "give" -> handleGive(sender, args);
            default -> {
                sender.sendMessage(messageManager.get("messages.relic-unknown-subcommand", "&cUnknown subcommand: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final String first = prefixAt(args, 0);
        final boolean firstComplete = "list".equals(first) || "give".equals(first);

        // Két hosszal: 0 = "/relic " (üres prefix), 1 = gépelés közben — kivéve, ha az args[0]
        // már pontos egyezés, akkor a P=1 (give esetén játékosnév) pozíció javaslatai jönnek.
        if (args.length == 0 || (args.length == 1 && !firstComplete)) {
            return Stream.of("list", "give").filter(name -> name.startsWith(first)).toList();
        }

        final boolean targetComplete = args.length >= 2 && Bukkit.getPlayerExact(args[1]) != null;
        if ("give".equals(first) && (args.length == 1 || (args.length == 2 && !targetComplete))) {
            final String prefix = prefixAt(args, 1);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }

        if ("give".equals(first) && ((args.length == 2 && targetComplete) || args.length == 3)) {
            final String prefix = prefixAt(args, 2);
            return relicManager.getDefinitions().stream()
                    .map(RelicDefinition::id)
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }

    /** Az adott pozíción gépelés alatt álló szó (kisbetűsítve), vagy üres, ha még el sem kezdték. */
    private static String prefixAt(final String[] args, final int index) {
        return args.length > index ? args[index].toLowerCase() : "";
    }

    private void handleList(final CommandSender sender) {
        final String relicList = relicManager.getDefinitions().stream()
                .map(def -> {
                    final String suffix = def.description().isBlank() ? "" : " &8(" + def.description() + ")";
                    return "&e" + def.id() + " &7- " + def.displayColor() + def.displayName() + suffix;
                })
                .collect(Collectors.joining("&7, &e", "&e", ""));

        if (relicList.isBlank()) {
            sender.sendMessage(messageManager.get("messages.relic-list-empty", "&eNo relic definitions are registered."));
            return;
        }

        sender.sendMessage(messageManager.get("messages.relic-list-header", "&6Registered relics: %s", relicList));
    }

    private void handleGive(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.relic-no-permission", "&cNo permission. Required: &f%s", ADMIN_PERMISSION));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("messages.relic-give-usage", "&cUsage: /relic give <player> <relicId> [amount]"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cPlayer not found or offline."));
            return;
        }

        final int amount;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (final NumberFormatException exception) {
                sender.sendMessage(messageManager.get("messages.invalid-amount", "&cAmount must be a number."));
                return;
            }
        } else {
            amount = 1;
        }

        if (amount <= 0) {
            sender.sendMessage(messageManager.get("messages.amount-must-be-positive", "&cAmount must be positive."));
            return;
        }

        if (!relicManager.isEnabled()) {
            sender.sendMessage(messageManager.get("messages.relic-disabled", "&cRelic system is disabled."));
            return;
        }

        if (relicManager.getDefinition(args[2]) == null) {
            sender.sendMessage(messageManager.get("messages.relic-unknown-id", "&cUnknown relic id: &f%s", args[2]));
            sender.sendMessage(messageManager.get("messages.relic-list-hint", "&7Use &f/relic list &7to see loaded relic ids."));
            return;
        }

        if (!relicManager.giveRelic(target, args[2], amount)) {
            sender.sendMessage(messageManager.get("messages.relic-give-failed", "&cCould not give relic item."));
            return;
        }

        sender.sendMessage(messageManager.get("messages.relic-give-success", "&aGiven &f%s&a relic item(s) of &f%s&a to &f%s&a.", amount, args[2], target.getName()));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("messages.relic-help-header", "&6/relic &7- available commands:"));
        sender.sendMessage(messageManager.get("messages.relic-help-list", "&e/relic list &7- lists registered relic placeholders."));
        sender.sendMessage(messageManager.get("messages.relic-help-give", "&e/relic give <player> <relicId> [amount] &7- gives test relic items."));
    }
}

