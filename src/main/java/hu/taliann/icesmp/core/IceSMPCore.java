package hu.taliann.icesmp.core;

import hu.taliann.icesmp.classspec.compat.ClassSpecDependencyPreflight;
import hu.taliann.icesmp.commands.BankCommand;
import hu.taliann.icesmp.commands.BountyCommand;
import hu.taliann.icesmp.commands.CurrencyCommand;
import hu.taliann.icesmp.commands.DailyCommand;
import hu.taliann.icesmp.commands.FactionCommand;
import hu.taliann.icesmp.commands.IceSMPCommand;
import hu.taliann.icesmp.commands.JobCommand;
import hu.taliann.icesmp.commands.LeaderboardCommand;
import hu.taliann.icesmp.commands.AchievementsCommand;
import hu.taliann.icesmp.commands.DonationChestCommand;
import hu.taliann.icesmp.commands.EventsCommand;
import hu.taliann.icesmp.commands.ExchangeBoardCommand;
import hu.taliann.icesmp.commands.MarketCommand;
import hu.taliann.icesmp.commands.MenuCommand;
import hu.taliann.icesmp.commands.NpcBindCommand;
import hu.taliann.icesmp.commands.PetCommand;
import hu.taliann.icesmp.commands.ParkourCommand;
import hu.taliann.icesmp.commands.ProfessionCommand;
import hu.taliann.icesmp.commands.ProfileCommand;
import hu.taliann.icesmp.commands.QuestCommand;
import hu.taliann.icesmp.commands.RelicCommand;
import hu.taliann.icesmp.commands.SinnerCommand;
import hu.taliann.icesmp.commands.SoulCommand;
import hu.taliann.icesmp.commands.SpellCommand;
import hu.taliann.icesmp.commands.SpellbookCommand;
import hu.taliann.icesmp.commands.SpecCommand;
import hu.taliann.icesmp.commands.TalentCommand;
import hu.taliann.icesmp.commands.TerritoryCommand;
import hu.taliann.icesmp.gui.CharacterMenuContext;
import hu.taliann.icesmp.gui.CommandMenuContext;
import hu.taliann.icesmp.gui.ProfileGUI;
import hu.taliann.icesmp.factions.FactionMobContextResolver;
import hu.taliann.icesmp.factions.FactionPassiveConfig;
import hu.taliann.icesmp.factions.FactionPassivePolicy;
import hu.taliann.icesmp.factions.FactionPassiveService;
import hu.taliann.icesmp.items.CaptureItemFactory;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.items.SiegeWeaponFactory;
import hu.taliann.icesmp.listeners.CatalystCraftSafetyListener;
import hu.taliann.icesmp.listeners.CharacterGUIListener;
import hu.taliann.icesmp.listeners.CommandMenuListener;
import hu.taliann.icesmp.listeners.ClassXpListener;
import hu.taliann.icesmp.listeners.HudListener;
import hu.taliann.icesmp.listeners.CurrencyCraftListener;
import hu.taliann.icesmp.listeners.CurrencyItemRefreshListener;
import hu.taliann.icesmp.listeners.DailyQuestListener;
import hu.taliann.icesmp.listeners.DonationChestListener;
import hu.taliann.icesmp.listeners.ElytraRelicListener;
import hu.taliann.icesmp.listeners.FactionPassiveListener;
import hu.taliann.icesmp.listeners.FactionSpawnListener;
import hu.taliann.icesmp.listeners.IntroListener;
import hu.taliann.icesmp.listeners.JobCraftRestrictionListener;
import hu.taliann.icesmp.listeners.JobGUIListener;
import hu.taliann.icesmp.listeners.MarketDeliveryListener;
import hu.taliann.icesmp.listeners.MarketGUIListener;
import hu.taliann.icesmp.listeners.MetelytepoRelicListener;
import hu.taliann.icesmp.listeners.MinionProtectionListener;
import hu.taliann.icesmp.listeners.PetCaptureListener;
import hu.taliann.icesmp.listeners.PetCombatListener;
import hu.taliann.icesmp.listeners.PetCommandListener;
import hu.taliann.icesmp.listeners.PetXpListener;
import hu.taliann.icesmp.listeners.MobScalingListener;
import hu.taliann.icesmp.listeners.PlayerSessionCleanupListener;
import hu.taliann.icesmp.listeners.ProfessionRecipeListener;
import hu.taliann.icesmp.listeners.ParkourListener;
import hu.taliann.icesmp.listeners.ProfessionXpListener;
import hu.taliann.icesmp.listeners.QuestProgressListener;
import hu.taliann.icesmp.listeners.RelicCraftSafetyListener;
import hu.taliann.icesmp.listeners.RelicInactivityListener;
import hu.taliann.icesmp.listeners.RelicItemRefreshListener;
import hu.taliann.icesmp.listeners.RelicPvpTransferListener;
import hu.taliann.icesmp.listeners.RelicTriggerListener;
import hu.taliann.icesmp.listeners.RitualListener;
import hu.taliann.icesmp.listeners.SiegeWeaponListener;
import hu.taliann.icesmp.listeners.SkillTreeGUIListener;
import hu.taliann.icesmp.listeners.SoulShardListener;
import hu.taliann.icesmp.listeners.SoulstoneListener;
import hu.taliann.icesmp.listeners.WorldBossListener;
import hu.taliann.icesmp.listeners.SinListener;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.listeners.SpellbookListener;
import hu.taliann.icesmp.listeners.SpellProjectileListener;
import hu.taliann.icesmp.listeners.SpellStateListener;
import hu.taliann.icesmp.listeners.TalentAttributeListener;
import hu.taliann.icesmp.listeners.TerritoryListener;
import hu.taliann.icesmp.listeners.TerritoryProtectionListener;
import hu.taliann.icesmp.listeners.TheftListener;
import hu.taliann.icesmp.managers.BlockRegenService;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.CommunityGoalManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ConfigValidator;
import hu.taliann.icesmp.managers.ShopManager;
import hu.taliann.icesmp.managers.CaravanManager;
import hu.taliann.icesmp.managers.AmbientEventManager;
import hu.taliann.icesmp.managers.GatheringBuffManager;
import hu.taliann.icesmp.managers.TreasureEventManager;
import hu.taliann.icesmp.managers.WildHuntManager;
import hu.taliann.icesmp.managers.AbundanceManager;
import hu.taliann.icesmp.managers.ServerChallengeManager;
import hu.taliann.icesmp.managers.EscortManager;
import hu.taliann.icesmp.managers.MeteorEventManager;
import hu.taliann.icesmp.managers.PartyManager;
import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.managers.CraftingRestrictionManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.DailyQuestManager;
import hu.taliann.icesmp.managers.DonationChestManager;
import hu.taliann.icesmp.managers.EconomyEventManager;
import hu.taliann.icesmp.managers.IntroManager;
import hu.taliann.icesmp.managers.InvasionManager;
import hu.taliann.icesmp.managers.ExchangeBoardManager;
import hu.taliann.icesmp.managers.ExchangeRateService;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.FactionRelationManager;
import hu.taliann.icesmp.managers.FactionTreasuryManager;
import hu.taliann.icesmp.managers.HudManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.managers.MarketManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.MinionManager;
import hu.taliann.icesmp.managers.NpcBindingManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.managers.ParkourManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ItemRarityService;
import hu.taliann.icesmp.managers.ProfessionRecipeManager;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.managers.RitualManager;
import hu.taliann.icesmp.managers.SeasonManager;
import hu.taliann.icesmp.managers.SoulShardManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellMasteryManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.managers.AchievementManager;
import hu.taliann.icesmp.managers.TalentManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.managers.TerritoryProtectionService;
import hu.taliann.icesmp.managers.WorldBossManager;
import hu.taliann.icesmp.spells.AngryChickenSpell;
import hu.taliann.icesmp.spells.ArmamentSpell;
import hu.taliann.icesmp.spells.BoneChillSpell;
import hu.taliann.icesmp.spells.BulwarkSpell;
import hu.taliann.icesmp.spells.ConfiguredSpell;
import hu.taliann.icesmp.spells.ConfusionSpell;
import hu.taliann.icesmp.spells.DoubleJumpSpell;
import hu.taliann.icesmp.spells.EagleEyeSpell;
import hu.taliann.icesmp.spells.FeastSpell;
import hu.taliann.icesmp.spells.FeatherfootSpell;
import hu.taliann.icesmp.spells.FriendshipSpell;
import hu.taliann.icesmp.spells.GustSpell;
import hu.taliann.icesmp.spells.HideSpell;
import hu.taliann.icesmp.spells.InnerFocusSpell;
import hu.taliann.icesmp.spells.LifeDrainSpell;
import hu.taliann.icesmp.spells.LuckyStarSpell;
import hu.taliann.icesmp.spells.MultishotSpell;
import hu.taliann.icesmp.spells.RainDanceSpell;
import hu.taliann.icesmp.spells.RootSpell;
import hu.taliann.icesmp.spells.ShadowstepSpell;
import hu.taliann.icesmp.spells.SmokeBombSpell;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.spells.SpellCatalog;
import hu.taliann.icesmp.spells.SunDanceSpell;
import hu.taliann.icesmp.spells.VenomStrikeSpell;
import hu.taliann.icesmp.spells.WisplightSpell;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.PersistentStoreCoordinator;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Core initialization and management for the IceSMP plugin.
 * Handles lifecycle, manager initialization, event listener registration,
 * and command registration.
 */
public final class IceSMPCore {

    private final JavaPlugin plugin;
    private final Runnable resourcePackReloadHook;
    private final Predicate<UUID> resourcePackReady;
    private final ConfigManager configManager;
    private final ClassSpecDependencyPreflight classSpecDependencyPreflight;
    private final MessageManager messageManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final FactionPassiveConfig factionPassiveConfig;
    private final FactionPassivePolicy factionPassivePolicy;
    private final FactionPassiveService factionPassiveService;
    private final FactionMobContextResolver factionMobContextResolver;
    private final FactionPassiveListener factionPassiveListener;
    private final JobManager jobManager;
    private final SpellRegistry spellRegistry;
    private final CatalystItemFactory catalystItemFactory;
    private final hu.taliann.icesmp.managers.ResourceManager resourceManager;
    private final hu.taliann.icesmp.managers.SpellFavoritesManager spellFavoritesManager;
    private final AbilityCatalystListener abilityCatalystListener;
    private final hu.taliann.icesmp.listeners.QuestBuilderListener questBuilderListener;
    private final SpellMasteryManager spellMasteryManager;
    private final PlayerSessionCleanupListener playerSessionCleanupListener;
    private final hu.taliann.icesmp.client.IceSmpClientBridge clientBridge;
    private final RelicManager relicManager;
    private final MetelytepoManager metelytepoManager;
    private final SinManager sinManager;
    private final MinionManager minionManager;
    private final hu.taliann.icesmp.managers.TotemManager totemManager;
    private final hu.taliann.icesmp.pve.MobAbilityRegistry mobAbilityRegistry;
    private final hu.taliann.icesmp.pve.CreatureSpeciesRegistry creatureSpeciesRegistry;
    private final hu.taliann.icesmp.pve.MobTemplateRegistry mobTemplateRegistry;
    private final MobScalingManager mobScalingManager;
    private final hu.taliann.icesmp.pve.MobAbilityRuntime mobAbilityRuntime;
    private final hu.taliann.icesmp.pve.AuthoredCreatureSpawnService authoredCreatureSpawns;
    private final hu.taliann.icesmp.pve.CreatureProfileService creatureProfileService;
    private final InvasionManager invasionManager;
    private final CaptureItemFactory captureItemFactory;
    private final PetManager petManager;
    private final DailyQuestManager dailyQuestManager;
    private final ParkourManager parkourManager;
    private final ProfessionManager professionManager;
    private final ProfessionRecipeManager professionRecipeManager;
    private final ItemRarityService itemRarityService;
    private final hu.taliann.icesmp.itemization.ItemTemplateRegistry itemTemplateRegistry;
    private final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService;
    private final hu.taliann.icesmp.itemization.EquipmentProficiencyService equipmentProficiencyService;
    private final hu.taliann.icesmp.itemization.ItemTransformationPolicy itemTransformationPolicy;
    private final hu.taliann.icesmp.itemization.ItemMutationCoordinator itemMutationCoordinator;
    private final hu.taliann.icesmp.gui.ItemForgeGUI itemForgeGUI;
    private final hu.taliann.icesmp.managers.ProfessionRecipeCatalog professionRecipeCatalog;
    private final hu.taliann.icesmp.items.BlueprintItemFactory blueprintItemFactory;
    private final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterialFactory;
    private final hu.taliann.icesmp.pve.EncounterRewardDeliveryService encounterRewardDelivery;
    private final hu.taliann.icesmp.pve.EquippedCombatPowerService equippedCombatPowerService;
    private final hu.taliann.icesmp.items.MoneyPouchItemFactory moneyPouchItemFactory;
    private final hu.taliann.icesmp.managers.DevItemManager devItemManager;
    private final hu.taliann.icesmp.managers.GuildManager guildManager;
    private final hu.taliann.icesmp.managers.PlayerCaravanManager playerCaravanManager;
    private final hu.taliann.icesmp.managers.BestiaryManager bestiaryManager;
    private final hu.taliann.icesmp.managers.SoulforgeManager soulforgeManager;
    private final hu.taliann.icesmp.playerprofile.integration.PlayerProfilePlatform playerProfilePlatform;
    private final hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority playerProfileAuthority;
    private final hu.taliann.icesmp.classspec.persistence.PlayerProfileClassSpecSectionRepository classSpecSectionRepository;
    private final hu.taliann.icesmp.classspec.application.ProfileSessionRegistry profileSessionRegistry;
    private final hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway classSpecProfileGateway;
    private final hu.taliann.icesmp.classspec.application.ClassSpecSectionLifecycleService classSpecSectionLifecycleService;
    private final hu.taliann.icesmp.classspec.integration.BukkitClassSpecRuntimeAdapter classSpecRuntimeAdapter;
    private final hu.taliann.icesmp.classspec.integration.BukkitClassSpecSectionSessionBridge profileSessionBridge;
    private final hu.taliann.icesmp.managers.ResourceBonusService resourceBonusService;
    private final hu.taliann.icesmp.classrelic.ClassRelicService classRelicService;
    private final hu.taliann.icesmp.managers.HonorDuelManager honorDuelManager;
    private final hu.taliann.icesmp.managers.WarWindowManager warWindowManager;
    private final hu.taliann.icesmp.managers.CombatTagManager combatTagManager;
    private final hu.taliann.icesmp.managers.DungeonLootService dungeonLootService;
    private final hu.taliann.icesmp.managers.ClassHealthService classHealthService;
    private final hu.taliann.icesmp.managers.CouncilManager councilManager;
    private final hu.taliann.icesmp.managers.SpyManager spyManager;
    private final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager professionWeeklyGoalManager;
    private final hu.taliann.icesmp.managers.HolidayService holidayService;
    private final hu.taliann.icesmp.managers.CityGuardManager cityGuardManager;
    private final hu.taliann.icesmp.managers.DarkUndeadAmbienceManager darkUndeadAmbienceManager;
    private final hu.taliann.icesmp.managers.EventSpawnPointManager eventSpawnPointManager;
    private final hu.taliann.icesmp.managers.FerryManager ferryManager;
    private final hu.taliann.icesmp.managers.CultistEventManager cultistEventManager;
    private final hu.taliann.icesmp.listeners.ProfessionRecipeBookListener professionRecipeBookListener;
    private final hu.taliann.icesmp.listeners.FactionFoodListener factionFoodListener;
    private final hu.taliann.icesmp.managers.WhisperManager whisperManager;
    private final hu.taliann.icesmp.managers.ChronicleManager chronicleManager;
    private final hu.taliann.icesmp.managers.CorruptionManager corruptionManager;
    private final hu.taliann.icesmp.listeners.CorruptionAuraListener corruptionAuraListener;
    private final hu.taliann.icesmp.listeners.LowHealthBorderListener lowHealthBorderListener;
    private final hu.taliann.icesmp.managers.AdvancementService advancementService;
    private final hu.taliann.icesmp.managers.SeasonFinaleManager seasonFinaleManager;
    private final hu.taliann.icesmp.managers.StrangerNpcManager strangerNpcManager;
    private final hu.taliann.icesmp.managers.BardManager bardManager;
    private final hu.taliann.icesmp.managers.BuyerService buyerService;
    private final hu.taliann.icesmp.managers.SeasonMonumentManager seasonMonumentManager;
    private final hu.taliann.icesmp.managers.CursedGearService cursedGearService;
    private final hu.taliann.icesmp.managers.HiddenSpotManager hiddenSpotManager;
    private final hu.taliann.icesmp.managers.ArcheologyManager archeologyManager;
    private final CraftingRestrictionManager craftingRestrictionManager;
    private final ExchangeRateService exchangeRateService;
    private final EconomyEventManager economyEventManager;
    private final FactionRelationManager factionRelationManager;
    private final MarketManager marketManager;
    private final DonationChestManager donationChestManager;
    private final QuestManager questManager;
    private final CommunityGoalManager communityGoalManager;
    private final ShopManager shopManager;
    private final NpcBindingManager npcBindingManager;
    private final CaravanManager caravanManager;
    private final AmbientEventManager ambientEventManager;
    private final GatheringBuffManager gatheringBuffManager;
    private final TreasureEventManager treasureEventManager;
    private final WildHuntManager wildHuntManager;
    private final AbundanceManager abundanceManager;
    private final ServerChallengeManager serverChallengeManager;
    private final EscortManager escortManager;
    private final MeteorEventManager meteorEventManager;
    private final PartyManager partyManager;
    private final ClaimManager claimManager;
    private final hu.taliann.icesmp.managers.EventSpawnGuard eventSpawnGuard;
    private final SpecializationManager specializationManager;
    private final TalentManager talentManager;
    private final TerritoryManager territoryManager;
    private final TerritoryProtectionService territoryProtectionService;
    private final FactionTreasuryManager factionTreasuryManager;
    private final KingManager kingManager;
    private final RaidManager raidManager;
    private final BloodMoonManager bloodMoonManager;
    private final SeasonManager seasonManager;
    private final WorldBossManager worldBossManager;
    private final IntroManager introManager;
    private final SiegeWeaponFactory siegeWeaponFactory;
    private final SoulShardManager soulShardManager;
    private final RitualManager ritualManager;
    private final ExchangeBoardManager exchangeBoardManager;
    private final hu.taliann.icesmp.managers.CrownCurseManager crownCurseManager;
    private final hu.taliann.icesmp.managers.RespecService respecService;
    private final CharacterMenuContext characterMenuContext;
    private final CommandMenuContext commandMenuContext;
    private final HudManager hudManager;
    private final List<PersistentStore> persistentStores;
    private final PersistentStoreCoordinator storeCoordinator;
    private volatile boolean enableCompleted;
    private final StatsManager statsManager;
    private final AchievementManager achievementManager;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask taxTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask questNpcMarkerTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask questNpcValidationTask;
    private hu.taliann.icesmp.integration.FancyNpcsQuestBridge npcQuestBridge;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask economyEventTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask worldEventsTask;
    private final BlockRegenService blockRegenService;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask hudTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask survivalHudTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask tablistTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask healthTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask corruptionAuraTask;
    private final hu.taliann.icesmp.utils.TextAnimator textAnimator;
    private final hu.taliann.icesmp.managers.TablistManager tablistManager;
    private final hu.taliann.icesmp.managers.AfkManager afkManager;
    private final hu.taliann.icesmp.managers.SitManager sitManager;
    private final hu.taliann.icesmp.items.CrateKeyFactory crateKeyFactory;
    private final hu.taliann.icesmp.managers.CrateManager crateManager;
    private final hu.taliann.icesmp.managers.ReportManager reportManager;
    private final hu.taliann.icesmp.managers.ModerationManager moderationManager;
    private final hu.taliann.icesmp.managers.VanishManager vanishManager;
    private final hu.taliann.icesmp.listeners.MotdListener motdListener;
    private final hu.taliann.icesmp.managers.InvseeManager invseeManager;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask petTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask moderationExpiryTask;

