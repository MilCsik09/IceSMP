package hu.taliann.icesmp.core;

import hu.taliann.icesmp.commands.BankCommand;
import hu.taliann.icesmp.commands.CurrencyCommand;
import hu.taliann.icesmp.commands.FactionCommand;
import hu.taliann.icesmp.commands.IceSMPCommand;
import hu.taliann.icesmp.commands.JobCommand;
import hu.taliann.icesmp.commands.ProfileCommand;
import hu.taliann.icesmp.commands.RelicCommand;
import hu.taliann.icesmp.commands.SinnerCommand;
import hu.taliann.icesmp.gui.ProfileGUI;
import hu.taliann.icesmp.items.SpellbookItemFactory;
import hu.taliann.icesmp.listeners.CurrencyCraftListener;
import hu.taliann.icesmp.listeners.CurrencyItemRefreshListener;
import hu.taliann.icesmp.listeners.JobGUIListener;
import hu.taliann.icesmp.listeners.MetelytepoRelicListener;
import hu.taliann.icesmp.listeners.PlayerSessionCleanupListener;
import hu.taliann.icesmp.listeners.ProfileGUIListener;
import hu.taliann.icesmp.listeners.RelicCraftSafetyListener;
import hu.taliann.icesmp.listeners.RelicItemRefreshListener;
import hu.taliann.icesmp.listeners.RelicTriggerListener;
import hu.taliann.icesmp.listeners.SpellbookListener;
import hu.taliann.icesmp.listeners.SpellProjectileListener;
import hu.taliann.icesmp.listeners.SpellStateListener;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.AngryChickenSpell;
import hu.taliann.icesmp.spells.ArmamentSpell;
import hu.taliann.icesmp.spells.ConfusionSpell;
import hu.taliann.icesmp.spells.DoubleJumpSpell;
import hu.taliann.icesmp.spells.FeastSpell;
import hu.taliann.icesmp.spells.FeatherfootSpell;
import hu.taliann.icesmp.spells.FriendshipSpell;
import hu.taliann.icesmp.spells.GustSpell;
import hu.taliann.icesmp.spells.HideSpell;
import hu.taliann.icesmp.spells.InnerFocusSpell;
import hu.taliann.icesmp.spells.LuckyStarSpell;
import hu.taliann.icesmp.spells.RainDanceSpell;
import hu.taliann.icesmp.spells.RootSpell;
import hu.taliann.icesmp.spells.SunDanceSpell;
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
    private final SpellbookItemFactory spellbookItemFactory;
    private final SpellbookListener spellbookListener;
    private final PlayerSessionCleanupListener playerSessionCleanupListener;
    private final RelicManager relicManager;
    private final MetelytepoManager metelytepoManager;

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
        this.jobManager = new JobManager(plugin);
        this.spellRegistry = new SpellRegistry();
        this.spellbookItemFactory = new SpellbookItemFactory(plugin);
        this.spellbookListener = new SpellbookListener(plugin, jobManager, spellRegistry, spellbookItemFactory, messageManager);
        this.relicManager = new RelicManager(plugin, configManager);
        this.metelytepoManager = new MetelytepoManager(plugin, messageManager);
        this.playerSessionCleanupListener = new PlayerSessionCleanupListener(
                spellbookListener,
                jobManager,
                currencyManager,
                factionManager,
                metelytepoManager,
                relicManager
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
        plugin.getLogger().info("IceSMP core disabled.");
    }


    /**
     * Registers all command handlers.
     */
    private void registerCommands() {
        plugin.registerCommand("icesmp", "IceSMP admin", List.of("ismp"), new IceSMPCommand(configManager, messageManager));
        plugin.registerCommand("currency", "Valuta parancsok", List.of("money", "eco"), new CurrencyCommand(currencyManager, configManager, messageManager));
        plugin.registerCommand("bank", "Bank parancsok", List.of("wallet", "vault"), new BankCommand(currencyManager, messageManager));
        plugin.registerCommand("faction", "Frakció parancsok", List.of("f"), new FactionCommand(factionManager, messageManager));
        plugin.registerCommand("job", "Kaszt admin parancsok", List.of("class"), new JobCommand(jobManager, spellRegistry, spellbookItemFactory, spellbookListener, messageManager));
        plugin.registerCommand("profile", "Játékos profil", List.of("status", "info"), new ProfileCommand(messageManager, metelytepoManager));
        plugin.registerCommand("sinner", "Bűnös állapot kezelése", List.of(), new SinnerCommand(metelytepoManager, messageManager));
        plugin.registerCommand("relic", "Relikvia framework parancsok", List.of("relics"), new RelicCommand(relicManager, messageManager));
    }

    /**
     * Registers all event listeners.
     */
    private void registerListeners() {
        final PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new CurrencyCraftListener(currencyManager), plugin);
        pluginManager.registerEvents(new CurrencyItemRefreshListener(plugin, currencyManager), plugin);
        pluginManager.registerEvents(new ProfileGUIListener(jobManager, messageManager), plugin);
        pluginManager.registerEvents(new JobGUIListener(jobManager, metelytepoManager, messageManager), plugin);
        pluginManager.registerEvents(spellbookListener, plugin);
        pluginManager.registerEvents(new SpellProjectileListener(plugin), plugin);
        pluginManager.registerEvents(new SpellStateListener(plugin), plugin);
        pluginManager.registerEvents(playerSessionCleanupListener, plugin);
        if (relicManager.isEnabled()) {
            pluginManager.registerEvents(new RelicCraftSafetyListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicItemRefreshListener(relicManager), plugin);
            pluginManager.registerEvents(new RelicTriggerListener(relicManager), plugin);
            pluginManager.registerEvents(new MetelytepoRelicListener(metelytepoManager, messageManager), plugin);
        }
    }
}
