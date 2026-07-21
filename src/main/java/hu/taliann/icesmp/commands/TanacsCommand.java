package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.CouncilManager;
import hu.taliann.icesmp.managers.EconomyEventManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /tanacs — a Menedék Vének Tanácsa: szavaz &lt;játékos&gt; (heti szavazat, NEUTRAL),
 * info (a tanács állása), vasarhet (tanácstag-only: Creutzér piaci díj-kedvezmény
 * ablak nyitása). A gameplay-logika a CouncilManagerben él.
 */
public final class TanacsCommand implements BasicCommand {

    private final CouncilManager councilManager;
    private final EconomyEventManager economyEventManager;
    private final MessageManager messageManager;

    public TanacsCommand(final CouncilManager councilManager, final EconomyEventManager economyEventManager,
                         final MessageManager messageManager) {
        this.councilManager = councilManager;
        this.economyEventManager = economyEventManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final CommandSourceStack source, final String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        final String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "szavaz" -> {
                if (args.length < 2) {
                    player.sendMessage(messageManager.get("messages.tanacs-szavaz-usage",
                            "&cHasználat: /tanacs szavaz <játékos>"));
                    return;
                }
                final Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(messageManager.get("messages.tanacs-target-offline",
                            "&cA jelöltnek online kell lennie a szavazáshoz."));
                    return;
                }
                final String error = councilManager.vote(player, target);
                if (error != null) {
                    player.sendMessage(messageManager.get("messages." + error, switch (error) {
                        case "council-disabled" -> "&7A Vének Tanácsa jelenleg nem ülésezik.";
                        case "council-neutral-only" -> "&cA tanács-választás a Menedék népének belügye.";
                        case "council-target-not-neutral" -> "&cCsak a Menedék polgárára szavazhatsz.";
                        default -> "&cA szavazat nem sikerült.";
                    }));
                    return;
                }
                player.sendMessage(messageManager.get("messages.tanacs-szavazat-ok",
                        "&a⚖ Szavazatod leadva: &f%s&a. (Hetente egyszer — átszavazással módosítható.)",
                        target.getName()));
            }
            case "vasarhet" -> {
                if (!councilManager.isCouncillor(player.getUniqueId())) {
                    player.sendMessage(messageManager.get("messages.tanacs-not-councillor",
                            "&cA Vásár-hetet csak a Vének Tanácsának tagja hirdetheti ki."));
                    return;
                }
                if (!economyEventManager.startCouncilBoom()) {
                    player.sendMessage(messageManager.get("messages.tanacs-boom-active",
                            "&7Már fut piaci fellendülés — a Vásár-hét most nem hirdethető ki."));
                }
            }
            default -> {
                player.sendMessage(messageManager.get("messages.tanacs-info-header",
                        "&6⚖ A Menedék Vének Tanácsa (heti választás):"));
                final List<String> names = councilManager.councillorNames();
                if (names.isEmpty()) {
                    player.sendMessage(messageManager.get("messages.tanacs-info-empty",
                            "&7Ezen a héten még nincs megválasztott vén — szavazz: /tanacs szavaz <játékos>"));
                } else {
                    for (final String line : names) {
                        player.sendMessage(messageManager.get("messages.tanacs-info-line", "&7 - &f%s", line));
                    }
                }
                player.sendMessage(messageManager.get("messages.tanacs-info-footer",
                        "&7Jogok: kassza-kivét (tanácsi kerettel), karaván-indítás, /tanacs vasarhet."));
            }
        }
    }

    @Override
    public Collection<String> suggest(final CommandSourceStack source, final String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("szavaz", "info", "vasarhet").stream()
                    .filter(option -> option.startsWith(prefix)).toList();
        }
        if (args.length == 2 && "szavaz".equalsIgnoreCase(args[0])) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
