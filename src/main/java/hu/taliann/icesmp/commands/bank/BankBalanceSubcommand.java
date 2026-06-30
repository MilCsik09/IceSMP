package hu.taliann.icesmp.commands.bank;

import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BankBalanceSubcommand implements BankSubcommand {

    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public BankBalanceSubcommand(final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "balance";
    }

    @Override
    public String description() {
        return "megmutatja az összes egyenleget.";
    }

    @Override
    public String usage() {
        return "/bank balance";
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        sender.sendMessage(messageManager.get(
                "messages.bank-balance-all",
                "&6Egyenlegeid: &e%s",
                currencyManager.describeBalances(player)
        ));
        return true;
    }
}
