package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.GateSnapshot;
import hu.taliann.icesmp.classspec.application.GateState;
import hu.taliann.icesmp.classspec.application.ProfileDiagnostic;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileSpecializationProgressStore;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import hu.taliann.icesmp.spells.SpellTargetingUtil;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.warrior.WarriorGameplayService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Profile v2 and typed PlayerProfile sections are the sole specialization authorities. */
public final class SpecializationManager {
    private static final String BERSERKER_TRIAL = "warrior_berserker_broken_horn";
    private static final String GUARDIAN_TRIAL = "warrior_guardian_last_wall";

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
    private final Set<UUID> professionMutationPending = ConcurrentHashMap.newKeySet();

    private volatile ClassSpecProfileGateway profileGateway;
    private volatile ResourceManager resourceManager;
    private volatile WarriorGameplayService warriorGameplayService;
    private volatile CatalystItemFactory warriorSoulbondFactory;
    private volatile Consumer<UUID> warriorSwitchCleanup = ignored -> { };
    private volatile Consumer<Player> warriorProfileRefresh = ignored -> { };

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

    public synchronized void setProfileGateway(final ClassSpecProfileGateway gateway) {
        profileGateway = Objects.requireNonNull(gateway, "profileGateway");
        if (warriorGameplayService != null) return;
        final CatalystItemFactory factory = CatalystItemFactory.installed().orElse(null);
        if (factory == null) {
            throw new IllegalStateException("Lélekkapocs factory is not initialized before Profile v2");
        }
        final WarriorGameplayService runtime = new WarriorGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        plugin.getServer().getPluginManager().registerEvents(runtime, plugin);
        warriorSoulbondFactory = factory;
        warriorGameplayService = runtime;
        warriorSwitchCleanup = runtime::clearSpecializationState;
        warriorProfileRefresh = this::scheduleWarriorProfileRefresh;
        jobManager.setXpChangeHook(player -> reconcileWarriorProgression(player)
                .exceptionally(failure -> {
                    plugin.getLogger().severe("Warrior level progression reconcile failed for "
                            + player.getUniqueId() + ": " + rootMessage(failure));
                    return null;
                }));
    }

    public Optional<WarriorGameplayService> warriorGameplayService() {
        return Optional.ofNullable(warriorGameplayService);
    }

    public void setSwitchSafetyResource(final ResourceManager resources) {
        resourceManager = Objects.requireNonNull(resources, "resources");
    }

    public void setWarriorRuntimeCallbacks(final Consumer<UUID> switchCleanup,
                                           final Consumer<Player> profileRefresh) {
        warriorSwitchCleanup = switchCleanup == null ? ignored -> { } : switchCleanup;
        warriorProfileRefresh = profileRefresh == null ? ignored -> { } : profileRefresh;
    }

    public ClassSpecProfileGateway profileGateway() {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null) throw new IllegalStateException("Profile v2 gateway is not initialized");
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

