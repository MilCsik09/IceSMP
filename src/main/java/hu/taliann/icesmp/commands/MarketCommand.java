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
            case "auction" -> handleAuction(player, args);
            case "claim" -> handleClaim(player);
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

        if (!Double.isFinite(price) || price <= 0.0D) {
            player.sendMessage(messageManager.get("amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
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

    private void handleAuction(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("market-auction-usage",
                    "&cHasználat: /market auction <kikiáltási ár> [óra] [valuta] [buyout:<ár>]"));
            return;
        }

        // A buy-out is given as a keyword token (buyout:<ár> / bo:<ár>) so it never
        // collides positionally with the optional hours / currency arguments.
        double buyOut = 0.0D;
        final String[] positional = new String[args.length];
        int positionalCount = 0;
        positional[positionalCount++] = args[0];
        for (int i = 1; i < args.length; i++) {
            final String buyOutToken = parseBuyOutToken(args[i]);
            if (buyOutToken != null) {
                try {
                    buyOut = Double.parseDouble(buyOutToken);
                } catch (final NumberFormatException exception) {
                    player.sendMessage(messageManager.get("invalid-amount", "&cÉrvénytelen összeg."));
                    return;
                }
                if (!Double.isFinite(buyOut) || buyOut <= 0.0D) {
                    player.sendMessage(messageManager.get("amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
                    return;
                }
                continue;
            }
            positional[positionalCount++] = args[i];
        }

        if (positionalCount < 2) {
            player.sendMessage(messageManager.get("market-auction-usage",
                    "&cHasználat: /market auction <kikiáltási ár> [óra] [valuta] [buyout:<ár>]"));
            return;
        }

        final double startPrice;
        try {
            startPrice = Double.parseDouble(positional[1]);
        } catch (final NumberFormatException exception) {
            player.sendMessage(messageManager.get("invalid-amount", "&cÉrvénytelen összeg."));
            return;
        }

        if (!Double.isFinite(startPrice) || startPrice <= 0.0D) {
            player.sendMessage(messageManager.get("amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
            return;
        }

        double hours = 0.0D;
        int currencyArgIndex = 2;
        if (positionalCount >= 3) {
            try {
                hours = Double.parseDouble(positional[2]);
                currencyArgIndex = 3;
            } catch (final NumberFormatException exception) {
                // Second positional argument is not a number: treat it as the currency.
            }
        }
        if (hours < 0.0D || !Double.isFinite(hours)) {
            player.sendMessage(messageManager.get("invalid-amount", "&cÉrvénytelen összeg."));
            return;
        }

        CurrencyType currency = positionalCount > currencyArgIndex ? CurrencyType.fromInput(positional[currencyArgIndex]) : null;
        if (positionalCount > currencyArgIndex && currency == null) {
            player.sendMessage(messageManager.get("bank-unknown-currency", "&cIsmeretlen valuta típus."));
            return;
        }
        if (currency == null) {
            currency = CurrencyType.fromFactionType(factionManager.getFaction(player.getUniqueId()));
        }

        final String errorKey = marketManager.createAuction(player, startPrice, currency,
                (long) (hours * 3_600_000.0D), buyOut);
        if (errorKey != null) {
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            return;
        }

        if (buyOut > 0.0D) {
            player.sendMessage(messageManager.get(
                    "market-auction-success-buyout",
                    "&aAukció elindítva: kikiáltási ár &f%s %s&a, buy-out &f%s&a. A licitek a /market GUI-ból érkeznek.",
                    currencyManager.formatBalance(startPrice),
                    currency.getDisplayName(),
                    currencyManager.formatBalance(buyOut)
            ));
        } else {
            player.sendMessage(messageManager.get(
                    "market-auction-success",
                    "&aAukció elindítva: kikiáltási ár &f%s %s&a. A licitek a /market GUI-ból érkeznek.",
                    currencyManager.formatBalance(startPrice),
                    currency.getDisplayName()
            ));
        }
    }

    /** Returns the numeric part of a {@code buyout:<ár>} / {@code bo:<ár>} token, or null if not such a token. */
    private String parseBuyOutToken(final String arg) {
        final String lower = arg.toLowerCase(Locale.ROOT);
        if (lower.startsWith("buyout:")) {
            return arg.substring("buyout:".length());
        }
        if (lower.startsWith("bo:")) {
            return arg.substring("bo:".length());
        }
        return null;
    }

    private void handleClaim(final Player player) {
        final int delivered = marketManager.deliverPending(player);
        if (delivered == 0) {
            player.sendMessage(messageManager.get("market-claim-none", "&7Nincs átvehető tárgyad a piactéren."));
            return;
        }

        player.sendMessage(messageManager.get("market-claim-success", "&aÁtvettél &f%s &atárgyat a piactérről.", delivered));
    }

    private void handleCancel(final Player player) {
        final int cancelled = marketManager.cancelListings(player);
        if (cancelled == 0) {
            if (marketManager.hasLockedAuction(player.getUniqueId())) {
                player.sendMessage(messageManager.get("market-cancel-auction-locked",
                        "&cAz élő licites aukciód nem vonható vissza — várd meg a lejáratát."));
                return;
            }
            player.sendMessage(messageManager.get("market-cancel-none", "&7Nincs aktív tételed a piacon."));
            return;
        }

        player.sendMessage(messageManager.get("market-cancel-success", "&aVisszavontál &f%s &atételt a piacról.", cancelled));
        if (marketManager.hasLockedAuction(player.getUniqueId())) {
            player.sendMessage(messageManager.get("market-cancel-auction-locked",
                    "&cAz élő licites aukciód nem vonható vissza — várd meg a lejáratát."));
        }
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
        player.sendMessage(messageManager.get("market-help-auction", "&e/market auction <kikiáltási ár> [óra] [valuta] [buyout:<ár>] &7- Aukció indítása (buy-out ár opcionális)."));
        player.sendMessage(messageManager.get("market-help-bid", "&e(GUI) &7bal-katt: min. licit • jobb-katt: nagyobb licit • shift-katt: buy-out."));
        player.sendMessage(messageManager.get("market-help-claim", "&e/market claim &7- Megnyert / visszajáró tárgyak átvétele."));
        player.sendMessage(messageManager.get("market-help-cancel", "&e/market cancel &7- Saját tételeid visszavonása (licites aukció nem vonható vissza)."));
        player.sendMessage(messageManager.get("market-help-search", "&e/market search <szöveg> &7- Keresés a piacon."));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("browse", "sell", "auction", "claim", "cancel", "search").stream()
                    .filter(option -> option.startsWith(prefix)).toList();
        }

        final boolean sellCurrencyArg = args.length == 3 && "sell".equalsIgnoreCase(args[0]);
        final boolean auctionCurrencyArg = args.length == 4 && "auction".equalsIgnoreCase(args[0]);
        if (sellCurrencyArg || auctionCurrencyArg) {
            final String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            return Arrays.stream(CurrencyType.values())
                    .map(type -> type.name().toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }

        // Offer the buy-out keyword on any auction argument past the opening price.
        if ("auction".equalsIgnoreCase(args[0]) && args.length >= 3
                && "buyout:".startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))
                && !args[args.length - 1].isEmpty()) {
            return List.of("buyout:");
        }

        return List.of();
    }
}
