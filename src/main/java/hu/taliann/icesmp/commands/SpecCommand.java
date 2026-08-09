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

    private void handleSwitch(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (!isGameplayV2Class(player)) {
            player.sendMessage(messageManager.get("spec-switch-class-gated",
                    "&cA két-slot gameplay váltás egyelőre csak a kész reworkölt classoknál aktív (%s).",
                    GameplayV2ClassPolicy.enabledList()));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(messageManager.get("spec-switch-usage",
                    "&cHasználat: /spec switch <first|second|spec-id>"));
            return;
        }
        final LoadoutSlot target = resolveTargetSlot(player, args[1]);
        if (target == null) {
            player.sendMessage(messageManager.get("spec-switch-unknown",
                    "&cNincs ilyen megtanult specializáció vagy slot: &f%s", args[1]));
            return;
        }
        specializationManager.switchClassSpecializationV2(player, target)
                .whenComplete((success, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure == null && Boolean.TRUE.equals(success)) {
                        final String active = specializationManager.getClassSpecialization(player) == null
                                ? target.name().toLowerCase(Locale.ROOT)
                                : specializationManager.getClassSpecialization(player).getId();
                        player.sendMessage(messageManager.get("spec-switch-success",
                                "&aAktív specialization: &f%s&a. A Düh/cooldown közös következményei megmaradtak.",
                                active));
                    } else {
                        player.sendMessage(messageManager.get("spec-switch-failed",
                                "&cNem válthatsz most: a cél-slot nem használható, harcban vagy, "
                                        + "vagy ellenség van a biztonsági körzetben."));
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
            player.sendMessage(messageManager.get("spec-oath-usage",
                    "&cHasználat (csak Paplovag): /spec esku <irgalom|itelet|oltalmazas>"));
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
            player.sendMessage(messageManager.get("spec-doctrine-class-gated",
                    "&cA doctrine-rendszer egyelőre csak a kész reworkölt classoknál aktív (%s).",
                    GameplayV2ClassPolicy.enabledList()));
            return;
        }
        if (args.length < 3) {
            final SpecializationType spec = specializationManager.getClassSpecialization(player);
            player.sendMessage(messageManager.get("spec-doctrine-usage",
                    "&e/spec doctrine <30|40|50> <választás>"));
            if (spec != null) {
                for (final int level : List.of(30, 40, 50)) {
                    player.sendMessage(messageManager.get("spec-doctrine-options",
                            "&7Szint %s: &f%s", level,
                            String.join(" | ", specializationManager.doctrineChoices(spec, level))));
                }
            }
            return;
        }
        final int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (final NumberFormatException invalid) {
            player.sendMessage(messageManager.get("spec-doctrine-level",
                    "&cDoctrine-szint csak 30, 40 vagy 50 lehet."));
            return;
        }
        specializationManager.chooseDoctrineV2(player, level, args[2])
                .whenComplete((success, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure == null && Boolean.TRUE.equals(success)) {
                        player.sendMessage(messageManager.get("spec-doctrine-success",
                                "&aDoctrine rögzítve: &f%s &7(szint %s)", args[2], level));
                    } else {
                        player.sendMessage(messageManager.get("spec-doctrine-failed",
                                "&cA doctrine nem választható: rossz spec/szint, ismeretlen választás, "
                                        + "vagy a tier már véglegesen rögzített."));
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
                            player.sendMessage(messageManager.get("spec-respec-persistence-failed",
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
                    "spec-respec-nothing", "&cNincs mit visszaváltani: nincs ilyen specializációd."));
            case INSUFFICIENT_FUNDS -> sender.sendMessage(messageManager.get(
                    "spec-respec-insufficient",
                    "&cA respec ára &f%s %s&c, de csak &f%s&c van a bankodban.",
                    currencyManager.formatBalance(outcome.cost()),
                    outcome.currency().getDisplayName(),
                    currencyManager.formatBalance(currencyManager.getBalance(player, outcome.currency()))));
            case OK -> sender.sendMessage(messageManager.get(
                    "spec-respec-success",
                    "&aSpecializáció visszaváltva &7(ár: &f%s %s&7, visszakapott talentpont: &f%s&7)&a.",
                    currencyManager.formatBalance(outcome.cost()),
                    outcome.currency().getDisplayName(),
                    outcome.refundedTalentPoints()));
            case PERSISTENCE_FAILED -> sender.sendMessage(messageManager.get(
                    "spec-respec-persistence-failed",
                    "&cA Profile v2 mentése meghiúsult; az esetleges költséget visszatérítettük."));
            case RUNTIME_FAILED -> sender.sendMessage(messageManager.get(
                    "spec-respec-runtime-failed",
                    "&4A profil commitolt, de a runtime-befejezés hibázott; a session blokkolva."));
            case REFUND_FAILED -> sender.sendMessage(messageManager.get(
                    "spec-respec-refund-failed",
                    "&4A profil- és valuta-visszaállítás kézi admin auditot igényel."));
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
                final var profile = specializationManager.profileGateway()
                        .currentProfile(player.getUniqueId()).orElse(null);
                final boolean learned = profile != null && profile.loadouts().stream()
                        .anyMatch(loadout -> specialization.getId()
                                .equals(loadout.specializationId()));
                final String availability = learned
                        ? messageManager.get("spec-learned", "&bMegtanult")
                        : specializationManager.canSelectClassSpecialization(player, specialization)
                        ? messageManager.get("spec-available", "&aVálasztható")
                        : messageManager.get("spec-unavailable", "&cNem elérhető");
                player.sendMessage(Component.text(" - ")
                        .append(specialization.getDisplayName())
                        .append(Component.text(" (" + specialization.getId() + ") "))
                        .append(messageManager.getMessage("spec-availability", "{state}",
                                Map.of("state", availability))));
            }
            player.sendMessage(messageManager.get("spec-second-slot-info",
                    "&7Második specialization slot: &f%s. szinttől&7.",
                    specializationManager.getSecondSpecUnlockLevel()));
        }

        sender.sendMessage(messageManager.get("spec-list-profession-header",
                "&6Szakma specializációk (szint %s-tól):",
                specializationManager.getRequiredProfessionLevel()));
        boolean anyProfessionSpec = false;
        for (final ProfessionSpecializationType specialization : ProfessionSpecializationType.values()) {
            if (!professionManager.hasProfession(player, specialization.getParentProfession())) continue;
            anyProfessionSpec = true;
            final String availability = specializationManager.canSelectProfessionSpecialization(player, specialization)
                    ? messageManager.get("spec-available", "&aVálasztható")
                    : messageManager.get("spec-unavailable", "&cNem elérhető");
            player.sendMessage(Component.text(" - ")
                    .append(specialization.getDisplayName())
                    .append(Component.text(" (" + specialization.getId() + ") "))
                    .append(messageManager.getMessage("spec-availability", "{state}",
                            Map.of("state", availability))));
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
                                            "spec-choose-success", "&aSpecializáció megtanulva:")
                                    .append(Component.space()).append(classSpec.getDisplayName()));
                        } else {
                            player.sendMessage(messageManager.get("spec-choose-failed",
                                    "&cA Profile v2 mentés vagy valamelyik kasztkapu miatt a választás meghiúsult."));
                        }
                    }, () -> specializationManager.profileGateway().blockSession(
                            player.getUniqueId(), "Spec selection completion scheduler rejected")));
            return;
        }
        final ProfessionSpecializationType professionSpec = ProfessionSpecializationType.fromId(args[1]);
        if (professionSpec != null) {
            if (specializationManager.selectProfessionSpecialization(player, professionSpec)) {
                player.sendMessage(messageManager.getMessage("spec-choose-success",
                                "&aSpecializáció kiválasztva:")
                        .append(Component.space()).append(professionSpec.getDisplayName()));
            } else {
                player.sendMessage(messageManager.get("spec-choose-failed-profession",
                        "&cNem választhatod ezt a specializációt."));
            }
            return;
        }
        sender.sendMessage(messageManager.get("spec-unknown",
                "&cIsmeretlen specializáció: &f%s", args[1]));
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
                            sendToSender(sender, messageManager.getComponent("spec-reset-success",
                                    "&aSpecializációk törölve: &f%s", target.getName()));
                        } else if (failure == null && result != null && result.durableMutationApplied()) {
                            specializationManager.profileGateway().blockSession(target.getUniqueId(),
                                    "Admin spec-reset committed, but runtime reconciliation failed");
                            sendToSender(sender, messageManager.getComponent("spec-reset-runtime-failed",
                                    "&cA profil commitolt, de a runtime-befejezés hibázott: &f%s",
                                    target.getName()));
                        } else {
                            sendToSender(sender, messageManager.getComponent("spec-reset-failed",
                                    "&cA Profile v2 mentése meghiúsult; semmi nem lett törölve: &f%s",
                                    target.getName()));
                        }
                    }, () -> specializationManager.profileGateway().blockSession(target.getUniqueId(),
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
        sender.sendMessage(messageManager.get("spec-help-header",
                "&6/spec &7- Elérhető parancsok:"));
        sender.sendMessage(messageManager.get("spec-help-list",
                "&e/spec list &7- Választható/megtanult specializációk."));
        sender.sendMessage(messageManager.get("spec-help-choose",
                "&e/spec choose <specializáció> &7- Specializáció megtanulása."));
        sender.sendMessage(messageManager.get("spec-help-switch",
                "&e/spec switch <slot|spec> &7- Harcos aktív specialization váltása biztonságos helyen."));
        sender.sendMessage(messageManager.get("spec-help-doctrine",
                "&e/spec doctrine <30|40|50> <választás> &7- Harcos doctrine rögzítése."));
        sender.sendMessage(messageManager.get("spec-help-info",
                "&e/spec info &7- Profile v2/loadout/mastery/doctrine állapot."));
        sender.sendMessage(messageManager.get("spec-help-respec",
                "&e/spec respec <class|profession> &7- Specializáció visszaváltása."));
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
        final List<String> base = List.of("list", "choose", "switch", "doctrine", "esku", "info", "respec");
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
