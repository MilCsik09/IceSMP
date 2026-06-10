package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.gui.ProfileGUI;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;

@NullMarked
public final class ProfileCommand implements BasicCommand {

    private final MessageManager messageManager;
    private final MetelytepoManager metelytepoManager;

    public ProfileCommand(final MessageManager messageManager, final MetelytepoManager metelytepoManager) {
        this.messageManager = messageManager;
        this.metelytepoManager = metelytepoManager;
    }

    @Override
    public void execute(final CommandSourceStack commandSourceStack, final String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }

        ProfileGUI.openProfile(player, player, metelytepoManager, messageManager);
    }

    @Override
    public Collection<String> suggest(final CommandSourceStack commandSourceStack, final String[] args) {
        return List.of();
    }
}
