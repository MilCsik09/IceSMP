package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.gui.CommandMenuContext;
import hu.taliann.icesmp.gui.CommandMenus;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /leaderboard — opens the leaderboard GUI (richest, highest level, most raid
 * kills). Optional argument picks the starting category.
 */
public final class LeaderboardCommand implements BasicCommand {

    private final CommandMenuContext menuContext;
    private final MessageManager messageManager;

    public LeaderboardCommand(final CommandMenuContext menuContext, final MessageManager messageManager) {
        this.menuContext = menuContext;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final StatsManager.Category category = args.length >= 1 ? switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wealth", "vagyon" -> StatsManager.Category.WEALTH;
            case "raidkills", "raid", "kills" -> StatsManager.Category.RAID_KILLS;
            default -> StatsManager.Category.LEVEL;
        } : StatsManager.Category.LEVEL;

        CommandMenus.openLeaderboard(player, menuContext, category);
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("level", "wealth", "raidkills").stream().filter(option -> option.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
