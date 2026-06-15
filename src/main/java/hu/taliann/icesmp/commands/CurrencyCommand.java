package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.commands.currency.CurrencyBalanceSubcommand;
import hu.taliann.icesmp.commands.currency.CurrencyExchangeSubcommand;
import hu.taliann.icesmp.commands.currency.CurrencyPaySubcommand;
import hu.taliann.icesmp.commands.currency.CurrencyRatesSubcommand;
import hu.taliann.icesmp.commands.currency.CurrencySetSubcommand;
import hu.taliann.icesmp.commands.currency.CurrencySubcommand;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.ExchangeRateService;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CurrencyCommand implements BasicCommand {

    private final Map<String, CurrencySubcommand> subcommands = new LinkedHashMap<>();
    private final MessageManager messageManager;

    public CurrencyCommand(final CurrencyManager currencyManager, final ConfigManager configManager,
                           final ExchangeRateService exchangeRateService, final MessageManager messageManager) {
        this.messageManager = messageManager;
        register(new CurrencyBalanceSubcommand(currencyManager, messageManager));
        register(new CurrencyPaySubcommand(currencyManager, messageManager));
        register(new CurrencySetSubcommand(currencyManager, messageManager));
        register(new CurrencyExchangeSubcommand(currencyManager, configManager, exchangeRateService, messageManager));
        register(new CurrencyRatesSubcommand(currencyManager, exchangeRateService, messageManager));
    }

    private void register(final CurrencySubcommand subcommand) {
        subcommands.put(subcommand.name().toLowerCase(), subcommand);
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        final CurrencySubcommand subcommand = subcommands.get(args[0].toLowerCase());
        if (subcommand == null) {
            sender.sendMessage(messageManager.get("messages.currency-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
            sendHelp(sender);
            return;
        }

        final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        subcommand.execute(sender, subArgs);
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            return subcommands.keySet().stream().toList();
        }

        if (args.length == 1) {
            return subcommands.keySet().stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        final CurrencySubcommand subcommand = subcommands.get(args[0].toLowerCase());
        if (subcommand == null) {
            return List.of();
        }

        final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subcommand.tabComplete(sender, subArgs);
    }

    private void sendHelp(final CommandSender sender) {
        final String header = messageManager.get("messages.currency-help-header", "&6/currency &7- elérhető parancsok:");
        sender.sendMessage(header);
        for (final CurrencySubcommand subcommand : subcommands.values()) {
            sender.sendMessage(messageManager.get(
                    "messages.currency-help-" + subcommand.name(),
                    "&e" + subcommand.usage() + " &7- " + subcommand.description()
            ));
        }
    }
}

