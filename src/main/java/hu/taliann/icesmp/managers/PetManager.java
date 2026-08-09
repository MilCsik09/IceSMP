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
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
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

/**
 * Companion pets for the Beast Master and Necromancer. The pet can be ANY mob,
 * obtained with a spec-specific capture item:
 * the Beast Master tames any non-hostile animal, the Necromancer binds any hostile
 * mob / undead. Type, level, XP, name and roster are durable Profile v2 state and
 * re-apply on summon. Tameable pets follow via vanilla; the rest are kept near the
 * owner by a teleport-follow tick. Levels come from the owner's nearby kills.
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

    private Optional<UUID> currentSessionToken(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        return gateway == null ? Optional.empty() : gateway.currentSessionToken(player.getUniqueId());
    }

    private boolean isCurrentSession(final Player player, final UUID sessionToken) {
        final ClassSpecProfileGateway gateway = profileGateway;
        return gateway != null && sessionToken != null
                && gateway.isCurrentSession(player.getUniqueId(), sessionToken);
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
                .thenCompose(result -> afterDurablePetMutation(player, sessionToken, result,
                        () -> { removeActive(player); adopt(mob, player); }));
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
        final int rosterCapacity=demonologist
                ?Math.max(1,configManager.getInt("classes.warlock.demonologist.roster-capacity",3)):0;
        if(rosterCapacity>0&&!ClassSpecCatalog.admitsCompanion(currentLoadout(player).orElse(null),namespace,rosterCapacity))
            return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-roster-full"));
        final UUID sessionToken=currentSessionToken(player).orElse(null);
        if(sessionToken==null)return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-session-unavailable"));
        final UUID logicalId=UUID.randomUUID();
        final CompanionProfile companion=new CompanionProfile(logicalId,namespace,form.name(),formName,1,0L,"",
                MinionManager.Stance.ACTIVE.name(),List.of(),0L,Map.of("ritual_summoned","true"));
        final String operationId="pet-ritual:"+logicalId;
        return gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                        ClassSpecProfileGateway.CompanionMutationRequest.Kind.ADD, logicalId, companion, "", 0,0L,0L,List.of(),Map.of(),rosterCapacity,operationId))
                .thenCompose(result -> afterDurablePetMutation(player,sessionToken,result,()->spawnAndAdopt(player,form)));
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
                .thenCompose(result -> afterDurablePetMutation(player, sessionToken, result,
                        () -> { removeActive(player); spawnAndAdopt(player, form); }));
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
                activeOwners.remove(player.getUniqueId());
                removeActive(player);
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
                .thenCompose(result -> afterDurablePetMutation(player, sessionToken, result,
                        () -> { removeActive(player); spawnAndAdopt(player, form); }));
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
        if (isUnholy(player) || isWarlock(player)) return CompletableFuture.completedFuture("pet-ritual-required");
        final Optional<LoadoutSlot> slot=activeSlot(player);
        if (slot.isEmpty()) return CompletableFuture.completedFuture("pet-none-captured");
        final ClassLoadout loadout=currentLoadout(player).orElse(null);
        if (loadout==null||loadout.companionRoster().isEmpty()) return CompletableFuture.completedFuture("pet-none-captured");
        final CompanionProfile selected=activeCompanion(player).orElseGet(()->loadout.companionRoster().values().stream()
                .sorted(java.util.Comparator.comparing(c->c.companionId().toString())).findFirst().orElseThrow());
        if (selected.resummonAtEpochMillis()>System.currentTimeMillis()) return CompletableFuture.completedFuture("pet-respawn-cooldown");
        final CompletionStage<ProfileMutationResult<hu.taliann.icesmp.classspec.application.ProfileDiagnostic>> durable;
        if (activeCompanion(player).isPresent()) durable=CompletableFuture.completedFuture(ProfileMutationResult.noChange(gateway().diagnostic(player.getUniqueId()),"already active"));
        else durable=gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                ClassSpecProfileGateway.CompanionMutationRequest.Kind.SET_ACTIVE,selected.companionId(),null,"",0,0L,0L,List.of(),Map.of(),"pet-summon:"+UUID.randomUUID()));
        final UUID sessionToken=currentSessionToken(player).orElse(null);
        if(sessionToken==null)return CompletableFuture.completedFuture("pet-session-unavailable");
        return durable.thenCompose(result->{
            if (!(result.committed()||result.status()==ProfileMutationResult.Status.NO_CHANGE)) return CompletableFuture.completedFuture("pet-persistence-failed");
            final CompletableFuture<String> completion=new CompletableFuture<>();
            runOnCurrentPlayer(player,sessionToken,()->{
                try { final EntityType type=entityType(selected.typeId()); if(type==null){completion.complete("pet-none-captured");return;} removeActive(player); spawnAndAdopt(player,type); completion.complete(null);}
                catch(Throwable failure){completion.complete("pet-runtime-retry");}
            },()->completion.complete("pet-stale-session"));
            return completion;
        });
    }

    public CompletionStage<Boolean> dismissV2(final Player player) {
        final Optional<LoadoutSlot> slot=activeSlot(player); final Optional<CompanionProfile> active=activeCompanion(player);
        if(slot.isEmpty()||active.isEmpty()) return CompletableFuture.completedFuture(false);
        final UUID sessionToken=currentSessionToken(player).orElse(null);
        if(sessionToken==null)return CompletableFuture.completedFuture(false);
        return gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                ClassSpecProfileGateway.CompanionMutationRequest.Kind.DISMISS,active.orElseThrow().companionId(),null,"",0,0L,0L,List.of(),Map.of(),"pet-dismiss:"+UUID.randomUUID()))
                .thenCompose(result->{
                    if(!result.committed())return CompletableFuture.completedFuture(false);
                    final CompletableFuture<Boolean> completion=new CompletableFuture<>();
                    runOnCurrentPlayer(player,sessionToken,()->{activeOwners.remove(player.getUniqueId());removeActive(player);completion.complete(true);},()->completion.complete(false));
                    return completion;
                });
    }

    /** Durable-first stable release: the roster entry is removed before any live-entity effect. */
    public CompletionStage<Boolean> releaseV2(final Player player) {
        final Optional<LoadoutSlot> slot = activeSlot(player);
        final Optional<CompanionProfile> active = activeCompanion(player);
        if (slot.isEmpty() || active.isEmpty()) return CompletableFuture.completedFuture(false);
        final UUID sessionToken = currentSessionToken(player).orElse(null);
        if (sessionToken == null) return CompletableFuture.completedFuture(false);
        final UUID companionId = active.orElseThrow().companionId();
        return gateway().mutateCompanion(player.getUniqueId(), companionRequest(slot.orElseThrow(),
                        ClassSpecProfileGateway.CompanionMutationRequest.Kind.REMOVE, companionId,
                        null, "", 0, 0L, 0L, List.of(), Map.of(), "pet-release:" + companionId))
                .thenCompose(result -> {
                    if (!result.committed()) return CompletableFuture.completedFuture(false);
                    final CompletableFuture<Boolean> completion = new CompletableFuture<>();
                    runOnCurrentPlayer(player, sessionToken, () -> {
                        activeOwners.remove(player.getUniqueId());
                        removeActive(player);
                        completion.complete(true);
                    }, () -> completion.complete(false));
                    return completion;
                });
    }

    public CompletionStage<Boolean> setNameV2(final Player player, final String name) {
        if(name==null||name.isBlank()||name.length()>24)return CompletableFuture.completedFuture(false);
        final UUID sessionToken=currentSessionToken(player).orElse(null);
        if(sessionToken==null)return CompletableFuture.completedFuture(false);
        return mutateActive(player,ClassSpecProfileGateway.CompanionMutationRequest.Kind.RENAME,name,0,0L,0L,List.of(),Map.of(),"pet-rename:"+UUID.randomUUID())
                .thenCompose(result->{
                    if(!result.committed())return CompletableFuture.completedFuture(false);
                    final CompletableFuture<Boolean> completion=new CompletableFuture<>();
                    runOnCurrentPlayer(player,sessionToken,()->{Mob pet=activePet(player);if(pet!=null)updateName(pet,player);completion.complete(true);},()->completion.complete(false));
                    return completion;
                });
    }

    public CompletionStage<Boolean> setStanceV2(final Player player, final MinionManager.Stance stance) {
        java.util.Objects.requireNonNull(stance,"stance");
        return mutateActive(player,ClassSpecProfileGateway.CompanionMutationRequest.Kind.STANCE,stance.name(),0,0L,0L,List.of(),Map.of(),"pet-stance:"+UUID.randomUUID()).thenApply(result -> result.committed());
    }

    public CompletionStage<MinionManager.Stance> cycleStanceV2(final Player player) {
        final MinionManager.Stance next=switch(getStance(player)){case ACTIVE->MinionManager.Stance.PASSIVE;case PASSIVE->MinionManager.Stance.STAY;case STAY->MinionManager.Stance.ACTIVE;};
        return setStanceV2(player,next).thenApply(committed->committed?next:null);
    }

    public CompletionStage<PetMutationResult> equipArmorV2(final Player player, final Entity clicked) {
        if(!canOwnPet(player))return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-not-allowed"));
        final Mob pet=activePet(player);if(pet==null||clicked==null||!pet.getUniqueId().equals(clicked.getUniqueId()))return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-armor-not-pet"));
        if(hasPetArmor(player))return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-armor-already"));
        final UUID sessionToken=currentSessionToken(player).orElse(null);
        if(sessionToken==null)return CompletableFuture.completedFuture(PetMutationResult.rejected("pet-session-unavailable"));
        return mutateActive(player,ClassSpecProfileGateway.CompanionMutationRequest.Kind.EQUIPMENT,"",0,0L,0L,List.of("pet_armor"),Map.of(),"pet-armor:"+UUID.randomUUID())
                .thenCompose(result->afterDurablePetMutation(player,sessionToken,result,()->applyEquipment(pet)));
    }

    public CompletionStage<Boolean> addXpV2(final Player player, final int amount, final String operationId) {
        if(amount<=0||!canOwnPet(player))return CompletableFuture.completedFuture(false);
        final CompanionProfile active=activeCompanion(player).orElse(null);if(active==null)return CompletableFuture.completedFuture(false);
        final int maxLevel=Math.max(1,configManager.getInt("pets.companion.max-level",30));
        int level=active.level();long xp=NumericGuards.addLong(active.experience(),amount,"companion experience");boolean leveled=false;
        while(level<maxLevel){final int cost=levelCost(level);if(xp<cost)break;xp-=cost;level=NumericGuards.addInt(level,1,"companion level");leveled=true;}
        final int committedLevel=level;final long committedXp=xp;final boolean didLevel=leveled;
        return mutateActive(player,ClassSpecProfileGateway.CompanionMutationRequest.Kind.PROGRESS,"",committedLevel,committedXp,0L,List.of(),Map.of(),operationId)
                .thenCompose(result->{
                    if(!result.committed())return CompletableFuture.completedFuture(false);
                    if(!didLevel)return CompletableFuture.completedFuture(true);
                    final UUID sessionToken=currentSessionToken(player).orElse(null);
                    if(sessionToken==null)return CompletableFuture.completedFuture(false);
                    final CompletableFuture<Boolean> completion=new CompletableFuture<>();
                    runOnCurrentPlayer(player,sessionToken,()->{if(committedLevel>=maxLevel)AdvancementService.award(player,"pet_bond");Mob pet=activePet(player);if(pet!=null){applyBuffs(pet,committedLevel,false);updateName(pet,player);}player.sendMessage(messageManager.getMessage("pet-level-up","<dark_green>🐾 A társad szintet lépett: <white>{level}</white></dark_green>",Map.of("level",String.valueOf(committedLevel))));completion.complete(true);},()->completion.complete(false));
                    return completion;
                });
    }

    /**
     * Handles a pet's death: clears the combat state for that pet, and if it was the
     * owner's active companion, clears the stored reference and notifies them. The
     * owner-side PDC/message runs on the owner's region thread (Folia-safe); call this
     * from an EntityDeathEvent for minion-tagged mobs.
     */
    public void handlePetDeath(final LivingEntity dead) {
        final UUID ownerId=minionManager.getOwner(dead);if(ownerId==null)return;final UUID deadId=dead.getUniqueId();attackReady.remove(deadId);
        final Player owner=Bukkit.getPlayer(ownerId);if(owner==null)return;
        final UUID sessionToken=currentSessionToken(owner).orElse(null);if(sessionToken==null)return;
        runOnCurrentPlayer(owner,sessionToken,()->{
            if(!deadId.equals(activePetEntities.get(ownerId)))return;
            final CompanionProfile companion=activeCompanion(owner).orElse(null);final Optional<LoadoutSlot> slot=activeSlot(owner);
            activeOwners.remove(ownerId);activePetEntities.remove(ownerId,deadId);combatTargets.remove(ownerId);
            petDeathHook.accept(ownerId);
            if(companion!=null&&slot.isPresent()){
                try {
                    final long seconds=Math.max(0L,configManager.getLong("pets.companion.death-respawn-seconds",120L));
                    final long cd=Math.multiplyExact(seconds,1000L);final long at=Math.addExact(System.currentTimeMillis(),cd);
                    gateway().mutateCompanion(ownerId,companionRequest(slot.orElseThrow(),ClassSpecProfileGateway.CompanionMutationRequest.Kind.RESPAWN_AT,companion.companionId(),null,"",0,0L,at,List.of(),Map.of(),"pet-death:"+deadId))
                            .exceptionally(failure->{plugin.getLogger().severe("Failed to persist pet death for "+ownerId+": "+failure.getMessage());return null;});
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
        final Mob pet = activePet(player);
        return pet != null && pet.getUniqueId().equals(clicked.getUniqueId());
    }

    public boolean hasActivePet(final Player player) {
        return activePet(player) != null;
    }

    public int nextLevelCost(final Player player) {
        return levelCost(getLevel(player));
    }

    public long respawnRemainingSeconds(final Player player) {
        final long at=activeCompanion(player).map(CompanionProfile::resummonAtEpochMillis).orElse(0L);
        final long now=System.currentTimeMillis();if(at<=now)return 0L;
        try{return Math.addExact(Math.subtractExact(at,now),999L)/1000L;}
        catch(ArithmeticException corrupt){gateway().blockSession(player.getUniqueId(),"Pet respawn timestamp overflow");return Long.MAX_VALUE;}
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
        return owner != null && canOwnPet(owner) && activePet(owner) != null;
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
        final int level = getLevel(owner);
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

    private void adopt(final Mob mob, final Player player) {
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
        final int buffLevel = getLevel(player)
                + (isSummonedPet(player) ? Math.max(0, configManager.getInt("pets.summon.bonus-levels", 5)) : 0);
        applyBuffs(mob, buffLevel, true);
        // A gazda max-health talentjei a PERMANENS társat is erősítik (a minionokkal
        // azonos megosztási arány — a két rendszer skálázása konzisztens).
        final hu.taliann.icesmp.managers.TalentManager talents = this.talentManagerRef;
        if (talents != null) {
            final double share = Math.max(0.0D, talents.getEffectTotal(player, "max-health")
                    * Math.max(0.0D, configManager.getDouble("pets.talent-health-share", 0.5D)));
            final org.bukkit.attribute.AttributeInstance hp =
                    mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (share > 0.0D && hp != null) {
                hp.setBaseValue(hp.getBaseValue() + share);
                mob.setHealth(hp.getValue());
            }
        }
        if (hasPetArmor(player)) {
            applyEquipment(mob);
            final AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                mob.setHealth(hp.getValue());
            }
        }
        updateName(mob, player);
        minionManager.tag(mob, player.getUniqueId());
        activePetEntities.put(player.getUniqueId(), mob.getUniqueId());
        activeOwners.add(player.getUniqueId());
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

    private void updateName(final Mob pet, final Player player) {
        pet.customName(Component.text(getName(player) + " [Lv " + getLevel(player) + "]", NamedTextColor.GREEN));
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
            activeOwners.remove(playerId);
            activePetEntities.remove(playerId);
            combatTargets.remove(playerId);
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

    /** Live active pet handle for same-call scheduler hops; may be null. */
    public Mob livePet(final Player player) {
        return activePet(player);
    }

    /** Owner-thread callback after the active companion's live entity died. */
    public void setPetDeathHook(final java.util.function.Consumer<UUID> hook) {
        petDeathHook = hook == null ? ignored -> { } : hook;
    }

    private Mob activePet(final Player player) {
        final UUID petId = activePetId(player);
        if (petId == null) {
            return null;
        }
        final Entity entity = Bukkit.getEntity(petId);
        return entity instanceof Mob mob && mob.isValid() ? mob : null;
    }

    private boolean removeActive(final Player player) {
        combatTargets.remove(player.getUniqueId());
        final UUID petId = activePetId(player);
        if (petId == null) {
            return false;
        }
        activePetEntities.remove(player.getUniqueId());
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

    private Optional<ClassSpecSection> currentProfile(final Player player) {
        final ClassSpecProfileGateway gateway=profileGateway;return gateway==null?Optional.empty():gateway.currentProfile(player.getUniqueId());
    }

    private Optional<LoadoutSlot> activeSlot(final Player player) { return currentProfile(player).map(ClassSpecSection::activeSlot); }
    private Optional<ClassLoadout> currentLoadout(final Player player) { return currentProfile(player).filter(p->p.activeSlot()!=null).map(p->p.loadout(p.activeSlot())); }
    private String activeSpec(final Player player) { return currentLoadout(player).map(ClassLoadout::specializationId).orElse(""); }

    private CompletionStage<ProfileMutationResult<hu.taliann.icesmp.classspec.application.ProfileDiagnostic>> mutateActive(
            final Player player, final ClassSpecProfileGateway.CompanionMutationRequest.Kind kind, final String text,
            final int level, final long experience, final long resummonAt, final List<String> equipment,
            final Map<String,String> state, final String operationId) {
        final Optional<LoadoutSlot> slot=activeSlot(player);final Optional<CompanionProfile> active=activeCompanion(player);
        if(slot.isEmpty()||active.isEmpty())return CompletableFuture.completedFuture(ProfileMutationResult.rejected(gateway().diagnostic(player.getUniqueId()),"no active companion"));
        return gateway().mutateCompanion(player.getUniqueId(),companionRequest(slot.orElseThrow(),kind,active.orElseThrow().companionId(),null,text,level,experience,resummonAt,equipment,state,operationId));
    }

    private static ClassSpecProfileGateway.CompanionMutationRequest companionRequest(LoadoutSlot slot,ClassSpecProfileGateway.CompanionMutationRequest.Kind kind,UUID id,CompanionProfile companion,String text,int level,long experience,long at,List<String> equipment,Map<String,String> state,String operationId){
        return companionRequest(slot,kind,id,companion,text,level,experience,at,equipment,state,0,operationId);
    }

    private static ClassSpecProfileGateway.CompanionMutationRequest companionRequest(LoadoutSlot slot,ClassSpecProfileGateway.CompanionMutationRequest.Kind kind,UUID id,CompanionProfile companion,String text,int level,long experience,long at,List<String> equipment,Map<String,String> state,int capacity,String operationId){
        return new ClassSpecProfileGateway.CompanionMutationRequest(slot,kind,id,companion,text,level,experience,at,equipment,state,capacity,operationId);
    }

    private CompletionStage<PetMutationResult> afterDurablePetMutation(final Player player,
            final UUID sessionToken, final ProfileMutationResult<?> result, final Runnable runtimeEffect) {
        if(!result.durableMutationApplied())return CompletableFuture.completedFuture(PetMutationResult.rejected(result.detail().isBlank()?"pet-persistence-failed":result.detail()));
        final CompletableFuture<PetMutationResult> completion=new CompletableFuture<>();
        runOnCurrentPlayer(player,sessionToken,()->{try{runtimeEffect.run();completion.complete(PetMutationResult.applied());}catch(Throwable failure){plugin.getLogger().severe("Durable pet mutation committed but runtime reconciliation failed for "+player.getUniqueId()+": "+failure.getMessage());completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-runtime-retry"));}},()->completion.complete(PetMutationResult.appliedWithRuntimeFailure("pet-stale-session")));
        return completion;
    }

    private EntityType entityType(final String raw) {
        if(raw==null||raw.isBlank())return null;try{final EntityType type=EntityType.valueOf(raw.toUpperCase(Locale.ROOT));return type.getEntityClass()!=null&&Mob.class.isAssignableFrom(type.getEntityClass())?type:null;}catch(IllegalArgumentException invalid){return null;}
    }

    private void spawnAndAdopt(final Player player, final EntityType type) {
        if(type==null||type.getEntityClass()==null||!Mob.class.isAssignableFrom(type.getEntityClass()))throw new IllegalArgumentException("Unsupported companion entity type");
        final Mob mob=(Mob)player.getWorld().spawn(player.getLocation(),type.getEntityClass().asSubclass(Mob.class));
        try{adopt(mob,player);}catch(Throwable failure){mob.remove();throw failure;}
    }

}
