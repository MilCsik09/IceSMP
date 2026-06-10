package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class JobStatusSubcommand implements JobSubcommand {

    private static final String PERMISSION = "icesmp.job.admin";

    private final JobManager jobManager;
    private final MessageManager messageManager;

    public JobStatusSubcommand(final JobManager jobManager, final MessageManager messageManager) {
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

        final JobType primaryJob = jobManager.getPrimaryJob(target);
        final JobType secondaryJob = jobManager.getSecondaryJob(target);

        final String noneText = messageManager.get("messages.job-status-none", "nincs");
        final String primaryName = primaryJob == null ? noneText : primaryJob.getId();
        final String secondaryName = secondaryJob == null ? noneText : secondaryJob.getId();
        final int primaryXp = jobManager.getXp(target, true);
        final int secondaryXp = jobManager.getXp(target, false);
        final int primaryLevel = jobManager.getLevel(target, true);
        final int secondaryLevel = jobManager.getLevel(target, false);

        sender.sendMessage(messageManager.get(
                "messages.job-status-result",
                "&6%s &7| Primary: &f%s &7(Lv. &f%s&7, XP: &f%s&7) &8| &7Secondary: &f%s &7(Lv. &f%s&7, XP: &f%s&7)",
                target.getName(),
                primaryName,
                primaryLevel,
                primaryXp,
                secondaryName,
                secondaryLevel,
                secondaryXp
        ));

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

