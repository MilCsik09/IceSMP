package hu.taliann.icesmp.commands.faction;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.FactionTreasuryManager;
import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /faction king — uralkodó és választás:
 * (arg nélkül) info; vote <játékos>; admin: set <frakció> <játékos>, clear <frakció>
 */
public final class FactionKingSubcommand implements FactionSubcommand {

    private static final String ADMIN_PERMISSION = hu.taliann.icesmp.core.Permissions.FACTION;

    private final KingManager kingManager;
    private final FactionManager factionManager;
    private final FactionTreasuryManager treasuryManager;
    private final MessageManager messageManager;

    public FactionKingSubcommand(final KingManager kingManager, final FactionManager factionManager,
                                 final FactionTreasuryManager treasuryManager, final MessageManager messageManager) {
        this.kingManager = kingManager;
        this.factionManager = factionManager;
        this.treasuryManager = treasuryManager;
        this.messageManager = messageManager;
    }

    private boolean handleTax(final CommandSender sender, final String rawPercent) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        if (!kingManager.isKing(player)) {
            sender.sendMessage(messageManager.get("messages.faction-king-tax-not-king", "&cAdókulcsot csak a frakciód királya állíthat."));
            return true;
        }

        final double requested;
        try {
            requested = Double.parseDouble(rawPercent);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("messages.invalid-amount", "&cÉrvénytelen összeg."));
            return true;
        }

        final FactionType faction = factionManager.getChosenFaction(player.getUniqueId()).orElse(null);
        if (faction == null) {
            sender.sendMessage(messageManager.get("messages.faction-choose-first",
                    "&cKirályi jogokhoz előbb válassz frakciót: &f/faction join <frakció>&c."));
            return true;
        }
        final double applied = treasuryManager.setTaxRate(faction, requested);
        sender.sendMessage(messageManager.get("messages.faction-king-tax-set", "&aA(z) %s frakció adókulcsa beállítva: &f%s%%", faction.getDisplayName(), applied));
        return true;
    }

    @Override
    public String name() {
        return "king";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-king", "Uralkodó és királyválasztás.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-king", "/faction king [vote <játékos>]");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (args.length >= 2 && "vote".equalsIgnoreCase(args[0])) {
            return handleVote(sender, args[1]);
        }

        if (args.length >= 2 && "set".equalsIgnoreCase(args[0])) {
            return handleSet(sender, args);
        }

        if (args.length >= 2 && "clear".equalsIgnoreCase(args[0])) {
            return handleClear(sender, args[1]);
        }

        if (args.length >= 2 && "tax".equalsIgnoreCase(args[0])) {
            return handleTax(sender, args[1]);
        }

        return handleInfo(sender);
    }

    private boolean handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        final FactionType faction = factionManager.getChosenFaction(player.getUniqueId()).orElse(null);
        if (faction == null) {
            sender.sendMessage(messageManager.get("messages.faction-choose-first",
                    "&cKirályi információhoz előbb válassz frakciót: &f/faction join <frakció>&c."));
            return true;
        }
        if (kingManager.isFactionExcluded(faction)) {
            sender.sendMessage(messageManager.get("messages.faction-king-excluded", "&7A(z) %s frakciónak nincs uralkodója.", faction.getDisplayName()));
            return true;
        }

        final UUID king = kingManager.getKing(faction);
        sender.sendMessage(messageManager.get(
                "messages.faction-king-info",
                "&6%s uralkodója: &f%s",
                faction.getDisplayName(),
                king == null
                        ? messageManager.get("messages.faction-king-none", "nincs (szavazz: /faction king vote <játékos>)")
                        : String.valueOf(Bukkit.getOfflinePlayer(king).getName())
        ));

        final Map<UUID, Integer> tally = kingManager.getTally(faction);
        for (final Map.Entry<UUID, Integer> entry : tally.entrySet()) {
            sender.sendMessage(messageManager.get(
                    "messages.faction-king-tally-line",
                    "&7 - %s: &f%s szavazat",
                    String.valueOf(Bukkit.getOfflinePlayer(entry.getKey()).getName()),
                    entry.getValue()
            ));
        }
        return true;
    }

    private boolean handleVote(final CommandSender sender, final String candidateName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        final Player candidate = Bukkit.getPlayerExact(candidateName);
        if (candidate == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA célpont játékos nem elérhető."));
            return true;
        }

        if (!kingManager.vote(player, candidate.getUniqueId())) {
            sender.sendMessage(messageManager.get(
                    "messages.faction-king-vote-failed",
                    "&cNem szavazhatsz: a jelöltnek a te frakciód tagjának kell lennie (és a frakciódnak lehet uralkodója)."
            ));
            return true;
        }

        sender.sendMessage(messageManager.get("messages.faction-king-vote-success", "&aSzavazat leadva: &f%s", candidate.getName()));
        return true;
    }

    private boolean handleSet(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("messages.faction-king-set-usage", "&cHasználat: /faction king set <frakció> <játékos>"));
            return true;
        }

        final FactionType faction = FactionType.fromInput(args[1]);
        final Player target = Bukkit.getPlayerExact(args[2]);
        if (faction == null || target == null) {
            sender.sendMessage(messageManager.get("messages.faction-king-set-usage", "&cHasználat: /faction king set <frakció> <játékos>"));
            return true;
        }

        kingManager.setKing(faction, target.getUniqueId());
        sender.sendMessage(messageManager.get("messages.faction-king-set-success", "&aUralkodó beállítva: &f%s &7-> &e%s", faction.getDisplayName(), target.getName()));
        return true;
    }

    private boolean handleClear(final CommandSender sender, final String rawFaction) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        final FactionType faction = FactionType.fromInput(rawFaction);
        if (faction == null) {
            sender.sendMessage(messageManager.get("messages.faction-unknown", "&cIsmeretlen frakció: &f%s", rawFaction));
            return true;
        }

        kingManager.setKing(faction, null);
        sender.sendMessage(messageManager.get("messages.faction-king-clear-success", "&aA(z) %s trónja megüresedett.", faction.getDisplayName()));
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        final List<String> options = sender.hasPermission(ADMIN_PERMISSION)
                ? List.of("vote", "tax", "set", "clear")
                : List.of("vote", "tax");
        final String action = prefixAt(args, 0);
        final boolean actionComplete = options.contains(action);

        // Két hosszal: 0 = "/faction king " (üres prefix), 1 = gépelés közben — kivéve, ha az
        // args[0] már pontosan egy ismert al-akció, akkor a P=1 pozíció javaslatai jönnek.
        if (args.length == 0 || (args.length == 1 && !actionComplete)) {
            return options.stream().filter(option -> option.startsWith(action)).toList();
        }

        if ("vote".equals(action) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        return List.of();
    }

}
