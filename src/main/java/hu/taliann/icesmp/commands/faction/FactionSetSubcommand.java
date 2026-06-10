package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

public final class FactionSetSubcommand implements FactionSubcommand {

    private static final String PERMISSION = "icesmp.faction.admin";

    private final FactionManager factionManager;
    private final MessageManager messageManager;

    public FactionSetSubcommand(final FactionManager factionManager, final MessageManager messageManager) {
        this.factionManager = factionManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "set";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-set", "Játékos frakciójának beállítása (admin).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-set", "/faction set <player> <faction>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("messages.faction-set-usage", "&cHasználat: %s", usage()));
            return true;
        }

        final OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        final FactionType factionType = FactionType.fromInput(args[1]);

        if (factionType == null) {
            sender.sendMessage(messageManager.get("messages.faction-unknown", "&cIsmeretlen frakció: &f%s", args[1]));
            return true;
        }

        factionManager.setFaction(target.getUniqueId(), factionType);
        sender.sendMessage(messageManager.get(
                "messages.faction-set-target-success",
                "&aFrakció beállítva: &f%s &7-> &f%s",
                target.getName(),
                factionType.getDisplayName()
        ));
        return true;
    }
}



