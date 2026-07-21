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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /events — világesemények: (arg nélkül vagy "season") a szezon-állás;
 * "status" (mindenkinek) a "Mi történik most?" összegzés; "blood-moon" a vérhold
 * állapota; admin: intro [játékos] az intro újrajátszása.
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
    /** D19: setterrel kötve (a manager a parancsnál később épül a DI-sorrendben). */
    private hu.taliann.icesmp.managers.StrangerNpcManager strangerNpcManager;

    public void setStrangerNpcManager(final hu.taliann.icesmp.managers.StrangerNpcManager strangerNpcManager) {
        this.strangerNpcManager = strangerNpcManager;
    }

    /** H2/B42: setterrel kötve (a managerek a parancsnál később épülnek a DI-sorrendben). */
    private hu.taliann.icesmp.managers.CorruptionManager corruptionManager;
    private hu.taliann.icesmp.managers.ArcheologyManager archeologyManager;

    public void setCorruptionManager(final hu.taliann.icesmp.managers.CorruptionManager corruptionManager) {
        this.corruptionManager = corruptionManager;
    }

    public void setArcheologyManager(final hu.taliann.icesmp.managers.ArcheologyManager archeologyManager) {
        this.archeologyManager = archeologyManager;
    }

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
            case "status" -> handleStatus(sender);
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
            case "stranger", "idegen" -> handleStranger(sender);
            case "corruption", "rontas" -> handleCorruption(sender);
            case "archeology", "regeszet" -> handleArcheology(sender);
            case "intro" -> handleIntro(sender, args);
            default -> handleSeason(sender);
        }
    }

    private void handleBloodMoon(final CommandSender sender, final String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
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

    /** D19 — az Idegen kézi megidézése (atmoszféra-teszthez). */
    private void handleStranger(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (strangerNpcManager == null) {
            return;
        }
        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(strangerNpcManager.forceSpawn(anchor)
                ? messageManager.get("events-stranger-spawned", "&8Az Idegen… valahol a közelben jár.")
                : messageManager.get("events-stranger-failed", "&7Nincs online játékos, aki mellett feltűnhetne."));
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
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
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

    private void handleCorruption(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(corruptionManager != null && corruptionManager.forceSpawn(anchor)
                ? messageManager.get("events-corruption-spawned", "&5Rontás-góc nyílik a közeledben!")
                : messageManager.get("events-corruption-failed", "&7Nem sikerült (már van aktív góc, vagy nincs online játékos)."));
    }

    private void handleArcheology(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        final Player anchor = sender instanceof Player player ? player : null;
        sender.sendMessage(archeologyManager != null && archeologyManager.forceSpawn(anchor)
                ? messageManager.get("events-archeology-spawned", "&eGyanús lelőhely bukkant fel a közeledben!")
                : messageManager.get("events-archeology-failed", "&7Nem sikerült (már van aktív lelőhely, vagy nincs online játékos)."));
    }

    /** One admin gate for every admin-only subcommand: messages and returns false when denied. */
    private boolean requireAdmin(final CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
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

    /**
     * "Mi történik most?" — publikus alparancs (senkinek sincs eltiltva): kilistázza
     * az épp aktív világeseményeket, majd a szezon állását. Ugyanazt a
     * {@link #activeEventLines} formázót hívja, mint a {@code /menu} Események
     * almenüjének info-ikonja, hogy a két felület sose térjen el egymástól.
     */
    private void handleStatus(final CommandSender sender) {
        sender.sendMessage(messageManager.get("events-status-header", "&6✦ Mi történik most? ✦"));

        final List<String> lines = activeEventLines(messageManager, bloodMoonManager, worldBossManager,
                invasionManager, caravanManager, gatheringBuffManager, treasureEventManager,
                wildHuntManager, abundanceManager, serverChallengeManager, escortManager, meteorEventManager);
        if (lines.isEmpty()) {
            sender.sendMessage(messageManager.get("events-status-empty", "&7Most éppen nyugalom van a vidéken."));
        } else {
            lines.forEach(sender::sendMessage);
        }

        final long remainingDays = Math.max(0L,
                (seasonManager.getSeasonEndMillis() - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L));
        sender.sendMessage(messageManager.get("events-status-season", "&6Szezon: &7még ~%s nap", String.valueOf(remainingDays)));
        for (final FactionType faction : FactionType.values()) {
            sender.sendMessage(messageManager.get(
                    "events-season-line", "&e%s&7: &f%s pont", faction.getDisplayName(), seasonManager.getPoints(faction)));
        }
    }

    /**
     * Shared "what's happening now" formatter: one colorized line per currently-active
     * world event (blood moon, world boss, invasion, caravan, gathering buff, treasure,
     * wild hunt, abundance, server challenge, escort, meteor), skipping any manager
     * passed as {@code null} — the {@code /menu} Események almenü info-ikonja does not
     * have every manager wired through {@code CommandMenuContext}, so it calls this with
     * {@code null} for the ones it lacks rather than duplicating the formatting logic.
     * Read-only: every value comes from each manager's existing volatile state getters.
     *
     * @return the active-event lines (empty when nothing is happening)
     */
    public static List<String> activeEventLines(final MessageManager messageManager,
            final BloodMoonManager bloodMoonManager, final WorldBossManager worldBossManager,
            final InvasionManager invasionManager, final CaravanManager caravanManager,
            final GatheringBuffManager gatheringBuffManager, final TreasureEventManager treasureEventManager,
            final WildHuntManager wildHuntManager, final AbundanceManager abundanceManager,
            final ServerChallengeManager serverChallengeManager, final EscortManager escortManager,
            final MeteorEventManager meteorEventManager) {
        final List<String> lines = new ArrayList<>();

        if (bloodMoonManager != null && bloodMoonManager.isActive()) {
            final long remaining = bloodMoonManager.getRemainingMillis();
            lines.add(remaining >= 0L
                    ? messageManager.get("events-status-bloodmoon-timed", "&c🌕 Vérhold — még ~%s perc", String.valueOf(minutesCeil(remaining)))
                    : messageManager.get("events-status-bloodmoon-active", "&c🌕 Vérhold tombol!"));
        }
        if (worldBossManager != null && worldBossManager.isBossActive()) {
            final long pct = Math.round(worldBossManager.getBossHealthFraction() * 100.0F);
            lines.add(messageManager.get("events-status-worldboss", "&4☠ Világboss él — HP %s%%", String.valueOf(pct)));
        }
        if (invasionManager != null && invasionManager.isActive()) {
            lines.add(messageManager.get("events-status-invasion", "&4⚔ Invázió tombol a vidéken"));
        }
        if (caravanManager != null && caravanManager.isActive()) {
            lines.add(messageManager.get("events-status-caravan", "&6🏜 Karaván a városban"));
        }
        if (gatheringBuffManager != null && gatheringBuffManager.getActive() != null) {
            final long remaining = gatheringBuffManager.getRemainingMillis();
            final String label = gatheringIcon(gatheringBuffManager.getActive()) + " " + gatheringBuffManager.describeActive();
            lines.add(remaining >= 0L
                    ? messageManager.get("events-status-gathering-timed", "&e%s — még ~%s perc", label, String.valueOf(minutesCeil(remaining)))
                    : messageManager.get("events-status-gathering-active", "&e%s", label));
        }
        if (treasureEventManager != null && treasureEventManager.isActive()) {
            lines.add(messageManager.get("events-status-treasure", "&6📦 Kincs vár felfedezésre"));
        }
        if (wildHuntManager != null && wildHuntManager.isActive()) {
            final long remaining = wildHuntManager.getRemainingMillis();
            lines.add(remaining >= 0L
                    ? messageManager.get("events-status-wildhunt-timed", "&4🐺 Vad Hajsza kóborol a vidéken — még ~%s perc", String.valueOf(minutesCeil(remaining)))
                    : messageManager.get("events-status-wildhunt-active", "&4🐺 Vad Hajsza kóborol a vidéken"));
        }
        if (abundanceManager != null && abundanceManager.isActive()) {
            final long remaining = abundanceManager.getRemainingMillis();
            lines.add(remaining >= 0L
                    ? messageManager.get("events-status-abundance-timed", "&a🌱 Bőség-idő van — még ~%s perc", String.valueOf(minutesCeil(remaining)))
                    : messageManager.get("events-status-abundance-active", "&a🌱 Bőség-idő van"));
        }
        if (escortManager != null && escortManager.isActive()) {
            final long remaining = escortManager.getRemainingMillis();
            lines.add(remaining >= 0L
                    ? messageManager.get("events-status-escort-timed", "&e🛡 Karaván-kíséret zajlik — még ~%s perc", String.valueOf(minutesCeil(remaining)))
                    : messageManager.get("events-status-escort-active", "&e🛡 Karaván-kíséret zajlik"));
        }
        if (serverChallengeManager != null && serverChallengeManager.isActive()) {
            final long remaining = serverChallengeManager.getRemainingMillis();
            final String goal = String.valueOf(serverChallengeManager.describeGoal());
            final String progress = serverChallengeManager.getProgress() + "/" + serverChallengeManager.getTarget();
            lines.add(remaining >= 0L
                    ? messageManager.get("events-status-challenge-timed", "&6🎯 Szerver-kihívás: %s (%s) — még ~%s perc", goal, progress, String.valueOf(minutesCeil(remaining)))
                    : messageManager.get("events-status-challenge-active", "&6🎯 Szerver-kihívás: %s (%s)", goal, progress));
        }
        if (meteorEventManager != null && meteorEventManager.isActive()) {
            final long remaining = meteorEventManager.getRemainingMillis();
            lines.add(remaining >= 0L
                    ? messageManager.get("events-status-meteor-timed", "&c☄ Meteor-kráter vár bányászásra — még ~%s perc", String.valueOf(minutesCeil(remaining)))
                    : messageManager.get("events-status-meteor-active", "&c☄ Meteor-kráter vár bányászásra"));
        }

        return lines;
    }

    private static String gatheringIcon(final GatheringBuffManager.GatheringBuff buff) {
        return switch (buff) {
            case MINING_RUSH -> "⛏";
            case HARVEST_HOUR -> "🌾";
            case FISHING_FRENZY -> "🎣";
            case XP_HOUR -> "✦";
        };
    }

    /** Rounds a remaining-millis value up to whole minutes (at least 1 while still active). */
    private static long minutesCeil(final long remainingMillis) {
        return Math.max(1L, (remainingMillis + 59_999L) / 60_000L);
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
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        introManager.play(target);
        sender.sendMessage(messageManager.get("events-intro-replayed", "&aIntro lejátszva: &f%s", target.getName()));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final List<String> options = sender.hasPermission(ADMIN_PERMISSION)
                ? List.of("status", "season", "blood-moon", "worldboss", "invasion", "caravan", "ambient", "gathering", "treasure", "wild-hunt", "abundance", "challenge", "escort", "meteor", "stranger", "corruption", "archeology", "intro")
                : List.of("status", "season", "blood-moon", "caravan");
        final String first = prefixAt(args, 0);
        final boolean firstComplete = options.contains(first);

        // Két hosszal: 0 = "/events " (üres prefix), 1 = gépelés közben — kivéve, ha az args[0]
        // már pontos egyezés, akkor a P=1 pozíció (blood-moon/caravan/intro alparancsa) jön.
        if (args.length == 0 || (args.length == 1 && !firstComplete)) {
            return options.stream().filter(option -> option.startsWith(first)).toList();
        }

        if (("blood-moon".equals(first) || "bloodmoon".equals(first))
                && sender.hasPermission(ADMIN_PERMISSION) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return List.of("start", "stop").stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (("caravan".equals(first) || "karavan".equals(first))
                && sender.hasPermission(ADMIN_PERMISSION) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return List.of("arrive", "depart").stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if ("intro".equals(first) && sender.hasPermission(ADMIN_PERMISSION) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        return List.of();
    }

    /** Az adott pozíción gépelés alatt álló szó (kisbetűsítve), vagy üres, ha még el sem kezdték. */
    private static String prefixAt(final String[] args, final int index) {
        return args.length > index ? args[index].toLowerCase(Locale.ROOT) : "";
    }
}
