package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.NumericGuards;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Durable companions for every pet-owning specialization. Type, level, XP, name,
 * stance, equipment and roster live in Profile v2; live entity identities and
 * combat controls are rebuildable runtime projections. Owner and pet kills use
 * the same progression reward gate.
 */
public final class PetManager implements hu.taliann.icesmp.session.PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MinionManager minionManager;
    private final SpecializationManager specializationManager;
    private final MessageManager messageManager;
    private final NamespacedKey healthModKey;
    private final NamespacedKey damageModKey;
    private final NamespacedKey armorDefenseModKey;
    private final NamespacedKey armorHealthModKey;
    /** Élő társsal rendelkező gazdák — a vezérlő tick CSAK rájuk hop-ol (üresjárat-fék). */
    private final java.util.Set<UUID> activeOwners = ConcurrentHashMap.newKeySet();
    /** owner UUID → current combat target UUID (assist / defend). */
    private final Map<UUID, UUID> combatTargets = new ConcurrentHashMap<>();
    /** pet UUID → epoch ms when the pet may attack again. */
    private final Map<UUID, Long> attackReady = new ConcurrentHashMap<>();
    /** Profile v2 runtime-only owner -> live pet identity; never written to PDC/profile. */
    private final Map<UUID, UUID> activePetEntities = new ConcurrentHashMap<>();
    /** Runtime-only owner -> logical companion identity represented by the live entity. */
    private final Map<UUID, UUID> activePetCompanionIds = new ConcurrentHashMap<>();
    /** Logical companion -> local fail-closed cooldown while the death mutation is committing. */
    private final Map<UUID, Long> pendingDeathCooldowns = new ConcurrentHashMap<>();
    private volatile ClassSpecProfileGateway profileGateway;
    private volatile java.util.function.Consumer<UUID> petDeathHook = ignored -> { };

    public PetManager(final JavaPlugin plugin, final ConfigManager configManager, final MinionManager minionManager,
                      final SpecializationManager specializationManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.minionManager = minionManager;
        this.specializationManager = specializationManager;
        this.messageManager = messageManager;
        this.healthModKey = new NamespacedKey(plugin, "pet_health_mod");
        this.damageModKey = new NamespacedKey(plugin, "pet_damage_mod");
        this.armorDefenseModKey = new NamespacedKey(plugin, "pet_armor_defense_mod");
        this.armorHealthModKey = new NamespacedKey(plugin, "pet_armor_health_mod");
    }

    public void setProfileGateway(
            final ClassSpecProfileGateway profileGateway) {
        this.profileGateway = java.util.Objects.requireNonNull(profileGateway, "profileGateway");
    }


    public boolean isBeastMaster(final Player player) {
        return specializationManager.getClassSpecialization(player) == SpecializationType.BEAST_MASTER;
    }

    public boolean isNecromancer(final Player player) {
        return specializationManager.getClassSpecialization(player) == SpecializationType.NECROMANCER;
    }

    /** Setter-injektált (a JobManager a PetManager után is elérhető a core-ból). */
    private volatile hu.taliann.icesmp.managers.JobManager jobManager;

    private volatile hu.taliann.icesmp.managers.TalentManager talentManagerRef;

    public void setTalentManager(final hu.taliann.icesmp.managers.TalentManager talentManager) {
        this.talentManagerRef = talentManager;
    }

    public void setJobManager(final hu.taliann.icesmp.managers.JobManager jobManager) {
        this.jobManager = jobManager;
    }

    /** Szentségtelen DK: állandó ghúl-társ (a WoW-hű permanens pet). */
    public boolean isUnholy(final Player player) {
        return specializationManager.getClassSpecialization(player) == SpecializationType.UNHOLY;
    }

    /** Boszorkánymester (kaszt-szintű): állandó démon-familiáris. */
    public boolean isWarlock(final Player player) {
        return specializationManager.getClassSpecialization(player) == SpecializationType.DEMONOLOGIST;
    }

    /** A Sötét Paktum-tekercset használó szerepek közös kapuja. */
    public boolean isDarkCapturer(final Player player) {
        return isNecromancer(player) || isUnholy(player) || isWarlock(player);
    }

    public boolean canOwnPet(final Player player) {
        return isBeastMaster(player) || isDarkCapturer(player);
    }

    public int getLevel(final Player player) {
        return activeCompanion(player).map(CompanionProfile::level).orElse(1);
    }

    public int getXp(final Player player) {
        return activeCompanion(player).map(companion -> (int) Math.min(Integer.MAX_VALUE, companion.experience())).orElse(0);
    }

    public String getName(final Player player) {
        return activeCompanion(player).map(CompanionProfile::name).filter(name -> !name.isBlank()).orElse("Társ");
    }

    /** Stable roster in deterministic order for GUI and command selection. */
    public List<CompanionProfile> companionRoster(final Player player) {
        return currentLoadout(player).stream()
                .flatMap(loadout -> loadout.companionRoster().values().stream())
                .sorted(java.util.Comparator.comparing(companion -> companion.companionId().toString()))
                .toList();
    }

    public Optional<UUID> selectedCompanionId(final Player player) {
        return activeCompanion(player).map(CompanionProfile::companionId);
    }

    /** One-based stable index or full logical UUID. */
    public Optional<CompanionProfile> resolveCompanion(final Player player, final String selector) {
        if (selector == null || selector.isBlank()) {
            return Optional.empty();
        }
        final List<CompanionProfile> roster = companionRoster(player);
        try {
            final int index = Integer.parseInt(selector.trim());
            return index >= 1 && index <= roster.size() ? Optional.of(roster.get(index - 1)) : Optional.empty();
        } catch (final NumberFormatException ignored) {
            try {
                final UUID id = UUID.fromString(selector.trim());
                return roster.stream().filter(companion -> companion.companionId().equals(id)).findFirst();
            } catch (final IllegalArgumentException invalidUuid) {
                return Optional.empty();
            }
        }
    }

    /** Whether the player may capture the target with their spec's capture item. */
    public boolean isValidTarget(final Player player, final Entity target) {
        if (!(target instanceof Mob) || target instanceof Player || minionManager.isMinion(target)) {
            return false;
        }
        // Más játékos vanília úton szelídített állata nem lopható el befogással.
        if (target instanceof org.bukkit.entity.Tameable tameable && tameable.isTamed()
                && !(tameable.getOwner() instanceof Player owner && owner.getUniqueId().equals(player.getUniqueId()))) {
            return false;
        }
        // Erő-tiltólista: a meta-törő "legjobb pet" választások (Warden, Ravager,
        // Vasgólem, Elder Guardian, Wither) egyik szerepnek sem foghatók be.
        for (final String banned : configManager.getStringList("pets.capture.blocklist")) {
            if (target.getType().name().equalsIgnoreCase(banned)) {
                return false;
            }
        }
        if (isBeastMaster(player)) {
            return !(target instanceof Monster); // any non-hostile animal/mob
        }
        if (isNecromancer(player)) {
            return target instanceof Monster; // any hostile mob / undead
        }
        // A Szentségtelen és a Boszorkánymester NEM befog, hanem IDÉZ (rituálé-kellékkel).
        return false;
    }

    public boolean isSummonedPet(final Player player) {
        return activeCompanion(player)
                .map(companion -> Boolean.parseBoolean(companion.persistentState().getOrDefault("ritual_summoned", "false")))
                .orElse(false);
    }

    /**
     * Rituálé-idézés (Szentségtelen ghúl / Boszorkánymester démon): csak éjjel, a
     * forma a pet-szinttel fejlődik — a magasabb forma új rituálét (új kelléket) kér.
     *
     * @return null siker, különben üzenet-kulcs
     */
    /** Legacy synchronous mutations are intentionally disabled; Profile v2 async methods are authoritative. */
    public String ritualSummon(final Player player) { return "pet-persistence-required"; }
    public String capture(final Player player, final Entity target) { return "pet-persistence-required"; }
    public String summon(final Player player) { return "pet-persistence-required"; }
    public boolean dismiss(final Player player) { return false; }

    private static final String DEMON_ROSTER = "demonologist.roster";
    private static final String NECRO_COURT = "necromancer.court";
    private static final String UNHOLY_GHOUL = "unholy.ghoul";
    private static final String GHOUL_MUTATION_STAGE = "ghoul_mutation_stage";

    public record PetMutationResult(boolean committed, String error) {
        public PetMutationResult { error = error == null ? "" : error; }
        static PetMutationResult rejected(final String error) { return new PetMutationResult(false, error); }
        static PetMutationResult applied() { return new PetMutationResult(true, ""); }
        static PetMutationResult appliedWithRuntimeFailure(final String error) { return new PetMutationResult(true, error); }
    }

    public void runOnPlayer(final Player player, final Runnable action) {
        if (player == null || action == null) return;
        player.getScheduler().run(plugin, task -> action.run(), null);
    }

    public boolean hasUnholyGhoul(final Player player) {
        return activeCompanion(player).filter(companion ->
                UNHOLY_GHOUL.equals(companion.namespace())).isPresent();
    }

    public int unholyGhoulMutationStage(final Player player) {
        return activeCompanion(player)
                .filter(companion -> UNHOLY_GHOUL.equals(companion.namespace()))
                .map(companion -> parseNonNegativeInt(
                        companion.persistentState().get(GHOUL_MUTATION_STAGE)))
                .orElse(0);
    }

    public CompletionStage<PetMutationResult> advanceUnholyGhoulMutationV2(
            final Player player, final int maximum, final String operationId) {
        if (player == null || !isUnholy(player)) {
            return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-wrong-spec"));
        }
        final ActiveCompanionRef active = activeCompanionRef(player)
                .filter(ref -> UNHOLY_GHOUL.equals(ref.companion().namespace()))
                .orElse(null);
        if (active == null) {
            return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-companion-absent"));
        }
        final int boundedMaximum = Math.max(1, maximum);
        final int current = Math.min(boundedMaximum, parseNonNegativeInt(
                active.companion().persistentState().get(GHOUL_MUTATION_STAGE)));
        if (current >= boundedMaximum) {
            return CompletableFuture.completedFuture(PetMutationResult.applied());
        }
        final Map<String, String> state = new java.util.LinkedHashMap<>(
                active.companion().persistentState());
        state.put(GHOUL_MUTATION_STAGE, Integer.toString(current + 1));
        final UUID sessionToken = currentSessionToken(player).orElse(null);
        if (sessionToken == null) {
            return CompletableFuture.completedFuture(PetMutationResult.rejected(
                    "pet-session-unavailable"));
        }
        return mutateCompanion(player, active,
                ClassSpecProfileGateway.CompanionMutationRequest.Kind.STATE,
                "", 0, 0L, 0L, List.of(), state, operationId)
                .thenCompose(result -> {
                    if (!result.durableMutationApplied()) {
                        return CompletableFuture.completedFuture(PetMutationResult.rejected(
                                result.detail().isBlank() ? "pet-persistence-failed" : result.detail()));
                    }
                    final CompletableFuture<PetMutationResult> completion = new CompletableFuture<>();
                    runOnCurrentPlayer(player, sessionToken, () -> {
                        if (active.companion().companionId().equals(
                                selectedCompanionId(player).orElse(null))) {
                            final PetRuntimeSnapshot snapshot = runtimeSnapshot(player);
                            scheduleActivePet(player, pet -> applyBuffs(
                                    pet, snapshot.buffLevel(), false));
                        }
                        completion.complete(PetMutationResult.applied());
                    }, () -> completion.complete(PetMutationResult.appliedWithRuntimeFailure(
                            "pet-stale-session")));
                    return completion;
                });
    }

    private Optional<UUID> currentSessionToken(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        return gateway == null ? Optional.empty() : gateway.currentSessionToken(player.getUniqueId());
    }

    private boolean isCurrentSession(final Player player, final UUID sessionToken) {
        return player != null && isCurrentSession(player.getUniqueId(), sessionToken);
    }

    private boolean isCurrentSession(final UUID playerId, final UUID sessionToken) {
        final ClassSpecProfileGateway gateway = profileGateway;
        return gateway != null && sessionToken != null
                && gateway.isCurrentSession(playerId, sessionToken);
    }

    private void runOnCurrentPlayer(final Player player, final UUID sessionToken,
                                    final Runnable action, final Runnable retired) {
        player.getScheduler().run(plugin, task -> {
            if (!isCurrentSession(player, sessionToken)) {
                if (retired != null) retired.run();
                return;
            }
            action.run();
        }, retired);
    }

    public CompletionStage<PetMutationResult> captureV2(final Player player, final Entity target) {
        if (!canOwnPet(player)) return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-not-allowed"));
        if (!isValidTarget(player, target) || !(target instanceof Mob mob)) return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-invalid-target"));
        final Optional<LoadoutSlot> slot = activeSlot(player);
        final String spec = activeSpec(player);
        final String namespace = ClassSpecCatalog.companionNamespace(spec);
        if (slot.isEmpty() || namespace == null) return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-wrong-spec"));
        int stableCapacity = 0;
        // A Vadmester Istállója szándékosan szűk: legfeljebb ennyi befogott társ férhet el.
        if ("beast_master.stable".equals(namespace)) {
            final int capacity = Math.max(1, configManager.getInt("pets.stable.maximum", 3));
            final int stabled = currentLoadout(player)
                    .map(loadout -> loadout.companionRoster().size()).orElse(0);
            if (stabled >= capacity) {
                return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-stable-full"));
            }
            stableCapacity = capacity;
        }
        final UUID sessionToken = currentSessionToken(player).orElse(null);
        if (sessionToken == null) return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-session-unavailable"));
        final UUID logicalId = UUID.randomUUID();
        final CompanionProfile companion = new CompanionProfile(logicalId, namespace, mob.getType().name(), "Társ",
                1, 0L, "", MinionManager.Stance.ACTIVE.name(), List.of(), 0L, Map.of("ritual_summoned", "false"));
        final String operationId = "pet-capture:" + logicalId;
        return gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                        ClassSpecProfileGateway.CompanionMutationRequest.Kind.ADD, logicalId, companion, "", 0, 0L, 0L, List.of(), Map.of(), stableCapacity, operationId))
                .thenCompose(result -> afterDurableCapture(player, sessionToken, result, logicalId, mob));
    }

    public CompletionStage<PetMutationResult> ritualSummonV2(final Player player) {
        final boolean unholy = isUnholy(player);
        final boolean demonologist = !unholy && isWarlock(player);
        if (!unholy && !demonologist) return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-wrong-spec"));
        final long time = player.getWorld().getTime();
        if (configManager.getBoolean("pets.summon.night-only", true) && (time < 13000L || time > 23000L))
            return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-ritual-night-only"));
        final Optional<LoadoutSlot> slot = activeSlot(player);
        final String spec = activeSpec(player);
        final String namespace = ClassSpecCatalog.companionNamespace(spec);
        if (slot.isEmpty() || namespace == null) return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-wrong-spec"));
        final int level = Math.max(1, activeCompanion(player).map(CompanionProfile::level).orElse(1));
        final EntityType form; final String formName;
        if (unholy) {
            if (level >= configManager.getInt("pets.summon.tier3-level", 25)) { form=EntityType.ZOGLIN; formName="Förtelem"; }
            else if (level >= configManager.getInt("pets.summon.tier2-level", 15)) { form=EntityType.WITHER_SKELETON; formName="Csontszolga"; }
            else { form=EntityType.HUSK; formName="Ghúl"; }
        } else {
            form=demonForm(level); formName=demonFormName(form);
        }
        // A paktum plafonja minden úton tart: a rituálé sem kerülheti meg a durable névsor kapacitását.
        final int rosterCapacity = unholy ? 1
                : Math.max(1, configManager.getInt("classes.warlock.demonologist.roster-capacity", 3));
        if (!ClassSpecCatalog.admitsCompanion(currentLoadout(player).orElse(null), namespace, rosterCapacity)) {
            return CompletableFuture.completedFuture(PetMutationResult.rejected(
                    unholy ? "pet-companion-exists" : "pet-roster-full"));
        }
        final UUID sessionToken=currentSessionToken(player).orElse(null);
        if(sessionToken==null)return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-session-unavailable"));
        final UUID logicalId=UUID.randomUUID();
        final CompanionProfile companion=new CompanionProfile(logicalId,namespace,form.name(),formName,1,0L,"",
                MinionManager.Stance.ACTIVE.name(),List.of(),0L,Map.of("ritual_summoned","true"));
        final String operationId="pet-ritual:"+logicalId;
        return gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                        ClassSpecProfileGateway.CompanionMutationRequest.Kind.ADD, logicalId, companion, "", 0,0L,0L,List.of(),Map.of(),rosterCapacity,operationId))
                .thenCompose(result -> afterDurableSpawnMutation(
                        player, sessionToken, result, logicalId, form));
    }

    /**
     * Demonológus paktum-kötés. A paktum EGYETLEN igazságforrása a durable demonologist.roster:
     * a kapacitást a tartós névsor dönti el, a névsorbejegyzés előbb commitol, és a démon csak
     * utána — a játékos saját régió-szálán, élő session mellett — ölt testet.
     */
    public CompletionStage<PetMutationResult> bindDemonV2(final Player player, final String kindId,
                                                          final int capacity) {
        if (!isWarlock(player)) return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-wrong-spec"));
        final String kind = ClassSpecCatalog.normalize(kindId);
        final Optional<LoadoutSlot> slot = activeSlot(player);
        final String namespace = ClassSpecCatalog.companionNamespace(activeSpec(player));
        if (kind.isEmpty() || slot.isEmpty() || !DEMON_ROSTER.equals(namespace))
            return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-wrong-spec"));
        if (!ClassSpecCatalog.admitsCompanion(currentLoadout(player).orElse(null), namespace, capacity))
            return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-roster-full"));
        final UUID sessionToken = currentSessionToken(player).orElse(null);
        if (sessionToken == null) return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-session-unavailable"));
        final EntityType form = demonForm(Math.max(1, activeCompanion(player).map(CompanionProfile::level).orElse(1)));
        final UUID logicalId = UUID.randomUUID();
        final CompanionProfile companion = new CompanionProfile(logicalId, namespace, form.name(),
                demonFormName(form), 1, 0L, "", MinionManager.Stance.ACTIVE.name(), List.of(), 0L,
                Map.of("ritual_summoned", "true", CompanionProfile.KIND_KEY, kind));
        return gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                        ClassSpecProfileGateway.CompanionMutationRequest.Kind.ADD, logicalId, companion,
                        "", 0, 0L, 0L, List.of(), Map.of(), Math.max(1, capacity),
                        "warlock-bind:" + logicalId))
                .thenCompose(result -> afterDurableSpawnMutation(
                        player, sessionToken, result, logicalId, form));
    }

    /**
     * Durable-first paktum-bontás: minden kötött démon előbb kikerül a tartós névsorból, és a
     * megtestesült démon csak azután tűnik el. Bukott commit után a világ nem tarthat olyan démont,
     * amit a profil már elengedett.
     */
    public CompletionStage<Integer> releaseDemonRosterV2(final Player player) {
        return releaseCompanionRosterV2(player, DEMON_ROSTER);
    }

    /** Durable-first Holtak Udvara betakarítás — ugyanaz a szabály, csak másik névtér. */
    public CompletionStage<Integer> releaseCourtV2(final Player player) {
        return releaseCompanionRosterV2(player, NECRO_COURT);
    }

    private CompletionStage<Integer> releaseCompanionRosterV2(final Player player,
                                                              final String namespace) {
        final List<CompanionProfile> bound = companionRoster(player, namespace);
        final Optional<LoadoutSlot> slot = activeSlot(player);
        final UUID sessionToken = currentSessionToken(player).orElse(null);
        if (bound.isEmpty() || slot.isEmpty() || sessionToken == null)
            return CompletableFuture.completedFuture(0);
        CompletionStage<Integer> released = CompletableFuture.completedFuture(0);
        for (final CompanionProfile companion : bound) {
            final UUID companionId = companion.companionId();
            released = released.thenCompose(count -> gateway().mutateCompanion(player.getUniqueId(),
                            companionRequest(slot.orElseThrow(),
                                    ClassSpecProfileGateway.CompanionMutationRequest.Kind.REMOVE,
                                    companionId, null, "", 0, 0L, 0L, List.of(), Map.of(),
                                    namespace + "-release:" + companionId))
                    .thenApply(result -> result.durableMutationApplied() ? count + 1 : count));
        }
        return released.thenCompose(count -> {
            if (count <= 0) return CompletableFuture.completedFuture(0);
            final CompletableFuture<Integer> completion = new CompletableFuture<>();
            runOnCurrentPlayer(player, sessionToken, () -> {
                final UUID liveCompanionId = activePetCompanionIds.get(player.getUniqueId());
                if (liveCompanionId != null && companionById(player, liveCompanionId).isEmpty()) {
                    removeActive(player, liveCompanionId);
                }
                completion.complete(count);
            }, () -> completion.complete(count));
            return completion;
        });
    }

    /**
     * Read-only projekció a durable paktumról. A névsor kizárólag a Profile v2 loadoutban él; egy
     * lezárt (SEALED) vagy más specializációjú loadout üres projekciót ad, a tartós adat érintetlen.
     */
    public List<CompanionProfile> demonRoster(final Player player) {
        return companionRoster(player, DEMON_ROSTER);
    }

    /** Read-only projection of the durable Holtak Udvara. */
    public List<CompanionProfile> courtRoster(final Player player) {
        return companionRoster(player, NECRO_COURT);
    }

    private List<CompanionProfile> companionRoster(final Player player, final String namespace) {
        return ClassSpecCatalog.companionProjection(currentLoadout(player).orElse(null), namespace);
    }

    /**
     * Holtak Udvara feltámasztás a MEGLÉVŐ companion-gatewayen. A fajta csak attribútum: az udvar
     * logikai companion-id szerint kulcsolt, így ugyanaz a fajta ismételhető és a kapacitás elérhető
     * marad. Ugyanaz a felvételi szabály dönt itt és a commitban, ezért nincs fantom-cast.
     */
    public CompletionStage<PetMutationResult> raiseCourtV2(final Player player, final String kindId,
                                                           final String entityTypeId,
                                                           final int capacity) {
        final String kind = ClassSpecCatalog.normalize(kindId);
        final EntityType form = entityType(entityTypeId);
        final Optional<LoadoutSlot> slot = activeSlot(player);
        final String namespace = ClassSpecCatalog.companionNamespace(activeSpec(player));
        if (kind.isEmpty() || form == null || slot.isEmpty() || !NECRO_COURT.equals(namespace))
            return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-wrong-spec"));
        if (!ClassSpecCatalog.admitsCompanion(currentLoadout(player).orElse(null), namespace, capacity))
            return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-court-full"));
        final UUID sessionToken = currentSessionToken(player).orElse(null);
        if (sessionToken == null) return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-session-unavailable"));
        final UUID logicalId = UUID.randomUUID();
        final CompanionProfile companion = new CompanionProfile(logicalId, namespace, form.name(),
                "Udvaronc", 1, 0L, "", MinionManager.Stance.ACTIVE.name(), List.of(), 0L,
                Map.of("ritual_summoned", "true", CompanionProfile.KIND_KEY, kind));
        return gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                        ClassSpecProfileGateway.CompanionMutationRequest.Kind.ADD, logicalId, companion,
                        "", 0, 0L, 0L, List.of(), Map.of(), Math.max(1, capacity),
                        "necromancer-raise:" + logicalId))
                .thenCompose(result -> afterDurableSpawnMutation(
                        player, sessionToken, result, logicalId, form));
    }

    private EntityType demonForm(final int level) {
        if (level >= configManager.getInt("pets.summon.tier3-level", 25)) return EntityType.MAGMA_CUBE;
        if (level >= configManager.getInt("pets.summon.tier2-level", 15)) return EntityType.BLAZE;
        return EntityType.VEX;
    }

    private static String demonFormName(final EntityType form) {
        if (form == EntityType.MAGMA_CUBE) return "Magma-behemót";
        return form == EntityType.BLAZE ? "Tűz-démon" : "Imp";
    }

    public CompletionStage<String> summonV2(final Player player) {
        if (!canOwnPet(player)) return CompletableFuture.completedFuture("pet-not-allowed");
        final CompanionProfile selected = activeCompanion(player)
                .orElseGet(() -> companionRoster(player).stream().findFirst().orElse(null));
        if (selected == null) {
            return CompletableFuture.completedFuture(isUnholy(player) || isWarlock(player)
                    ? "pet-ritual-required" : "pet-none-captured");
        }
        return selectV2(player, selected.companionId());
    }

    /** Durable stable selection followed by a safe runtime switch to that companion. */
    public CompletionStage<String> selectV2(final Player player, final UUID companionId) {
        if (!canOwnPet(player)) return CompletableFuture.completedFuture("pet-not-allowed");
        final Optional<LoadoutSlot> slot = activeSlot(player);
        final CompanionProfile selected = companionRoster(player).stream()
                .filter(companion -> companion.companionId().equals(companionId)).findFirst().orElse(null);
        if (slot.isEmpty() || selected == null) return CompletableFuture.completedFuture("pet-selection-invalid");
        if (resummonAt(selected) > System.currentTimeMillis()) {
            return CompletableFuture.completedFuture("pet-respawn-cooldown");
        }
        if (selected.companionId().equals(selectedCompanionId(player).orElse(null)) && hasActivePet(player)) {
            return CompletableFuture.completedFuture("pet-already-summoned");
        }
        final UUID sessionToken = currentSessionToken(player).orElse(null);
        if (sessionToken == null) return CompletableFuture.completedFuture("pet-session-unavailable");
        final CompletionStage<ProfileMutationResult<hu.taliann.icesmp.classspec.application.ProfileDiagnostic>> durable;
        if (selected.companionId().equals(selectedCompanionId(player).orElse(null))) {
            durable = CompletableFuture.completedFuture(ProfileMutationResult.noChange(
                    gateway().diagnostic(player.getUniqueId()), "companion already selected"));
        } else {
            durable = gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                    ClassSpecProfileGateway.CompanionMutationRequest.Kind.SET_ACTIVE, selected.companionId(), null,
                    "", 0, 0L, 0L, List.of(), Map.of(), "pet-select:" + UUID.randomUUID()));
        }
        return durable.thenCompose(result -> {
            if (!result.runtimeGenerationUsable()) {
                return CompletableFuture.completedFuture(switch (result.status()) {
                    case STALE_SESSION -> "pet-stale-session";
                    case RUNTIME_EFFECT_FAILED -> "pet-runtime-retry";
                    default -> "pet-persistence-failed";
                });
            }
            final CompletableFuture<String> completion = new CompletableFuture<>();
            runOnCurrentPlayer(player, sessionToken, () -> {
                if (!selected.companionId().equals(selectedCompanionId(player).orElse(null))) {
                    completion.complete("pet-selection-superseded");
                    return;
                }
                try {
                    final EntityType type = entityType(selected.typeId());
                    if (type == null) {
                        completion.complete("pet-selection-invalid");
                        return;
                    }
                    beginPetActivation(player, selected.companionId());
                    spawnAndAdopt(player, type);
                    completion.complete(null);
                } catch (final PetSpawnException failure) {
                    retirePetActivation(player.getUniqueId(), selected.companionId());
                    completion.complete(failure.messageKey());
                } catch (final Throwable failure) {
                    retirePetActivation(player.getUniqueId(), selected.companionId());
                    plugin.getLogger().warning("Pet runtime switch failed for " + player.getUniqueId()
                            + ": " + failure.getMessage());
                    completion.complete("pet-runtime-retry");
                }
            }, () -> completion.complete("pet-stale-session"));
            return completion;
        });
    }

    public CompletionStage<Boolean> dismissV2(final Player player) {
        final Optional<LoadoutSlot> slot=activeSlot(player); final Optional<CompanionProfile> active=activeCompanion(player);
        if(slot.isEmpty()||active.isEmpty()) return CompletableFuture.completedFuture(false);
        final UUID companionId = active.orElseThrow().companionId();
        final UUID sessionToken=currentSessionToken(player).orElse(null);
        if(sessionToken==null)return CompletableFuture.completedFuture(false);
        return gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                ClassSpecProfileGateway.CompanionMutationRequest.Kind.DISMISS,companionId,null,"",0,0L,0L,List.of(),Map.of(),"pet-dismiss:"+UUID.randomUUID()))
                .thenCompose(result->{
                    if(!result.durableMutationApplied())return CompletableFuture.completedFuture(false);
                    final CompletableFuture<Boolean> completion=new CompletableFuture<>();
                    runOnCurrentPlayer(player,sessionToken,()->{
                        removeActive(player, companionId);
                        completion.complete(true);
                    },()->completion.complete(true));
                    return completion;
                });
    }

    /** Durable-first stable release: the roster entry is removed before any live-entity effect. */
    public CompletionStage<Boolean> releaseV2(final Player player) {
        final List<CompanionProfile> roster = companionRoster(player);
        final CompanionProfile selected = activeCompanion(player)
                .orElseGet(() -> roster.size() == 1 ? roster.getFirst() : null);
        return selected == null ? CompletableFuture.completedFuture(false)
                : releaseV2(player, selected.companionId());
    }

    /** Durable-first release of an explicitly selected stable entry. */
    public CompletionStage<Boolean> releaseV2(final Player player, final UUID companionId) {
        final Optional<LoadoutSlot> slot = activeSlot(player);
        final CompanionProfile selected = companionRoster(player).stream()
                .filter(companion -> companion.companionId().equals(companionId)).findFirst().orElse(null);
        if (slot.isEmpty() || selected == null) return CompletableFuture.completedFuture(false);
        final UUID sessionToken = currentSessionToken(player).orElse(null);
        if (sessionToken == null) return CompletableFuture.completedFuture(false);
        return gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                        ClassSpecProfileGateway.CompanionMutationRequest.Kind.REMOVE, companionId,
                        null, "", 0, 0L, 0L, List.of(), Map.of(), "pet-release:" + companionId))
                .thenCompose(result -> {
                    if (!result.durableMutationApplied()) return CompletableFuture.completedFuture(false);
                    final CompletableFuture<Boolean> completion = new CompletableFuture<>();
                    runOnCurrentPlayer(player, sessionToken, () -> {
                        pendingDeathCooldowns.remove(companionId);
                        removeActive(player, companionId);
                        completion.complete(true);
                    }, () -> completion.complete(true));
                    return completion;
                });
    }

    public CompletionStage<Boolean> setNameV2(final Player player, final String name) {
        if(name==null||name.isBlank()||name.length()>24)return CompletableFuture.completedFuture(false);
        final ActiveCompanionRef active = activeCompanionRef(player).orElse(null);
        if (active == null) return CompletableFuture.completedFuture(false);
        final UUID companionId = active.companion().companionId();
        final UUID sessionToken=currentSessionToken(player).orElse(null);
        if(sessionToken==null)return CompletableFuture.completedFuture(false);
        return mutateCompanion(player, active, ClassSpecProfileGateway.CompanionMutationRequest.Kind.RENAME,
                        name, 0, 0L, 0L, List.of(), Map.of(), "pet-rename:" + UUID.randomUUID())
                .thenCompose(result->{
                    if(!result.durableMutationApplied())return CompletableFuture.completedFuture(false);
                    final CompletableFuture<Boolean> completion=new CompletableFuture<>();
                    runOnCurrentPlayer(player,sessionToken,()->{
                        companionById(player, companionId).ifPresent(committed -> {
                            if (companionId.equals(selectedCompanionId(player).orElse(null))) {
                                scheduleActivePet(player, pet -> updateName(
                                        pet, committed.name(), committed.level()));
                            }
                        });
                        completion.complete(true);
                    },()->completion.complete(true));
                    return completion;
                });
    }

    public CompletionStage<Boolean> setStanceV2(final Player player, final MinionManager.Stance stance) {
        java.util.Objects.requireNonNull(stance,"stance");
        final ActiveCompanionRef active = activeCompanionRef(player).orElse(null);
        if (active == null) return CompletableFuture.completedFuture(false);
        return mutateCompanion(player, active, ClassSpecProfileGateway.CompanionMutationRequest.Kind.STANCE,
                stance.name(), 0, 0L, 0L, List.of(), Map.of(), "pet-stance:" + UUID.randomUUID())
                .thenApply(ProfileMutationResult::durableMutationApplied);
    }

    public CompletionStage<MinionManager.Stance> cycleStanceV2(final Player player) {
        final MinionManager.Stance next=switch(getStance(player)){case ACTIVE->MinionManager.Stance.PASSIVE;case PASSIVE->MinionManager.Stance.STAY;case STAY->MinionManager.Stance.ACTIVE;};
        return setStanceV2(player,next).thenApply(committed->committed?next:null);
    }

    public CompletionStage<PetMutationResult> equipArmorV2(final Player player, final Entity clicked) {
        if(!canOwnPet(player))return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-not-allowed"));
        final UUID petId = activePetId(player);
        if(petId==null||clicked==null||!petId.equals(clicked.getUniqueId()))return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-armor-not-pet"));
        final ActiveCompanionRef active = activeCompanionRef(player).orElse(null);
        final UUID companionId = activePetCompanionIds.get(player.getUniqueId());
        if (active == null || companionId == null
                || !companionId.equals(active.companion().companionId())) {
            return CompletableFuture.completedFuture(
                PetMutationResult.rejected("pet-armor-not-pet"));
        }
        if(!active.companion().equipmentIds().isEmpty())return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-armor-already"));
        final UUID sessionToken=currentSessionToken(player).orElse(null);
        if(sessionToken==null)return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-session-unavailable"));
        return mutateCompanion(player, active, ClassSpecProfileGateway.CompanionMutationRequest.Kind.EQUIPMENT,
                        "", 0, 0L, 0L, List.of("pet_armor"), Map.of(),
                        "pet-armor:" + UUID.randomUUID())
                .thenCompose(result->afterDurableEntityMutation(player,sessionToken,result,
                        companionId, clicked,
                        pet -> applyEquipment(pet)));
    }

    public CompletionStage<Boolean> addXpV2(final Player player, final int amount, final String operationId) {
        if(amount<=0||!canOwnPet(player))return CompletableFuture.completedFuture(false);
        final ActiveCompanionRef active = activeCompanionRef(player).orElse(null);
        if (active == null) return CompletableFuture.completedFuture(false);
        return addXpV2(player, active, amount, operationId);
    }

    /** Credits a kill to the exact durable companion represented by the killing entity. */
    public CompletionStage<Boolean> addXpV2(final Player player, final UUID companionId,
                                             final int amount, final String operationId) {
        if (amount <= 0 || !canOwnPet(player)) return CompletableFuture.completedFuture(false);
        final ActiveCompanionRef companion = companionRefById(player, companionId).orElse(null);
        if (companion == null) return CompletableFuture.completedFuture(false);
        return addXpV2(player, companion, amount, operationId);
    }

    private CompletionStage<Boolean> addXpV2(final Player player, final ActiveCompanionRef active,
                                              final int amount, final String operationId) {
        final CompanionProfile before = active.companion();
        final LoadoutSlot slot = active.slot();
        final int maxLevel=Math.max(1,Math.min(CompanionProfile.MAX_LEVEL,
                configManager.getInt("pets.companion.max-level",30)));
        final int baseXp=Math.max(1,configManager.getInt("pets.companion.base-xp",10));
        final int increment=Math.max(0,configManager.getInt("pets.companion.increment-per-level",5));
        return gateway().mutateCompanionProgress(player.getUniqueId(),
                        new ClassSpecProfileGateway.CompanionProgressRequest(slot,
                                before.companionId(),amount,baseXp,increment,maxLevel,operationId))
                .thenCompose(result->{
                    if(!result.durableOutcomeAccepted())return CompletableFuture.completedFuture(false);
                    // Replay, stale generation and runtime-failed commits are already durable; never
                    // fabricate a second feedback effect. Reconnect rebuilds the live companion.
                    if(!result.committed())return CompletableFuture.completedFuture(true);
                    final CompanionProfile committed=gateway().currentProfile(player.getUniqueId())
                            .map(profile->profile.loadout(slot).companionRoster().get(before.companionId()))
                            .orElse(null);
                    if(committed==null||committed.level()<=before.level())return CompletableFuture.completedFuture(true);
                    final UUID sessionToken=currentSessionToken(player).orElse(null);
                    if(sessionToken==null)return CompletableFuture.completedFuture(true);
                    final int committedLevel=committed.level();
                    final CompletableFuture<Boolean> completion=new CompletableFuture<>();
                    runOnCurrentPlayer(player,sessionToken,()->{
                        if(committedLevel>=maxLevel)AdvancementService.award(player,"pet_bond");
                        if (before.companionId().equals(selectedCompanionId(player).orElse(null))) {
                            scheduleActivePet(player, pet -> {
                                applyBuffs(pet, committedLevel, false);
                                updateName(pet, committed.name(), committedLevel);
                            });
                        }
                        player.sendMessage(messageManager.getMessage("pet-level-up","<dark_green>🐾 A társad szintet lépett: <white>{level}</white></dark_green>",Map.of("level",String.valueOf(committedLevel))));completion.complete(true);
                    },()->completion.complete(true));
                    return completion;
                });
    }

    /**
     * Handles a pet's death: clears the combat state for that pet, and if it was the
     * owner's active companion, clears the stored reference and notifies them. The
     * owner-side Profile v2 mutation/message runs on the owner's region thread (Folia-safe); call this
     * from an EntityDeathEvent for minion-tagged mobs.
     */
    public void handlePetDeath(final LivingEntity dead) {
        final UUID ownerId=minionManager.getOwner(dead);if(ownerId==null)return;final UUID deadId=dead.getUniqueId();attackReady.remove(deadId);
        final Player owner=Bukkit.getPlayer(ownerId);if(owner==null)return;
        final UUID sessionToken=currentSessionToken(owner).orElse(null);if(sessionToken==null)return;
        runOnCurrentPlayer(owner,sessionToken,()->{
            final java.util.concurrent.atomic.AtomicReference<UUID> retiredCompanion =
                    new java.util.concurrent.atomic.AtomicReference<>();
            final java.util.concurrent.atomic.AtomicBoolean matched =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            activePetCompanionIds.compute(ownerId, (id, logicalId) -> {
                if (!deadId.equals(activePetEntities.get(id))) return logicalId;
                activePetEntities.remove(id, deadId);
                retiredCompanion.set(logicalId);
                matched.set(true);
                return null;
            });
            if (!matched.get()) return;
            final UUID logicalId = retiredCompanion.get();
            final ActiveCompanionRef companionRef = companionRefById(owner, logicalId).orElse(null);
            final CompanionProfile companion = companionRef == null ? null : companionRef.companion();
            activeOwners.remove(ownerId);
            combatTargets.remove(ownerId);
            petDeathHook.accept(ownerId);
            if(companionRef != null){
                try {
                    final long seconds=Math.max(0L,configManager.getLong("pets.companion.death-respawn-seconds",120L));
                    final long cd=Math.multiplyExact(seconds,1000L);final long at=Math.addExact(System.currentTimeMillis(),cd);
                    pendingDeathCooldowns.put(companion.companionId(), at);
                    gateway().mutateCompanion(ownerId,companionRequest(companionRef.slot(),ClassSpecProfileGateway.CompanionMutationRequest.Kind.RESPAWN_AT,companion.companionId(),null,"",0,0L,at,List.of(),Map.of(),"pet-death:"+deadId))
                            .whenComplete((result, failure) -> {
                                if (failure == null && result != null && result.durableOutcomeAccepted()) {
                                    pendingDeathCooldowns.remove(companion.companionId(), at);
                                    return;
                                }
                                gateway().blockSession(ownerId, "Pet death cooldown persistence failed");
                                plugin.getLogger().severe("Failed to persist pet death for " + ownerId
                                        + (failure == null ? "" : ": " + failure.getMessage()));
                            });
                } catch (ArithmeticException invalidConfig) {
                    gateway().blockSession(ownerId,"Pet respawn cooldown overflow");
                    plugin.getLogger().severe("Pet respawn cooldown overflow for "+ownerId);
                }
            }
            owner.sendMessage(messageManager.getMessage("pet-died","<gray>A társad elesett a harcban. <dark_gray>(/pet summon az új idézéshez)</dark_gray></gray>"));
        },()->plugin.getLogger().fine("Discarded stale pet-death callback for "+ownerId));
    }

    public boolean setName(final Player player, final String name) { return false; }

    /**
     * A társ állásmódja a gazda aktív Profile v2 loadoutjához tartozik. A vezérlő tick
     * a gazda schedulerén készített immutable snapshotot viszi át a pet schedulerére.
     */
    public MinionManager.Stance getStance(final Player player) {
        final String stored=activeCompanion(player).map(CompanionProfile::stance).orElse("ACTIVE");
        try{return MinionManager.Stance.valueOf(stored.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException invalid){return MinionManager.Stance.ACTIVE;}
    }

    public void setStance(final Player player, final MinionManager.Stance stance) { }

    public MinionManager.Stance cycleStance(final Player player) { return getStance(player); }

    /** A kattintott entitás a játékos runtime registryben aktív társa-e. */
    public boolean isActivePetEntity(final Player player, final Entity clicked) {
        return player != null && clicked != null
                && clicked.getUniqueId().equals(activePetId(player));
    }

    public record PetKillAttribution(UUID ownerId, UUID companionId) {
    }

    /** Resolves only the one live durable companion, never a temporary spell minion. */
    public Optional<PetKillAttribution> activePetAttribution(final Entity entity) {
        if (entity == null) return Optional.empty();
        final UUID ownerId = minionManager.getOwner(entity);
        if (ownerId == null) return Optional.empty();
        final UUID entityId = entity.getUniqueId();
        final java.util.concurrent.atomic.AtomicReference<PetKillAttribution> attribution =
                new java.util.concurrent.atomic.AtomicReference<>();
        activePetCompanionIds.compute(ownerId, (id, companionId) -> {
            if (companionId != null && entityId.equals(activePetEntities.get(id))) {
                attribution.set(new PetKillAttribution(id, companionId));
            }
            return companionId;
        });
        return Optional.ofNullable(attribution.get());
    }

    public Optional<UUID> activePetOwnerId(final Entity entity) {
        return activePetAttribution(entity).map(PetKillAttribution::ownerId);
    }

    public boolean hasActivePet(final Player player) {
        return player != null && activePetId(player) != null;
    }

    public int nextLevelCost(final Player player) {
        return levelCost(getLevel(player));
    }

    public long respawnRemainingSeconds(final Player player) {
        final long at=activeCompanion(player).map(this::resummonAt).orElse(0L);
        final long now=System.currentTimeMillis();if(at<=now)return 0L;
        try{return Math.addExact(Math.subtractExact(at,now),999L)/1000L;}
        catch(ArithmeticException corrupt){gateway().blockSession(player.getUniqueId(),"Pet respawn timestamp overflow");return Long.MAX_VALUE;}
    }

    public long respawnRemainingSeconds(final CompanionProfile companion) {
        if (companion == null) return 0L;
        final long at = resummonAt(companion);
        final long now = System.currentTimeMillis();
        if (at <= now) return 0L;
        try {
            return Math.addExact(Math.subtractExact(at, now), 999L) / 1000L;
        } catch (final ArithmeticException corrupt) {
            return Long.MAX_VALUE;
        }
    }

    private long resummonAt(final CompanionProfile companion) {
        return Math.max(companion.resummonAtEpochMillis(),
                pendingDeathCooldowns.getOrDefault(companion.companionId(), 0L));
    }

    /** Társvért: a durable companion equipment listában él, így újraidézéskor is visszakerül. */
    public boolean hasPetArmor(final Player player) { return activeCompanion(player).map(c->!c.equipmentIds().isEmpty()).orElse(false); }

    /**
     * Felszereli a Társvértet a játékos aktív társára.
     *
     * @return null siker, különben üzenet-kulcs
     */
    public String equipArmor(final Player player, final Entity clicked) { return "pet-persistence-required"; }

    private void applyEquipment(final LivingEntity pet) {
        applyModifier(pet, Attribute.ARMOR, armorDefenseModKey,
                Math.max(0.0D, configManager.getDouble("pets.equipment.armor-bonus", 4.0D)));
        applyModifier(pet, Attribute.MAX_HEALTH, armorHealthModKey,
                Math.max(0.0D, configManager.getDouble("pets.equipment.health-bonus", 4.0D)));
    }

    /** Awards companion XP for the owner; levels up (rebuffing the active pet) on threshold. */
    public void addXp(final Player player, final int amount) { }

    /**
     * Owner-side gate of the combat-target flow: the owner has a usable Profile v2 pet spec
     * and a live runtime companion. Must run on the owner's region thread (Folia).
     */
    public boolean canReceiveCombatTarget(final Player owner) {
        return owner != null && canOwnPet(owner) && activePetId(owner) != null;
    }

    /**
     * Target-side filter of the combat-target flow: the target is alive, not the owner and not
     * one of the owner's own minions (a pet never turns on its allies). Reads the TARGET's
     * state/PDC — must run on the target's region thread (Folia).
     */
    public boolean isEligibleCombatTarget(final UUID ownerId, final LivingEntity target) {
        return ownerId != null && target != null && !target.isDead() && target.isValid()
                && !target.getUniqueId().equals(ownerId)
                && !minionManager.isOwnedBy(target, ownerId);
    }

    /**
     * Records a validated combat target for the owner's pet (assist/defend). Concurrent-map
     * write — safe from any region thread once both sides were validated on their own threads
     * ({@link #canReceiveCombatTarget} / {@link #isEligibleCombatTarget}); the pet controller
     * {@link #tick()} re-validates the target anyway.
     */
    public void putCombatTarget(final UUID ownerId, final UUID targetId) {
        if (ownerId != null && targetId != null) {
            combatTargets.put(ownerId, targetId);
        }
    }

    /**
     * Drives every active companion each scheduler pass. The pet's behaviour is
     * controlled entirely by the plugin (not the mob's own AI): in ACTIVE stance it
     * chases its target via the pathfinder and lands plugin-applied hits, so even a
     * peaceful animal fights like a real pet; with no target it follows the owner.
     * STAY holds position, PASSIVE only follows.
     */
    public void tick() {
        final long now = System.currentTimeMillis();
        pendingDeathCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        final double followSq = Math.pow(Math.max(4.0D, configManager.getDouble("pets.companion.follow-distance", 16.0D)), 2);
        final double followStartSq = Math.pow(Math.max(2.0D, configManager.getDouble("pets.companion.follow-start-distance", 5.0D)), 2);
        final double reach = Math.max(1.5D, configManager.getDouble("pets.companion.attack-reach", 2.6D));
        final double aggro = Math.max(0.0D, configManager.getDouble("pets.companion.aggro-range", 10.0D));
        final double leash = Math.max(8.0D, configManager.getDouble("pets.companion.leash-range", 24.0D));
        final double chaseSpeed = Math.max(0.1D, configManager.getDouble("pets.companion.chase-speed", 1.3D));
        final long cooldownMs = Math.max(200L, configManager.getInt("pets.companion.attack-cooldown-ticks", 16) * 50L);

        // Folia: read each owner's PDC + location on the OWNER's region thread, snapshot it,
        // then hop to the PET's region thread for all pet mutations (the pet may be elsewhere).
        for (final UUID ownerId : activeOwners) {
            final Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null) {
                activeOwners.remove(ownerId);
                continue;
            }
            owner.getScheduler().run(plugin, ownerTask ->
                    tickOwner(owner, followSq, followStartSq, reach, aggro, leash, chaseSpeed, cooldownMs), null);
        }
    }

    private void tickOwner(final Player owner, final double followSq, final double followStartSq, final double reach,
                           final double aggro, final double leash, final double chaseSpeed, final long cooldownMs) {
        final UUID petId = activePetId(owner);
        if (petId == null) {
            return;
        }
        final Entity entity;
        entity = Bukkit.getEntity(petId);
        if (!(entity instanceof Mob pet)) {
            return;
        }
        final UUID ownerId = owner.getUniqueId();
        final int level;
        try {
            level = runtimeSnapshot(owner).buffLevel();
        } catch (final IllegalStateException missingProjection) {
            return;
        }
        final Location ownerLoc = owner.getLocation();
        final World ownerWorld = owner.getWorld();
        final MinionManager.Stance stance = getStance(owner);
        pet.getScheduler().run(plugin, petTask ->
                runPetTick(pet, ownerId, stance, ownerLoc, ownerWorld, level, followSq, followStartSq, reach, aggro, leash, chaseSpeed, cooldownMs), null);
    }

    private void runPetTick(final Mob pet, final UUID ownerId, final MinionManager.Stance stance,
                            final Location ownerLoc, final World ownerWorld,
                            final int level, final double followSq, final double followStartSq, final double reach,
                            final double aggro, final double leash, final double chaseSpeed, final long cooldownMs) {
        if (!pet.isValid()) {
            return;
        }

        if (stance == MinionManager.Stance.STAY) {
            combatTargets.remove(ownerId);
            pet.setTarget(null);
            pet.getPathfinder().stopPathfinding();
            // Világváltásnál a STAY pet sem maradhat árván a régi világban.
            if (!pet.getWorld().equals(ownerWorld)) {
                pet.teleportAsync(ownerLoc);
            }
            return; // hold position — no follow, no combat
        }

        LivingEntity target = null;
        if (stance == MinionManager.Stance.ACTIVE) {
            target = resolveTarget(ownerId, ownerWorld, ownerLoc, leash);
            if (target == null) {
                target = acquireNearbyThreat(pet, ownerId, aggro);
                if (target != null) {
                    combatTargets.put(ownerId, target.getUniqueId());
                }
            }
        } else {
            combatTargets.remove(ownerId); // PASSIVE never fights
            pet.setTarget(null);
        }

        if (target != null) {
            attack(pet, target, level, reach, chaseSpeed, cooldownMs);
            return;
        }

        // Ne maradjon meg vanilla célpont (pl. egy untamed farkas birkája): csak az
        // IceSMP által feloldott combatTargets célponttal harcolhat a társ.
        pet.setTarget(null);
        // No target → follow the owner. Every pet trails its owner by default: it walks toward
        // them once it lags past the follow-start radius, and teleports to catch up only when it
        // falls too far behind or ends up in another world.
        followOwner(pet, ownerLoc, ownerWorld, followSq, followStartSq, chaseSpeed);
    }

    /** Keeps an idle pet near its owner (snapshot location): walk to trail, teleport to catch up. */
    private void followOwner(final Mob pet, final Location ownerLoc, final World ownerWorld, final double followSq,
                             final double followStartSq, final double chaseSpeed) {
        if (!ownerWorld.equals(pet.getWorld())) {
            pet.teleportAsync(ownerLoc);
            return;
        }
        final double distSq = pet.getLocation().distanceSquared(ownerLoc);
        if (distSq > followSq) {
            pet.teleportAsync(ownerLoc);
        } else if (distSq > followStartSq) {
            pet.getPathfinder().moveTo(ownerLoc, chaseSpeed);
        }
    }

    /** Validates the stored combat target (alive, same world, within the owner's leash). */
    private LivingEntity resolveTarget(final UUID ownerId, final World ownerWorld, final Location ownerLoc, final double leash) {
        final UUID id = combatTargets.get(ownerId);
        if (id == null) {
            return null;
        }
        final Entity entity = Bukkit.getEntity(id);
        if (!(entity instanceof LivingEntity living)) {
            combatTargets.remove(ownerId);
            return null;
        }
        // A cél lehet szomszéd régióé — állapotát (pozíció, világ, életjel) csak birtokolt
        // szálon olvassuk; idegen régióban lévő célt ebben a tickben kihagyunk, a bejegyzés
        // marad és a következő tickben újra-feloldódik.
        if (!Bukkit.isOwnedByCurrentRegion(living)) {
            return null;
        }
        if (living.isDead() || !living.isValid()
                || living.getUniqueId().equals(ownerId)
                || !living.getWorld().equals(ownerWorld)
                || living.getLocation().distanceSquared(ownerLoc) > leash * leash) {
            combatTargets.remove(ownerId);
            return null;
        }
        return living;
    }

    /** Picks the nearest hostile mob around the pet to defend against (excludes allies). */
    private LivingEntity acquireNearbyThreat(final Mob pet, final UUID ownerId, final double aggro) {
        if (aggro <= 0.0D) {
            return null;
        }
        LivingEntity best = null;
        double bestSq = aggro * aggro;
        for (final Entity nearby : pet.getNearbyEntities(aggro, aggro, aggro)) {
            // Az aggro-sugár átnyúlhat régióhatáron — idegen régió mobját nem olvassuk/célozzuk.
            if (!(nearby instanceof Monster monster) || !Bukkit.isOwnedByCurrentRegion(monster)
                    || monster.isDead() || !monster.isValid()
                    || minionManager.isOwnedBy(monster, ownerId)) {
                continue;
            }
            final double sq = monster.getLocation().distanceSquared(pet.getLocation());
            if (sq < bestSq) {
                bestSq = sq;
                best = monster;
            }
        }
        return best;
    }

    /** Chases the target via the pathfinder and lands a plugin-applied hit on cooldown. */
    private void attack(final Mob pet, final LivingEntity target, final int level, final double reach,
                        final double chaseSpeed, final long cooldownMs) {
        pet.setTarget(target); // reinforce mobs that do have attack AI
        if (pet.getLocation().distanceSquared(target.getLocation()) > reach * reach) {
            pet.getPathfinder().moveTo(target, chaseSpeed); // AI-independent chase
            return;
        }
        final long now = System.currentTimeMillis();
        final Long ready = attackReady.get(pet.getUniqueId());
        if (ready != null && now < ready) {
            return;
        }
        attackReady.put(pet.getUniqueId(), now + cooldownMs);
        pet.swingMainHand();
        target.damage(petDamage(level), pet); // the plugin lands the hit, whatever the mob is
    }

    private double petDamage(final int level) {
        final double base = Math.max(0.5D, configManager.getDouble("pets.companion.attack-damage-base", 3.0D));
        final double perLevel = Math.max(0.0D, configManager.getDouble("pets.companion.damage-per-level", 0.5D));
        return base + (level * perLevel);
    }

    private EntityType resolveType(final Player player) {
        return activeCompanion(player).map(CompanionProfile::typeId).map(this::entityType).orElse(null);
    }

    private record PetRuntimeSnapshot(UUID ownerId, UUID companionId, String name, int level, int buffLevel,
                                      double talentHealthBonus, boolean armored) {
    }

    private PetRuntimeSnapshot runtimeSnapshot(final Player player) {
        final CompanionProfile companion = activeCompanion(player).orElseThrow(
                () -> new IllegalStateException("Active companion snapshot is unavailable"));
        final int level = companion.level();
        final int buffLevel = level
                + (Boolean.parseBoolean(companion.persistentState().getOrDefault("ritual_summoned", "false"))
                ? Math.max(0, configManager.getInt("pets.summon.bonus-levels", 5)) : 0)
                + (UNHOLY_GHOUL.equals(companion.namespace())
                ? unholyMutationBonusLevels(companion) : 0);
        final hu.taliann.icesmp.managers.TalentManager talents = this.talentManagerRef;
        final double talentHealthBonus = talents == null ? 0.0D
                : Math.max(0.0D, talents.getEffectTotal(player, "max-health")
                        * Math.max(0.0D, configManager.getDouble("pets.talent-health-share", 0.5D)));
        final String name = companion.name().isBlank() ? "Társ" : companion.name();
        return new PetRuntimeSnapshot(player.getUniqueId(), companion.companionId(), name, level,
                buffLevel, talentHealthBonus, !companion.equipmentIds().isEmpty());
    }

    private boolean adopt(final Mob mob, final PetRuntimeSnapshot snapshot) {
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        if (mob instanceof Tameable tameable) {
            // A vanilla gazda scoreboard-teamjét (és így a LuckPerms rangprefixét)
            // a tameable névtáblája is örökölné. A követést és a harcot az IceSMP
            // saját vezérlője intézi, ezért a társ nem marad vanilla-szelídített.
            tameable.setOwner(null);
            tameable.setTamed(false);
        }
        mob.setTarget(null);
        // A pet BÁRMILYEN mob lehet(Beast Master / Necromancer): a közös keményítés fedi a
        // zombi/csontváz/phantom nappali égést ÉS a piglin/hoglin overworld-zombisodását is.
        EventSpawnGuard.prepare(mob);
        // Idézett társ prémiuma: bónusz-szintekkel skálázott statok (a rituálé-beszerzés ára).
        applyBuffs(mob, snapshot.buffLevel(), true);
        // A gazda max-health talentjei a PERMANENS társat is erősítik (a minionokkal
        // azonos megosztási arány — a két rendszer skálázása konzisztens).
        if (snapshot.talentHealthBonus() > 0.0D) {
            final org.bukkit.attribute.AttributeInstance hp =
                    mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(hp.getBaseValue() + snapshot.talentHealthBonus());
                mob.setHealth(hp.getValue());
            }
        }
        if (snapshot.armored()) {
            applyEquipment(mob);
            final AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                mob.setHealth(hp.getValue());
            }
        }
        updateName(mob, snapshot.name(), snapshot.level());
        minionManager.tag(mob, snapshot.ownerId());
        final java.util.concurrent.atomic.AtomicBoolean activated =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        activePetCompanionIds.compute(snapshot.ownerId(), (ownerId, expectedId) -> {
            if (!snapshot.companionId().equals(expectedId)) return expectedId;
            activePetEntities.put(ownerId, mob.getUniqueId());
            activeOwners.add(ownerId);
            activated.set(true);
            return expectedId;
        });
        if (!activated.get()) mob.remove();
        return activated.get();
    }

    private int levelCost(final int level) {
        final int base = Math.max(1, configManager.getInt("pets.companion.base-xp", 10));
        final int increment = Math.max(0, configManager.getInt("pets.companion.increment-per-level", 5));
        try{return Math.addExact(base,Math.multiplyExact(Math.max(0,level-1),increment));}
        catch(ArithmeticException invalidConfig){throw new IllegalStateException("Pet level cost overflow",invalidConfig);}
    }

    /**
     * (Re)applies the level-based attribute buffs. {@code heal} fully restores the
     * pet to its new max health — only wanted on summon/adopt. On level-up the cap
     * grows but current health is NOT topped up, so a kill mid-fight can't instantly
     * heal a near-dead pet to full.
     */
    private void applyBuffs(final LivingEntity pet, final int level, final boolean heal) {
        final double healthPerLevel = Math.max(0.0D, configManager.getDouble("pets.companion.health-per-level", 2.0D));
        final double damagePerLevel = Math.max(0.0D, configManager.getDouble("pets.companion.damage-per-level", 0.5D));

        // Idempotent attribute modifiers (re-applied on level-up without compounding).
        applyModifier(pet, Attribute.MAX_HEALTH, healthModKey, level * healthPerLevel);
        applyModifier(pet, Attribute.ATTACK_DAMAGE, damageModKey, level * damagePerLevel);

        if (heal) {
            final AttributeInstance maxHealth = pet.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                pet.setHealth(maxHealth.getValue());
            }
        }
    }

    private void applyModifier(final LivingEntity pet, final Attribute attribute, final NamespacedKey key, final double amount) {
        final AttributeInstance instance = pet.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        for (final AttributeModifier modifier : instance.getModifiers()) {
            if (key.equals(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }
        if (amount != 0.0D) {
            instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void updateName(final Mob pet, final String name, final int level) {
        pet.customName(Component.text(name + " [Lv " + level + "]", NamedTextColor.GREEN));
        pet.setCustomNameVisible(true);
    }

    /**
     * Clears the owner-keyed combat-target entry on logout so a player who disconnects mid-combat
     * does not leave a stale {@code combatTargets} entry. ({@code attackReady} is pet-UUID-keyed and
     * pruned on pet death/removal.)
     */
    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId != null) {
            retirePetActivation(playerId, null);
            // A gazda nélkül maradt társ/minionok despawnolnak (Profile v2-ből újraidézhetők) —
            // nem maradhat árva, örök-persistent entitás a világban.
            minionManager.removeAllOwned(playerId);
        }
    }

    /** Runtime-only projections for class gameplay services; never durable identity. */
    public Optional<UUID> activePetEntityId(final UUID ownerId) {
        return Optional.ofNullable(activePetEntities.get(ownerId));
    }

    public Optional<UUID> currentCombatTarget(final UUID ownerId) {
        return Optional.ofNullable(combatTargets.get(ownerId));
    }

    /** Runs a cross-entity effect exclusively on the active pet's entity scheduler. */
    public void runOnActivePet(final Player player, final Consumer<Mob> effect) {
        if (player != null && effect != null) {
            scheduleActivePet(player, effect);
        }
    }

    /** Owner-thread callback after the active companion's live entity died. */
    public void setPetDeathHook(final java.util.function.Consumer<UUID> hook) {
        petDeathHook = hook == null ? ignored -> { } : hook;
    }

    private void scheduleActivePet(final Player player, final Consumer<Mob> effect) {
        final UUID petId = activePetId(player);
        if (petId == null) return;
        final Entity entity = Bukkit.getEntity(petId);
        if (!(entity instanceof Mob pet)) return;
        pet.getScheduler().run(plugin, task -> {
            if (pet.isValid() && !pet.isDead()) {
                effect.accept(pet);
            }
        }, null);
    }

    private void beginPetActivation(final Player player, final UUID companionId) {
        retirePetActivation(player.getUniqueId(), null);
        activePetCompanionIds.put(player.getUniqueId(), companionId);
    }

    private boolean activationCurrent(final UUID ownerId, final UUID companionId) {
        return ownerId != null && companionId != null
                && companionId.equals(activePetCompanionIds.get(ownerId));
    }

    private boolean removeActive(final Player player) {
        return retirePetActivation(player.getUniqueId(), null);
    }

    private boolean removeActive(final Player player, final UUID expectedCompanionId) {
        return retirePetActivation(player.getUniqueId(), expectedCompanionId);
    }

    /** Atomically retires only the expected activation generation, then removes its entity. */
    private boolean retirePetActivation(final UUID ownerId, final UUID expectedCompanionId) {
        final java.util.concurrent.atomic.AtomicReference<UUID> retiredEntity =
                new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean retired =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        activePetCompanionIds.compute(ownerId, (id, currentCompanionId) -> {
            if (expectedCompanionId != null && !expectedCompanionId.equals(currentCompanionId)) {
                return currentCompanionId;
            }
            retiredEntity.set(activePetEntities.remove(id));
            retired.set(currentCompanionId != null || retiredEntity.get() != null);
            return null;
        });
        if (expectedCompanionId == null && retiredEntity.get() == null) {
            final UUID orphanEntity = activePetEntities.remove(ownerId);
            if (orphanEntity != null) {
                retiredEntity.set(orphanEntity);
                retired.set(true);
            }
        }
        if (!retired.get()) return false;
        activeOwners.remove(ownerId);
        combatTargets.remove(ownerId);
        final UUID petId = retiredEntity.get();
        if (petId == null) return false;
        attackReady.remove(petId);
        final Entity entity = Bukkit.getEntity(petId);
        if (entity != null) {
            entity.getScheduler().run(plugin, task -> entity.remove(), null);
            return true;
        }
        return false;
    }

    private UUID activePetId(final Player player) { return activePetEntities.get(player.getUniqueId()); }
    private ClassSpecProfileGateway gateway() {
        final ClassSpecProfileGateway gateway=profileGateway;if(gateway==null)throw new IllegalStateException("Profile v2 gateway is unavailable");return gateway;
    }

    private Optional<CompanionProfile> activeCompanion(final Player player) {
        final ClassSpecProfileGateway gateway=profileGateway;return gateway==null?Optional.empty():gateway.activeCompanion(player.getUniqueId());
    }

    private int unholyMutationBonusLevels(final CompanionProfile companion) {
        return parseNonNegativeInt(companion.persistentState().get(GHOUL_MUTATION_STAGE))
                * Math.max(0, configManager.getInt(
                "pets.summon.mutation-bonus-levels-per-stage", 2));
    }

    private static int parseNonNegativeInt(final String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (final NumberFormatException invalid) {
            return 0;
        }
    }

    private record ActiveCompanionRef(LoadoutSlot slot, CompanionProfile companion) {
    }

    /** Coherent active-slot/companion read from one immutable Profile v2 snapshot. */
    private Optional<ActiveCompanionRef> activeCompanionRef(final Player player) {
        return currentProfile(player).flatMap(profile -> {
            final LoadoutSlot slot = profile.activeSlot();
            if (slot == null) return Optional.empty();
            final ClassLoadout loadout = profile.loadout(slot);
            final String raw = loadout.mechanicState().get("companion.active_id");
            if (raw == null || raw.isBlank()) return Optional.empty();
            try {
                return Optional.ofNullable(loadout.companionRoster().get(UUID.fromString(raw)))
                        .map(companion -> new ActiveCompanionRef(slot, companion));
            } catch (final IllegalArgumentException invalidIdentity) {
                gateway().blockSession(player.getUniqueId(), "Invalid active companion identity");
                return Optional.empty();
            }
        });
    }

    private Optional<CompanionProfile> companionById(final Player player, final UUID companionId) {
        return companionRefById(player, companionId).map(ActiveCompanionRef::companion);
    }

    private Optional<ActiveCompanionRef> companionRefById(final Player player, final UUID companionId) {
        if (companionId == null) return Optional.empty();
        return currentProfile(player).flatMap(profile -> {
            for (final LoadoutSlot slot : LoadoutSlot.values()) {
                final CompanionProfile companion = profile.loadout(slot).companionRoster().get(companionId);
                if (companion != null) return Optional.of(new ActiveCompanionRef(slot, companion));
            }
            return Optional.empty();
        });
    }

    private Optional<ClassSpecSection> currentProfile(final Player player) {
        final ClassSpecProfileGateway gateway=profileGateway;return gateway==null?Optional.empty():gateway.currentProfile(player.getUniqueId());
    }

    private Optional<LoadoutSlot> activeSlot(final Player player) { return currentProfile(player).map(ClassSpecSection::activeSlot); }
    private Optional<ClassLoadout> currentLoadout(final Player player) { return currentProfile(player).filter(p->p.activeSlot()!=null).map(p->p.loadout(p.activeSlot())); }
    private String activeSpec(final Player player) { return currentLoadout(player).map(ClassLoadout::specializationId).orElse(""); }

    private CompletionStage<ProfileMutationResult<hu.taliann.icesmp.classspec.application.ProfileDiagnostic>> mutateCompanion(
            final Player player, final ActiveCompanionRef active,
            final ClassSpecProfileGateway.CompanionMutationRequest.Kind kind, final String text,
            final int level, final long experience, final long resummonAt, final List<String> equipment,
            final Map<String,String> state, final String operationId) {
        if (active == null) {
            return CompletableFuture.completedFuture(ProfileMutationResult.rejected(
                    gateway().diagnostic(player.getUniqueId()), "no active companion"));
        }
        return gateway().mutateCompanion(player.getUniqueId(), companionRequest(active.slot(), kind,
                active.companion().companionId(), null, text, level, experience, resummonAt,
                equipment, state, operationId));
    }

    private static ClassSpecProfileGateway.CompanionMutationRequest companionRequest(LoadoutSlot slot,ClassSpecProfileGateway.CompanionMutationRequest.Kind kind,UUID id,CompanionProfile companion,String text,int level,long experience,long at,List<String> equipment,Map<String,String> state,String operationId){
        return companionRequest(slot,kind,id,companion,text,level,experience,at,equipment,state,0,operationId);
    }

    private static ClassSpecProfileGateway.CompanionMutationRequest companionRequest(LoadoutSlot slot,ClassSpecProfileGateway.CompanionMutationRequest.Kind kind,UUID id,CompanionProfile companion,String text,int level,long experience,long at,List<String> equipment,Map<String,String> state,int capacity,String operationId){
        return new ClassSpecProfileGateway.CompanionMutationRequest(slot,kind,id,companion,text,level,experience,at,equipment,state,capacity,operationId);
    }

    private CompletionStage<PetMutationResult> afterDurableSpawnMutation(final Player player,
            final UUID sessionToken, final ProfileMutationResult<?> result, final UUID companionId,
            final EntityType type) {
        if (!result.durableMutationApplied()) {
            return CompletableFuture.completedFuture(PetMutationResult.rejected(
                    result.detail().isBlank() ? "pet-persistence-failed" : result.detail()));
        }
        final CompletableFuture<PetMutationResult> completion = new CompletableFuture<>();
        runOnCurrentPlayer(player, sessionToken, () -> {
            if (!companionId.equals(selectedCompanionId(player).orElse(null))) {
                completion.complete(PetMutationResult.appliedWithRuntimeFailure(
                        "pet-selection-superseded"));
                return;
            }
            try {
                beginPetActivation(player, companionId);
                spawnAndAdopt(player, type);
                completion.complete(PetMutationResult.applied());
            } catch (final PetSpawnException failure) {
                retirePetActivation(player.getUniqueId(), companionId);
                completion.complete(PetMutationResult.appliedWithRuntimeFailure(failure.messageKey()));
            } catch (final Throwable failure) {
                retirePetActivation(player.getUniqueId(), companionId);
                plugin.getLogger().severe("Durable pet mutation committed but runtime reconciliation failed for "
                        + player.getUniqueId() + ": " + failure.getMessage());
                completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-runtime-retry"));
            }
        }, () -> completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-stale-session")));
        return completion;
    }

    private CompletionStage<PetMutationResult> afterDurableCapture(final Player player,
            final UUID sessionToken, final ProfileMutationResult<?> result, final UUID companionId,
            final Mob mob) {
        if (!result.durableMutationApplied()) {
            return CompletableFuture.completedFuture(PetMutationResult.rejected(
                    result.detail().isBlank() ? "pet-persistence-failed" : result.detail()));
        }
        final CompletableFuture<PetMutationResult> completion = new CompletableFuture<>();
        runOnCurrentPlayer(player, sessionToken, () -> {
            if (!companionId.equals(selectedCompanionId(player).orElse(null))) {
                removeCapturedEntity(mob, completion, "pet-selection-superseded");
                return;
            }
            final UUID ownerId = player.getUniqueId();
            final PetRuntimeSnapshot snapshot;
            try {
                beginPetActivation(player, companionId);
                snapshot = runtimeSnapshot(player);
            } catch (final Throwable failure) {
                retirePetActivation(ownerId, companionId);
                removeCapturedEntity(mob, completion, "pet-runtime-retry");
                return;
            }
            mob.getScheduler().run(plugin, task -> {
                if (!isCurrentSession(ownerId, sessionToken)) {
                    retirePetActivation(ownerId, companionId);
                    if (mob.isValid()) mob.remove();
                    completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-stale-session"));
                    return;
                }
                if (!mob.isValid() || mob.isDead()) {
                    retirePetActivation(ownerId, companionId);
                    completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-runtime-retry"));
                    return;
                }
                if (!activationCurrent(ownerId, companionId)) {
                    mob.remove();
                    completion.complete(PetMutationResult.appliedWithRuntimeFailure(
                            "pet-selection-superseded"));
                    return;
                }
                try {
                    completion.complete(adopt(mob, snapshot)
                            ? PetMutationResult.applied()
                            : PetMutationResult.appliedWithRuntimeFailure("pet-selection-superseded"));
                } catch (final Throwable failure) {
                    retirePetActivation(ownerId, companionId);
                    if (mob.isValid()) mob.remove();
                    plugin.getLogger().severe("Durable pet capture committed but entity adoption failed for "
                            + ownerId + ": " + failure.getMessage());
                    completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-runtime-retry"));
                }
            }, () -> {
                retirePetActivation(ownerId, companionId);
                completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-runtime-retry"));
            });
        }, () -> removeCapturedEntity(mob, completion, "pet-stale-session"));
        return completion;
    }

    private void removeCapturedEntity(final Mob mob,
                                      final CompletableFuture<PetMutationResult> completion,
                                      final String messageKey) {
        mob.getScheduler().run(plugin, task -> {
            if (mob.isValid()) mob.remove();
            completion.complete(PetMutationResult.appliedWithRuntimeFailure(messageKey));
        }, () -> completion.complete(PetMutationResult.appliedWithRuntimeFailure(messageKey)));
    }

    private CompletionStage<PetMutationResult> afterDurableEntityMutation(final Player player,
            final UUID sessionToken, final ProfileMutationResult<?> result, final UUID companionId,
            final Entity entity, final Consumer<Mob> runtimeEffect) {
        if (!result.durableMutationApplied()) {
            return CompletableFuture.completedFuture(PetMutationResult.rejected(
                    result.detail().isBlank() ? "pet-persistence-failed" : result.detail()));
        }
        final CompletableFuture<PetMutationResult> completion = new CompletableFuture<>();
        if (!(entity instanceof Mob mob)) {
            return CompletableFuture.completedFuture(PetMutationResult.appliedWithRuntimeFailure(
                    "pet-runtime-retry"));
        }
        final UUID ownerId = player.getUniqueId();
        mob.getScheduler().run(plugin, task -> {
            if (!isCurrentSession(ownerId, sessionToken) || !mob.isValid() || mob.isDead()
                    || !mob.getUniqueId().equals(activePetEntities.get(ownerId))
                    || !companionId.equals(activePetCompanionIds.get(ownerId))) {
                completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-runtime-retry"));
                return;
            }
            try {
                runtimeEffect.accept(mob);
                completion.complete(PetMutationResult.applied());
            } catch (final Throwable failure) {
                completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-runtime-retry"));
            }
        }, () -> completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-runtime-retry")));
        return completion;
    }

    private EntityType entityType(final String raw) {
        if(raw==null||raw.isBlank())return null;try{final EntityType type=EntityType.valueOf(raw.toUpperCase(Locale.ROOT));return type.getEntityClass()!=null&&Mob.class.isAssignableFrom(type.getEntityClass())?type:null;}catch(IllegalArgumentException invalid){return null;}
    }

    private void spawnAndAdopt(final Player player, final EntityType type) {
        if(type==null||type.getEntityClass()==null||!Mob.class.isAssignableFrom(type.getEntityClass()))throw new IllegalArgumentException("Unsupported companion entity type");
        final PetRuntimeSnapshot snapshot = runtimeSnapshot(player);
        final Location spawn = findSafeSpawnLocation(player).orElseThrow(
                () -> new PetSpawnException("pet-no-safe-spawn"));
        final Mob mob;
        try {
            mob = (Mob) player.getWorld().spawn(spawn, type.getEntityClass().asSubclass(Mob.class));
        } catch (final RuntimeException blocked) {
            throw new PetSpawnException("pet-spawn-blocked", blocked);
        }
        if (!mob.isValid()) {
            throw new PetSpawnException("pet-spawn-blocked");
        }
        try {
            if (!adopt(mob, snapshot)) {
                throw new PetSpawnException("pet-selection-superseded");
            }
        } catch (final Throwable failure) {
            if (mob.isValid()) mob.remove();
            throw failure;
        }
    }

    /** Bounded, region-local standing-space search around the player; never loads a chunk. */
    private Optional<Location> findSafeSpawnLocation(final Player player) {
        final Location origin = player.getLocation();
        final World world = origin.getWorld();
        if (world == null) return Optional.empty();
        final int radius = Math.max(1, Math.min(8,
                configManager.getInt("pets.companion.spawn-search-radius", 4)));
        final int vertical = Math.max(0, Math.min(4,
                configManager.getInt("pets.companion.spawn-vertical-range", 2)));
        for (int ring = 1; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    for (int step = 0; step <= vertical * 2; step++) {
                        final int dy = step == 0 ? 0 : (step % 2 == 1 ? (step + 1) / 2 : -step / 2);
                        final int x = origin.getBlockX() + dx;
                        final int y = origin.getBlockY() + dy;
                        final int z = origin.getBlockZ() + dz;
                        final int chunkX = x >> 4;
                        final int chunkZ = z >> 4;
                        if (!world.isChunkLoaded(chunkX, chunkZ)
                                || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) continue;
                        final Location candidate = new Location(world, x + 0.5D, y, z + 0.5D,
                                origin.getYaw(), origin.getPitch());
                        if (world.getWorldBorder().isInside(candidate) && safePetStandingSpace(world, x, y, z)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean safePetStandingSpace(final World world, final int x, final int y, final int z) {
        if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) return false;
        final Block floor = world.getBlockAt(x, y - 1, z);
        final Material floorType = floor.getType();
        if (!floorType.isSolid() || floor.isLiquid() || floorType.hasGravity()
                || floorType == Material.POWDER_SNOW || floorType == Material.MAGMA_BLOCK
                || floorType == Material.CAMPFIRE || floorType == Material.SOUL_CAMPFIRE
                || floorType == Material.CACTUS || floorType == Material.SWEET_BERRY_BUSH
                || floorType == Material.WITHER_ROSE) return false;
        return clearPetBody(world.getBlockAt(x, y, z))
                && clearPetBody(world.getBlockAt(x, y + 1, z))
                && clearPetBody(world.getBlockAt(x, y + 2, z));
    }

    private static boolean clearPetBody(final Block block) {
        return block.isPassable() && !block.isLiquid()
                && block.getType() != Material.FIRE && block.getType() != Material.SOUL_FIRE;
    }

    private static final class PetSpawnException extends RuntimeException {
        private final String messageKey;

        private PetSpawnException(final String messageKey) {
            this(messageKey, null);
        }

        private PetSpawnException(final String messageKey, final Throwable cause) {
            super(messageKey, cause);
            this.messageKey = messageKey;
        }

        private String messageKey() {
            return messageKey;
        }
    }

}
