package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.gui.QuestLogGUI;
import hu.taliann.icesmp.gui.QuestLogHolder;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/**
 * /daily — canonical shortcut into the authored quest journal.
 */
public final class DailyCommand implements BasicCommand {

    private final QuestManager questManager;
    private final MessageManager messageManager;

    public DailyCommand(final QuestManager questManager, final MessageManager messageManager) {
        this.questManager = questManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        player.sendMessage(messageManager.get("daily-authored-route",
                "<gold>📅 A napi és heti megbízások a kanonikus Küldetésnaplóban vannak; jutalmukat ott előre látod.</gold>"));
        QuestLogGUI.open(player, questManager, messageManager, 0, QuestLogHolder.Tab.BOARD);
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        return List.of();
    }
}
