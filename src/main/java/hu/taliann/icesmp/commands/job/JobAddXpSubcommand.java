package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class JobAddXpSubcommand implements JobSubcommand {

    private static final String PERMISSION = "icesmp.job.admin";

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final MessageManager messageManager;

    public JobAddXpSubcommand(final JavaPlugin plugin, final JobManager jobManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "addxp";
    }

    @Override
    public String description() {
        return messageManager.get("messages.job-desc-addxp", "Admin XP hozzaadas a kaszthoz.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.job-usage-addxp", "/job addxp <player> <amount>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("messages.job-addxp-usage", "&cHasznalat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA celjatekos nem erheto el online."));
            return true;
        }

        final int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("messages.invalid-amount", "&cErvenytelen osszeg."));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(messageManager.get("messages.amount-must-be-positive", "&cAz osszegnek pozitivnak kell lennie."));
            return true;
        }

        // Folia: addXpToJob writes the target's PDC and may message them on level-up, so it must run
        // on the target's own region thread (the target may be in a different region than the admin).
        // sender.sendMessage is safe from there.
        target.getScheduler().run(plugin, task -> {
            if (!jobManager.addXpToJob(target, amount)) {
                sender.sendMessage(messageManager.get("messages.job-slot-not-set", "&cA celpontnak nincs kasztja."));
                return;
            }

            final int currentXp = jobManager.getXp(target);
            final int currentLevel = jobManager.getPrimaryLevel(target);
            sender.sendMessage(messageManager.get(
                    "messages.job-addxp-success",
                    "&aXP hozzaadva: &f%s &7| +&f%s XP &7| Uj XP: &f%s &7| Szint: &f%s",
                    target.getName(),
                    amount,
                    currentXp,
                    currentLevel
            ));
        }, null);
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}
