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
 * /faction donate <összeg> — a saját frakcióvaluta banki egyenlegéből adomány
 * a frakciókasszába (money sink + a leendő király hadi kasszája).
 */
public final class FactionDonateSubcommand implements FactionSubcommand {

    private final FactionTreasuryManager treasuryManager;
    private final FactionManager factionManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public FactionDonateSubcommand(final FactionTreasuryManager treasuryManager, final FactionManager factionManager,
                                   final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.treasuryManager = treasuryManager;
        this.factionManager = factionManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "donate";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-donate", "Adomány a frakciókasszába.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-donate", "/faction donate <összeg>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageManager.get("messages.faction-donate-usage", "&cHasználat: %s", usage()));
            return true;
        }

        final double amount;
        try {
            amount = Double.parseDouble(args[0]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("messages.invalid-amount", "&cÉrvénytelen összeg."));
            return true;
        }

        if (!Double.isFinite(amount) || amount <= 0.0D) {
            sender.sendMessage(messageManager.get("messages.amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
            return true;
        }

        final FactionType faction = factionManager.getChosenFaction(player.getUniqueId()).orElse(null);
        if (faction == null) {
            sender.sendMessage(messageManager.get("messages.faction-choose-first",
                    "&cFrakciókasszához előbb válassz frakciót: &f/faction join <frakció>&c."));
            return true;
        }
        final CurrencyType currency = CurrencyType.fromFactionType(faction);
        if (!currencyManager.deductFromBalance(player.getUniqueId(), currency, amount)) {
            sender.sendMessage(messageManager.get("messages.currency-insufficient-balance", "&cNincs elég pénzed a művelethez."));
            return true;
        }

        treasuryManager.deposit(faction, amount);
        sender.sendMessage(messageManager.get(
                "messages.faction-donate-success",
                "&aAdomány elküldve: &f%s %s &7-> a(z) &f%s &7frakció kasszája (új egyenleg: &f%s&7).",
                currencyManager.formatBalance(amount),
                currency.getDisplayName(),
                faction.getDisplayName(),
                currencyManager.formatBalance(treasuryManager.getBalance(faction))
        ));
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        return List.of();
    }
}
