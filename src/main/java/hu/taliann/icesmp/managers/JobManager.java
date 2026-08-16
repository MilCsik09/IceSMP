package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.ProfileDiagnostic;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.domain.ProfileOperation;
import hu.taliann.icesmp.classspec.domain.ProfileOperationType;
import hu.taliann.icesmp.classspec.domain.SpellGrantLedger;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import hu.taliann.icesmp.prologue.PrologueContentPolicy;
import hu.taliann.icesmp.prologue.PrologueProgression;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/** Profile v2-backed class progression and explicit spell provenance. */
public final class JobManager implements PlayerStateCleanup {
    public static final int MAX_JOB_LEVEL = 50;
    public static final String SOURCE_BASE = "BASE";
    public static final String SOURCE_BASE_PREFIX = "BASE:";
    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String SOURCE_SPEC_PREFIX = "SPEC:";
    public static final String SOURCE_TALENT_PREFIX = "TALENT:";
    public static final String SOURCE_QUEST_PREFIX = "QUEST:";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private final hu.taliann.icesmp.playerprofile.application.PlayerProfileSpellGrantStore spellGrantStore =
            new hu.taliann.icesmp.playerprofile.application.PlayerProfileSpellGrantStore();
    private volatile FactionManager factionManagerRef;
    private volatile ClassSpecProfileGateway profileGateway;
    private java.util.function.Function<Player, CompletionStage<Void>> xpChangeHook;

