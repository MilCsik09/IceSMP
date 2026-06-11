package hu.taliann.icesmp.core;

import hu.taliann.icesmp.commands.BankCommand;
import hu.taliann.icesmp.commands.CurrencyCommand;
import hu.taliann.icesmp.commands.FactionCommand;
import hu.taliann.icesmp.commands.IceSMPCommand;
import hu.taliann.icesmp.commands.JobCommand;
import hu.taliann.icesmp.commands.ProfessionCommand;
import hu.taliann.icesmp.commands.ProfileCommand;
import hu.taliann.icesmp.commands.RelicCommand;
import hu.taliann.icesmp.commands.SinnerCommand;
import hu.taliann.icesmp.commands.SpecCommand;
import hu.taliann.icesmp.commands.TalentCommand;
import hu.taliann.icesmp.commands.TerritoryCommand;
import hu.taliann.icesmp.gui.ProfileGUI;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.listeners.CatalystCraftSafetyListener;
import hu.taliann.icesmp.listeners.ClassXpListener;
import hu.taliann.icesmp.listeners.CurrencyCraftListener;
import hu.taliann.icesmp.listeners.CurrencyItemRefreshListener;
import hu.taliann.icesmp.listeners.FactionPassiveListener;
import hu.taliann.icesmp.listeners.JobCraftRestrictionListener;
import hu.taliann.icesmp.listeners.JobGUIListener;
import hu.taliann.icesmp.listeners.MetelytepoRelicListener;
import hu.taliann.icesmp.listeners.MobScalingListener;
import hu.taliann.icesmp.listeners.PlayerSessionCleanupListener;
import hu.taliann.icesmp.listeners.ProfessionXpListener;
import hu.taliann.icesmp.listeners.ProfileGUIListener;
import hu.taliann.icesmp.listeners.RelicCraftSafetyListener;
import hu.taliann.icesmp.listeners.RelicInactivityListener;
import hu.taliann.icesmp.listeners.RelicItemRefreshListener;
import hu.taliann.icesmp.listeners.RelicTriggerListener;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.listeners.SpellProjectileListener;
import hu.taliann.icesmp.listeners.SpellStateListener;
import hu.taliann.icesmp.listeners.TalentAttributeListener;
import hu.taliann.icesmp.listeners.TerritoryListener;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CraftingRestrictionManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.ExchangeRateService;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.managers.TalentManager;
import hu.taliann.icesmp.managers.TerritoryManager;
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
    private final PlayerSessionCleanupListener playerSessionCleanupListener;
    private final RelicManager relicManager;
    private final MetelytepoManager metelytepoManager;
    private final MobScalingManager mobScalingManager;
    private final ProfessionManager professionManager;
    private final CraftingRestrictionManager craftingRestrictionManager;
    private final ExchangeRateService exchangeRateService;
    private final SpecializationManager specializationManager;
    private final TalentManager talentManager;
    private final TerritoryManager territoryManager;

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
        this.abilityCatalystListener = new AbilityCatalystListener(plugin, jobManager, spellRegistry, catalystItemFactory, messageManager);
        this.relicManager = new RelicManager(plugin, configManager);
        this.metelytepoManager = new MetelytepoManager(plugin, messageManager);
        this.mobScalingManager = new MobScalingManager(plugin, configManager);
        this.professionManager = new ProfessionManager(plugin);
        this.craftingRestrictionManager = new CraftingRestrictionManager(plugin, configManager, jobManager, professionManager);
        this.exchangeRateService = new ExchangeRateService(configManager, currencyManager);
        this.specializationManager = new SpecializationManager(plugin, configManager, messageManager,
                jobManager, professionManager, factionManager, metelytepoManager);
        this.talentManager = new TalentManager(plugin, configManager, jobManager, professionManager);
        this.territoryManager = new TerritoryManager(plugin);
        jobManager.setXpChangeHook(specializationManager::applyClassSpecializationUnlocks);
        this.playerSessionCleanupListener = new PlayerSessionCleanupListener(
                abilityCatalystListener,
                jobManager,
                currencyManager,
                factionManager,
                metelytepoManager,
                relicManager,
                craftingRestrictionManager
        );

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
        registerListeners();
        registerCommands();

        plugin.getLogger().info("IceSMP core enabled.");
        plugin.getLogger().info("Available factions: " + factionManager.describeAvailableFactions());
    }

    /**
     * Disables the plugin core by saving all manager data.
     */
    public void disable() {
        for (final Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            playerSessionCleanupListener.cleanupPlayerState(onlinePlayer.getUniqueId());
        }

        ProfileGUI.closeAll();
        currencyManager.save();
        factionManager.save();
        relicManager.save();
        territoryManager.save();
        plugin.getLogger().info("IceSMP core disabled.");
    }


    /**
     * Registers all command handlers.
     */
    private void registerCommands() {
        plugin.registerCommand("icesmp", "IceSMP admin", List.of("ismp"), new IceSMPCommand(configManager, messageManager));
        plugin.registerCommand("currency", "Valuta parancsok", List.of("money", "eco"), new CurrencyCommand(currencyManager, configManager, exchangeRateService, messageManager));
        plugin.registerCommand("bank", "Bank parancsok", List.of("wallet", "vault"), new BankCommand(currencyManager, messageManager));
        plugin.registerCommand("faction", "Frakció parancsok", List.of("f"), new FactionCommand(factionManager, metelytepoManager, messageManager));
        plugin.registerCommand("job", "Kaszt admin parancsok", List.of("class"), new JobCommand(jobManager, spellRegistry, catalystItemFactory, abilityCatalystListener, messageManager));
        plugin.registerCommand("profile", "Játékos profil", List.of("status", "info"), new ProfileCommand(messageManager, metelytepoManager));
        plugin.registerCommand("sinner", "Bűnös állapot kezelése", List.of(), new SinnerCommand(metelytepoManager, messageManager));
        plugin.registerCommand("relic", "Relikvia framework parancsok", List.of("relics"), new RelicCommand(relicManager, messageManager));
        plugin.registerCommand("profession", "Szakma parancsok", List.of("prof", "szakma"), new ProfessionCommand(professionManager, messageManager));
        plugin.registerCommand("spec", "Specializáció parancsok", List.of("specialization"), new SpecCommand(specializationManager, jobManager, professionManager, messageManager));
        plugin.registerCommand("talent", "Talent parancsok", List.of("talents"), new TalentCommand(talentManager, messageManager));
        plugin.registerCommand("territory", "Frakció terület parancsok", List.of("terulet"), new TerritoryCommand(territoryManager, messageManager));
    }

    /**
     * Registers all event listeners.
     */
    private void registerListeners() {
        final PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new CurrencyCraftListener(currencyManager), plugin);
        pluginManager.registerEvents(new CurrencyItemRefreshListener(plugin, currencyManager), plugin);
        pluginManager.registerEvents(new ProfileGUIListener(jobManager, catalystItemFactory, messageManager), plugin);
        pluginManager.registerEvents(new JobGUIListener(jobManager, metelytepoManager, catalystItemFactory, messageManager), plugin);
        pluginManager.registerEvents(abilityCatalystListener, plugin);
        pluginManager.registerEvents(new CatalystCraftSafetyListener(catalystItemFactory), plugin);
        pluginManager.registerEvents(new SpellProjectileListener(plugin), plugin);
        pluginManager.registerEvents(new SpellStateListener(plugin), plugin);
        pluginManager.registerEvents(playerSessionCleanupListener, plugin);
        pluginManager.registerEvents(new MobScalingListener(mobScalingManager), plugin);
        pluginManager.registerEvents(new JobCraftRestrictionListener(craftingRestrictionManager, messageManager), plugin);
        pluginManager.registerEvents(new ClassXpListener(jobManager, mobScalingManager, configManager, talentManager), plugin);
        pluginManager.registerEvents(new ProfessionXpListener(professionManager, configManager, talentManager), plugin);
        pluginManager.registerEvents(new FactionPassiveListener(factionManager, configManager), plugin);
        pluginManager.registerEvents(new TalentAttributeListener(talentManager), plugin);
        pluginManager.registerEvents(new TerritoryListener(territoryManager, factionManager, configManager, messageManager), plugin);
        if (relicManager.isEnabled()) {
            pluginManager.registerEvents(new RelicCraftSafetyListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicInactivityListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicItemRefreshListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicTriggerListener(relicManager), plugin);
            pluginManager.registerEvents(new MetelytepoRelicListener(metelytepoManager, messageManager), plugin);
        }
    }
}
