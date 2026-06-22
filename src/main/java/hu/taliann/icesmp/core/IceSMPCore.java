package hu.taliann.icesmp.core;

import hu.taliann.icesmp.commands.BankCommand;
import hu.taliann.icesmp.commands.CurrencyCommand;
import hu.taliann.icesmp.commands.DailyCommand;
import hu.taliann.icesmp.commands.FactionCommand;
import hu.taliann.icesmp.commands.IceSMPCommand;
import hu.taliann.icesmp.commands.JobCommand;
import hu.taliann.icesmp.commands.LeaderboardCommand;
import hu.taliann.icesmp.commands.AchievementsCommand;
import hu.taliann.icesmp.commands.EventsCommand;
import hu.taliann.icesmp.commands.ExchangeBoardCommand;
import hu.taliann.icesmp.commands.MarketCommand;
import hu.taliann.icesmp.commands.MenuCommand;
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
import hu.taliann.icesmp.listeners.ElytraRelicListener;
import hu.taliann.icesmp.listeners.FactionPassiveListener;
import hu.taliann.icesmp.listeners.IntroListener;
import hu.taliann.icesmp.listeners.JobCraftRestrictionListener;
import hu.taliann.icesmp.listeners.JobGUIListener;
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
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CraftingRestrictionManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.DailyQuestManager;
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
import hu.taliann.icesmp.managers.MinionManager;
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
    private final AbilityCatalystListener abilityCatalystListener;
    private final SpellMasteryManager spellMasteryManager;
    private final PlayerSessionCleanupListener playerSessionCleanupListener;
    private final RelicManager relicManager;
    private final MetelytepoManager metelytepoManager;
    private final MinionManager minionManager;
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
    private final QuestManager questManager;
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
    private final StatsManager statsManager;
    private final AchievementManager achievementManager;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask taxTask;
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
        this.factionManager = new FactionManager(plugin);
        this.jobManager = new JobManager(plugin, configManager, messageManager, factionManager);
        this.spellRegistry = new SpellRegistry();
        this.catalystItemFactory = new CatalystItemFactory(plugin);
        this.captureItemFactory = new CaptureItemFactory(plugin);
        this.spellMasteryManager = new SpellMasteryManager(plugin, configManager, currencyManager, factionManager);
        this.relicManager = new RelicManager(plugin, configManager);
        this.metelytepoManager = new MetelytepoManager(plugin, configManager, messageManager, factionManager);
        this.minionManager = new MinionManager(plugin);
        this.factionTreasuryManager = new FactionTreasuryManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.kingManager = new KingManager(plugin, configManager, factionManager, messageManager);
        this.bloodMoonManager = new BloodMoonManager(plugin, configManager, messageManager);
        this.seasonManager = new SeasonManager(plugin, configManager, messageManager, factionTreasuryManager);
        this.raidManager = new RaidManager(plugin, configManager, factionManager, factionTreasuryManager, seasonManager, messageManager);
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
        this.marketManager = new MarketManager(plugin, configManager, currencyManager, factionManager, factionRelationManager);
        this.questManager = new QuestManager(plugin, configManager, messageManager, jobManager,
                currencyManager, factionManager, metelytepoManager);
        this.specializationManager = new SpecializationManager(plugin, configManager, messageManager,
                jobManager, professionManager, factionManager, metelytepoManager, questManager);
        this.abilityCatalystListener = new AbilityCatalystListener(plugin, jobManager, spellRegistry,
                catalystItemFactory, configManager, spellMasteryManager, specializationManager, messageManager);
        this.talentManager = new TalentManager(plugin, configManager, jobManager, professionManager, specializationManager);
        this.petManager = new PetManager(plugin, configManager, minionManager, specializationManager, messageManager);
        this.dailyQuestManager = new DailyQuestManager(plugin, configManager, currencyManager, factionManager, messageManager);
        this.parkourManager = new ParkourManager(plugin, currencyManager, factionManager, messageManager);
        this.territoryManager = new TerritoryManager(plugin);
        this.siegeWeaponFactory = new SiegeWeaponFactory(plugin);
        this.soulShardManager = new SoulShardManager(plugin, configManager, minionManager, messageManager);
        this.ritualManager = new RitualManager(configManager, relicManager, messageManager);
        this.exchangeBoardManager = new ExchangeBoardManager(plugin, configManager, exchangeRateService);
        this.characterMenuContext = new CharacterMenuContext(messageManager, jobManager, specializationManager,
                professionManager, talentManager, factionManager, currencyManager, metelytepoManager,
                catalystItemFactory, spellRegistry, configManager);
        this.statsManager = new StatsManager(plugin, jobManager, currencyManager);
        this.achievementManager = new AchievementManager(plugin, configManager, jobManager, currencyManager,
                professionManager, factionManager, statsManager, messageManager);
        this.commandMenuContext = new CommandMenuContext(messageManager, factionManager, currencyManager,
                exchangeRateService, factionTreasuryManager, kingManager, raidManager, questManager,
                seasonManager, bloodMoonManager, soulShardManager, specializationManager, relicManager,
                statsManager, achievementManager, configManager);
        this.hudManager = new HudManager(plugin, configManager, factionManager, currencyManager, jobManager,
                raidManager, bloodMoonManager, worldBossManager);
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
                craftingRestrictionManager
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
    }

    /**
     * Enables the plugin core by loading all managers and registering systems.
     */
    public void enable() {
        configManager.load();
        messageManager.reload();
        currencyManager.load();
        factionManager.load();
        relicManager.load();
        mobScalingManager.load();
        craftingRestrictionManager.load();
        territoryManager.load();
        factionTreasuryManager.load();
        kingManager.load();
        economyEventManager.load();
        marketManager.load();
        seasonManager.load();
        exchangeBoardManager.load();
        statsManager.load();
        parkourManager.load();
        siegeWeaponFactory.registerRecipe();
        professionRecipeManager.registerRecipes();
        registerListeners();
        registerCommands();
        scheduleTaxCollection();
        scheduleEconomyEvents();
        scheduleWorldEvents();
        scheduleHud();
        schedulePetCombat();

        plugin.getLogger().info("IceSMP core enabled.");
        plugin.getLogger().info("Available factions: " + factionManager.describeAvailableFactions());
    }

    /**
     * Disables the plugin core by saving all manager data.
     */
    public void disable() {
        if (taxTask != null) {
            taxTask.cancel();
            taxTask = null;
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

        // Save ALL persistent state FIRST, before any cleanup that could mutate in-memory state.
        // (mobScalingManager / craftingRestrictionManager are config-derived read-only — no save.)
        ProfileGUI.closeAll();
        currencyManager.save();
        factionManager.save();
        relicManager.save();
        territoryManager.save();
        factionTreasuryManager.save();
        kingManager.save();
        economyEventManager.save();
        marketManager.save();
        seasonManager.save();
        exchangeBoardManager.save();
        statsManager.save();
        parkourManager.save();

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
     * which runs on both Paper and Folia (the tax touches no entities, only
     * in-memory balances).
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
        plugin.registerCommand("currency", "Valuta parancsok", List.of("money", "eco"), new CurrencyCommand(currencyManager, configManager, exchangeRateService, messageManager));
        plugin.registerCommand("bank", "Bank parancsok", List.of("wallet", "vault"), new BankCommand(currencyManager, messageManager));
        plugin.registerCommand("faction", "Frakció parancsok", List.of("f"), new FactionCommand(factionManager, metelytepoManager, factionTreasuryManager, currencyManager, kingManager, raidManager, messageManager));
        plugin.registerCommand("class", "Kaszt (class): szint, katalizátor, admin", List.of("kaszt", "job"), new JobCommand(plugin, jobManager, spellRegistry, catalystItemFactory, abilityCatalystListener, messageManager));
        plugin.registerCommand("menu", "Központi menü — minden parancs egy helyen", List.of("hub", "m"), new MenuCommand(commandMenuContext, messageManager));
        plugin.registerCommand("achievements", "Elérések (mérföldkövek + jutalmak)", List.of("ach", "eleresek"), new AchievementsCommand(commandMenuContext, messageManager));
        plugin.registerCommand("leaderboard", "Ranglisták (szint, vagyon, raid-kill)", List.of("lb", "top", "rangsor"), new LeaderboardCommand(commandMenuContext, messageManager));
        plugin.registerCommand("profile", "Karakterlap — kaszt, spec, szakma, talent menük", List.of("karakter", "char", "status"), new ProfileCommand(characterMenuContext, messageManager));
        plugin.registerCommand("sinner", "Bűnös állapot kezelése (admin)", List.of(), new SinnerCommand(metelytepoManager, messageManager));
        plugin.registerCommand("relic", "Relikvia parancsok (admin)", List.of("relics", "relikvia"), new RelicCommand(relicManager, messageManager));
        plugin.registerCommand("parkour", "Parkour-pályák (futás, admin beállítás)", List.of("trial", "palya"), new ParkourCommand(parkourManager, messageManager));
        plugin.registerCommand("daily", "Napi küldetés", List.of("napi"), new DailyCommand(dailyQuestManager, messageManager));
        plugin.registerCommand("pet", "Társ (befogó item, idézés, név, szint)", List.of("tars", "companion"), new PetCommand(petManager, captureItemFactory, messageManager));
        plugin.registerCommand("profession", "Szakma (profession) parancsok", List.of("prof", "szakma"), new ProfessionCommand(professionManager, messageManager));
        plugin.registerCommand("spec", "Specializáció parancsok", List.of("specialization", "specializacio"), new SpecCommand(specializationManager, jobManager, professionManager, currencyManager, factionManager, talentManager, messageManager));
        plugin.registerCommand("talent", "Talent-fa parancsok", List.of("talents", "talentfa"), new TalentCommand(talentManager, messageManager));
        plugin.registerCommand("territory", "Frakció terület parancsok", List.of("terulet"), new TerritoryCommand(territoryManager, messageManager));
        plugin.registerCommand("quest", "Küldetés parancsok", List.of("quests", "kuldetes"), new QuestCommand(questManager, messageManager));
        plugin.registerCommand("market", "Piactér parancsok", List.of("piac", "ah"), new MarketCommand(marketManager, currencyManager, factionManager, messageManager));
        plugin.registerCommand("events", "Világesemény parancsok", List.of("event", "esemeny"), new EventsCommand(seasonManager, bloodMoonManager, worldBossManager, invasionManager, introManager, messageManager));
        plugin.registerCommand("souls", "Lélekszilánk parancsok", List.of("soul", "lelek"), new SoulCommand(soulShardManager, messageManager));
        plugin.registerCommand("spell", "Spell-mesterség (cooldown-csökkentés valutáért)", List.of("spells", "mastery", "mesterseg"), new SpellCommand(jobManager, spellRegistry, spellMasteryManager, messageManager));
        plugin.registerCommand("spellbook", "Varázskönyv: spellek böngészése és kiválasztása", List.of("varazskonyv", "konyv", "sb"), new SpellbookCommand(abilityCatalystListener, messageManager));
        plugin.registerCommand("exchangeboard", "Árfolyamtábla admin", List.of("ratesboard", "arfolyamtabla"), new ExchangeBoardCommand(exchangeBoardManager, messageManager));
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
        pluginManager.registerEvents(new MarketGUIListener(marketManager, currencyManager, messageManager), plugin);
        pluginManager.registerEvents(abilityCatalystListener, plugin);
        pluginManager.registerEvents(new SpellbookListener(abilityCatalystListener), plugin);
        pluginManager.registerEvents(new CatalystCraftSafetyListener(catalystItemFactory), plugin);
        pluginManager.registerEvents(new SpellProjectileListener(plugin), plugin);
        pluginManager.registerEvents(new SpellStateListener(plugin), plugin);
        pluginManager.registerEvents(playerSessionCleanupListener, plugin);
        pluginManager.registerEvents(new MobScalingListener(mobScalingManager), plugin);
        pluginManager.registerEvents(new JobCraftRestrictionListener(craftingRestrictionManager, messageManager), plugin);
        pluginManager.registerEvents(new ClassXpListener(jobManager, mobScalingManager, configManager, talentManager), plugin);
        pluginManager.registerEvents(new ProfessionXpListener(professionManager, configManager, talentManager), plugin);
        pluginManager.registerEvents(new ProfessionRecipeListener(professionRecipeManager, professionManager, messageManager), plugin);
        pluginManager.registerEvents(new FactionPassiveListener(factionManager, configManager), plugin);
        pluginManager.registerEvents(new TalentAttributeListener(plugin, talentManager), plugin);
        pluginManager.registerEvents(new TerritoryListener(territoryManager, factionManager, configManager, questManager, messageManager), plugin);
        pluginManager.registerEvents(new QuestProgressListener(questManager, mobScalingManager), plugin);
        pluginManager.registerEvents(new MinionProtectionListener(minionManager), plugin);
        pluginManager.registerEvents(new PetCommandListener(minionManager, messageManager), plugin);
        pluginManager.registerEvents(new PetXpListener(petManager, configManager), plugin);
        pluginManager.registerEvents(new PetCaptureListener(petManager, captureItemFactory, messageManager), plugin);
        pluginManager.registerEvents(new PetCombatListener(petManager), plugin);
        pluginManager.registerEvents(new DailyQuestListener(dailyQuestManager), plugin);
        pluginManager.registerEvents(new ParkourListener(parkourManager), plugin);
        pluginManager.registerEvents(new SinListener(plugin, metelytepoManager, raidManager, factionManager, statsManager, configManager, messageManager), plugin);
        pluginManager.registerEvents(new SoulstoneListener(currencyManager, mobScalingManager, bloodMoonManager, configManager), plugin);
        pluginManager.registerEvents(new WorldBossListener(worldBossManager), plugin);
        pluginManager.registerEvents(new IntroListener(introManager), plugin);
        pluginManager.registerEvents(new SiegeWeaponListener(siegeWeaponFactory, raidManager, configManager, messageManager), plugin);
        pluginManager.registerEvents(new SoulShardListener(soulShardManager, specializationManager, configManager), plugin);
        pluginManager.registerEvents(new RitualListener(ritualManager), plugin);
        if (relicManager.isEnabled()) {
            pluginManager.registerEvents(new RelicCraftSafetyListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicInactivityListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicItemRefreshListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicTriggerListener(relicManager), plugin);
            pluginManager.registerEvents(new MetelytepoRelicListener(metelytepoManager, messageManager), plugin);
            pluginManager.registerEvents(new ElytraRelicListener(relicManager, factionManager, messageManager), plugin);
            pluginManager.registerEvents(new RelicPvpTransferListener(relicManager, configManager, messageManager), plugin);
        }
    }
}
