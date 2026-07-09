package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.AmbientEventManager;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.CaravanManager;
import hu.taliann.icesmp.managers.GatheringBuffManager;
import hu.taliann.icesmp.managers.TreasureEventManager;
import hu.taliann.icesmp.managers.WildHuntManager;
import hu.taliann.icesmp.managers.AbundanceManager;
import hu.taliann.icesmp.managers.ServerChallengeManager;
import hu.taliann.icesmp.managers.EscortManager;
import hu.taliann.icesmp.managers.MeteorEventManager;
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
    private final CaravanManager caravanManager;
    private final AmbientEventManager ambientEventManager;
    private final GatheringBuffManager gatheringBuffManager;
    private final TreasureEventManager treasureEventManager;
    private final WildHuntManager wildHuntManager;
    private final AbundanceManager abundanceManager;
    private final ServerChallengeManager serverChallengeManager;
    private final EscortManager escortManager;
    private final MeteorEventManager meteorEventManager;
    private final IntroManager introManager;
    private final MessageManager messageManager;

    public EventsCommand(final SeasonManager seasonManager, final BloodMoonManager bloodMoonManager,
                         final WorldBossManager worldBossManager, final InvasionManager invasionManager,
                         final CaravanManager caravanManager, final AmbientEventManager ambientEventManager,
                         final GatheringBuffManager gatheringBuffManager, final TreasureEventManager treasureEventManager,
                         final WildHuntManager wildHuntManager, final AbundanceManager abundanceManager,
                         final ServerChallengeManager serverChallengeManager, final EscortManager escortManager,
                         final MeteorEventManager meteorEventManager, final IntroManager introManager,
                         final MessageManager messageManager) {
        this.seasonManager = seasonManager;
        this.bloodMoonManager = bloodMoonManager;
        this.worldBossManager = worldBossManager;
        this.invasionManager = invasionManager;
        this.caravanManager = caravanManager;
        this.ambientEventManager = ambientEventManager;
        this.gatheringBuffManager = gatheringBuffManager;
        this.treasureEventManager = treasureEventManager;
        this.wildHuntManager = wildHuntManager;
        this.abundanceManager = abundanceManager;
        this.serverChallengeManager = serverChallengeManager;
        this.escortManager = escortManager;
        this.meteorEventManager = meteorEventManager;
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
            case "caravan", "karavan" -> handleCaravan(sender, args);
            case "ambient", "hangulat" -> handleAmbient(sender);
            case "gathering", "buff", "gyujtes" -> handleGathering(sender);
            case "treasure", "kincs" -> handleTreasure(sender);
            case "wild-hunt", "wildhunt", "hajsza" -> handleWildHunt(sender);
            case "abundance", "boseg" -> handleAbundance(sender);
            case "challenge", "kihivas" -> handleChallenge(sender);
            case "escort", "kiseret" -> handleEscort(sender);
            case "meteor" -> handleMeteor(sender);
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
        if (!requireAdmin(sender)) {
            return;
        }

        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(worldBossManager.forceSpawn(anchor)
                ? messageManager.get("events-worldboss-spawned", "&cVilágboss megidézve!")
                : messageManager.get("events-worldboss-failed", "&7Nem sikerült (már aktív boss, vagy nincs online játékos)."));
    }

    private void handleInvasion(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }

        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(invasionManager.forceStart(anchor)
                ? messageManager.get("events-invasion-started", "&cInvázió elindítva!")
                : messageManager.get("events-invasion-failed", "&7Nem sikerült (nincs online játékos)."));
    }

    private void handleCaravan(final CommandSender sender, final String[] args) {
        // /events caravan [arrive|depart] — admin override; no arg = status.
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
                return;
            }
            final String sub = args[1].toLowerCase(Locale.ROOT);
            if ("arrive".equals(sub) || "start".equals(sub)) {
                final Player anchor = sender instanceof Player player ? player : null;
                sender.sendMessage(caravanManager.forceArrive(anchor)
                        ? messageManager.get("events-caravan-arrived", "&6Kereskedő-karaván megérkezett!")
                        : messageManager.get("events-caravan-already", "&7A karaván már a városban van."));
            } else if ("depart".equals(sub) || "stop".equals(sub)) {
                sender.sendMessage(caravanManager.forceDepart()
                        ? messageManager.get("events-caravan-departed", "&6A karaván továbbállt.")
                        : messageManager.get("events-caravan-not-active", "&7Most nincs itt karaván."));
            } else {
                sender.sendMessage(messageManager.get("events-caravan-usage", "&cHasználat: /events caravan [arrive|depart]"));
            }
            return;
        }

        sender.sendMessage(messageManager.get(
                caravanManager.isActive() ? "events-caravan-active" : "events-caravan-inactive",
                caravanManager.isActive() ? "&6A kereskedő-karaván épp a városban van!" : "&7Jelenleg nincs itt kereskedő-karaván."));
    }

    private void handleAmbient(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        sender.sendMessage(ambientEventManager.forceRandom()
                ? messageManager.get("events-ambient-fired", "&bHangulat-esemény kiváltva!")
                : messageManager.get("events-ambient-none", "&7Nincs engedélyezett hangulat-esemény a configban."));
    }

    private void handleGathering(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        sender.sendMessage(gatheringBuffManager.forceRandom()
                ? messageManager.get("events-gathering-fired", "&eGyűjtögető buff-ablak megnyitva!")
                : messageManager.get("events-gathering-none", "&7Már fut egy buff-ablak, vagy egy sincs engedélyezve."));
    }

    private void handleTreasure(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(treasureEventManager.forceSpawn(anchor)
                ? messageManager.get("events-treasure-spawned", "&6Kincs elrejtve a közeledben!")
                : messageManager.get("events-treasure-failed", "&7Nem sikerült (már van elrejtett kincs, vagy nincs online játékos)."));
    }

    private void handleWildHunt(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(wildHuntManager.forceStart(anchor)
                ? messageManager.get("events-wildhunt-started", "&4Vad Hajsza elindítva!")
                : messageManager.get("events-wildhunt-failed", "&7Nem sikerült (már kóborol egy fenevad, vagy nincs online játékos)."));
    }

    private void handleAbundance(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        sender.sendMessage(abundanceManager.forceStart()
                ? messageManager.get("events-abundance-started", "&aBőség-idő elindítva!")
                : messageManager.get("events-abundance-already", "&7Már tart egy Bőség-idő."));
    }

    private void handleChallenge(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        sender.sendMessage(serverChallengeManager.forceStart()
                ? messageManager.get("events-challenge-started", "&6Szerver-kihívás elindítva!")
                : messageManager.get("events-challenge-already", "&7Már fut egy szerver-kihívás, vagy egy sincs engedélyezve."));
    }

    private void handleEscort(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(escortManager.forceStart(anchor)
                ? messageManager.get("events-escort-started", "&eKaraván-kíséret elindítva!")
                : messageManager.get("events-escort-failed", "&7Nem sikerült (már úton van egy konvoj, vagy nincs online játékos)."));
    }

    private void handleMeteor(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(meteorEventManager.forceSpawn(anchor)
                ? messageManager.get("events-meteor-spawned", "&cMeteor becsapódott a közeledben!")
                : messageManager.get("events-meteor-failed", "&7Nem sikerült (már van kráter, vagy nincs online játékos)."));
    }

    /** One admin gate for every admin-only subcommand: messages and returns false when denied. */
    private boolean requireAdmin(final CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
        return false;
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
        if (!requireAdmin(sender)) {
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
                    ? List.of("season", "blood-moon", "worldboss", "invasion", "caravan", "ambient", "gathering", "treasure", "wild-hunt", "abundance", "challenge", "escort", "meteor", "intro")
                    : List.of("season", "blood-moon", "caravan");
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (args.length == 2 && ("blood-moon".equalsIgnoreCase(args[0]) || "bloodmoon".equalsIgnoreCase(args[0]))
                && sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of("start", "stop").stream()
                    .filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        if (args.length == 2 && ("caravan".equalsIgnoreCase(args[0]) || "karavan".equalsIgnoreCase(args[0]))
                && sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of("arrive", "depart").stream()
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
