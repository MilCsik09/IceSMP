package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.gui.CommandMenuContext;
import hu.taliann.icesmp.gui.CommandMenus;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/**
 * /achievements — opens the achievements GUI (milestones + rewards).
 */
public final class AchievementsCommand implements BasicCommand {

    private final CommandMenuContext menuContext;
    private final MessageManager messageManager;

    public AchievementsCommand(final CommandMenuContext menuContext, final MessageManager messageManager) {
        this.menuContext = menuContext;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        CommandMenus.openAchievements(player, menuContext);
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        return List.of();
    }
}