    public IceSMPCore(final JavaPlugin plugin) {
        this(plugin, () -> { }, ignored -> true);
    }

    public IceSMPCore(final JavaPlugin plugin, final Runnable resourcePackReloadHook) {
        this(plugin, resourcePackReloadHook, ignored -> true);
    }

    public IceSMPCore(final JavaPlugin plugin, final Runnable resourcePackReloadHook,
                      final Predicate<UUID> resourcePackReady) {
        this.plugin = plugin;
        this.resourcePackReloadHook = resourcePackReloadHook == null ? () -> { } : resourcePackReloadHook;
        this.resourcePackReady = resourcePackReady == null ? ignored -> false : resourcePackReady;
        this.configManager = new ConfigManager(plugin);
        // A config MÁR A KONSTRUKTOR-LÁNC ELŐTT betöltődik: több world-event manager a saját
        // konstruktorában számol első időablakot (nextAttemptAt) config-kulcsból — betöltés
        // nélkül a kódbeli fallbackot kapnák a yml-ben beállított érték helyett (így
        // az első ablak restart után rövidebb/hosszabb volt a beállítottnál). Az enable()
        // load()-ja emiatt már csak frissítés (idempotens).
        configManager.load();
        this.classSpecDependencyPreflight = new ClassSpecDependencyPreflight(plugin, configManager);
        this.messageManager = new MessageManager(plugin, configManager);
        this.currencyManager = new CurrencyManager(plugin, configManager);
        this.factionManager = new FactionManager(plugin, configManager, currencyManager);
        this.factionPassiveConfig = new FactionPassiveConfig(configManager, plugin.getLogger());
        this.factionPassivePolicy = new FactionPassivePolicy();
        this.factionPassiveService = new FactionPassiveService();
        this.jobManager = new JobManager(plugin, configManager, messageManager, factionManager);
        this.spellRegistry = new SpellRegistry();
        // Statikus bekötés a spell-iskola feloldáshoz (SpellDamageUtil — minta: ProtectionBridge).
        hu.taliann.icesmp.utils.SpellDamageUtil.init(configManager, jobManager, spellRegistry);
        hu.taliann.icesmp.utils.CcDiminish.init(configManager);
        this.catalystItemFactory = new CatalystItemFactory(plugin);
        this.captureItemFactory = new CaptureItemFactory(plugin);
        this.spellMasteryManager = new SpellMasteryManager(plugin, configManager, currencyManager, factionManager);
        this.relicManager = new RelicManager(plugin, configManager);
        this.sinManager = new SinManager(plugin, configManager, messageManager, factionManager);
        this.whisperManager = new hu.taliann.icesmp.managers.WhisperManager(plugin, configManager, factionManager, sinManager, messageManager);
        this.metelytepoManager = new MetelytepoManager(plugin, sinManager);
        this.minionManager = new MinionManager(plugin);
        this.totemManager = new hu.taliann.icesmp.managers.TotemManager(plugin, configManager);
        this.factionTreasuryManager = new FactionTreasuryManager(plugin, configManager, currencyManager, factionManager, sinManager, messageManager);
        this.kingManager = new KingManager(plugin, configManager, factionManager, messageManager);
        this.bloodMoonManager = new BloodMoonManager(plugin, configManager, messageManager);
        this.seasonManager = new SeasonManager(plugin, configManager, messageManager, factionTreasuryManager, factionManager);
        factionManager.setSeasonManager(seasonManager);

        this.territoryManager = new TerritoryManager(plugin);
        this.blockRegenService = new BlockRegenService(plugin, configManager);
        this.territoryProtectionService = new TerritoryProtectionService(plugin, configManager, territoryManager, factionManager, messageManager);
        this.raidManager = new RaidManager(plugin, configManager, factionManager, factionTreasuryManager, seasonManager, territoryManager, messageManager);
        territoryProtectionService.setRaidManager(raidManager); // ostrom alatt a célzóna hadszíntér
        this.worldBossManager = new WorldBossManager(plugin, configManager, messageManager, factionManager, factionTreasuryManager, seasonManager);
        this.introManager = new IntroManager(plugin, configManager);
        this.mobAbilityRegistry = new hu.taliann.icesmp.pve.MobAbilityRegistry(configManager);
        this.creatureSpeciesRegistry = new hu.taliann.icesmp.pve.CreatureSpeciesRegistry(
                configManager, mobAbilityRegistry);
        this.mobTemplateRegistry = new hu.taliann.icesmp.pve.MobTemplateRegistry(
                configManager, mobAbilityRegistry);
        this.mobScalingManager = new MobScalingManager(plugin, configManager,
                bloodMoonManager, territoryManager, mobTemplateRegistry, creatureSpeciesRegistry);
        this.mobAbilityRuntime = new hu.taliann.icesmp.pve.MobAbilityRuntime(
                plugin, configManager, mobScalingManager, mobTemplateRegistry, mobAbilityRegistry,
                creatureSpeciesRegistry);
        this.authoredCreatureSpawns = new hu.taliann.icesmp.pve.AuthoredCreatureSpawnService(
                plugin, mobTemplateRegistry, mobScalingManager, mobAbilityRuntime);
        this.creatureProfileService = new hu.taliann.icesmp.pve.CreatureProfileService(
                plugin, creatureSpeciesRegistry, mobScalingManager, mobAbilityRuntime);
        this.invasionManager = new InvasionManager(plugin, configManager, mobScalingManager, messageManager);
        this.professionManager = new ProfessionManager(plugin, configManager);
        this.professionRecipeManager = new ProfessionRecipeManager(plugin, configManager);
        this.itemRarityService = new ItemRarityService(plugin, configManager);
        this.itemTemplateRegistry = new hu.taliann.icesmp.itemization.ItemTemplateRegistry(plugin, configManager);
        this.itemIdentityService = new hu.taliann.icesmp.itemization.ItemIdentityService(plugin, itemTemplateRegistry);
        this.equipmentProficiencyService =
                new hu.taliann.icesmp.itemization.EquipmentProficiencyService(
                        plugin, jobManager, itemIdentityService, messageManager);
        this.itemIdentityService.setEquipmentProficiencyService(equipmentProficiencyService);
        this.itemTransformationPolicy = new hu.taliann.icesmp.itemization.ItemTransformationPolicy(
                plugin, configManager, itemIdentityService);
        this.professionRecipeCatalog = new hu.taliann.icesmp.managers.ProfessionRecipeCatalog(plugin, configManager);
        this.professionRecipeCatalog.setItemTemplates(itemTemplateRegistry);
        this.blueprintItemFactory = new hu.taliann.icesmp.items.BlueprintItemFactory(plugin, professionRecipeCatalog);
        this.uniqueMaterialFactory = new hu.taliann.icesmp.items.UniqueMaterialFactory(plugin, configManager);
        worldBossManager.setUniqueMaterials(uniqueMaterialFactory);
        this.encounterRewardDelivery = new hu.taliann.icesmp.pve.EncounterRewardDeliveryService(
                plugin, uniqueMaterialFactory, messageManager);
        this.equippedCombatPowerService = new hu.taliann.icesmp.pve.EquippedCombatPowerService(
                plugin, itemIdentityService, jobManager, equipmentProficiencyService);
        worldBossManager.setPveRuntime(mobScalingManager, mobAbilityRuntime,
                encounterRewardDelivery, equippedCombatPowerService);
        this.itemMutationCoordinator = new hu.taliann.icesmp.itemization.ItemMutationCoordinator(
                plugin, configManager, itemIdentityService, uniqueMaterialFactory, messageManager);
        this.itemForgeGUI = new hu.taliann.icesmp.gui.ItemForgeGUI(
                itemMutationCoordinator, messageManager);
        this.moneyPouchItemFactory = new hu.taliann.icesmp.items.MoneyPouchItemFactory(plugin);
        this.guildManager = new hu.taliann.icesmp.managers.GuildManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.bestiaryManager = new hu.taliann.icesmp.managers.BestiaryManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.bestiaryManager.setMobTemplateRegistry(mobTemplateRegistry);
        this.honorDuelManager = new hu.taliann.icesmp.managers.HonorDuelManager(plugin, configManager, sinManager, factionManager, seasonManager, messageManager);
        // Hadi-ablak — RED↔BLUE ölés az ablak alatt nem bűn, liga-pontot ér.
        this.warWindowManager = new hu.taliann.icesmp.managers.WarWindowManager(plugin, configManager, messageManager, seasonManager);
        this.combatTagManager = new hu.taliann.icesmp.managers.CombatTagManager(plugin, configManager, messageManager);
        this.dungeonLootService = new hu.taliann.icesmp.managers.DungeonLootService(plugin, configManager,
                messageManager, uniqueMaterialFactory, mobScalingManager);
        this.classHealthService = new hu.taliann.icesmp.managers.ClassHealthService(plugin, configManager, jobManager);
        this.advancementService = new hu.taliann.icesmp.managers.AdvancementService(plugin, configManager);
        territoryProtectionService.setCombatTagManager(combatTagManager);
        warWindowManager.setCombatTagManager(combatTagManager);
        honorDuelManager.setCombatTagManager(combatTagManager);
        // A Menedék Vének Tanácsa (a NEUTRAL "király-pótlék" — gazdasági jogok, raid nélkül).
        this.councilManager = new hu.taliann.icesmp.managers.CouncilManager(plugin, configManager, factionManager, messageManager);
        this.spyManager = new hu.taliann.icesmp.managers.SpyManager(plugin, configManager, raidManager, messageManager, factionManager, seasonManager, territoryManager);
        this.professionWeeklyGoalManager = new hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager(plugin, configManager, professionManager, messageManager);
        this.holidayService = new hu.taliann.icesmp.managers.HolidayService(configManager, messageManager);
        // Az ünnep-felülbírálás bekötése a világesemény-esélyekbe (a hook eddig halott volt).
        bloodMoonManager.setHolidayService(holidayService);
        invasionManager.setHolidayService(holidayService);
        this.cityGuardManager = new hu.taliann.icesmp.managers.CityGuardManager(plugin, configManager);
        this.eventSpawnPointManager = new hu.taliann.icesmp.managers.EventSpawnPointManager(plugin, configManager);
        // Komp (építész-kérés): fix két-végpontú átkelő (óceán-átkelés híd helyett).
        this.ferryManager = new hu.taliann.icesmp.managers.FerryManager(plugin, configManager, currencyManager, factionManager, messageManager);
        worldBossManager.setSpawnPointManager(eventSpawnPointManager); // hely-horgony
        this.professionRecipeBookListener = new hu.taliann.icesmp.listeners.ProfessionRecipeBookListener(plugin,
                professionManager, professionRecipeCatalog, itemRarityService, uniqueMaterialFactory, messageManager, factionManager, configManager);
        professionRecipeBookListener.setItemIdentityService(itemIdentityService);
        this.devItemManager = new hu.taliann.icesmp.managers.DevItemManager(plugin, configManager, messageManager,
                uniqueMaterialFactory, professionRecipeCatalog, blueprintItemFactory, professionRecipeBookListener);
        this.factionFoodListener = new hu.taliann.icesmp.listeners.FactionFoodListener(plugin, configManager, factionManager, messageManager);
        this.craftingRestrictionManager = new CraftingRestrictionManager(plugin, configManager, jobManager, professionManager);
        this.economyEventManager = new EconomyEventManager(plugin, configManager, messageManager);
        this.exchangeRateService = new ExchangeRateService(configManager, currencyManager, economyEventManager);
        this.factionRelationManager = new FactionRelationManager(configManager, raidManager);
        this.marketManager = new MarketManager(plugin, configManager, currencyManager, factionManager,
                factionRelationManager, messageManager, itemIdentityService);
        this.donationChestManager = new DonationChestManager(plugin, configManager);
        this.questManager = new QuestManager(plugin, configManager, messageManager, jobManager,
                currencyManager, factionManager, sinManager, seasonManager);
        this.communityGoalManager = new CommunityGoalManager(plugin, configManager, factionManager,
                factionTreasuryManager, messageManager, seasonManager);
        seasonManager.setSeasonTransitionCoordinator(communityGoalManager::commitSeasonTransition);
        this.shopManager = new ShopManager(configManager, currencyManager, factionManager, messageManager);
        shopManager.setWhisperManager(whisperManager); // Suttogó feketepiac-kedvezmény
        this.npcBindingManager = new NpcBindingManager(plugin);
        this.caravanManager = new CaravanManager(plugin, configManager, messageManager);
        this.ambientEventManager = new AmbientEventManager(plugin, configManager, messageManager, currencyManager, factionManager);
        this.gatheringBuffManager = new GatheringBuffManager(plugin, configManager, messageManager);
        this.partyManager = new PartyManager(plugin, configManager, messageManager);
        this.claimManager = new ClaimManager(plugin, configManager, currencyManager, factionManager, territoryManager);
        // Közös, esemény×védelem mátrixszal configolható spawn-hely szabályok (world-events.
        // spawn-rules) minden világeseménynek. A világboss/invázió/vad hajsza setter-t kap,
        // mert a DI-sorrendben a ClaimManager ELŐTT épülnek.
        this.eventSpawnGuard = new hu.taliann.icesmp.managers.EventSpawnGuard(
                plugin, configManager, territoryManager, claimManager);
        worldBossManager.setSpawnGuard(eventSpawnGuard);
        invasionManager.setSpawnGuard(eventSpawnGuard);
        // PlayerCaravan + DarkUndead az EventSpawnGuardot igényli — az a ClaimManager után
        // épül, ezért itt (nem a saját blokkjukban) konstruáljuk őket.
        this.playerCaravanManager = new hu.taliann.icesmp.managers.PlayerCaravanManager(plugin, configManager, factionTreasuryManager, factionManager, eventSpawnGuard, messageManager);
        this.darkUndeadAmbienceManager = new hu.taliann.icesmp.managers.DarkUndeadAmbienceManager(plugin, configManager, territoryManager, mobScalingManager, eventSpawnGuard);
        this.treasureEventManager = new TreasureEventManager(plugin, configManager, partyManager, eventSpawnGuard, messageManager);
        this.wildHuntManager = new WildHuntManager(plugin, configManager, mobScalingManager, partyManager, messageManager);
        this.abundanceManager = new AbundanceManager(plugin, configManager, messageManager);
        this.serverChallengeManager = new ServerChallengeManager(plugin, configManager, messageManager);
        this.escortManager = new EscortManager(plugin, configManager, mobScalingManager, messageManager, eventSpawnGuard);
        escortManager.setSpawnPointManager(eventSpawnPointManager);
        caravanManager.setSpawnPointManager(eventSpawnPointManager);
        this.meteorEventManager = new MeteorEventManager(plugin, configManager, eventSpawnGuard, messageManager);
        wildHuntManager.setSpawnGuard(eventSpawnGuard);
        this.corruptionManager = new hu.taliann.icesmp.managers.CorruptionManager(plugin, configManager, mobScalingManager, eventSpawnGuard, messageManager, territoryManager, factionManager, seasonManager);
        this.corruptionAuraListener = new hu.taliann.icesmp.listeners.CorruptionAuraListener(plugin, configManager, corruptionManager, messageManager);
        this.lowHealthBorderListener = new hu.taliann.icesmp.listeners.LowHealthBorderListener(plugin, configManager);
        this.cultistEventManager = new hu.taliann.icesmp.managers.CultistEventManager(plugin, configManager,
                mobScalingManager, eventSpawnGuard, territoryManager, corruptionManager, messageManager,
                whisperManager, seasonManager);
        cultistEventManager.setSpawnPointManager(eventSpawnPointManager); // hely-horgony
        this.factionMobContextResolver = new FactionMobContextResolver(
                darkUndeadAmbienceManager, corruptionManager, dungeonLootService, invasionManager,
                worldBossManager, cultistEventManager, escortManager, wildHuntManager,
                territoryManager, bloodMoonManager);
        this.factionPassiveListener = new FactionPassiveListener(
                plugin, factionManager, whisperManager, factionPassiveConfig, factionPassivePolicy,
                factionPassiveService, factionMobContextResolver);
        factionManager.setMembershipChangeHook(playerId -> {
            factionPassiveListener.clearPlayerState(playerId);
            raidManager.onMembershipChange(playerId);
            spyManager.clearPlayerState(playerId);
            councilManager.onMembershipChange(playerId);
            kingManager.onMembershipChange(playerId);
            final Player online = Bukkit.getPlayer(playerId);
            if (online == null) {
                whisperManager.clearPlayerState(playerId);
            } else {
                online.getScheduler().run(plugin,
                        task -> whisperManager.reconcileMembership(online), null);
            }
        });
        // Figyelem-orchestráció — egyszerre csak egy nagy PvE-esemény
        // induljon természetes sorsolásból (admin-indítás mindig átmegy a kapun).
        final hu.taliann.icesmp.managers.MajorEventGate majorEventGate =
                new hu.taliann.icesmp.managers.MajorEventGate(configManager);
        majorEventGate.register("world-boss", worldBossManager::isBossActive);
        majorEventGate.register("invasion", invasionManager::isActive);
        majorEventGate.register("wild-hunt", wildHuntManager::isActive);
        majorEventGate.register("escort", escortManager::isActive);
        majorEventGate.register("cultists", cultistEventManager::isActive);
        worldBossManager.setEventGate(majorEventGate);
        invasionManager.setEventGate(majorEventGate);
        wildHuntManager.setEventGate(majorEventGate);
        escortManager.setEventGate(majorEventGate);
        cultistEventManager.setEventGate(majorEventGate);
        this.archeologyManager = new hu.taliann.icesmp.managers.ArcheologyManager(plugin, configManager, eventSpawnGuard, uniqueMaterialFactory, messageManager);
        // A loot-táblák "unique:<id>" sorai a UniqueMaterialFactory-n át épülnek (statikus híd).
        hu.taliann.icesmp.managers.LootTable.setUniqueFactory(uniqueMaterialFactory);
        professionManager.setMessageManager(messageManager); // szintlépés/fokozat üzenetek
        factionManager.setGuildManager(guildManager);
        questManager.setGuildManager(guildManager); // quest-teljesítés céh-XP
        professionRecipeBookListener.setBestiaryManager(bestiaryManager); // recept-lajstrom
        professionRecipeBookListener.setJobManager(jobManager); // kaszt-zárt receptek
        professionRecipeBookListener.setWeeklyGoal(professionWeeklyGoalManager); // craft-XP a heti célba
        // Vendor-only unique anyagok a boltokban (economy.yml `unique:` bolt-item mező).
        shopManager.setUniqueMaterialFactory(uniqueMaterialFactory);
        // A Rejtélyes Idegen (tisztán atmoszférikus, ritka felbukkanás).
        this.strangerNpcManager = new hu.taliann.icesmp.managers.StrangerNpcManager(plugin, configManager, messageManager);
        strangerNpcManager.setSpawnGuard(eventSpawnGuard);
        // Évszakos világ-modifikátorok: a valós évszak finom esély-szorzói.
        final hu.taliann.icesmp.managers.SeasonalModifierService seasonalModifiers =
                new hu.taliann.icesmp.managers.SeasonalModifierService(configManager);
        bloodMoonManager.setSeasonalModifiers(seasonalModifiers);
        worldBossManager.setSeasonalModifiers(seasonalModifiers);
        invasionManager.setSeasonalModifiers(seasonalModifiers);
        wildHuntManager.setSeasonalModifiers(seasonalModifiers);
        abundanceManager.setSeasonalModifiers(seasonalModifiers);
        gatheringBuffManager.setSeasonalModifiers(seasonalModifiers);
        // Szezonzáró finálé: a fogyasztók (season/boss/vérhold/invázió) setterrel kapják,
        // mert a finálé-manager náluk később épül (kölcsönös hivatkozás a DI-sorrendben).
        this.seasonFinaleManager = new hu.taliann.icesmp.managers.SeasonFinaleManager(plugin, configManager,
                seasonManager, worldBossManager, territoryManager, messageManager);
        seasonManager.setSeasonFinale(seasonFinaleManager);
        worldBossManager.setSeasonFinale(seasonFinaleManager);
        bloodMoonManager.setSeasonFinale(seasonFinaleManager);
        invasionManager.setSeasonFinale(seasonFinaleManager);
        // Escort-success perk: the caravan shop sells its bonus stock while the window is open.
        this.shopManager.setEscortBonusCheck(escortManager::isBonusStockActive);
        // The caravan's stock is served through ShopManager under the reserved "caravan" name,
        // buyable only while the merchant is in town.
        this.shopManager.setCaravanActiveCheck(caravanManager::isActive);
        // Rotáló karaván-készlet: a látogatás sorsolási magját a CaravanManager adja.
        this.shopManager.setCaravanStockSeed(caravanManager::getStockSeed);
        this.specializationManager = new SpecializationManager(plugin, configManager, messageManager,
                jobManager, professionManager, factionManager, sinManager, questManager);
        this.equipmentProficiencyService.setSpecializationManager(specializationManager);
        this.specializationManager.setWorldBossManager(worldBossManager);
        questManager.setSpecializationManager(specializationManager); // szezon-plafon + hajrá-zár a váltás-szabályokhoz
        this.resourceManager = new hu.taliann.icesmp.managers.ResourceManager(plugin, configManager, jobManager);
        this.talentManager = new TalentManager(plugin, configManager, jobManager, professionManager, specializationManager);
        this.spellFavoritesManager = new hu.taliann.icesmp.managers.SpellFavoritesManager(plugin);
        this.abilityCatalystListener = new AbilityCatalystListener(plugin, jobManager, spellRegistry,
                catalystItemFactory, configManager, factionManager, spellMasteryManager,
                specializationManager, resourceManager,
                talentManager, messageManager, spellFavoritesManager);
        abilityCatalystListener.setQuestManager(questManager);
        abilityCatalystListener.setItemRarityService(itemRarityService);
        abilityCatalystListener.setItemIdentityService(itemIdentityService);
        this.questBuilderListener = new hu.taliann.icesmp.listeners.QuestBuilderListener(plugin, questManager, messageManager);
        this.petManager = new PetManager(plugin, configManager, minionManager, specializationManager, messageManager);
        petManager.setJobManager(jobManager);
        petManager.setTalentManager(talentManager);
        jobManager.setFactionManager(factionManager);
        this.dailyQuestManager = new DailyQuestManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.parkourManager = new ParkourManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.siegeWeaponFactory = new SiegeWeaponFactory(plugin);
        this.soulShardManager = new SoulShardManager(plugin, configManager, minionManager, messageManager);
        this.ritualManager = new RitualManager(plugin, configManager, relicManager, sinManager, factionManager,
                territoryManager, jobManager, messageManager);
        // CSAK ide, a resourceManager/soulShardManager/ritualManager felépülte
        // UTÁN köthető (korábbi hívásuk null-mezőn robbant volna a konstruktorban).
        this.soulforgeManager = new hu.taliann.icesmp.managers.SoulforgeManager(plugin, configManager, soulShardManager);
        this.playerProfilePlatform = new hu.taliann.icesmp.playerprofile.integration.PlayerProfilePlatform(plugin, configManager);
        this.playerProfileAuthority = hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.install(
                playerProfilePlatform.service(), playerProfilePlatform.repository(),
                playerProfilePlatform.transactions());
        this.classSpecSectionRepository = new hu.taliann.icesmp.classspec.persistence.PlayerProfileClassSpecSectionRepository(
                playerProfilePlatform.repository());
        this.profileSessionRegistry = new hu.taliann.icesmp.classspec.application.ProfileSessionRegistry();
        this.classSpecRuntimeAdapter = new hu.taliann.icesmp.classspec.integration.BukkitClassSpecRuntimeAdapter(
                plugin, jobManager, specializationManager, abilityCatalystListener, petManager,
                bloodMoonManager, minionManager, soulforgeManager,
                resourceManager, spellRegistry, profileSessionRegistry);
        this.classSpecProfileGateway = new hu.taliann.icesmp.classspec.application.DefaultClassSpecProfileGateway(
                new hu.taliann.icesmp.classspec.persistence.ClassSpecSectionMutationStoreAdapter(classSpecSectionRepository),
                classSpecRuntimeAdapter, profileSessionRegistry);
        this.classSpecSectionLifecycleService = new hu.taliann.icesmp.classspec.application.ClassSpecSectionLifecycleService(
                classSpecSectionRepository);
        specializationManager.setProfileGateway(classSpecProfileGateway);
        specializationManager.shamanGameplayService()
                .ifPresent(shaman -> shaman.setTotemManager(totemManager));
        jobManager.setProfileGateway(classSpecProfileGateway);
        abilityCatalystListener.setProfileGateway(classSpecProfileGateway);
        petManager.setProfileGateway(classSpecProfileGateway);
        soulforgeManager.setProfileGateway(classSpecProfileGateway);
        soulShardManager.setProfileGateway(classSpecProfileGateway);
        sinManager.setSpecializationManager(specializationManager);
        // Class Relic Framework: a resolver a gateway-t (Profile v2 authority) és a
        // vilag-szintu relic-ownershipet adaptalja — ezert csak a gateway UTAN epulhet.
        this.classRelicService = new hu.taliann.icesmp.classrelic.ClassRelicService(
                plugin, configManager, relicManager, classSpecProfileGateway);
        this.resourceBonusService = new hu.taliann.icesmp.managers.ResourceBonusService(
                plugin, configManager, classRelicService);
        resourceManager.setMaxMultiplier(resourceBonusService::maxMultiplier); // pool-bónuszok
        ritualManager.setPaktDependencies(resourceBonusService, uniqueMaterialFactory); // pakt-oltár
        hu.taliann.icesmp.spells.SummonMinionsSpell.setSoulforge(soulforgeManager); // statikus híd
        this.exchangeBoardManager = new ExchangeBoardManager(plugin, configManager, exchangeRateService);
        // A Néma Királynő átka a koronán: a szint a trónon töltött időből számolódik (a
        // KingManager tartja), ezért nincs saját perzisztenciája.
        this.crownCurseManager = new hu.taliann.icesmp.managers.CrownCurseManager(
                plugin, configManager, kingManager, messageManager);
        // A respec EGYETLEN végrehajtója (a parancs és a GUI is ezt hívja) — a TalentManager
        // után épül, mert a talentpont-visszatérítéshez kell.
        this.respecService = new hu.taliann.icesmp.managers.RespecService(
                plugin, specializationManager, talentManager, currencyManager, factionManager);
        this.profileSessionBridge = new hu.taliann.icesmp.classspec.integration.BukkitClassSpecSectionSessionBridge(
                plugin, playerProfilePlatform.service(), classSpecSectionLifecycleService, classSpecProfileGateway, profileSessionRegistry,
                specializationManager, classSpecRuntimeAdapter, respecService);
        this.characterMenuContext = new CharacterMenuContext(messageManager, jobManager, specializationManager,
                professionManager, talentManager, factionManager, currencyManager, sinManager,
                catalystItemFactory, spellRegistry, petManager, resourceManager, classRelicService,
                configManager, respecService);
        this.statsManager = new StatsManager(plugin, jobManager, currencyManager);
        this.chronicleManager = new hu.taliann.icesmp.managers.ChronicleManager(plugin, configManager, statsManager, seasonManager, messageManager);
        // Korszakváltás-narratíva: a szezonzárás hookja (a StatsManager itt már él).
        seasonManager.setStoryTeller(new hu.taliann.icesmp.managers.SeasonStoryTeller(
                plugin, configManager, statsManager, messageManager));
        // Énekmondó: a heti balladát a FancyNpcs interact-hook (registerNpcQuestBridge) köti a bárd-NPC-re.
        this.bardManager = new hu.taliann.icesmp.managers.BardManager(configManager, statsManager, messageManager);
        // Felvásárló NPC: napi keretes nyersanyag-eladás (jövedelem-csap; szintén interact-hook).
        this.buyerService = new hu.taliann.icesmp.managers.BuyerService(configManager, currencyManager, factionManager, messageManager);
        // Szezon-emlékmű: a bajnok kőbe vésése a szezonzárás-hookon.
        this.seasonMonumentManager = new hu.taliann.icesmp.managers.SeasonMonumentManager(plugin, configManager, statsManager);
        seasonManager.setMonumentManager(seasonMonumentManager);
        // Átkozott felszerelés: curse-stamp a boss-lootra + Átok-törés az oltárnál.
        this.cursedGearService = new hu.taliann.icesmp.managers.CursedGearService(plugin, configManager);
        ritualManager.setCursedGearService(cursedGearService);
        // Gazdasági események: pánik-ág + konjunktúra díj-ablak + finálé-sokkok.
        marketManager.setEconomyEventManager(economyEventManager);
        economyEventManager.setSeasonFinale(seasonFinaleManager);
        // Felfedezhető titkos helyek (admin-kijelölt pontok, első-felfedező jutalom).
        this.hiddenSpotManager = new hu.taliann.icesmp.managers.HiddenSpotManager(plugin, configManager, messageManager);
        // A quest-teljesítés és a spell-cast számlálója setterrel kap StatsManager-t
        // (mindkét célosztály a DI-sorrendben korábban épül).
        questManager.setStatsManager(statsManager);
        abilityCatalystListener.setStatsManager(statsManager);
        this.achievementManager = new AchievementManager(plugin, configManager, jobManager, currencyManager,
                professionManager, factionManager, statsManager, dailyQuestManager, messageManager);
        this.commandMenuContext = new CommandMenuContext(messageManager, factionManager, currencyManager,
                exchangeRateService, factionTreasuryManager, kingManager, raidManager, questManager,
                seasonManager, bloodMoonManager, worldBossManager, caravanManager, escortManager,
                abundanceManager, serverChallengeManager, gatheringBuffManager, meteorEventManager, soulShardManager,
                specializationManager, relicManager, statsManager, achievementManager,
                partyManager, claimManager, sinManager, dailyQuestManager, configManager);
        this.afkManager = new hu.taliann.icesmp.managers.AfkManager(configManager);
        ambientEventManager.setAfkManager(afkManager);
        wildHuntManager.setAfkManager(afkManager);
        this.sitManager = new hu.taliann.icesmp.managers.SitManager(plugin, configManager);
        this.reportManager = new hu.taliann.icesmp.managers.ReportManager(plugin, messageManager);
        this.moderationManager = new hu.taliann.icesmp.managers.ModerationManager(plugin, configManager, messageManager);
        this.vanishManager = new hu.taliann.icesmp.managers.VanishManager(plugin, moderationManager, configManager);
        this.eventSpawnGuard.setVanishedPredicate(moderationManager::isVanished);
        this.motdListener = new hu.taliann.icesmp.listeners.MotdListener(plugin, configManager,
                bloodMoonManager, worldBossManager, seasonManager, vanishManager);
        this.invseeManager = new hu.taliann.icesmp.managers.InvseeManager(plugin, messageManager, moderationManager);
        this.crateKeyFactory = new hu.taliann.icesmp.items.CrateKeyFactory(plugin, configManager);
        this.crateManager = new hu.taliann.icesmp.managers.CrateManager(
                plugin, configManager, currencyManager, crateKeyFactory, uniqueMaterialFactory,
                professionRecipeCatalog, professionRecipeBookListener, blueprintItemFactory,
                messageManager, itemIdentityService);
        // A quest "rewards.crate-key" mezője setterrel kap CrateKeyFactory-t
        // (CrateKeyFactory a DI-sorrendben a QuestManager UTÁN épül).
        questManager.setCrateKeyFactory(crateKeyFactory);
        this.textAnimator = new hu.taliann.icesmp.utils.TextAnimator(configManager);
        this.hudManager = new HudManager(plugin, configManager, factionManager, currencyManager, jobManager,
                raidManager, bloodMoonManager, worldBossManager, resourceManager, partyManager,
                caravanManager, escortManager, abundanceManager, serverChallengeManager,
                meteorEventManager, gatheringBuffManager, textAnimator, seasonManager, dailyQuestManager,
                resourcePackReady);
        this.tablistManager = new hu.taliann.icesmp.managers.TablistManager(plugin, configManager,
                factionManager, textAnimator, afkManager);
        // Relációs háború-színek a tablistában (raid alatt az ellenség piros).
        this.tablistManager.setRaidManager(raidManager);
        this.tablistManager.setVanishManager(vanishManager);
        // One registered list of YAML-persistent managers: the core loads them all on enable and
        // saves them all on disable (replacing two hand-maintained call lists).
        this.persistentStores = List.of(blockRegenService, currencyManager, factionManager, relicManager, territoryManager,
                factionTreasuryManager, kingManager, economyEventManager, marketManager, seasonManager,
                exchangeBoardManager, statsManager, parkourManager, questManager, communityGoalManager,
                claimManager, donationChestManager, npcBindingManager, crateManager, reportManager,
                moderationManager, invseeManager, chronicleManager, corruptionManager, seasonFinaleManager,
                seasonMonumentManager, hiddenSpotManager,
                guildManager,
                professionWeeklyGoalManager,
                eventSpawnPointManager,
                councilManager,
                dungeonLootService,
                raidManager,
                devItemManager);
        this.storeCoordinator = new PersistentStoreCoordinator(persistentStores);
        parkourManager.setFinishHook(questManager::handleParkourFinish);
        raidManager.setWinHook(fighter -> {
            questManager.handleRaidWin(fighter);
            communityGoalManager.contribute(fighter, "WIN_RAID", null, 1);
        });
        jobManager.setXpChangeHook(player -> {
            questManager.handleLevelChange(player);
            equipmentProficiencyService.reconcileNextTick(player);
            hu.taliann.icesmp.pve.EquippedCombatPowerService.refreshAfterMutation(player);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        });
        this.clientBridge = new hu.taliann.icesmp.client.IceSmpClientBridge(plugin, configManager);
        this.playerSessionCleanupListener = new PlayerSessionCleanupListener(
                abilityCatalystListener,
                jobManager,
                currencyManager,
                factionManager,
                factionPassiveListener,
                metelytepoManager,
                relicManager,
                craftingRestrictionManager,
                resourceManager,
                partyManager,
                claimManager,
                territoryManager,
                petManager,
                ritualManager,
                professionManager,
                afkManager,
                sitManager,
                crateManager,
                moderationManager,
                vanishManager,
                invseeManager,
                whisperManager,
                guildManager,
                honorDuelManager,
                spyManager,
                combatTagManager,
                classHealthService,
                lowHealthBorderListener,
                soulforgeManager,
                spellRegistry,
                profileSessionBridge,
                clientBridge
        );

        registerSpells();
    }

