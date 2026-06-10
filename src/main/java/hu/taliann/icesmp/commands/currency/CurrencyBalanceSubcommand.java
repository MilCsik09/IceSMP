package hu.taliann.icesmp.commands.currency;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CurrencyBalanceSubcommand implements CurrencySubcommand {

    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public CurrencyBalanceSubcommand(final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "balance";
    }

    @Override
    public String description() {
        return messageManager.get("messages.currency-desc-balance", "Megmutatja a pénzedet.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.currency-usage-balance", "/currency balance [currency]");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(messageManager.get("messages.currency-balance-all", "&6Egyenlegeid: &e%s", currencyManager.describeBalances(player)));
            return true;
        }

        final FactionType currencyType = FactionType.fromInput(args[0]);
        if (currencyType == null) {
            sender.sendMessage(messageManager.get("messages.bank-unknown-currency", "&cIsmeretlen valuta."));
            return true;
        }

        sender.sendMessage(messageManager.get(
                "messages.currency-balance-single",
                "&6Egyenleged (&f%s&6): &e%s",
                currencyType.getDisplayName(),
                currencyManager.formatBalance(currencyManager.getBalance(player, currencyType))
        ));
        return true;
    }
}

