package hu.taliann.icesmp.commands.job;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
    public String permission() { return PERMISSION; }

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
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA céljátékos nem érhető el online."));
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
                CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
                for (final Spell spell : spellRegistry.getAll()) {
                    chain = chain.thenCompose(ignored -> jobManager.unlockSpellV2(
                                    target, spell.getId(), JobManager.SOURCE_ADMIN)
                            .thenApply(changed -> null));
                }
                chain.whenComplete((ignored, failure) -> target.getScheduler().run(plugin, followup -> {
                    if (failure != null) {
                        sender.sendMessage(messageManager.get("admin.job.unlock-all.persistence-failed",
                                "&cA PlayerProfile spellbook frissítése meghiúsult: &f%s",
                                target.getName()));
                        return;
                    }
                    sender.sendMessage(messageManager.get(
                            "admin.job.unlock-all.success",
                            "&aAz összes varázslat tartósan feloldva: &f%s",
                            target.getName()));
                    target.sendMessage(messageManager.get("admin.job.unlock-all.notify",
                            "&eEgy admin feloldotta neked az összes varázslatot."));
                }, null));
                return;
            }

            if ("resetskills".equals(action)) {
                jobManager.clearSpellGrantsV2(target)
                        .whenComplete((ignored, failure) -> target.getScheduler().run(plugin, followup -> {
                            if (failure != null) {
                                sender.sendMessage(messageManager.get("admin.job.reset-skills.persistence-failed",
                                        "&cA PlayerProfile spellbook törlése meghiúsult: &f%s",
                                        target.getName()));
                                return;
                            }
                            abilityCatalystListener.resetAllSpellState(target);
                            sender.sendMessage(messageManager.get(
                                    "admin.job.reset-skills.success",
                                    "&aMinden varázslat állapot tartósan alaphelyzetbe állítva: &f%s",
                                    target.getName()));
                            target.sendMessage(messageManager.get("admin.job.reset-skills.notify",
                                    "&eEgy admin alaphelyzetbe állította a varázslataidat."));
                        }, null));
                return;
            }

            // resetclass: full class wipe — both job slots + XP/levels, the class specialization,
            // and all unlocked spells + spell state. The player can then pick a fresh class.
                final long revision = specializationManager.profileGateway()
                        .diagnostic(target.getUniqueId()).revision();
                specializationManager.resetClassSpecSection(target, true,
                                "admin-class-reset:" + target.getUniqueId() + ":" + revision)
                        .whenComplete((result, failure) -> target.getScheduler().run(plugin, followup -> {
                            if (failure != null || result == null || !result.durableMutationApplied()) {
                                sender.sendMessage(messageManager.get(
                                        "admin.job.reset-class.persistence-failed",
                                        "&cA Profile v2 kaszt-reset meghiúsult: &f%s",
                                        target.getName()));
                                return;
                            }
                            if (!result.committed()) {
                                specializationManager.profileGateway().blockSession(target.getUniqueId(),
                                        "Admin class-reset committed, but runtime reconciliation failed");
                                sender.sendMessage(messageManager.get(
                                        "admin.job.reset-class.runtime-failed",
                                        "&cA profil commitolt, de a runtime-befejezés hibázott; a session blokkolva: &f%s",
                                        target.getName()));
                                return;
                            }
                            try {
                                specializationManager.resetProfessionSpecialization(target);
                                abilityCatalystListener.resetAllSpellState(target);
                                sender.sendMessage(messageManager.get(
                                        "admin.job.reset-class.success",
                                        "&aKaszt teljesen alaphelyzetbe állítva (kaszt + spec + varázslatok): &f%s",
                                        target.getName()));
                                target.sendMessage(messageManager.get("admin.job.reset-class.notify",
                                        "&eEgy adminisztrátor alaphelyzetbe állította a kasztodat — válassz újat a /profile menüből."));
                            } catch (final Throwable mirrorFailure) {
                                specializationManager.profileGateway().blockSession(target.getUniqueId(),
                                        "Admin class-reset runtime cleanup failed after Profile v2 commit");
                                sender.sendMessage(messageManager.get(
                                        "admin.job.reset-class.mirror-failed",
                                        "&cA profil commit sikerült, de a runtime/XP cleanup hibázott; a session blokkolva: &f%s",
                                        target.getName()));
                            }
                        }, () -> specializationManager.profileGateway().blockSession(
                                target.getUniqueId(),
                                "Admin class-reset runtime scheduler rejected after Profile v2 commit")));
                return;
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

}
