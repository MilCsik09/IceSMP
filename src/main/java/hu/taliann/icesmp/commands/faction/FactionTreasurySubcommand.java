package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.FactionTreasuryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Faction treasury view + admin withdrawal:
 * /faction treasury — saját frakció kasszája (admin: mindegyik)
 * /faction treasury withdraw <összeg> — kivét a saját frakció kasszájából a bankodba (admin)
 */
public final class FactionTreasurySubcommand implements FactionSubcommand {

    private static final String ADMIN_PERMISSION = "icesmp.faction.admin";

    private final FactionTreasuryManager treasuryManager;
    private final FactionManager factionManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public FactionTreasurySubcommand(final FactionTreasuryManager treasuryManager, final FactionManager factionManager,
                                     final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.treasuryManager = treasuryManager;
        this.factionManager = factionManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "treasury";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-treasury", "Frakciókassza megtekintése.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-treasury", "/faction treasury [withdraw <összeg>]");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        if (args.length >= 2 && "withdraw".equalsIgnoreCase(args[0])) {
            return handleWithdraw(player, args[1]);
        }

        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.faction-treasury-header", "&6Frakciókasszák:"));
            for (final FactionType faction : FactionType.values()) {
                sender.sendMessage(messageManager.get(
                        "messages.faction-treasury-line",
                        "&e%s&7: &f%s",
                        faction.getDisplayName(),
                        currencyManager.formatBalance(treasuryManager.getBalance(faction))
                ));
            }
            return true;
        }

        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        sender.sendMessage(messageManager.get(
                "messages.faction-treasury-own",
                "&6A(z) &f%s &6frakció kasszája: &f%s",
                faction.getDisplayName(),
                currencyManager.formatBalance(treasuryManager.getBalance(faction))
        ));
        return true;
    }

    private boolean handleWithdraw(final Player player, final String rawAmount) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        final double amount;
        try {
            amount = Double.parseDouble(rawAmount);
        } catch (final NumberFormatException exception) {
            player.sendMessage(messageManager.get("messages.invalid-amount", "&cÉrvénytelen összeg."));
            return true;
        }

        if (amount <= 0.0D) {
            player.sendMessage(messageManager.get("messages.amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
            return true;
        }

        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        if (!treasuryManager.withdraw(faction, amount)) {
            player.sendMessage(messageManager.get("messages.faction-treasury-insufficient", "&cNincs ennyi a frakciókasszában."));
            return true;
        }

        currencyManager.addToBalance(player.getUniqueId(), CurrencyType.fromFactionType(faction), amount);
        player.sendMessage(messageManager.get(
                "messages.faction-treasury-withdraw-success",
                "&aKivét a kasszából: &f%s %s &7(a bankodba került).",
                currencyManager.formatBalance(amount),
                faction.getDisplayName()
        ));
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1 && sender.hasPermission(ADMIN_PERMISSION)) {
            return "withdraw".startsWith(args[0].toLowerCase()) ? List.of("withdraw") : List.of();
        }
        return List.of();
    }
}
