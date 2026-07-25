package hu.taliann.icesmp.commands.currency;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.ExchangeRateService;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CurrencyExchangeSubcommand implements CurrencySubcommand {

    private final CurrencyManager currencyManager;
    private final ConfigManager configManager;
    private final ExchangeRateService exchangeRateService;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;

    public CurrencyExchangeSubcommand(final CurrencyManager currencyManager, final ConfigManager configManager,
                                      final ExchangeRateService exchangeRateService, final TerritoryManager territoryManager,
                                      final MessageManager messageManager) {
        this.currencyManager = currencyManager;
        this.configManager = configManager;
        this.exchangeRateService = exchangeRateService;
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "exchange";
    }

    @Override
    public String description() {
        return messageManager.get("messages.currency-desc-exchange", "Valuta váltása egy másikra.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.currency-usage-exchange", "/currency exchange <amount> <from> <to>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        // Valutaváltás csak fővárosban (mint minden banki ügyintézés).
        if (configManager.getBoolean("banking.capital-only", true)
                && !territoryManager.isInCapital(player.getLocation())) {
            sender.sendMessage(messageManager.get("messages.bank-capital-only",
                    "&cBanki ügyintézés csak a fővárosokban lehetséges — keresd fel valamelyik város bankját."));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("messages.currency-exchange-usage", "&cHasználat: %s", usage()));
            return true;
        }

        final long amount;
        try {
            amount = Long.parseLong(args[0]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("messages.invalid-amount", "&cÉrvénytelen összeg."));
            return true;
        }

        if (amount <= 0L) {
            sender.sendMessage(messageManager.get("messages.amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
            return true;
        }

        final FactionType fromType = FactionType.fromInput(args[1]);
        final FactionType toType = FactionType.fromInput(args[2]);
        if (fromType == null || toType == null) {
            sender.sendMessage(messageManager.get("messages.bank-unknown-currency", "&cIsmeretlen valuta."));
            return true;
        }

        if (fromType == toType) {
            sender.sendMessage(messageManager.get("messages.currency-exchange-same-currency", "&cNem válthatsz ugyanarra a valutára."));
            return true;
        }

        // Napi váltási keret: a tömeges kivét-visszaváltás árfolyam-manipuláció féke
        // (0 = kikapcsolva). A keret a forrás-összegben számol.
        final long dailyLimit = configManager.getLong("currency.dynamic-exchange.daily-limit", 200L);
        if (dailyLimit > 0L && hu.taliann.icesmp.utils.DailyBudget
                .spentTodayOnOwnThread(player, "exchange") + amount > dailyLimit) {
            sender.sendMessage(messageManager.get("messages.currency-exchange-daily-limit",
                    "&cA mai váltási kereted (%s) ehhez már kevés — holnap folytathatod.", String.valueOf(dailyLimit)));
            return true;
        }

        final double rate;
        if (exchangeRateService.isEnabled()) {
            rate = exchangeRateService.getRate(CurrencyType.fromFactionType(fromType), CurrencyType.fromFactionType(toType));
        } else {
            rate = configManager.getDouble("currency.exchange-rate", 1.0D);
        }
        final double feePercent = exchangeRateService.getFeePercent();
        if (rate <= 0.0D || feePercent < 0.0D) {
            sender.sendMessage(messageManager.get("messages.currency-exchange-config-invalid", "&cA valuta váltó beállításai hibásak."));
            return true;
        }

        final long received = currencyManager.exchange(player, fromType, toType, amount, rate, feePercent);
        if (received < 0L) {
            sender.sendMessage(messageManager.get("messages.currency-exchange-failed", "&cA váltás nem sikerült, valószínűleg nincs elég egyenleged."));
            return true;
        }
        // A keret csak a SIKERES váltás után könyvelődik (a meghiúsult kísérlet nem fogyaszt).
        hu.taliann.icesmp.utils.DailyBudget.tryConsumeOnOwnThread(player, "exchange", dailyLimit, amount);

        sender.sendMessage(messageManager.get(
                "messages.currency-exchange-success",
                "&aSikeres váltás: &e%s &f%s &7-> &a%s &f%s &7(| Árfolyam: %s, díj: %s%%)",
                amount,
                fromType.getDisplayName(),
                received,
                toType.getDisplayName(),
                String.format(Locale.ROOT, "%.3f", rate),
                feePercent
        ));
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        // Két hosszal kezeljük mindkét pozíciót: P (üres prefix, szóköz után) és P+1 (args[P] prefix);
        // a "from" pozíció (1) és a "to" pozíció (2) hossza 2-nél ütközik, ezt a from pontos
        // egyezése dönti el (az amount szabad szöveg, azt nem kell/lehet ellenőrizni).
        final boolean fromComplete = args.length >= 2 && FactionType.fromInput(args[1]) != null;

        if (args.length == 1 || (args.length == 2 && !fromComplete)) {
            final String prefix = prefixAt(args, 1);
            return Arrays.stream(FactionType.values())
                    .map(type -> type.name().toLowerCase())
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }

        if ((args.length == 2 && fromComplete) || args.length == 3) {
            final String prefix = prefixAt(args, 2);
            return Arrays.stream(FactionType.values())
                    .map(type -> type.name().toLowerCase())
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }

}

