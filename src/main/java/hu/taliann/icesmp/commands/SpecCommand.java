package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;

import hu.taliann.icesmp.classspec.application.ProfileDiagnostic;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.RespecService;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class SpecCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "icesmp.admin.spec";
    private static final String RECOVERY_PERMISSION =
            hu.taliann.icesmp.core.Permissions.SPEC_RECOVER;

    private final JavaPlugin plugin;
    private final SpecializationManager specializationManager;
    private final JobManager jobManager;
    private final ProfessionManager professionManager;
    private final CurrencyManager currencyManager;
    private final RespecService respecService;
    private final MessageManager messageManager;

    public SpecCommand(final JavaPlugin plugin,
                       final SpecializationManager specializationManager,
                       final JobManager jobManager,
                       final ProfessionManager professionManager,
                       final CurrencyManager currencyManager,
                       final MessageManager messageManager,
                       final RespecService respecService) {
        this.plugin = plugin;
        this.specializationManager = specializationManager;
        this.jobManager = jobManager;
        this.professionManager = professionManager;
        this.currencyManager = currencyManager;
        this.respecService = respecService;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack,
                        final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> handleList(sender);
            case "choose" -> handleChoose(sender, args);
            case "doctrine" -> handleDoctrine(sender, args);
            case "info" -> handleInfo(sender);
            case "respec" -> handleRespec(sender, args);
            case "reset" -> handleReset(sender, args);
            case "recover" -> handleRecover(sender, args);
            default -> {
                sender.sendMessage(messageManager.get("spec-unknown-subcommand",
                        "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    private void handleRespec(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 2 || (!"class".equalsIgnoreCase(args[1])
                && !"profession".equalsIgnoreCase(args[1]))) {
            sender.sendMessage(messageManager.get("spec-respec-usage",
                    "&cHasználat: /spec respec <class|profession>"));
            return;
        }
        if ("class".equalsIgnoreCase(args[1])) {
            final long revision = specializationManager.profileGateway()
                    .diagnostic(player.getUniqueId()).revision();
            respecService.respecV2(player,
                            "player-respec:" + player.getUniqueId() + ":" + revision)
                    .whenComplete((outcome, failure) -> player.getScheduler().run(plugin, task -> {
                        if (failure != null || outcome == null) {
                            player.sendMessage(messageManager.get(
                                    "spec-respec-persistence-failed",
                                    "&cA Profile v2 tranzakció meghiúsult; a költség nem veszett el."));
                        } else {
                            sendRespecOutcome(player, player, outcome);
                        }
                    }, () -> specializationManager.profileGateway().blockSession(
                            player.getUniqueId(), "Respec completion scheduler rejected")));
            return;
        }
        sendRespecOutcome(sender, player, respecService.respec(player, false));
    }

    private void sendRespecOutcome(final CommandSender sender, final Player player,
                                   final RespecService.Outcome outcome) {
        switch (outcome.status()) {
            case NOTHING_TO_RESPEC -> sender.sendMessage(messageManager.get(
                    "spec-respec-nothing",
                    "&cNincs mit visszaváltani: nincs ilyen specializációd."));
            case INSUFFICIENT_FUNDS -> sender.sendMessage(messageManager.get(
                    "spec-respec-insufficient",
                    "&cA respec ára &f%s %s&c, de csak &f%s&c van a bankodban.",
                    currencyManager.formatBalance(outcome.cost()),
                    outcome.currency().getDisplayName(),
                    currencyManager.formatBalance(
                            currencyManager.getBalance(player, outcome.currency()))));
            case OK -> sender.sendMessage(messageManager.get(
                    "spec-respec-success",
                    "&aSpecializáció visszaváltva &7(ár: &f%s %s&7, visszakapott talentpont: &f%s&7)&a. Újra választhatsz a /spec choose paranccsal.",
                    currencyManager.formatBalance(outcome.cost()),
                    outcome.currency().getDisplayName(), outcome.refundedTalentPoints()));
            case PERSISTENCE_FAILED -> sender.sendMessage(messageManager.get(
                    "spec-respec-persistence-failed",
                    "&cA Profile v2 mentése meghiúsult; az esetleges költséget visszatérítettük."));
            case RUNTIME_FAILED -> sender.sendMessage(messageManager.get(
                    "spec-respec-runtime-failed",
                    "&4A profil commitolt, de a runtime-befejezés hibázott; a session blokkolva, admin audit szükséges."));
            case REFUND_FAILED -> sender.sendMessage(messageManager.get(
                    "spec-respec-refund-failed",
                    "&4A profil- és valuta-visszaállítás kézi admin auditot igényel; a session blokkolva maradt."));
        }
    }

    private void handleList(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final JobType primaryJob = jobManager.getPrimaryJob(player);
        sender.sendMessage(messageManager.get("spec-list-class-header",
                "&6Kaszt specializációk (szint %s-tól):",
                specializationManager.getRequiredClassLevel()));
        if (primaryJob == null) {
            sender.sendMessage(messageManager.get("spec-list-no-class",
                    "&7Nincs elsődleges kasztod."));
        } else {
            for (final SpecializationType specialization : SpecializationType.values()) {
                if (specialization.getParentJob() != primaryJob) continue;
                final String availability =
                        specializationManager.canSelectClassSpecialization(player, specialization)
                                ? messageManager.get("spec-available", "&aVálasztható / aktiválható")
                                : messageManager.get("spec-unavailable", "&cNem elérhető");
                player.sendMessage(Component.text(" - ")
                        .append(specialization.getDisplayName())
                        .append(Component.text(" (" + specialization.getId() + ") "))
                        .append(messageManager.getMessage("spec-availability", "{state}",
                                java.util.Map.of("state", availability))));
            }
        }

        sender.sendMessage(messageManager.get("spec-list-profession-header",
                "&6Szakma specializációk (szint %s-tól):",
                specializationManager.getRequiredProfessionLevel()));
        boolean anyProfessionSpec = false;
        for (final ProfessionSpecializationType specialization
                : ProfessionSpecializationType.values()) {
            if (!professionManager.hasProfession(player, specialization.getParentProfession())) {
                continue;
            }
            anyProfessionSpec = true;
            final String availability =
                    specializationManager.canSelectProfessionSpecialization(player, specialization)
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
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("spec-choose-usage",
                    "&cHasználat: /spec choose <specializáció>"));
            return;
        }

        final SpecializationType classSpec = SpecializationType.fromId(args[1]);
        if (classSpec != null) {
            specializationManager.selectClassSpecializationV2(player, classSpec)
                    .whenComplete((success, failure) -> player.getScheduler().run(plugin, task -> {
                        if (failure == null && Boolean.TRUE.equals(success)) {
                            player.sendMessage(messageManager.getMessage(
                                            "spec-choose-success",
                                            "&aAktív specializáció:")
                                    .append(Component.space()).append(classSpec.getDisplayName()));
                        } else {
                            player.sendMessage(messageManager.get("spec-choose-failed",
                                    "&cA Profile v2 mentés vagy valamelyik kasztkapu miatt a választás/váltás meghiúsult."));
                        }
                    }, () -> specializationManager.profileGateway().blockSession(
                            player.getUniqueId(),
                            "Spec selection completion scheduler rejected")));
            return;
        }

        final ProfessionSpecializationType professionSpec =
                ProfessionSpecializationType.fromId(args[1]);
        if (professionSpec != null) {
            if (specializationManager.selectProfessionSpecialization(player, professionSpec)) {
                player.sendMessage(messageManager.getMessage("spec-choose-success",
                                "&aSpecializáció kiválasztva:")
                        .append(Component.space()).append(professionSpec.getDisplayName()));
            } else {
                player.sendMessage(messageManager.get("spec-choose-failed-profession",
                        "&cNem választhatod ezt a specializációt (szakma vagy szint feltétel hiányzik)."));
            }
            return;
        }
        sender.sendMessage(messageManager.get("spec-unknown",
                "&cIsmeretlen specializáció: &f%s", args[1]));
    }

    private void handleDoctrine(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final List<String> choices = specializationManager.availableDoctrineChoices(player);
        if (choices.isEmpty()) {
            player.sendMessage(messageManager.get("spec-doctrine-unavailable",
                    "&cAz aktív specializációdhoz még nincs játszható doctrine."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(messageManager.get("spec-doctrine-usage",
                    "&eDoctrine választások: &f%s", String.join(", ", choices)));
            return;
        }
        final String choice = args[1].toLowerCase(Locale.ROOT);
        if (!choices.contains(choice)) {
            player.sendMessage(messageManager.get("spec-doctrine-invalid",
                    "&cIsmeretlen doctrine. Választható: &f%s", String.join(", ", choices)));
            return;
        }
        specializationManager.chooseDoctrineV2(player, choice)
                .whenComplete((success, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure == null && Boolean.TRUE.equals(success)) {
                        player.sendMessage(messageManager.get("spec-doctrine-success",
                                "&aDoctrine aktiválva: &f%s", displayId(choice)));
                    } else {
                        player.sendMessage(messageManager.get("spec-doctrine-failed",
                                "&cA doctrine nem aktiválható. Ellenőrizd a mastery-szintedet és a Profile v2 állapotát."));
                    }
                }, () -> specializationManager.profileGateway().blockSession(
                        player.getUniqueId(), "Doctrine completion scheduler rejected")));
    }

    private void handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final var gateway = specializationManager.profileGateway();
        final ProfileDiagnostic diagnostic = gateway.diagnostic(player.getUniqueId());
        final var durable = gateway.currentProfile(player.getUniqueId()).orElse(null);
        player.sendMessage(Component.text("Profile v2: "
                + (diagnostic.loaded() ? diagnostic.profileStatus() : "UNAVAILABLE")
                + " | schema=" + diagnostic.schemaVersion()
                + " | revision=" + diagnostic.revision()));
        player.sendMessage(Component.text("Primary class="
                + diagnostic.primaryClassId().orElse("none")
                + " | level=" + diagnostic.classLevel()
                + " | xp=" + diagnostic.classExperience()
                + " | activeSlot=" + diagnostic.activeSlot().map(Enum::name).orElse("none")
                + " | secondSpecUnlocked=" + diagnostic.secondSpecUnlocked()));
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            final ProfileDiagnostic.SlotDiagnostic state = diagnostic.slots().get(slot);
            if (state == null) {
                player.sendMessage(Component.text(slot.name() + ": unavailable"));
                continue;
            }
            final var loadout = durable == null ? null : durable.loadout(slot);
            final String doctrine = loadout == null
                    ? "none" : loadout.doctrineChoices().getOrDefault("core", "none");
            final String capstone = loadout == null
                    ? "unknown" : loadout.capstoneStatus().name();
            player.sendMessage(Component.text(slot.name() + ": spec="
                    + state.specializationId().orElse("none") + " | status=" + state.status()
                    + " | seal=" + state.sealReason().map(Object::toString).orElse("none")
                    + " | mastery=" + state.masteryRank() + "/10 xp=" + state.masteryXp()
                    + " | doctrine=" + doctrine + " | capstone=" + capstone));
        }
        diagnostic.reviewReason().ifPresent(reason ->
                player.sendMessage(Component.text("Review: " + reason)));
        diagnostic.quarantineReason().ifPresent(reason ->
                player.sendMessage(Component.text("Quarantine: " + reason)));
        diagnostic.sessionBlockReason().ifPresent(reason ->
                player.sendMessage(Component.text("Session block: " + reason)));
    }

    private void handleReset(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied",
                    "&cNincs jogosultságod erre a parancsra."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("spec-reset-usage",
                    "&cHasználat: /spec reset <játékos>"));
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("target-player-offline",
                    "&cA célpont játékos nem elérhető."));
            return;
        }
        target.getScheduler().run(plugin, task -> {
            final long revision = specializationManager.profileGateway()
                    .diagnostic(target.getUniqueId()).revision();
            specializationManager.resetClassSpecSection(target, false,
                            "admin-spec-reset:" + target.getUniqueId() + ":" + revision)
                    .whenComplete((result, failure) -> target.getScheduler().run(plugin, followup -> {
                        if (failure == null && result != null && result.committed()) {
                            specializationManager.resetProfessionSpecialization(target);
                            sendToSender(sender, messageManager.getComponent(
                                    "spec-reset-success",
                                    "&aSpecializációk törölve: &f%s", target.getName()));
                        } else if (failure == null && result != null
                                && result.durableMutationApplied()) {
                            specializationManager.profileGateway().blockSession(
                                    target.getUniqueId(),
                                    "Admin spec-reset committed, but runtime reconciliation failed");
                            sendToSender(sender, messageManager.getComponent(
                                    "spec-reset-runtime-failed",
                                    "&cA profil commitolt, de a runtime-befejezés hibázott; a session blokkolva: &f%s",
                                    target.getName()));
                        } else {
                            sendToSender(sender, messageManager.getComponent(
                                    "spec-reset-failed",
                                    "&cA Profile v2 mentése meghiúsult; semmi nem lett törölve: &f%s",
                                    target.getName()));
                        }
                    }, () -> specializationManager.profileGateway().blockSession(
                            target.getUniqueId(),
                            "Admin spec-reset completion scheduler rejected")));
        }, () -> sendToSender(sender, messageManager.getComponent("spec-reset-failed",
                "&cA célpont scheduler elutasította a resetet: &f%s", target.getName())));
    }

    private void handleRecover(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(RECOVERY_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied",
                    "&cNincs jogosultságod erre a parancsra."));
            return;
        }
        if (args.length != 3 || !"confirm".equalsIgnoreCase(args[2])) {
            sender.sendMessage(Component.text(
                    "Használat: /spec recover <player|uuid> confirm"));
            return;
        }
        final java.util.UUID targetId = resolveKnownPlayerId(args[1]);
        if (targetId == null) {
            sender.sendMessage(Component.text(
                    "Ismeretlen játékos vagy hibás UUID: " + args[1]));
            return;
        }
        final var gateway = specializationManager.profileGateway();
        final String evidenceId = gateway.quarantineEvidenceId(targetId).orElse(null);
        if (evidenceId == null) {
            sender.sendMessage(Component.text(
                    "Nincs aktív quarantine evidence ehhez a profilhoz."));
            return;
        }
        final String auditId = "spec-recovery:" + sender.getName() + ":"
                + targetId + ":" + evidenceId;
        gateway.recoverQuarantined(targetId, evidenceId, auditId)
                .whenComplete((result, failure) -> {
                    if (failure != null || result == null) {
                        sendToSender(sender, Component.text("Profile recovery failed: "
                                + (failure == null ? "missing result" : failure.getMessage())));
                    } else {
                        sendToSender(sender, Component.text(
                                "Profile v2 recovered as clean inactive revision "
                                        + result.profile().revision()
                                        + "; evidence preserved: " + result.evidenceId()
                                        + ". A játékosnak újra kell csatlakoznia."));
                    }
                });
    }

    private java.util.UUID resolveKnownPlayerId(final String token) {
        try {
            return java.util.UUID.fromString(token);
        } catch (final IllegalArgumentException ignored) {
            // Fall through to known player lookup.
        }
        final Player online = Bukkit.getPlayerExact(token);
        if (online != null) return online.getUniqueId();
        for (final org.bukkit.OfflinePlayer known : Bukkit.getOfflinePlayers()) {
            final String name = known.getName();
            if (name != null && name.equalsIgnoreCase(token)) return known.getUniqueId();
        }
        return null;
    }

    private void sendToSender(final CommandSender sender, final Component message) {
        if (sender instanceof Player player) {
            player.getScheduler().run(plugin, task -> player.sendMessage(message), null);
        } else {
            sender.sendMessage(message);
        }
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("spec-help-header",
                "&6/spec &7- Elérhető parancsok:"));
        sender.sendMessage(messageManager.get("spec-help-list",
                "&e/spec list &7- Választható specializációk."));
        sender.sendMessage(messageManager.get("spec-help-choose",
                "&e/spec choose <specializáció> &7- Új spec választása vagy a két loadout közti váltás."));
        sender.sendMessage(messageManager.get("spec-help-doctrine",
                "&e/spec doctrine <választás> &7- Az aktív spec doctrine-jának beállítása."));
        sender.sendMessage(messageManager.get("spec-help-info",
                "&e/spec info &7- Loadout, mastery, doctrine és capstone állapot."));
        sender.sendMessage(messageManager.get("spec-help-respec",
                "&e/spec respec <class|profession> &7- Specializáció visszaváltása (frakcióvalutáért)."));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("spec-help-reset",
                    "&e/spec reset <játékos> &7- Specializációk törlése (Admin)."));
        }
        if (sender.hasPermission(RECOVERY_PERMISSION)) {
            sender.sendMessage(Component.text(
                    "/spec recover <player|uuid> confirm - explicit quarantine recovery (Admin)."));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(
            final @NonNull CommandSourceStack commandSourceStack,
            final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final List<String> subcommands = sender.hasPermission(RECOVERY_PERMISSION)
                ? List.of("list", "choose", "doctrine", "info", "respec", "reset", "recover")
                : sender.hasPermission(ADMIN_PERMISSION)
                ? List.of("list", "choose", "doctrine", "info", "respec", "reset")
                : List.of("list", "choose", "doctrine", "info", "respec");

        final String subcommand = prefixAt(args, 0);
        final boolean subcommandComplete = subcommands.contains(subcommand);
        if (args.length == 0 || (args.length == 1 && !subcommandComplete)) {
            return subcommands.stream()
                    .filter(option -> option.startsWith(subcommand)).toList();
        }

        if ("choose".equals(subcommand) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            final List<String> options = new ArrayList<>();
            for (final SpecializationType specialization : SpecializationType.values()) {
                if (specialization.getId().startsWith(prefix)) {
                    options.add(specialization.getId());
                }
            }
            for (final ProfessionSpecializationType specialization
                    : ProfessionSpecializationType.values()) {
                if (specialization.getId().startsWith(prefix)) {
                    options.add(specialization.getId());
                }
            }
            return options;
        }

        if ("doctrine".equals(subcommand) && args.length <= 2
                && sender instanceof Player player) {
            final String prefix = prefixAt(args, 1);
            return specializationManager.availableDoctrineChoices(player).stream()
                    .filter(option -> option.startsWith(prefix)).toList();
        }

        if ("respec".equals(subcommand) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return List.of("class", "profession").stream()
                    .filter(option -> option.startsWith(prefix)).toList();
        }

        if (("reset".equals(subcommand) || "recover".equals(subcommand))
                && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private static String displayId(final String id) {
        final String normalized = id == null ? "" : id.replace('_', ' ').trim();
        if (normalized.isEmpty()) return "-";
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
