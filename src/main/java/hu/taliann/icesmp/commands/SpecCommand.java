package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class SpecCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "icesmp.admin.spec";

    private final SpecializationManager specializationManager;
    private final JobManager jobManager;
    private final ProfessionManager professionManager;
    private final MessageManager messageManager;

    public SpecCommand(final SpecializationManager specializationManager, final JobManager jobManager,
                       final ProfessionManager professionManager, final MessageManager messageManager) {
        this.specializationManager = specializationManager;
        this.jobManager = jobManager;
        this.professionManager = professionManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> handleList(sender);
            case "choose" -> handleChoose(sender, args);
            case "info" -> handleInfo(sender);
            case "reset" -> handleReset(sender, args);
            default -> {
                sender.sendMessage(messageManager.get("spec-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    private void handleList(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final JobType primaryJob = jobManager.getPrimaryJob(player);
        sender.sendMessage(messageManager.get("spec-list-class-header", "&6Kaszt specializációk (szint %s-tól):",
                specializationManager.getRequiredClassLevel()));
        if (primaryJob == null) {
            sender.sendMessage(messageManager.get("spec-list-no-class", "&7Nincs elsődleges kasztod."));
        } else {
            for (final SpecializationType specialization : SpecializationType.values()) {
                if (specialization.getParentJob() != primaryJob) {
                    continue;
                }

                final String availability = specializationManager.canSelectClassSpecialization(player, specialization)
                        ? messageManager.get("spec-available", "&aVálasztható")
                        : messageManager.get("spec-unavailable", "&cNem elérhető");
                player.sendMessage(Component.text(" - ")
                        .append(specialization.getDisplayName())
                        .append(Component.text(" (" + specialization.getId() + ") "))
                        .append(messageManager.getMessage("spec-availability", "{state}",
                                java.util.Map.of("state", availability))));
            }
        }

        final ProfessionType profession = professionManager.getProfession(player);
        sender.sendMessage(messageManager.get("spec-list-profession-header", "&6Szakma specializációk (szint %s-tól):",
                specializationManager.getRequiredProfessionLevel()));
        if (profession == null) {
            sender.sendMessage(messageManager.get("spec-list-no-profession", "&7Nincs szakmád."));
            return;
        }

        for (final ProfessionSpecializationType specialization : ProfessionSpecializationType.values()) {
            if (specialization.getParentProfession() != profession) {
                continue;
            }

            final String availability = specializationManager.canSelectProfessionSpecialization(player, specialization)
                    ? messageManager.get("spec-available", "&aVálasztható")
                    : messageManager.get("spec-unavailable", "&cNem elérhető");
            player.sendMessage(Component.text(" - ")
                    .append(specialization.getDisplayName())
                    .append(Component.text(" (" + specialization.getId() + ") "))
                    .append(messageManager.getMessage("spec-availability", "{state}",
                            java.util.Map.of("state", availability))));
        }
    }

    private void handleChoose(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("spec-choose-usage", "&cHasználat: /spec choose <specializáció>"));
            return;
        }

        final SpecializationType classSpec = SpecializationType.fromId(args[1]);
        if (classSpec != null) {
            if (specializationManager.selectClassSpecialization(player, classSpec)) {
                player.sendMessage(messageManager.getMessage("spec-choose-success", "&aSpecializáció kiválasztva:")
                        .append(Component.space())
                        .append(classSpec.getDisplayName()));
            } else {
                player.sendMessage(messageManager.get(
                        "spec-choose-failed",
                        "&cNem választhatod ezt a specializációt (kaszt, szint, frakció vagy bűnös feltétel hiányzik)."
                ));
            }
            return;
        }

        final ProfessionSpecializationType professionSpec = ProfessionSpecializationType.fromId(args[1]);
        if (professionSpec != null) {
            if (specializationManager.selectProfessionSpecialization(player, professionSpec)) {
                player.sendMessage(messageManager.getMessage("spec-choose-success", "&aSpecializáció kiválasztva:")
                        .append(Component.space())
                        .append(professionSpec.getDisplayName()));
            } else {
                player.sendMessage(messageManager.get(
                        "spec-choose-failed-profession",
                        "&cNem választhatod ezt a specializációt (szakma vagy szint feltétel hiányzik)."
                ));
            }
            return;
        }

        sender.sendMessage(messageManager.get("spec-unknown", "&cIsmeretlen specializáció: &f%s", args[1]));
    }

    private void handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final SpecializationType classSpec = specializationManager.getClassSpecialization(player);
        final ProfessionSpecializationType professionSpec = specializationManager.getProfessionSpecialization(player);

        player.sendMessage(messageManager.getMessage("spec-info-class", "&6Kaszt specializáció: ")
                .append(classSpec == null
                        ? messageManager.getMessage("spec-info-none", "&7nincs")
                        : classSpec.getDisplayName()));
        player.sendMessage(messageManager.getMessage("spec-info-profession", "&6Szakma specializáció: ")
                .append(professionSpec == null
                        ? messageManager.getMessage("spec-info-none", "&7nincs")
                        : professionSpec.getDisplayName()));
    }

    private void handleReset(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("spec-reset-usage", "&cHasználat: /spec reset <játékos>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("target-player-offline", "&cA célpont játékos nem elérhető."));
            return;
        }

        specializationManager.resetSpecializations(target);
        sender.sendMessage(messageManager.get("spec-reset-success", "&aSpecializációk törölve: &f%s", target.getName()));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("spec-help-header", "&6/spec &7- Elérhető parancsok:"));
        sender.sendMessage(messageManager.get("spec-help-list", "&e/spec list &7- Választható specializációk."));
        sender.sendMessage(messageManager.get("spec-help-choose", "&e/spec choose <specializáció> &7- Specializáció kiválasztása."));
        sender.sendMessage(messageManager.get("spec-help-info", "&e/spec info &7- Aktuális specializációid."));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("spec-help-reset", "&e/spec reset <játékos> &7- Specializációk törlése (Admin)."));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final List<String> subcommands = sender.hasPermission(ADMIN_PERMISSION)
                ? List.of("list", "choose", "info", "reset")
                : List.of("list", "choose", "info");

        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return subcommands.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && "choose".equals(subcommand)) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            final List<String> options = new ArrayList<>();
            for (final SpecializationType specialization : SpecializationType.values()) {
                if (specialization.getId().startsWith(prefix)) {
                    options.add(specialization.getId());
                }
            }
            for (final ProfessionSpecializationType specialization : ProfessionSpecializationType.values()) {
                if (specialization.getId().startsWith(prefix)) {
                    options.add(specialization.getId());
                }
            }
            return options;
        }

        if (args.length == 2 && "reset".equals(subcommand)) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        return List.of();
    }
}
