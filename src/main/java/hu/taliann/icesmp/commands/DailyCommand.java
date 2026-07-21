package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.DailyQuestManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * /daily — shows today's rotating daily quest and the player's progress.
 */
public final class DailyCommand implements BasicCommand {

    private final DailyQuestManager dailyQuestManager;
    private final MessageManager messageManager;

    public DailyCommand(final DailyQuestManager dailyQuestManager, final MessageManager messageManager) {
        this.dailyQuestManager = dailyQuestManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final DailyQuestManager.Daily daily = dailyQuestManager.getActive();
        final String state = dailyQuestManager.isDone(player)
                ? messageManager.get("daily-state-done", "&ateljesítve")
                : dailyQuestManager.getProgress(player) + "/" + daily.amount();
        player.sendMessage(messageManager.getMessage(
                "daily-info",
                "<gold>📅 Napi küldetés: <yellow>{name}</yellow> &7| Állás: <white>{state}</white> &7| Jutalom: <white>{reward}</white> &7| Sorozat: <white>{streak} nap</white></gold>",
                Map.of(
                        "name", daily.name(),
                        "state", state,
                        "reward", String.valueOf(daily.reward()),
                        "streak", String.valueOf(dailyQuestManager.getStreak(player))
                )));

        final DailyQuestManager.Daily weekly = dailyQuestManager.getActiveWeekly();
        final String weeklyState = dailyQuestManager.isWeeklyDone(player)
                ? messageManager.get("daily-state-done", "&ateljesítve")
                : dailyQuestManager.getWeeklyProgress(player) + "/" + weekly.amount();
        player.sendMessage(messageManager.getMessage(
                "weekly-info",
                "<gold>🗓 Heti küldetés: <yellow>{name}</yellow> &7| Állás: <white>{state}</white> &7| Jutalom: <white>{reward}</white></gold>",
                Map.of(
                        "name", weekly.name(),
                        "state", weeklyState,
                        "reward", String.valueOf(weekly.reward())
                )));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        return List.of();
    }
}
