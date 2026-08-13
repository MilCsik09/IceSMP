package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;

import hu.taliann.icesmp.classspec.application.GameplayV2ClassPolicy;
import hu.taliann.icesmp.classspec.application.ProfileDiagnostic;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
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
import java.util.Map;

public final class SpecCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "icesmp.admin.spec";
    private static final String RECOVERY_PERMISSION = hu.taliann.icesmp.core.Permissions.SPEC_RECOVER;

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
            case "switch" -> handleSwitch(sender, args);
            case "doctrine" -> handleDoctrine(sender, args);
            case "esku" -> handleOath(sender, args);
            case "ima" -> handleLitany(sender, args);
            case "info" -> handleInfo(sender);
            case "respec" -> handleRespec(sender, args);
            case "reset" -> handleReset(sender, args);
            case "recover" -> handleRecover(sender, args);
            default -> {
                sender.sendMessage(messageManager.required("spec-unknown-subcommand", args[0]));
                sendHelp(sender);
            }
        }
    }

    private void handleSwitch(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (!isGameplayV2Class(player)) {
            player.sendMessage(messageManager.required("spec-switch-class-gated",
                    GameplayV2ClassPolicy.enabledList()));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(messageManager.required("spec-switch-usage"));
            return;
        }
        final LoadoutSlot target = resolveTargetSlot(player, args[1]);
        if (target == null) {
            player.sendMessage(messageManager.required("spec-switch-unknown", args[1]));
            return;
        }
        specializationManager.switchClassSpecializationV2(player, target)
                .whenComplete((success, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure == null && Boolean.TRUE.equals(success)) {
                        final String active = specializationManager.getClassSpecialization(player) == null
                                ? target.name().toLowerCase(Locale.ROOT)
                                : specializationManager.getClassSpecialization(player).getId();
                        player.sendMessage(messageManager.required("spec-switch-success",
                                active));
                    } else {
                        player.sendMessage(messageManager.required("spec-switch-failed"));
                    }
                }, () -> specializationManager.profileGateway().blockSession(
                        player.getUniqueId(), "Spec switch completion scheduler rejected")));
    }

    private void handleOath(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 2 || !specializationManager.choosePaladinOath(player, args[1])) {
            player.sendMessage(messageManager.required("spec-oath-usage"));
        }
    }

    private void handleLitany(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 2 || !specializationManager.choosePriestLitany(player, args[1])) {
            player.sendMessage(messageManager.required("spec-litany-usage"));
        }
    }

    private boolean isGameplayV2Class(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        return job != null && GameplayV2ClassPolicy.isEnabled(job.getId());
    }

    private LoadoutSlot resolveTargetSlot(final Player player, final String raw) {
        final String token = raw.toLowerCase(Locale.ROOT);
        if (token.equals("first") || token.equals("1")) return LoadoutSlot.FIRST;
        if (token.equals("second") || token.equals("2")) return LoadoutSlot.SECOND;
        final var profile = specializationManager.profileGateway()
                .currentProfile(player.getUniqueId()).orElse(null);
        if (profile == null) return null;
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            if (profile.loadout(slot).specializationId().equals(token)) return slot;
        }
        return null;
    }

    private void handleDoctrine(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (!isGameplayV2Class(player)) {
            player.sendMessage(messageManager.required("spec-doctrine-class-gated",
                    GameplayV2ClassPolicy.enabledList()));
            return;
        }
        if (args.length < 3) {
            final SpecializationType spec = specializationManager.getClassSpecialization(player);
            player.sendMessage(messageManager.required("spec-doctrine-usage"));
            if (spec != null) {
                for (final int level : List.of(30, 40, 50)) {
                    player.sendMessage(messageManager.required("spec-doctrine-options", level,
                            String.join(" | ", specializationManager.doctrineChoices(spec, level))));
                }
            }
            return;
        }
        final int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (final NumberFormatException invalid) {
            player.sendMessage(messageManager.required("spec-doctrine-level"));
            return;
        }
        specializationManager.chooseDoctrineV2(player, level, args[2])
                .whenComplete((success, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure == null && Boolean.TRUE.equals(success)) {
                        player.sendMessage(messageManager.required("spec-doctrine-success", args[2], level));
                    } else {
                        player.sendMessage(messageManager.required("spec-doctrine-failed"));
                    }
                }, () -> specializationManager.profileGateway().blockSession(
                        player.getUniqueId(), "Doctrine completion scheduler rejected")));
    }

    private void handleRespec(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 2 || (!"class".equalsIgnoreCase(args[1])
                && !"profession".equalsIgnoreCase(args[1]))) {
            sender.sendMessage(messageManager.required("spec-respec-usage"));
            return;
        }
        if ("class".equalsIgnoreCase(args[1])) {
            final long revision = specializationManager.profileGateway()
                    .diagnostic(player.getUniqueId()).revision();
            respecService.respecV2(player,
                            "player-respec:" + player.getUniqueId() + ":" + revision)
                    .whenComplete((outcome, failure) -> player.getScheduler().run(plugin, task -> {
                        if (failure != null || outcome == null) {
                            player.sendMessage(messageManager.required("spec-respec-persistence-failed"));
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
            case NOTHING_TO_RESPEC -> sender.sendMessage(messageManager.required(
                    "spec-respec-nothing"));
            case INSUFFICIENT_FUNDS -> sender.sendMessage(messageManager.required(
                    "spec-respec-insufficient",
                    currencyManager.formatBalance(outcome.cost()),
                    outcome.currency().getDisplayName(),
                    currencyManager.formatBalance(currencyManager.getBalance(player, outcome.currency()))));
            case OK -> sender.sendMessage(messageManager.required(
                    "spec-respec-success",
                    currencyManager.formatBalance(outcome.cost()),
                    outcome.currency().getDisplayName(),
                    outcome.refundedTalentPoints()));
            case PERSISTENCE_FAILED -> sender.sendMessage(messageManager.required(
                    "spec-respec-persistence-failed"));
            case RUNTIME_FAILED -> sender.sendMessage(messageManager.required(
                    "spec-respec-runtime-failed"));
            case REFUND_FAILED -> sender.sendMessage(messageManager.required(
                    "spec-respec-refund-failed"));
        }
    }

    private void handleList(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final JobType primaryJob = jobManager.getPrimaryJob(player);
        sender.sendMessage(messageManager.required("spec-list-class-header",
                specializationManager.getRequiredClassLevel()));
        if (primaryJob == null) {
            sender.sendMessage(messageManager.required("spec-list-no-class"));
        } else {
            for (final SpecializationType specialization : SpecializationType.values()) {
                if (specialization.getParentJob() != primaryJob) continue;
                final var profile = specializationManager.profileGateway()
                        .currentProfile(player.getUniqueId()).orElse(null);
                final boolean learned = profile != null && profile.loadouts().stream()
                        .anyMatch(loadout -> specialization.getId()
                                .equals(loadout.specializationId()));
                final String availability = learned
                        ? messageManager.required("spec-learned")
                        : specializationManager.canSelectClassSpecialization(player, specialization)
                        ? messageManager.required("spec-available")
                        : messageManager.required("spec-unavailable");
                player.sendMessage(Component.text(" - ")
                        .append(specialization.getDisplayName())
                        .append(Component.text(" (" + specialization.getId() + ") "))
                        .append(messageManager.requiredMessage("spec-availability",
                                Map.of("state", availability))));
            }
            player.sendMessage(messageManager.required("spec-second-slot-info",
                    specializationManager.getSecondSpecUnlockLevel()));
        }

        sender.sendMessage(messageManager.required("spec-list-profession-header",
                specializationManager.getRequiredProfessionLevel()));
        boolean anyProfessionSpec = false;
        for (final ProfessionSpecializationType specialization : ProfessionSpecializationType.values()) {
            if (!professionManager.hasProfession(player, specialization.getParentProfession())) continue;
            anyProfessionSpec = true;
            final String availability = specializationManager.canSelectProfessionSpecialization(player, specialization)
                    ? messageManager.required("spec-available")
                    : messageManager.required("spec-unavailable");
            player.sendMessage(Component.text(" - ")
                    .append(specialization.getDisplayName())
                    .append(Component.text(" (" + specialization.getId() + ") "))
                    .append(messageManager.requiredMessage("spec-availability",
                            Map.of("state", availability))));
        }
        if (!anyProfessionSpec) {
            sender.sendMessage(messageManager.required("spec-list-no-profession"));
        }
    }

    private void handleChoose(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messageManager.required("spec-choose-usage"));
            return;
        }
        final SpecializationType classSpec = SpecializationType.fromId(args[1]);
        if (classSpec != null) {
            specializationManager.selectClassSpecializationV2(player, classSpec)
                    .whenComplete((success, failure) -> player.getScheduler().run(plugin, task -> {
                        if (failure == null && Boolean.TRUE.equals(success)) {
                            player.sendMessage(messageManager.requiredMessage(
                                            "spec-choose-success")
                                    .append(Component.space()).append(classSpec.getDisplayName()));
                        } else {
                            player.sendMessage(messageManager.required("spec-choose-failed"));
                        }
                    }, () -> specializationManager.profileGateway().blockSession(
                            player.getUniqueId(), "Spec selection completion scheduler rejected")));
            return;
        }
        final ProfessionSpecializationType professionSpec = ProfessionSpecializationType.fromId(args[1]);
        if (professionSpec != null) {
            if (specializationManager.selectProfessionSpecialization(player, professionSpec)) {
                player.sendMessage(messageManager.requiredMessage("spec-choose-success")
                        .append(Component.space()).append(professionSpec.getDisplayName()));
            } else {
                player.sendMessage(messageManager.required("spec-choose-failed-profession"));
            }
            return;
        }
        sender.sendMessage(messageManager.required("spec-unknown", args[1]));
    }

    private void handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final ProfileDiagnostic diagnostic = specializationManager.profileGateway()
                .diagnostic(player.getUniqueId());
        player.sendMessage(Component.text("Profile v2: "
                + (diagnostic.loaded() ? diagnostic.profileStatus() : "UNAVAILABLE")
                + " | schema=" + diagnostic.schemaVersion() + " | revision=" + diagnostic.revision()));
        player.sendMessage(Component.text("Primary class="
                + diagnostic.primaryClassId().orElse("none") + " | level=" + diagnostic.classLevel()
                + " | xp=" + diagnostic.classExperience() + " | activeSlot="
                + diagnostic.activeSlot().map(Enum::name).orElse("none")
                + " | secondSpecUnlocked=" + diagnostic.secondSpecUnlocked()));
        final var profile = specializationManager.profileGateway()
                .currentProfile(player.getUniqueId()).orElse(null);
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            final ProfileDiagnostic.SlotDiagnostic state = diagnostic.slots().get(slot);
            if (state == null) {
                player.sendMessage(Component.text(slot.name() + ": unavailable"));
                continue;
            }
            String extra = "";
            if (profile != null) {
                final ClassLoadout loadout = profile.loadout(slot);
                extra = " | doctrine=" + loadout.doctrineChoices()
                        + " | capstone=" + loadout.capstoneStatus();
            }
            player.sendMessage(Component.text(slot.name() + ": spec="
                    + state.specializationId().orElse("none") + " | status=" + state.status()
                    + " | seal=" + state.sealReason().map(Object::toString).orElse("none")
                    + " | mastery=" + state.masteryRank() + "/10 xp=" + state.masteryXp()
                    + extra));
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
            sender.sendMessage(messageManager.required("spec-reset-usage"));
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
                            sendToSender(sender, messageManager.requiredComponent("spec-reset-success", target.getName()));
                        } else if (failure == null && result != null && result.durableMutationApplied()) {
                            specializationManager.profileGateway().blockSession(target.getUniqueId(),
                                    "Admin spec-reset committed, but runtime reconciliation failed");
                            sendToSender(sender, messageManager.requiredComponent("spec-reset-runtime-failed",
                                    target.getName()));
                        } else {
                            sendToSender(sender, messageManager.requiredComponent("spec-reset-failed",
                                    target.getName()));
                        }
                    }, () -> specializationManager.profileGateway().blockSession(target.getUniqueId(),
                            "Admin spec-reset completion scheduler rejected")));
        }, () -> sendToSender(sender, messageManager.requiredComponent("spec-reset-scheduler-failed", target.getName())));
    }

    private void handleRecover(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(RECOVERY_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied",
                    "&cNincs jogosultságod erre a parancsra."));
            return;
        }
        if (args.length != 3 || !"confirm".equalsIgnoreCase(args[2])) {
            sender.sendMessage(Component.text("Használat: /spec recover <player|uuid> confirm"));
            return;
        }
        final java.util.UUID targetId = resolveKnownPlayerId(args[1]);
        if (targetId == null) {
            sender.sendMessage(Component.text("Ismeretlen játékos vagy hibás UUID: " + args[1]));
            return;
        }
        final var gateway = specializationManager.profileGateway();
        final String evidenceId = gateway.quarantineEvidenceId(targetId).orElse(null);
        if (evidenceId == null) {
            sender.sendMessage(Component.text("Nincs aktív quarantine evidence ehhez a profilhoz."));
            return;
        }
        final String auditId = "spec-recovery:" + sender.getName() + ":" + targetId + ":" + evidenceId;
        gateway.recoverQuarantined(targetId, evidenceId, auditId).whenComplete((result, failure) -> {
            if (failure != null || result == null) {
                sendToSender(sender, Component.text("Profile recovery failed: "
                        + (failure == null ? "missing result" : failure.getMessage())));
            } else {
                sendToSender(sender, Component.text("Profile v2 recovered as clean inactive revision "
                        + result.profile().revision() + "; evidence preserved: " + result.evidenceId()
                        + ". A játékosnak újra kell csatlakoznia."));
            }
        });
    }

    private java.util.UUID resolveKnownPlayerId(final String token) {
        try {
            return java.util.UUID.fromString(token);
        } catch (final IllegalArgumentException ignored) {
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
        sender.sendMessage(messageManager.required("spec-help-header"));
        sender.sendMessage(messageManager.required("spec-help-list"));
        sender.sendMessage(messageManager.required("spec-help-choose"));
        sender.sendMessage(messageManager.required("spec-help-switch"));
        sender.sendMessage(messageManager.required("spec-help-doctrine"));
        sender.sendMessage(messageManager.required("spec-help-info"));
        sender.sendMessage(messageManager.required("spec-help-respec"));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.required("spec-help-reset"));
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
        final List<String> base = List.of("list", "choose", "switch", "doctrine", "esku", "ima", "info", "respec");
        final List<String> subcommands = new ArrayList<>(base);
        if (sender.hasPermission(ADMIN_PERMISSION)) subcommands.add("reset");
        if (sender.hasPermission(RECOVERY_PERMISSION)) subcommands.add("recover");
        final String subcommand = prefixAt(args, 0);
        final boolean subcommandComplete = subcommands.contains(subcommand);
        if (args.length == 0 || (args.length == 1 && !subcommandComplete)) {
            return subcommands.stream().filter(option -> option.startsWith(subcommand)).toList();
        }
        if ("choose".equals(subcommand) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            final List<String> options = new ArrayList<>();
            for (final SpecializationType specialization : SpecializationType.values()) {
                if (specialization.getId().startsWith(prefix)) options.add(specialization.getId());
            }
            for (final ProfessionSpecializationType specialization : ProfessionSpecializationType.values()) {
                if (specialization.getId().startsWith(prefix)) options.add(specialization.getId());
            }
            return options;
        }
        if ("switch".equals(subcommand) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            final List<String> options = new ArrayList<>(List.of("first", "second"));
            if (sender instanceof Player player) {
                final var profile = specializationManager.profileGateway()
                        .currentProfile(player.getUniqueId()).orElse(null);
                if (profile != null) {
                    for (final LoadoutSlot slot : LoadoutSlot.values()) {
                        final String spec = profile.loadout(slot).specializationId();
                        if (!spec.isBlank() && !options.contains(spec)) options.add(spec);
                    }
                }
            }
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }
        if ("doctrine".equals(subcommand)) {
            if (args.length <= 2) {
                final String prefix = prefixAt(args, 1);
                return List.of("30", "40", "50").stream()
                        .filter(option -> option.startsWith(prefix)).toList();
            }
            if (args.length <= 3 && sender instanceof Player player) {
                final int level;
                try {
                    level = Integer.parseInt(args[1]);
                } catch (final NumberFormatException invalid) {
                    return List.of();
                }
                final String prefix = prefixAt(args, 2);
                return specializationManager.doctrineChoices(
                                specializationManager.getClassSpecialization(player), level).stream()
                        .filter(option -> option.startsWith(prefix)).sorted().toList();
            }
        }
        if ("esku".equals(subcommand) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return List.of("irgalom", "itelet", "oltalmazas").stream()
                    .filter(option -> option.startsWith(prefix)).toList();
        }
        if ("ima".equals(subcommand) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return List.of("vigasz", "ostor", "csend").stream()
                    .filter(option -> option.startsWith(prefix)).toList();
        }
        if ("respec".equals(subcommand) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return List.of("class", "profession").stream()
                    .filter(option -> option.startsWith(prefix)).toList();
        }
        if (("reset".equals(subcommand) || "recover".equals(subcommand)) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
