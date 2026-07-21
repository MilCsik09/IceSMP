package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.HonorDuelManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * G6 — /parbaj: becsület-párbaj. "kihiv <név>" (csak bűnös), "elfogad",
 * "elutasit". A logika a HonorDuelManagerben; a párbaj beleegyezéses —
 * nem termel bűnt, és a győztes bűnös egy pontot tisztul.
 */
public final class HonorDuelCommand implements BasicCommand {

    private final JavaPlugin plugin;
    private final HonorDuelManager duelManager;
    private final MessageManager messageManager;

    public HonorDuelCommand(final JavaPlugin plugin, final HonorDuelManager duelManager,
                            final MessageManager messageManager) {
        this.plugin = plugin;
        this.duelManager = duelManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            commandSourceStack.getSender().sendMessage(messageManager.get(
                    "messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        if (args.length == 0) {
            player.sendMessage(messageManager.get("duel-help",
                    "&6⚔ /parbaj &7- kihiv <név> (csak bűnös) | elfogad | elutasit — a győztes bűnös egy bűnpontot tisztul."));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "kihiv", "challenge" -> {
                if (args.length < 2) {
                    player.sendMessage(messageManager.get("duel-usage", "&cHasználat: /parbaj kihiv <név>"));
                    return;
                }
                final Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(messageManager.get("duel-no-player", "&cNincs ilyen online játékos."));
                    return;
                }
                final String error = duelManager.challenge(player, target);
                if (error == null) {
                    player.sendMessage(messageManager.get("duel-challenged", "&6⚔ Elégtételt ajánlottál: &f%s&6 — várd a döntését.", target.getName()));
                    target.getScheduler().run(plugin, task -> target.sendMessage(messageManager.getMessage(
                            "duel-challenge-received",
                            "<gold>⚔ <white>{challenger}</white> becsület-párbajt ajánl az elégtételért! Elfogadás: <white>/parbaj elfogad</white> — ha ő nyer, egy bűne törlődik; a párbaj nem termel bűnt.</gold>",
                            Map.of("challenger", player.getName()))), null);
                } else {
                    player.sendMessage(messageManager.get("duel-error." + error, switch (error) {
                        case "duel-not-sinner" -> "&cCsak bűnös ajánlhat elégtételt — a te lelked tiszta.";
                        case "duel-self" -> "&cÖnmagaddal nem párbajozhatsz.";
                        case "duel-busy" -> "&cValamelyikőtök már párbajban áll.";
                        case "duel-limit" -> "&cE heti párbaj-kereted elfogyott.";
                        default -> "&cA párbaj most nem ajánlható fel.";
                    }));
                }
            }
            case "elfogad", "accept" -> {
                final String error = duelManager.accept(player);
                if (error == null) {
                    player.sendMessage(messageManager.get("duel-accepted",
                            "&6⚔ A párbaj megkezdődött — 3 perc! A halál dönt, bűn nélkül."));
                } else {
                    player.sendMessage(messageManager.get("duel-no-challenge-msg", "&cNincs függő párbaj-felkérésed."));
                }
            }
            case "elutasit", "decline" -> player.sendMessage(duelManager.declined(player)
                    ? messageManager.get("duel-declined", "&7⚔ Elutasítottad az elégtételt.")
                    : messageManager.get("duel-no-challenge-msg", "&cNincs függő párbaj-felkérésed."));
            default -> player.sendMessage(messageManager.get("duel-usage", "&cHasználat: /parbaj kihiv <név> | elfogad | elutasit"));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack,
                                               final @NonNull String[] args) {
        if (args.length <= 1) {
            return List.of("kihiv", "elfogad", "elutasit");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kihiv")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
