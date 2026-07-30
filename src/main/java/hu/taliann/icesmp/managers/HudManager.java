package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live player HUD: a sidebar scoreboard (faction, currency,
 * class + level, active event), faction-coloured tab-list names, and shared
 * boss-bars for the raid / blood moon / world boss. Refreshed on a ~1s tick.
 *
 * <p>Folia: the tick runs on the global region scheduler, but every per-player
 * scoreboard/tab/boss-bar mutation hops onto that player's own region thread.
 */
public final class HudManager {

    /** A vanilla sidebar scoreboard hard maximuma. */
    private static final int LINES = 15;
    private static final String OBJECTIVE = "icesmp_hud";
    /** Statikus elválasztó, ha nincs 'bar' animáció definiálva a tablist.yml-ben. */
    private static final String FALLBACK_SEPARATOR = "&8&m                       ";

    // /hud toggleable section keys (buildLines() rows). "mind" hides the whole sidebar.
    public static final String SECTION_FACTION = "frakcio";
    public static final String SECTION_CURRENCY = "valuta";
    public static final String SECTION_CLASS = "kaszt";
    public static final String SECTION_RESOURCE = "eroforras";
    public static final String SECTION_EVENT = "esemeny";
    public static final String SECTION_PARTY = "csapat";
    public static final String SECTION_ALL = "mind";
    public static final List<String> SECTIONS = List.of(
            SECTION_FACTION, SECTION_CURRENCY, SECTION_CLASS, SECTION_RESOURCE, SECTION_EVENT, SECTION_PARTY);

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final CurrencyManager currencyManager;
    private final JobManager jobManager;
    private final RaidManager raidManager;
    private final BloodMoonManager bloodMoonManager;
    private final WorldBossManager worldBossManager;
    private final ResourceManager resourceManager;
    private final PartyManager partyManager;
    private final CaravanManager caravanManager;
    private final EscortManager escortManager;
    private final AbundanceManager abundanceManager;
    private final ServerChallengeManager serverChallengeManager;
    private final MeteorEventManager meteorEventManager;
    private final GatheringBuffManager gatheringBuffManager;
    private final hu.taliann.icesmp.utils.TextAnimator animator;
    private final SeasonManager seasonManager;
    private final DailyQuestManager dailyQuestManager;
    /** A harc-fókusz célpont-sorának adatforrása; setterrel kötve, regisztrációkor. */
    private volatile hu.taliann.icesmp.listeners.DamageIndicatorListener damageIndicators;

    public void setDamageIndicators(final hu.taliann.icesmp.listeners.DamageIndicatorListener damageIndicators) {
        this.damageIndicators = damageIndicators;
    }

