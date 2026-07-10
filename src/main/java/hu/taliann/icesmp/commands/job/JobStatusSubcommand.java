package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class JobStatusSubcommand implements JobSubcommand {

    private static final String PERMISSION = "icesmp.job.admin";

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final MessageManager messageManager;

    public JobStatusSubcommand(final JavaPlugin plugin, final JobManager jobManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String description() {
        return messageManager.get("messages.job-desc-status", "Jatekos kaszt allapotanak megtekintese (admin).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.job-usage-status", "/job status <player>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageManager.get("messages.job-status-usage", "&cHasznalat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA celjatekos nem erheto el online."));
            return true;
        }

        target.getScheduler().run(plugin, task -> {
            final JobType primaryJob = jobManager.getPrimaryJob(target);

            final String noneText = messageManager.get("messages.job-status-none", "nincs");
            final String primaryName = primaryJob == null ? noneText : primaryJob.getId();
            final int primaryXp = jobManager.getXp(target);
            final int primaryLevel = jobManager.getPrimaryLevel(target);

            sender.sendMessage(messageManager.get(
                    "messages.job-status-result",
                    "&6%s &7| Kaszt: &f%s &7(Lv. &f%s&7, XP: &f%s&7)",
                    target.getName(),
                    primaryName,
                    primaryLevel,
                    primaryXp
            ));
        }, null);

        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        // Két hosszal: 0 = "/job status " (üres prefix), 1 = gépelés közben (args[0] prefix).
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }

        return List.of();
    }
}
