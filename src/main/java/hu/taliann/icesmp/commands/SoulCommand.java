package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.SoulShardManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /souls — lélekszilánk egyenleg és bajnok-idézés (Nekromanta erőforrás).
 */
public final class SoulCommand implements BasicCommand {

    private final SoulShardManager soulShardManager;
    private final MessageManager messageManager;

    public SoulCommand(final SoulShardManager soulShardManager, final MessageManager messageManager) {
        this.soulShardManager = soulShardManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length >= 1 && "champion".equalsIgnoreCase(args[0])) {
            final String errorKey = soulShardManager.summonChampion(player);
            if (errorKey != null) {
                player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            }
            return;
        }

        player.sendMessage(messageManager.getMessage(
                "souls-balance",
                "<dark_purple>Lélekszilánkjaid: <white>{shards}</white> &7(/souls champion — bajnok idézése)</dark_purple>",
                Map.of("shards", String.valueOf(soulShardManager.getShards(player)))
        ));
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "souls-champion-cap" -> "&cElérted a maximális idézett társ-számot.";
            case "souls-champion-insufficient" -> "&cNincs elég lélekszilánkod a bajnokhoz.";
            default -> "&cA bajnok idézése nem sikerült.";
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("champion").stream().filter(option -> option.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
