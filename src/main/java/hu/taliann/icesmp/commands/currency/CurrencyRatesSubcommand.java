package hu.taliann.icesmp.commands.currency;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.ExchangeRateService;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * Shows the current dynamic exchange rates: per-currency supply, value index,
 * and the rate against the server default currency.
 */
public final class CurrencyRatesSubcommand implements CurrencySubcommand {

    private final CurrencyManager currencyManager;
    private final ExchangeRateService exchangeRateService;
    private final MessageManager messageManager;

    public CurrencyRatesSubcommand(final CurrencyManager currencyManager, final ExchangeRateService exchangeRateService,
                                   final MessageManager messageManager) {
        this.currencyManager = currencyManager;
        this.exchangeRateService = exchangeRateService;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "rates";
    }

    @Override
    public String description() {
        return messageManager.get("messages.currency-desc-rates", "Aktuális árfolyamok megtekintése.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.currency-usage-rates", "/currency rates");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!exchangeRateService.isEnabled()) {
            sender.sendMessage(messageManager.get("messages.currency-rates-static",
                    "&eA dinamikus árfolyam ki van kapcsolva; fix árfolyam érvényes."));
            return true;
        }

        sender.sendMessage(messageManager.get("messages.currency-rates-header", "&6Aktuális árfolyamok (kínálat alapú):"));

        final CurrencyType reference = currencyManager.getDefaultCurrency();
        for (final CurrencyType currencyType : CurrencyType.values()) {
            final double supply = currencyManager.getTotalSupply(currencyType);
            final double value = exchangeRateService.getValue(currencyType);
            final double rate = exchangeRateService.getRate(currencyType, reference);

            sender.sendMessage(messageManager.get(
                    "messages.currency-rates-line",
                    "&e%s &7| Kínálat: &f%s &7| Érték: &f%s &7| 1 egység = &f%s %s",
                    currencyType.getDisplayName(),
                    currencyManager.formatBalance(supply),
                    String.format(Locale.ROOT, "%.3f", value),
                    String.format(Locale.ROOT, "%.3f", rate),
                    reference.getDisplayName()
            ));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        return List.of();
    }
}
