package hu.taliann.icesmp.commands.faction;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /faction raid <célfrakció> [terület] — csak a frakció királya hirdethet raidet
 * (a nevezési díj a frakciókasszából megy); alapból a védő fővárosáért folyik.
 * /faction raid join — jelentkezés harcosnak (létszámkorlátig);
 * /faction raid status — állás (fázis, létszám, pontok).
 */
public final class FactionRaidSubcommand implements FactionSubcommand {

    private final RaidManager raidManager;
    private final KingManager kingManager;
    private final FactionManager factionManager;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;

    public FactionRaidSubcommand(final RaidManager raidManager, final KingManager kingManager,
                                 final FactionManager factionManager, final TerritoryManager territoryManager,
                                 final MessageManager messageManager) {
        this.raidManager = raidManager;
        this.kingManager = kingManager;
        this.factionManager = factionManager;
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "raid";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-raid", "Raid: meghirdetés (király), jelentkezés, állás.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-raid", "/faction raid <célfrakció> [terület] | join | status");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageManager.get("messages.faction-raid-usage", "&cHasználat: %s", usage()));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> handleJoin(player);
            case "status" -> handleStatus(player);
            default -> handleDeclare(player, args);
        }
        return true;
    }

    private void handleDeclare(final Player player, final String[] args) {
        if (!kingManager.isKing(player)) {
            player.sendMessage(messageManager.get("messages.faction-raid-not-king", "&cRaidet csak a frakciód királya hirdethet."));
            return;
        }

        final FactionType defender = FactionType.fromInput(args[0]);
        if (defender == null) {
            player.sendMessage(messageManager.get("messages.faction-unknown", "&cIsmeretlen frakció: &f%s", args[0]));
            return;
        }

        final FactionType attacker = factionManager.getFaction(player.getUniqueId());
        final String territoryId = args.length >= 2 ? args[1] : null;
        final String errorKey = raidManager.startRaid(attacker, defender, territoryId);
        if (errorKey != null) {
            player.sendMessage(messageManager.get("messages." + errorKey, defaultErrorFor(errorKey)));
            return;
        }

        // The declaring king marches with the army automatically.
        raidManager.joinRaid(player);
    }

    private void handleJoin(final Player player) {
        final String errorKey = raidManager.joinRaid(player);
        if (errorKey != null) {
            player.sendMessage(messageManager.get("messages." + errorKey, defaultErrorFor(errorKey)));
            return;
        }

        final FactionType side = factionManager.getFaction(player.getUniqueId());
        player.sendMessage(messageManager.get(
                "messages.faction-raid-joined",
                "&aBeálltál a(z) &f%s &ahadseregébe (&f%s&a/&f%s&a). Csak a jelentkezett harcosok közti ölés számít a raidbe!",
                side.getDisplayName(),
                raidManager.countParticipants(side),
                raidManager.maxPerSide()
        ));
    }

    private void handleStatus(final Player player) {
        final RaidManager.ActiveRaid raid = raidManager.getActiveRaid();
        if (raid == null) {
            player.sendMessage(messageManager.get("messages.faction-raid-none", "&7Jelenleg nincs raid."));
            return;
        }

        final Territory territory = raid.territoryId() == null ? null : territoryManager.getById(raid.territoryId());
        final long remainingMinutes = Math.max(0L, (raid.endsAtMillis() - System.currentTimeMillis()) / 60_000L);
        player.sendMessage(messageManager.get(
                "messages.faction-raid-status",
                "&6⚔ Raid: &f%s %s &7- &f%s %s &6| fázis: &f%s &6| hátra: ~&f%s perc&6 | harcosok: &f%s&7/&f%s &6vs &f%s&7/&f%s%s",
                raid.attacker().getDisplayName(),
                raidManager.getPoints(raid.attacker()),
                raidManager.getPoints(raid.defender()),
                raid.defender().getDisplayName(),
                raid.inWarmup() ? "felkészülés (/faction raid join)" : "harc",
                remainingMinutes,
                raidManager.countParticipants(raid.attacker()),
                raidManager.maxPerSide(),
                raidManager.countParticipants(raid.defender()),
                raidManager.maxPerSide(),
                territory == null ? "" : " | terület: " + territory.name()
        ));
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "faction-raid-already-active" -> "&cMár zajlik egy raid.";
            case "faction-raid-cooldown" -> "&cA hadak pihennek — két raid közt kötelező a szünet.";
            case "faction-raid-invalid-target" -> "&cÉrvénytelen célpont.";
            case "faction-raid-protected-target" -> "&cEz a frakció védett, nem raidelhető.";
            case "faction-raid-no-funds" -> "&cNincs elég pénz a frakciókasszában a nevezési díjhoz.";
            case "faction-raid-unknown-territory" -> "&cNincs ilyen terület a célfrakciónál.";
            case "faction-raid-none" -> "&7Jelenleg nincs raid.";
            case "faction-raid-not-party" -> "&cA frakciód nem hadviselő fél ebben a raidben.";
            case "faction-raid-already-joined" -> "&7Már jelentkeztél ebbe a raidbe.";
            case "faction-raid-side-full" -> "&cA te oldalad hadserege már megtelt.";
            case "faction-raid-combat-locked" ->
                    "&cA harci szakasz már megkezdődött — nevezni csak a felkészülés alatt lehet.";
            default -> "&cA raid művelet nem sikerült.";
        };
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        final List<String> options = new ArrayList<>(List.of("join", "status"));
        Arrays.stream(FactionType.values())
                .map(faction -> faction.name().toLowerCase(Locale.ROOT))
                .forEach(options::add);

        final String first = prefixAt(args, 0);
        final boolean firstComplete = args.length >= 1 && options.contains(first);

        // Két hosszal: 0 = "/faction raid " (üres prefix), 1 = gépelés közben — kivéve, ha az
        // args[0] már pontos egyezés, akkor a P=1 (terület) pozíció javaslatai jönnek.
        if (args.length == 0 || (args.length == 1 && !firstComplete)) {
            return options.stream().filter(option -> option.startsWith(first)).toList();
        }

        final FactionType defender = args.length >= 1 ? FactionType.fromInput(args[0]) : null;
        if (defender != null && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return territoryManager.all().stream()
                    .filter(territory -> territory.faction() == defender)
                    .map(Territory::id)
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }

}
