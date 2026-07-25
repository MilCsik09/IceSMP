package hu.taliann.icesmp.commands.job;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class JobUnlockSpellSubcommand implements JobSubcommand {

    private static final String PERMISSION = hu.taliann.icesmp.core.Permissions.JOB;

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final SpellRegistry spellRegistry;
    private final MessageManager messageManager;

    public JobUnlockSpellSubcommand(final JavaPlugin plugin, final JobManager jobManager,
                                    final SpellRegistry spellRegistry,
                                    final MessageManager messageManager) {
        this.plugin = plugin;
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
        return messageManager.get("messages.job-desc-unlockspell", "Varázslat feloldása egy játékosnak (admin).");
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
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA céljátékos nem érhető el online."));
            return true;
        }

        final Spell spell = spellRegistry.getById(args[1]);
        if (spell == null) {
            sender.sendMessage(messageManager.get("messages.job-unknown-spell", "&cIsmeretlen varázslat: &f%s", args[1]));
            return true;
        }

        // Folia: unlockSpell writes the target's PDC, so it must run on the target's own region
        // thread (the target may be in a different region than the admin). sender.sendMessage is safe
        // from there.
        target.getScheduler().run(plugin, task -> {
            if (!jobManager.unlockSpell(target, spell.getId())) {
                sender.sendMessage(messageManager.get("messages.job-spell-already-unlocked", "&eEz a varázslat már fel van oldva."));
                return;
            }

            sender.sendMessage(messageManager.get(
                    "messages.job-unlockspell-success",
                    "&aVarázslat feloldva: &f%s &7-> &e%s",
                    target.getName(),
                    spell.getId()
            ));
        }, null);
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        final boolean playerComplete = args.length >= 1 && Bukkit.getPlayerExact(args[0]) != null;

        // Két hosszal: 0 = "/job unlockspell " (üres prefix), 1 = gépelés közben — kivéve, ha az
        // args[0] már online játékosnévre pontosan illeszkedik, akkor a P=1 (spellId) jön.
        if (args.length == 0 || (args.length == 1 && !playerComplete)) {
            final String prefix = prefixAt(args, 0);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        if ((args.length == 1 && playerComplete) || args.length == 2) {
            final String prefix = prefixAt(args, 1);
            return spellRegistry.getAll().stream()
                    .map(Spell::getId)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        return List.of();
    }

}

