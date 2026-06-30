package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.commands.currency.CurrencyBalanceSubcommand;
import hu.taliann.icesmp.commands.currency.CurrencyExchangeSubcommand;
import hu.taliann.icesmp.commands.currency.CurrencyPaySubcommand;
import hu.taliann.icesmp.commands.currency.CurrencyRatesSubcommand;
import hu.taliann.icesmp.commands.currency.CurrencySetSubcommand;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.ExchangeRateService;
import hu.taliann.icesmp.utils.MessageManager;

public final class CurrencyCommand extends AbstractDispatchCommand {

    public CurrencyCommand(final CurrencyManager currencyManager, final ConfigManager configManager,
                           final ExchangeRateService exchangeRateService, final MessageManager messageManager) {
        super(messageManager, "currency", "&6/currency &7- elérhető parancsok:");
        register(new CurrencyBalanceSubcommand(currencyManager, messageManager));
        register(new CurrencyPaySubcommand(currencyManager, messageManager));
        register(new CurrencySetSubcommand(currencyManager, messageManager));
        register(new CurrencyExchangeSubcommand(currencyManager, configManager, exchangeRateService, messageManager));
        register(new CurrencyRatesSubcommand(currencyManager, exchangeRateService, messageManager));
    }
}
