package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.listeners.SpellbookListener;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.List;

public final class JobAdminSubcommand implements JobSubcommand {

    private static final String PERMISSION = "icesmp.admin";

    private final JobManager jobManager;
    private final SpellRegistry spellRegistry;
    private final SpellbookListener spellbookListener;
    private final MessageManager messageManager;

    public JobAdminSubcommand(final JobManager jobManager, final SpellRegistry spellRegistry,
                              final SpellbookListener spellbookListener, final MessageManager messageManager) {
        this.jobManager = jobManager;
        this.spellRegistry = spellRegistry;
        this.spellbookListener = spellbookListener;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "admin";
    }

    @Override
    public String description() {
        return messageManager.get("admin.job.description", "Admin segedparancsok.");
    }

    @Override
    public String usage() {
        return messageManager.get("admin.job.usage", "/job admin <resetcd|unlockallskills|resetskills> <player>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("admin.job.invalid-usage", "&cHasznalat: %s", usage()));
            return true;
        }

        final String action = args[0].toLowerCase(Locale.ROOT);
        if (!"resetcd".equals(action) && !"unlockallskills".equals(action) && !"resetskills".equals(action)) {
            sender.sendMessage(messageManager.get("admin.job.invalid-usage", "&cHasznalat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("system.target-player-offline", "&cA celjatekos nem erheto el online."));
            return true;
        }

        if ("resetcd".equals(action)) {
            spellbookListener.resetCooldowns(target);
            sender.sendMessage(messageManager.get(
                    "admin.job.reset-cooldowns.success",
                    "&aVarazslat cooldownok torolve: &f%s",
                    target.getName()
            ));
            target.sendMessage(messageManager.get("admin.job.reset-cooldowns.notify", "&eEgy admin torolte a spell cooldownjaidat."));
            return true;
        }

        if ("unlockallskills".equals(action)) {
            final List<String> allSpellIds = spellRegistry.getAll().stream().map(Spell::getId).toList();
            jobManager.setUnlockedSpellIds(target, allSpellIds);
            sender.sendMessage(messageManager.get(
                    "admin.job.unlock-all.success",
                    "&aAz osszes varazslat feloldva: &f%s",
                    target.getName()
            ));
            target.sendMessage(messageManager.get("admin.job.unlock-all.notify", "&eEgy admin feloldotta neked az osszes varazslatot."));
            return true;
        }

        jobManager.setUnlockedSpellIds(target, List.of());
        spellbookListener.resetAllSpellState(target);
        sender.sendMessage(messageManager.get(
                "admin.job.reset-skills.success",
                "&aMinden varazslat allapot alaphelyzetbe allitva: &f%s",
                target.getName()
        ));
        target.sendMessage(messageManager.get("admin.job.reset-skills.notify", "&eEgy admin alaphelyzetbe allitotta a varazslataidat."));
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            return java.util.stream.Stream.of("resetcd", "unlockallskills", "resetskills")
                    .filter(value -> value.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}