    public int getSecondSpecUnlockLevel() {
        return Math.max(getRequiredClassLevel(),
                configManager.getInt("classes.specialization.second-slot-level", 28));
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

    public boolean canSelectClassSpecialization(final Player player,
                                                final SpecializationType specialization) {
        if (player == null || specialization == null) return false;
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return false;
        if (jobManager.getPrimaryJob(player) != specialization.getParentJob()) return false;
        if (jobManager.getPrimaryLevel(player) < getRequiredClassLevel()
                && !hasMemorySpecUnlock(player)) return false;
        final ProfileDiagnostic diagnostic = gateway.diagnostic(player.getUniqueId());
        if (slotContaining(diagnostic, specialization).isPresent()) return false;
        if (selectionSlot(diagnostic).isEmpty()) return false;
        return captureGateSnapshot(player, specialization).missingReason() == null;
    }

    public boolean selectClassSpecialization(final Player player,
                                             final SpecializationType specialization) {
        return false;
    }

    public CompletionStage<Boolean> selectClassSpecializationV2(
            final Player player, final SpecializationType specialization) {
        if (!canSelectClassSpecialization(player, specialization)) {
            return CompletableFuture.completedFuture(false);
        }
        final ProfileDiagnostic diagnostic = profileGateway().diagnostic(player.getUniqueId());
        final LoadoutSlot slot = selectionSlot(diagnostic).orElseThrow();
        return profileGateway().select(player.getUniqueId(),
                        new ClassSpecProfileGateway.SelectRequest(
                                specialization.getId(), slot,
                                captureGateSnapshot(player, specialization)))
                .thenApply(result -> {
                    final boolean success = result.committed()
                            || result.status() == ProfileMutationResult.Status.NO_CHANGE;
                    if (success) warriorProfileRefresh.accept(player);
                    return success;
                });
    }

    public boolean canSwitchClassSpecialization(final Player player,
                                                final LoadoutSlot targetSlot) {
        if (player == null || targetSlot == null
                || jobManager.getPrimaryJob(player) != JobType.WARRIOR) return false;
        final ClassSpecProfileGateway gateway = profileGateway;
        final ResourceManager resources = resourceManager;
        if (gateway == null || resources == null
                || !gateway.isSessionReady(player.getUniqueId())) return false;
        final ProfileDiagnostic diagnostic = gateway.diagnostic(player.getUniqueId());
        final ProfileDiagnostic.SlotDiagnostic target = diagnostic.slots().get(targetSlot);
        if (target == null || target.status() != LoadoutStatus.INACTIVE
                || diagnostic.activeSlot().filter(targetSlot::equals).isPresent()) return false;
        final long graceMillis = Math.max(1L, configManager.getLong(
                "classes.specialization.switch-combat-grace-seconds", 8L)) * 1000L;
        if (resources.isInCombat(player.getUniqueId(), graceMillis)) return false;
        final double radius = Math.max(1.0D, configManager.getDouble(
                "classes.specialization.switch-safe-radius", 12.0D));
        return player.getNearbyEntities(radius, radius, radius).stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .noneMatch(entity -> SpellTargetingUtil.isHostileTarget(player, entity));
    }

    public CompletionStage<Boolean> switchClassSpecializationV2(
            final Player player, final LoadoutSlot targetSlot) {
        if (!canSwitchClassSpecialization(player, targetSlot)) {
            return CompletableFuture.completedFuture(false);
        }
        return profileGateway().switchLoadout(player.getUniqueId(),
                        new ClassSpecProfileGateway.SwitchRequest(targetSlot))
                .thenApply(result -> {
                    final boolean success = result.committed()
                            || result.status() == ProfileMutationResult.Status.NO_CHANGE;
                    if (success) {
                        warriorSwitchCleanup.accept(player.getUniqueId());
                        warriorProfileRefresh.accept(player);
                    }
                    return success;
                });
    }

    public Set<String> doctrineChoices(final SpecializationType specialization,
                                       final int level) {
        if (specialization == null) return Set.of();
        return switch (specialization) {
            case BERSERKER -> switch (level) {
                case 30 -> Set.of("bloodlust", "titan");
                case 40 -> Set.of("whirlwind", "executioner");
                case 50 -> Set.of("defiant", "kallan_wrath");
                default -> Set.of();
            };
            case GUARDIAN -> switch (level) {
                case 30 -> Set.of("bastion", "vanguard");
                case 40 -> Set.of("shield_wall", "war_signal");
                case 50 -> Set.of("for_one", "for_all");
                default -> Set.of();
            };
            default -> Set.of();
        };
    }

    public Optional<String> activeDoctrine(final Player player, final int level) {
        if (player == null || !Set.of(30, 40, 50).contains(level)) return Optional.empty();
        final ClassSpecSection profile = profileGateway().currentProfile(player.getUniqueId())
                .orElse(null);
        if (profile == null || profile.activeSlot() == null) return Optional.empty();
        return Optional.ofNullable(profile.loadout(profile.activeSlot())
                .doctrineChoices().get(doctrineTier(level)));
    }

    public CompletionStage<Boolean> chooseDoctrineV2(final Player player,
                                                     final int level,
                                                     final String choice) {
        if (player == null || choice == null || !Set.of(30, 40, 50).contains(level)
                || jobManager.getPrimaryJob(player) != JobType.WARRIOR
                || jobManager.getPrimaryLevel(player) < level) {
            return CompletableFuture.completedFuture(false);
        }
        final SpecializationType specialization = getClassSpecialization(player);
        final String normalized = choice.trim().toLowerCase(Locale.ROOT);
        if (!doctrineChoices(specialization, level).contains(normalized)) {
            return CompletableFuture.completedFuture(false);
        }
        final ClassSpecSection profile = profileGateway().currentProfile(player.getUniqueId())
                .orElse(null);
        if (profile == null || profile.activeSlot() == null) {
            return CompletableFuture.completedFuture(false);
        }
        return profileGateway().chooseDoctrine(player.getUniqueId(),
                        new ClassSpecProfileGateway.DoctrineChoiceRequest(
                                profile.activeSlot(), doctrineTier(level), normalized))
                .thenApply(result -> {
                    final boolean success = result.committed()
                            || result.status() == ProfileMutationResult.Status.NO_CHANGE;
                    if (success) warriorProfileRefresh.accept(player);
                    return success;
                });
    }

    public CompletionStage<Boolean> contributeWarriorMastery(final Player player,
                                                             final int experience) {
        if (player == null || experience <= 0 || jobManager.getPrimaryJob(player) != JobType.WARRIOR
                || jobManager.getPrimaryLevel(player) < 50) {
            return CompletableFuture.completedFuture(false);
        }
        final ClassSpecSection profile = profileGateway().currentProfile(player.getUniqueId())
                .orElse(null);
        if (profile == null || profile.activeSlot() == null
                || !isWarriorSpec(profile.loadout(profile.activeSlot()).specializationId())) {
            return CompletableFuture.completedFuture(false);
        }
        final long perRank = Math.max(1L, configManager.getLong(
                "classes.warrior.mastery.experience-per-rank", 100L));
        return profileGateway().contributeMastery(player.getUniqueId(),
                        new ClassSpecProfileGateway.MasteryContributionRequest(
                                profile.activeSlot(), experience, perRank))
                .thenApply(result -> {
                    if (result.committed()) warriorProfileRefresh.accept(player);
                    return result.committed();
                });
    }

    public CompletionStage<Void> reconcileWarriorProgression(final Player player) {
        if (player == null || jobManager.getPrimaryJob(player) != JobType.WARRIOR
                || jobManager.getPrimaryLevel(player) < 50) {
            return CompletableFuture.completedFuture(null);
        }
        final ClassSpecSection profile = profileGateway().currentProfile(player.getUniqueId())
                .orElse(null);
        if (profile == null) return CompletableFuture.completedFuture(null);
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            final ClassLoadout loadout = profile.loadout(slot);
            if (!isWarriorSpec(loadout.specializationId())
                    || loadout.capstoneStatus() != CapstoneStatus.LOCKED) continue;
            chain = chain.thenCompose(ignored -> profileGateway().setCapstone(
                            player.getUniqueId(),
                            new ClassSpecProfileGateway.CapstoneRequest(
                                    slot, CapstoneStatus.AVAILABLE))
                    .thenApply(result -> null));
        }
        return chain.thenRun(() -> warriorProfileRefresh.accept(player));
    }

