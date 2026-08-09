package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.GateSnapshot;
import hu.taliann.icesmp.classspec.application.GateState;
import hu.taliann.icesmp.classspec.application.ProfileDiagnostic;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Profile v2 is the sole authority for class specializations. */
public final class SpecializationManager {
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final JobManager jobManager;
    private final ProfessionManager professionManager;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final QuestManager questManager;
    private final NamespacedKey classSpecKey;
    private final NamespacedKey professionSpecKey;
    private final NamespacedKey memorySpecUnlockKey;
    private volatile ClassSpecProfileGateway profileGateway;

    public SpecializationManager(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager, final JobManager jobManager,
                                 final ProfessionManager professionManager, final FactionManager factionManager,
                                 final SinManager sinManager, final QuestManager questManager) {
        this.configManager = Objects.requireNonNull(configManager);
        this.messageManager = Objects.requireNonNull(messageManager);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.professionManager = Objects.requireNonNull(professionManager);
        this.factionManager = Objects.requireNonNull(factionManager);
        this.sinManager = Objects.requireNonNull(sinManager);
        this.questManager = Objects.requireNonNull(questManager);
        this.classSpecKey = new NamespacedKey(plugin, "class_spec");
        this.professionSpecKey = new NamespacedKey(plugin, "profession_spec");
        this.memorySpecUnlockKey = new NamespacedKey(plugin, "memory_spec_unlock");
    }

    public void setProfileGateway(final ClassSpecProfileGateway gateway) {
        profileGateway = Objects.requireNonNull(gateway, "profileGateway");
    }

