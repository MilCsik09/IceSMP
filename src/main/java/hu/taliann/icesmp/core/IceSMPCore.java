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
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask petTask;

    /**
     * Constructs a new IceSMPCore and initializes all managers.
     *
     * @param plugin the plugin instance
     */
    public IceSMPCore(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = new ConfigManager(plugin);
        this.messageManager = new MessageManager(plugin, configManager);
        this.currencyManager = new CurrencyManager(plugin, configManager);
        this.factionManager = new FactionManager(plugin, configManager);
        this.jobManager = new JobManager(plugin, configManager, messageManager, factionManager);
        this.spellRegistry = new SpellRegistry();
        this.catalystItemFactory = new CatalystItemFactory(plugin);
        this.captureItemFactory = new CaptureItemFactory(plugin);
        this.spellMasteryManager = new SpellMasteryManager(plugin, configManager, currencyManager, factionManager);
        this.relicManager = new RelicManager(plugin, configManager);
        this.sinManager = new SinManager(plugin, configManager, messageManager, factionManager);
        this.metelytepoManager = new MetelytepoManager(plugin, sinManager);
        this.minionManager = new MinionManager(plugin);
        this.totemManager = new hu.taliann.icesmp.managers.TotemManager(plugin, configManager);
        this.factionTreasuryManager = new FactionTreasuryManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.kingManager = new KingManager(plugin, configManager, factionManager, messageManager);
        this.bloodMoonManager = new BloodMoonManager(plugin, configManager, messageManager);
        this.seasonManager = new SeasonManager(plugin, configManager, messageManager, factionTreasuryManager, factionManager);
        this.territoryManager = new TerritoryManager(plugin);
        this.raidManager = new RaidManager(plugin, configManager, factionManager, factionTreasuryManager, seasonManager, territoryManager, messageManager);
        this.worldBossManager = new WorldBossManager(plugin, configManager, messageManager, factionManager, factionTreasuryManager, seasonManager);
        this.introManager = new IntroManager(plugin, configManager);
        this.mobScalingManager = new MobScalingManager(plugin, configManager, bloodMoonManager);
        this.invasionManager = new InvasionManager(plugin, configManager, mobScalingManager, messageManager);
        this.professionManager = new ProfessionManager(plugin, configManager);
        this.professionRecipeManager = new ProfessionRecipeManager(plugin, configManager);
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
        this.treasureEventManager = new TreasureEventManager(plugin, configManager, partyManager, claimManager, territoryManager, messageManager);
        this.wildHuntManager = new WildHuntManager(plugin, configManager, mobScalingManager, partyManager, messageManager);
        this.abundanceManager = new AbundanceManager(plugin, configManager, messageManager);
        this.serverChallengeManager = new ServerChallengeManager(plugin, configManager, messageManager);
        this.escortManager = new EscortManager(plugin, configManager, mobScalingManager, messageManager);
        this.meteorEventManager = new MeteorEventManager(plugin, configManager, territoryManager, claimManager, messageManager);
        // Escort-success perk: the caravan shop sells its bonus stock while the window is open.
        this.shopManager.setEscortBonusCheck(escortManager::isBonusStockActive);
        // The caravan's stock is served through ShopManager under the reserved "caravan" name,
        // buyable only while the merchant is in town.
        this.shopManager.setCaravanActiveCheck(caravanManager::isActive);
        this.specializationManager = new SpecializationManager(plugin, configManager, messageManager,
                jobManager, professionManager, factionManager, sinManager, questManager);
        this.resourceManager = new hu.taliann.icesmp.managers.ResourceManager(plugin, configManager, jobManager, specializationManager);
        this.talentManager = new TalentManager(plugin, configManager, jobManager, professionManager, specializationManager);
        this.abilityCatalystListener = new AbilityCatalystListener(plugin, jobManager, spellRegistry,
                catalystItemFactory, configManager, spellMasteryManager, specializationManager, resourceManager,
                talentManager, messageManager);
        this.questBuilderListener = new hu.taliann.icesmp.listeners.QuestBuilderListener(plugin, questManager, messageManager);
        this.petManager = new PetManager(plugin, configManager, minionManager, specializationManager, messageManager);
        this.dailyQuestManager = new DailyQuestManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.parkourManager = new ParkourManager(plugin, currencyManager, factionManager, messageManager);
        this.siegeWeaponFactory = new SiegeWeaponFactory(plugin);
        this.soulShardManager = new SoulShardManager(plugin, configManager, minionManager, messageManager);
        this.ritualManager = new RitualManager(configManager, relicManager, sinManager, messageManager);
        this.exchangeBoardManager = new ExchangeBoardManager(plugin, configManager, exchangeRateService);
        this.characterMenuContext = new CharacterMenuContext(messageManager, jobManager, specializationManager,
                professionManager, talentManager, factionManager, currencyManager, sinManager,
                catalystItemFactory, spellRegistry, configManager);
        this.statsManager = new StatsManager(plugin, jobManager, currencyManager);
        this.achievementManager = new AchievementManager(plugin, configManager, jobManager, currencyManager,
                professionManager, factionManager, statsManager, dailyQuestManager, messageManager);
        this.commandMenuContext = new CommandMenuContext(messageManager, factionManager, currencyManager,
                exchangeRateService, factionTreasuryManager, kingManager, raidManager, questManager,
                seasonManager, bloodMoonManager, worldBossManager, caravanManager, escortManager,
                abundanceManager, serverChallengeManager, gatheringBuffManager, meteorEventManager, soulShardManager,
                specializationManager, relicManager, statsManager, achievementManager,
                partyManager, claimManager, sinManager, dailyQuestManager, configManager);
        this.hudManager = new HudManager(plugin, configManager, factionManager, currencyManager, jobManager,
                raidManager, bloodMoonManager, worldBossManager, resourceManager, partyManager,
                caravanManager, escortManager, abundanceManager, serverChallengeManager,
                meteorEventManager, gatheringBuffManager);
        // One registered list of YAML-persistent managers: the core loads them all on enable and
        // saves them all on disable (replacing two hand-maintained call lists).
        this.persistentStores = List.of(currencyManager, factionManager, relicManager, territoryManager,
                factionTreasuryManager, kingManager, economyEventManager, marketManager, seasonManager,
                exchangeBoardManager, statsManager, parkourManager, questManager, communityGoalManager,
                claimManager, donationChestManager, npcBindingManager);
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
        spellRegistry.register(new ConfusionSpell(messageManager));
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
        configManager.load();
        // Surface admin typos (bad material/currency names, out-of-range percents, negative
        // durations) as clear log warnings — never blocks startup, only reports.
        ConfigValidator.validate(configManager, plugin.getLogger());
        // Config-driven spell balance: applies config/spells-balance.yml overrides on top of every
        // declaratively-configured (ConfiguredSpell) spell. Must run after configManager.load() and
        // after registerSpells() (already done in the constructor) — spells register only once at
        // startup, so this is the single application point; changing spells-balance.yml needs a restart.
        // A statikus (kódolt) spellek viszont cast-időben olvassák a felülbírálásokat innen,
        // így rájuk a /icesmp reload is azonnal hat.
        hu.taliann.icesmp.spells.BaseSpell.setBalanceSource(configManager);
        applySpellBalanceOverrides();
        adviseOnPluginCompatibility();
        messageManager.reload();
        // Config-derived (load-only) managers first, then every registered persistent store.
        mobScalingManager.load();
        craftingRestrictionManager.load();
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
    static void disableLocatorBar(final org.bukkit.World world) {
        final org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName("locatorBar");
        if (rule != null && rule.getType() == Boolean.class) {
            world.setGameRule((org.bukkit.GameRule<Boolean>) rule, false);
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
                }
            });
            // /npcbind <npc> bank|exchange: both open the existing bank menu — the deposit/withdraw/
            // exchange buttons there are already gated by the banking.capital-only config.
            npcQuestBridge.setBankOpenHook(player ->
                    hu.taliann.icesmp.gui.CommandMenus.openBank(player, commandMenuContext));
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
            if (configManager.getBoolean("hud.sidebar-enabled", true)) {
                plugin.getLogger().warning("TAB észlelve, de a hud.sidebar-enabled még true — a két scoreboard ütközni fog!"
                        + " Ajánlott: general.yml → hud.sidebar-enabled: false, és a TAB-ban a %icesmp_...% placeholderek"
                        + " (party-HUD: %icesmp_party_size%, %icesmp_party_1..5%).");
            }
            if (configManager.getBoolean("hud.tablist-enabled", true)) {
                plugin.getLogger().warning("TAB észlelve, de a hud.tablist-enabled még true — a tab-lista neveken osztozni fognak."
                        + " Ajánlott: general.yml → hud.tablist-enabled: false (frakció-szín a TAB-ból, %icesmp_faction%).");
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
        if (petTask != null) {
            petTask.cancel();
            petTask = null;
        }
        worldBossManager.shutdown();
        invasionManager.shutdown();
        caravanManager.shutdown();
        treasureEventManager.shutdown();
        wildHuntManager.shutdown();
        escortManager.shutdown();
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
        plugin.registerCommand("icesmp", "IceSMP admin", List.of("ismp"), new IceSMPCommand(configManager, messageManager));
        plugin.registerCommand("currency", "Valuta parancsok", List.of("money", "eco"), new CurrencyCommand(currencyManager, configManager, exchangeRateService, territoryManager, messageManager));
        plugin.registerCommand("bank", "Bank parancsok", List.of("wallet", "vault"), new BankCommand(currencyManager, configManager, territoryManager, messageManager));
        plugin.registerCommand("faction", "Frakció parancsok", List.of("f"), new FactionCommand(factionManager, sinManager, factionTreasuryManager, currencyManager, kingManager, raidManager, territoryManager, messageManager));
        plugin.registerCommand("class", "Kaszt (class): szint, katalizátor, admin", List.of("kaszt", "job"), new JobCommand(plugin, jobManager, spellRegistry, catalystItemFactory, abilityCatalystListener, specializationManager, messageManager));
        plugin.registerCommand("menu", "Központi menü — minden parancs egy helyen", List.of("hub", "m"), new MenuCommand(commandMenuContext, messageManager));
        plugin.registerCommand("achievements", "Elérések (mérföldkövek + jutalmak)", List.of("ach", "eleresek"), new AchievementsCommand(commandMenuContext, messageManager));
        plugin.registerCommand("leaderboard", "Ranglisták (szint, vagyon, raid-kill)", List.of("lb", "top", "rangsor"), new LeaderboardCommand(commandMenuContext, messageManager));
        plugin.registerCommand("profile", "Karakterlap — kaszt, spec, szakma, talent menük", List.of("karakter", "char", "status"), new ProfileCommand(characterMenuContext, messageManager));
        plugin.registerCommand("sinner", "Bűnös állapot kezelése (admin)", List.of(), new SinnerCommand(plugin, sinManager, messageManager));
        plugin.registerCommand("bounty", "Körözési lista (fejpénzek)", List.of("fejvadasz", "korozes"), new BountyCommand(sinManager, currencyManager, configManager, messageManager));
        plugin.registerCommand("relic", "Relikvia parancsok (admin)", List.of("relics", "relikvia"), new RelicCommand(relicManager, messageManager));
        plugin.registerCommand("parkour", "Parkour-pályák (futás, admin beállítás)", List.of("trial", "palya"), new ParkourCommand(parkourManager, messageManager));
        plugin.registerCommand("daily", "Napi küldetés", List.of("napi"), new DailyCommand(dailyQuestManager, messageManager));
        plugin.registerCommand("pet", "Társ (befogó item, idézés, név, szint)", List.of("tars", "companion"), new PetCommand(petManager, captureItemFactory, messageManager));
        plugin.registerCommand("profession", "Szakma (profession) parancsok", List.of("prof", "szakma"), new ProfessionCommand(plugin, professionManager, messageManager));
        plugin.registerCommand("spec", "Specializáció parancsok", List.of("specialization", "specializacio"), new SpecCommand(specializationManager, jobManager, professionManager, currencyManager, factionManager, talentManager, messageManager));
        plugin.registerCommand("talent", "Talent-fa parancsok", List.of("talents", "talentfa"), new TalentCommand(talentManager, messageManager));
        plugin.registerCommand("territory", "Frakció terület parancsok", List.of("terulet"), new TerritoryCommand(territoryManager, messageManager));
        plugin.registerCommand("quest", "Küldetés parancsok", List.of("quests", "kuldetes"), new QuestCommand(plugin, questManager, messageManager, questBuilderListener));
        plugin.registerCommand("market", "Piactér parancsok", List.of("piac", "ah"), new MarketCommand(marketManager, currencyManager, factionManager, messageManager));
        plugin.registerCommand("adomany", "Közösségi adomány-láda", List.of("donate", "adomanylada"), new DonationChestCommand(donationChestManager, messageManager));
        plugin.registerCommand("party", "Party (csapat) parancsok", List.of("p", "parti"), new hu.taliann.icesmp.commands.PartyCommand(partyManager, messageManager));
        plugin.registerCommand("claim", "Terület-claim parancsok", List.of("birtok"), new hu.taliann.icesmp.commands.ClaimCommand(claimManager, currencyManager, messageManager));
        plugin.registerCommand("events", "Világesemény parancsok", List.of("event", "esemeny"), new EventsCommand(seasonManager, bloodMoonManager, worldBossManager, invasionManager, caravanManager, ambientEventManager, gatheringBuffManager, treasureEventManager, wildHuntManager, abundanceManager, serverChallengeManager, escortManager, meteorEventManager, introManager, messageManager));
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
        pluginManager.registerEvents(new HudListener(hudManager), plugin);
        pluginManager.registerEvents(new JobGUIListener(jobManager, catalystItemFactory, specializationManager, spellRegistry, configManager, messageManager, characterMenuContext), plugin);
        pluginManager.registerEvents(new SkillTreeGUIListener(jobManager, catalystItemFactory, messageManager), plugin);
        pluginManager.registerEvents(new MarketGUIListener(plugin, marketManager, currencyManager, messageManager), plugin);
        pluginManager.registerEvents(new MarketDeliveryListener(marketManager, messageManager), plugin);
        pluginManager.registerEvents(new DonationChestListener(donationChestManager, messageManager), plugin);
        pluginManager.registerEvents(abilityCatalystListener, plugin);
        pluginManager.registerEvents(new SpellbookListener(abilityCatalystListener), plugin);
        pluginManager.registerEvents(new CatalystCraftSafetyListener(catalystItemFactory), plugin);
        pluginManager.registerEvents(new SpellProjectileListener(plugin), plugin);
        pluginManager.registerEvents(new SpellStateListener(plugin), plugin);
        pluginManager.registerEvents(playerSessionCleanupListener, plugin);
        pluginManager.registerEvents(new MobScalingListener(mobScalingManager), plugin);
        pluginManager.registerEvents(new JobCraftRestrictionListener(craftingRestrictionManager, messageManager), plugin);
        pluginManager.registerEvents(new ClassXpListener(plugin, jobManager, mobScalingManager, configManager, talentManager), plugin);
        pluginManager.registerEvents(new ProfessionXpListener(professionManager, configManager, talentManager), plugin);
        pluginManager.registerEvents(new ProfessionRecipeListener(professionRecipeManager, professionManager, messageManager), plugin);
        pluginManager.registerEvents(new FactionPassiveListener(factionManager, configManager), plugin);
        pluginManager.registerEvents(new TalentAttributeListener(plugin, talentManager), plugin);
        pluginManager.registerEvents(new TerritoryListener(territoryManager, factionManager, configManager, questManager, messageManager), plugin);
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
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.ChatFormatListener(configManager, hudManager), plugin);
        pluginManager.registerEvents(new MinionProtectionListener(minionManager), plugin);
        pluginManager.registerEvents(new PetCommandListener(minionManager, messageManager), plugin);
        pluginManager.registerEvents(new PetXpListener(plugin, petManager, configManager), plugin);
        pluginManager.registerEvents(new PetCaptureListener(petManager, captureItemFactory, messageManager), plugin);
        pluginManager.registerEvents(new PetCombatListener(petManager), plugin);
        pluginManager.registerEvents(new DailyQuestListener(plugin, dailyQuestManager), plugin);
        pluginManager.registerEvents(new ParkourListener(parkourManager), plugin);
        pluginManager.registerEvents(new SinListener(plugin, sinManager, raidManager, factionManager, statsManager, currencyManager, configManager, messageManager), plugin);
        pluginManager.registerEvents(new TheftListener(sinManager, territoryManager, factionManager, raidManager, configManager, messageManager), plugin);
        pluginManager.registerEvents(new SoulstoneListener(currencyManager, mobScalingManager, bloodMoonManager, configManager), plugin);
        pluginManager.registerEvents(new WorldBossListener(worldBossManager), plugin);
        pluginManager.registerEvents(new IntroListener(introManager), plugin);
        pluginManager.registerEvents(new SiegeWeaponListener(plugin, siegeWeaponFactory, raidManager, configManager, messageManager), plugin);
        pluginManager.registerEvents(new SoulShardListener(plugin, soulShardManager, specializationManager, configManager), plugin);
        pluginManager.registerEvents(new RitualListener(ritualManager), plugin);
        pluginManager.registerEvents(new hu.taliann.icesmp.listeners.WorldGameRuleListener(configManager), plugin);
        if (relicManager.isEnabled()) {
            pluginManager.registerEvents(new RelicCraftSafetyListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicInactivityListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicItemRefreshListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicTriggerListener(relicManager), plugin);
            pluginManager.registerEvents(new MetelytepoRelicListener(plugin, metelytepoManager, sinManager,
                    worldBossManager, invasionManager, messageManager), plugin);
            pluginManager.registerEvents(new ElytraRelicListener(relicManager, factionManager, messageManager), plugin);
            pluginManager.registerEvents(new RelicPvpTransferListener(plugin, relicManager, configManager, messageManager), plugin);
        }
    }
}
