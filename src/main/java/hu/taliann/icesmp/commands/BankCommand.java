package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public final class BankCommand implements BasicCommand {

    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public BankCommand(final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        final String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "balance" -> handleBalance(sender);
            case "deposit" -> handleDeposit(sender);
            case "withdraw" -> handleWithdraw(sender, args);
            default -> {
                sender.sendMessage(messageManager.get("messages.bank-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length == 0) {
            return List.of("balance", "deposit", "withdraw");
        }

        if (args.length == 1) {
            return Stream.of("balance", "deposit", "withdraw")
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && "withdraw".equalsIgnoreCase(args[0])) {
            final String prefix = args[1].toLowerCase();
            return Stream.of("red", "blue", "neutral")
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }

    private void handleBalance(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }

        sender.sendMessage(messageManager.get(
                "messages.bank-balance-all",
                "&6Egyenlegeid: &e%s",
                currencyManager.describeBalances(player)
        ));
    }

    private void handleDeposit(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }

        final double deposited = currencyManager.deposit(player);
        if (deposited <= 0.0D) {
            sender.sendMessage(messageManager.get("messages.bank-no-currency-items", "&eNem találtam beváltható valuta itemet a készletedben."));
            return;
        }

        sender.sendMessage(messageManager.get("messages.bank-deposit-success", "&aSikeres beváltás: &f%s &avaluta került a bankba.", currencyManager.formatBalance(deposited)));
    }

    private void handleWithdraw(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("messages.bank-withdraw-usage", "&cHasználat: /bank withdraw <red|blue|neutral> <amount>"));
            return;
        }

        final FactionType currencyType = FactionType.fromInput(args[1]);
        if (currencyType == null) {
            sender.sendMessage(messageManager.get("messages.bank-unknown-currency", "&cIsmeretlen valuta."));
            return;
        }

        final int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("messages.invalid-amount", "&cÉrvénytelen összeg."));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(messageManager.get("messages.amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
            return;
        }

        if (!currencyManager.withdraw(player, currencyType, amount)) {
            sender.sendMessage(messageManager.get("messages.bank-insufficient-balance", "&cNincs elég bankegyenleged ehhez."));
            return;
        }

        sender.sendMessage(messageManager.get("messages.bank-withdraw-success", "&aSikeres kivétel: &e%s &f%s &avaluta vissza lett adva itemként.", currencyManager.formatBalance(amount), currencyType.getDisplayName()));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("messages.bank-help-header", "&6/bank &7- elérhető parancsok:"));
        sender.sendMessage(messageManager.get("messages.bank-help-balance", "&e/bank balance &7- megmutatja az összes egyenleget."));
        sender.sendMessage(messageManager.get("messages.bank-help-deposit", "&e/bank deposit &7- beváltja a fizikai valuta itemeket."));
        sender.sendMessage(messageManager.get("messages.bank-help-withdraw", "&e/bank withdraw <red|blue|neutral> <amount> &7- fizikai valutát ad vissza."));
    }
}


