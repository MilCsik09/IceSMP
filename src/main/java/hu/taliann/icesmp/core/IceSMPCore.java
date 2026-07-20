package hu.taliann.icesmp.core;

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
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Core initialization and management for the IceSMP plugin.
 * Handles lifecycle, manager initialization, event listener registration,
 * and command registration.
 */
public final class IceSMPCore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final JobManager jobManager;
    private final SpellRegistry spellRegistry;
    private final CatalystItemFactory catalystItemFactory;
    private final hu.taliann.icesmp.managers.ResourceManager resourceManager;
    private final hu.taliann.icesmp.managers.SpellFavoritesManager spellFavoritesManager;
    private final AbilityCatalystListener abilityCatalystListener;
    private final hu.taliann.icesmp.listeners.QuestBuilderListener questBuilderListener;
    private final SpellMasteryManager spellMasteryManager;
    private final PlayerSessionCleanupListener playerSessionCleanupListener;
    private final RelicManager relicManager;
    private final MetelytepoManager metelytepoManager;
    private final SinManager sinManager;
    private final MinionManager minionManager;
    private final hu.taliann.icesmp.managers.TotemManager totemManager;
    private final MobScalingManager mobScalingManager;
    private final InvasionManager invasionManager;
    private final CaptureItemFactory captureItemFactory;
    private final PetManager petManager;
    private final DailyQuestManager dailyQuestManager;
    private final ParkourManager parkourManager;
    private final ProfessionManager professionManager;
    private final ProfessionRecipeManager professionRecipeManager;
    private final ItemRarityService itemRarityService;
    private final hu.taliann.icesmp.managers.ProfessionRecipeCatalog professionRecipeCatalog;
    private final hu.taliann.icesmp.items.BlueprintItemFactory blueprintItemFactory;
    private final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterialFactory;
    private final hu.taliann.icesmp.items.MoneyPouchItemFactory moneyPouchItemFactory;
    private final hu.taliann.icesmp.managers.GuildManager guildManager;
    private final hu.taliann.icesmp.managers.PlayerCaravanManager playerCaravanManager;
    private final hu.taliann.icesmp.managers.BestiaryManager bestiaryManager;
    private final hu.taliann.icesmp.managers.SoulforgeManager soulforgeManager;
    private final hu.taliann.icesmp.managers.ResourceBonusService resourceBonusService;
    private final hu.taliann.icesmp.managers.HonorDuelManager honorDuelManager;
    private final hu.taliann.icesmp.managers.SpyManager spyManager;
    private final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager professionWeeklyGoalManager;
    private final hu.taliann.icesmp.listeners.ProfessionRecipeBookListener professionRecipeBookListener;
    private final hu.taliann.icesmp.listeners.FactionFoodListener factionFoodListener;
    private final hu.taliann.icesmp.managers.WhisperManager whisperManager;
    private final hu.taliann.icesmp.managers.ChronicleManager chronicleManager;
    private final hu.taliann.icesmp.managers.CorruptionManager corruptionManager;
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
    private final CharacterMenuContext characterMenuContext;
    private final CommandMenuContext commandMenuContext;
    private final HudManager hudManager;
    private final List<hu.taliann.icesmp.storage.PersistentStore> persistentStores;
    private final StatsManager statsManager;
    private final AchievementManager achievementManager;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask taxTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask questNpcMarkerTask;
    private hu.taliann.icesmp.integration.FancyNpcsQuestBridge npcQuestBridge;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask economyEventTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask worldEventsTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask hudTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask tablistTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask afkTask;
    private final hu.taliann.icesmp.utils.TextAnimator textAnimator;
    private final hu.taliann.icesmp.managers.TablistManager tablistManager;
    private final hu.taliann.icesmp.managers.AfkManager afkManager;
    private final hu.taliann.icesmp.managers.SitManager sitManager;
    private final hu.taliann.icesmp.items.CrateKeyFactory crateKeyFactory;
    private final hu.taliann.icesmp.managers.CrateManager crateManager;
    private final hu.taliann.icesmp.managers.ReportManager reportManager;
    private final hu.taliann.icesmp.managers.ModerationManager moderationManager;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask petTask;

    public IceSMPCore(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = new ConfigManager(plugin);
        // A config MÁR A KONSTRUKTOR-LÁNC ELŐTT betöltődik: több world-event manager a saját
        // konstruktorában számol első időablakot (nextAttemptAt) config-kulcsból — betöltés
        // nélkül a kódbeli fallbackot kapnák a yml-ben beállított érték helyett (audit-hiba:
        // az első ablak restart után rövidebb/hosszabb volt a beállítottnál). Az enable()
        // load()-ja emiatt már csak frissítés (idempotens).
        configManager.load();
        this.messageManager = new MessageManager(plugin, configManager);
        this.currencyManager = new CurrencyManager(plugin, configManager);
        this.factionManager = new FactionManager(plugin, configManager);
        this.jobManager = new JobManager(plugin, configManager, messageManager, factionManager);
        this.spellRegistry = new SpellRegistry();
        // Statikus bekötés a spell-iskola feloldáshoz (SpellDamageUtil — minta: ProtectionBridge).
        hu.taliann.icesmp.utils.SpellDamageUtil.init(configManager, jobManager);
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
        this.territoryManager = new TerritoryManager(plugin);
        this.territoryProtectionService = new TerritoryProtectionService(plugin, configManager, territoryManager, factionManager, messageManager);
        this.raidManager = new RaidManager(plugin, configManager, factionManager, factionTreasuryManager, seasonManager, territoryManager, messageManager);
        this.worldBossManager = new WorldBossManager(plugin, configManager, messageManager, factionManager, factionTreasuryManager, seasonManager);
        this.introManager = new IntroManager(plugin, configManager);
        this.mobScalingManager = new MobScalingManager(plugin, configManager, bloodMoonManager, territoryManager);
        this.invasionManager = new InvasionManager(plugin, configManager, mobScalingManager, messageManager);
        this.professionManager = new ProfessionManager(plugin, configManager);
        this.professionRecipeManager = new ProfessionRecipeManager(plugin, configManager);
        this.itemRarityService = new ItemRarityService(plugin, configManager);
        this.professionRecipeCatalog = new hu.taliann.icesmp.managers.ProfessionRecipeCatalog(plugin, configManager);
        this.blueprintItemFactory = new hu.taliann.icesmp.items.BlueprintItemFactory(plugin, professionRecipeCatalog);
        this.uniqueMaterialFactory = new hu.taliann.icesmp.items.UniqueMaterialFactory(plugin, configManager);
        this.moneyPouchItemFactory = new hu.taliann.icesmp.items.MoneyPouchItemFactory(plugin);
        this.guildManager = new hu.taliann.icesmp.managers.GuildManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.playerCaravanManager = new hu.taliann.icesmp.managers.PlayerCaravanManager(plugin, configManager, factionTreasuryManager, factionManager, eventSpawnGuard, messageManager);
        this.bestiaryManager = new hu.taliann.icesmp.managers.BestiaryManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.soulforgeManager = new hu.taliann.icesmp.managers.SoulforgeManager(plugin, configManager, soulShardManager);
        this.resourceBonusService = new hu.taliann.icesmp.managers.ResourceBonusService(plugin, configManager, jobManager, relicManager);
        this.honorDuelManager = new hu.taliann.icesmp.managers.HonorDuelManager(plugin, configManager, sinManager);
        this.spyManager = new hu.taliann.icesmp.managers.SpyManager(plugin, configManager, raidManager, messageManager);
        this.professionWeeklyGoalManager = new hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager(plugin, configManager, professionManager, messageManager);
        resourceManager.setMaxMultiplier(resourceBonusService::maxMultiplier); // E25/E32 — pool-bónuszok
        ritualManager.setPaktDependencies(resourceBonusService, uniqueMaterialFactory); // E25 — pakt-oltár
        hu.taliann.icesmp.spells.SummonMinionsSpell.setSoulforge(soulforgeManager); // E1 — statikus híd
        this.professionRecipeBookListener = new hu.taliann.icesmp.listeners.ProfessionRecipeBookListener(plugin,
                professionManager, professionRecipeCatalog, itemRarityService, uniqueMaterialFactory, messageManager, factionManager, configManager);
        this.factionFoodListener = new hu.taliann.icesmp.listeners.FactionFoodListener(plugin, configManager, factionManager, messageManager);
        this.craftingRestrictionManager = new CraftingRestrictionManager(plugin, configManager, jobManager, professionManager);
        this.economyEventManager = new EconomyEventManager(plugin, configManager, messageManager);
        this.exchangeRateService = new ExchangeRateService(configManager, currencyManager, economyEventManager);
        this.factionRelationManager = new FactionRelationManager(configManager, raidManager);
        this.marketManager = new MarketManager(plugin, configManager, currencyManager, factionManager, factionRelationManager, messageManager);
        this.donationChestManager = new DonationChestManager(plugin, configManager);
        this.questManager = new QuestManager(plugin, configManager, messageManager, jobManager,
                currencyManager, factionManager, sinManager, seasonManager);
        this.communityGoalManager = new CommunityGoalManager(plugin, configManager, factionManager,
                factionTreasuryManager, messageManager);
        this.shopManager = new ShopManager(configManager, currencyManager, factionManager, messageManager);
        this.npcBindingManager = new NpcBindingManager(plugin);
        this.caravanManager = new CaravanManager(plugin, configManager, messageManager);
        this.ambientEventManager = new AmbientEventManager(plugin, configManager, messageManager, currencyManager, factionManager);
        this.gatheringBuffManager = new GatheringBuffManager(plugin, configManager, messageManager);
        this.partyManager = new PartyManager(plugin, configManager, messageManager);
        this.claimManager = new ClaimManager(plugin, configManager, currencyManager, factionManager, territoryManager);
        // Közös, esemény×védelem mátrixszal configolható spawn-hely szabályok (world-events.
        // spawn-rules) minden világeseménynek. A világboss/invázió/vad hajsza setter-t kap,
        // mert a DI-sorrendben a ClaimManager ELŐTT épülnek.
        final hu.taliann.icesmp.managers.EventSpawnGuard eventSpawnGuard =
                new hu.taliann.icesmp.managers.EventSpawnGuard(configManager, territoryManager, claimManager);
        worldBossManager.setSpawnGuard(eventSpawnGuard);
        invasionManager.setSpawnGuard(eventSpawnGuard);
        this.treasureEventManager = new TreasureEventManager(plugin, configManager, partyManager, eventSpawnGuard, messageManager);
        this.wildHuntManager = new WildHuntManager(plugin, configManager, mobScalingManager, partyManager, messageManager);
        this.abundanceManager = new AbundanceManager(plugin, configManager, messageManager);
        this.serverChallengeManager = new ServerChallengeManager(plugin, configManager, messageManager);
        this.escortManager = new EscortManager(plugin, configManager, mobScalingManager, messageManager);
        this.meteorEventManager = new MeteorEventManager(plugin, configManager, eventSpawnGuard, messageManager);
        wildHuntManager.setSpawnGuard(eventSpawnGuard);
        this.corruptionManager = new hu.taliann.icesmp.managers.CorruptionManager(plugin, configManager, mobScalingManager, eventSpawnGuard, messageManager);
        this.archeologyManager = new hu.taliann.icesmp.managers.ArcheologyManager(plugin, configManager, eventSpawnGuard, uniqueMaterialFactory, messageManager);
        // A loot-táblák "unique:<id>" sorai a UniqueMaterialFactory-n át épülnek (statikus híd).
        hu.taliann.icesmp.managers.LootTable.setUniqueFactory(uniqueMaterialFactory);
        professionManager.setMessageManager(messageManager); // szintlépés/fokozat üzenetek
        questManager.setGuildManager(guildManager); // B35 — quest-teljesítés céh-XP
        professionRecipeBookListener.setBestiaryManager(bestiaryManager); // B21 — recept-lajstrom
        professionRecipeBookListener.setJobManager(jobManager); // E7 — kaszt-zárt receptek
        // Vendor-only unique anyagok a boltokban (economy.yml `unique:` bolt-item mező).
        shopManager.setUniqueMaterialFactory(uniqueMaterialFactory);
        // D19 — a Rejtélyes Idegen (tisztán atmoszférikus, ritka felbukkanás).
        this.strangerNpcManager = new hu.taliann.icesmp.managers.StrangerNpcManager(plugin, configManager, messageManager);
        strangerNpcManager.setSpawnGuard(eventSpawnGuard);
        // B19 — évszakos világ-modifikátorok: a valós évszak finom esély-szorzói.
        final hu.taliann.icesmp.managers.SeasonalModifierService seasonalModifiers =
                new hu.taliann.icesmp.managers.SeasonalModifierService(configManager);
        bloodMoonManager.setSeasonalModifiers(seasonalModifiers);
        worldBossManager.setSeasonalModifiers(seasonalModifiers);
        invasionManager.setSeasonalModifiers(seasonalModifiers);
        wildHuntManager.setSeasonalModifiers(seasonalModifiers);
        abundanceManager.setSeasonalModifiers(seasonalModifiers);
        gatheringBuffManager.setSeasonalModifiers(seasonalModifiers);
        // B33 — szezonzáró finálé: a fogyasztók (season/boss/vérhold/invázió) setterrel kapják,
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
        this.resourceManager = new hu.taliann.icesmp.managers.ResourceManager(plugin, configManager, jobManager, specializationManager);
        this.talentManager = new TalentManager(plugin, configManager, jobManager, professionManager, specializationManager);
        this.spellFavoritesManager = new hu.taliann.icesmp.managers.SpellFavoritesManager(plugin);
        this.abilityCatalystListener = new AbilityCatalystListener(plugin, jobManager, spellRegistry,
                catalystItemFactory, configManager, spellMasteryManager, specializationManager, resourceManager,
                talentManager, messageManager, spellFavoritesManager);
        this.questBuilderListener = new hu.taliann.icesmp.listeners.QuestBuilderListener(plugin, questManager, messageManager);
        this.petManager = new PetManager(plugin, configManager, minionManager, specializationManager, messageManager);
        this.dailyQuestManager = new DailyQuestManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.parkourManager = new ParkourManager(plugin, currencyManager, factionManager, messageManager);
        this.siegeWeaponFactory = new SiegeWeaponFactory(plugin);
        this.soulShardManager = new SoulShardManager(plugin, configManager, minionManager, messageManager);
        this.ritualManager = new RitualManager(plugin, configManager, relicManager, sinManager, factionManager,
                territoryManager, jobManager, messageManager);
        this.exchangeBoardManager = new ExchangeBoardManager(plugin, configManager, exchangeRateService);
        this.characterMenuContext = new CharacterMenuContext(messageManager, jobManager, specializationManager,
                professionManager, talentManager, factionManager, currencyManager, sinManager,
                catalystItemFactory, spellRegistry, configManager);
        this.statsManager = new StatsManager(plugin, jobManager, currencyManager);
        this.chronicleManager = new hu.taliann.icesmp.managers.ChronicleManager(plugin, configManager, statsManager, seasonManager, messageManager);
        // D17 — korszakváltás-narratíva: a szezonzárás hookja (a StatsManager itt már él).
        seasonManager.setStoryTeller(new hu.taliann.icesmp.managers.SeasonStoryTeller(
                plugin, configManager, statsManager, messageManager));
        // D9 — Énekmondó: a heti balladát a FancyNpcs interact-hook (registerNpcQuestBridge) köti a bárd-NPC-re.
        this.bardManager = new hu.taliann.icesmp.managers.BardManager(configManager, statsManager, messageManager);
        // Felvásárló NPC: napi keretes nyersanyag-eladás (jövedelem-csap; szintén interact-hook).
        this.buyerService = new hu.taliann.icesmp.managers.BuyerService(plugin, configManager, currencyManager, factionManager, messageManager);
        // D3 — Szezon-emlékmű: a bajnok kőbe vésése a szezonzárás-hookon.
        this.seasonMonumentManager = new hu.taliann.icesmp.managers.SeasonMonumentManager(plugin, configManager, statsManager);
        seasonManager.setMonumentManager(seasonMonumentManager);
        // B54 — Átkozott felszerelés: curse-stamp a boss-lootra + Átok-törés az oltárnál.
        this.cursedGearService = new hu.taliann.icesmp.managers.CursedGearService(plugin, configManager);
        ritualManager.setCursedGearService(cursedGearService);
        // F13/F14/F15 — gazdasági események: pánik-ág + konjunktúra díj-ablak + finálé-sokkok.
        marketManager.setEconomyEventManager(economyEventManager);
        economyEventManager.setSeasonFinale(seasonFinaleManager);
        // D8 — felfedezhető titkos helyek (admin-kijelölt pontok, első-felfedező jutalom).
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
        this.afkManager = new hu.taliann.icesmp.managers.AfkManager(plugin, configManager, currencyManager, messageManager);
        this.sitManager = new hu.taliann.icesmp.managers.SitManager(plugin);
        this.reportManager = new hu.taliann.icesmp.managers.ReportManager(plugin, messageManager);
        this.moderationManager = new hu.taliann.icesmp.managers.ModerationManager(plugin, configManager, messageManager);
        this.crateKeyFactory = new hu.taliann.icesmp.items.CrateKeyFactory(plugin, configManager);
        this.crateManager = new hu.taliann.icesmp.managers.CrateManager(plugin, configManager, currencyManager, crateKeyFactory, messageManager);
        // A quest "rewards.crate-key" mezője setterrel kap CrateKeyFactory-t
        // (CrateKeyFactory a DI-sorrendben a QuestManager UTÁN épül).
        questManager.setCrateKeyFactory(crateKeyFactory);
        this.textAnimator = new hu.taliann.icesmp.utils.TextAnimator(configManager);
        this.hudManager = new HudManager(plugin, configManager, factionManager, currencyManager, jobManager,
                raidManager, bloodMoonManager, worldBossManager, resourceManager, partyManager,
                caravanManager, escortManager, abundanceManager, serverChallengeManager,
                meteorEventManager, gatheringBuffManager, textAnimator, seasonManager, dailyQuestManager);
        this.tablistManager = new hu.taliann.icesmp.managers.TablistManager(plugin, configManager,
                factionManager, textAnimator, afkManager);
        // Relációs háború-színek a tablistában (raid alatt az ellenség piros).
        this.tablistManager.setRaidManager(raidManager);
        // One registered list of YAML-persistent managers: the core loads them all on enable and
        // saves them all on disable (replacing two hand-maintained call lists).
        this.persistentStores = List.of(currencyManager, factionManager, relicManager, territoryManager,
                factionTreasuryManager, kingManager, economyEventManager, marketManager, seasonManager,
                exchangeBoardManager, statsManager, parkourManager, questManager, communityGoalManager,
                claimManager, donationChestManager, npcBindingManager, crateManager, reportManager,
                moderationManager, chronicleManager, corruptionManager, seasonFinaleManager,
                seasonMonumentManager, hiddenSpotManager,
                guildManager,
                professionWeeklyGoalManager);
        parkourManager.setFinishHook(questManager::handleParkourFinish);
        raidManager.setWinHook(fighter -> {
            questManager.handleRaidWin(fighter);
            communityGoalManager.contribute(fighter, "WIN_RAID", null, 1);
        });
        jobManager.setXpChangeHook(player -> {
            specializationManager.applyClassSpecializationUnlocks(player);
            questManager.handleLevelChange(player);
        });
        this.playerSessionCleanupListener = new PlayerSessionCleanupListener(
                abilityCatalystListener,
                jobManager,
                currencyManager,
                factionManager,
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
                moderationManager,
                whisperManager,
                guildManager,
                spellRegistry
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
        // Surface admin typos (bad material/currency names, out-of-range percents, negative
        // durations) as clear log warnings — never blocks startup, only reports.
        ConfigValidator.validate(configManager, plugin.getLogger());
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
        // Config-derived (load-only) managers first, then every registered persistent store.
        mobScalingManager.load();
        craftingRestrictionManager.load();
        professionRecipeCatalog.load();
        persistentStores.forEach(hu.taliann.icesmp.storage.PersistentStore::load);
        siegeWeaponFactory.registerRecipe();
        professionRecipeManager.registerRecipes();
        registerListeners();
        registerCommands();
        scheduleTaxCollection();
        scheduleEconomyEvents();
        scheduleWorldEvents();
        scheduleHud();
        schedulePetCombat();
        registerPlaceholders();
        registerNpcQuestBridge();
        applyWorldGameRules();

        plugin.getLogger().info("IceSMP core enabled.");
        plugin.getLogger().info("Available factions: " + factionManager.describeAvailableFactions());
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
                    .getMethod("register", JavaPlugin.class, HudManager.class)
                    .invoke(null, plugin, hudManager);
            plugin.getLogger().info("PlaceholderAPI integráció bekapcsolva (%icesmp_...% placeholderek).");
        } catch (final Throwable throwable) {
            plugin.getLogger().warning("PlaceholderAPI jelen van, de a placeholder-integráció nem indult: "
                    + throwable.getMessage());
        }
    }

    /**
     * Registers the FancyNpcs quest bridge (TALK_TO_NPC objectives) if FancyNpcs is
     * installed. The bridge is fully reflective, so the core has no compile-time
     * dependency on the FancyNpcs API; without the plugin this is a no-op.
     */
    private void registerNpcQuestBridge() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("FancyNpcs")) {
            return;
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
                // D9 — Énekmondó: a bard.npc-name nevű NPC jobb-kattra a heti balladát énekli
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
            plugin.getLogger().info("FancyNpcs quest-bridge bekapcsolva (TALK_TO_NPC próbák, giver-npc questek, NPC-markerek, frakció-boltok, /npcbind kötések).");
        } catch (final Throwable throwable) {
            plugin.getLogger().warning("FancyNpcs jelen van, de a quest-bridge nem indult: "
                    + throwable.getMessage());
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
                    || configManager.getBoolean("hud.sidebar-enabled", true)) {
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
        if (taxTask != null) {
            taxTask.cancel();
            taxTask = null;
        }
        if (questNpcMarkerTask != null) {
            questNpcMarkerTask.cancel();
            questNpcMarkerTask = null;
        }
        raidManager.shutdown();
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
        if (tablistTask != null) {
            tablistTask.cancel();
            tablistTask = null;
        }
        if (afkTask != null) {
            afkTask.cancel();
            afkTask = null;
        }
        if (petTask != null) {
            petTask.cancel();
            petTask = null;
        }
        worldBossManager.shutdown();
        invasionManager.shutdown();
        caravanManager.shutdown();
        treasureEventManager.shutdown();
        wildHuntManager.shutdown();
        corruptionManager.shutdown();
        archeologyManager.shutdown();
        strangerNpcManager.shutdown();
        escortManager.shutdown();
        playerCaravanManager.shutdown();
        meteorEventManager.shutdown();
        serverChallengeManager.shutdown();
        totemManager.shutdown();

        // Save ALL persistent state FIRST, before any cleanup that could mutate in-memory state.
        // (mobScalingManager / craftingRestrictionManager are config-derived read-only — no save.)
        persistentStores.forEach(hu.taliann.icesmp.storage.PersistentStore::save);
        ProfileGUI.closeAll();

        // Then clean up live player session state (HUD teams, restored armor, caches).
        for (final Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            hudManager.cleanup(onlinePlayer);
            afkManager.cleanup(onlinePlayer);
            playerSessionCleanupListener.cleanupPlayerState(onlinePlayer.getUniqueId());
        }

        plugin.getLogger().info("IceSMP core disabled.");
    }

    /**
     * Schedules the shared world-events tick (blood moon, world boss, season
     * expiry) on the global region scheduler. Each manager guards its own
     * config toggle, so this single timer drives all of section 7.
     */
    private void scheduleWorldEvents() {
        final long intervalTicks = Math.max(1L, configManager.getLong("world-events.check-interval-seconds", 60L)) * 20L;
        worldEventsTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> {
                    bloodMoonManager.tick();
                    worldBossManager.tick();
                    invasionManager.tick();
                    seasonManager.tick();
                    exchangeBoardManager.tick();
                    statsManager.tick();
                    achievementManager.tick();
                    marketManager.tickAuctions();
                    caravanManager.tick();
                    ambientEventManager.tick();
                    gatheringBuffManager.tick();
                    treasureEventManager.tick();
                    wildHuntManager.tick();
                    abundanceManager.tick();
                    serverChallengeManager.tick();
                    escortManager.tick();
                    meteorEventManager.tick();
                    factionFoodListener.tick();
                    whisperManager.tick();
                    chronicleManager.tick();
                    corruptionManager.tick();
                    archeologyManager.tick();
                    seasonFinaleManager.tick();
            playerCaravanManager.tick();
            professionWeeklyGoalManager.tick();
                    strangerNpcManager.tick();
                    hiddenSpotManager.tick();
                },
                intervalTicks,
                intervalTicks
        );
    }

    /**
     * Schedules the live HUD refresh (sidebar, tab-list, boss-bars) on the global
     * region scheduler; the manager hops to each player's region thread.
     */
    private void scheduleHud() {
        if (!configManager.getBoolean("hud.enabled", true)) {
            return;
        }

        final long intervalTicks = Math.max(5L, configManager.getLong("hud.refresh-ticks", 20L));
        hudTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> hudManager.tick(), intervalTicks, intervalTicks);
        // IDEAS A54: a tablist {event} tokenje a HUD-snapshotból olvas.
        tablistManager.setHudManager(hudManager);
        // Natív tablist (TAB-kiváltás): saját, gyorsabb tick — a header/footer és a tab-nevek
        // diff-eltek, így a sűrűbb ütem csak valódi változáskor jelent csomagot.
        final long tablistTicks = Math.max(5L, configManager.getLong("tablist.refresh-ticks", 10L));
        tablistTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> tablistManager.tick(), tablistTicks, tablistTicks);
        // Natív AFK-rendszer (AxAFKZone-kiváltás): zóna-jutalom + bossbar tick.
        final long afkTicks = Math.max(5L, configManager.getLong("afk.refresh-ticks", 20L));
        afkTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> afkManager.tick(), afkTicks, afkTicks);
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
        plugin.getLogger().info("Faction tax scheduled every "
                + configManager.getLong("factions.tax.interval-minutes", 60L) + " minute(s).");
    }

    /**
     * Schedules the periodic economy-event tick (demand shocks) on the global
     * region scheduler.
     */
    private void scheduleEconomyEvents() {
        if (!configManager.getBoolean("currency.economy-event.enabled", true)) {
            return;
        }

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
        // GUI-s config-menü (/icesmp config menu): kategorizált, kattintható felület a
        // leggyakoribb kulcsokhoz — az override-fájlba ír, restart nélkül él.
        final hu.taliann.icesmp.listeners.ConfigMenuGUIListener configMenuGUIListener =
                new hu.taliann.icesmp.listeners.ConfigMenuGUIListener(plugin, configManager, messageManager);
        plugin.getServer().getPluginManager().registerEvents(configMenuGUIListener, plugin);
        iceSMPCommand.setConfigMenuOpener(configMenuGUIListener::open);
        plugin.registerCommand("icesmp", "IceSMP admin", List.of("ismp"), iceSMPCommand);
        plugin.registerCommand("invsee", "Inventory-betekintés (admin, csak olvasás)", List.of(),
                new hu.taliann.icesmp.commands.InvseeCommand(plugin, messageManager));
        plugin.registerCommand("hud", "HUD beállítások", List.of(), new hu.taliann.icesmp.commands.HudCommand(hudManager, messageManager));
        plugin.registerCommand("stats", "Statisztika-profil", List.of(), new hu.taliann.icesmp.commands.StatsCommand(statsManager, messageManager));
        plugin.registerCommand("sit", "Ülés (leül/feláll)", List.of(), new hu.taliann.icesmp.commands.SitCommand(sitManager, messageManager));
        plugin.registerCommand("afk", "Önkéntes AFK-jelölés", List.of(), new hu.taliann.icesmp.commands.AfkCommand(afkManager, messageManager));
        plugin.registerCommand("crate", "Láda (crate) parancsok", List.of("ladak", "crates"),
                new hu.taliann.icesmp.commands.CrateCommand(plugin, crateManager, crateKeyFactory, currencyManager, messageManager));
        plugin.registerCommand("report", "Játékos bejelentése (admin: /reports)", List.of("bejelent"),
                new hu.taliann.icesmp.commands.ReportCommand(reportManager, messageManager));
        plugin.registerCommand("reports", "Bejelentések kezelése (admin)", List.of(),
                new hu.taliann.icesmp.commands.ReportsCommand(reportManager, messageManager));
        plugin.registerCommand("mute", "Némítás (admin)", List.of(),
                new hu.taliann.icesmp.commands.MuteCommand(plugin, moderationManager, messageManager));
        plugin.registerCommand("unmute", "Némítás feloldása (admin)", List.of(),
                new hu.taliann.icesmp.commands.UnmuteCommand(plugin, moderationManager, messageManager));
        plugin.registerCommand("currency", "Valuta parancsok", List.of("money", "eco"), new CurrencyCommand(currencyManager, configManager, exchangeRateService, territoryManager, messageManager));
        plugin.registerCommand("bank", "Bank parancsok", List.of("wallet", "vault"), new BankCommand(currencyManager, configManager, territoryManager, messageManager));
        plugin.registerCommand("faction", "Frakció parancsok", List.of("f"), new FactionCommand(plugin, factionManager, sinManager, factionTreasuryManager, currencyManager, kingManager, raidManager, territoryManager, configManager, playerCaravanManager, messageManager));
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
        plugin.registerCommand("profession", "Szakma (profession) parancsok", List.of("prof", "szakma"), new ProfessionCommand(plugin, professionManager, messageManager, professionRecipeBookListener, professionRecipeCatalog, blueprintItemFactory));
        plugin.registerCommand("spec", "Specializáció parancsok", List.of("specialization", "specializacio"), new SpecCommand(plugin, specializationManager, jobManager, professionManager, currencyManager, factionManager, talentManager, messageManager));
        plugin.registerCommand("talent", "Talent-fa parancsok", List.of("talents", "talentfa"), new TalentCommand(talentManager, messageManager));
        plugin.registerCommand("territory", "Frakció terület parancsok", List.of("terulet"), new TerritoryCommand(plugin, territoryManager, messageManager));
        plugin.registerCommand("quest", "Küldetés parancsok", List.of("quests", "kuldetes"), new QuestCommand(plugin, questManager, messageManager, questBuilderListener));
        plugin.registerCommand("market", "Piactér parancsok", List.of("piac", "ah"), new MarketCommand(marketManager, currencyManager, factionManager, messageManager));
        plugin.registerCommand("adomany", "Közösségi adomány-láda", List.of("donate", "adomanylada"), new DonationChestCommand(donationChestManager, messageManager));
        plugin.registerCommand("party", "Party (csapat) parancsok", List.of("p", "parti"), new hu.taliann.icesmp.commands.PartyCommand(partyManager, messageManager));
        plugin.registerCommand("ceh", "Céh (frakción belüli kisközösség) parancsok", List.of("guild", "gild"),
                new hu.taliann.icesmp.commands.GuildCommand(plugin, guildManager, messageManager));
        plugin.registerCommand("bestiarium", "Bestiárium — a krónikás-lajstromod", List.of("bestiary", "lajstrom"),
                new hu.taliann.icesmp.commands.BestiaryCommand(bestiaryManager, professionRecipeCatalog, territoryManager, messageManager));
        plugin.registerCommand("soulforge", "Lélek-kovács — a Nekromanta minion-fejlesztései", List.of("lelekkovacs"),
                new hu.taliann.icesmp.commands.SoulforgeCommand(soulforgeManager, soulShardManager, messageManager));
        plugin.registerCommand("parbaj", "Becsület-párbaj — elégtétel a bűnökért", List.of("duel"),
                new hu.taliann.icesmp.commands.HonorDuelCommand(plugin, honorDuelManager, messageManager));
        plugin.registerCommand("kem", "Kém-álca — rövid felderítő álöltözet", List.of("spy"),
                new hu.taliann.icesmp.commands.SpyCommand(spyManager, messageManager));
        plugin.registerCommand("szakmacel", "Szakma-céhek heti közös céljai", List.of("weeklygoal"),
                new hu.taliann.icesmp.commands.ProfessionWeeklyCommand(professionWeeklyGoalManager, messageManager));
        plugin.registerCommand("claim", "Terület-claim parancsok", List.of("birtok"), new hu.taliann.icesmp.commands.ClaimCommand(claimManager, currencyManager, messageManager));
        final EventsCommand eventsCommand = new EventsCommand(seasonManager, bloodMoonManager, worldBossManager, invasionManager, caravanManager, ambientEventManager, gatheringBuffManager, treasureEventManager, wildHuntManager, abundanceManager, serverChallengeManager, escortManager, meteorEventManager, introManager, messageManager);
        eventsCommand.setStrangerNpcManager(strangerNpcManager);
        plugin.registerCommand("events", "Világesemény parancsok", List.of("event", "esemeny"), eventsCommand);
        plugin.registerCommand("emlek", "Emlékszilánk-beváltás (visszaemlékezés)", List.of("memory", "emlekek"),
                new hu.taliann.icesmp.commands.MemoryCommand(configManager, jobManager, talentManager, specializationManager, uniqueMaterialFactory, messageManager));
        plugin.registerCommand("suttogas", "A Suttogók titkos csatornája és tanú-vád", List.of("sutt"),
                new hu.taliann.icesmp.commands.WhisperCommand(plugin, configManager, whisperManager, messageManager));
        plugin.registerCommand("lore", "A kódex lapjai — frakciók és helyek története", List.of("kodex"),
                new hu.taliann.icesmp.commands.LoreCommand(messageManager));
        plugin.registerCommand("kronika", "Az utolsó Heti Krónika visszaolvasása", List.of("chronicle"),
                new hu.taliann.icesmp.commands.KronikaCommand(chronicleManager, messageManager));
        plugin.registerCommand("iceitem", "Plugin-item kiadása (admin): unique/recept/relikvia/tervrajz/erszeny",
                List.of("iitem", "icegive"),
                new hu.taliann.icesmp.commands.ItemGiveCommand(plugin, uniqueMaterialFactory, professionRecipeCatalog,
                        professionRecipeBookListener, relicManager, blueprintItemFactory, messageManager, moneyPouchItemFactory));
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
        pluginManager.registerEvents(new CharacterGUIListener(characterMenuContext), plugin);
        pluginManager.registerEvents(new CommandMenuListener(commandMenuContext), plugin);
        pluginManager.registerEvents(new HudListener(hudManager, tablistManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.AfkActivityListener(afkManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.SitListener(sitManager, configManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CrateListener(crateManager, crateKeyFactory, currencyManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CrateSpinGUIListener(), plugin);
        pluginManager.registerEvents(new JobGUIListener(jobManager, catalystItemFactory, specializationManager, spellRegistry, configManager, messageManager, characterMenuContext), plugin);
        pluginManager.registerEvents(new SkillTreeGUIListener(jobManager, catalystItemFactory, messageManager), plugin);
        pluginManager.registerEvents(new MarketGUIListener(plugin, marketManager, currencyManager, messageManager), plugin);
        pluginManager.registerEvents(new MarketDeliveryListener(marketManager, messageManager), plugin);
        pluginManager.registerEvents(new DonationChestListener(donationChestManager, messageManager), plugin);
        pluginManager.registerEvents(abilityCatalystListener, plugin);
        pluginManager.registerEvents(new SpellbookListener(abilityCatalystListener, spellFavoritesManager), plugin);
        pluginManager.registerEvents(new CatalystCraftSafetyListener(catalystItemFactory), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CatalystProtectionListener(catalystItemFactory, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.SignatureItemListener(plugin, configManager, messageManager, gatheringBuffManager, currencyManager, territoryManager), plugin);
        pluginManager.registerEvents(new SpellProjectileListener(plugin), plugin);
        pluginManager.registerEvents(new SpellStateListener(plugin), plugin);
        pluginManager.registerEvents(playerSessionCleanupListener, plugin);
        pluginManager.registerEvents(new MobScalingListener(mobScalingManager), plugin);
        pluginManager.registerEvents(new JobCraftRestrictionListener(craftingRestrictionManager, messageManager), plugin);
        pluginManager.registerEvents(new ClassXpListener(plugin, jobManager, mobScalingManager, configManager, talentManager, afkManager), plugin);
        final ProfessionXpListener professionXpListener = new ProfessionXpListener(professionManager, configManager, talentManager, afkManager);
        professionXpListener.setAbundanceManager(abundanceManager);
        professionXpListener.setWeeklyGoal(professionWeeklyGoalManager); // I16 — heti közös cél
        pluginManager.registerEvents(professionXpListener, plugin);
        pluginManager.registerEvents(new ProfessionRecipeListener(professionRecipeManager, professionManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.MasterworkCraftListener(professionRecipeManager, itemRarityService), plugin);
        final hu.taliann.icesmp.listeners.MobLootListener mobLootListener =
                new hu.taliann.icesmp.listeners.MobLootListener(configManager, itemRarityService, worldBossManager, invasionManager, wildHuntManager, blueprintItemFactory, professionRecipeCatalog, uniqueMaterialFactory);
        mobLootListener.setCursedGearService(cursedGearService);
        pluginManager.registerEvents(mobLootListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CursedGearListener(cursedGearService, messageManager), plugin);
        pluginManager.registerEvents(professionRecipeBookListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.BlueprintUseListener(blueprintItemFactory, professionRecipeCatalog, professionManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.UniqueMaterialProtectionListener(uniqueMaterialFactory), plugin);
        pluginManager.registerEvents(new FactionPassiveListener(factionManager, configManager), plugin);
        pluginManager.registerEvents(factionFoodListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.WhisperListener(plugin, configManager, whisperManager, factionManager, raidManager, uniqueMaterialFactory, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.SpellDamageListener(configManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CapitalLawListener(plugin, configManager, territoryManager, sinManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CorruptionListener(corruptionManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.StrangerListener(strangerNpcManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.CampfireStoryListener(plugin, configManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.FishingWindfallListener(configManager, moneyPouchItemFactory, afkManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.MoneyPouchListener(moneyPouchItemFactory, currencyManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.RuneApplyListener(uniqueMaterialFactory, configManager, messageManager), plugin);
        final hu.taliann.icesmp.listeners.RuneEffectListener runeEffectListener = new hu.taliann.icesmp.listeners.RuneEffectListener(configManager);
        runeEffectListener.setJobManager(jobManager); // E7 — Varázsló rúna-affinitás
        pluginManager.registerEvents(runeEffectListener, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.BestiaryListener(bestiaryManager, worldBossManager), plugin);
        pluginManager.registerEvents(resourceBonusService, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.SpyRevealListener(plugin, spyManager), plugin);
        pluginManager.registerEvents(professionWeeklyGoalManager, plugin);
        pluginManager.registerEvents(new org.bukkit.event.Listener() {
            // B6 — a szállítmány-konvoj halála: a rabló frakció kasszája kapja a rakományt.
            @org.bukkit.event.EventHandler
            public void onConvoyDeath(final org.bukkit.event.entity.EntityDeathEvent event) {
                if (playerCaravanManager.isConvoy(event.getEntity().getUniqueId())) {
                    playerCaravanManager.onConvoyKilled(event.getEntity().getKiller());
                }
            }
        }, plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.MobMoneyDropListener(plugin, configManager, mobScalingManager, moneyPouchItemFactory), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.DungeonGateListener(plugin, configManager, territoryManager, messageManager), plugin);
        pluginManager.registerEvents(new TalentAttributeListener(plugin, talentManager), plugin);
        final TerritoryListener territoryListener = new TerritoryListener(territoryManager, territoryProtectionService, configManager, questManager, messageManager);
        territoryListener.setBestiaryManager(bestiaryManager); // B21 — territórium-lajstrom
        pluginManager.registerEvents(territoryListener, plugin);
        pluginManager.registerEvents(new TerritoryProtectionListener(territoryProtectionService), plugin);
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
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ClaimProtectionListener(claimManager, configManager, factionManager, raidManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ClaimTrustGUIListener(claimManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ChatFormatListener(configManager, hudManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ChatModerationListener(
                configManager, moderationManager, messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.InvseeGUIListener(messageManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ReportFeedbackListener(reportManager), plugin);
        pluginManager.registerEvents(new MinionProtectionListener(minionManager), plugin);
        pluginManager.registerEvents(new PetCommandListener(minionManager, messageManager), plugin);
        pluginManager.registerEvents(new PetXpListener(plugin, petManager, configManager), plugin);
        pluginManager.registerEvents(new PetCaptureListener(petManager, captureItemFactory, messageManager), plugin);
        pluginManager.registerEvents(new PetCombatListener(plugin, petManager), plugin);
        pluginManager.registerEvents(new DailyQuestListener(plugin, dailyQuestManager), plugin);
        pluginManager.registerEvents(new ParkourListener(parkourManager), plugin);
        final SinListener sinListener = new SinListener(plugin, sinManager, raidManager, factionManager, territoryManager, statsManager, currencyManager, configManager, messageManager);
        sinListener.setHonorDuelManager(honorDuelManager); // G6 — párbaj-kill kizárás
        pluginManager.registerEvents(sinListener, plugin);
        pluginManager.registerEvents(new TheftListener(sinManager, territoryManager, factionManager, raidManager, configManager, messageManager), plugin);
        pluginManager.registerEvents(new SoulstoneListener(currencyManager, mobScalingManager, bloodMoonManager, configManager, factionManager, afkManager), plugin);
        pluginManager.registerEvents(new WorldBossListener(worldBossManager), plugin);
        pluginManager.registerEvents(new IntroListener(introManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.OnboardingListener(configManager, questManager, messageManager), plugin);
        pluginManager.registerEvents(new FactionSpawnListener(factionManager, territoryManager, configManager), plugin);
        pluginManager.registerEvents(new SiegeWeaponListener(plugin, siegeWeaponFactory, raidManager, configManager, messageManager), plugin);
        pluginManager.registerEvents(new SoulShardListener(plugin, soulShardManager, specializationManager, configManager), plugin);
        pluginManager.registerEvents(new RitualListener(ritualManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.WorldGameRuleListener(configManager), plugin);
        // Plugin-leépítés: ICEsmpadditions + FarmProtect + MiniMOTD natív kiváltása
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.WorldTweaksListener(configManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.DisplayFxCleanupListener(), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.MotdListener(plugin, configManager, bloodMoonManager, worldBossManager, seasonManager), plugin);
        // IDEAS A3/A8/A9: harci erőforrás-töltés, sebzés-számok, halál-összegzés
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ResourceCombatListener(resourceManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.StatsCombatListener(statsManager), plugin);
        // IDEAS A45/A51: a sebzés-szám listener a kombó-boost jelzéshez a katalizátor-listenert,
        // a HUD célpont-sora pedig ezt a listenert olvassa.
        final hu.taliann.icesmp.listeners.DamageIndicatorListener damageIndicators =
                new hu.taliann.icesmp.listeners.DamageIndicatorListener(plugin, configManager, abilityCatalystListener);
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
