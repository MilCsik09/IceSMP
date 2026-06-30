package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.commands.job.JobAddXpSubcommand;
import hu.taliann.icesmp.commands.job.JobAdminSubcommand;
import hu.taliann.icesmp.commands.job.JobGiveCatalystSubcommand;
import hu.taliann.icesmp.commands.job.JobListSpellsSubcommand;
import hu.taliann.icesmp.commands.job.JobSetXpSubcommand;
import hu.taliann.icesmp.commands.job.JobStatusSubcommand;
import hu.taliann.icesmp.commands.job.JobUnlockSpellSubcommand;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class JobCommand extends AbstractDispatchCommand {

    public JobCommand(final JavaPlugin plugin, final JobManager jobManager,
                      final SpellRegistry spellRegistry, final CatalystItemFactory catalystItemFactory,
                      final AbilityCatalystListener abilityCatalystListener, final MessageManager messageManager) {
        super(messageManager, "job", "&6/job &7- elerheto parancsok:");
        register(new JobAddXpSubcommand(plugin, jobManager, messageManager));
        register(new JobSetXpSubcommand(plugin, jobManager, messageManager));
        register(new JobStatusSubcommand(jobManager, messageManager));
        register(new JobUnlockSpellSubcommand(plugin, jobManager, spellRegistry, messageManager));
        register(new JobGiveCatalystSubcommand(plugin, jobManager, catalystItemFactory, messageManager));
        register(new JobListSpellsSubcommand(spellRegistry, messageManager));
        register(new JobAdminSubcommand(plugin, jobManager, spellRegistry, abilityCatalystListener, messageManager));
    }
}
