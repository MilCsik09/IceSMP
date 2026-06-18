package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.commands.job.JobAddXpSubcommand;
import hu.taliann.icesmp.commands.job.JobAdminSubcommand;
import hu.taliann.icesmp.commands.job.JobGiveCatalystSubcommand;
import hu.taliann.icesmp.commands.job.JobListSpellsSubcommand;
import hu.taliann.icesmp.commands.job.JobSetXpSubcommand;
import hu.taliann.icesmp.commands.job.JobStatusSubcommand;
import hu.taliann.icesmp.commands.job.JobSubcommand;
import hu.taliann.icesmp.commands.job.JobUnlockSpellSubcommand;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JobCommand implements BasicCommand {

    private final Map<String, JobSubcommand> subcommands = new LinkedHashMap<>();
    private final MessageManager messageManager;

    public JobCommand(final org.bukkit.plugin.java.JavaPlugin plugin, final JobManager jobManager,
                      final SpellRegistry spellRegistry, final CatalystItemFactory catalystItemFactory,
                      final AbilityCatalystListener abilityCatalystListener, final MessageManager messageManager) {
        this.messageManager = messageManager;
        register(new JobAddXpSubcommand(jobManager, messageManager));
        register(new JobSetXpSubcommand(jobManager, messageManager));
        register(new JobStatusSubcommand(jobManager, messageManager));
        register(new JobUnlockSpellSubcommand(jobManager, spellRegistry, messageManager));
        register(new JobGiveCatalystSubcommand(plugin, jobManager, catalystItemFactory, messageManager));
        register(new JobListSpellsSubcommand(spellRegistry, messageManager));
        register(new JobAdminSubcommand(jobManager, spellRegistry, abilityCatalystListener, messageManager));
    }

    private void register(final JobSubcommand subcommand) {
        subcommands.put(subcommand.name().toLowerCase(), subcommand);
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        final JobSubcommand subcommand = subcommands.get(args[0].toLowerCase());
        if (subcommand == null) {
            sender.sendMessage(messageManager.get("messages.job-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
            sendHelp(sender);
            return;
        }

        final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        subcommand.execute(sender, subArgs);
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            return subcommands.keySet().stream().toList();
        }

        if (args.length == 1) {
            return subcommands.keySet().stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        final JobSubcommand subcommand = subcommands.get(args[0].toLowerCase());
        if (subcommand == null) {
            return List.of();
        }

        final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subcommand.tabComplete(sender, subArgs);
    }

    private void sendHelp(final CommandSender sender) {
        final String header = messageManager.get("messages.job-help-header", "&6/job &7- elerheto parancsok:");
        sender.sendMessage(header);
        for (final JobSubcommand subcommand : subcommands.values()) {
            sender.sendMessage(messageManager.get(
                    "messages.job-help-" + subcommand.name(),
                    "&e" + subcommand.usage() + " &7- " + subcommand.description()
            ));
        }
    }
}


