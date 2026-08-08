package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.GateSnapshot;
import hu.taliann.icesmp.classspec.application.GateState;
import hu.taliann.icesmp.classspec.application.ProfileDiagnostic;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileSpecializationProgressStore;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** Profile v2 and typed PlayerProfile sections are the sole specialization authorities. */
public final class SpecializationManager {
    private static final String DOCTRINE_BRANCH = "core";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final JobManager jobManager;
    private final ProfessionManager professionManager;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final QuestManager questManager;
    private final PlayerProfileSpecializationProgressStore progressStore =
            new PlayerProfileSpecializationProgressStore();
    private final Map<UUID, ProfessionSpecializationType> professionMirror =
            new ConcurrentHashMap<>();
    private final java.util.Set<UUID> professionMutationPending =
            ConcurrentHashMap.newKeySet();
    private volatile ClassSpecProfileGateway profileGateway;

    public SpecializationManager(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager, final JobManager jobManager,
                                 final ProfessionManager professionManager,
                                 final FactionManager factionManager,
                                 final SinManager sinManager, final QuestManager questManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.configManager = Objects.requireNonNull(configManager);
        this.messageManager = Objects.requireNonNull(messageManager);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.professionManager = Objects.requireNonNull(professionManager);
        this.factionManager = Objects.requireNonNull(factionManager);
        this.sinManager = Objects.requireNonNull(sinManager);
        this.questManager = Objects.requireNonNull(questManager);
    }

    public void setProfileGateway(final ClassSpecProfileGateway gateway) {
        profileGateway = Objects.requireNonNull(gateway, "profileGateway");
    }

