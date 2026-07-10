package hu.taliann.icesmp.commands.bank;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Stream;

public final class BankWithdrawSubcommand implements BankSubcommand {

    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public BankWithdrawSubcommand(final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "withdraw";
    }

    @Override
    public String description() {
        return "fizikai valutát ad vissza.";
    }

    @Override
    public String usage() {
        return "/bank withdraw <red|blue|neutral> <amount>";
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        // The base strips the subcommand token, so args here are [currency, amount].
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("messages.bank-withdraw-usage", "&cHasználat: /bank withdraw <red|blue|neutral> <amount>"));
            return true;
        }

        final FactionType currencyType = FactionType.fromInput(args[0]);
        if (currencyType == null) {
            sender.sendMessage(messageManager.get("messages.bank-unknown-currency", "&cIsmeretlen valuta."));
            return true;
        }

        final int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("messages.invalid-amount", "&cÉrvénytelen összeg."));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(messageManager.get("messages.amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
            return true;
        }

        if (!currencyManager.withdraw(player, currencyType, amount)) {
            sender.sendMessage(messageManager.get("messages.bank-insufficient-balance", "&cNincs elég bankegyenleged ehhez."));
            return true;
        }

        sender.sendMessage(messageManager.get("messages.bank-withdraw-success", "&aSikeres kivétel: &e%s &f%s &avaluta vissza lett adva itemként.", currencyManager.formatBalance(amount), currencyType.getDisplayName()));
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        // Két hosszal kezeljük: 0 = "/bank withdraw " (üres prefix), 1 = gépelés közben (args[0] prefix).
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return Stream.of("red", "blue", "neutral")
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }
}