    public CompletionStage<Boolean> onQuestCompleted(final Player player,
                                                     final String questId) {
        if (player == null || questId == null || jobManager.getPrimaryJob(player) != JobType.WARRIOR
                || jobManager.getPrimaryLevel(player) < 50) {
            return CompletableFuture.completedFuture(false);
        }
        final String requiredSpec = switch (questId.toLowerCase(Locale.ROOT)) {
            case BERSERKER_TRIAL -> "berserker";
            case GUARDIAN_TRIAL -> "guardian";
            default -> "";
        };
        if (requiredSpec.isEmpty()) return CompletableFuture.completedFuture(false);
        final ClassSpecSection profile = profileGateway().currentProfile(player.getUniqueId())
                .orElse(null);
        if (profile == null) return CompletableFuture.completedFuture(false);
        final Optional<LoadoutSlot> slot = slotContaining(profile, requiredSpec);
        if (slot.isEmpty()) return CompletableFuture.completedFuture(false);
        return profileGateway().setCapstone(player.getUniqueId(),
                        new ClassSpecProfileGateway.CapstoneRequest(
                                slot.orElseThrow(), CapstoneStatus.COMPLETED))
                .thenCompose(result -> {
                    final boolean success = result.committed()
                            || result.status() == ProfileMutationResult.Status.NO_CHANGE;
                    if (!success) return CompletableFuture.completedFuture(false);
                    final ClassSpecSection durable = profileGateway().currentProfile(player.getUniqueId())
                            .orElse(null);
                    final CompletionStage<Void> grants = durable == null
                            ? CompletableFuture.completedFuture(null)
                            : applyClassSpecializationUnlocksV2(player, durable);
                    return grants.thenApply(ignored -> {
                        warriorProfileRefresh.accept(player);
                        return true;
                    });
                });
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
        final boolean questSatisfied = !questRequired || questManager.hasCompleted(player, requiredQuest);
        final GateState state = GateState.ofRequirements(factionRequired, factionSatisfied,
                sinnerRequired, sinnerSatisfied, questRequired, questSatisfied);
        final Map<GateState.Gate, String> ids = new EnumMap<>(GateState.Gate.class);
        if (factionRequired) {
            ids.put(GateState.Gate.FACTION,
                    "faction:" + specialization.getRequiredFaction().name().toLowerCase(Locale.ROOT));
        }
        if (sinnerRequired) ids.put(GateState.Gate.SINNER, "sinner:permanent");
        if (questRequired) ids.put(GateState.Gate.QUEST,
                "quest:" + requiredQuest.toLowerCase(Locale.ROOT));
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
                        plugin.getLogger().severe("PlayerProfile profession specialization failed for "
                                + playerId + ": " + (failure == null ? "already selected"
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
        return resetClassSpecSection(player.getUniqueId(), adminClassReset, operationId)
                .thenApply(result -> {
                    if (result.committed() || result.status() == ProfileMutationResult.Status.NO_CHANGE) {
                        warriorSwitchCleanup.accept(player.getUniqueId());
                        warriorProfileRefresh.accept(player);
                    }
                    return result;
                });
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
                    gateway.diagnostic(player.getUniqueId()), "Profile v2 session is not ready"));
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
                        new ClassSpecProfileGateway.ReconcileRequest(snapshots))
                .thenCompose(result -> reconcileCompletedWarriorTrials(player)
                        .thenApply(ignored -> result));
    }

    private CompletionStage<Void> reconcileCompletedWarriorTrials(final Player player) {
        if (player == null || jobManager.getPrimaryJob(player) != JobType.WARRIOR) {
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        if (questManager.hasCompleted(player, BERSERKER_TRIAL)) {
            chain = chain.thenCompose(ignored -> onQuestCompleted(player, BERSERKER_TRIAL)
                    .thenApply(done -> null));
        }
        if (questManager.hasCompleted(player, GUARDIAN_TRIAL)) {
            chain = chain.thenCompose(ignored -> onQuestCompleted(player, GUARDIAN_TRIAL)
                    .thenApply(done -> null));
        }
        return chain;
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
        final double configured = configManager.getDouble("specializations.respec-cost", 100.0D);
        if (!Double.isFinite(configured) || configured < 0.0D) {
            throw new IllegalStateException("specializations.respec-cost must be finite and non-negative");
        }
        return configured;
    }

    public void applyClassSpecializationUnlocks(final Player player) {
        final ClassSpecSection durable = profileGateway().currentProfile(player.getUniqueId()).orElse(null);
        final ClassLoadout active = durable == null || durable.activeSlot() == null
                ? null : durable.loadout(durable.activeSlot());
        applyClassSpecializationUnlocks(player, getClassSpecialization(player),
                jobManager.getPrimaryJob(player), jobManager.getPrimaryLevel(player),
                active == null ? CapstoneStatus.LOCKED : active.capstoneStatus())
                .exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile spec spell reconcile failed for "
                            + player.getUniqueId() + ": " + rootMessage(failure));
                    return null;
                });
    }

    public CompletionStage<Void> applyClassSpecializationUnlocksV2(
            final Player player, final ClassSpecSection durable) {
        Objects.requireNonNull(durable, "durable");
        final ClassLoadout active = durable.activeSlot() == null
                ? null : durable.loadout(durable.activeSlot());
        final SpecializationType specialization = active == null ? null
                : SpecializationType.fromId(active.specializationId());
        return applyClassSpecializationUnlocks(player, specialization,
                JobType.fromId(durable.primaryClassId()), durable.classLevel(),
                active == null ? CapstoneStatus.LOCKED : active.capstoneStatus());
    }

    private CompletionStage<Void> applyClassSpecializationUnlocks(
            final Player player, final SpecializationType specialization,
            final JobType primaryJob, final int classLevel,
            final CapstoneStatus capstoneStatus) {
        if (specialization == null || primaryJob != specialization.getParentJob()
                || configManager.getConfiguration() == null) {
            return CompletableFuture.completedFuture(null);
        }
        final ConfigurationSection unlocks = configManager.getConfiguration()
                .getConfigurationSection("specializations." + specialization.getId() + ".spell-unlocks");
        if (unlocks == null) return CompletableFuture.completedFuture(null);
        final String capstoneSpell = configManager.getString(
                "specializations." + specialization.getId() + ".capstone-spell", "")
                .trim().toLowerCase(Locale.ROOT);
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final String spellId : unlocks.getKeys(false)) {
            final int required = unlocks.getInt(spellId, Integer.MAX_VALUE);
            if (classLevel < required) continue;
            if (spellId.equalsIgnoreCase(capstoneSpell)
                    && capstoneStatus != CapstoneStatus.COMPLETED) continue;
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

    private Optional<LoadoutSlot> selectionSlot(final ProfileDiagnostic diagnostic) {
        final ProfileDiagnostic.SlotDiagnostic first = diagnostic.slots().get(LoadoutSlot.FIRST);
        if (first != null && first.status() == LoadoutStatus.EMPTY) return Optional.of(LoadoutSlot.FIRST);
        final ProfileDiagnostic.SlotDiagnostic second = diagnostic.slots().get(LoadoutSlot.SECOND);
        if (diagnostic.secondSpecUnlocked() && second != null
                && second.status() == LoadoutStatus.EMPTY) return Optional.of(LoadoutSlot.SECOND);
        return Optional.empty();
    }

    private Optional<LoadoutSlot> slotContaining(final ProfileDiagnostic diagnostic,
                                                 final SpecializationType specialization) {
        if (specialization == null) return Optional.empty();
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            final ProfileDiagnostic.SlotDiagnostic value = diagnostic.slots().get(slot);
            if (value != null && value.specializationId()
                    .filter(specialization.getId()::equalsIgnoreCase).isPresent()) return Optional.of(slot);
        }
        return Optional.empty();
    }

    private Optional<LoadoutSlot> slotContaining(final ClassSpecSection profile,
                                                 final String specializationId) {
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            if (profile.loadout(slot).specializationId().equalsIgnoreCase(specializationId)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    private void scheduleWarriorProfileRefresh(final Player player) {
        if (player == null) return;
        player.getScheduler().run(plugin, task -> refreshWarriorProfile(player), null);
    }

    private void refreshWarriorProfile(final Player player) {
        final WarriorGameplayService runtime = warriorGameplayService;
        final CatalystItemFactory factory = warriorSoulbondFactory;
        if (runtime != null) runtime.reconcileProfile(player);
        if (factory == null || jobManager.getPrimaryJob(player) != JobType.WARRIOR) return;
        final ClassSpecSection profile = profileGateway().currentProfile(player.getUniqueId()).orElse(null);
        if (profile == null) return;
        String spec = "";
        int mastery = 0;
        Map<String, String> doctrines = Map.of();
        if (profile.activeSlot() != null) {
            final ClassLoadout active = profile.loadout(profile.activeSlot());
            spec = active.specializationId();
            mastery = active.mastery().rank();
            doctrines = active.doctrineChoices();
        }
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (factory.isPersonalCopyFor(stack, player.getUniqueId(), JobType.WARRIOR)) {
                factory.refreshPresentation(stack, player.getUniqueId(), JobType.WARRIOR,
                        spec, profile.classLevel(), mastery, doctrines);
            }
        }
    }

    private static boolean isWarriorSpec(final String specializationId) {
        return "berserker".equals(specializationId) || "guardian".equals(specializationId);
    }

    private static String doctrineTier(final int level) {
        return "level_" + level;
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
