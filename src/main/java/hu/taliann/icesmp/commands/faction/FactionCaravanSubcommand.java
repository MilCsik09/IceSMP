package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.managers.PlayerCaravanManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * B6 — /faction caravan send <összeg>: a király a frakciókasszából szállítmányt
 * indít (őrzőpont + védési ablak); a logika a PlayerCaravanManagerben él.
 */
public final class FactionCaravanSubcommand implements FactionSubcommand {

    private final PlayerCaravanManager caravanManager;
    private final KingManager kingManager;
    private final MessageManager messageManager;
    /** A Vének Tanácsa (setterrel kötve; null = nincs tanács-jog). */
    private volatile hu.taliann.icesmp.managers.CouncilManager councilManager;

    public void setCouncilManager(final hu.taliann.icesmp.managers.CouncilManager councilManager) {
        this.councilManager = councilManager;
    }

    public FactionCaravanSubcommand(final PlayerCaravanManager caravanManager,
                                    final KingManager kingManager, final MessageManager messageManager) {
        this.caravanManager = caravanManager;
        this.kingManager = kingManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "caravan";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-caravan",
                "Szállítmány indítása a kasszából (király) — védhető és rabolható!");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-caravan", "/faction caravan send <összeg>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }
        if (args.length < 2 || !"send".equalsIgnoreCase(args[0])) {
            sender.sendMessage(messageManager.get("messages.faction-caravan-usage", "&cHasználat: %s", usage()));
            return true;
        }
        final hu.taliann.icesmp.managers.CouncilManager councilRef = councilManager;
        // A Menedékben a Vének Tanácsa tölti be a király gazdasági szerepét.
        if (!kingManager.isKing(player)
                && (councilRef == null || !councilRef.isCouncillor(player.getUniqueId()))) {
            sender.sendMessage(messageManager.get("faction-caravan-not-king",
                    "&cSzállítmányt csak a frakciód KIRÁLYA (a Menedékben: a Vének Tanácsa) indíthat."));
            return true;
        }
        final double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("faction-caravan-bad-amount", "&cÉrvénytelen összeg."));
            return true;
        }
        final String error = caravanManager.send(player, amount);
        if (error != null) {
            sender.sendMessage(messageManager.get("faction-caravan-error." + error, switch (error) {
                case "pcaravan-disabled" -> "&cA játékos-karaván ki van kapcsolva.";
                case "pcaravan-busy" -> "&cMár úton van egy szállítmány — várd meg a végét.";
                case "pcaravan-no-faction" -> "&cA Menedék vendégeként nincs frakciókasszád; előbb válassz frakciót.";
                case "pcaravan-cooldown" -> "&cA frakciód karavánja még pihen — nézz vissza később.";
                case "pcaravan-bad-amount" -> "&cA rakomány értéke a megengedett sávon kívül esik.";
                case "pcaravan-poor" -> "&cNincs elég fedezet a frakciókasszában.";
                case "pcaravan-no-site" -> "&cNem találtunk biztonságos őrzőpontot — próbáld máshonnan.";
                default -> "&cA szállítmány nem indítható.";
            }));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length <= 1) {
            return List.of("send");
        }
        if (args.length == 2) {
            return List.of("100", "250", "500", "1000");
        }
        return List.of();
    }
}
