package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.gui.MarketGUI;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.MarketManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /market — piactér: (arg nélkül) böngészés GUI-ban;
 * sell <ár> [valuta] — a kézben tartott tárgy listázása;
 * cancel — saját tételek visszavonása.
 */
public final class MarketCommand implements BasicCommand {

    private final MarketManager marketManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;

    public MarketCommand(final MarketManager marketManager, final CurrencyManager currencyManager,
                         final FactionManager factionManager, final MessageManager messageManager) {
        this.marketManager = marketManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length == 0 || "browse".equalsIgnoreCase(args[0])) {
            MarketGUI.open(player, marketManager, currencyManager, messageManager);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> handleSell(player, args);
            case "cancel" -> handleCancel(player);
            case "search" -> handleSearch(player, args);
            default -> sendHelp(player);
        }
    }

    private void handleSell(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("market-sell-usage", "&cHasználat: /market sell <ár> [valuta]"));
            return;
        }

        final double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (final NumberFormatException exception) {
            player.sendMessage(messageManager.get("invalid-amount", "&cÉrvénytelen összeg."));
            return;
        }

        CurrencyType currency = args.length >= 3 ? CurrencyType.fromInput(args[2]) : null;
        if (args.length >= 3 && currency == null) {
            player.sendMessage(messageManager.get("bank-unknown-currency", "&cIsmeretlen valuta típus."));
            return;
        }
        if (currency == null) {
            currency = CurrencyType.fromFactionType(factionManager.getFaction(player.getUniqueId()));
        }

        final String errorKey = marketManager.createListing(player, price, currency);
        if (errorKey != null) {
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            return;
        }

        player.sendMessage(messageManager.get(
                "market-sell-success",
                "&aTárgy listázva a piacon: &f%s %s &7(visszavonás: /market cancel).",
                currencyManager.formatBalance(price),
                currency.getDisplayName()
        ));
    }

    private void handleCancel(final Player player) {
        final int cancelled = marketManager.cancelListings(player);
        if (cancelled == 0) {
            player.sendMessage(messageManager.get("market-cancel-none", "&7Nincs aktív tételed a piacon."));
            return;
        }

        player.sendMessage(messageManager.get("market-cancel-success", "&aVisszavontál &f%s &atételt a piacról.", cancelled));
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "market-no-item" -> "&cNincs tárgy a kezedben.";
            case "market-too-many-listings" -> "&cElérted a maximális tétel-számot a piacon.";
            case "amount-must-be-positive" -> "&cAz összegnek pozitívnak kell lennie.";
            default -> "&cA listázás nem sikerült.";
        };
    }

    private void handleSearch(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("market-search-usage", "&cHasználat: /market search <szöveg>"));
            return;
        }
        final String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        MarketGUI.open(player, marketManager, currencyManager, messageManager, 0, query);
    }

    private void sendHelp(final Player player) {
        player.sendMessage(messageManager.get("market-help-header", "&6/market &7- Elérhető parancsok:"));
        player.sendMessage(messageManager.get("market-help-browse", "&e/market &7- Piactér böngészése."));
        player.sendMessage(messageManager.get("market-help-sell", "&e/market sell <ár> [valuta] &7- A kezedben lévő tárgy listázása."));
        player.sendMessage(messageManager.get("market-help-cancel", "&e/market cancel &7- Saját tételeid visszavonása."));
        player.sendMessage(messageManager.get("market-help-search", "&e/market search <szöveg> &7- Keresés a piacon."));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("browse", "sell", "cancel", "search").stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (args.length == 3 && "sell".equalsIgnoreCase(args[0])) {
            final String prefix = args[2].toLowerCase(Locale.ROOT);
            return Arrays.stream(CurrencyType.values())
                    .map(type -> type.name().toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }
}
