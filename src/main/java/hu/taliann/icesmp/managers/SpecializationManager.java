package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.GameplayV2ClassPolicy;
import hu.taliann.icesmp.classspec.application.GateSnapshot;
import hu.taliann.icesmp.classspec.application.GateState;
import hu.taliann.icesmp.classspec.application.ProfileDiagnostic;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.archer.ArcherGameplayService;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.demonhunter.DemonHunterGameplayService;
import hu.taliann.icesmp.assassin.AssassinGameplayService;
import hu.taliann.icesmp.warlock.WarlockGameplayService;
import hu.taliann.icesmp.wizard.WizardGameplayService;
import hu.taliann.icesmp.deathknight.DeathKnightGameplayService;
import hu.taliann.icesmp.druid.DruidGameplayService;
import hu.taliann.icesmp.priest.PriestGameplayService;
import hu.taliann.icesmp.evoker.EvokerGameplayService;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.monk.MonkGameplayService;
import hu.taliann.icesmp.paladin.PaladinGameplayService;
import hu.taliann.icesmp.shaman.ShamanGameplayService;
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
    private static final String DEVASTATION_TRIAL = "evoker_devastation_trial";
    private static final String PRESERVATION_TRIAL = "evoker_preservation_trial";
    private static final String SHARPSHOOTER_TRIAL = "archer_sharpshooter_trial";
    private static final String BEAST_MASTER_TRIAL = "archer_beast_master_trial";
    private static final Map<String, String> TRIAL_SPECS = Map.ofEntries(
            Map.entry(BERSERKER_TRIAL, "berserker"),
            Map.entry(GUARDIAN_TRIAL, "guardian"),
            Map.entry(DEVASTATION_TRIAL, "devastation"),
            Map.entry(PRESERVATION_TRIAL, "preservation"),
            Map.entry(SHARPSHOOTER_TRIAL, "sharpshooter"),
            Map.entry(BEAST_MASTER_TRIAL, "beast_master"),
            Map.entry("shaman_elemental_trial", "elemental"),
            Map.entry("shaman_enhancement_trial", "enhancement"),
            Map.entry("shaman_tidal_trial", "tidal"),
            Map.entry("monk_windwalker_trial", "windwalker"),
            Map.entry("monk_brewmaster_trial", "brewmaster"),
            Map.entry("monk_mistweaver_trial", "mistweaver"),
            Map.entry("paladin_holy_trial", "holy"),
            Map.entry("paladin_retribution_trial", "retribution"),
            Map.entry("paladin_protection_trial", "protection"),
            Map.entry("demon_hunter_havoc_trial", "havoc"),
            Map.entry("demon_hunter_vengeance_trial", "vengeance"),
            Map.entry("druid_feral_trial", "feral"),
            Map.entry("druid_lunar_trial", "lunar"),
            Map.entry("druid_ironbark_trial", "ironbark"),
            Map.entry("druid_restoration_trial", "restoration"),
            Map.entry("priest_discipline_trial", "discipline"),
            Map.entry("priest_bone_priest_trial", "bone_priest"),
            Map.entry("priest_shadow_trial", "shadow"),
            Map.entry("death_knight_blood_trial", "blood"),
            Map.entry("death_knight_frost_trial", "frost"),
            Map.entry("death_knight_unholy_trial", "unholy"),
            Map.entry("assassin_poisoner_trial", "poisoner"),
            Map.entry("assassin_phantom_trial", "phantom"),
            Map.entry("assassin_plaguebringer_trial", "plaguebringer"),
            Map.entry("warlock_affliction_trial", "affliction"),
            Map.entry("warlock_destruction_trial", "destruction"),
            Map.entry("warlock_demonologist_trial", "demonologist"),
            Map.entry("wizard_elementalist_trial", "elementalist"),
            Map.entry("wizard_necromancer_trial", "necromancer"));

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
    private volatile EvokerGameplayService evokerGameplayService;
    private volatile ArcherGameplayService archerGameplayService;
    private volatile ShamanGameplayService shamanGameplayService;
    private volatile MonkGameplayService monkGameplayService;
    private volatile PaladinGameplayService paladinGameplayService;
    private volatile DemonHunterGameplayService demonHunterGameplayService;
    private volatile DruidGameplayService druidGameplayService;
    private volatile PriestGameplayService priestGameplayService;
    private volatile DeathKnightGameplayService deathKnightGameplayService;
    private volatile AssassinGameplayService assassinGameplayService;
    private volatile WarlockGameplayService warlockGameplayService;
    private volatile WizardGameplayService wizardGameplayService;
    private volatile CatalystItemFactory soulbondFactory;
    private volatile WorldBossManager worldBossManager;
    private volatile Consumer<UUID> classSwitchCleanup = ignored -> { };
    private volatile Consumer<Player> classProfileRefresh = ignored -> { };

    public void setWorldBossManager(final WorldBossManager worldBossManager) {
        this.worldBossManager = worldBossManager;
        final MonkGameplayService monk = monkGameplayService;
        final PaladinGameplayService paladin = paladinGameplayService;
        if (monk != null) monk.setWorldBossManager(worldBossManager);
        if (paladin != null) paladin.setWorldBossManager(worldBossManager);
    }

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
        final WarriorGameplayService warriorRuntime = new WarriorGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        final EvokerGameplayService evokerRuntime = new EvokerGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        final ArcherGameplayService archerRuntime = new ArcherGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        final ShamanGameplayService shamanRuntime = new ShamanGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        final MonkGameplayService monkRuntime = new MonkGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        final PaladinGameplayService paladinRuntime = new PaladinGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        if (worldBossManager != null) {
            monkRuntime.setWorldBossManager(worldBossManager);
            paladinRuntime.setWorldBossManager(worldBossManager);
        }
        final DemonHunterGameplayService demonHunterRuntime = new DemonHunterGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        plugin.getServer().getPluginManager().registerEvents(warriorRuntime, plugin);
        plugin.getServer().getPluginManager().registerEvents(evokerRuntime, plugin);
        plugin.getServer().getPluginManager().registerEvents(archerRuntime, plugin);
        plugin.getServer().getPluginManager().registerEvents(shamanRuntime, plugin);
        plugin.getServer().getPluginManager().registerEvents(monkRuntime, plugin);
        plugin.getServer().getPluginManager().registerEvents(paladinRuntime, plugin);
        final DruidGameplayService druidRuntime = new DruidGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        plugin.getServer().getPluginManager().registerEvents(demonHunterRuntime, plugin);
        final PriestGameplayService priestRuntime = new PriestGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        plugin.getServer().getPluginManager().registerEvents(druidRuntime, plugin);
        final DeathKnightGameplayService deathKnightRuntime = new DeathKnightGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        plugin.getServer().getPluginManager().registerEvents(priestRuntime, plugin);
        final AssassinGameplayService assassinRuntime = new AssassinGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        plugin.getServer().getPluginManager().registerEvents(deathKnightRuntime, plugin);
        final WarlockGameplayService warlockRuntime = new WarlockGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        plugin.getServer().getPluginManager().registerEvents(assassinRuntime, plugin);
        final WizardGameplayService wizardRuntime = new WizardGameplayService(
                plugin, configManager, jobManager, this, factory, messageManager);
        plugin.getServer().getPluginManager().registerEvents(warlockRuntime, plugin);
        plugin.getServer().getPluginManager().registerEvents(wizardRuntime, plugin);
        soulbondFactory = factory;
        warriorGameplayService = warriorRuntime;
        evokerGameplayService = evokerRuntime;
        archerGameplayService = archerRuntime;
        shamanGameplayService = shamanRuntime;
        monkGameplayService = monkRuntime;
        paladinGameplayService = paladinRuntime;
        demonHunterGameplayService = demonHunterRuntime;
        druidGameplayService = druidRuntime;
        priestGameplayService = priestRuntime;
        deathKnightGameplayService = deathKnightRuntime;
        assassinGameplayService = assassinRuntime;
        warlockGameplayService = warlockRuntime;
        wizardGameplayService = wizardRuntime;
        classSwitchCleanup = playerId -> {
            warriorRuntime.clearSpecializationState(playerId);
            evokerRuntime.clearSpecializationState(playerId);
            archerRuntime.clearSpecializationState(playerId);
            shamanRuntime.clearSpecializationState(playerId);
            monkRuntime.clearSpecializationState(playerId);
            paladinRuntime.clearSpecializationState(playerId);
            demonHunterRuntime.clearSpecializationState(playerId);
            druidRuntime.clearSpecializationState(playerId);
            priestRuntime.clearSpecializationState(playerId);
            deathKnightRuntime.clearSpecializationState(playerId);
            assassinRuntime.clearSpecializationState(playerId);
            warlockRuntime.clearSpecializationState(playerId);
            wizardRuntime.clearSpecializationState(playerId);
        };
        classProfileRefresh = this::scheduleClassProfileRefresh;
        jobManager.setXpChangeHook(player -> reconcileClassProgression(player)
                .exceptionally(failure -> {
                    plugin.getLogger().severe("Class level progression reconcile failed for "
                            + player.getUniqueId() + ": " + rootMessage(failure));
                    return null;
                }));
    }

    public Optional<WarriorGameplayService> warriorGameplayService() {
        return Optional.ofNullable(warriorGameplayService);
    }

    public Optional<EvokerGameplayService> evokerGameplayService() {
        return Optional.ofNullable(evokerGameplayService);
    }

    public Optional<ArcherGameplayService> archerGameplayService() {
        return Optional.ofNullable(archerGameplayService);
    }

    public Optional<ShamanGameplayService> shamanGameplayService() {
        return Optional.ofNullable(shamanGameplayService);
    }

    public Optional<MonkGameplayService> monkGameplayService() {
        return Optional.ofNullable(monkGameplayService);
    }

    public Optional<PaladinGameplayService> paladinGameplayService() {
        return Optional.ofNullable(paladinGameplayService);
    }

    public Optional<DemonHunterGameplayService> demonHunterGameplayService() {
        return Optional.ofNullable(demonHunterGameplayService);
    }

    public Optional<DruidGameplayService> druidGameplayService() {
        return Optional.ofNullable(druidGameplayService);
    }

    public Optional<PriestGameplayService> priestGameplayService() {
        return Optional.ofNullable(priestGameplayService);
    }

    public Optional<DeathKnightGameplayService> deathKnightGameplayService() {
        return Optional.ofNullable(deathKnightGameplayService);
    }

    public Optional<AssassinGameplayService> assassinGameplayService() {
        return Optional.ofNullable(assassinGameplayService);
    }

    public Optional<WarlockGameplayService> warlockGameplayService() {
        return Optional.ofNullable(warlockGameplayService);
    }

    public Optional<WizardGameplayService> wizardGameplayService() {
        return Optional.ofNullable(wizardGameplayService);
    }

    public boolean choosePriestLitany(final Player player, final String litanyId) {
        final PriestGameplayService runtime = priestGameplayService;
        return runtime != null && runtime.chooseLitany(player, litanyId);
    }

    public boolean choosePaladinOath(final Player player, final String oathId) {
        final PaladinGameplayService runtime = paladinGameplayService;
        return runtime != null && runtime.chooseOath(player, oathId);
    }

    public void setSwitchSafetyResource(final ResourceManager resources) {
        resourceManager = Objects.requireNonNull(resources, "resources");
    }

    public void setClassRuntimeCallbacks(final Consumer<UUID> switchCleanup,
                                         final Consumer<Player> profileRefresh) {
        classSwitchCleanup = switchCleanup == null ? ignored -> { } : switchCleanup;
        classProfileRefresh = profileRefresh == null ? ignored -> { } : profileRefresh;
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
                    if (success) classProfileRefresh.accept(player);
                    return success;
                });
    }

    public boolean canSwitchClassSpecialization(final Player player,
                                                final LoadoutSlot targetSlot) {
        if (player == null || targetSlot == null || !isGameplayV2Class(player)) return false;
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
                        classSwitchCleanup.accept(player.getUniqueId());
                        classProfileRefresh.accept(player);
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
            case DEVASTATION -> switch (level) {
                case 30 -> Set.of("gyujtopont", "hosszu_lelegzet");
                case 40 -> Set.of("iker_aram", "tulhevites");
                case 50 -> Set.of("orok_izzas", "kettos_szikra");
                default -> Set.of();
            };
            case PRESERVATION -> switch (level) {
                case 30 -> Set.of("hosszu_visszhang", "melyebb_visszhang");
                case 40 -> Set.of("idofonal", "gyors_lenyomat");
                case 50 -> Set.of("orzo_pajzs", "tiszta_ido");
                default -> Set.of();
            };
            case SHARPSHOOTER -> switch (level) {
                case 30 -> Set.of("nyugodt_kez", "gyors_felhuzas");
                case 40 -> Set.of("eles_szem", "mely_loves");
                case 50 -> Set.of("sorozat", "egy_loves_egy_elet");
                default -> Set.of();
            };
            case BEAST_MASTER -> switch (level) {
                case 30 -> Set.of("vadasz_osztone", "gondozo");
                case 40 -> Set.of("osszhang", "vastag_bor");
                case 50 -> Set.of("orok_kotelek", "falka_vezere");
                default -> Set.of();
            };
            case ELEMENTAL -> switch (level) {
                case 30 -> Set.of("mely_gyokerek", "eleven_szikra");
                case 40 -> Set.of("vihar_hirnoke", "tulcsordulas");
                case 50 -> Set.of("orok_rezonancia", "vihar_kegyeltje");
                default -> Set.of();
            };
            case ENHANCEMENT -> switch (level) {
                case 30 -> Set.of("surito_ritmus", "acel_zapor");
                case 40 -> Set.of("vihartorok", "foldrenges");
                case 50 -> Set.of("maelstrom_ura", "vihar_tanca");
                default -> Set.of();
            };
            case TIDAL -> switch (level) {
                case 30 -> Set.of("aramlat", "melyviz");
                case 40 -> Set.of("dagaly_ura", "apaly_ura");
                case 50 -> Set.of("szoko_ar", "eletado_veno");
                default -> Set.of();
            };
            case WINDWALKER -> switch (level) {
                case 30 -> Set.of("konnyed_lepes", "parducsap");
                case 40 -> Set.of("vihar_okle", "sarkany_lendulet");
                case 50 -> Set.of("derus_eg", "ezer_okol");
                default -> Set.of();
            };
            case BREWMASTER -> switch (level) {
                case 30 -> Set.of("surubb_fozet", "gyors_korty");
                case 40 -> Set.of("vas_bendo", "langlehelet");
                case 50 -> Set.of("celesztialis_nyugalom", "niuzao_oltalma");
                default -> Set.of();
            };
            case MISTWEAVER -> switch (level) {
                case 30 -> Set.of("friss_kod", "gyors_szoves");
                case 40 -> Set.of("melyebb_kod", "vedo_kod");
                case 50 -> Set.of("eletviraga", "szellemkod");
                default -> Set.of();
            };
            case HOLY -> switch (level) {
                case 30 -> Set.of("fenymeleg", "gyors_aldas");
                case 40 -> Set.of("orzo_fenye", "aldott_kez");
                case 50 -> Set.of("hajnal_ereje", "megvalto");
                default -> Set.of();
            };
            case RETRIBUTION -> switch (level) {
                case 30 -> Set.of("gyors_itelet", "buzgalom");
                case 40 -> Set.of("melto_harag", "itelet_sulya");
                case 50 -> Set.of("vegso_itelet", "szent_haboru");
                default -> Set.of();
            };
            case PROTECTION -> switch (level) {
                case 30 -> Set.of("acel_hit", "szent_fal");
                case 40 -> Set.of("kiterjesztett_fold", "rendithetetlen");
                case 50 -> Set.of("kiralyok_orzoje", "utolso_bastya");
                default -> Set.of();
            };
            case HAVOC -> switch (level) {
                case 30 -> Set.of("elso_vagas", "vadaszat");
                case 40 -> Set.of("lendulet_mestere", "tancos");
                case 50 -> Set.of("tulvilagi_lendulet", "vadaszat_ura");
                default -> Set.of();
            };
            case VENGEANCE -> switch (level) {
                case 30 -> Set.of("vastag_tuske", "olcso_pecset");
                case 40 -> Set.of("egeto_marka", "lelekvago");
                case 50 -> Set.of("pokoli_pusztitas", "demontuskek");
                default -> Set.of();
            };
            case FERAL -> switch (level) {
                case 30 -> Set.of("eles_karom", "ragadozo_osztone");
                case 40 -> Set.of("szagnyom_mestere", "gyors_marcangolas");
                case 50 -> Set.of("vad_hajsza_ura", "orok_uldozo");
                default -> Set.of();
            };
            case LUNAR -> switch (level) {
                case 30 -> Set.of("napkelte", "holdkelte");
                case 40 -> Set.of("csillagszem", "hosszu_egyuttallas");
                case 50 -> Set.of("orok_egyuttallas", "ket_egbolt");
                default -> Set.of();
            };
            case IRONBARK -> switch (level) {
                case 30 -> Set.of("vastag_kereg", "gyors_gyokerek");
                case 40 -> Set.of("tuskes_kereg", "melyre_nyulo_gyokerek");
                case 50 -> Set.of("oreg_tolgy", "gyokerek_ura");
                default -> Set.of();
            };
            case RESTORATION -> switch (level) {
                case 30 -> Set.of("korai_eres", "bo_vetes");
                case 40 -> Set.of("melyebb_gyoker", "gyors_viragzas");
                case 50 -> Set.of("orok_tavasz", "eletfa");
                default -> Set.of();
            };
            case DISCIPLINE -> switch (level) {
                case 30 -> Set.of("korai_kegyelem", "szeles_pajzs");
                case 40 -> Set.of("tarto_vezekles", "surubb_pajzs");
                case 50 -> Set.of("orok_kegyelem", "megvalto_szo");
                default -> Set.of();
            };
            case BONE_PRIEST -> switch (level) {
                case 30 -> Set.of("mely_velo", "csonttar");
                case 40 -> Set.of("gazdag_osszarium", "olcso_aldozat");
                case 50 -> Set.of("nema_kiralyno_kegye", "orok_csontfal");
                default -> Set.of();
            };
            case SHADOW -> switch (level) {
                case 30 -> Set.of("higgadt_elme", "mely_arnyek");
                case 40 -> Set.of("kuszob_mestere", "gyors_szorodas");
                case 50 -> Set.of("uresseg_ura", "tiszta_orulet");
                default -> Set.of();
            };
            case BLOOD -> switch (level) {
                case 30 -> Set.of("hosszu_emlekezet", "suru_ver");
                case 40 -> Set.of("melyebb_rovas", "gyors_verkor");
                case 50 -> Set.of("halal_jegye", "vertenger");
                default -> Set.of();
            };
            case FROST -> switch (level) {
                case 30 -> Set.of("dermeszto_kez", "jeges_szel");
                case 40 -> Set.of("toresvonal", "zuzmara");
                case 50 -> Set.of("jegpancel", "sindragosa_lehelete");
                default -> Set.of();
            };
            case UNHOLY -> switch (level) {
                case 30 -> Set.of("terjedo_kor", "savas_ver");
                case 40 -> Set.of("dus_kor", "pusztito_kor");
                case 50 -> Set.of("torz_ghul", "orok_jarvany");
                default -> Set.of();
            };
            case POISONER -> switch (level) {
                case 30 -> Set.of("hosszu_pillanat", "gyors_kever");
                case 40 -> Set.of("arnyekbol", "szeles_hatas");
                case 50 -> Set.of("melyebb_dozis", "halalos_fozet");
                default -> Set.of();
            };
            case PHANTOM -> switch (level) {
                case 30 -> Set.of("halk_lepes", "hosszu_nyom");
                case 40 -> Set.of("tiszta_visszhang", "kettos_lepes");
                case 50 -> Set.of("mely_arny", "kisertet");
                default -> Set.of();
            };
            case PLAGUEBRINGER -> switch (level) {
                case 30 -> Set.of("szivos_torzs", "gyors_lappangas");
                case 40 -> Set.of("szeles_jarvany", "mely_fertozes");
                case 50 -> Set.of("torzs_mestere", "fekete_halal");
                default -> Set.of();
            };
            case AFFLICTION -> switch (level) {
                case 30 -> Set.of("tarto_atok", "olcso_paktum");
                case 40 -> Set.of("eros_fonal", "mely_alku");
                case 50 -> Set.of("teher_biras", "gyors_torlesztes");
                default -> Set.of();
            };
            case DESTRUCTION -> switch (level) {
                case 30 -> Set.of("elenk_parazs", "szikra_ora");
                case 40 -> Set.of("hideg_kez", "robbano_mag");
                case 50 -> Set.of("mely_zsarat", "tuzvihar");
                default -> Set.of();
            };
            case DEMONOLOGIST -> switch (level) {
                case 30 -> Set.of("hu_szolga", "gyors_hivas");
                case 40 -> Set.of("vasbor", "legios_rend");
                case 50 -> Set.of("orok_paktum", "nagy_legio");
                default -> Set.of();
            };
            case ELEMENTALIST -> switch (level) {
                case 30 -> Set.of("gyors_rahangolodas", "hosszu_visszacsatolas");
                case 40 -> Set.of("mely_szoves", "elemi_egyensuly");
                case 50 -> Set.of("konnyu_korona", "arkan_ura");
                default -> Set.of();
            };
            case NECROMANCER -> switch (level) {
                case 30 -> Set.of("nagyobb_udvar", "hu_holtak");
                case 40 -> Set.of("csontkiraly", "lelekaratas");
                case 50 -> Set.of("orok_udvar", "halalmester");
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
                || !isGameplayV2Class(player)
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
                    if (success) classProfileRefresh.accept(player);
                    return success;
                });
    }

    public CompletionStage<Boolean> contributeWarriorMastery(final Player player,
                                                             final int experience) {
        return contributeClassMastery(player, JobType.WARRIOR, experience);
    }

    /** 50+ mastery is Profile v2 loadout state; only the active spec of the same class earns it. */
    public CompletionStage<Boolean> contributeClassMastery(final Player player,
                                                           final JobType classType,
                                                           final int experience) {
        if (player == null || classType == null || experience <= 0
                || !GameplayV2ClassPolicy.isEnabled(classType.getId())
                || jobManager.getPrimaryJob(player) != classType
                || jobManager.getPrimaryLevel(player) < 50) {
            return CompletableFuture.completedFuture(false);
        }
        final ClassSpecSection profile = profileGateway().currentProfile(player.getUniqueId())
                .orElse(null);
        if (profile == null || profile.activeSlot() == null
                || !ClassSpecCatalog.belongsTo(
                profile.loadout(profile.activeSlot()).specializationId(), classType.getId())) {
            return CompletableFuture.completedFuture(false);
        }
        final long perRank = Math.max(1L, configManager.getLong(
                "classes." + classType.getId() + ".mastery.experience-per-rank", 100L));
        return profileGateway().contributeMastery(player.getUniqueId(),
                        new ClassSpecProfileGateway.MasteryContributionRequest(
                                profile.activeSlot(), experience, perRank))
                .thenApply(result -> {
                    if (result.committed()) classProfileRefresh.accept(player);
                    return result.committed();
                });
    }

    public CompletionStage<Void> reconcileClassProgression(final Player player) {
        final JobType job = player == null ? null : jobManager.getPrimaryJob(player);
        if (job == null || !GameplayV2ClassPolicy.isEnabled(job.getId())
                || jobManager.getPrimaryLevel(player) < 50) {
            return CompletableFuture.completedFuture(null);
        }
        final ClassSpecSection profile = profileGateway().currentProfile(player.getUniqueId())
                .orElse(null);
        if (profile == null) return CompletableFuture.completedFuture(null);
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final LoadoutSlot slot : LoadoutSlot.values()) {
            final ClassLoadout loadout = profile.loadout(slot);
            if (!ClassSpecCatalog.belongsTo(loadout.specializationId(), job.getId())
                    || loadout.capstoneStatus() != CapstoneStatus.LOCKED) continue;
            chain = chain.thenCompose(ignored -> profileGateway().setCapstone(
                            player.getUniqueId(),
                            new ClassSpecProfileGateway.CapstoneRequest(
                                    slot, CapstoneStatus.AVAILABLE))
                    .thenApply(result -> null));
        }
        return chain.thenRun(() -> classProfileRefresh.accept(player));
    }

    public CompletionStage<Boolean> onQuestCompleted(final Player player,
                                                     final String questId) {
        if (player == null || questId == null
                || jobManager.getPrimaryLevel(player) < 50) {
            return CompletableFuture.completedFuture(false);
        }
        final String requiredSpec = TRIAL_SPECS.getOrDefault(
                questId.toLowerCase(Locale.ROOT), "");
        final JobType job = jobManager.getPrimaryJob(player);
        if (requiredSpec.isEmpty() || job == null
                || !ClassSpecCatalog.belongsTo(requiredSpec, job.getId())) {
            return CompletableFuture.completedFuture(false);
        }
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
                        classProfileRefresh.accept(player);
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
                        classSwitchCleanup.accept(player.getUniqueId());
                        classProfileRefresh.accept(player);
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
                .thenCompose(result -> reconcileCompletedTrials(player)
                        .thenApply(ignored -> result));
    }

    private CompletionStage<Void> reconcileCompletedTrials(final Player player) {
        final JobType job = player == null ? null : jobManager.getPrimaryJob(player);
        if (job == null || !GameplayV2ClassPolicy.isEnabled(job.getId())) {
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final Map.Entry<String, String> trial : TRIAL_SPECS.entrySet()) {
            if (!ClassSpecCatalog.belongsTo(trial.getValue(), job.getId())
                    || !questManager.hasCompleted(player, trial.getKey())) continue;
            final String questId = trial.getKey();
            chain = chain.thenCompose(ignored -> onQuestCompleted(player, questId)
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

    private void scheduleClassProfileRefresh(final Player player) {
        hu.taliann.icesmp.itemization.EquipmentProficiencyService
                .reconcileAfterClassChange(player);
        if (player == null) return;
        player.getScheduler().run(plugin, task -> refreshClassProfile(player), null);
    }

    private void refreshClassProfile(final Player player) {
        final WarriorGameplayService warriorRuntime = warriorGameplayService;
        final EvokerGameplayService evokerRuntime = evokerGameplayService;
        final ArcherGameplayService archerRuntime = archerGameplayService;
        final ShamanGameplayService shamanRuntime = shamanGameplayService;
        final MonkGameplayService monkRuntime = monkGameplayService;
        final PaladinGameplayService paladinRuntime = paladinGameplayService;
        final DemonHunterGameplayService demonHunterRuntime = demonHunterGameplayService;
        final DruidGameplayService druidRuntime = druidGameplayService;
        final PriestGameplayService priestRuntime = priestGameplayService;
        final DeathKnightGameplayService deathKnightRuntime = deathKnightGameplayService;
        final AssassinGameplayService assassinRuntime = assassinGameplayService;
        final WarlockGameplayService warlockRuntime = warlockGameplayService;
        final WizardGameplayService wizardRuntime = wizardGameplayService;
        final CatalystItemFactory factory = soulbondFactory;
        if (warriorRuntime != null) warriorRuntime.reconcileProfile(player);
        if (evokerRuntime != null) evokerRuntime.reconcileProfile(player);
        if (archerRuntime != null) archerRuntime.reconcileProfile(player);
        if (shamanRuntime != null) shamanRuntime.reconcileProfile(player);
        if (monkRuntime != null) monkRuntime.reconcileProfile(player);
        if (paladinRuntime != null) paladinRuntime.reconcileProfile(player);
        if (demonHunterRuntime != null) demonHunterRuntime.reconcileProfile(player);
        if (druidRuntime != null) druidRuntime.reconcileProfile(player);
        if (priestRuntime != null) priestRuntime.reconcileProfile(player);
        if (deathKnightRuntime != null) deathKnightRuntime.reconcileProfile(player);
        if (assassinRuntime != null) assassinRuntime.reconcileProfile(player);
        if (warlockRuntime != null) warlockRuntime.reconcileProfile(player);
        if (wizardRuntime != null) wizardRuntime.reconcileProfile(player);
        final JobType job = jobManager.getPrimaryJob(player);
        if (factory == null || job == null || !GameplayV2ClassPolicy.isEnabled(job.getId())) return;
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
            if (factory.isPersonalCopyFor(stack, player.getUniqueId(), job)) {
                factory.refreshPresentation(stack, player.getUniqueId(), job,
                        spec, profile.classLevel(), mastery, doctrines);
            }
        }
    }

    private boolean isGameplayV2Class(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        return job != null && GameplayV2ClassPolicy.isEnabled(job.getId());
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
