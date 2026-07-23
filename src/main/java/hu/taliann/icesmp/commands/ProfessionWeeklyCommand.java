package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/** I16 — /szakmacel: a szakma-céhek heti közös céljainak állása. */
public final class ProfessionWeeklyCommand implements BasicCommand {

    private final ProfessionWeeklyGoalManager weeklyGoal;
    private final MessageManager messageManager;

    public ProfessionWeeklyCommand(final ProfessionWeeklyGoalManager weeklyGoal,
                                   final MessageManager messageManager) {
        this.weeklyGoal = weeklyGoal;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final var sender = commandSourceStack.getSender();
        sender.sendMessage(messageManager.get("profession-weekly-header",
                "&6⚒ Szakma-céhek heti közös céljai &7(a hét fordulásakor jár a jutalom):"));
        for (final ProfessionType profession : ProfessionType.values()) {
            final long goal = weeklyGoal.goalOf(profession);
            if (goal <= 0) {
                continue;
            }
            final long current = weeklyGoal.counterOf(profession);
            sender.sendMessage(messageManager.get("profession-weekly-row",
                    "&7- &f%s&7: &f%s&7/&f%s %s",
                    profession.getId(), current, goal, current >= goal ? "&a✔" : ""));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack,
                                               final @NonNull String[] args) {
        return List.of();
    }
}
