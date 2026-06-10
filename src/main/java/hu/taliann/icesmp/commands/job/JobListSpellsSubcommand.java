package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

public final class JobListSpellsSubcommand implements JobSubcommand {

    private static final String PERMISSION = "icesmp.job.admin";

    private final SpellRegistry spellRegistry;
    private final MessageManager messageManager;

    public JobListSpellsSubcommand(final SpellRegistry spellRegistry, final MessageManager messageManager) {
        this.spellRegistry = spellRegistry;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "listspells";
    }

    @Override
    public String description() {
        return messageManager.get("messages.job-desc-listspells", "Regisztralt varazslatok listazasa (admin).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.job-usage-listspells", "/job listspells");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length > 0) {
            sender.sendMessage(messageManager.get("messages.job-listspells-usage", "&cHasznalat: %s", usage()));
            return true;
        }

        final List<Spell> spells = spellRegistry.getAll().stream()
                .sorted(Comparator.comparing(Spell::getId))
                .toList();

        if (spells.isEmpty()) {
            sender.sendMessage(messageManager.get("messages.job-listspells-empty", "&eNincsenek regisztralt varazslatok."));
            return true;
        }

        final StringJoiner joiner = new StringJoiner(", ");
        for (final Spell spell : spells) {
            joiner.add(spell.getId());
        }

        sender.sendMessage(messageManager.get(
                "messages.job-listspells-result",
                "&6Regisztralt varazslatok (%s): &e%s",
                spells.size(),
                joiner
        ));

        return true;
    }
}


