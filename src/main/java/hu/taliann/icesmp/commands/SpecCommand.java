package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.TalentManager;
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
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final TalentManager talentManager;
    private final MessageManager messageManager;

    public SpecCommand(final SpecializationManager specializationManager, final JobManager jobManager,
                       final ProfessionManager professionManager, final CurrencyManager currencyManager,
                       final FactionManager factionManager, final TalentManager talentManager,
                       final MessageManager messageManager) {
        this.specializationManager = specializationManager;
        this.jobManager = jobManager;
        this.professionManager = professionManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.talentManager = talentManager;
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
            case "respec" -> handleRespec(sender, args);
            case "reset" -> handleReset(sender, args);
            default -> {
                sender.sendMessage(messageManager.get("spec-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    private void handleRespec(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 2 || (!"class".equalsIgnoreCase(args[1]) && !"profession".equalsIgnoreCase(args[1]))) {
            sender.sendMessage(messageManager.get("spec-respec-usage", "&cHasználat: /spec respec <class|profession>"));
            return;
        }

        final boolean classRespec = "class".equalsIgnoreCase(args[1]);
        if (classRespec && specializationManager.getClassSpecialization(player) == null) {
            sender.sendMessage(messageManager.get("spec-respec-nothing", "&cNincs mit visszaváltani: nincs ilyen specializációd."));
            return;
        }
        if (!classRespec && specializationManager.getProfessionSpecialization(player) == null) {
            sender.sendMessage(messageManager.get("spec-respec-nothing", "&cNincs mit visszaváltani: nincs ilyen specializációd."));
            return;
        }

        final double cost = specializationManager.getRespecCost();
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        final CurrencyType currency = CurrencyType.fromFactionType(faction);
        final double balance = currencyManager.getBalance(player, currency);
        if (balance < cost) {
            sender.sendMessage(messageManager.get(
                    "spec-respec-insufficient",
                    "&cA respec ára &f%s %s&c, de csak &f%s&c van a bankodban.",
                    currencyManager.formatBalance(cost),
                    currency.getDisplayName(),
                    currencyManager.formatBalance(balance)
            ));
            return;
        }

        currencyManager.setBalance(player, currency, balance - cost);
        int refundedPoints = 0;
        if (classRespec) {
            specializationManager.resetClassSpecialization(player);
            // Spec-locked talents lapse with the dropped spec; their points return to the pool.
            refundedPoints = talentManager.refundUnavailableTalents(player, true);
        } else {
            specializationManager.resetProfessionSpecialization(player);
        }

        sender.sendMessage(messageManager.get(
                "spec-respec-success",
                "&aSpecializáció visszaváltva &7(ár: &f%s %s&7, visszakapott talentpont: &f%s&7)&a. Újra választhatsz a /spec choose paranccsal.",
                currencyManager.formatBalance(cost),
                currency.getDisplayName(),
                refundedPoints
        ));
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

        sender.sendMessage(messageManager.get("spec-list-profession-header", "&6Szakma specializációk (szint %s-tól):",
                specializationManager.getRequiredProfessionLevel()));

        boolean anyProfessionSpec = false;
        for (final ProfessionSpecializationType specialization : ProfessionSpecializationType.values()) {
            // Only list specs of professions the player actively practices (primaries + secondaries).
            if (!professionManager.hasProfession(player, specialization.getParentProfession())) {
                continue;
            }

            anyProfessionSpec = true;
            final String availability = specializationManager.canSelectProfessionSpecialization(player, specialization)
                    ? messageManager.get("spec-available", "&aVálasztható")
                    : messageManager.get("spec-unavailable", "&cNem elérhető");
            player.sendMessage(Component.text(" - ")
                    .append(specialization.getDisplayName())
                    .append(Component.text(" (" + specialization.getId() + ") "))
                    .append(messageManager.getMessage("spec-availability", "{state}",
                            java.util.Map.of("state", availability))));
        }

        if (!anyProfessionSpec) {
            sender.sendMessage(messageManager.get("spec-list-no-profession", "&7Nincs szakmád."));
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
        sender.sendMessage(messageManager.get("spec-help-respec", "&e/spec respec <class|profession> &7- Specializáció visszaváltása (frakcióvalutáért)."));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("spec-help-reset", "&e/spec reset <játékos> &7- Specializációk törlése (Admin)."));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final List<String> subcommands = sender.hasPermission(ADMIN_PERMISSION)
                ? List.of("list", "choose", "info", "respec", "reset")
                : List.of("list", "choose", "info", "respec");

        final String subcommand = prefixAt(args, 0);
        final boolean subcommandComplete = subcommands.contains(subcommand);

        // Két hosszal: 0 = "/spec " (üres prefix), 1 = gépelés közben — kivéve, ha az args[0]
        // már pontos egyezés, akkor a P=1 pozíció javaslatai jönnek.
        if (args.length == 0 || (args.length == 1 && !subcommandComplete)) {
            return subcommands.stream().filter(option -> option.startsWith(subcommand)).toList();
        }

        if ("choose".equals(subcommand) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
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

        if ("respec".equals(subcommand) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return List.of("class", "profession").stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if ("reset".equals(subcommand) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        return List.of();
    }

    /** Az adott pozíción gépelés alatt álló szó (kisbetűsítve), vagy üres, ha még el sem kezdték. */
    private static String prefixAt(final String[] args, final int index) {
        return args.length > index ? args[index].toLowerCase(Locale.ROOT) : "";
    }
}