    private final BossBar raidBar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
    private final BossBar bloodMoonBar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private final BossBar worldBossBar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_6);

    private final ConcurrentHashMap<UUID, Team[]> playerTeams = new ConcurrentHashMap<>();
    /** játékos → az utoljára kiküldött oldalsáv-sorok (diff-cache: azonos sor nem megy ki újra). */
    private final ConcurrentHashMap<UUID, List<Component>> lastLines = new ConcurrentHashMap<>();
    /** A YAML-layout egyszer parse-olódik konfiguráció-példányonként; reloadkor új objektum érkezik. */
    private volatile FileConfiguration cachedLayoutSource;
    private volatile List<HudSidebarLayout.Entry> cachedLayout = HudSidebarLayout.defaults();

    private final NamespacedKey hiddenSectionsKey;
    /** Per-player /hud toggle state, cached in memory (PDC is only touched on load/save, never per tick). */
    private final ConcurrentHashMap<UUID, Set<String>> hiddenSectionsCache = new ConcurrentHashMap<>();

    /**
     * A thread-safe snapshot of a player's HUD data, refreshed on the player's region thread each
     * tick. Read by the PlaceholderAPI bridge from arbitrary threads (e.g. TAB's async refresh), so it
     * must NOT touch the live player/PDC off-thread — only this immutable record.
     */
    public record HudSnapshot(String faction, String factionId, String className, int classLevel,
                              String balance, boolean hasClass, int resource, int resourceMax,
                              int resourcePercent, String resourceName, String resourceBar,
                              String event, List<String> partyLines) {
    }

    private final ConcurrentHashMap<UUID, HudSnapshot> snapshots = new ConcurrentHashMap<>();

    public HudManager(final JavaPlugin plugin, final ConfigManager configManager, final FactionManager factionManager,
                      final CurrencyManager currencyManager, final JobManager jobManager, final RaidManager raidManager,
                      final BloodMoonManager bloodMoonManager, final WorldBossManager worldBossManager,
                      final ResourceManager resourceManager, final PartyManager partyManager,
                      final CaravanManager caravanManager, final EscortManager escortManager,
                      final AbundanceManager abundanceManager, final ServerChallengeManager serverChallengeManager,
                      final MeteorEventManager meteorEventManager, final GatheringBuffManager gatheringBuffManager,
                      final hu.taliann.icesmp.utils.TextAnimator animator,
                      final SeasonManager seasonManager, final DailyQuestManager dailyQuestManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.currencyManager = currencyManager;
        this.jobManager = jobManager;
        this.raidManager = raidManager;
        this.bloodMoonManager = bloodMoonManager;
        this.worldBossManager = worldBossManager;
        this.resourceManager = resourceManager;
        this.partyManager = partyManager;
        this.caravanManager = caravanManager;
        this.escortManager = escortManager;
        this.abundanceManager = abundanceManager;
        this.serverChallengeManager = serverChallengeManager;
        this.meteorEventManager = meteorEventManager;
        this.gatheringBuffManager = gatheringBuffManager;
        this.animator = animator;
        this.seasonManager = seasonManager;
        this.dailyQuestManager = dailyQuestManager;
        this.hiddenSectionsKey = new NamespacedKey(plugin, "hud_hidden_sections");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("hud.enabled", true);
    }

    /**
     * Whether IceSMP draws its own scoreboard sidebar. Set false when another plugin (e.g. TAB) owns
     * the scoreboard — IceSMP then won't fight it (use the %icesmp_...% PlaceholderAPI placeholders).
     */
    private boolean sidebarEnabled() {
        return configManager.getBoolean("hud.sidebar-enabled", true);
    }

    /** Whether IceSMP sets faction-coloured tab-list names. Set false when TAB owns the tab list. */
    private boolean tablistEnabled() {
        return configManager.getBoolean("hud.tablist-enabled", true);
    }

    /**
     * The set of HUD section keys ({@link #SECTIONS} + {@link #SECTION_ALL}) hidden by the player
     * via /hud. Loaded from PDC on first access after (re)join and cached in memory afterwards —
     * never re-read from PDC on the per-second HUD tick.
     */
    public Set<String> hiddenSections(final Player player) {
        return hiddenSectionsCache.computeIfAbsent(player.getUniqueId(), id -> loadHiddenSections(player));
    }

    private Set<String> loadHiddenSections(final Player player) {
        final String raw = player.getPersistentDataContainer().get(hiddenSectionsKey, PersistentDataType.STRING);
        final Set<String> hidden = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) {
            hidden.addAll(Arrays.asList(raw.split(",")));
        }
        return hidden;
    }

    /** Whether the given section (or {@link #SECTION_ALL}) is currently hidden for the player. */
    public boolean isSectionHidden(final Player player, final String section) {
        return hiddenSections(player).contains(section);
    }

    /**
     * Toggles a HUD section (or "mind" for the whole sidebar) on/off for the player, persists the
     * result to their PDC (restart-proof) and refreshes the in-memory cache. Called from /hud, i.e.
     * on the player's own region thread, so touching their own PDC directly is Folia-safe.
     *
     * @return true if the section is hidden after the toggle, false if it is now shown
     */
    public boolean toggleSection(final Player player, final String section) {
        final Set<String> hidden = new LinkedHashSet<>(hiddenSections(player));
        final boolean nowHidden = hidden.add(section);
        if (!nowHidden) {
            hidden.remove(section);
        }
        hiddenSectionsCache.put(player.getUniqueId(), hidden);
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (hidden.isEmpty()) {
            pdc.remove(hiddenSectionsKey);
        } else {
            pdc.set(hiddenSectionsKey, PersistentDataType.STRING, String.join(",", hidden));
        }
        return nowHidden;
    }

    /** Whether the sidebar should render at all for this player (config gate + their own "mind" toggle). */
    private boolean sidebarVisibleFor(final Player player) {
        return sidebarEnabled() && !isSectionHidden(player, SECTION_ALL);
    }

    /** Builds the player's HUD (called on join, on that player's region thread). */
    public void init(final Player player) {
        if (!isEnabled()) {
            return;
        }
        if (sidebarVisibleFor(player)) {
            buildSidebar(player);
        }
        update(player);
    }

    /**
     * A játékos SAJÁT scoreboardja — ha még a közös main boardon áll, kap egy sajátot.
     * A boardot a TablistManager nametag/ping rétege is használja, ezért a sidebar
     * ki-bekapcsolása SOSEM cseréli le magát a boardot (csak az objective-et kezeli).
     */
    private Scoreboard ownBoard(final Player player) {
        final ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return null;
        }
        Scoreboard board = player.getScoreboard();
        if (board == manager.getMainScoreboard()) {
            board = manager.getNewScoreboard();
            player.setScoreboard(board);
        }
        return board;
    }

    private Component sidebarTitle() {
        return hu.taliann.icesmp.utils.TextAnimator.legacy(
                configManager.getString("hud.sidebar.title", "✦ Ice SMP ✦"));
    }

    private void syncSidebarTitle(final Objective objective) {
        final Component title = sidebarTitle();
        if (!title.equals(objective.displayName())) {
            objective.displayName(title);
        }
    }

    /** Registers the IceSMP scoreboard sidebar for the player (only when sidebar-enabled). */
    private void buildSidebar(final Player player) {
        final Scoreboard board = ownBoard(player);
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, sidebarTitle());
        }
        syncSidebarTitle(objective);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        final Team[] teams = new Team[LINES];
        for (int i = 0; i < LINES; i++) {
            Team team = board.getTeam("hud_" + i);
            if (team == null) {
                team = board.registerNewTeam("hud_" + i);
                team.addEntry(entry(i));
            }
            teams[i] = team;
        }
        // Row scores are set lazily in update(), so unused rows (e.g. the party
        // frames while not in a party) don't render as blank sidebar lines.
        playerTeams.put(player.getUniqueId(), teams);
    }

    /** Refreshes the sidebar text and/or tab-list name (on the player's region thread). */
    public void update(final Player player) {
        if (!isEnabled()) {
            return;
        }
        if (sidebarVisibleFor(player)) {
            Team[] teams = playerTeams.get(player.getUniqueId());
            if (teams == null) {
                // Friss (üres prefixű) sorok épülnek — a régi diff-cache nem érvényes rájuk.
                lastLines.remove(player.getUniqueId());
                buildSidebar(player);
                teams = playerTeams.get(player.getUniqueId());
            }
            if (teams != null) {
                final List<Component> lines = buildLines(player);
                // Last-line diff-cache: változatlan sor nem megy ki csomagként — enélkül
                // a HUD másodpercenként ~15 azonos tartalmú team-frissítést küldene fejenként.
                final List<Component> previous = lastLines.get(player.getUniqueId());
                final Scoreboard board = player.getScoreboard();
                final Objective objective = board.getObjective(OBJECTIVE);
                if (objective != null) {
                    // /icesmp reload után a cím is azonnal követi a szerkesztett configot.
                    syncSidebarTitle(objective);
                }
                for (int i = 0; i < LINES; i++) {
                    final String entry = entry(i);
                    if (i < lines.size()) {
                        if (previous == null || i >= previous.size() || !lines.get(i).equals(previous.get(i))) {
                            teams[i].prefix(lines.get(i));
                        }
                        if (objective != null && !objective.getScore(entry).isScoreSet()) {
                            final org.bukkit.scoreboard.Score score = objective.getScore(entry);
                            score.setScore(LINES - i);
                            // A jobb szélső piros sor-számok elrejtése (1.20.3+ API).
                            score.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
                        }
                    } else if (previous == null || previous.size() > i) {
                        // Hide the unused row entirely (no empty sidebar line).
                        teams[i].prefix(Component.empty());
                        board.resetScores(entry);
                    }
                }
                lastLines.put(player.getUniqueId(), lines);
            }
        } else if (playerTeams.remove(player.getUniqueId()) != null) {
            lastLines.remove(player.getUniqueId());
            // Sidebar kikapcsolva (config vagy /hud mind): csak az objective-et és a sor-teameket
            // szedjük le — a board marad, mert a TablistManager nametag/ping rétege azon él.
            final Scoreboard board = player.getScoreboard();
            final Objective objective = board.getObjective(OBJECTIVE);
            if (objective != null) {
                objective.unregister();
            }
            for (int i = 0; i < LINES; i++) {
                final Team team = board.getTeam("hud_" + i);
                if (team != null) {
                    team.unregister();
                }
            }
        }
        // Tab-név: alapesetben a TablistManager rendezi (LP-prefix + frakció-szín + suffix,
        // diff-elve); ez az egyszerű frakció-színes fallback csak akkor él, ha a natív
        // tablist ki van kapcsolva, különben a két írás egymással villogna.
        if (tablistEnabled() && !configManager.getBoolean("tablist.enabled", true)) {
            player.playerListName(tabName(player));
        }
    }

    /**
     * Periodic refresh: updates the shared boss-bars, then refreshes every online
     * player's sidebar/tab and shows/hides their boss-bars on their region thread.
     */
    public void tick() {
        if (!isEnabled()) {
            return;
        }

        refreshBossBarState();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                update(player);
                applyBossBars(player);
                // Refresh the thread-safe snapshot on the player's own region thread (for PlaceholderAPI).
                snapshots.put(player.getUniqueId(), buildSnapshot(player));
            }, null);
        }
    }

    /** The latest thread-safe HUD snapshot for a player (null until the first tick). Used by PlaceholderAPI. */
    public HudSnapshot snapshot(final UUID playerId) {
        return snapshots.get(playerId);
    }

    private HudSnapshot buildSnapshot(final Player player) {
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        final JobType job = jobManager.getPrimaryJob(player);
        final boolean hasClass = job != null;
        // The snapshot's hasClass flag is solely the resource-display gate in the PlaceholderAPI
        // bridge, so it also folds in the resource system's enabled state — with the system off,
        // %icesmp_resource...% goes blank instead of showing a phantom full bar.
        final boolean showResource = hasClass && resourceManager.isEnabled();
        final double balance = currencyManager.getBalance(player, faction == null ? FactionType.NEUTRAL : faction);
        return new HudSnapshot(
                faction == null ? "nincs" : faction.getDisplayName(),
                faction == null ? "" : faction.name(),
                hasClass ? PlainTextComponentSerializer.plainText().serialize(job.getDisplayName()) : "nincs",
                hasClass ? jobManager.getPrimaryLevel(player) : 0,
                currencyManager.formatBalance(balance),
                showResource,
                showResource ? resourceManager.resourceValue(player) : 0,
                showResource ? resourceManager.resourceMax(player) : resourceManager.resourceMax(),
                showResource ? resourceManager.resourcePercent(player) : 0,
                resourceManager.resourceName(player),
                showResource ? resourceManager.resourceBarPlain(player) : "",
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(eventLabel()),
                partyLinesPlain(player));
    }

    /**
     * Plain-text party-frame lines for the PlaceholderAPI bridge (%icesmp_party_N%),
     * so scoreboard plugins like TAB can render the party HUD when the IceSMP
     * sidebar is disabled. Empty when the player is not in a party.
     */
    private List<String> partyLinesPlain(final Player player) {
        final PartyManager.Party party = partyManager.getParty(player.getUniqueId());
        if (party == null || !configManager.getBoolean("party.hud-enabled", true)) {
            return List.of();
        }
        final List<String> lines = new ArrayList<>();
        for (final UUID memberId : party.getMembers()) {
            // Legacy (§) serialization keeps the colour-coded health bar readable in TAB;
            // plain text stripped the colours and every bar segment looked identical.
            lines.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(partyMemberLine(party, memberId)));
        }
        return List.copyOf(lines);
    }

    public void cleanup(final Player player) {
        if (player == null) {
            return;
        }
        playerTeams.remove(player.getUniqueId());
        lastLines.remove(player.getUniqueId());
        snapshots.remove(player.getUniqueId());
        hiddenSectionsCache.remove(player.getUniqueId());
        player.hideBossBar(raidBar);
        player.hideBossBar(bloodMoonBar);
        player.hideBossBar(worldBossBar);
    }

    private void refreshBossBarState() {
        final RaidManager.ActiveRaid raid = raidManager.getActiveRaid();
        if (raid != null) {
            final long totalMs = Math.max(1L, configManager.getInt("factions.raid.duration-minutes", 15) * 60L * 1000L);
            final long remaining = Math.max(0L, raid.endsAtMillis() - System.currentTimeMillis());
            final String label = raid.inWarmup()
                    ? "⚔ RAID (felkészülés — /faction raid join): "
                            + raid.attacker().getDisplayName() + " ⚔ " + raid.defender().getDisplayName()
                    : "⚔ RAID: " + raid.attacker().getDisplayName() + " " + raidManager.getPoints(raid.attacker())
                            + " ⚔ " + raidManager.getPoints(raid.defender()) + " " + raid.defender().getDisplayName();
            raidBar.name(Component.text(label, NamedTextColor.RED));
            raidBar.progress(clamp((float) remaining / (float) totalMs));
        }
        bloodMoonBar.name(Component.text("🌕 VÉRHOLD — a szörnyek erősebbek", NamedTextColor.DARK_RED));
        final float bossFraction = clamp(worldBossManager.getBossHealthFraction());
        worldBossBar.name(Component.text("☠ Világboss — " + Math.round(bossFraction * 100.0F) + "% HP",
                NamedTextColor.LIGHT_PURPLE));
        worldBossBar.progress(bossFraction);
    }

    private void applyBossBars(final Player player) {
        toggle(player, raidBar, raidManager.isRaidActive());
        toggle(player, bloodMoonBar, bloodMoonManager.isActive());
        toggle(player, worldBossBar, worldBossManager.isBossActive());
    }

    private void toggle(final Player player, final BossBar bar, final boolean show) {
        if (show) {
            player.showBossBar(bar);
        } else {
            player.hideBossBar(bar);
        }
    }

    /**
     * Kiszorítási sorrend, ha a tartalom nem fér a 15 soros keretbe: az itt ELÖL álló
     * szekció sorai esnek ki először (a harc-kritikus erőforrás/party marad utoljára).
     */
    private static final List<String> EVICTION_ORDER = List.of(
            SECTION_EVENT, SECTION_CURRENCY, SECTION_FACTION, SECTION_CLASS, SECTION_PARTY, SECTION_RESOURCE);

    /**
     * YAML-SZERKESZTHETŐ dinamikus oldalsáv ({@code hud.sidebar.layout}):
     * <ul>
     *   <li>a sorrend, a statikus szövegek, a színek és az elválasztók configból jönnek;</li>
     *   <li>a {@code text} sorok tokenjei dinamikus értékekké oldódnak fel;</li>
     *   <li>a {@code target/resource/info/party} típusok csak akkor adnak sort, amikor van
     *       megjeleníthető adatuk;</li>
     *   <li>a /hud szekció-rejtés, a harc-fókusz és a prioritás-kiszorítás változatlanul él;</li>
     *   <li>a vanilla 15 soros maximumot a teljes, már kibontott layouton tartjuk be.</li>
     * </ul>
     */
    private List<Component> buildLines(final Player player) {
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        final double balance = currencyManager.getBalance(player, faction == null ? FactionType.NEUTRAL : faction);
        final JobType job = jobManager.getPrimaryJob(player);
        final Set<String> hidden = hiddenSections(player);

        final boolean combatFocus = configManager.getBoolean("hud.dynamic.combat-focus", true)
                && resourceManager.isInCombat(player.getUniqueId(),
                        Math.max(1L, configManager.getLong("hud.dynamic.combat-grace-seconds", 8L)) * 1000L);
        final List<String> combatVisible = configManager.getConfiguration() == null
                ? List.of(SECTION_RESOURCE, SECTION_PARTY)
                : orDefault(configManager.getConfiguration().getStringList("hud.dynamic.combat-visible-sections"),
                        List.of(SECTION_RESOURCE, SECTION_PARTY));

        final PartyManager.Party party = partyManager.getParty(player.getUniqueId());
        final InfoFrame info = rotatingInfoFrame(player);
        final Map<String, String> tokens = sidebarTokens(player, faction, job, balance, info);
        final List<HudSidebarLayout.Row<Component>> rows = new ArrayList<>();

        for (final HudSidebarLayout.Entry entry : sidebarLayout()) {
            if (!visible(entry.section(), hidden, combatFocus, combatVisible)) {
                continue;
            }
            switch (entry.type()) {
                case SPACER -> rows.add(new HudSidebarLayout.Row<>(entry.section(),
                        renderTemplate(entry.text().isEmpty() ? " " : entry.text(), tokens)));
                case SEPARATOR -> rows.add(new HudSidebarLayout.Row<>(entry.section(),
                        entry.text().isEmpty() ? separator() : renderTemplate(entry.text(), tokens)));
                case TEXT -> rows.add(new HudSidebarLayout.Row<>(entry.section(),
                        renderTemplate(entry.text(), tokens)));
                case TARGET -> {
                    final Component target = combatFocus
                            ? targetLine(player, entry.text(), tokens) : null;
                    if (target != null) {
                        rows.add(new HudSidebarLayout.Row<>(entry.section(), target));
                    }
                }
                case RESOURCE -> {
                    if (job != null && resourceManager.isEnabled()) {
                        rows.add(new HudSidebarLayout.Row<>(entry.section(),
                                renderTemplate(entry.text(), tokens)));
                    }
                }
                case INFO -> rows.add(new HudSidebarLayout.Row<>(entry.section(),
                        renderTemplate(entry.text(), tokens)));
                case PARTY -> {
                    if (party != null && configManager.getBoolean("party.hud-enabled", true)) {
                        if (!entry.text().isBlank()) {
                            rows.add(new HudSidebarLayout.Row<>(entry.section(),
                                    renderTemplate(entry.text(), tokens)));
                        }
                        for (final UUID memberId : party.getMembers()) {
                            rows.add(new HudSidebarLayout.Row<>(entry.section(),
                                    partyMemberLine(party, memberId)));
                        }
                    }
                }
            }
        }

        return HudSidebarLayout.fit(rows, LINES, sidebarEvictionOrder()).stream()
                .map(HudSidebarLayout.Row::value)
                .toList();
    }

    /** Látszik-e a szekció: /hud toggle + (harcban) a harc-fókusz fehérlistája. */
    private boolean visible(final String section, final Set<String> hidden,
                            final boolean combatFocus, final List<String> combatVisible) {
        return section == null || section.isBlank()
                || (!hidden.contains(section) && (!combatFocus || combatVisible.contains(section)));
    }

    private static List<String> orDefault(final List<String> value, final List<String> fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    /** Reload-barát, lock-minimal layout cache: a ConfigManager reloadkor új YAML-objektumot publikál. */
    private List<HudSidebarLayout.Entry> sidebarLayout() {
        final FileConfiguration source = configManager.getConfiguration();
        if (source == null) {
            return HudSidebarLayout.defaults();
        }
        if (source != cachedLayoutSource) {
            synchronized (this) {
                if (source != cachedLayoutSource) {
                    cachedLayout = HudSidebarLayout.parse(source.getMapList("hud.sidebar.layout"));
                    cachedLayoutSource = source;
                }
            }
        }
        return cachedLayout;
    }

    /** Configured priorities first; missing known sections are appended to keep overflow safe. */
    private List<String> sidebarEvictionOrder() {
        final List<String> configured = configManager.getConfiguration() == null ? List.of()
                : configManager.getConfiguration().getStringList("hud.sidebar.eviction-order");
        if (configured.isEmpty()) {
            return EVICTION_ORDER;
        }
        final Set<String> completed = new LinkedHashSet<>();
        for (final String section : configured) {
            if (section != null && !section.isBlank()) {
                completed.add(section.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        completed.addAll(EVICTION_ORDER);
        return List.copyOf(completed);
    }

    private Map<String, String> sidebarTokens(final Player player, final FactionType faction,
                                               final JobType job, final double balance,
                                               final InfoFrame info) {
        final Map<String, String> tokens = new HashMap<>();
        final Component factionValue = faction == null
                ? Component.text("nincs", NamedTextColor.GRAY)
                : Component.text(faction.getDisplayName(), factionColor(faction));
        final Component classValue = job == null
                ? Component.text("nincs", NamedTextColor.GRAY)
                : job.getDisplayName();
        final boolean hasResource = job != null && resourceManager.isEnabled();
        final long seasonDays = Math.max(0L,
                (seasonManager.getSeasonEndMillis() - System.currentTimeMillis())
                        / (24L * 60L * 60L * 1000L));

        tokens.put("player", player.getName());
        tokens.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        tokens.put("max", String.valueOf(Bukkit.getMaxPlayers()));
        tokens.put("ping", String.valueOf(Math.max(0, player.getPing())));
        tokens.put("faction", legacy(factionValue));
        tokens.put("class", legacy(classValue));
        tokens.put("class_level", String.valueOf(job == null ? 0 : jobManager.getPrimaryLevel(player)));
        tokens.put("balance", currencyManager.formatBalance(balance));
        tokens.put("resource_name", hasResource ? resourceManager.resourceName(player) : "");
        tokens.put("resource_value", hasResource ? String.valueOf(resourceManager.resourceValue(player)) : "");
        tokens.put("resource_max", hasResource ? String.valueOf(resourceManager.resourceMax(player)) : "");
        tokens.put("resource_percent", hasResource ? String.valueOf(resourceManager.resourcePercent(player)) : "");
        tokens.put("resource_bar", hasResource ? resourceManager.resourceBarPlain(player) : "");
        tokens.put("info_label", info.label());
        tokens.put("info_value", legacy(info.value()));
        tokens.put("event", legacy(eventLabel()));
        tokens.put("season_days", String.valueOf(seasonDays));
        return tokens;
    }

    private Component targetLine(final Player viewer, final String configuredTemplate,
                                 final Map<String, String> baseTokens) {
        final hu.taliann.icesmp.listeners.DamageIndicatorListener indicators = damageIndicators;
        if (indicators == null) {
            return null;
        }
        final hu.taliann.icesmp.listeners.DamageIndicatorListener.LastTarget target =
                indicators.lastTarget(viewer.getUniqueId());
        if (target == null) {
            return null;
        }

        Component health = Component.empty();
        if (target.player()) {
            final Player targetPlayer = Bukkit.getPlayer(target.targetId());
            if (targetPlayer != null && targetPlayer.isOnline()) {
                try {
                    // Cross-region mező-olvasás a party-frame try/catch mintájával.
                    final org.bukkit.attribute.AttributeInstance maxAttr =
                            targetPlayer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    final double max = Math.max(1.0D, maxAttr == null ? 20.0D : maxAttr.getValue());
                    health = healthBar(Math.max(0.0D, targetPlayer.getHealth()), max);
                } catch (final Exception ignored) {
                    // Régió-átmenet — ezen a ticken a név magában is elég.
                }
            }
        }
        final Map<String, String> tokens = new HashMap<>(baseTokens);
        tokens.put("target_name", target.targetName());
        tokens.put("target_health", legacy(health));
        final String template = configuredTemplate == null || configuredTemplate.isBlank()
                ? "&c⌖ &f{target_name} {target_health}" : configuredTemplate;
        return renderTemplate(template, tokens);
    }

    /**
     * Rotáló infósor az esemény-sor helyén: időalapon (hud.dynamic.rotation-seconds, default 4 mp)
     * váltakozik az esemény-állapot, a szezon-visszaszámláló és a napi kihívás állása. A tartalom
     * nélküli frame-ek kimaradnak (pl. nyugalomban az esemény-frame), így a sor mindig mond
     * valamit; kikapcsolva (hud.dynamic.rotating-line: false) a régi fix esemény-sor él.
     */
    private record InfoFrame(String label, Component value) {
    }

    private InfoFrame rotatingInfoFrame(final Player player) {
        if (!configManager.getBoolean("hud.dynamic.rotating-line", true)) {
            return new InfoFrame("ᴇꜱᴇᴍéɴʏ", eventLabel());
        }

        final List<InfoFrame> frames = new ArrayList<>(3);
        final Component event = eventLabel();
        final boolean quiet = "nyugalom".equals(PlainTextComponentSerializer.plainText().serialize(event));
        if (!quiet) {
            frames.add(new InfoFrame("ᴇꜱᴇᴍéɴʏ", event));
        }
        final long remainingDays = Math.max(0L,
                (seasonManager.getSeasonEndMillis() - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L));
        frames.add(new InfoFrame("ꜱᴢᴇᴢᴏɴ",
                Component.text("még ~" + remainingDays + " nap", NamedTextColor.WHITE)));
        final DailyQuestManager.Daily daily = dailyQuestManager.isEnabled() ? dailyQuestManager.getActive() : null;
        if (daily != null) {
            frames.add(new InfoFrame("ɴᴀᴘɪ", dailyQuestManager.isDone(player)
                    ? Component.text("kész ✔", NamedTextColor.GREEN)
                    : Component.text(dailyQuestManager.getProgress(player) + "/" + daily.amount(),
                            NamedTextColor.YELLOW)));
        }

        final long rotationMillis = Math.max(2L, configManager.getLong("hud.dynamic.rotation-seconds", 4L)) * 1000L;
        return frames.get((int) ((System.currentTimeMillis() / rotationMillis) % frames.size()));
    }

    private Component renderTemplate(final String template, final Map<String, String> tokens) {
        String rendered = HudSidebarLayout.render(template, tokens);
        int guard = 0;
        while (animator != null && rendered.contains("{anim:") && guard++ < 8) {
            final int start = rendered.indexOf("{anim:");
            final int end = rendered.indexOf('}', start);
            if (end < 0) {
                break;
            }
            final String name = rendered.substring(start + 6, end);
            rendered = rendered.substring(0, start) + animator.frame(name) + rendered.substring(end + 1);
        }
        return hu.taliann.icesmp.utils.TextAnimator.legacy(rendered);
    }

    private static String legacy(final Component component) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(component == null ? Component.empty() : component);
    }

    /** Animált elválasztó-sor (a tablist.yml 'bar' animációja; nélküle statikus vonal). */
    private Component separator() {
        final String frame = animator == null ? "" : animator.frame("bar");
        return hu.taliann.icesmp.utils.TextAnimator.legacy(frame.isEmpty() ? FALLBACK_SEPARATOR : frame);
    }

    /** One party-frame row: 👑/• + name + a colour-coded 5-segment health bar + hearts. */
    private Component partyMemberLine(final PartyManager.Party party, final UUID memberId) {
        final boolean leader = memberId.equals(party.getLeader());
        final Component marker = Component.text(leader ? "👑 " : "• ",
                leader ? NamedTextColor.GOLD : NamedTextColor.GRAY);

        final Player member = Bukkit.getPlayer(memberId);
        if (member == null || !member.isOnline()) {
            return marker.append(Component.text("?", NamedTextColor.DARK_GRAY));
        }
        final String name = member.getName().length() > 10
                ? member.getName().substring(0, 10) : member.getName();

        try {
            // Cross-region reads on Folia: health/max-health are plain field reads; if the
            // member's region is mid-transition we fall back to a placeholder for one tick.
            final org.bukkit.attribute.AttributeInstance maxAttr =
                    member.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            final double max = Math.max(1.0D, maxAttr == null ? 20.0D : maxAttr.getValue());
            final double hp = Math.max(0.0D, member.getHealth());
            return marker.append(Component.text(name + " ", NamedTextColor.WHITE)).append(healthBar(hp, max));
        } catch (final Exception ignored) {
            return marker.append(Component.text(name + " …", NamedTextColor.DARK_GRAY));
        }
    }

    /** A 5-segment health bar coloured by remaining fraction (green/yellow/red) plus the heart count. */
    private Component healthBar(final double hp, final double max) {
        final double fraction = Math.max(0.0D, Math.min(1.0D, hp / max));
        final int segments = 5;
        final int filled = (int) Math.ceil(fraction * segments);
        final NamedTextColor color = fraction > 0.66D ? NamedTextColor.GREEN
                : fraction > 0.33D ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return Component.text("▮".repeat(filled), color)
                .append(Component.text("▮".repeat(segments - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + (int) Math.ceil(hp / 2.0D) + "❤", color));
    }

    /**
     * The running world events on ONE line (sidebar + %icesmp_event%), width-capped:
     * at most two names are spelled out, the rest collapse into a "+N" suffix so the
     * scoreboard can never grow arbitrarily wide.
     */
    private Component eventLabel() {
        final List<String> active = new ArrayList<>();
        if (raidManager.isRaidActive()) {
            active.add("Raid!");
        }
        if (worldBossManager.isBossActive()) {
            active.add("Világboss");
        }
        if (bloodMoonManager.isActive()) {
            active.add("Vérhold");
        }
        if (caravanManager.isActive()) {
            active.add("Karaván");
        }
        if (escortManager.isActive()) {
            active.add("Kíséret");
        }
        if (abundanceManager.isActive()) {
            active.add("Bőség-idő");
        }
        if (serverChallengeManager.isActive()) {
            active.add("Kihívás");
        }
        if (meteorEventManager.isActive()) {
            active.add("Meteor");
        }
        final String buff = gatheringBuffManager.describeActive();
        if (buff != null) {
            active.add(buff);
        }
        if (active.isEmpty()) {
            return Component.text("nyugalom", NamedTextColor.GRAY);
        }
        final String shown = String.join(", ", active.subList(0, Math.min(2, active.size())));
        final Component label = Component.text(shown, NamedTextColor.YELLOW);
        return active.size() <= 2 ? label
                : label.append(Component.text(" +" + (active.size() - 2), NamedTextColor.GOLD));
    }

    private Component tabName(final Player player) {
        // A név MAGA kapja a frakció színét — külön [Frakció] tag nélkül (rövidebb tab-lista).
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        return Component.text(player.getName(), faction == null ? NamedTextColor.WHITE : factionColor(faction));
    }

    private NamedTextColor factionColor(final FactionType faction) {
        return switch (faction) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.BLUE;
            case NEUTRAL -> NamedTextColor.GRAY;
            case DARK -> NamedTextColor.DARK_GRAY;
        };
    }


    private static float clamp(final float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static String entry(final int index) {
        return "§" + "0123456789abcdef".charAt(index % 16) + "§r";
    }
}
