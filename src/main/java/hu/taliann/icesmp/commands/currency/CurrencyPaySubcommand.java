package hu.taliann.icesmp.commands.currency;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public final class CurrencyPaySubcommand implements CurrencySubcommand {

    private final CurrencyManager currencyManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public CurrencyPaySubcommand(final CurrencyManager currencyManager, final ConfigManager configManager,
                                 final MessageManager messageManager) {
        this.currencyManager = currencyManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "pay";
    }

    @Override
    public String description() {
        return messageManager.get("messages.currency-desc-pay", "Pénz küldése egy másik játékosnak.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.currency-usage-pay", "/currency pay <player> <amount> [currency]");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        // A közvetlen számla-utalás a KP-alapú világban alapból tiltott: a player-player
        // kereskedelem kézből kézbe, tokennel (vagy a piacon) zajlik.
        if (!configManager.getBoolean("banking.pay-enabled", false)) {
            sender.sendMessage(messageManager.get("messages.currency-pay-disabled",
                    "&cA közvetlen utalás nincs engedélyezve — kereskedj kézből kézbe tokenekkel, vagy a piacon."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("messages.currency-pay-usage", "&cHasználat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA céljátékos nem elérhető online."));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(messageManager.get("messages.currency-pay-self",
                    "&cSaját magadnak nem utalhatsz."));
            return true;
        }
        final long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("messages.invalid-amount", "&cÉrvénytelen összeg."));
            return true;
        }

        if (amount <= 0L) {
            sender.sendMessage(messageManager.get("messages.amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
            return true;
        }

        final FactionType currencyType = resolveCurrency(args);
        if (currencyType == null) {
            sender.sendMessage(messageManager.get("messages.bank-unknown-currency", "&cIsmeretlen valuta."));
            return true;
        }

        if (!currencyManager.transfer(player, target, currencyType, amount)) {
            sender.sendMessage(messageManager.get("messages.currency-insufficient-balance", "&cNincs elég egyenleged."));
            return true;
        }

        sender.sendMessage(messageManager.get(
                "messages.currency-pay-success",
                "&aSikeresen elküldtél &e%s &f%s &apénzt &f%s&a játékosnak.",
                amount,
                currencyType.getDisplayName(),
                target.getName()
        ));
        return true;
    }

    private FactionType resolveCurrency(final String[] args) {
        if (args.length < 3) {
            return currencyManager.getDefaultCurrencyType();
        }

        return FactionType.fromInput(args[2]);
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 || args.length == 3) {
            final String prefix = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
            return Arrays.stream(FactionType.values())
                    .map(type -> type.name().toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}


