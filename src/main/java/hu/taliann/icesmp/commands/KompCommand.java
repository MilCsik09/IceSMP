package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.FerryManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /komp [útvonal] — átkelés egy fix két-végpontú kompjáraton (építész-kérés:
 * óceán-átkelés híd helyett). Útvonal nélkül a járatok listája. Tipikus kötés:
 * a kikötői révész-NPC-re: /npcbind <npc> command "komp <útvonal>".
 */
public final class KompCommand implements BasicCommand {

    private final FerryManager ferryManager;
    private final MessageManager messageManager;

    public KompCommand(final FerryManager ferryManager, final MessageManager messageManager) {
        this.ferryManager = ferryManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final CommandSourceStack source, final String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        if (args.length == 0) {
            final List<String> routes = ferryManager.routeIds();
            if (routes.isEmpty()) {
                player.sendMessage(messageManager.get("messages.ferry-no-routes",
                        "&7⛴ Még nincs kompjárat kijelölve (config: ferry.routes)."));
                return;
            }
            player.sendMessage(messageManager.get("messages.ferry-list-header", "&b⛴ Kompjáratok:"));
            for (final String id : routes) {
                player.sendMessage(messageManager.get("messages.ferry-list-line",
                        "&7 - &f%s &7(%s)", id, ferryManager.routeName(id)));
            }
            return;
        }
        ferryManager.ride(player, args[0].toLowerCase(Locale.ROOT));
    }

    @Override
    public Collection<String> suggest(final CommandSourceStack source, final String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return ferryManager.routeIds().stream()
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
