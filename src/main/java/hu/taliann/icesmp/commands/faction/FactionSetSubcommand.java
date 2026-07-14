package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FactionSetSubcommand implements FactionSubcommand {

    private static final String PERMISSION = "icesmp.faction.admin";

    private final JavaPlugin plugin;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final MessageManager messageManager;

    public FactionSetSubcommand(final JavaPlugin plugin, final FactionManager factionManager, final SinManager sinManager,
                                final MessageManager messageManager) {
        this.plugin = plugin;
        this.factionManager = factionManager;
        this.sinManager = sinManager;
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

        // Never Bukkit.getOfflinePlayer(name): for unknown names it fires a BLOCKING Mojang
        // lookup on the region thread. Online-exact first, then the local profile cache.
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
        }
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.faction-set-player-unknown",
                    "&cIsmeretlen játékos (még sosem járt a szerveren): &f%s", args[0]));
            return true;
        }
        final FactionType factionType = FactionType.fromInput(args[1]);

        if (factionType == null) {
            sender.sendMessage(messageManager.get("messages.faction-unknown", "&cIsmeretlen frakció: &f%s", args[1]));
            return true;
        }

        factionManager.setFaction(target.getUniqueId(), factionType);

        // Admin placement into the Dark faction also seals the permanent pact (requires the target online).
        // Folia: sealDarkPact writes the target's PDC — run it on the target's own region thread.
        if (factionType == FactionType.DARK && target.getPlayer() instanceof Player onlineTarget) {
            onlineTarget.getScheduler().run(plugin, task -> sinManager.sealDarkPact(onlineTarget), null);
        }

        sender.sendMessage(messageManager.get(
                "messages.faction-set-target-success",
                "&aFrakció beállítva: &f%s &7-> &f%s",
                target.getName(),
                factionType.getDisplayName()
        ));
        return true;
    }
}