    /**
     * Registers every bespoke spell plus the declaratively-configured expansion and
     * summon spell pools. Extracted from the constructor to keep the wiring readable;
     * uses only already-constructed fields (spellRegistry, minionManager, talentManager…).
     */
    private void registerSpells() {
        spellRegistry.register(new DoubleJumpSpell(messageManager));
        spellRegistry.register(new FriendshipSpell(messageManager));
        spellRegistry.register(new FeatherfootSpell(messageManager));
        spellRegistry.register(new AngryChickenSpell(plugin, messageManager));
        spellRegistry.register(new InnerFocusSpell(plugin, messageManager));
        spellRegistry.register(new RootSpell(messageManager));
        spellRegistry.register(new WisplightSpell(plugin, messageManager));
        spellRegistry.register(new FeastSpell(messageManager));
        spellRegistry.register(new RainDanceSpell(messageManager));
        spellRegistry.register(new SunDanceSpell(messageManager));
        spellRegistry.register(new ArmamentSpell(plugin, messageManager));
        spellRegistry.register(new ConfusionSpell(plugin, messageManager));
        spellRegistry.register(new HideSpell(plugin, messageManager));
        spellRegistry.register(new GustSpell(messageManager));
        spellRegistry.register(new LuckyStarSpell(plugin, messageManager));
        spellRegistry.register(new EagleEyeSpell(messageManager));
        spellRegistry.register(new MultishotSpell(messageManager));
        spellRegistry.register(new ShadowstepSpell(messageManager));
        spellRegistry.register(new SmokeBombSpell(messageManager));
        spellRegistry.register(new LifeDrainSpell(messageManager));
        spellRegistry.register(new BoneChillSpell(messageManager));
        spellRegistry.register(new BulwarkSpell(messageManager));
        spellRegistry.register(new VenomStrikeSpell(messageManager));
        SpellCatalog.registerExpansionSpells(spellRegistry, messageManager);
        SpellCatalog.registerSummonSpells(spellRegistry, messageManager, plugin, minionManager, configManager, talentManager);

        // Playtest-rework spellek (bespoke osztályok — a katalógusbeli deklaratív elődjeik törölve).
        spellRegistry.register(new hu.taliann.icesmp.spells.WildMushroomSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.RuneStrikeSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.DemonicCircleSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.ShadowburnSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.ExpelHarmSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.DevotionAuraSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.HolyWrathSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.SoulExchangeSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.GlaiveThrowSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.LivingFlameSpell(messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.MindBlastSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.DeepBreathSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.WhirlwindSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.SpinningCraneKickSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.FlyingSerpentKickSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.SpectralSightSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.ChainsOfIceSpell(plugin, messageManager));
        spellRegistry.register(new hu.taliann.icesmp.spells.FrostFeverSpell(plugin, messageManager));

        // Persistent Shaman totems (bespoke — need the TotemManager). Registered after the catalog so
        // they own their ids (the data-driven instant-aura versions were removed from SpellCatalog).
        spellRegistry.register(new hu.taliann.icesmp.spells.ShamanTotemSpell(messageManager, totemManager,
                hu.taliann.icesmp.managers.TotemManager.TotemType.HEALING_STREAM, 90, hu.taliann.icesmp.spells.SpellCostType.HUNGER, 5));
        spellRegistry.register(new hu.taliann.icesmp.spells.ShamanTotemSpell(messageManager, totemManager,
                hu.taliann.icesmp.managers.TotemManager.TotemType.SEARING, 75, hu.taliann.icesmp.spells.SpellCostType.XP, 50));
        spellRegistry.register(new hu.taliann.icesmp.spells.ShamanTotemSpell(messageManager, totemManager,
                hu.taliann.icesmp.managers.TotemManager.TotemType.WINDFURY, 90, hu.taliann.icesmp.spells.SpellCostType.HUNGER, 5));
        spellRegistry.register(new hu.taliann.icesmp.spells.ShamanTotemSpell(messageManager, totemManager,
                hu.taliann.icesmp.managers.TotemManager.TotemType.EARTHBIND, 75, hu.taliann.icesmp.spells.SpellCostType.HUNGER, 5));
    }

    /**
     * Applies the {@code config/spells-balance.yml} overrides on top of every declaratively-configured
     * (ConfiguredSpell) spell already in the registry, re-registering the overridden copies in place.
     * The stateful spell classes (hardcoded-constant subclasses of BaseSpell) read the same file
     * directly at cast time via {@code balance()}/{@code balanceInt()}, so they need no re-registration
     * step here — only {@link hu.taliann.icesmp.spells.BaseSpell#setBalanceSource} below wires them up.
     * Also validates the {@code spell-balance} section's keys against the registry so a typo'd spell
     * id is reported at startup instead of silently doing nothing.
     */
    /**
     * Spell-VFX paletta-térkép: a {@code spell-vfx.class-palettes.<kaszt/spec>} hozzárendeléseket a
     * {@code classes.<kaszt/spec>.spell-unlocks} kulcsaira terjeszti (egy paletta ráterjed a spec
     * összes spelljére), majd a {@code spell-vfx.overrides.<spell-id>} felülírja az egyedieket.
     */
    private void configureSpellVfxPalettes() {
        final org.bukkit.configuration.file.FileConfiguration cfg = configManager.getConfiguration();
        if (cfg == null) {
            return;
        }
        final java.util.Map<String, hu.taliann.icesmp.utils.SpellVfx.Palette> map = new java.util.HashMap<>();
        final org.bukkit.configuration.ConfigurationSection classPalettes =
                cfg.getConfigurationSection("spell-vfx.class-palettes");
        if (classPalettes != null) {
            for (final String section : classPalettes.getKeys(false)) {
                final hu.taliann.icesmp.utils.SpellVfx.Palette palette = parsePalette(classPalettes.getString(section));
                final org.bukkit.configuration.ConfigurationSection unlocks =
                        cfg.getConfigurationSection("classes." + section + ".spell-unlocks");
                if (palette == null || unlocks == null) {
                    continue;
                }
                for (final String spellId : unlocks.getKeys(false)) {
                    map.put(spellId, palette);
                }
            }
        }
        final org.bukkit.configuration.ConfigurationSection overrides =
                cfg.getConfigurationSection("spell-vfx.overrides");
        if (overrides != null) {
            for (final String spellId : overrides.getKeys(false)) {
                final hu.taliann.icesmp.utils.SpellVfx.Palette palette = parsePalette(overrides.getString(spellId));
                if (palette != null) {
                    map.put(spellId, palette);
                }
            }
        }
        hu.taliann.icesmp.utils.SpellVfx.setSpellPalettes(map);

        // Forma-override: per-spell explicit forma (a heurisztika/targeting-alapot írja felül).
        final java.util.Map<String, hu.taliann.icesmp.utils.SpellVfx.Shape> shapeMap = new java.util.HashMap<>();
        final org.bukkit.configuration.ConfigurationSection shapes = cfg.getConfigurationSection("spell-vfx.shapes");
        if (shapes != null) {
            for (final String spellId : shapes.getKeys(false)) {
                final hu.taliann.icesmp.utils.SpellVfx.Shape shape = parseShape(shapes.getString(spellId));
                if (shape != null) {
                    shapeMap.put(spellId, shape);
                }
            }
        }
        hu.taliann.icesmp.utils.SpellVfx.setSpellShapes(shapeMap);
    }

    private static hu.taliann.icesmp.utils.SpellVfx.Palette parsePalette(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return hu.taliann.icesmp.utils.SpellVfx.Palette.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private static hu.taliann.icesmp.utils.SpellVfx.Shape parseShape(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            final hu.taliann.icesmp.utils.SpellVfx.Shape shape =
                    hu.taliann.icesmp.utils.SpellVfx.Shape.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            return shape == hu.taliann.icesmp.utils.SpellVfx.Shape.AUTO ? null : shape;
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private void applySpellBalanceOverrides() {
        for (final Spell spell : List.copyOf(spellRegistry.getAll())) {
            if (spell instanceof ConfiguredSpell configuredSpell) {
                final ConfiguredSpell overridden = ConfiguredSpell.withBalanceOverrides(configuredSpell, configManager, plugin.getLogger());
                if (overridden != configuredSpell) {
                    spellRegistry.register(overridden);
                }
            }
        }

        if (configManager.getConfiguration() == null) {
            return;
        }
        final org.bukkit.configuration.ConfigurationSection balanceSection =
                configManager.getConfiguration().getConfigurationSection("spell-balance");
        if (balanceSection == null) {
            return;
        }
        for (final String key : balanceSection.getKeys(false)) {
            final Spell spell = spellRegistry.getById(key);
            if (spell == null) {
                plugin.getLogger().warning("spells-balance.yml: ismeretlen spell id a 'spell-balance." + key
                        + "' alatt — elgépelés? A felülbírálás nem érvényesül.");
            }
        }
    }

    /**
     * Enables the plugin core by loading all managers and registering systems.
     */
    public void enable() {
        // Canonical permission scheme + the icesmp.admin.all parent + legacy aliases.
        Permissions.register();
        configManager.load();
        factionPassiveConfig.reload();
        classSpecDependencyPreflight.verify();
        // Surface admin typos (bad material/currency names, out-of-range percents, negative
        // durations) as clear log warnings — never blocks startup, only reports.
        ConfigValidator.validate(configManager, plugin.getLogger());
        hu.taliann.icesmp.utils.NamedEntityDeathLogFilter.install(configManager);
        // Config-driven spell balance: seeds config/spells-balance.yml overrides at startup
        // (startup log + unknown-id warnings). The overridable keys are ALSO read live at
        // cast time (BaseSpell.balance + ConfiguredSpell live accessors), so /icesmp reload
        // applies changes immediately for every spell — no restart needed.
        hu.taliann.icesmp.spells.BaseSpell.setBalanceSource(configManager);
        // Party-tudatos célzás: az ellenséges spellek kihagyják a szövetségest.
        hu.taliann.icesmp.spells.SpellTargetingUtil.initCombatContext(partyManager, factionManager, configManager);
        // Egységes GUI-hangnyelv config-forrása (gui.sounds.* felülbírálások).
        hu.taliann.icesmp.gui.GuiUtil.initSounds(configManager);
        // Formázott spell-effektek kapcsolója + pontszám-plafon (spell-vfx.*).
        hu.taliann.icesmp.utils.SpellVfx.configure(
                configManager.getBoolean("spell-vfx.enabled", true),
                configManager.getInt("spell-vfx.max-points", 48));
        configureSpellVfxPalettes();
        applySpellBalanceOverrides();
        adviseOnPluginCompatibility();
        messageManager.reload();
        motdListener.reload();
        sitManager.reload();
        // Config-derived (load-only) managers first, then every registered persistent store.
        mobAbilityRegistry.load();
        creatureSpeciesRegistry.load();
        mobTemplateRegistry.load();
        final var authoredPveReport = hu.taliann.icesmp.pve.AuthoredPveContentValidator.validate(
                mobTemplateRegistry, mobAbilityRegistry);
        plugin.getLogger().info("Authored PvE authority validated: "
                + authoredPveReport.worldBosses() + " world bosses, "
                + authoredPveReport.invasionChampions() + " invasion champions, "
                + authoredPveReport.prologueTemplates() + " Prologue templates.");
        mobScalingManager.load();
        craftingRestrictionManager.load();
        itemTemplateRegistry.load();
        professionRecipeCatalog.load();
        crateManager.reloadConfig();
        advancementService.load();
        // The modular PlayerProfile platform is the sole player-owned persistence authority.
        playerProfilePlatform.start();
        // Authoritative state is fail-closed: one failed store aborts the whole enable instead of
        // letting later gameplay run against an empty/default manager and overwrite the evidence.
        storeCoordinator.loadAll();
        // A class-relic katalógus kereszt-validációja a generikus relic-registryt kérdezi,
        // ezért csak a RelicManager (persistent store) betöltése UTÁN futhat.
        classRelicService.reload();
        // Exact-once mastery wallet witnesses are reconciled against PlayerProfile receipts
        // before listeners or commands can admit new gameplay mutations.
        spellMasteryManager.recoverPendingOperations().toCompletableFuture().join();
        siegeWeaponFactory.registerRecipe();
        professionRecipeManager.registerRecipes();
        registerListeners();
        // Hot plugin reloads may enable while players are already online and therefore do not emit
        // a new join event. Give those sessions a fresh generation before PM delivery can link them.
        for (final Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            moderationManager.openReplySession(onlinePlayer.getUniqueId());
            afkManager.recordActivity(onlinePlayer.getUniqueId());
            onlinePlayer.getScheduler().run(plugin, task -> eventSpawnGuard.trackPlayer(onlinePlayer), null);
            profileSessionBridge.join(onlinePlayer);
        }
        vanishManager.refreshAll();
        registerCommands();
        scheduleTaxCollection();
        scheduleEconomyEvents();
        scheduleWorldEvents();
        scheduleHud();
        scheduleHealth();
        scheduleCorruptionAura();
        schedulePetCombat();
        devItemManager.start();
        scheduleAutosave();
        scheduleModerationExpiry();
        // A visszaépítés saját, sűrű ütemén fut (látványos, fokozatos gyógyulás) —
        // a 60 mp-es világesemény-tick ehhez túl durva.
        final long regenTicks = blockRegenService.restoreIntervalTicks();
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                task -> blockRegenService.tick(), regenTicks, regenTicks);
        registerPlaceholders();
        logClassHudCapability();
        registerNpcQuestBridge();
        applyWorldGameRules();

        // Only a fully assembled runtime may execute stateful shutdown or common persistence.
        enableCompleted = true;
        plugin.getLogger().info("IceSMP core enabled.");
        hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "Available factions: " + factionManager.describeAvailableFactions());
    }

    /**
     * Applies the configured client gamerules to every loaded world (and to worlds loaded later,
     * via {@link hu.taliann.icesmp.listeners.WorldGameRuleListener}). Currently just disables the
     * vanilla 1.21.6+ Locator Bar, which otherwise draws a direction pip above the XP bar and
     * clashes with the plugin's own HUD. Looked up by name so it degrades gracefully on server
     * versions where the gamerule does not exist.
     */
    private void applyWorldGameRules() {
        if (!configManager.getBoolean("settings.disable-locator-bar", true)) {
            return;
        }
        for (final org.bukkit.World world : Bukkit.getWorlds()) {
            disableLocatorBar(world);
        }
    }

    /** Sets the locatorBar gamerule to false on one world, no-op if the gamerule is unavailable. */
    @SuppressWarnings("unchecked")
    public static void disableLocatorBar(final org.bukkit.World world) {
        try {
            // Reflexióval hívjuk meg a getByName-t, így a fordító nem dob warningot a deprecation miatt.
            final java.lang.reflect.Method getByNameMethod = org.bukkit.GameRule.class.getMethod("getByName", String.class);
            final org.bukkit.GameRule<?> rule = (org.bukkit.GameRule<?>) getByNameMethod.invoke(null, "locatorBar");

            if (rule != null && rule.getType() == Boolean.class) {
                world.setGameRule((org.bukkit.GameRule<Boolean>) rule, false);
            }
        } catch (final Exception ignored) {
            // Ha a gamerule nem létezik, vagy a jövőben végleg eltávolítják a getByName metódust,
            // egyszerűen kilépünk, így megmarad az elvárt "no-op" viselkedés.
        }
    }

    /**
     * Registers the PlaceholderAPI bridge if PlaceholderAPI is installed. Done reflectively so the core
     * has no compile-time dependency on PlaceholderAPI (and on the {@code IceSMPPlaceholders} class,
     * which only compiles when the PlaceholderAPI API is on the build classpath). If PlaceholderAPI is
     * absent — or the integration class was not bundled — this is a no-op.
     */
    private void registerPlaceholders() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            Class.forName("hu.taliann.icesmp.integration.IceSMPPlaceholders")
                    .getMethod("register", JavaPlugin.class, HudManager.class,
                            hu.taliann.icesmp.managers.ConfigManager.class,
                            hu.taliann.icesmp.managers.BestiaryManager.class,
                            hu.taliann.icesmp.managers.ProfessionRecipeCatalog.class,
                            hu.taliann.icesmp.managers.TerritoryManager.class)
                    .invoke(null, plugin, hudManager, configManager, bestiaryManager,
                            professionRecipeCatalog, territoryManager);
            hudManager.setPlaceholderBridgeReady(true);
            plugin.getLogger().info("PlaceholderAPI integráció bekapcsolva (%icesmp_...% placeholderek).");
        } catch (final Throwable throwable) {
            hudManager.setPlaceholderBridgeReady(false);
            plugin.getLogger().warning("PlaceholderAPI jelen van, de a placeholder-integráció nem indult: "
                    + throwable.getMessage());
        }
    }

    private void logClassHudCapability() {
        if (configManager.getBoolean("hud.icesmp-hud.enabled", true)) {
            plugin.getLogger().info("First-party IceSMP HUD enabled: it activates per player after the IceSMP pack reports SUCCESSFULLY_LOADED; native HUD remains the readiness fallback.");
        } else {
            plugin.getLogger().info("First-party IceSMP HUD disabled; native compact class HUD fallback active.");
        }
    }

    /** Registers the required FancyNpcs production bridge; every failure is startup-fatal. */
    private void registerNpcQuestBridge() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("FancyNpcs")) {
            throw new IllegalStateException("FancyNpcs required production dependency is not enabled; "
                    + questManager.getQuestNpcNames().size() + " authored quest NPC is unreachable");
        }
        try {
            npcQuestBridge = hu.taliann.icesmp.integration.FancyNpcsQuestBridge.register(
                    plugin, configManager, questManager, npcBindingManager);
            // Faction shop NPCs: right-clicking a shop NPC opens its buy GUI (money sink). Also
            // fired with an explicit /npcbind SHOP binding's shop name instead of the NPC's own.
            npcQuestBridge.setInteractHook((player, shopName) -> {
                if (shopManager.hasShop(shopName)) {
                    hu.taliann.icesmp.gui.ShopGUI.open(player, shopManager, currencyManager, messageManager, shopName);
                    return;
                }
                // Énekmondó: a bard.npc-name nevű NPC jobb-kattra a heti balladát énekli
                // (a hook a játékos saját régió-szálán fut, a küldés biztonságos).
                if (shopName != null && shopName.toLowerCase(java.util.Locale.ROOT).equals(bardManager.npcName())) {
                    bardManager.sing(player);
                    return;
                }
                // Felvásárló NPC: a kézben tartott nyersanyag napi keretes eladása.
                if (shopName != null && shopName.toLowerCase(java.util.Locale.ROOT).equals(buyerService.npcName())) {
                    buyerService.handle(player);
                }
            });
            // /npcbind <npc> bank|exchange: both open the existing bank menu — the deposit/withdraw/
            // exchange buttons there are already gated by the banking.capital-only config.
            npcQuestBridge.setBankOpenHook(player ->
                    hu.taliann.icesmp.gui.CommandMenus.openBank(player, commandMenuContext));
            // /npcbind <npc> faction: kingdom-choice NPC (the neutral capital's herald) — opens the
            // faction menu; the actual join/switch rules stay in /faction join (capital gate, cost).
            npcQuestBridge.setFactionMenuHook(player ->
                    hu.taliann.icesmp.gui.CommandMenus.openFaction(player, commandMenuContext));
            scheduleQuestNpcMarkers();
            questManager.setNpcBridgeActive(true);
            // NPC-létezés ellenőrzés késleltetve (a FancyNpcs a saját NPC-it a világok
            // betöltése után éleszti) — hiányos authored snapshot fail-closed letiltást kap.
            final hu.taliann.icesmp.integration.FancyNpcsQuestBridge bridgeRef = npcQuestBridge;
            questNpcValidationTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                final var report = bridgeRef.validateNpcs(questManager.getQuestNpcNames());
                if (!report.healthy()) {
                    plugin.getLogger().severe("FancyNpcs authored NPC snapshot is incomplete; "
                            + "IceSMP disables fail-closed instead of exposing dead onboarding content.");
                    plugin.getServer().getPluginManager().disablePlugin(plugin);
                }
            }, 20L * 60L);
            plugin.getLogger().info("FancyNpcs quest-bridge bekapcsolva (TALK_TO_NPC próbák, giver-npc questek, NPC-markerek, frakció-boltok, /npcbind kötések).");
        } catch (final Throwable throwable) {
            throw new IllegalStateException("FancyNpcs required production bridge failed to initialize", throwable);
        }
        if (npcQuestBridge == null) {
            throw new IllegalStateException("FancyNpcs bridge returned no runtime authority");
        }
    }

    /**
     * Schedules the per-player quest-NPC marker tick (gold/green aura above
     * quest-giver NPCs, visible only to eligible players) on the global region
     * scheduler; the bridge hops to each player's own thread.
     */
    private void scheduleQuestNpcMarkers() {
        if (!configManager.getBoolean("quest-npc-markers.enabled", true)) {
            return;
        }

        final long intervalTicks = Math.max(10L, configManager.getLong("quest-npc-markers.interval-ticks", 40L));
        questNpcMarkerTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> npcQuestBridge.tickMarkers(), intervalTicks, intervalTicks);
    }

    /**
     * Logs actionable guidance when known scoreboard/region plugins are present, so
     * admins see the right config switches at startup instead of debugging a
     * flickering sidebar or events landing in protected towns. Log-only; never
     * changes behaviour by itself.
     */
    private void adviseOnPluginCompatibility() {
        final org.bukkit.plugin.PluginManager pluginManager = plugin.getServer().getPluginManager();

        if (pluginManager.getPlugin("TAB") != null) {
            // A tablist/scoreboard mostantól natívan megy (TablistManager + HudManager) — a TAB
            // plugin felesleges, és ha mindkettő aktív, a teameken/neveken verekedni fognak.
            if (configManager.getBoolean("tablist.enabled", true)
                    || configManager.getBoolean("hud.sidebar-enabled", false)) {
                plugin.getLogger().warning("TAB észlelve, miközben az IceSMP natív tablist/scoreboard rétege aktív —"
                        + " a kettő ütközni fog! Ajánlott: a TAB plugin eltávolítása (a teljes funkciója house-ban van:"
                        + " config/tablist.yml + hud.sidebar). Ha mégis a TAB-ot tartod meg: tablist.enabled: false"
                        + " és general.yml → hud.sidebar-enabled: false (%icesmp_...% placeholderek).");
            }
        }
        if (pluginManager.getPlugin("WorldGuard") != null) {
            plugin.getLogger().info("WorldGuard észlelve: a meteor/kincs események kerülik a WG-régiókat (ProtectionBridge).");
        }
        if (pluginManager.getPlugin("LuckPermsChatFormatterFolia") != null
                && configManager.getBoolean("chat.format-enabled", true)) {
            plugin.getLogger().warning("LuckPermsChatFormatterFolia észlelve, de az IceSMP natív chat-formázója is aktív"
                    + " — DUPLA formázás lesz! Töröld a régi plugint, vagy állítsd: general.yml → chat.format-enabled: false.");
        }
        if (pluginManager.getPlugin("SimpleClaimSystem") != null
                && configManager.getBoolean("claims.enabled", true)) {
            plugin.getLogger().warning("SimpleClaimSystem észlelve, de az IceSMP natív claim-rendszere is aktív — a két"
                    + " védelem ütközhet. A régi SCS-claimek nem konvertálódnak automatikusan; migrálás után töröld az"
                    + " SCS jart, vagy állítsd: general.yml → claims.enabled: false.");
        }
    }

    /**
     * Disables the plugin core by saving all manager data.
     */
    public void disable() {
        // A passzívok per-player megtorlási/célzási állapota nem perzisztens. Sikertelen
        // enable után is takarítani kell, különben hot-reloadnál régi célok maradhatnak.
        factionPassiveListener.clearAllState();
        try {
            disableStateful();
        } finally {
            // A "nem merek state-et menteni" döntés nem jelentheti azt, hogy külső erőforrás
            // (repository executor, HTTP adapter, Bukkit service, statikus authority) nyitva
            // marad: a záró út minden korai return és kivétel után is lefut, és idempotens.
            closePlayerProfileResources();
            // A per-player birtoklás-frissítő taskok és a pillanatkép-cache plugin-életciklushoz
            // kötöttek; részleges enable után is takarítandók.
            shutdownStep("ClassRelicService.shutdown", classRelicService::shutdown);
            // Bent hagyott plugin-message listener a régi core-példányt tartaná életben a
            // következő enable-ig; részleges enable után is takarítandó (idempotens no-op).
            shutdownStep("ClientBridge.unregister", clientBridge::unregister);
            // A root-loggerre akasztott szűrő plugin-életciklushoz kötött: bent hagyva egy
            // eldobott ConfigManager-példányt tartana életben a következő enable-ig.
            shutdownStep("NamedEntityDeathLogFilter.uninstall",
                    hu.taliann.icesmp.utils.NamedEntityDeathLogFilter::uninstall);
            shutdownStep("AdvancementService.clearIfCurrent",
                    () -> hu.taliann.icesmp.managers.AdvancementService.clearIfCurrent(advancementService));
            shutdownStep("ItemTemplateRegistry.clearIfCurrent",
                    () -> hu.taliann.icesmp.itemization.ItemTemplateRegistry.clearIfCurrent(itemTemplateRegistry));
            shutdownStep("ConfigManager.clearIfCurrent",
                    () -> hu.taliann.icesmp.managers.ConfigManager.clearIfCurrent(configManager));
        }
    }

    private void disableStateful() {
        if (!enableCompleted) {
            plugin.getLogger().severe("IceSMP enable did not complete — skipping stateful manager shutdown "
                    + "and persistent-store writes to protect the last durable state.");
            return;
        }
        if (moderationExpiryTask != null) {
            moderationExpiryTask.cancel();
            moderationExpiryTask = null;
        }
        // Stop new moderation writes and drain already-reserved transactions before the common
        // final-save gate closes. Otherwise a queued expiry/command could commit after shutdown save.
        if (!moderationManager.prepareShutdown(10_000L)) {
            plugin.getLogger().severe("A moderációs tranzakciók nem álltak le; a shutdown-save megtagadva.");
            return;
        }
        if (!invseeManager.prepareShutdown(10_000L)) {
            plugin.getLogger().severe("Az invsee escrow tranzakciók nem álltak le; a shutdown-save megtagadva.");
            return;
        }
        if (!respecService.prepareShutdown(10_000L)) {
            plugin.getLogger().severe("A Profile v2 respec tranzakciók nem álltak le; a shutdown-save megtagadva.");
            return;
        }
        // Atomically wait for any running common autosave and close its gate before shutdown hooks
        // start mutating manager state.
        if (!storeCoordinator.beginShutdown()) {
            plugin.getLogger().severe("Persistent-store lifecycle is not ready for shutdown; refusing writes.");
            return;
        }
        enableCompleted = false;
        if (taxTask != null) {
            taxTask.cancel();
            taxTask = null;
        }
        if (questNpcMarkerTask != null) {
            questNpcMarkerTask.cancel();
            questNpcMarkerTask = null;
        }
        if (questNpcValidationTask != null) {
            questNpcValidationTask.cancel();
            questNpcValidationTask = null;
        }
        shutdownStep("raidManager", raidManager::shutdown);
        if (economyEventTask != null) {
            economyEventTask.cancel();
            economyEventTask = null;
        }
        if (worldEventsTask != null) {
            worldEventsTask.cancel();
            worldEventsTask = null;
        }
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
        if (survivalHudTask != null) {
            survivalHudTask.cancel();
            survivalHudTask = null;
        }
        if (tablistTask != null) {
            tablistTask.cancel();
            tablistTask = null;
        }
        if (healthTask != null) {
            healthTask.cancel();
            healthTask = null;
        }
        if (corruptionAuraTask != null) {
            corruptionAuraTask.cancel();
            corruptionAuraTask = null;
        }
        if (petTask != null) {
            petTask.cancel();
            petTask = null;
        }
        shutdownStep("worldBossManager", worldBossManager::shutdown);
        shutdownStep("mobAbilityRuntime", mobAbilityRuntime::shutdown);
        shutdownStep("invasionManager", invasionManager::shutdown);
        shutdownStep("caravanManager", caravanManager::shutdown);
        shutdownStep("treasureEventManager", treasureEventManager::shutdown);
        shutdownStep("wildHuntManager", wildHuntManager::shutdown);
        shutdownStep("corruptionManager", corruptionManager::shutdown);
        shutdownStep("archeologyManager", archeologyManager::shutdown);
        shutdownStep("strangerNpcManager", strangerNpcManager::shutdown);
        shutdownStep("escortManager", escortManager::shutdown);
        shutdownStep("playerCaravanManager", playerCaravanManager::shutdown);
        shutdownStep("cityGuardManager", cityGuardManager::shutdown);
        shutdownStep("meteorEventManager", meteorEventManager::shutdown);
        shutdownStep("serverChallengeManager", serverChallengeManager::shutdown);
        shutdownStep("spyManager", spyManager::shutdown);
        shutdownStep("cultistEventManager", cultistEventManager::shutdown);
        shutdownStep("totemManager", totemManager::shutdown);
        shutdownStep("devItemManager", devItemManager::shutdown);
        shutdownStep("sitManager", sitManager::shutdown);
        shutdownStep("professionRecipeManager", professionRecipeManager::shutdown);
        shutdownStep("crateManager", crateManager::shutdown);
        shutdownStep("invseeManager", invseeManager::shutdown);
        shutdownStep("motdListener", motdListener::shutdown);
        shutdownStep("vanishManager", vanishManager::shutdown);
        shutdownStep("eventSpawnGuard", eventSpawnGuard::clearReservations);

        // Save ALL persistent state FIRST, before any cleanup that could mutate in-memory state.
        // (mobScalingManager / craftingRestrictionManager are config-derived read-only — no save.)
        shutdownStep("storeCoordinator.saveForShutdown", () ->
                storeCoordinator.saveForShutdown(failure -> plugin.getLogger().severe("Store save() hiba ("
                        + failure.store().getClass().getSimpleName() + "): " + failure.cause())));

        // Stateful consumers must finish rollback and final-save writes while Profile v2 remains
        // installed; only their completed durable boundary permits the authority teardown.
        final long profileDeadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.SECONDS.toNanos(10L);
        try {
            profileSessionBridge.prepareDisable().toCompletableFuture().get(
                    remainingProfileShutdownNanos(profileDeadline),
                    java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("PlayerProfile disable interrupted: " + interrupted);
        } catch (final java.util.concurrent.ExecutionException
                       | java.util.concurrent.TimeoutException failure) {
            plugin.getLogger().severe("PlayerProfile session drain incomplete: " + failure);
        }
        profileSessionBridge.stopRuntime();
        try {
            final long remaining = remainingProfileShutdownNanos(profileDeadline);
            final var shutdown = playerProfilePlatform.shutdown(
                            java.time.Duration.ofNanos(remaining))
                    .toCompletableFuture().get(remaining,
                            java.util.concurrent.TimeUnit.NANOSECONDS);
            if (!shutdown.drained()) {
                plugin.getLogger().severe("PlayerProfile disable drain incomplete ("
                        + shutdown.pendingOperations() + " pending): " + shutdown.detail());
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("PlayerProfile platform shutdown interrupted: " + interrupted);
        } catch (final java.util.concurrent.ExecutionException
                       | java.util.concurrent.TimeoutException failure) {
            plugin.getLogger().severe("PlayerProfile platform shutdown deadline exceeded: " + failure);
        }
        if (hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.installed()
                .filter(installed -> installed == playerProfileAuthority).isPresent()) {
            playerProfileAuthority.uninstall();
        }
        shutdownStep("ProfileGUI.closeAll", ProfileGUI::closeAll);

        // Then clean up live player session state (HUD teams, restored armor, caches).
        for (final Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            shutdownStep("player-cleanup " + onlinePlayer.getName(), () -> {
                hudManager.cleanup(onlinePlayer);
                tablistManager.cleanup(onlinePlayer);
                playerSessionCleanupListener.cleanupPlayerState(onlinePlayer.getUniqueId());
            });
        }

        plugin.getLogger().info("IceSMP core disabled.");
    }

    private static long remainingProfileShutdownNanos(final long deadline) {
        return Math.max(1L, deadline - System.nanoTime());
    }

    /**
     * A PlayerProfile külső erőforrásainak idempotens zárása. Nem ír autoritatív state-et:
     * a runtime admission-t állítja le, a platform teardownt hívja (service-deregisztráció,
     * HTTP adapter és repository executor zárása — a repository a már befogadott írásokat
     * a saját CAS/drain protokollja szerint fejezi be), majd a statikus authority-t szereli
     * le. Sikeres stateful shutdown után minden lépése no-op.
     */
    private void closePlayerProfileResources() {
        shutdownStep("profileSessionBridge.stopRuntime", profileSessionBridge::stopRuntime);
        shutdownStep("playerProfilePlatform.shutdown", () -> {
            try {
                playerProfilePlatform.shutdown(java.time.Duration.ofSeconds(10))
                        .toCompletableFuture().get(11, java.util.concurrent.TimeUnit.SECONDS);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("PlayerProfile resource close interrupted", interrupted);
            } catch (final java.util.concurrent.ExecutionException
                           | java.util.concurrent.TimeoutException failure) {
                throw new IllegalStateException("PlayerProfile resource close failed", failure);
            }
        });
        if (hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.installed()
                .filter(installed -> installed == playerProfileAuthority).isPresent()) {
            shutdownStep("playerProfileAuthority.uninstall", playerProfileAuthority::uninstall);
        }
    }

    /**
     * Best-effort leállítási lépés: egyetlen hibás manager-shutdown vagy cleanup nem
     * akadályozhatja meg a KÉSŐBBI állapot-mentést és takarítást — a hiba loggal megy tovább.
     */
    private void shutdownStep(final String name, final Runnable step) {
        try {
            step.run();
        } catch (final Throwable failure) {
            plugin.getLogger().severe("Leállítási lépés hibázott (" + name + "): " + failure);
        }
    }

    /**
     * Időszakos mentés ASYNC szálon (fájl-I/O nem mehet régió-szálra) — crash esetén
     * legfeljebb az utolsó ciklus vész el, nem a teljes uptime. A store-ok concurrent
     * szerkezetekből dolgoznak, a YamlStore.saveAtomic írásonként atomi.
     */
    private void scheduleModerationExpiry() {
        moderationExpiryTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                task -> moderationManager.expireDueAsync(),
                1L, 1L, java.util.concurrent.TimeUnit.MINUTES);
    }

    private void scheduleAutosave() {
        final long minutes = Math.max(0L, configManager.getLong("settings.autosave-minutes", 10L));
        if (minutes <= 0L) {
            return;
        }
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
            if (!enableCompleted) {
                return;
            }
            storeCoordinator.saveAll(failure -> plugin.getLogger().warning("Autosave hiba ("
                    + failure.store().getClass().getSimpleName() + "): " + failure.cause()));
        }, minutes, minutes, java.util.concurrent.TimeUnit.MINUTES);
    }

    /**
     * Schedules the shared world-events tick (blood moon, world boss, season
     * expiry) on the global region scheduler. Each manager guards its own
     * config toggle, so this single timer drives all of section 7.
     */
    private void scheduleWorldEvents() {
        final long intervalTicks = Math.max(1L, configManager.getLong("world-events.check-interval-seconds", 60L)) * 20L;
        // A ~33 rendszer-tick nem futhat egyetlen kötegben (globál-szálas tüske):
        // kis csokrokban, az intervallum első felére terítve fut. A rendszerek
        // egymástól függetlenek (mind saját config-őrrel dolgozik), és egy hibázó
        // manager nem viheti el a köteg többi tagját.
        final List<Runnable> ticks = List.of(
                bloodMoonManager::tick,
                worldBossManager::tick,
                invasionManager::tick,
                seasonManager::tick,
                exchangeBoardManager::tick,
                statsManager::tick,
                achievementManager::tick,
                crownCurseManager::tick,
                marketManager::tickAuctions,
                caravanManager::tick,
                ambientEventManager::tick,
                gatheringBuffManager::tick,
                treasureEventManager::tick,
                wildHuntManager::tick,
                abundanceManager::tick,
                serverChallengeManager::tick,
                escortManager::tick,
                meteorEventManager::tick,
                factionFoodListener::tick,
                whisperManager::tick,
                chronicleManager::tick,
                corruptionManager::tick,
                archeologyManager::tick,
                seasonFinaleManager::tick,
                playerCaravanManager::tick,
                professionWeeklyGoalManager::tick,
                holidayService::tick,
                warWindowManager::tick,
                councilManager::tick,
                cityGuardManager::tick,
                darkUndeadAmbienceManager::tick,
                cultistEventManager::tick,
                strangerNpcManager::tick,
                hiddenSpotManager::tick);
        final int bucketSize = 4;
        final int buckets = (ticks.size() + bucketSize - 1) / bucketSize;
        final long spreadTicks = Math.max(buckets, intervalTicks / 2);
        worldEventsTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> {
                    for (int b = 0; b < buckets; b++) {
                        final List<Runnable> bucket = ticks.subList(b * bucketSize,
                                Math.min(ticks.size(), (b + 1) * bucketSize));
                        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, delayed -> {
                            for (final Runnable tick : bucket) {
                                try {
                                    tick.run();
                                } catch (final Throwable throwable) {
                                    plugin.getLogger().warning("Világesemény-tick hiba: " + throwable);
                                }
                            }
                        }, Math.max(1L, b * spreadTicks / buckets));
                    }
                },
                intervalTicks,
                intervalTicks
        );
    }

    /**
     * Schedules the independently gated HUD and native tablist refreshes on the global region
     * scheduler; each manager hops to the affected player's region thread.
     */
    private void scheduleHud() {
        // The presentation tasks stay present so live gates can re-enable their surfaces.
        final long intervalTicks = Math.max(5L, configManager.getLong("hud.refresh-ticks", 20L));
        hudTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> hudManager.tick(), intervalTicks, intervalTicks);

        // HP/armor/food/air are combat-critical and intentionally do not wait for the heavier
        // sidebar, wallet and party snapshot cadence.
        final long survivalTicks = Math.max(1L, configManager.getLong(
                "hud.icesmp-hud.survival.refresh-ticks", 2L));
        survivalHudTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> hudManager.tickSurvivalHud(), survivalTicks, survivalTicks);

        // A tablist {event} tokenje a HUD-snapshotból olvas, de a tablista nem függ a HUD kapcsolójától.
        tablistManager.setHudManager(hudManager);
        final long tablistTicks = Math.max(5L, configManager.getLong("tablist.refresh-ticks", 10L));
        tablistTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> tablistManager.tick(), tablistTicks, tablistTicks);
    }

    /** HP-rendszer: kaszt-profil karbantartás + harcon kívüli regen (a HUD-kapcsolótól független). */
    private void scheduleHealth() {
        final long healthTicks = Math.max(20L, configManager.getLong("health.ooc-regen.interval-ticks", 40L));
        healthTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> classHealthService.tick(), healthTicks, healthTicks);
    }

    /** Rontás-mag aura (P4e): a korrupt zóna magjában álló játékosok ismétlődő sebzése. */
    private void scheduleCorruptionAura() {
        final long auraTicks = Math.max(10L, configManager.getLong("corruption.aura.interval-ticks", 40L));
        corruptionAuraTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> corruptionAuraListener.tick(), auraTicks, auraTicks);
    }

    /**
     * Schedules the companion drive loop (follow + plugin-driven combat) on the
     * global region scheduler; the manager hops to each pet's region thread. Runs
     * faster than the HUD so chasing and attacks feel responsive.
     */
    private void schedulePetCombat() {
        final long interval = Math.max(2L, configManager.getLong("pets.companion.tick-ticks", 5L));
        petTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> petManager.tick(), interval, interval);
    }


    /**
     * Schedules the periodic faction tax on the global region scheduler,
     * which runs on both Paper and Folia. Tax math touches only in-memory
     * balances; player notices hop to each player's scheduler.
     */
    private void scheduleTaxCollection() {
        if (!configManager.getBoolean("factions.tax.enabled", true)) {
            return;
        }

        final long intervalTicks = Math.max(1L, configManager.getLong("factions.tax.interval-minutes", 60L)) * 60L * 20L;
        taxTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> factionTreasuryManager.collectTaxes(),
                intervalTicks,
                intervalTicks
        );
        hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "Faction tax scheduled every "
                + configManager.getLong("factions.tax.interval-minutes", 60L) + " minute(s).");
    }

    /**
     * Schedules the periodic economy-event tick (demand shocks) on the global
     * region scheduler.
     */
    private void scheduleEconomyEvents() {
        // A tick a sokk (economy-event) ÉS a konjunktúra (market-boom) közös drivere —
        // mindig fut, a feature-kapukat a tick() nézi kulcsonként (élő config; a korábbi
        // itteni kapu a boomot is némán letiltotta a sokkal együtt).
        final long intervalTicks = Math.max(1L, configManager.getLong("currency.economy-event.check-interval-minutes", 60L)) * 60L * 20L;
        economyEventTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> economyEventManager.tick(),
                intervalTicks,
                intervalTicks
        );
    }

    /**
     * Registers all command handlers.
     */
    private void registerCommands() {
        final IceSMPCommand iceSMPCommand = new IceSMPCommand(plugin, configManager, messageManager,
                jobManager, specializationManager, resourceManager, factionManager, currencyManager,
                statsManager, claimManager, questManager, abilityCatalystListener, sinManager);
        iceSMPCommand.setClientBridge(clientBridge);
        // Native HUD routing: a HudManager csak a seam-interfészt látja, a bridge a
        // snapshot-forrást — a két réteg a core-ban találkozik, nem egymásban.
        hudManager.setClientHudRoute(clientBridge);
        clientBridge.connectHudSnapshots(hudManager::snapshot);
        clientBridge.connectAbilityKit(abilityCatalystListener, spellRegistry);
        clientBridge.connectTalents(talentManager);
        clientBridge.connectQuests(questManager);
        clientBridge.connectProfessions(professionManager, specializationManager,
                professionRecipeCatalog, professionWeeklyGoalManager, uniqueMaterialFactory);
        clientBridge.connectParty(partyManager);
        clientBridge.connectWorldBoss(worldBossManager);
        clientBridge.connectTerritory(territoryManager, raidManager);
        clientBridge.connectFaction(factionManager, factionTreasuryManager, currencyManager,
                kingManager, seasonManager, warWindowManager);
        worldBossManager.setFxRoute(clientBridge);
        classRelicService.setFxRoute(clientBridge);
        clientBridge.connectProfile(profilePlayer -> hu.taliann.icesmp.client.projection.ClientProfileProjector
                .project(profilePlayer, characterMenuContext, statsManager, achievementManager));
        clientBridge.connectRelicState(relicPlayerId -> {
            final long readyAt = classRelicService.awakeningReadyAt(relicPlayerId);
            return hu.taliann.icesmp.client.projection.ClientRelicProjector
                    .project(classRelicService.resolve(relicPlayerId), relicId -> {
                        final hu.taliann.icesmp.relics.RelicDefinition definition = relicManager.getDefinition(relicId);
                        return definition == null ? relicId : definition.displayName();
                    }, readyAt == 0L ? 0L : readyAt - System.currentTimeMillis());
        });
        iceSMPCommand.setReloadHook(() -> {
            factionPassiveConfig.reload();
            factionPassiveListener.clearAllState();
            relicManager.load();
            classRelicService.reload();
            mobAbilityRegistry.load();
            creatureSpeciesRegistry.load();
            mobTemplateRegistry.load();
            hu.taliann.icesmp.pve.AuthoredPveContentValidator.validate(
                    mobTemplateRegistry, mobAbilityRegistry);
            mobScalingManager.load();
            craftingRestrictionManager.load();
            itemTemplateRegistry.load();
            for (final Player online : Bukkit.getOnlinePlayers()) {
                equipmentProficiencyService.reconcileNextTick(online);
            }
            professionRecipeCatalog.load();
            professionRecipeManager.registerRecipes();
            crateManager.reloadConfig();
            achievementManager.reload();
            devItemManager.refreshOnlineOwner();
            // A spell-VFX statikus mezőkbe cache-el — reload nélkül az enable-kori érték
            // ragadna be, pedig a VFX-kikapcsolás tipikus élő TPS-mentő beavatkozás.
            hu.taliann.icesmp.utils.SpellVfx.configure(
                    configManager.getBoolean("spell-vfx.enabled", true),
                    configManager.getInt("spell-vfx.max-points", 48));
            configureSpellVfxPalettes();
            moderationManager.reloadConfiguration();
            // Transient admin sessions must not retain stale config-dependent state across reload.
            invseeManager.reload();
            vanishManager.refreshAll();
            motdListener.reload();
            sitManager.reload();
            resourcePackReloadHook.run();
        });
        final java.util.function.Consumer<String> configChangeHook = key -> {
            if (key == null) {
                return;
            }
            if (key.startsWith("motd.")) {
                motdListener.reload();
            }
            if (key.startsWith("sit.")) {
                sitManager.reload();
            }
            if (key.startsWith("crates.")) {
                crateManager.reloadConfig();
            }
            if (key.startsWith("resource-pack.")) {
                resourcePackReloadHook.run();
            }
            if (key.startsWith("professions.recipes.")) {
                professionRecipeManager.registerRecipes();
            }
            if (key.startsWith("factions.passives.") || key.startsWith("factions.whisper.")) {
                factionPassiveConfig.reload();
                factionPassiveListener.clearAllState();
            }
        };
        iceSMPCommand.setConfigChangeHook(configChangeHook);
        // GUI-s config-menü (/icesmp config menu): kategorizált, kattintható felület a
        // leggyakoribb kulcsokhoz — az override-fájlba ír, restart nélkül él.
        final hu.taliann.icesmp.listeners.ConfigMenuGUIListener configMenuGUIListener =
                new hu.taliann.icesmp.listeners.ConfigMenuGUIListener(plugin, configManager, messageManager);
        configMenuGUIListener.setConfigChangeHook(configChangeHook);
        plugin.getServer().getPluginManager().registerEvents(configMenuGUIListener, plugin);
        iceSMPCommand.setConfigMenuOpener(configMenuGUIListener::open);
        plugin.registerCommand("icesmp", "IceSMP admin", List.of("ismp"), iceSMPCommand);
        plugin.registerCommand("invsee", "Online inventory/ender live nézet (admin)", List.of(),
                new hu.taliann.icesmp.commands.InvseeCommand(invseeManager, messageManager));
        plugin.registerCommand("hud", "HUD beállítások", List.of(), new hu.taliann.icesmp.commands.HudCommand(hudManager, messageManager));
        plugin.registerCommand("stats", "Statisztika-profil", List.of(), new hu.taliann.icesmp.commands.StatsCommand(statsManager, messageManager));
        plugin.registerCommand("sit", "Ülés (leül/feláll)", List.of(), new hu.taliann.icesmp.commands.SitCommand(sitManager, messageManager));
        plugin.registerCommand("afk", "Önkéntes AFK-jelölés", List.of(), new hu.taliann.icesmp.commands.AfkCommand(afkManager, messageManager));
        plugin.registerCommand("crate", "Láda (crate) parancsok", List.of("ladak", "crates"),
                new hu.taliann.icesmp.commands.CrateCommand(plugin, crateManager, currencyManager, messageManager));
        plugin.registerCommand("report", "Játékos bejelentése (admin: /reports)", List.of("bejelent"),
                new hu.taliann.icesmp.commands.ReportCommand(reportManager, messageManager));
        plugin.registerCommand("reports", "Bejelentések kezelése (admin)", List.of(),
                new hu.taliann.icesmp.commands.ReportsCommand(reportManager, messageManager));
        plugin.registerCommand("warn", "Figyelmeztetés (admin)", List.of(),
                new hu.taliann.icesmp.commands.ModerationActionCommand(plugin, moderationManager, messageManager,
                        hu.taliann.icesmp.moderation.PunishmentType.WARNING, Permissions.MODERATION_WARN, "warn"));
        plugin.registerCommand("kick", "Játékos kirúgása (admin)", List.of(),
                new hu.taliann.icesmp.commands.ModerationActionCommand(plugin, moderationManager, messageManager,
                        hu.taliann.icesmp.moderation.PunishmentType.KICK, Permissions.MODERATION_KICK, "kick"));
        plugin.registerCommand("mute", "Némítás (admin)", List.of(),
                new hu.taliann.icesmp.commands.ModerationActionCommand(plugin, moderationManager, messageManager,
                        hu.taliann.icesmp.moderation.PunishmentType.MUTE, Permissions.MODERATION_MUTE, "mute"));
        plugin.registerCommand("unmute", "Némítás feloldása (admin)", List.of(),
                new hu.taliann.icesmp.commands.ModerationRevokeCommand(plugin, moderationManager, messageManager,
                        hu.taliann.icesmp.moderation.PunishmentType.Family.MUTE, Permissions.MODERATION_MUTE, "unmute"));
        plugin.registerCommand("ban", "Kitiltás (admin)", List.of(),
                new hu.taliann.icesmp.commands.ModerationActionCommand(plugin, moderationManager, messageManager,
                        hu.taliann.icesmp.moderation.PunishmentType.BAN, Permissions.MODERATION_BAN, "ban"));
        plugin.registerCommand("tempban", "Ideiglenes kitiltás (admin)", List.of(),
                new hu.taliann.icesmp.commands.ModerationActionCommand(plugin, moderationManager, messageManager,
                        hu.taliann.icesmp.moderation.PunishmentType.TEMPORARY_BAN, Permissions.MODERATION_BAN, "tempban"));
        plugin.registerCommand("unban", "Kitiltás feloldása (admin)", List.of(),
                new hu.taliann.icesmp.commands.ModerationRevokeCommand(plugin, moderationManager, messageManager,
                        hu.taliann.icesmp.moderation.PunishmentType.Family.BAN, Permissions.MODERATION_BAN, "unban"));
        plugin.registerCommand("history", "Teljes büntetési előzmény (admin)", List.of(),
                new hu.taliann.icesmp.commands.PunishmentHistoryCommand(moderationManager, messageManager,
                        Permissions.MODERATION_HISTORY));
        plugin.registerCommand("punishments", "Aktív büntetések (admin)", List.of(),
                new hu.taliann.icesmp.commands.ActivePunishmentsCommand(moderationManager, messageManager,
                        Permissions.MODERATION_HISTORY));
        plugin.registerCommand("moderation", "Natív moderációs admin GUI", List.of("mod"),
                new hu.taliann.icesmp.commands.ModerationGuiCommand(messageManager, Permissions.MODERATION_GUI));
        plugin.registerCommand("socialspy", "Privát üzenetek megfigyelése (admin)", List.of(),
                new hu.taliann.icesmp.commands.SocialSpyCommand(plugin, moderationManager, messageManager,
                        Permissions.MODERATION_SOCIALSPY));
        plugin.registerCommand("vanish", "Admin láthatatlanság", List.of("v"),
                new hu.taliann.icesmp.commands.VanishCommand(plugin, moderationManager, vanishManager, messageManager,
                        Permissions.MODERATION_VANISH));
        plugin.registerCommand("offlinetp", "Teleport az utolsó kijelentkezési helyre", List.of(),
                new hu.taliann.icesmp.commands.OfflineTeleportCommand(plugin, moderationManager, messageManager,
                        Permissions.MODERATION_OFFLINE_TP));
        plugin.registerCommand("msg", "Privát üzenet", List.of(),
                new hu.taliann.icesmp.commands.PrivateMessageCommand(plugin, moderationManager, messageManager,
                        "msg", false, Permissions.MESSAGE));
        plugin.registerCommand("tell", "Privát üzenet", List.of(),
                new hu.taliann.icesmp.commands.PrivateMessageCommand(plugin, moderationManager, messageManager,
                        "tell", false, Permissions.MESSAGE));
        plugin.registerCommand("w", "Privát üzenet", List.of(),
                new hu.taliann.icesmp.commands.PrivateMessageCommand(plugin, moderationManager, messageManager,
                        "w", false, Permissions.MESSAGE));
        plugin.registerCommand("reply", "Válasz privát üzenetre", List.of("r"),
                new hu.taliann.icesmp.commands.PrivateMessageCommand(plugin, moderationManager, messageManager,
                        "reply", true, Permissions.MESSAGE));
        plugin.registerCommand("currency", "Valuta parancsok", List.of("money", "eco"), new CurrencyCommand(currencyManager, configManager, exchangeRateService, territoryManager, messageManager));
        plugin.registerCommand("bank", "Bank parancsok", List.of("wallet", "vault"), new BankCommand(currencyManager, configManager, territoryManager, messageManager));
        final FactionCommand factionCommand = new FactionCommand(plugin, factionManager, sinManager, factionTreasuryManager, currencyManager, kingManager, raidManager, territoryManager, configManager, playerCaravanManager, warWindowManager, councilManager, messageManager);
        factionCommand.setSpecializationManager(specializationManager);
        plugin.registerCommand("faction", "Frakció parancsok", List.of("f"), factionCommand);
        plugin.registerCommand("class", "Kaszt (class): szint, Lélekkapocs, admin", List.of("kaszt", "job"), new JobCommand(plugin, jobManager, spellRegistry, catalystItemFactory, abilityCatalystListener, specializationManager, messageManager));
        plugin.registerCommand("menu", "Központi menü — minden parancs egy helyen", List.of("hub", "m"), new MenuCommand(commandMenuContext, messageManager));
        plugin.registerCommand("achievements", "Elérések (mérföldkövek + jutalmak)", List.of("ach", "eleresek"), new AchievementsCommand(commandMenuContext, messageManager));
        plugin.registerCommand("leaderboard", "Ranglisták (szint, vagyon, raid-kill)", List.of("lb", "top", "rangsor"), new LeaderboardCommand(commandMenuContext, messageManager));
        plugin.registerCommand("profile", "Karakterlap — kaszt, spec, szakma, talent menük", List.of("karakter", "char", "status"), new ProfileCommand(characterMenuContext, messageManager));
        plugin.registerCommand("sinner", "Bűnös állapot kezelése (admin)", List.of(), new SinnerCommand(plugin, sinManager, messageManager));
        plugin.registerCommand("bounty", "Körözési lista (fejpénzek)", List.of("fejvadasz", "korozes"), new BountyCommand(sinManager, currencyManager, configManager, messageManager));
        plugin.registerCommand("relic", "Relikvia parancsok (admin)", List.of("relics", "relikvia"), new RelicCommand(plugin, relicManager, messageManager));
        plugin.registerCommand("parkour", "Parkour-pályák (futás, admin beállítás)", List.of("trial", "palya"), new ParkourCommand(parkourManager, messageManager));
        plugin.registerCommand("daily", "Napi küldetés", List.of("napi"), new DailyCommand(dailyQuestManager, messageManager));
        plugin.registerCommand("pet", "Társ (befogó item, idézés, név, szint)", List.of("tars", "companion"), new PetCommand(petManager, captureItemFactory, messageManager));
        plugin.registerCommand("profession", "Szakma (profession) parancsok", List.of("prof", "szakma"),
                new ProfessionCommand(plugin, professionManager, messageManager,
                        professionRecipeBookListener, professionRecipeCatalog,
                        blueprintItemFactory, itemForgeGUI));
        plugin.registerCommand("spec", "Specializáció parancsok", List.of("specialization", "specializacio"), new SpecCommand(plugin, specializationManager, jobManager, professionManager, currencyManager, messageManager, respecService));
        plugin.registerCommand("talent", "Talent-fa parancsok", List.of("talents", "talentfa"), new TalentCommand(talentManager, messageManager));
        final TerritoryCommand territoryCommand = new TerritoryCommand(plugin, territoryManager, claimManager, messageManager);
        territoryCommand.setDungeonLootService(dungeonLootService);
        plugin.registerCommand("territory", "Frakció terület parancsok", List.of("terulet"), territoryCommand);
        plugin.registerCommand("quest", "Küldetés parancsok", List.of("quests", "kuldetes"), new QuestCommand(plugin, questManager, configManager, messageManager, questBuilderListener));
        plugin.registerCommand("market", "Piactér parancsok", List.of("piac", "ah"), new MarketCommand(marketManager, currencyManager, factionManager, configManager, messageManager));
        plugin.registerCommand("adomany", "Közösségi adomány-láda", List.of("donate", "adomanylada"), new DonationChestCommand(donationChestManager, messageManager));
        plugin.registerCommand("party", "Party (csapat) parancsok", List.of("p", "parti"), new hu.taliann.icesmp.commands.PartyCommand(partyManager, messageManager));
        plugin.registerCommand("ceh", "Céh (frakción belüli kisközösség) parancsok", List.of("guild", "gild"),
                new hu.taliann.icesmp.commands.GuildCommand(plugin, guildManager, messageManager));
        plugin.registerCommand("bestiarium", "Bestiárium — a krónikás-lajstromod", List.of("bestiary", "lajstrom"),
                new hu.taliann.icesmp.commands.BestiaryCommand(bestiaryManager, professionRecipeCatalog, territoryManager, messageManager));
        plugin.registerCommand("soulforge", "Lélek-kovács — a Nekromanta minion-fejlesztései", List.of("lelekkovacs"),
                new hu.taliann.icesmp.commands.SoulforgeCommand(
                        plugin, soulforgeManager, soulShardManager, messageManager));
        plugin.registerCommand("parbaj", "Becsület-párbaj — elégtétel a bűnökért", List.of("duel"),
                new hu.taliann.icesmp.commands.HonorDuelCommand(plugin, honorDuelManager, messageManager));
        plugin.registerCommand("kem", "Kém-álca — rövid felderítő álöltözet", List.of("spy"),
                new hu.taliann.icesmp.commands.SpyCommand(spyManager, messageManager));
        plugin.registerCommand("szakmacel", "Szakma-céhek heti közös céljai", List.of("weeklygoal"),
                new hu.taliann.icesmp.commands.ProfessionWeeklyCommand(professionWeeklyGoalManager, messageManager));
        plugin.registerCommand("claim", "Terület-claim parancsok", List.of("birtok"), new hu.taliann.icesmp.commands.ClaimCommand(claimManager, currencyManager, messageManager));
        final EventsCommand eventsCommand = new EventsCommand(seasonManager, bloodMoonManager, worldBossManager, invasionManager, caravanManager, ambientEventManager, gatheringBuffManager, treasureEventManager, wildHuntManager, abundanceManager, serverChallengeManager, escortManager, meteorEventManager, introManager, messageManager);
        eventsCommand.setStrangerNpcManager(strangerNpcManager);
        eventsCommand.setCorruptionManager(corruptionManager);
        eventsCommand.setArcheologyManager(archeologyManager);
        eventsCommand.setSpawnPointManager(eventSpawnPointManager);
        eventsCommand.setCultistEventManager(cultistEventManager);
        plugin.registerCommand("events", "Világesemény parancsok", List.of("event", "esemeny"), eventsCommand);
        plugin.registerCommand("komp", "Kompjárat: átkelés a túlpartra", List.of("ferry"), new hu.taliann.icesmp.commands.KompCommand(ferryManager, combatTagManager, messageManager));
        plugin.registerCommand("tanacs", "A Menedék Vének Tanácsa: szavazás, Vásár-hét", List.of("council"), new hu.taliann.icesmp.commands.TanacsCommand(councilManager, economyEventManager, messageManager));
        plugin.registerCommand("emlek", "Emlékszilánk-beváltás (visszaemlékezés)", List.of("memory", "emlekek"),
                new hu.taliann.icesmp.commands.MemoryCommand(configManager, jobManager, talentManager, specializationManager, uniqueMaterialFactory, messageManager));
        plugin.registerCommand("suttogas", "A Suttogók titkos csatornája és tanú-vád", List.of("sutt"),
                new hu.taliann.icesmp.commands.WhisperCommand(plugin, configManager, whisperManager, messageManager));
        plugin.registerCommand("lore", "A kódex lapjai — frakciók és helyek története", List.of("kodex"),
                new hu.taliann.icesmp.commands.LoreCommand(messageManager));
        plugin.registerCommand("kronika", "Az utolsó Heti Krónika visszaolvasása", List.of("chronicle"),
                new hu.taliann.icesmp.commands.KronikaCommand(chronicleManager, messageManager));
        plugin.registerCommand("iceitem", "Plugin-item kiadása (admin): unique/recept/relikvia/tervrajz/erszeny/dev",
                List.of("iitem", "icegive"),
                new hu.taliann.icesmp.commands.ItemGiveCommand(plugin, uniqueMaterialFactory, professionRecipeCatalog,
                        professionRecipeBookListener, relicManager, blueprintItemFactory, messageManager,
                        moneyPouchItemFactory, devItemManager, itemIdentityService, itemTemplateRegistry,
                        itemTransformationPolicy, equipmentProficiencyService));
        plugin.registerCommand("souls", "Lélekszilánk parancsok", List.of("soul", "lelek"), new SoulCommand(soulShardManager, messageManager));
        plugin.registerCommand("spell", "Spell-mesterség (cooldown + erő valutáért)", List.of("spells", "mastery", "mesterseg"), new SpellCommand(jobManager, spellRegistry, spellMasteryManager, messageManager));
        plugin.registerCommand("spellbook", "Varázskönyv: spellek böngészése és kiválasztása", List.of("varazskonyv", "konyv", "sb"), new SpellbookCommand(abilityCatalystListener, messageManager));
        plugin.registerCommand("exchangeboard", "Árfolyamtábla admin", List.of("ratesboard", "arfolyamtabla"), new ExchangeBoardCommand(exchangeBoardManager, messageManager));
        plugin.registerCommand("npcbind", "NPC-kötések: küldetés/bolt/bankár/valutaváltó (admin)", List.of("npckotes"), new NpcBindCommand(npcBindingManager, questManager, shopManager, messageManager));
    }

    /**
     * Registers all event listeners.
     */
    private void registerListeners() {
        final PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new CurrencyCraftListener(currencyManager), plugin);
        pluginManager.registerEvents(new CurrencyItemRefreshListener(plugin, currencyManager), plugin);
        pluginManager.registerEvents(new CharacterGUIListener(plugin, characterMenuContext), plugin);
        pluginManager.registerEvents(new CommandMenuListener(commandMenuContext), plugin);
        pluginManager.registerEvents(new HudListener(hudManager, tablistManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.AfkActivityListener(afkManager), plugin);
        final hu.taliann.icesmp.listeners.CampfireStoryListener campfireStoryListener =
                new hu.taliann.icesmp.listeners.CampfireStoryListener(
                        plugin, configManager, messageManager, factionManager, sitManager);
        sitManager.setSuccessfulSitHandler(campfireStoryListener::onSuccessfulSit);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.SitListener(sitManager, messageManager), plugin);
        pluginManager.registerEvents(campfireStoryListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CrateListener(crateManager, crateKeyFactory, currencyManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CrateBrowserGUIListener(crateManager, currencyManager), plugin);
        final JobGUIListener jobGUIListener = new JobGUIListener(plugin, jobManager, catalystItemFactory,
                specializationManager, spellRegistry, configManager, messageManager, characterMenuContext);
        jobGUIListener.setFactionManager(factionManager);
        pluginManager.registerEvents(jobGUIListener, plugin);
        pluginManager.registerEvents(new SkillTreeGUIListener(
                jobManager, catalystItemFactory, factionManager, messageManager), plugin);
        pluginManager.registerEvents(new MarketGUIListener(plugin, marketManager, currencyManager, messageManager), plugin);
        pluginManager.registerEvents(new MarketDeliveryListener(marketManager, messageManager), plugin);
        pluginManager.registerEvents(new DonationChestListener(donationChestManager, messageManager), plugin);
        pluginManager.registerEvents(abilityCatalystListener, plugin);
        pluginManager.registerEvents(new SpellbookListener(abilityCatalystListener, spellFavoritesManager,
                spellMasteryManager, messageManager), plugin);
        pluginManager.registerEvents(new CatalystCraftSafetyListener(catalystItemFactory), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CatalystProtectionListener(plugin, catalystItemFactory, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.SignatureItemListener(
                plugin, configManager, messageManager, gatheringBuffManager, currencyManager,
                territoryManager, equipmentProficiencyService, itemIdentityService), plugin);
        pluginManager.registerEvents(new SpellProjectileListener(plugin), plugin);
        pluginManager.registerEvents(new SpellStateListener(plugin), plugin);
        pluginManager.registerEvents(playerSessionCleanupListener, plugin);
        // Plugin messaging csatorna az opcionális kliensmodhoz — a client.enabled kapcsolót
        // a híd üzenetenként, élő configból olvassa, ezért a regisztráció feltétel nélküli.
        clientBridge.register();
        pluginManager.registerEvents(totemManager, plugin);
        pluginManager.registerEvents(new MobScalingListener(mobScalingManager), plugin);
        pluginManager.registerEvents(creatureProfileService, plugin);
        pluginManager.registerEvents(mobAbilityRuntime, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.EquipmentProficiencyListener(
                plugin, equipmentProficiencyService, itemIdentityService), plugin);
        pluginManager.registerEvents(equippedCombatPowerService, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.VanillaCraftingBoundaryListener(
                itemTransformationPolicy, messageManager), plugin);
        pluginManager.registerEvents(new JobCraftRestrictionListener(craftingRestrictionManager, messageManager), plugin);
        pluginManager.registerEvents(new ClassXpListener(plugin, jobManager, mobScalingManager, configManager, talentManager, afkManager), plugin);
        final ProfessionXpListener professionXpListener = new ProfessionXpListener(professionManager, configManager, talentManager, afkManager);
        professionXpListener.setAbundanceManager(abundanceManager);
        professionXpListener.setWeeklyGoal(professionWeeklyGoalManager); // heti közös cél
        pluginManager.registerEvents(professionXpListener, plugin);
        pluginManager.registerEvents(new ProfessionRecipeListener(professionRecipeManager, professionManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.MasterworkCraftListener(professionRecipeManager, itemRarityService), plugin);
        final hu.taliann.icesmp.listeners.MobLootListener mobLootListener =
                new hu.taliann.icesmp.listeners.MobLootListener(plugin, configManager,
                        itemRarityService, worldBossManager, invasionManager, wildHuntManager,
                        blueprintItemFactory, professionRecipeCatalog, uniqueMaterialFactory,
                        itemTemplateRegistry, itemIdentityService, jobManager, specializationManager);
        mobLootListener.setCursedGearService(cursedGearService);
        mobLootListener.setCultistEventManager(cultistEventManager);
        mobLootListener.setAfkManager(afkManager);
        pluginManager.registerEvents(mobLootListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CursedGearListener(cursedGearService, messageManager), plugin);
        pluginManager.registerEvents(professionRecipeBookListener, plugin);
        pluginManager.registerEvents(itemMutationCoordinator, plugin);
        pluginManager.registerEvents(itemForgeGUI, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.RareGatheringListener(
                plugin, configManager, professionManager, uniqueMaterialFactory,
                blockRegenService, afkManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.BlueprintUseListener(blueprintItemFactory, professionRecipeCatalog, professionManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.UniqueMaterialProtectionListener(uniqueMaterialFactory), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.DevItemProtectionListener(plugin, devItemManager), plugin);
        pluginManager.registerEvents(factionPassiveListener, plugin);
        pluginManager.registerEvents(factionFoodListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.WhisperListener(plugin, configManager, whisperManager, factionManager, raidManager, uniqueMaterialFactory, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.SpellDamageListener(configManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CapitalLawListener(plugin, configManager, territoryManager, sinManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CorruptionListener(corruptionManager), plugin);
        pluginManager.registerEvents(corruptionAuraListener, plugin);
        pluginManager.registerEvents(lowHealthBorderListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.StrangerListener(strangerNpcManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.FishingWindfallListener(configManager, moneyPouchItemFactory, afkManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.MoneyPouchListener(moneyPouchItemFactory, currencyManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.SelectionWandListener(claimManager, territoryManager, currencyManager, messageManager), plugin);
        // Nether-portál világszabály: új portál nem gyújtható — csak a Kárhozat Kapuja él.
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.PortalGuardListener(configManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.RuneApplyListener(
                uniqueMaterialFactory, configManager, messageManager, itemIdentityService), plugin);
        final hu.taliann.icesmp.listeners.RuneEffectListener runeEffectListener =
                new hu.taliann.icesmp.listeners.RuneEffectListener(
                        configManager, itemIdentityService, equipmentProficiencyService);
        runeEffectListener.setJobManager(jobManager); // Varázsló rúna-affinitás
        pluginManager.registerEvents(runeEffectListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.BestiaryListener(bestiaryManager, worldBossManager, statsManager, professionRecipeCatalog, territoryManager), plugin);
        pluginManager.registerEvents(resourceBonusService, plugin);
        pluginManager.registerEvents(classRelicService, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.SpyRevealListener(plugin, spyManager), plugin);
        pluginManager.registerEvents(professionWeeklyGoalManager, plugin);
        // Az offline bajnok-tagok függő szezon-jutalma belépéskor jár.
        pluginManager.registerEvents(seasonManager, plugin);
        pluginManager.registerEvents(new org.bukkit.event.Listener() {
            // A szállítmány-konvoj halála: a rabló frakció kasszája kapja a rakományt.
            @org.bukkit.event.EventHandler
            public void onConvoyDeath(final org.bukkit.event.entity.EntityDeathEvent event) {
                if (playerCaravanManager.isConvoy(event.getEntity().getUniqueId())) {
                    playerCaravanManager.onConvoyKilled(event.getEntity().getKiller());
                }
                // DARK undead-népesség könyvelése (a jelölt undead kiesett).
                if (darkUndeadAmbienceManager.isMarked(event.getEntity())) {
                    darkUndeadAmbienceManager.onDeath(event.getEntity().getUniqueId());
                }
                // Kultista esemény könyvelése (portya/rítus/hírvivő zárása).
                if (cultistEventManager.isCultist(event.getEntity())) {
                    cultistEventManager.onDeath(event.getEntity().getUniqueId());
                }
            }
        }, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.MobMoneyDropListener(
                plugin, configManager, mobScalingManager, moneyPouchItemFactory, afkManager,
                itemIdentityService), plugin);
        final hu.taliann.icesmp.listeners.DungeonGateListener dungeonGateListener =
                new hu.taliann.icesmp.listeners.DungeonGateListener(plugin, configManager, territoryManager, messageManager);
        dungeonGateListener.setPartyManager(partyManager);
        pluginManager.registerEvents(dungeonGateListener, plugin);
        pluginManager.registerEvents(new TalentAttributeListener(plugin, talentManager), plugin);
        final TerritoryListener territoryListener = new TerritoryListener(territoryManager, territoryProtectionService, configManager, questManager, messageManager);
        territoryListener.setBestiaryManager(bestiaryManager); // territórium-lajstrom
        pluginManager.registerEvents(territoryListener, plugin);
        final TerritoryProtectionListener territoryProtectionListener = new TerritoryProtectionListener(territoryProtectionService);
        territoryProtectionListener.setBlockRegenService(blockRegenService);
        pluginManager.registerEvents(territoryProtectionListener, plugin);
        pluginManager.registerEvents(new QuestProgressListener(plugin, questManager, mobScalingManager, worldBossManager, communityGoalManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.QuestLogListener(questManager, messageManager), plugin);
        pluginManager.registerEvents(questBuilderListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ShopListener(shopManager, currencyManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CaravanListener(caravanManager, shopManager, currencyManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.GatheringBuffListener(gatheringBuffManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.TreasureListener(treasureEventManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.WildHuntListener(wildHuntManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.AbundanceListener(abundanceManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ServerChallengeListener(serverChallengeManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.EscortListener(escortManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.PartyListener(plugin, partyManager), plugin);
        final hu.taliann.icesmp.listeners.ClaimProtectionListener claimProtectionListener =
                new hu.taliann.icesmp.listeners.ClaimProtectionListener(claimManager, configManager, factionManager, raidManager, messageManager);
        claimProtectionListener.setBlockRegenService(blockRegenService);
        pluginManager.registerEvents(claimProtectionListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ClaimTrustGUIListener(claimManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ChatFormatListener(configManager, hudManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ChatModerationListener(
                plugin, configManager, moderationManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ModerationLoginListener(
                moderationManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.VanishListener(
                plugin, moderationManager, vanishManager, configManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.EventSpawnGuardListener(eventSpawnGuard), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.InvseeGUIListener(invseeManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ModerationGUIListener(plugin, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ReportFeedbackListener(reportManager), plugin);
        pluginManager.registerEvents(new MinionProtectionListener(minionManager), plugin);
        pluginManager.registerEvents(new PetCommandListener(minionManager, petManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.PetGUIListener(petManager, messageManager), plugin);
        final PetXpListener petXpListener = new PetXpListener(plugin, petManager, configManager);
        petXpListener.setCaptureItemFactory(captureItemFactory);
        petXpListener.setAfkManager(afkManager);
        pluginManager.registerEvents(petXpListener, plugin);
        pluginManager.registerEvents(new PetCaptureListener(petManager, captureItemFactory, messageManager), plugin);
        pluginManager.registerEvents(new PetCombatListener(plugin, petManager), plugin);
        pluginManager.registerEvents(new DailyQuestListener(plugin, dailyQuestManager), plugin);
        pluginManager.registerEvents(new ParkourListener(parkourManager), plugin);
        final SinListener sinListener = new SinListener(plugin, sinManager, raidManager, factionManager, territoryManager, statsManager, currencyManager, configManager, messageManager);
        sinListener.setHonorDuelManager(honorDuelManager); // párbaj-kill kizárás
        sinListener.setWarWindowManager(warWindowManager); // hadi-ablak: RED↔BLUE kill nem bűn
        pluginManager.registerEvents(sinListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CombatTagListener(combatTagManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ItemProvenanceListener(plugin), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.DungeonLootListener(afkManager, dungeonLootService, territoryManager, configManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ArcheologyShareListener(archeologyManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.HealthRegenListener(classHealthService), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.SchoolCounterAnvilListener(), plugin);
        pluginManager.registerEvents(new TheftListener(sinManager, territoryManager, factionManager, raidManager, configManager, messageManager), plugin);
        pluginManager.registerEvents(new SoulstoneListener(currencyManager, mobScalingManager, bloodMoonManager, configManager, factionManager, afkManager), plugin);
        pluginManager.registerEvents(new WorldBossListener(worldBossManager, configManager, afkManager), plugin);
        pluginManager.registerEvents(encounterRewardDelivery, plugin);
        pluginManager.registerEvents(new IntroListener(introManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.OnboardingListener(plugin, configManager, questManager, messageManager), plugin);
        pluginManager.registerEvents(new FactionSpawnListener(factionManager, territoryManager, configManager), plugin);
        pluginManager.registerEvents(new SiegeWeaponListener(plugin, siegeWeaponFactory, raidManager, configManager, messageManager), plugin);
        final SoulShardListener soulShardListener = new SoulShardListener(plugin, soulShardManager, specializationManager, configManager);
        soulShardListener.setAfkManager(afkManager);
        pluginManager.registerEvents(soulShardListener, plugin);
        pluginManager.registerEvents(new RitualListener(ritualManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.WorldGameRuleListener(configManager), plugin);
        // Plugin-leépítés: ICEsmpadditions + FarmProtect + MiniMOTD natív kiváltása
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.WorldTweaksListener(configManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.DisplayFxCleanupListener(), plugin);
        pluginManager.registerEvents(motdListener, plugin);
        // Harci erőforrás-töltés, sebzés-számok, halál-összegzés
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ResourceCombatListener(resourceManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.StatsCombatListener(statsManager), plugin);
        // A sebzés-szám listener a kombó-boost jelzéshez a katalizátor-listenert,
        // a HUD célpont-sora pedig ezt a listenert olvassa.
        final hu.taliann.icesmp.listeners.DamageIndicatorListener damageIndicators =
                new hu.taliann.icesmp.listeners.DamageIndicatorListener(plugin, configManager,
                        abilityCatalystListener, resourceManager, jobManager, mobTemplateRegistry);
        pluginManager.registerEvents(damageIndicators, plugin);
        hudManager.setDamageIndicators(damageIndicators);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.DeathRecapListener(configManager, messageManager), plugin);
        if (relicManager.isEnabled()) {
            pluginManager.registerEvents(new RelicCraftSafetyListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicInactivityListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicItemRefreshListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicTriggerListener(relicManager), plugin);
            pluginManager.registerEvents(new MetelytepoRelicListener(plugin, metelytepoManager, sinManager,
                    worldBossManager, invasionManager, messageManager), plugin);
            pluginManager.registerEvents(new ElytraRelicListener(plugin, relicManager, factionManager, configManager, messageManager), plugin);
            pluginManager.registerEvents(new RelicPvpTransferListener(plugin, relicManager, configManager, messageManager), plugin);
        }
    }
}
