package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FactionLeaveSubcommand implements FactionSubcommand {

    private final FactionManager factionManager;
    private final CurrencyManager currencyManager;
    private final TerritoryManager territoryManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public FactionLeaveSubcommand(final FactionManager factionManager, final CurrencyManager currencyManager,
                                  final TerritoryManager territoryManager, final ConfigManager configManager,
                                  final MessageManager messageManager) {
        this.factionManager = factionManager;
        this.currencyManager = currencyManager;
        this.territoryManager = territoryManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "leave";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-leave", "Kilépés a jelenlegi frakcióból.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-leave", "/faction leave");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        // A kilépés szabályos frakcióváltásnak számít Semlegesbe: ugyanaz a főváros-kapu,
        // ár és cooldown vonatkozik rá, mint a /faction join váltásra — különben a
        // leave+join páros ingyenes, kapu nélküli kerülőút lenne.
        final FactionType currentFaction = factionManager.getFaction(player.getUniqueId());
        final boolean leavingKingdom = factionManager.hasChosenFaction(player.getUniqueId())
                && currentFaction != FactionType.NEUTRAL;
        if (leavingKingdom) {
            if (!FactionSwitchRules.passesNeutralCapitalGate(player, territoryManager, configManager, messageManager)) {
                return true;
            }
            if (!FactionSwitchRules.passesSeasonRules(player, factionManager, messageManager)) {
                return true;
            }
            if (!FactionSwitchRules.chargeSwitch(player, currentFaction, factionManager, currencyManager, messageManager)) {
                return true;
            }
        }

        factionManager.removeFaction(player.getUniqueId());
        sender.sendMessage(messageManager.get("messages.faction-left", "&eKiléptél a frakciódból."));
        return true;
    }
}



