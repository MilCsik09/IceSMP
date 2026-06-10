package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class JobUnlockSpellSubcommand implements JobSubcommand {

    private static final String PERMISSION = "icesmp.job.admin";

    private final JobManager jobManager;
    private final SpellRegistry spellRegistry;
    private final MessageManager messageManager;

    public JobUnlockSpellSubcommand(final JobManager jobManager, final SpellRegistry spellRegistry,
                                    final MessageManager messageManager) {
        this.jobManager = jobManager;
        this.spellRegistry = spellRegistry;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "unlockspell";
    }

    @Override
    public String description() {
        return messageManager.get("messages.job-desc-unlockspell", "Varazslat feloldasa egy jatekosnak (admin).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.job-usage-unlockspell", "/job unlockspell <player> <spellId>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("messages.job-unlockspell-usage", "&cHasznalat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA celjatekos nem erheto el online."));
            return true;
        }

        final Spell spell = spellRegistry.getById(args[1]);
        if (spell == null) {
            sender.sendMessage(messageManager.get("messages.job-unknown-spell", "&cIsmeretlen varazslat: &f%s", args[1]));
            return true;
        }

        if (!jobManager.unlockSpell(target, spell.getId())) {
            sender.sendMessage(messageManager.get("messages.job-spell-already-unlocked", "&eEz a varazslat mar fel van oldva."));
            return true;
        }

        sender.sendMessage(messageManager.get(
                "messages.job-unlockspell-success",
                "&aVarazslat feloldva: &f%s &7-> &e%s",
                target.getName(),
                spell.getId()
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

        if (args.length == 2) {
            return spellRegistry.getAll().stream()
                    .map(Spell::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}