    public JobManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final MessageManager messageManager, final FactionManager factionManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.messageManager = Objects.requireNonNull(messageManager, "messageManager");
        this.factionManager = Objects.requireNonNull(factionManager, "factionManager");
    }

    public void setProfileGateway(final ClassSpecProfileGateway gateway) {
        this.profileGateway = Objects.requireNonNull(gateway, "gateway");
    }

    public void setFactionManager(final FactionManager manager) { this.factionManagerRef = manager; }

    private ClassSpecProfileGateway gateway() {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null) throw new IllegalStateException("Profile v2 gateway is not initialized");
        return gateway;
    }

    public boolean hasPrimaryJob(final Player player) { return getPrimaryJob(player) != null; }

    public JobType getPrimaryJob(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return null;
        return JobType.fromId(gateway.currentProfile(player.getUniqueId())
                .map(profile -> profile.primaryClassId()).orElse(null));
    }

    public int getXp(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return 0;
        return gateway.currentProfile(player.getUniqueId())
                .map(profile -> profile.classExperience()).orElse(0);
    }

    public int getPrimaryLevel(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return 0;
        return gateway.currentProfile(player.getUniqueId())
                .map(profile -> profile.classLevel()).orElse(0);
    }

    public boolean canSelectPrimary(final Player player, final JobType job) {
        if (job == null || !meetsFactionRequirement(player, job)) return false;
        final ClassSpecProfileGateway gateway = profileGateway;
        return gateway != null && gateway.isSessionReady(player.getUniqueId())
                && gateway.currentProfile(player.getUniqueId())
                .map(profile -> profile.primaryClassId().isEmpty()).orElse(false);
    }

    public boolean meetsFactionRequirement(final Player player, final JobType job) {
        final FactionType required = job == null ? null : job.getRequiredFaction();
        return required == null || factionManager.isMember(player.getUniqueId(), required);
    }

    public CompletionStage<Boolean> setPrimaryJobV2(final Player player, final JobType job) {
        Objects.requireNonNull(player, "player");
        if (!canSelectPrimary(player, job)) return CompletableFuture.completedFuture(false);
        if (job == JobType.DEATH_KNIGHT
                && configManager.getBoolean("classes.death-knight.dark-only", false)) {
            final FactionManager factions = factionManagerRef;
            if (factions != null && !factions.isMember(player.getUniqueId(), FactionType.DARK)) {
                return CompletableFuture.completedFuture(false);
            }
        }
        final String operationId = "class-assign:" + player.getUniqueId() + ':' + job.getId();
        return gateway().assignClass(player.getUniqueId(),
                new ClassSpecProfileGateway.ClassAssignmentRequest(job.getId(), 1, 0, operationId))
                .thenCompose(result -> {
                    if (!result.durableOutcomeAccepted()) return CompletableFuture.completedFuture(false);
                    if (!result.runtimeGenerationUsable()) return CompletableFuture.completedFuture(true);
                    return schedulePlayer(player, () -> {
                        AdvancementService.award(player, "root");
                        AdvancementService.award(player, "first_class");
                    }).thenCompose(ignored -> applyAutoUnlocksV2(player)).thenApply(ignored -> true);
                });
    }

    public CompletionStage<Boolean> mirrorPrimaryLevelV2(final Player player) {
        return CompletableFuture.completedFuture(profileGateway != null
                && profileGateway.isSessionReady(player.getUniqueId()));
    }

    public CompletionStage<Boolean> addXpToJobV2(final Player player, final int amount,
                                                  final String operationId) {
        return addXpToJobResultV2(player, amount, operationId)
                .thenApply(ProfileMutationResult::durableOutcomeAccepted);
    }

    public CompletionStage<Boolean> setXpV2(final Player player, final int xp,
                                             final String operationId) {
        return setXpResultV2(player, xp, operationId)
                .thenApply(ProfileMutationResult::durableOutcomeAccepted);
    }

    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> addXpToJobResultV2(
            final Player player, final int amount, final String operationId) {
        if (amount <= 0 || !hasPrimaryJob(player)) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    gateway().diagnostic(player.getUniqueId()), "class XP target is unavailable"));
        }
        return mutateXpResult(player, ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD,
                amount, operationId);
    }

    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> setXpResultV2(
            final Player player, final int xp, final String operationId) {
        if (xp < 0 || !hasPrimaryJob(player)) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    gateway().diagnostic(player.getUniqueId()), "class XP target is unavailable"));
        }
        return mutateXpResult(player, ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET,
                xp, operationId);
    }

    private CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mutateXpResult(
            final Player player,
            final ClassSpecProfileGateway.ClassExperienceRequest.Mode requestedMode,
            final int requestedValue, final String operationId) {
        final int baseXp = Math.max(1, configManager.getInt("classes.leveling.base-xp", 100));
        final int increment = Math.max(0, configManager.getInt(
                "classes.leveling.increment-per-level", 20));
        final int secondSpecUnlockLevel = Math.max(1, configManager.getInt(
                "classes.specialization.second-slot-level", 28));
        final ClassSpecProfileGateway gateway = gateway();
        final Optional<ProfileOperation> replay = gateway.operation(player.getUniqueId(), operationId);
        final ClassSpecProfileGateway.ClassExperienceRequest.Mode mode;
        final int value;
        if (replay.isPresent()) {
            final ProfileOperation receipt = replay.orElseThrow();
            if (receipt.type() != ProfileOperationType.CLASS_EXPERIENCE) {
                return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                        gateway.diagnostic(player.getUniqueId()), "operation id belongs to another mutation"));
            }
            try {
                mode = ClassSpecProfileGateway.ClassExperienceRequest.Mode.valueOf(receipt.target());
                value = Integer.parseInt(receipt.amount());
            } catch (final IllegalArgumentException invalidReceipt) {
                return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                        gateway.diagnostic(player.getUniqueId()), "invalid durable class XP receipt"));
            }
        } else {
            final ClassSpecSection current = gateway.currentProfile(player.getUniqueId()).orElse(null);
            if (current == null) {
                return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                        gateway.diagnostic(player.getUniqueId()), "class XP profile is unavailable"));
            }
            final int currentXp = current.classExperience();
            long target = requestedMode == ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET
                    ? requestedValue : (long) currentXp + PrologueProgression.applyMultiplier(
                    requestedValue, PrologueContentPolicy.catchUpMultiplier(
                            configManager, current.classLevel(), operationId));
            target = Math.max(0L, Math.min(Integer.MAX_VALUE, target));
            if (PrologueContentPolicy.active(configManager)) {
                target = PrologueContentPolicy.clampClassExperience(
                        configManager, (int) target, baseXp, increment);
            }
            if (target < currentXp || requestedMode == ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET) {
                mode = ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET;
                value = (int) target;
            } else {
                mode = ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD;
                value = (int) Math.min(Integer.MAX_VALUE, target - currentXp);
            }
        }
        return gateway.mutateClassExperience(player.getUniqueId(),
                new ClassSpecProfileGateway.ClassExperienceRequest(mode, value, baseXp,
                        increment, secondSpecUnlockLevel, operationId))
                .thenCompose(result -> {
                    if (!result.runtimeGenerationUsable()) return CompletableFuture.completedFuture(result);
                    return schedulePlayer(player, () -> {
                        if (getPrimaryLevel(player) >= MAX_JOB_LEVEL)
                            AdvancementService.award(player, "class_max");
                    }).thenCompose(ignored -> {
                        final java.util.function.Function<Player, CompletionStage<Void>> hook = xpChangeHook;
                        return hook == null ? CompletableFuture.completedFuture(null) : hook.apply(player);
                    }).thenCompose(ignored -> applyAutoUnlocksV2(player))
                            .handle((ignored, failure) -> {
                                if (failure == null) return result;
                                final String detail = "Class XP runtime reconciliation failed: "
                                        + rootMessage(failure);
                                gateway.blockSession(player.getUniqueId(), detail);
                                final ProfileDiagnostic durable = result.durableProfileOptional()
                                        .orElseGet(() -> gateway.diagnostic(player.getUniqueId()));
                                return ProfileMutationResult.failed(durable,
                                        ProfileMutationResult.Status.RUNTIME_EFFECT_FAILED, detail);
                            });
                });
    }

    public void setXpChangeHook(
            final java.util.function.Function<Player, CompletionStage<Void>> hook) {
        if (hook == null) return;
        final java.util.function.Function<Player, CompletionStage<Void>> previous = xpChangeHook;
        xpChangeHook = previous == null ? hook : player -> previous.apply(player)
                .thenCompose(ignored -> hook.apply(player));
    }

    public CompletionStage<Void> applyAutoUnlocksV2(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) {
            return CompletableFuture.completedFuture(null);
        }
        final ClassSpecSection durable = gateway.currentProfile(player.getUniqueId()).orElse(null);
        return durable == null ? CompletableFuture.completedFuture(null)
                : applyAutoUnlocksV2(player, durable, true);
    }

    public CompletionStage<Void> applyAutoUnlocksV2(final Player player,
                                                    final ClassSpecSection durable) {
        return applyAutoUnlocksV2(player, durable, false);
    }

    private CompletionStage<Void> applyAutoUnlocksV2(final Player player,
                                                     final ClassSpecSection durable,
                                                     final boolean announce) {
        Objects.requireNonNull(durable, "durable");
        final JobType job = JobType.fromId(durable.primaryClassId());
        if (job == null || configManager.getConfiguration() == null)
            return CompletableFuture.completedFuture(null);
        final ConfigurationSection unlocks = configManager.getConfiguration()
                .getConfigurationSection("classes." + job.getId() + ".spell-unlocks");
        if (unlocks == null) return CompletableFuture.completedFuture(null);
        final int level = durable.classLevel();
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final String spellId : unlocks.getKeys(false)) {
            final int required = unlocks.getInt(spellId, Integer.MAX_VALUE);
            if (level < required) continue;
            chain = chain.thenCompose(ignored -> unlockSpellV2(player, spellId,
                            SOURCE_BASE_PREFIX + job.getId())
                    .thenCompose(unlocked -> announce && Boolean.TRUE.equals(unlocked)
                            ? schedulePlayer(player, () -> player.sendMessage(
                            messageManager.getMessage("job-spell-auto-unlocked",
                                    "&aÚj képesség feloldva: &e{spell} &7(szint {level})",
                                    Map.of("spell", messageManager.get(
                                                    "spell." + spellId.toLowerCase(Locale.ROOT)
                                                            + ".name",
                                                    spellId.toLowerCase(Locale.ROOT)),
                                            "level", String.valueOf(required)))))
                            : CompletableFuture.completedFuture(null)));
        }
        return chain;
    }

    public List<String> getUnlockedSpellIds(final Player player) {
        return List.copyOf(readLedger(player).spellIds());
    }

    public boolean hasUnlockedSpell(final Player player, final String spellId) {
        return spellId != null && !spellId.isBlank() && readLedger(player).contains(spellId);
    }

    public CompletionStage<Void> setUnlockedSpellIdsV2(final Player player,
                                                        final List<String> spellIds) {
        SpellGrantLedger ledger = SpellGrantLedger.empty();
        if (spellIds != null) {
            for (final String spellId : spellIds) {
                if (spellId != null && !spellId.isBlank())
                    ledger = ledger.add(spellId, SOURCE_ADMIN).ledger();
            }
        }
        final SpellGrantLedger requested = ledger;
        return spellGrantStore.replace(player.getUniqueId(), requested).thenApply(ignored -> null);
    }

    public CompletionStage<Boolean> unlockSpellV2(final Player player, final String spellId) {
        return unlockSpellV2(player, spellId, SOURCE_ADMIN);
    }

    public CompletionStage<Boolean> unlockSpellV2(final Player player,
                                                   final String spellId,
                                                   final String source) {
        return spellGrantStore.add(player.getUniqueId(), spellId, source)
                .thenApply(mutation -> mutation.changed() && mutation.spellLockChanged());
    }

    public CompletionStage<Boolean> revokeGrantV2(final Player player,
                                                   final String spellId,
                                                   final String source) {
        return spellGrantStore.remove(player.getUniqueId(), spellId, source)
                .thenApply(mutation -> mutation.changed() && mutation.spellLockChanged());
    }

    public CompletionStage<List<String>> revokeGrantsFromV2(
            final Player player, final Predicate<String> sourceMatches) {
        return spellGrantStore.revokeSources(player.getUniqueId(), sourceMatches)
                .thenApply(result -> result.lockedSpellIds());
    }

    public CompletionStage<Void> clearSpellGrantsV2(final Player player) {
        return spellGrantStore.replace(player.getUniqueId(), SpellGrantLedger.empty())
                .thenApply(ignored -> null);
    }

    public Set<String> getGrantSources(final Player player, final String spellId) {
        if (spellId == null || spellId.isBlank()) return Set.of();
        return readLedger(player).sources(spellId);
    }

    public void backfillSpellGrants(final Player player) { readLedger(player); }

    private SpellGrantLedger readLedger(final Player player) {
        return spellGrantStore.read(player.getUniqueId());
    }

    private CompletionStage<Void> schedulePlayer(final Player player, final Runnable work) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            try { work.run(); result.complete(null); }
            catch (final Throwable failure) { result.completeExceptionally(failure); }
        }, () -> result.completeExceptionally(
                new IllegalStateException("Player scheduler rejected Profile v2 effect")));
        return result;
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public void cleanup(final UUID playerId) { }
    public void clearPlayerState(final UUID playerId) { cleanup(playerId); }
}
