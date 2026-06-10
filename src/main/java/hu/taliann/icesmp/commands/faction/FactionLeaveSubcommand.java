package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FactionLeaveSubcommand implements FactionSubcommand {

    private final FactionManager factionManager;
    private final MessageManager messageManager;

    public FactionLeaveSubcommand(final FactionManager factionManager, final MessageManager messageManager) {
        this.factionManager = factionManager;
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

        factionManager.removeFaction(player.getUniqueId());
        sender.sendMessage(messageManager.get("messages.faction-left", "&eKiléptél a frakciódból."));
        return true;
    }
}



