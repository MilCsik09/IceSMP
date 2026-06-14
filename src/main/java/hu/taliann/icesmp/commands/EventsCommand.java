package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.IntroManager;
import hu.taliann.icesmp.managers.SeasonManager;
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
 * /events — világesemények: (arg nélkül vagy "season") a szezon-állás;
 * "blood-moon" a vérhold állapota; admin: intro [játékos] az intro újrajátszása.
 */
public final class EventsCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "icesmp.admin.events";

    private final SeasonManager seasonManager;
    private final BloodMoonManager bloodMoonManager;
    private final IntroManager introManager;
    private final MessageManager messageManager;

    public EventsCommand(final SeasonManager seasonManager, final BloodMoonManager bloodMoonManager,
                         final IntroManager introManager, final MessageManager messageManager) {
        this.seasonManager = seasonManager;
        this.bloodMoonManager = bloodMoonManager;
        this.introManager = introManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0 || "season".equalsIgnoreCase(args[0])) {
            handleSeason(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "blood-moon", "bloodmoon" -> sender.sendMessage(messageManager.get(
                    bloodMoonManager.isActive() ? "events-bloodmoon-active" : "events-bloodmoon-inactive",
                    bloodMoonManager.isActive() ? "&cVérhold tombol!" : "&7Jelenleg nincs vérhold."));
            case "intro" -> handleIntro(sender, args);
            default -> handleSeason(sender);
        }
    }

    private void handleSeason(final CommandSender sender) {
        sender.sendMessage(messageManager.get("events-season-header", "&6Szezon-állás:"));
        for (final FactionType faction : FactionType.values()) {
            sender.sendMessage(messageManager.get(
                    "events-season-line",
                    "&e%s&7: &f%s pont",
                    faction.getDisplayName(),
                    seasonManager.getPoints(faction)
            ));
        }
    }

    private void handleIntro(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        final Player target;
        if (args.length >= 2) {
            target = org.bukkit.Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(messageManager.get("target-player-offline", "&cA célpont játékos nem elérhető."));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        introManager.play(target);
        sender.sendMessage(messageManager.get("events-intro-replayed", "&aIntro lejátszva: &f%s", target.getName()));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            final List<String> options = sender.hasPermission(ADMIN_PERMISSION)
                    ? List.of("season", "blood-moon", "intro")
                    : List.of("season", "blood-moon");
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (args.length == 2 && "intro".equalsIgnoreCase(args[0]) && sender.hasPermission(ADMIN_PERMISSION)) {
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        return List.of();
    }
}
