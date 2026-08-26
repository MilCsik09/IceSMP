package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ConfigValidator;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import hu.taliann.icesmp.trash.TrashDevCommand;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Permission-filtered admin entry point for bounded reload, operator configuration, inspection
 * and client diagnostics. Authored gameplay content is visible to inspection but cannot be
 * mutated through this command.
 */
public final class IceSMPCommand implements BasicCommand {

    private static final String PERMISSION = Permissions.RELOAD;
    private static final String CONFIG_PERMISSION = Permissions.CONFIG;
    private static final String INSPECT_PERMISSION = Permissions.INSPECT;
    private static final String CLIENT_PERMISSION = Permissions.CLIENT;
    private static final int MAX_SUGGESTED_KEYS = 40;
    private static final int MAX_LISTED_KEYS = 30;
    private static final int MAX_INSPECT_QUESTS = 5;
    private static final int MAX_INSPECT_COOLDOWNS = 5;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final JobManager jobManager;
    private final SpecializationManager specializationManager;
    private final ResourceManager resourceManager;
    private final FactionManager factionManager;
    private final CurrencyManager currencyManager;
    private final StatsManager statsManager;
    private final ClaimManager claimManager;
    private final QuestManager questManager;
    private final AbilityCatalystListener abilityCatalystListener;
    private final SinManager sinManager;
    private final TrashDevCommand trashDevCommand;

    private volatile Runnable reloadHook;
    private volatile java.util.function.Consumer<String> configChangeHook;
    private volatile hu.taliann.icesmp.client.IceSmpClientBridge clientBridge;

    public void setClientBridge(final hu.taliann.icesmp.client.IceSmpClientBridge clientBridge) {
        this.clientBridge = clientBridge;
    }

    public void setReloadHook(final Runnable reloadHook) {
        this.reloadHook = reloadHook;
    }

    public void setConfigChangeHook(final java.util.function.Consumer<String> configChangeHook) {
        this.configChangeHook = configChangeHook;
    }

