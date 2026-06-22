package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.commands.faction.FactionDonateSubcommand;
import hu.taliann.icesmp.commands.faction.FactionJoinSubcommand;
import hu.taliann.icesmp.commands.faction.FactionKingSubcommand;
import hu.taliann.icesmp.commands.faction.FactionLeaveSubcommand;
import hu.taliann.icesmp.commands.faction.FactionRaidSubcommand;
import hu.taliann.icesmp.commands.faction.FactionSetSubcommand;
import hu.taliann.icesmp.commands.faction.FactionTreasurySubcommand;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.FactionTreasuryManager;
import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.utils.MessageManager;

public final class FactionCommand extends AbstractDispatchCommand {

    public FactionCommand(final FactionManager factionManager, final MetelytepoManager metelytepoManager,
                          final FactionTreasuryManager treasuryManager, final CurrencyManager currencyManager,
                          final KingManager kingManager, final RaidManager raidManager,
                          final MessageManager messageManager) {
        super(messageManager, "faction", "&6/faction &7- elérhető parancsok:");
        register(new FactionJoinSubcommand(factionManager, metelytepoManager, messageManager));
        register(new FactionLeaveSubcommand(factionManager, messageManager));
        register(new FactionSetSubcommand(factionManager, metelytepoManager, messageManager));
        register(new FactionTreasurySubcommand(treasuryManager, factionManager, currencyManager, kingManager, messageManager));
        register(new FactionDonateSubcommand(treasuryManager, factionManager, currencyManager, messageManager));
        register(new FactionKingSubcommand(kingManager, factionManager, treasuryManager, messageManager));
        register(new FactionRaidSubcommand(raidManager, kingManager, factionManager, messageManager));
    }
}
