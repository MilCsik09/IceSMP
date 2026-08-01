package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.GateSnapshot;
import hu.taliann.icesmp.classspec.application.GateState;
import hu.taliann.icesmp.classspec.application.ProfileDiagnostic;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Manager for class and profession specializations.
 * The primary class can specialize once it reaches the configured level
 * (the secondary class never specializes, per design). Specialization spell
 * unlocks live under 'specializations.&lt;specId&gt;.spell-unlocks' in config.yml
 * and are applied on selection and on every class XP change via the JobManager hook.
 */
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
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.jobManager = jobManager;
        this.professionManager = professionManager;
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.questManager = questManager;
        this.classSpecKey = new NamespacedKey(plugin, "class_spec");
        this.professionSpecKey = new NamespacedKey(plugin, "profession_spec");
        this.memorySpecUnlockKey = new NamespacedKey(plugin, "memory_spec_unlock");
    }

    /** Setter injection keeps the legacy constructor graph acyclic. */
    public void setProfileGateway(final ClassSpecProfileGateway profileGateway) {
        this.profileGateway = Objects.requireNonNull(profileGateway, "profileGateway");
    }

    public boolean profileV2Enabled() {
        final ClassSpecProfileGateway gateway = profileGateway;
        return gateway != null && gateway.enabled();
    }

    public ClassSpecProfileGateway profileGateway() {
        return profileGateway;
    }

    /** K8: a játékos "visszaemlékezett" — a spec-választás szint-kapuja feloldva (PDC-flag). */
    public boolean hasMemorySpecUnlock(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(
                memorySpecUnlockKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /** K8: Emlékszilánk-beváltás — a szint-kapu feloldása (a játékos saját szálán hívandó). */
    public void grantMemorySpecUnlock(final Player player) {
        player.getPersistentDataContainer().set(memorySpecUnlockKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
    }

    public int getRequiredClassLevel() {
        return Math.max(1, configManager.getInt("classes.specialization.required-level", 25));
    }

    public int getRequiredProfessionLevel() {
        return Math.max(1, configManager.getInt("professions.specialization.required-level", 25));
    }

    public SpecializationType getClassSpecialization(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway != null && gateway.enabled()) {
            return gateway.activeSpecId(player.getUniqueId())
                    .map(SpecializationType::fromId).orElse(null);
        }
        return getLegacyClassSpecialization(player);
    }

    /** Read-only migration adapter; gameplay code must use {@link #getClassSpecialization(Player)}. */
    public SpecializationType getLegacyClassSpecialization(final Player player) {
        final String rawSpec = player.getPersistentDataContainer().get(classSpecKey, PersistentDataType.STRING);
        return SpecializationType.fromId(rawSpec);
    }

    /** Publishes only a rollback mirror after the Profile v2 commit is durable. */
    public void mirrorActiveClassSpecializationV2(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.enabled()) {
            return;
        }
        final Optional<String> active = gateway.activeSpecId(player.getUniqueId());
        if (active.isPresent()) {
            player.getPersistentDataContainer().set(
                    classSpecKey, PersistentDataType.STRING, active.orElseThrow());
        } else {
            player.getPersistentDataContainer().remove(classSpecKey);
        }
    }

    /** Runtime-adapter mirror that uses the committed snapshot while login activation is gated. */
    public void mirrorActiveClassSpecializationV2(
            final Player player,
            final hu.taliann.icesmp.classspec.domain.ClassProfile durable) {
        java.util.Objects.requireNonNull(durable, "durable");
        final String active = durable.activeSlot() == null ? ""
                : durable.loadout(durable.activeSlot()).specializationId();
        if (active.isEmpty()) {
            player.getPersistentDataContainer().remove(classSpecKey);
        } else {
            player.getPersistentDataContainer().set(classSpecKey, PersistentDataType.STRING, active);
        }
    }

    public ProfessionSpecializationType getProfessionSpecialization(final Player player) {
        final String rawSpec = player.getPersistentDataContainer().get(professionSpecKey, PersistentDataType.STRING);
        return ProfessionSpecializationType.fromId(rawSpec);
    }

    /**
     * Checks whether a class specialization can be selected by the player:
     * matching primary class at the required level, no specialization yet,
     * and the spec's faction/sinner requirements satisfied
     * (Necromancer: Dark faction + permanent sinner mark).
     *
     * @param player the player
     * @param specialization the desired specialization
     * @return true if selectable
     */
    public boolean canSelectClassSpecialization(final Player player, final SpecializationType specialization) {
        if (specialization == null) {
            return false;
        }
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway != null && gateway.enabled()) {
            if (!gateway.isSessionReady(player.getUniqueId())) {
                return false;
            }
            final ProfileDiagnostic diagnostic = gateway.diagnostic(player.getUniqueId());
            final ProfileDiagnostic.SlotDiagnostic first = diagnostic.slots().get(LoadoutSlot.FIRST);
            if (first == null || first.status() != LoadoutStatus.EMPTY) {
                return false;
            }
        } else if (getLegacyClassSpecialization(player) != null) {
            return false;
        }

        // Emlékszilánk: a "visszaemlékezett" játékosnál a szint-kapu elesik (a kaszt-egyezés
        // és a többi kapu — frakció/bűnös/quest — továbbra is kötelező).
        if (jobManager.getPrimaryJob(player) != specialization.getParentJob()) {
            return false;
        }
        if (jobManager.getPrimaryLevel(player) < getRequiredClassLevel() && !hasMemorySpecUnlock(player)) {
            return false;
        }

        final FactionType requiredFaction = specialization.getRequiredFaction();
        if (requiredFaction != null && factionManager.getFaction(player.getUniqueId()) != requiredFaction) {
            return false;
        }

        if (specialization.requiresSinner() && !sinManager.isSinner(player)) {
            return false;
        }

        // Quest gate (e.g. the necromancer initiation ritual in the ruined city).
        final String requiredQuest = configManager.getString(
                "specializations." + specialization.getId() + ".required-quest", "");
        return requiredQuest.isBlank() || questManager.hasCompleted(player, requiredQuest);
    }

    public boolean selectClassSpecialization(final Player player, final SpecializationType specialization) {
        if (profileV2Enabled()) {
            return false;
        }
        if (!canSelectClassSpecialization(player, specialization)) {
            return false;
        }

        player.getPersistentDataContainer().set(classSpecKey, PersistentDataType.STRING, specialization.getId());
        applyClassSpecializationUnlocks(player);
        AdvancementService.award(player, "first_spec");
        return true;
    }

    /** Durable Profile v2 selection; no PDC mutation is published before CAS commit. */
    public CompletionStage<Boolean> selectClassSpecializationV2(
            final Player player, final SpecializationType specialization) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.enabled() || !canSelectClassSpecialization(player, specialization)) {
            return CompletableFuture.completedFuture(false);
        }
        final GateSnapshot gates = captureGateSnapshot(player, specialization);
        return gateway.select(player.getUniqueId(), new ClassSpecProfileGateway.SelectRequest(
                        specialization.getId(), LoadoutSlot.FIRST, gates))
                .thenApply(ProfileMutationResult::committed);
    }

    public GateSnapshot captureGateSnapshot(final Player player,
                                            final SpecializationType specialization) {
        final boolean factionRequired = specialization.getRequiredFaction() != null;
        final boolean factionSatisfied = !factionRequired
                || factionManager.getFaction(player.getUniqueId()) == specialization.getRequiredFaction();
        final boolean sinnerRequired = specialization.requiresSinner();
        final boolean sinnerSatisfied = !sinnerRequired || sinManager.isSinner(player);
        final String requiredQuest = configManager.getString(
                "specializations." + specialization.getId() + ".required-quest", "").trim();
        final boolean questRequired = !requiredQuest.isEmpty();
        final boolean questSatisfied = !questRequired || questManager.hasCompleted(player, requiredQuest);
        final GateState state = GateState.ofRequirements(factionRequired, factionSatisfied,
                sinnerRequired, sinnerSatisfied, questRequired, questSatisfied);
        final Map<GateState.Gate, String> gateIds = new EnumMap<>(GateState.Gate.class);
        if (factionRequired) {
            gateIds.put(GateState.Gate.FACTION,
                    "faction:" + specialization.getRequiredFaction().name().toLowerCase(Locale.ROOT));
        }
        if (sinnerRequired) {
            gateIds.put(GateState.Gate.SINNER, "sinner:permanent");
        }
        if (questRequired) {
            gateIds.put(GateState.Gate.QUEST, "quest:" + requiredQuest.toLowerCase(Locale.ROOT));
        }
        return new GateSnapshot(state, gateIds);
    }

    public boolean canSelectProfessionSpecialization(final Player player, final ProfessionSpecializationType specialization) {
        if (specialization == null || getProfessionSpecialization(player) != null) {
            return false;
        }

        return professionManager.hasProfession(player, specialization.getParentProfession())
                && professionManager.getLevel(player, specialization.getParentProfession()) >= getRequiredProfessionLevel();
    }

    public boolean selectProfessionSpecialization(final Player player, final ProfessionSpecializationType specialization) {
        if (!canSelectProfessionSpecialization(player, specialization)) {
            return false;
        }

        player.getPersistentDataContainer().set(professionSpecKey, PersistentDataType.STRING, specialization.getId());
        return true;
    }

    /**
     * Admin operation: clears both the class and profession specialization of a player.
     *
     * @param player the player to reset
     */
    /**
     * A DARK-kapus spec (Nekromanta, Szentségtelen, Csontpap, Pestishozó, Demonológus…)
     * nem élhet tovább a Kitaszítottakon kívül — frakció-elhagyáskor hívandó.
     *
     * @return true, ha volt mit elengedni
     */
    public boolean resetDarkGatedSpecialization(final Player player) {
        if (profileV2Enabled()) {
            final SpecializationType current = getClassSpecialization(player);
            if (current == null || (current.getRequiredFaction() == null && !current.requiresSinner())) {
                return false;
            }
            reconcileDarkGates(player);
            return true;
        }
        final SpecializationType current = getClassSpecialization(player);
        if (current == null || (current.getRequiredFaction() != hu.taliann.icesmp.data.FactionType.DARK
                && !current.requiresSinner())) {
            return false;
        }
        resetClassSpecialization(player);
        return true;
    }

    public void resetSpecializations(final Player player) {
        if (profileV2Enabled()) {
            return;
        }
        resetClassSpecialization(player);
        resetProfessionSpecialization(player);
    }

    /**
     * Drops the class specialization AND takes back the spells it granted. Without the
     * revoke a player could stack every specialization's full spell set by choosing,
     * resetting and choosing again; a spell that the class level or a talent also granted
     * survives, because the revoke keys off the recorded grant source.
     */
    public void resetClassSpecialization(final Player player) {
        if (profileV2Enabled()) {
            return;
        }
        player.getPersistentDataContainer().remove(classSpecKey);
        // Csak a kaszt-spec ad spellt (specializations.<id>.spell-unlocks), és egyszerre
        // egy élhet — ezért MINDEN SPEC:* grant elvonható; a régi, már resetelt specek
        // ottmaradt bejegyzéseit is ez takarítja el (backfill utáni halmozás-tisztítás).
        jobManager.backfillSpellGrants(player);
        jobManager.revokeGrantsFrom(player, source -> source.startsWith(JobManager.SOURCE_SPEC_PREFIX));
    }

    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> resetClassProfileV2(
            final Player player, final boolean adminClassReset, final String operationId) {
        return resetClassProfileV2(player.getUniqueId(), adminClassReset, operationId);
    }

    /** UUID-only mutation entry used by off-thread economic transactions. */
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> resetClassProfileV2(
            final java.util.UUID playerId, final boolean adminClassReset, final String operationId) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.enabled()) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    ProfileDiagnostic.disabled(), "Profile v2 disabled"));
        }
        final ProfileDiagnostic diagnostic = gateway.diagnostic(playerId);
        final Optional<LoadoutSlot> slot = adminClassReset
                ? Optional.empty() : diagnostic.activeSlot().or(() -> Optional.of(LoadoutSlot.FIRST));
        return gateway.reset(playerId, new ClassSpecProfileGateway.ResetRequest(
                adminClassReset ? ClassSpecProfileGateway.ResetMode.ADMIN_CLASS
                        : ClassSpecProfileGateway.ResetMode.LOADOUT_RESPEC,
                slot, operationId));
    }

    /** Re-evaluates only DARK loadouts; gate events cannot clear admin/persistence seals. */
    public CompletionStage<ProfileMutationResult<ProfileDiagnostic>> reconcileDarkGates(
            final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.enabled()) {
            return CompletableFuture.completedFuture(ProfileMutationResult.noChange(
                    ProfileDiagnostic.disabled(), "legacy runtime"));
        }
        final ProfileDiagnostic diagnostic = gateway.diagnostic(player.getUniqueId());
        final Map<LoadoutSlot, GateSnapshot> snapshots = new EnumMap<>(LoadoutSlot.class);
        for (final Map.Entry<LoadoutSlot, ProfileDiagnostic.SlotDiagnostic> entry
                : diagnostic.slots().entrySet()) {
            final SpecializationType type = entry.getValue().specializationId()
                    .map(SpecializationType::fromId).orElse(null);
            if (type != null && (type.getRequiredFaction() != null || type.requiresSinner())) {
                snapshots.put(entry.getKey(), captureGateSnapshot(player, type));
            }
        }
        return gateway.reconcile(player.getUniqueId(),
                new ClassSpecProfileGateway.ReconcileRequest(snapshots));
    }

    public void resetProfessionSpecialization(final Player player) {
        player.getPersistentDataContainer().remove(professionSpecKey);
    }

    /**
     * Gets the respec price (paid in the player's own faction currency) for
     * dropping a chosen specialization via /spec respec.
     *
     * @return the configured respec cost
     */
    public double getRespecCost() {
        return Math.max(0.0D, configManager.getDouble("specializations.respec-cost", 100.0D));
    }

    /**
     * Unlocks every specialization spell whose required primary class level has
     * been reached. Wired into JobManager's XP change hook so admin XP commands
     * and kill XP both trigger it.
     *
     * @param player the player to check
     */
    public void applyClassSpecializationUnlocks(final Player player) {
        final SpecializationType specialization = getClassSpecialization(player);
        applyClassSpecializationUnlocks(player, specialization, jobManager.getPrimaryJob(player));
    }

    /** Runtime rebuild from the committed snapshot; does not bypass the public session-ready gate. */
    public void applyClassSpecializationUnlocksV2(
            final Player player,
            final hu.taliann.icesmp.classspec.domain.ClassProfile durable) {
        java.util.Objects.requireNonNull(durable, "durable");
        final SpecializationType specialization = durable.activeSlot() == null ? null
                : SpecializationType.fromId(durable.loadout(durable.activeSlot()).specializationId());
        applyClassSpecializationUnlocks(player, specialization,
                hu.taliann.icesmp.data.JobType.fromId(durable.primaryClassId()));
    }

    private void applyClassSpecializationUnlocks(final Player player,
                                                  final SpecializationType specialization,
                                                  final hu.taliann.icesmp.data.JobType primaryJob) {
        if (specialization == null || configManager.getConfiguration() == null) {
            return;
        }

        if (primaryJob != specialization.getParentJob()) {
            return;
        }

        final ConfigurationSection unlockSection = configManager.getConfiguration()
                .getConfigurationSection("specializations." + specialization.getId() + ".spell-unlocks");
        if (unlockSection == null) {
            return;
        }

        final int level = jobManager.getPrimaryLevel(player);
        for (final String spellId : unlockSection.getKeys(false)) {
            final int requiredLevel = unlockSection.getInt(spellId, Integer.MAX_VALUE);
            if (level < requiredLevel) {
                continue;
            }

            // A már feloldott spellre is RÁ KELL írni a spec forrását (az unlockSpell ilyenkor
            // csak a forrást rögzíti és false-t ad): enélkül a máshonnan — pl. questből —
            // korábban megkapott spellt a spec-reset nem tudta visszavenni, tehát a
            // specializációk spellkészlete tovább halmozódott.
            if (jobManager.unlockSpell(player, spellId,
                    JobManager.SOURCE_SPEC_PREFIX + specialization.getId())) {
                player.sendMessage(messageManager.getMessage(
                        "spec-spell-unlocked",
                        "&5Specializációs képesség feloldva: &e{spell} &7(szint {level})",
                        Map.of("spell", spellId.toLowerCase(Locale.ROOT), "level", String.valueOf(requiredLevel))
                ));
            }
        }
    }
}