    public ClassSpecProfileGateway profileGateway() {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null) throw new IllegalStateException("Profile v2 gateway is not initialized");
        return gateway;
    }

    public boolean hasMemorySpecUnlock(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(memorySpecUnlockKey,
                PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    public void grantMemorySpecUnlock(final Player player) {
        player.getPersistentDataContainer().set(memorySpecUnlockKey, PersistentDataType.BYTE, (byte) 1);
    }

    public int getRequiredClassLevel() {
        return Math.max(1, configManager.getInt("classes.specialization.required-level", 25));
    }

    public int getRequiredProfessionLevel() {
        return Math.max(1, configManager.getInt("professions.specialization.required-level", 25));
    }

    public SpecializationType getClassSpecialization(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return null;
        return gateway.activeSpecId(player.getUniqueId()).map(SpecializationType::fromId).orElse(null);
    }

    /** Write-only compatibility mirror; never read as class/spec authority. */
    public void mirrorActiveClassSpecializationV2(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway();
        final Optional<String> active = gateway.activeSpecId(player.getUniqueId());
        if (active.isPresent()) player.getPersistentDataContainer().set(classSpecKey,
                PersistentDataType.STRING, active.orElseThrow());
        else player.getPersistentDataContainer().remove(classSpecKey);
    }

    public void mirrorActiveClassSpecializationV2(final Player player, final ClassProfile durable) {
        Objects.requireNonNull(durable, "durable");
        final String active = durable.activeSlot() == null ? ""
                : durable.loadout(durable.activeSlot()).specializationId();
        if (active.isEmpty()) player.getPersistentDataContainer().remove(classSpecKey);
        else player.getPersistentDataContainer().set(classSpecKey, PersistentDataType.STRING, active);
    }

    public ProfessionSpecializationType getProfessionSpecialization(final Player player) {
        return ProfessionSpecializationType.fromId(player.getPersistentDataContainer()
                .get(professionSpecKey, PersistentDataType.STRING));
    }

    public boolean canSelectClassSpecialization(final Player player,
                                                 final SpecializationType specialization) {
        if (specialization == null) return false;
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return false;
        final ProfileDiagnostic diagnostic = gateway.diagnostic(player.getUniqueId());
        final ProfileDiagnostic.SlotDiagnostic first = diagnostic.slots().get(LoadoutSlot.FIRST);
        if (first == null || first.status() != LoadoutStatus.EMPTY) return false;
        if (jobManager.getPrimaryJob(player) != specialization.getParentJob()) return false;
        if (jobManager.getPrimaryLevel(player) < getRequiredClassLevel() && !hasMemorySpecUnlock(player)) return false;
        final GateSnapshot gates = captureGateSnapshot(player, specialization);
        return gates.missingReason() == null;
    }

    /** Legacy direct PDC selection is unsupported. */
    public boolean selectClassSpecialization(final Player player, final SpecializationType specialization) {
        return false;
    }

    public CompletionStage<Boolean> selectClassSpecializationV2(final Player player,
                                                                 final SpecializationType specialization) {
        if (!canSelectClassSpecialization(player, specialization)) return CompletableFuture.completedFuture(false);
        return profileGateway().select(player.getUniqueId(), new ClassSpecProfileGateway.SelectRequest(
                        specialization.getId(), LoadoutSlot.FIRST,
                        captureGateSnapshot(player, specialization)))
                .thenApply(result -> result.committed()
                        || result.status() == ProfileMutationResult.Status.NO_CHANGE);
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
        if (factionRequired) ids.put(GateState.Gate.FACTION,
                "faction:" + specialization.getRequiredFaction().name().toLowerCase(Locale.ROOT));
        if (sinnerRequired) ids.put(GateState.Gate.SINNER, "sinner:permanent");
        if (questRequired) ids.put(GateState.Gate.QUEST,
                "quest:" + requiredQuest.toLowerCase(Locale.ROOT));
        return new GateSnapshot(state, ids);
    }

    public boolean canSelectProfessionSpecialization(final Player player,
                                                       final ProfessionSpecializationType specialization) {
        return specialization != null && getProfessionSpecialization(player) == null
                && professionManager.hasProfession(player, specialization.getParentProfession())
                && professionManager.getLevel(player, specialization.getParentProfession())
                >= getRequiredProfessionLevel();
    }

    public boolean selectProfessionSpecialization(final Player player,
                                                   final ProfessionSpecializationType specialization) {
        if (!canSelectProfessionSpecialization(player, specialization)) return false;
        player.getPersistentDataContainer().set(professionSpecKey, PersistentDataType.STRING,
                specialization.getId());
        return true;
    }

    public boolean resetDarkGatedSpecialization(final Player player) {
        final SpecializationType current = getClassSpecialization(player);
        if (current == null || (current.getRequiredFaction() == null && !current.requiresSinner()
                && configManager.getString("specializations." + current.getId() + ".required-quest", "").isBlank())) {
            return false;
        }
        reconcileDarkGates(player);
        return true;
    }

    /** Class reset must go through resetClassProfileV2; only the profession side is synchronous. */
    public void resetSpecializations(final Player player) { resetProfessionSpecialization(player); }
    /** Direct class-spec PDC deletion is intentionally unsupported. */
    public void resetClassSpecialization(final Player player) { }

    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> resetClassProfileV2(
            final Player player, final boolean adminClassReset, final String operationId) {
        return resetClassProfileV2(player.getUniqueId(), adminClassReset, operationId);
    }

    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> resetClassProfileV2(
            final java.util.UUID playerId, final boolean adminClassReset, final String operationId) {
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
        final var playerId = player.getUniqueId();
        // Login activation must reconcile DARK gates before the session is marked READY.
        // Requiring isSessionReady() here creates a circular dependency: the bridge cannot
        // mark READY until this reconciliation succeeds. A current generation plus a loaded
        // profile is sufficient; gateway.reconcile() still enforces session fencing and the
        // persistence/review/quarantine fail-closed policy.
        if (gateway.currentSessionToken(playerId).isEmpty()
                || gateway.currentProfile(playerId).isEmpty()) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    gateway.diagnostic(playerId), "Profile v2 session/profile is not available"));
        }
        final ProfileDiagnostic diagnostic = gateway.diagnostic(playerId);
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
        return gateway.reconcile(playerId,
                new ClassSpecProfileGateway.ReconcileRequest(snapshots));
    }

    public void resetProfessionSpecialization(final Player player) {
        player.getPersistentDataContainer().remove(professionSpecKey);
    }

    public double getRespecCost() {
        final double configured = configManager.getDouble("specializations.respec-cost", 100.0D);
        if (!Double.isFinite(configured) || configured < 0.0D) {
            throw new IllegalStateException("specializations.respec-cost must be finite and non-negative");
        }
        return configured;
    }

    public void applyClassSpecializationUnlocks(final Player player) {
        applyClassSpecializationUnlocks(player, getClassSpecialization(player), jobManager.getPrimaryJob(player),
                jobManager.getPrimaryLevel(player));
    }

    public void applyClassSpecializationUnlocksV2(final Player player, final ClassProfile durable) {
        Objects.requireNonNull(durable, "durable");
        final SpecializationType specialization = durable.activeSlot() == null ? null
                : SpecializationType.fromId(durable.loadout(durable.activeSlot()).specializationId());
        applyClassSpecializationUnlocks(player, specialization, JobType.fromId(durable.primaryClassId()),
                durable.classLevel());
    }

    private void applyClassSpecializationUnlocks(final Player player,
                                                  final SpecializationType specialization,
                                                  final JobType primaryJob,
                                                  final int classLevel) {
        if (specialization == null || primaryJob != specialization.getParentJob()
                || configManager.getConfiguration() == null) return;
        final ConfigurationSection unlocks = configManager.getConfiguration()
                .getConfigurationSection("specializations." + specialization.getId() + ".spell-unlocks");
        if (unlocks == null) return;
        for (final String spellId : unlocks.getKeys(false)) {
            final int required = unlocks.getInt(spellId, Integer.MAX_VALUE);
            if (classLevel >= required && jobManager.unlockSpell(player, spellId,
                    JobManager.SOURCE_SPEC_PREFIX + specialization.getId())) {
                player.sendMessage(messageManager.getMessage("spec-spell-unlocked",
                        "&5Specializációs képesség feloldva: &e{spell} &7(szint {level})",
                        Map.of("spell", spellId.toLowerCase(Locale.ROOT),
                                "level", String.valueOf(required))));
            }
        }
    }
}
