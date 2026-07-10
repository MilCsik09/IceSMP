package hu.taliann.icesmp.commands.bank;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BankDepositSubcommand implements BankSubcommand {

    private final CurrencyManager currencyManager;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;

    public BankDepositSubcommand(final CurrencyManager currencyManager, final ConfigManager configManager,
                                 final TerritoryManager territoryManager, final MessageManager messageManager) {
        this.currencyManager = currencyManager;
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "deposit";
    }

    @Override
    public String description() {
        return "beváltja a fizikai valuta itemeket.";
    }

    @Override
    public String usage() {
        return "/bank deposit";
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        // Banki ügyintézés csak fővárosban (a világban készpénz — token — jár kézről kézre).
        if (configManager.getBoolean("banking.capital-only", true)
                && !territoryManager.isInCapital(player.getLocation())) {
            sender.sendMessage(messageManager.get("messages.bank-capital-only",
                    "&cBanki ügyintézés csak a fővárosokban lehetséges — keresd fel valamelyik város bankját."));
            return true;
        }

        final double deposited = currencyManager.deposit(player);
        if (deposited <= 0.0D) {
            sender.sendMessage(messageManager.get("messages.bank-no-currency-items", "&eNem találtam beváltható valuta itemet a készletedben."));
            return true;
        }

        sender.sendMessage(messageManager.get("messages.bank-deposit-success", "&aSikeres beváltás: &f%s &avaluta került a bankba.", currencyManager.formatBalance(deposited)));
        return true;
    }
}
