package hu.taliann.icesmp.commands.currency;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public final class CurrencySetSubcommand implements CurrencySubcommand {

    private static final String PERMISSION = "icesmp.currency.admin";

    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public CurrencySetSubcommand(final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "set";
    }

    @Override
    public String description() {
        return messageManager.get("messages.currency-desc-set", "Játékos egyenlegének beállítása (admin).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.currency-usage-set", "/currency set <player> <amount> [currency]");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("messages.currency-set-usage", "&cHasználat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA céljátékos nem elérhető online."));
            return true;
        }
        final long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("messages.invalid-amount", "&cÉrvénytelen összeg."));
            return true;
        }

        if (amount < 0L) {
            sender.sendMessage(messageManager.get("messages.amount-cannot-be-negative", "&cAz összeg nem lehet negatív."));
            return true;
        }

        final FactionType currencyType = resolveCurrency(args);
        if (currencyType == null) {
            sender.sendMessage(messageManager.get("messages.bank-unknown-currency", "&cIsmeretlen valuta."));
            return true;
        }

        currencyManager.setBalance(target, currencyType, amount);
        sender.sendMessage(messageManager.get(
                "messages.currency-set-success",
                "&aEgyenleg beállítva: &f%s &7-> &e%s &f%s",
                target.getName(),
                amount,
                currencyType.getDisplayName()
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


