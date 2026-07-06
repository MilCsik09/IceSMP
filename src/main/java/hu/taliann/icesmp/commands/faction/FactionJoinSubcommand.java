package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class FactionJoinSubcommand implements FactionSubcommand {

    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final MessageManager messageManager;

    public FactionJoinSubcommand(final FactionManager factionManager, final SinManager sinManager,
                                 final MessageManager messageManager) {
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "join";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-join", "Belépés egy frakcióba.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-join", "/faction join <faction>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageManager.get("messages.faction-join-usage", "&cHasználat: %s", usage()));
            return true;
        }

        final FactionType factionType = FactionType.fromInput(args[0]);
        if (factionType == null) {
            sender.sendMessage(messageManager.get("messages.faction-unknown", "&cIsmeretlen frakció: &f%s", args[0]));
            return true;
        }

        if (factionType == FactionType.DARK) {
            if (!sinManager.isSinner(player)) {
                sender.sendMessage(messageManager.get(
                        "messages.faction-dark-sinners-only",
                        "&5A Sötét frakcióba csak bűnösök léphetnek be."
                ));
                return true;
            }

            factionManager.setFaction(player.getUniqueId(), FactionType.DARK);
            sinManager.sealDarkPact(player);
            sender.sendMessage(messageManager.get(
                    "messages.faction-dark-pact-sealed",
                    "&5A sötét paktum megköttetett. A bűnöd mostantól örökre veled marad."
            ));
            return true;
        }

        factionManager.setFaction(player.getUniqueId(), factionType);
        sender.sendMessage(messageManager.get("messages.faction-set-self-success", "&aFrakció beállítva: &f%s", factionType.getDisplayName()));
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            return List.of("red", "blue", "neutral", "dark");
        }
        return List.of();
    }
}



