package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.commands.bank.BankBalanceSubcommand;
import hu.taliann.icesmp.commands.bank.BankDepositSubcommand;
import hu.taliann.icesmp.commands.bank.BankWithdrawSubcommand;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;

public final class BankCommand extends AbstractDispatchCommand {

    public BankCommand(final CurrencyManager currencyManager, final ConfigManager configManager,
                       final TerritoryManager territoryManager, final MessageManager messageManager) {
        super(messageManager, "bank", "&6/bank &7- elérhető parancsok:");
        register(new BankBalanceSubcommand(currencyManager, messageManager));
        register(new BankDepositSubcommand(currencyManager, configManager, territoryManager, messageManager));
        register(new BankWithdrawSubcommand(currencyManager, configManager, territoryManager, messageManager));
    }
}