    public IceSMPCommand(final JavaPlugin plugin, final ConfigManager configManager,
                         final MessageManager messageManager, final JobManager jobManager,
                         final SpecializationManager specializationManager, final ResourceManager resourceManager,
                         final FactionManager factionManager, final CurrencyManager currencyManager,
                         final StatsManager statsManager, final ClaimManager claimManager,
                         final QuestManager questManager, final AbilityCatalystListener abilityCatalystListener,
                         final SinManager sinManager, final TrashDevCommand trashDevCommand) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.jobManager = jobManager;
        this.specializationManager = specializationManager;
        this.resourceManager = resourceManager;
        this.factionManager = factionManager;
        this.currencyManager = currencyManager;
        this.statsManager = statsManager;
        this.claimManager = claimManager;
        this.questManager = questManager;
        this.abilityCatalystListener = abilityCatalystListener;
        this.sinManager = sinManager;
        this.trashDevCommand = trashDevCommand;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        if ("dev".equalsIgnoreCase(args[0]) && HiddenDevAuthority.mayUseHiddenContent(sender)) {
            trashDevCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            return;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!require(sender, PERMISSION)) return;
            handleReload(sender, args);
            return;
        }

        if ("config".equalsIgnoreCase(args[0])) {
            if (!require(sender, CONFIG_PERMISSION)) return;
            handleConfig(sender, args);
            return;
        }

        if ("inspect".equalsIgnoreCase(args[0])) {
            if (!require(sender, INSPECT_PERMISSION)) return;
            handleInspect(sender, args);
            return;
        }

        if ("client".equalsIgnoreCase(args[0])) {
            if (!require(sender, CLIENT_PERMISSION)) return;
            handleClient(sender, args);
            return;
        }

        sendHelp(sender);
    }

    private boolean require(final CommandSender sender, final String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(messageManager.get("messages.permission-denied",
                "&cNincs jogosultságod erre a parancsra."));
        return false;
    }

    private void handleReload(final CommandSender sender, final String[] args) {
        final String domain = args.length < 2 ? "operator" : args[1].toLowerCase(Locale.ROOT);
        if ("status".equals(domain)) {
            sender.sendMessage(messageManager.get("admin.icesmp.reload.status",
                    "&6Reload policy: &foperator/config = path-specific LIVE/SAFE/RESTART, messages = LIVE_RELOADABLE, authored content = RESTART_REQUIRED."));
            return;
        }
        if (List.of("content", "classes", "equipment", "pve", "professions", "events").contains(domain)) {
            sender.sendMessage(messageManager.get("admin.icesmp.reload.restart-required",
                    "&eA(z) &f%s &edomain canonical contentje csak újraindítással tölthető újra; az aktív snapshot változatlan maradt.", domain));
            return;
        }
        if ("messages".equals(domain)) {
            messageManager.reload();
            sender.sendMessage(messageManager.get("admin.icesmp.reload.messages-success",
                    "&aAz üzenetek újratöltve."));
            return;
        }
        if (!"operator".equals(domain) && !"safe".equals(domain)) {
            sender.sendMessage(messageManager.get("admin.icesmp.reload.usage",
                    "&cHasználat: /icesmp reload [operator|messages|status|content-domain]"));
            return;
        }

        final ConfigManager.ConfigSnapshot previous = configManager.snapshot();
        try {
            configManager.reload();
            messageManager.reload();
            ConfigValidator.validate(configManager, plugin.getLogger());
            final Runnable hook = this.reloadHook;
            if (hook != null) hook.run();
            final java.util.List<String> questErrors = questManager.reloadDefinitions();
            if (!questErrors.isEmpty()) {
                throw new IllegalStateException("quest registry rejected " + questErrors.size() + " definition(s)");
            }
            sender.sendMessage(messageManager.get("admin.icesmp.reload.success",
                    "<green>Az operator konfiguráció biztonságosan újratöltve.</green>"));
        } catch (final RuntimeException failure) {
            configManager.restoreSnapshot(previous);
            try {
                final Runnable hook = this.reloadHook;
                if (hook != null) hook.run();
            } catch (final RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            plugin.getLogger().severe("Config reload rejected; previous snapshot restored: " + failure);
            sender.sendMessage(messageManager.get("admin.icesmp.reload.rollback",
                    "&cAz újratöltés hibás adatot talált; a korábbi aktív snapshot maradt érvényben. Részletek a szervernaplóban."));
        }
    }

    /**
     * {@code /icesmp inspect <név>}: egy játékos-jelentés (kaszt, spec, erőforrás,
     * egyenlegek, statok, bűnpontok, claimek, aktív questek, aktív cooldownok). Online célpontnál
     * a jelentés összeállítása a CÉLPONT saját régió-szálán történik (a Player-t kérő API-k —
     * JobManager, ResourceManager stb. — miatt), majd a kész, több soros szöveg a KÉRDEZŐ saját
     * szálán kerül visszaküldésre (Folia: a sender is entitás lehet, ha player). Offline
     * célpontnál csak a UUID-alapú (perzisztens) adatforrások olvashatók.
     */
    private void handleInspect(final CommandSender sender, final String[] args) {
        if (args.length >= 2 && "config".equalsIgnoreCase(args[1])) {
            inspectConfigAuthority(sender, args);
            return;
        }
        if (args.length < 2 || args[1].isBlank()) {
            sender.sendMessage(messageManager.get("admin.icesmp.inspect.usage",
                    "&cHasználat: /icesmp inspect <név> | /icesmp inspect config <kulcs>"));
            return;
        }

        final String name = args[1];
        final Player target = Bukkit.getPlayerExact(name);
        if (target != null) {
            inspectOnline(sender, target);
            return;
        }

        final OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline == null) {
            sender.sendMessage(messageManager.get("admin.icesmp.inspect.unknown", "&cIsmeretlen játékos: &f%s", name));
            return;
        }
        inspectOffline(sender, offline);
    }

    private void inspectConfigAuthority(final CommandSender sender, final String[] args) {
        if (args.length < 3 || args[2].isBlank()) {
            sender.sendMessage(messageManager.get("admin.icesmp.inspect.config-usage",
                    "&cHasználat: /icesmp inspect config <kulcs>"));
            return;
        }
        final String key = args[2];
        final Object value = configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().get(key);
        if (value == null) {
            sender.sendMessage(messageManager.get("admin.icesmp.inspect.config-missing",
                    "&eIsmeretlen config-kulcs: &f%s", key));
            return;
        }
        sender.sendMessage(messageManager.get("admin.icesmp.inspect.config-authority",
                "&6%s &7= &f%s &7| authority: &f%s &7| reload: &f%s",
                key, String.valueOf(value), configManager.authorityOf(key), configManager.reloadPolicyOf(key)));
    }

    private void inspectOnline(final CommandSender sender, final Player target) {
        target.getScheduler().run(plugin, task -> {
            final List<String> report = buildOnlineReport(target);
            deliverReport(sender, report);
        }, null);
    }

    private void inspectOffline(final CommandSender sender, final OfflinePlayer offline) {
        final List<String> report = buildOfflineReport(offline);
        deliverReport(sender, report);
    }

    /** Sends the finished, already-rendered report lines back on the SENDER'S own thread (Folia). */
    private void deliverReport(final CommandSender sender, final List<String> report) {
        if (sender instanceof Player senderPlayer) {
            senderPlayer.getScheduler().run(plugin, task -> report.forEach(senderPlayer::sendMessage), null);
        } else {
            report.forEach(sender::sendMessage);
        }
    }

    /** Full report for an online target — runs entirely on the target's own region thread. */
    private List<String> buildOnlineReport(final Player target) {
        final List<String> lines = new ArrayList<>();
        final UUID id = target.getUniqueId();

        lines.add(messageManager.get("admin.icesmp.inspect.header", "&6=== Inspektor: &f%s &6===", target.getName()));

        final FactionType faction = factionManager.getChosenFaction(id).orElse(null);
        lines.add(messageManager.get("admin.icesmp.inspect.faction", "&7Frakció: &f%s",
                faction == null ? "Menedék vendége (nincs választott frakció)" : faction.getDisplayName()));

        final JobType job = jobManager.getPrimaryJob(target);
        lines.add(messageManager.get("admin.icesmp.inspect.job", "&7Kaszt: &f%s &7(Lv. &f%s&7, XP: &f%s&7)",
                job == null ? "nincs" : job.getId(), jobManager.getPrimaryLevel(target), jobManager.getXp(target)));

        final SpecializationType spec = specializationManager.getClassSpecialization(target);
        lines.add(messageManager.get("admin.icesmp.inspect.spec", "&7Specializáció: &f%s",
                spec == null ? "nincs" : spec.getId()));

        lines.add(messageManager.get("admin.icesmp.inspect.resource", "&7Erőforrás: &f%s &7- &f%s&7/&f%s",
                resourceManager.resourceName(target), resourceManager.resourceValue(target), resourceManager.resourceMax(target)));

        lines.add(messageManager.get("admin.icesmp.inspect.balances", "&7Egyenlegek: &f%s", currencyManager.describeBalances(target)));

        lines.add(messageManager.get("admin.icesmp.inspect.stats", "&7Statok: &fÖlés &7%s &f| Halál &7%s &f| K/D &7%s "
                        + "&f| Mob-ölés &7%s &f| Castok &7%s &f| Teljesített questek &7%s",
                statsManager.getKills(id), statsManager.getDeaths(id), formatKd(statsManager.getKills(id), statsManager.getDeaths(id)),
                statsManager.getMobKills(id), statsManager.getSpellCasts(id), statsManager.getQuestsCompleted(id)));

        lines.add(messageManager.get("admin.icesmp.inspect.sins", "&7Bűnpontok: &f%s &7(%s)",
                sinManager.getSinCount(target), sinManager.isSinner(target) ? "bűnös" : "tiszta"));

        lines.add(messageManager.get("admin.icesmp.inspect.claims", "&7Claimek: &f%s db &7/ &f%s oszlop",
                claimManager.countClaims(id), claimManager.countColumns(id)));

        final List<String> activeQuests = questManager.getActiveQuests(target);
        lines.add(messageManager.get("admin.icesmp.inspect.quests-header", "&7Aktív questek (&f%s&7):", activeQuests.size()));
        activeQuests.stream().limit(MAX_INSPECT_QUESTS).forEach(questId -> lines.add(messageManager.get(
                "admin.icesmp.inspect.quests-line", "&8- &e%s &7- %s",
                questManager.getDisplayName(questId), questManager.describeProgress(target, questId))));

        final Map<String, Long> cooldowns = abilityCatalystListener.activeCooldowns(id);
        lines.add(messageManager.get("admin.icesmp.inspect.cooldowns-header", "&7Aktív cooldownok (&f%s&7):", cooldowns.size()));
        cooldowns.entrySet().stream().limit(MAX_INSPECT_COOLDOWNS).forEach(entry -> lines.add(messageManager.get(
                "admin.icesmp.inspect.cooldowns-line", "&8- &e%s &7- &f%s mp",
                entry.getKey(), Math.max(1L, Math.round(entry.getValue() / 1000.0D)))));

        return lines;
    }

    /** Limited report for an offline target — only UUID-keyed (persisted) data sources. */
    private List<String> buildOfflineReport(final OfflinePlayer offline) {
        final List<String> lines = new ArrayList<>();
        final UUID id = offline.getUniqueId();
        final String name = offline.getName() == null ? statsManager.getStoredName(id, "?") : offline.getName();

        lines.add(messageManager.get("admin.icesmp.inspect.header", "&6=== Inspektor: &f%s &6===", name));
        lines.add(messageManager.get("admin.icesmp.inspect.offline-notice",
                "&eOffline — korlátozott adatok (csak a mentett/UUID-alapú értékek)."));

        final FactionType faction = factionManager.getChosenFaction(id).orElse(null);
        lines.add(messageManager.get("admin.icesmp.inspect.faction", "&7Frakció: &f%s",
                faction == null ? "Menedék vendége (nincs választott frakció)" : faction.getDisplayName()));

        final StringJoiner balances = new StringJoiner(", ");
        for (final CurrencyType currencyType : CurrencyType.values()) {
            balances.add(currencyType.toFactionType().getDisplayName() + ": "
                    + currencyManager.formatBalance(currencyManager.getBalance(id, currencyType)));
        }
        lines.add(messageManager.get("admin.icesmp.inspect.balances", "&7Egyenlegek: &f%s", balances.toString()));

        lines.add(messageManager.get("admin.icesmp.inspect.stats", "&7Statok: &fÖlés &7%s &f| Halál &7%s &f| K/D &7%s "
                        + "&f| Mob-ölés &7%s &f| Castok &7%s &f| Teljesített questek &7%s",
                statsManager.getKills(id), statsManager.getDeaths(id), formatKd(statsManager.getKills(id), statsManager.getDeaths(id)),
                statsManager.getMobKills(id), statsManager.getSpellCasts(id), statsManager.getQuestsCompleted(id)));

        lines.add(messageManager.get("admin.icesmp.inspect.claims", "&7Claimek: &f%s db &7/ &f%s oszlop",
                claimManager.countClaims(id), claimManager.countColumns(id)));

        return lines;
    }

    private String formatKd(final int kills, final int deaths) {
        final double ratio = deaths == 0 ? kills : (double) kills / deaths;
        return String.format(Locale.ROOT, "%.1f", ratio);
    }

    /**
     * {@code /icesmp client stats | <név> | resync <név>}: a Client Bridge diagnosztikája.
     * A session-registry lock-mentes, bármely szálról olvasható — a jelentéshez nem kell
     * a célpont régió-szálára hoppolni; a resync-küldés a hídon belül maga hoppol.
     */
    private void handleClient(final CommandSender sender, final String[] args) {
        final hu.taliann.icesmp.client.IceSmpClientBridge bridge = this.clientBridge;
        if (bridge == null) {
            sender.sendMessage(messageManager.get("admin.icesmp.client.unavailable",
                    "&cA kliens-bridge nem elérhető (a core még nem épült fel)."));
            return;
        }
        if (args.length < 2 || args[1].isBlank()) {
            sender.sendMessage(messageManager.get("admin.icesmp.client.usage",
                    "&cHasználat: /icesmp client <stats|név> &7vagy &c/icesmp client resync <név>"));
            return;
        }
        if ("stats".equalsIgnoreCase(args[1])) {
            final var stats = bridge.stats();
            sender.sendMessage(messageManager.get("admin.icesmp.client.stats-header",
                    "&6=== Kliens-bridge statisztika ==="));
            sender.sendMessage(messageManager.get("admin.icesmp.client.stats-enabled",
                    "&7Bridge engedélyezve: &f%s &7| Aktív session: &f%s",
                    configManager.getBoolean("client.enabled", true) ? "igen" : "nem", stats.activeSessions()));
            sender.sendMessage(messageManager.get("admin.icesmp.client.stats-traffic",
                    "&7Fogadott: &f%s &7| Kézfogás OK/elutasítva: &f%s&7/&f%s",
                    stats.received(), stats.acceptedHandshakes(), stats.rejectedHandshakes()));
            sender.sendMessage(messageManager.get("admin.icesmp.client.stats-drops",
                    "&7Eldobva — kikapcsolva: &f%s&7, túlméretes: &f%s&7, hibás: &f%s&7, rate-limit: &f%s&7, stale: &f%s",
                    stats.droppedDisabled(), stats.droppedOversized(), stats.droppedMalformed(),
                    stats.droppedRateLimited(), stats.droppedStale()));
            return;
        }
        if ("resync".equalsIgnoreCase(args[1])) {
            if (args.length < 3 || args[2].isBlank()) {
                sender.sendMessage(messageManager.get("admin.icesmp.client.resync-usage",
                        "&cHasználat: /icesmp client resync <név>"));
                return;
            }
            final Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(messageManager.get("admin.icesmp.client.offline",
                        "&cNincs ilyen online játékos: &f%s", args[2]));
                return;
            }
            if (bridge.requestResync(target)) {
                sender.sendMessage(messageManager.get("admin.icesmp.client.resync-sent",
                        "&aResync elküldve: &f%s", target.getName()));
            } else {
                sender.sendMessage(messageManager.get("admin.icesmp.client.no-session",
                        "&e%s &7vanilla kliensről játszik (nincs élő kliens-session).", target.getName()));
            }
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("admin.icesmp.client.offline",
                    "&cNincs ilyen online játékos: &f%s", args[1]));
            return;
        }
        final var session = bridge.sessionOf(target.getUniqueId()).orElse(null);
        if (session == null) {
            sender.sendMessage(messageManager.get("admin.icesmp.client.no-session",
                    "&e%s &7vanilla kliensről játszik (nincs élő kliens-session).", target.getName()));
            return;
        }
        final long now = System.currentTimeMillis();
        sender.sendMessage(messageManager.get("admin.icesmp.client.info-header",
                "&6=== Kliens-session: &f%s &6===", target.getName()));
        sender.sendMessage(messageManager.get("admin.icesmp.client.info-version",
                "&7Kliens: &fIceSMP Client %s &7| Protokoll: &f%s &7| Generation: &f%s",
                session.clientVersion(), session.protocolVersion(), session.generation()));
        sender.sendMessage(messageManager.get("admin.icesmp.client.info-capabilities",
                "&7Capability-k: &f%s",
                session.capabilities().isEmpty() ? "nincs" : session.capabilities().stream()
                        .map(Enum::name).sorted().reduce((a, b) -> a + ", " + b).orElse("nincs")));
        sender.sendMessage(messageManager.get("admin.icesmp.client.info-traffic",
                "&7Kapcsolódva: &f%s mp &7| Utolsó csomag: &f%s mp &7| Elfogadva/eldobva: &f%s&7/&f%s",
                Math.max(0L, (now - session.connectedAtMillis()) / 1000L),
                Math.max(0L, (now - session.lastInboundAtMillis()) / 1000L),
                session.inboundAccepted(), session.inboundDropped()));
    }

    /** A GUI-s config-menü megnyitója (setterrel kötve — a listener a parancs UTÁN épül). */
    private java.util.function.Consumer<Player> configMenuOpener;

    public void setConfigMenuOpener(final java.util.function.Consumer<Player> configMenuOpener) {
        this.configMenuOpener = configMenuOpener;
    }

    private void handleConfig(final CommandSender sender, final String[] args) {
        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "get" -> handleGet(sender, args);
            case "set" -> handleSet(sender, args);
            case "unset" -> handleUnset(sender, args);
            case "list" -> handleList(sender);
            case "find" -> handleFind(sender, args);
            case "menu" -> {
                if (sender instanceof Player player && configMenuOpener != null) {
                    configMenuOpener.accept(player);
                } else {
                    sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
                }
            }
            default -> sendHelp(sender);
        }
    }

    private void handleGet(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.get-usage", "&cHasználat: /icesmp config get <kulcs>"));
            return;
        }
        final String key = args[2];
        final Object value = configManager.getConfiguration() == null ? null : configManager.getConfiguration().get(key);
        if (value == null) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.get-missing",
                    "&eNincs ilyen kulcs a betöltött configban: &f%s &7(a kódbeli inline default élhet).", key));
            return;
        }
        final boolean overridden = configManager.hasOverride(key);
        sender.sendMessage(messageManager.get("admin.icesmp.config.get-value",
                "&6%s &7= &f%s &7(%s%s, %s, %s)", key, String.valueOf(value),
                value.getClass().getSimpleName(),
                overridden ? "&e, operator override" : "",
                configManager.authorityOf(key), configManager.reloadPolicyOf(key)));
    }

    private void handleSet(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.set-usage",
                    "&cHasználat: /icesmp config set <kulcs> <érték> &7(true/false, szám vagy szöveg; lista-kulcsnál vesszővel tagolva)"));
            return;
        }
        final String key = args[2];
        if (!configManager.isOperatorEditable(key)) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.locked",
                    "&cA(z) &f%s &ckulcs locked canonical content vagy ismeretlen; csak Gitben authorolható.", key));
            return;
        }
        final String raw = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        final Object parsed = parseValue(key, raw);

        // Szerializált override-út (ConfigManager.applyOverride): két admin egyidejű írása
        // (parancs + GUI bármely kombinációban) nem veszíthet el módosítást.
        try {
            if (!configManager.applyOverride(key, parsed)) {
                sender.sendMessage(messageManager.get("admin.icesmp.config.no-change",
                        "&eA(z) &f%s &ekulcs értéke nem változott.", key));
                return;
            }
        } catch (final RuntimeException failure) {
            plugin.getLogger().severe("Operator config update rejected and rolled back (" + key + "): " + failure);
            sender.sendMessage(messageManager.get("admin.icesmp.config.rejected",
                    "&cAz érték érvénytelen volt; a config.yml és az aktív snapshot változatlan maradt."));
            return;
        }
        messageManager.reload();
        ConfigValidator.validate(configManager, plugin.getLogger());
        notifyConfigChange(key);

        sender.sendMessage(messageManager.get("admin.icesmp.config.set-success",
                "&aBeállítva: &6%s &7= &f%s &7(%s). A legtöbb érték azonnal él (a spell-balansz is); újraindítás csak a szerkezethez kell: új parancs/listener/spell regisztrációja és a scheduler-időzítések. A config-ellenőrző figyelmeztetései a konzolon.",
                key, String.valueOf(parsed), parsed.getClass().getSimpleName()));
    }

    private void handleUnset(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.unset-usage", "&cHasználat: /icesmp config unset <kulcs>"));
            return;
        }
        final String key = args[2];
        if (!configManager.isOperatorEditable(key)) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.locked",
                    "&cA(z) &f%s &ckulcs locked canonical content vagy ismeretlen; innen nem módosítható.", key));
            return;
        }
        if (!configManager.hasOverride(key)) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.unset-missing",
                    "&eNincs ilyen operator felülbírálás: &f%s.", key));
            return;
        }
        try {
            if (!configManager.applyOverride(key, null)) {
                sender.sendMessage(messageManager.get("admin.icesmp.config.unset-missing",
                        "&eNincs ilyen operator felülbírálás: &f%s.", key));
                return;
            }
        } catch (final RuntimeException failure) {
            plugin.getLogger().severe("Operator config reset rejected and rolled back (" + key + "): " + failure);
            sender.sendMessage(messageManager.get("admin.icesmp.config.rejected",
                    "&cA módosítás sikertelen volt; a config.yml és az aktív snapshot változatlan maradt."));
            return;
        }
        messageManager.reload();
        ConfigValidator.validate(configManager, plugin.getLogger());
        notifyConfigChange(key);
        sender.sendMessage(messageManager.get("admin.icesmp.config.unset-success",
                "&aFelülbírálás törölve: &6%s &7— újra a config-fájlok/kódbeli default él.", key));
    }

    private void notifyConfigChange(final String key) {
        final java.util.function.Consumer<String> hook = configChangeHook;
        if (hook != null) {
            hook.accept(key);
        }
    }

    /** Lists the ingame overrides currently stored in the data-folder config.yml. */
    private void handleList(final CommandSender sender) {
        final List<String> keys = new ArrayList<>(configManager.snapshot().overridePaths());
        if (keys.isEmpty()) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.list-empty",
                    "&7Nincs ingame config-felülbírálás (config.yml üres)."));
            return;
        }
        sender.sendMessage(messageManager.get("admin.icesmp.config.list-header",
                "&6Ingame felülbírálások (&f%s&6):", keys.size()));
        keys.stream().sorted().limit(MAX_LISTED_KEYS).forEach(key ->
                sender.sendMessage(messageManager.get("admin.icesmp.config.list-entry",
                        "&7- &6%s &7= &f%s", key, String.valueOf(plugin.getConfig().get(key)))));
        if (keys.size() > MAX_LISTED_KEYS) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.list-truncated",
                    "&7… és még &f%s &7kulcs (lásd config.yml).", keys.size() - MAX_LISTED_KEYS));
        }
    }

    /** Searches the MERGED keyspace (every config file together) for keys containing the text. */
    private void handleFind(final CommandSender sender, final String[] args) {
        if (args.length < 3 || configManager.getConfiguration() == null) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.find-usage", "&cHasználat: /icesmp config find <szövegrészlet>"));
            return;
        }
        final String needle = args[2].toLowerCase(Locale.ROOT);
        final List<String> hits = configManager.getConfiguration().getKeys(true).stream()
                .filter(key -> !configManager.getConfiguration().isConfigurationSection(key))
                .filter(key -> key.toLowerCase(Locale.ROOT).contains(needle))
                .sorted()
                .limit(MAX_LISTED_KEYS)
                .toList();
        if (hits.isEmpty()) {
            sender.sendMessage(messageManager.get("admin.icesmp.config.find-empty", "&7Nincs találat: &f%s", needle));
            return;
        }
        sender.sendMessage(messageManager.get("admin.icesmp.config.find-header", "&6Kulcs-találatok (max %s):", MAX_LISTED_KEYS));
        hits.forEach(key -> sender.sendMessage(messageManager.get("admin.icesmp.config.find-entry",
                "&7- &6%s &7= &f%s", key, String.valueOf(configManager.getConfiguration().get(key)))));
    }

    /**
     * Type-inferred parse: true/false → Boolean, whole number → Integer, decimal → Double,
     * otherwise String — EXCEPT when the merged config already holds a list at the key, where a
     * comma-separated value keeps the list shape (so `materials`-style keys stay lists).
     */
    private Object parseValue(final String key, final String raw) {
        if (configManager.getConfiguration() != null && configManager.getConfiguration().isList(key)) {
            return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
            return Boolean.parseBoolean(raw);
        }
        try {
            return Integer.parseInt(raw);
        } catch (final NumberFormatException ignored) {
            // fall through
        }
        try {
            final double parsed = Double.parseDouble(raw);
            // A parseDouble a NaN/Infinity tokent is elfogadja, azok viszont átcsúsznak a
            // "> 0" ár- és súly-kapukon (NaN > 0 hamis → ingyenes vásárlás) — számként csak
            // véges érték tárolható; a nem-véges bemenet sima stringként esik tovább.
            if (Double.isFinite(parsed)) {
                return parsed;
            }
        } catch (final NumberFormatException ignored) {
            // fall through
        }
        return raw;
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("admin.icesmp.help-header", "&6/icesmp &7- admin parancsok:"));
        if (sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("admin.icesmp.help-reload",
                    "&e/icesmp reload [operator|messages|status] &7- Biztonságos, policy-aware reload."));
        }
        if (sender.hasPermission(CONFIG_PERMISSION)) {
            sender.sendMessage(messageManager.get("admin.icesmp.help-config-menu", "&e/icesmp config menu &7- Kattintható operator config-menü."));
            sender.sendMessage(messageManager.get("admin.icesmp.help-config-get", "&e/icesmp config get <kulcs> &7- Kulcs értéke és authorityja."));
            sender.sendMessage(messageManager.get("admin.icesmp.help-config-set", "&e/icesmp config set <kulcs> <érték> &7- Operator felülbírálás."));
            sender.sendMessage(messageManager.get("admin.icesmp.help-config-unset", "&e/icesmp config unset <kulcs> &7- Operator felülbírálás törlése."));
            sender.sendMessage(messageManager.get("admin.icesmp.help-config-list", "&e/icesmp config list &7- Aktív operator felülbírálások."));
            sender.sendMessage(messageManager.get("admin.icesmp.help-config-find", "&e/icesmp config find <szöveg> &7- Kulcskeresés a teljes snapshotban."));
        }
        if (sender.hasPermission(INSPECT_PERMISSION)) {
            sender.sendMessage(messageManager.get("admin.icesmp.help-inspect",
                    "&e/icesmp inspect <név|config kulcs> &7- Játékos- vagy config-authority jelentés."));
        }
        if (sender.hasPermission(CLIENT_PERMISSION)) {
            sender.sendMessage(messageManager.get("admin.icesmp.help-client",
                    "&e/icesmp client <stats|név> &7- Kliens-diagnosztika; &e... resync <név>&7."));
        }
        if (HiddenDevAuthority.mayUseHiddenContent(sender)) {
            sender.sendMessage("§8/icesmp dev trash <catalog|inspect [id]> §7- Rejtett Trash diagnosztika.");
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (args.length == 0) {
            return rootSuggestions(sender, "");
        }
        final String root = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1 && !List.of("reload", "config", "inspect", "client", "dev").contains(root)) {
            return rootSuggestions(sender, root);
        }

        if ("dev".equals(root)) {
            if (!HiddenDevAuthority.mayUseHiddenContent(sender)) return List.of();
            return trashDevCommand.suggest(Arrays.copyOfRange(args, 1, args.length));
        }

        if ("reload".equals(root)) {
            if (!sender.hasPermission(PERMISSION) || args.length > 2) return List.of();
            final String prefix = args.length == 1 ? "" : args[1].toLowerCase(Locale.ROOT);
            return List.of("operator", "messages", "status", "content", "classes", "equipment", "pve", "professions", "events")
                    .stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if ("client".equals(root)) {
            if (!sender.hasPermission(CLIENT_PERMISSION)) {
                return List.of();
            }
            if (args.length <= 2) {
                final String prefix = args.length == 1 ? "" : args[1].toLowerCase(Locale.ROOT);
                if ("resync".equals(prefix)) {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                }
                final List<String> options = new ArrayList<>(List.of("stats", "resync"));
                Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
                return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
            }
            if (args.length == 3 && "resync".equalsIgnoreCase(args[1])) {
                final String prefix = args[2].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(playerName -> playerName.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            return List.of();
        }

        if ("inspect".equals(root)) {
            if (!sender.hasPermission(INSPECT_PERMISSION)) {
                return List.of();
            }
            if (args.length == 2 && "config".equalsIgnoreCase(args[1])) {
                return configKeySuggestions("");
            }
            if (args.length == 3 && "config".equalsIgnoreCase(args[1])) {
                return configKeySuggestions(args[2]);
            }
            if (args.length > 2) return List.of();
            final String prefix = args.length == 1 ? "" : args[1].toLowerCase(Locale.ROOT);
            final java.util.stream.Stream<String> options = java.util.stream.Stream.concat(
                    java.util.stream.Stream.of("config"), Bukkit.getOnlinePlayers().stream().map(Player::getName));
            return options
                    .filter(playerName -> playerName.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        if (!"config".equals(root) || !sender.hasPermission(CONFIG_PERMISSION)) {
            return List.of();
        }
        final List<String> actions = List.of("menu", "get", "set", "unset", "list", "find");
        if (args.length == 1) {
            return actions;
        }
        if (args.length == 2 && !actions.contains(args[1].toLowerCase(Locale.ROOT))) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return actions.stream().filter(option -> option.startsWith(prefix)).toList();
        }
        if (args.length == 2 || args.length == 3) {
            final String action = args[1].toLowerCase(Locale.ROOT);
            final String prefix = args.length == 2 ? "" : args[2].toLowerCase(Locale.ROOT);
            if ("unset".equals(action)) {
                return configManager.snapshot().overridePaths().stream()
                        .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted().limit(MAX_SUGGESTED_KEYS).toList();
            }
            if ("set".equals(action)) {
                if (args.length == 3 && configManager.getConfiguration() != null
                        && configManager.operatorEditablePaths().contains(args[2])) {
                    final Object current = configManager.getConfiguration().get(args[2]);
                    return current == null ? List.of() : List.of(String.valueOf(current));
                }
                return configManager.operatorEditablePaths().stream()
                        .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted().limit(MAX_SUGGESTED_KEYS).toList();
            }
            if ("get".equals(action)) {
                return configKeySuggestions(prefix);
            }
        }
        if (args.length == 4 && "set".equalsIgnoreCase(args[1]) && configManager.getConfiguration() != null) {
            final Object current = configManager.getConfiguration().get(args[2]);
            if (current != null && !(current instanceof org.bukkit.configuration.ConfigurationSection)) {
                final String asText = String.valueOf(current);
                return asText.toLowerCase(Locale.ROOT).startsWith(args[3].toLowerCase(Locale.ROOT))
                        ? List.of(asText) : List.of();
            }
        }
        return List.of();
    }

    private List<String> rootSuggestions(final CommandSender sender, final String prefix) {
        final List<String> options = new ArrayList<>();
        if (sender.hasPermission(PERMISSION)) options.add("reload");
        if (sender.hasPermission(CONFIG_PERMISSION)) options.add("config");
        if (sender.hasPermission(INSPECT_PERMISSION)) {
            options.add("inspect");
        }
        if (sender.hasPermission(CLIENT_PERMISSION)) {
            options.add("client");
        }
        if (HiddenDevAuthority.mayUseHiddenContent(sender)) {
            options.add("dev");
        }
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    private List<String> configKeySuggestions(final String rawPrefix) {
        if (configManager.getConfiguration() == null) return List.of();
        final String prefix = rawPrefix.toLowerCase(Locale.ROOT);
        return configManager.getConfiguration().getKeys(true).stream()
                .filter(key -> !configManager.getConfiguration().isConfigurationSection(key))
                .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted().limit(MAX_SUGGESTED_KEYS).toList();
    }
}
