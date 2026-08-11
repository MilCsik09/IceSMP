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

    private FactionJoinSubcommand joinSubcommand;
    private FactionLeaveSubcommand leaveSubcommand;
    private FactionSetSubcommand setSubcommand;

    public void setSpecializationManager(final hu.taliann.icesmp.managers.SpecializationManager specializationManager) {
        if (joinSubcommand != null) {
            joinSubcommand.setSpecializationManager(specializationManager);
        }
        if (leaveSubcommand != null) {
            leaveSubcommand.setSpecializationManager(specializationManager);
        }
        if (setSubcommand != null) {
            setSubcommand.setSpecializationManager(specializationManager);
        }
    }

    public FactionCommand(final JavaPlugin plugin, final FactionManager factionManager, final SinManager sinManager,
                          final FactionTreasuryManager treasuryManager, final CurrencyManager currencyManager,
                          final KingManager kingManager, final RaidManager raidManager,
                          final TerritoryManager territoryManager, final ConfigManager configManager,
                          final hu.taliann.icesmp.managers.PlayerCaravanManager playerCaravanManager,
                          final hu.taliann.icesmp.managers.WarWindowManager warWindowManager,
                          final hu.taliann.icesmp.managers.CouncilManager councilManager,
                          final MessageManager messageManager) {
        super(messageManager, "faction", "&6/faction &7- elérhető parancsok:");
        final FactionJoinSubcommand joinSubcommand = new FactionJoinSubcommand(plugin, factionManager, sinManager,
                currencyManager, territoryManager, configManager, messageManager);
        final FactionLeaveSubcommand leaveSubcommand = new FactionLeaveSubcommand(plugin, factionManager, sinManager,
                currencyManager, territoryManager, configManager, messageManager);
        register(joinSubcommand);
        register(leaveSubcommand);
        this.joinSubcommand = joinSubcommand;
        this.leaveSubcommand = leaveSubcommand;
        final FactionSetSubcommand setSubcommand =
                new FactionSetSubcommand(plugin, factionManager, sinManager, messageManager);
        register(setSubcommand);
        this.setSubcommand = setSubcommand;
        final FactionTreasurySubcommand treasurySubcommand = new FactionTreasurySubcommand(treasuryManager, factionManager, currencyManager, kingManager, messageManager, configManager);
        treasurySubcommand.setCouncilManager(councilManager); // Menedék: Vének Tanácsa jog
        register(treasurySubcommand);
        register(new FactionDonateSubcommand(treasuryManager, factionManager, currencyManager, messageManager));
        register(new FactionKingSubcommand(kingManager, factionManager, treasuryManager, messageManager));
        register(new FactionRaidSubcommand(raidManager, kingManager, factionManager, territoryManager, messageManager));
        final hu.taliann.icesmp.commands.faction.FactionCaravanSubcommand caravanSubcommand =
                new hu.taliann.icesmp.commands.faction.FactionCaravanSubcommand(playerCaravanManager, kingManager, messageManager);
        caravanSubcommand.setCouncilManager(councilManager); // Menedék: Vének Tanácsa jog
        register(caravanSubcommand);
        register(new hu.taliann.icesmp.commands.faction.FactionWarSubcommand(warWindowManager, messageManager));
    }
}
