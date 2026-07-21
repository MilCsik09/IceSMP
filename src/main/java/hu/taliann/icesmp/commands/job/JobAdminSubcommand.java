package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.List;

public final class JobAdminSubcommand implements JobSubcommand {

    private static final String PERMISSION = hu.taliann.icesmp.core.Permissions.JOB;

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final SpellRegistry spellRegistry;
    private final AbilityCatalystListener abilityCatalystListener;
    private final SpecializationManager specializationManager;
    private final MessageManager messageManager;

    public JobAdminSubcommand(final JavaPlugin plugin, final JobManager jobManager, final SpellRegistry spellRegistry,
                              final AbilityCatalystListener abilityCatalystListener,
                              final SpecializationManager specializationManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.spellRegistry = spellRegistry;
        this.abilityCatalystListener = abilityCatalystListener;
        this.specializationManager = specializationManager;
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
        return messageManager.get("admin.job.usage", "/job admin <resetcd|unlockallskills|resetskills|resetclass> <player>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("admin.job.invalid-usage", "&cHasznalat: %s", usage()));
            return true;
        }

        final String action = args[0].toLowerCase(Locale.ROOT);
        if (!"resetcd".equals(action) && !"unlockallskills".equals(action)
                && !"resetskills".equals(action) && !"resetclass".equals(action)) {
            sender.sendMessage(messageManager.get("admin.job.invalid-usage", "&cHasznalat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA celjatekos nem erheto el online."));
            return true;
        }

        // Folia: the target may be in a different region than the admin, so every target read/mutation
        // (cooldown reset, spell-state PDC writes) runs on the target's own region thread.
        // sender.sendMessage is safe from there.
        target.getScheduler().run(plugin, task -> {
            if ("resetcd".equals(action)) {
                abilityCatalystListener.resetCooldowns(target);
                sender.sendMessage(messageManager.get(
                        "admin.job.reset-cooldowns.success",
                        "&aVarazslat cooldownok torolve: &f%s",
                        target.getName()
                ));
                target.sendMessage(messageManager.get("admin.job.reset-cooldowns.notify", "&eEgy admin torolte a spell cooldownjaidat."));
                return;
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
                return;
            }

            if ("resetskills".equals(action)) {
                jobManager.setUnlockedSpellIds(target, List.of());
                abilityCatalystListener.resetAllSpellState(target);
                sender.sendMessage(messageManager.get(
                        "admin.job.reset-skills.success",
                        "&aMinden varazslat allapot alaphelyzetbe allitva: &f%s",
                        target.getName()
                ));
                target.sendMessage(messageManager.get("admin.job.reset-skills.notify", "&eEgy admin alaphelyzetbe allitotta a varazslataidat."));
                return;
            }

            // resetclass: full class wipe — both job slots + XP/levels, the class specialization,
            // and all unlocked spells + spell state. The player can then pick a fresh class.
            jobManager.resetClass(target);
            specializationManager.resetClassSpecialization(target);
            abilityCatalystListener.resetAllSpellState(target);
            sender.sendMessage(messageManager.get(
                    "admin.job.reset-class.success",
                    "&aKaszt teljesen alaphelyzetbe allitva (kaszt + spec + spellek): &f%s",
                    target.getName()
            ));
            target.sendMessage(messageManager.get("admin.job.reset-class.notify",
                    "&eEgy admin alaphelyzetbe allitotta a kasztodat — valassz ujat a /profile menubol."));
        }, null);
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        final List<String> actions = List.of("resetcd", "unlockallskills", "resetskills", "resetclass");
        final String action = prefixAt(args, 0);
        final boolean actionComplete = actions.contains(action);

        // Két hosszal: 0 = "/job admin " (üres prefix), 1 = gépelés közben — kivéve, ha az args[0]
        // már pontos egyezés, akkor a P=1 (játékosnév) pozíció javaslatai jönnek.
        if (args.length == 0 || (args.length == 1 && !actionComplete)) {
            return actions.stream().filter(value -> value.startsWith(action)).toList();
        }

        if ((args.length == 1 && actionComplete) || args.length == 2) {
            final String prefix = prefixAt(args, 1);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }

        return List.of();
    }

    /** Az adott pozíción gépelés alatt álló szó (kisbetűsítve), vagy üres, ha még el sem kezdték. */
    private static String prefixAt(final String[] args, final int index) {
        return args.length > index ? args[index].toLowerCase(Locale.ROOT) : "";
    }
}