    public ClassSpecProfileGateway profileGateway() {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null) {
            throw new IllegalStateException("Profile v2 gateway is not initialized");
        }
        return gateway;
    }

    public boolean hasMemorySpecUnlock(final Player player) {
        if (player == null) return false;
        try {
            return progressStore.memoryUnlocked(player.getUniqueId());
        } catch (final RuntimeException notReady) {
            return false;
        }
    }

    public void grantMemorySpecUnlock(final Player player) {
        if (player == null) return;
        progressStore.grantMemoryUnlock(player.getUniqueId()).exceptionally(failure -> {
            plugin.getLogger().severe("PlayerProfile memory specialization unlock failed for "
                    + player.getUniqueId() + ": " + rootMessage(failure));
            return false;
        });
    }

    public int getRequiredClassLevel() {
        return Math.max(1, configManager.getInt("classes.specialization.required-level", 25));
    }

    public int getRequiredProfessionLevel() {
        return Math.max(1, configManager.getInt(
                "professions.specialization.required-level", 25));
    }

    public SpecializationType getClassSpecialization(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (player == null || gateway == null
                || !gateway.isSessionReady(player.getUniqueId())) return null;
        return gateway.activeSpecId(player.getUniqueId())
                .map(SpecializationType::fromId).orElse(null);
    }

    /** No PDC mirror remains; callers may retain the hook as a source-compatible no-op. */
    public void mirrorActiveClassSpecializationV2(final Player player) { }

    public void mirrorActiveClassSpecializationV2(final Player player,
                                                  final ClassSpecSection durable) {
        Objects.requireNonNull(durable, "durable");
    }

    public ProfessionSpecializationType getProfessionSpecialization(final Player player) {
        if (player == null) return null;
        final UUID id = player.getUniqueId();
        final ProfessionSpecializationType mirror = professionMirror.get(id);
        if (mirror != null) return mirror;
        try {
            final ProfessionSpecializationType durable = progressStore
                    .professionSpecialization(id).orElse(null);
            if (durable != null) professionMirror.put(id, durable);
            return durable;
        } catch (final RuntimeException notReady) {
            return null;
        }
    }

    /**
     * Returns true both for adding a new loadout and for activating an already-owned inactive
     * loadout. SEALED loadouts fail closed and the second slot is never bypassed.
     */
    public boolean canSelectClassSpecialization(final Player player,
                                                final SpecializationType specialization) {
        if (player == null || specialization == null) return false;
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return false;
        if (jobManager.getPrimaryJob(player) != specialization.getParentJob()) return false;
        if (jobManager.getPrimaryLevel(player) < getRequiredClassLevel()
                && !hasMemorySpecUnlock(player)) return false;
        if (captureGateSnapshot(player, specialization).missingReason() != null) return false;

        final ClassSpecSection profile = gateway.currentProfile(player.getUniqueId()).orElse(null);
        if (profile == null) return false;
        final LoadoutSlot existing = findSlot(profile, specialization.getId());
        if (existing != null) {
            final LoadoutStatus status = profile.loadout(existing).status();
            return status == LoadoutStatus.ACTIVE || status == LoadoutStatus.INACTIVE;
        }
        return selectionSlot(profile).isPresent();
    }

    public boolean selectClassSpecialization(final Player player,
                                             final SpecializationType specialization) {
        return false;
    }

    /**
     * New specs occupy the first available unlocked slot. Selecting an already-owned spec performs
     * a durable active-loadout switch. Adding slot two is followed by the same switch operation so
     * `/spec choose` always leaves the chosen specialization active rather than silently parking it.
     */
    public CompletionStage<Boolean> selectClassSpecializationV2(
            final Player player, final SpecializationType specialization) {
        if (!canSelectClassSpecialization(player, specialization)) {
            return CompletableFuture.completedFuture(false);
        }
        final ClassSpecProfileGateway gateway = profileGateway();
        final ClassSpecSection profile = gateway.currentProfile(player.getUniqueId()).orElse(null);
        if (profile == null) return CompletableFuture.completedFuture(false);

        final LoadoutSlot existing = findSlot(profile, specialization.getId());
        if (existing != null) {
            if (profile.activeSlot() == existing
                    && profile.loadout(existing).status() == LoadoutStatus.ACTIVE) {
                return CompletableFuture.completedFuture(true);
            }
            return gateway.switchActiveLoadout(player.getUniqueId(),
                            new ClassSpecProfileGateway.SwitchLoadoutRequest(existing))
                    .thenApply(this::mutationSucceeded);
        }

        final LoadoutSlot target = selectionSlot(profile).orElse(null);
        if (target == null) return CompletableFuture.completedFuture(false);
        final GateSnapshot gates = captureGateSnapshot(player, specialization);
        return gateway.select(player.getUniqueId(),
                        new ClassSpecProfileGateway.SelectRequest(
                                specialization.getId(), target, gates))
                .thenCompose(result -> {
                    if (!mutationSucceeded(result)) {
                        return CompletableFuture.completedFuture(false);
                    }
                    final ClassSpecSection durable = gateway.currentProfile(player.getUniqueId())
                            .orElse(null);
                    if (durable == null || durable.activeSlot() == target) {
                        return CompletableFuture.completedFuture(durable != null);
                    }
                    return gateway.switchActiveLoadout(player.getUniqueId(),
                                    new ClassSpecProfileGateway.SwitchLoadoutRequest(target))
                            .thenApply(this::mutationSucceeded);
                });
    }

    public List<String> availableDoctrineChoices(final Player player) {
        final SpecializationType specialization = getClassSpecialization(player);
        if (specialization == null) return List.of();
        return configManager.getStringList("class-gameplay.specializations."
                        + specialization.getId() + ".doctrines")
                .stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    public String activeDoctrine(final Player player) {
        if (player == null) return "";
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return "";
        return gateway.currentProfile(player.getUniqueId())
                .filter(profile -> profile.activeSlot() != null)
                .map(profile -> profile.loadout(profile.activeSlot()).doctrineChoices()
                        .getOrDefault(DOCTRINE_BRANCH, ""))
                .orElse("");
    }

    public CompletionStage<Boolean> chooseDoctrineV2(final Player player, final String rawChoice) {
        if (player == null || rawChoice == null) return CompletableFuture.completedFuture(false);
        final String choice = rawChoice.trim().toLowerCase(Locale.ROOT);
        if (!availableDoctrineChoices(player).contains(choice)) {
            return CompletableFuture.completedFuture(false);
        }
        final ClassSpecProfileGateway gateway = profileGateway();
        final ClassSpecSection profile = gateway.currentProfile(player.getUniqueId()).orElse(null);
        if (profile == null || profile.activeSlot() == null) {
            return CompletableFuture.completedFuture(false);
        }
        final ClassLoadout active = profile.loadout(profile.activeSlot());
        final int requiredRank = Math.max(0, Math.min(10,
                configManager.getInt("class-gameplay.doctrine.unlock-mastery-rank", 3)));
        if (active.mastery().rank() < requiredRank) {
            return CompletableFuture.completedFuture(false);
        }
        return gateway.chooseDoctrine(player.getUniqueId(),
                        new ClassSpecProfileGateway.DoctrineChoiceRequest(
                                profile.activeSlot(), DOCTRINE_BRANCH, choice))
                .thenApply(this::mutationSucceeded);
    }

    private boolean mutationSucceeded(final ProfileMutationResult<?> result) {
        return result != null && (result.committed()
                || result.status() == ProfileMutationResult.Status.NO_CHANGE);
    }

    private static LoadoutSlot findSlot(final ClassSpecSection profile, final String specId) {
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            if (specId.equals(profile.loadout(slot).specializationId())) return slot;
        }
        return null;
    }

    private static Optional<LoadoutSlot> selectionSlot(final ClassSpecSection profile) {
        if (profile.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.EMPTY) {
            return Optional.of(LoadoutSlot.FIRST);
        }
        if (profile.secondSpecUnlocked()
                && profile.loadout(LoadoutSlot.SECOND).status() == LoadoutStatus.EMPTY) {
            return Optional.of(LoadoutSlot.SECOND);
        }
        return Optional.empty();
    }

    public GateSnapshot captureGateSnapshot(final Player player,
                                            final SpecializationType specialization) {
        final boolean factionRequired = specialization.getRequiredFaction() != null;
        final boolean factionSatisfied = !factionRequired
                || factionManager.isMember(player.getUniqueId(), specialization.getRequiredFaction());
        final boolean sinnerRequired = specialization.requiresSinner();
        final boolean sinnerSatisfied = !sinnerRequired || sinManager.isSinner(player);
        final String requiredQuest = configManager.getString(
                "specializations." + specialization.getId() + ".required-quest", "").trim();
        final boolean questRequired = !requiredQuest.isEmpty();
        final boolean questSatisfied = !questRequired
                || questManager.hasCompleted(player, requiredQuest);
        final GateState state = GateState.ofRequirements(factionRequired, factionSatisfied,
                sinnerRequired, sinnerSatisfied, questRequired, questSatisfied);
        final Map<GateState.Gate, String> ids = new EnumMap<>(GateState.Gate.class);
        if (factionRequired) {
            ids.put(GateState.Gate.FACTION,
                    "faction:" + specialization.getRequiredFaction().name()
                            .toLowerCase(Locale.ROOT));
        }
        if (sinnerRequired) ids.put(GateState.Gate.SINNER, "sinner:permanent");
        if (questRequired) {
            ids.put(GateState.Gate.QUEST, "quest:" + requiredQuest.toLowerCase(Locale.ROOT));
        }
        return new GateSnapshot(state, ids);
    }

    public boolean canSelectProfessionSpecialization(
            final Player player, final ProfessionSpecializationType specialization) {
        return player != null && specialization != null
                && !professionMutationPending.contains(player.getUniqueId())
                && getProfessionSpecialization(player) == null
                && professionManager.hasProfession(player, specialization.getParentProfession())
                && professionManager.getLevel(player, specialization.getParentProfession())
                >= getRequiredProfessionLevel();
    }

    /** Optimistic compatibility API; canonical selection is a ProfessionSection CAS. */
    public boolean selectProfessionSpecialization(
            final Player player, final ProfessionSpecializationType specialization) {
        if (!canSelectProfessionSpecialization(player, specialization)) return false;
        final UUID playerId = player.getUniqueId();
        if (!professionMutationPending.add(playerId)) return false;
        professionMirror.put(playerId, specialization);
        progressStore.selectProfessionSpecialization(playerId, specialization)
                .whenComplete((selected, failure) -> {
                    professionMutationPending.remove(playerId);
                    if (failure != null || !Boolean.TRUE.equals(selected)) {
                        professionMirror.remove(playerId, specialization);
                        plugin.getLogger().severe(
                                "PlayerProfile profession specialization failed for "
                                        + playerId + ": "
                                        + (failure == null ? "already selected"
                                        : rootMessage(failure)));
                    }
                });
        return true;
    }

    public boolean resetDarkGatedSpecialization(final Player player) {
        final SpecializationType current = getClassSpecialization(player);
        if (current == null || current.getRequiredFaction() == null
                && !current.requiresSinner()
                && configManager.getString("specializations." + current.getId()
                + ".required-quest", "").isBlank()) return false;
        reconcileDarkGates(player);
        return true;
    }

    public void resetSpecializations(final Player player) {
        resetProfessionSpecialization(player);
    }

    public void resetClassSpecialization(final Player player) { }

    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> resetClassSpecSection(
            final Player player, final boolean adminClassReset, final String operationId) {
        return resetClassSpecSection(player.getUniqueId(), adminClassReset, operationId);
    }

    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> resetClassSpecSection(
            final UUID playerId, final boolean adminClassReset, final String operationId) {
        final ClassSpecProfileGateway gateway = profileGateway();
        final ProfileDiagnostic diagnostic = gateway.diagnostic(playerId);
        final Optional<LoadoutSlot> slot = adminClassReset ? Optional.empty()
                : diagnostic.activeSlot().or(() -> Optional.of(LoadoutSlot.FIRST));
        return gateway.reset(playerId, new ClassSpecProfileGateway.ResetRequest(
                adminClassReset ? ClassSpecProfileGateway.ResetMode.ADMIN_CLASS
                        : ClassSpecProfileGateway.ResetMode.LOADOUT_RESPEC,
                slot, operationId));
    }

    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> reconcileDarkGates(
            final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway();
        if (!gateway.isSessionReady(player.getUniqueId())) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    gateway.diagnostic(player.getUniqueId()),
                    "Profile v2 session is not ready"));
        }
        final ProfileDiagnostic diagnostic = gateway.diagnostic(player.getUniqueId());
        final Map<LoadoutSlot, GateSnapshot> snapshots = new EnumMap<>(LoadoutSlot.class);
        for (final Map.Entry<LoadoutSlot, ProfileDiagnostic.SlotDiagnostic> entry
                : diagnostic.slots().entrySet()) {
            final SpecializationType type = entry.getValue().specializationId()
                    .map(SpecializationType::fromId).orElse(null);
            if (type != null && (type.getRequiredFaction() != null || type.requiresSinner()
                    || !configManager.getString("specializations." + type.getId()
                    + ".required-quest", "").isBlank())) {
                snapshots.put(entry.getKey(), captureGateSnapshot(player, type));
            }
        }
        return gateway.reconcile(player.getUniqueId(),
                new ClassSpecProfileGateway.ReconcileRequest(snapshots));
    }

    public void resetProfessionSpecialization(final Player player) {
        if (player == null) return;
        final UUID id = player.getUniqueId();
        professionMirror.remove(id);
        progressStore.resetProfessionSpecialization(id).exceptionally(failure -> {
            plugin.getLogger().severe("PlayerProfile profession specialization reset failed for "
                    + id + ": " + rootMessage(failure));
            return false;
        });
    }

    public double getRespecCost() {
        final double configured = configManager.getDouble(
                "specializations.respec-cost", 100.0D);
        if (!Double.isFinite(configured) || configured < 0.0D) {
            throw new IllegalStateException(
                    "specializations.respec-cost must be finite and non-negative");
        }
        return configured;
    }

    public void applyClassSpecializationUnlocks(final Player player) {
        applyClassSpecializationUnlocks(player, getClassSpecialization(player),
                jobManager.getPrimaryJob(player), jobManager.getPrimaryLevel(player))
                .exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile spec spell reconcile failed for "
                            + player.getUniqueId() + ": " + rootMessage(failure));
                    return null;
                });
    }

    public CompletionStage<Void> applyClassSpecializationUnlocksV2(
            final Player player, final ClassSpecSection durable) {
        Objects.requireNonNull(durable, "durable");
        final SpecializationType specialization = durable.activeSlot() == null ? null
                : SpecializationType.fromId(durable.loadout(durable.activeSlot())
                .specializationId());
        return applyClassSpecializationUnlocks(player, specialization,
                JobType.fromId(durable.primaryClassId()), durable.classLevel());
    }

    private CompletionStage<Void> applyClassSpecializationUnlocks(
            final Player player, final SpecializationType specialization,
            final JobType primaryJob, final int classLevel) {
        if (specialization == null || primaryJob != specialization.getParentJob()
                || configManager.getConfiguration() == null) {
            return CompletableFuture.completedFuture(null);
        }
        final ConfigurationSection unlocks = configManager.getConfiguration()
                .getConfigurationSection("specializations." + specialization.getId()
                        + ".spell-unlocks");
        if (unlocks == null) return CompletableFuture.completedFuture(null);
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final String spellId : unlocks.getKeys(false)) {
            final int required = unlocks.getInt(spellId, Integer.MAX_VALUE);
            if (classLevel < required) continue;
            chain = chain.thenCompose(ignored -> jobManager.unlockSpellV2(player, spellId,
                            JobManager.SOURCE_SPEC_PREFIX + specialization.getId())
                    .thenCompose(unlocked -> Boolean.TRUE.equals(unlocked)
                            ? notifySpecSpellUnlocked(player, spellId, required)
                            : CompletableFuture.completedFuture(null)));
        }
        return chain;
    }

    private CompletionStage<Void> notifySpecSpellUnlocked(
            final Player player, final String spellId, final int required) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            player.sendMessage(messageManager.getMessage("spec-spell-unlocked",
                    "&5Specializációs képesség feloldva: &e{spell} &7(szint {level})",
                    Map.of("spell", spellId.toLowerCase(Locale.ROOT),
                            "level", Integer.toString(required))));
            result.complete(null);
        }, () -> result.completeExceptionally(
                new IllegalStateException("Player scheduler rejected spec spell notification")));
        return result;
    }

    public void clearPlayerState(final UUID playerId) {
        professionMirror.remove(playerId);
        professionMutationPending.remove(playerId);
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
