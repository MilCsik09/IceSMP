package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.commands.faction.FactionDonateSubcommand;
import hu.taliann.icesmp.commands.faction.FactionJoinSubcommand;
import hu.taliann.icesmp.commands.faction.FactionKingSubcommand;
import hu.taliann.icesmp.commands.faction.FactionLeaveSubcommand;
import hu.taliann.icesmp.commands.faction.FactionRaidSubcommand;
import hu.taliann.icesmp.commands.faction.FactionSetSubcommand;
import hu.taliann.icesmp.commands.faction.FactionTreasurySubcommand;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.FactionTreasuryManager;
import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class FactionCommand extends AbstractDispatchCommand {

    public FactionCommand(final JavaPlugin plugin, final FactionManager factionManager, final SinManager sinManager,
                          final FactionTreasuryManager treasuryManager, final CurrencyManager currencyManager,
                          final KingManager kingManager, final RaidManager raidManager,
                          final TerritoryManager territoryManager, final ConfigManager configManager,
                          final hu.taliann.icesmp.managers.PlayerCaravanManager playerCaravanManager,
                          final hu.taliann.icesmp.managers.WarWindowManager warWindowManager,
                          final MessageManager messageManager) {
        super(messageManager, "faction", "&6/faction &7- elérhető parancsok:");
        register(new FactionJoinSubcommand(factionManager, sinManager, currencyManager, territoryManager, configManager, messageManager));
        register(new FactionLeaveSubcommand(factionManager, currencyManager, territoryManager, configManager, messageManager));
        register(new FactionSetSubcommand(plugin, factionManager, sinManager, messageManager));
        register(new FactionTreasurySubcommand(treasuryManager, factionManager, currencyManager, kingManager, messageManager, configManager));
        register(new FactionDonateSubcommand(treasuryManager, factionManager, currencyManager, messageManager));
        register(new FactionKingSubcommand(kingManager, factionManager, treasuryManager, messageManager));
        register(new FactionRaidSubcommand(raidManager, kingManager, factionManager, territoryManager, messageManager));
        register(new hu.taliann.icesmp.commands.faction.FactionCaravanSubcommand(playerCaravanManager, kingManager, messageManager));
        register(new hu.taliann.icesmp.commands.faction.FactionWarSubcommand(warWindowManager, messageManager));
    }
}
