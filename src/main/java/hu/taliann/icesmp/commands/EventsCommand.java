package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.IntroManager;
import hu.taliann.icesmp.managers.InvasionManager;
import hu.taliann.icesmp.managers.SeasonManager;
import hu.taliann.icesmp.managers.WorldBossManager;
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
    private final WorldBossManager worldBossManager;
    private final InvasionManager invasionManager;
    private final IntroManager introManager;
    private final MessageManager messageManager;

    public EventsCommand(final SeasonManager seasonManager, final BloodMoonManager bloodMoonManager,
                         final WorldBossManager worldBossManager, final InvasionManager invasionManager,
                         final IntroManager introManager, final MessageManager messageManager) {
        this.seasonManager = seasonManager;
        this.bloodMoonManager = bloodMoonManager;
        this.worldBossManager = worldBossManager;
        this.invasionManager = invasionManager;
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
            case "blood-moon", "bloodmoon" -> handleBloodMoon(sender, args);
            case "world-boss", "worldboss", "boss" -> handleWorldBoss(sender);
            case "invasion", "invazio" -> handleInvasion(sender);
            case "intro" -> handleIntro(sender, args);
            default -> handleSeason(sender);
        }
    }

    private void handleBloodMoon(final CommandSender sender, final String[] args) {
        // /events bloodmoon [start|stop] — admin override; no arg = status.
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
                return;
            }
            final String sub = args[1].toLowerCase(Locale.ROOT);
            if ("start".equals(sub)) {
                sender.sendMessage(bloodMoonManager.forceStart()
                        ? messageManager.get("events-bloodmoon-forced", "&cVérhold elindítva!")
                        : messageManager.get("events-bloodmoon-already", "&7Már tombol a vérhold."));
            } else if ("stop".equals(sub)) {
                sender.sendMessage(bloodMoonManager.forceEnd()
                        ? messageManager.get("events-bloodmoon-stopped", "&aVérhold leállítva.")
                        : messageManager.get("events-bloodmoon-not-active", "&7Most nincs vérhold."));
            } else {
                sender.sendMessage(messageManager.get("events-bloodmoon-usage", "&cHasználat: /events bloodmoon [start|stop]"));
            }
            return;
        }

        sender.sendMessage(messageManager.get(
                bloodMoonManager.isActive() ? "events-bloodmoon-active" : "events-bloodmoon-inactive",
                bloodMoonManager.isActive() ? "&cVérhold tombol!" : "&7Jelenleg nincs vérhold."));
    }

    private void handleWorldBoss(final CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(worldBossManager.forceSpawn(anchor)
                ? messageManager.get("events-worldboss-spawned", "&cVilágboss megidézve!")
                : messageManager.get("events-worldboss-failed", "&7Nem sikerült (már aktív boss, vagy nincs online játékos)."));
    }

    private void handleInvasion(final CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(invasionManager.forceStart(anchor)
                ? messageManager.get("events-invasion-started", "&cInvázió elindítva!")
                : messageManager.get("events-invasion-failed", "&7Nem sikerült (nincs online játékos)."));
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
                    ? List.of("season", "blood-moon", "worldboss", "invasion", "intro")
                    : List.of("season", "blood-moon");
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (args.length == 2 && ("blood-moon".equalsIgnoreCase(args[0]) || "bloodmoon".equalsIgnoreCase(args[0]))
                && sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of("start", "stop").stream()
                    .filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
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
