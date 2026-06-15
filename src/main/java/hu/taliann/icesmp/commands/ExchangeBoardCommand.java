package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ExchangeBoardManager;
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
 * /exchangeboard — admin placement of exchange-rate hologram boards.
 * Subcommands: place (spawn a board here), remove (delete the nearest board).
 */
public final class ExchangeBoardCommand implements BasicCommand {

    private static final String PERMISSION = "icesmp.admin.exchangeboard";

    private final ExchangeBoardManager exchangeBoardManager;
    private final MessageManager messageManager;

    public ExchangeBoardCommand(final ExchangeBoardManager exchangeBoardManager, final MessageManager messageManager) {
        this.exchangeBoardManager = exchangeBoardManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultsagod erre a parancsra."));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final String action = args.length == 0 ? "place" : args[0].toLowerCase(Locale.ROOT);
        if ("remove".equals(action)) {
            if (exchangeBoardManager.removeNearest(player, 6.0D)) {
                player.sendMessage(messageManager.get("exchangeboard-removed", "<yellow>A legközelebbi árfolyamtábla eltávolítva.</yellow>"));
            } else {
                player.sendMessage(messageManager.get("exchangeboard-none-nearby", "<red>Nincs árfolyamtábla a közelben.</red>"));
            }
            return;
        }

        exchangeBoardManager.placeBoard(player);
        player.sendMessage(messageManager.get("exchangeboard-placed", "<green>Árfolyamtábla elhelyezve.</green>"));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("place", "remove").stream().filter(option -> option.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
