package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /faction raid <célfrakció> — csak a frakció királya hirdethet raidet;
 * a nevezési díj a frakciókasszából megy (money sink).
 */
public final class FactionRaidSubcommand implements FactionSubcommand {

    private final RaidManager raidManager;
    private final KingManager kingManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;

    public FactionRaidSubcommand(final RaidManager raidManager, final KingManager kingManager,
                                 final FactionManager factionManager, final MessageManager messageManager) {
        this.raidManager = raidManager;
        this.kingManager = kingManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "raid";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-raid", "Raid meghirdetése (csak király).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-raid", "/faction raid <célfrakció>");
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

        if (!kingManager.isKing(player)) {
            sender.sendMessage(messageManager.get("messages.faction-raid-not-king", "&cRaidet csak a frakciód királya hirdethet."));
            return true;
        }

        final FactionType defender = FactionType.fromInput(args[0]);
        if (defender == null) {
            sender.sendMessage(messageManager.get("messages.faction-unknown", "&cIsmeretlen frakció: &f%s", args[0]));
            return true;
        }

        final FactionType attacker = factionManager.getFaction(player.getUniqueId());
        final String errorKey = raidManager.startRaid(attacker, defender);
        if (errorKey != null) {
            sender.sendMessage(messageManager.get("messages." + errorKey, defaultErrorFor(errorKey)));
            return true;
        }

        return true;
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "faction-raid-already-active" -> "&cMár zajlik egy raid.";
            case "faction-raid-invalid-target" -> "&cÉrvénytelen célpont.";
            case "faction-raid-protected-target" -> "&cEz a frakció védett, nem raidelhető.";
            case "faction-raid-no-funds" -> "&cNincs elég pénz a frakciókasszában a nevezési díjhoz.";
            default -> "&cA raid indítása nem sikerült.";
        };
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(FactionType.values())
                    .map(faction -> faction.name().toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }
}
