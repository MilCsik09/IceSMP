package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.integration.LuckPermsBridge;
import hu.taliann.icesmp.utils.TextAnimator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Natív tablist a TAB plugin kiváltására: header/footer animációkkal,
 * LP-prefixes + frakció-színes tab-nevek, fej fölötti nametag + LP-rang szerinti RENDEZÉS
 * (scoreboard-teamek), és ping-oszlop a tablistában. Minden funkció külön config-kapcsolós
 * ({@code config/tablist.yml}), a master {@code tablist.enabled} kikapcsolásával a szerver
 * visszaállhat külső tablist-pluginra.
 *
 * <p><b>Villogás-mentesség:</b> minden kimenet diff-elt — a header/footer és a tab-név csak
 * akkor megy ki újra, ha a renderelt szöveg tényleg változott; a team-prefix/color/score
 * írás előtt összehasonlítunk. Így az 500 ms-os tick ellenére a kliens csak valódi
 * változáskor kap csomagot (a TAB packet-szintű optimalizálásának API-oldali megfelelője).
 *
 * <p><b>Folia-modell:</b> a tick a global schedulerről indul, de minden játékos-érintés a
 * saját régió-szálán fut, két fázisban: (1) minden játékos a SAJÁT szálán publikálja a
 * {@link TabInfo} snapshotját (név, frakció, LP prefix/suffix/csoport, ping) egy konkurens
 * map-be; (2) minden NÉZŐ a saját szálán a snapshotokból szinkronizálja a saját boardján
 * a nametag-teameket és a ping-score-okat — más játékos entitását sosem érinti.
 *
 * <p>A nametag-teamek a néző SAJÁT scoreboardján élnek (amit a HudManager oldalsáv-logikája
 * is használ) — a team-nevek "nt" előtaggal kezdődnek, így sosem ütköznek a sidebar
 * "hud_N" sor-teamjeivel, a rendezést pedig a 3. karaktertől kezdődő rang-kulcs adja.
 */
public final class TablistManager {

    private static final String PING_OBJECTIVE = "icesmp_ping";
    private static final String TEAM_PREFIX = "nt";

    /** Egy játékos tablist-adatai, bármely régió-szálról biztonságosan olvasható snapshot. */
    private record TabInfo(UUID id, String name, String sortKey, String prefixLegacy, String suffixLegacy,
                           NamedTextColor nameColor, FactionType faction, int ping) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final TextAnimator animator;
    private final AfkManager afkManager;
    /** Relációs háború-színekhez; setterrel kötve a kézi DI-sorrend miatt. */
    private volatile RaidManager raidManager;
    private volatile VanishManager vanishManager;

    public void setVanishManager(final VanishManager vanishManager) {
        this.vanishManager = vanishManager;
    }

    public void setRaidManager(final RaidManager raidManager) {
        this.raidManager = raidManager;
    }

    /** A {event} token forrása; setterrel kötve — a HudManager később épül. */
    private volatile HudManager hudManager;

    public void setHudManager(final HudManager hudManager) {
        this.hudManager = hudManager;
    }

    private final Map<UUID, TabInfo> snapshots = new ConcurrentHashMap<>();
    /** Diff-cache-ek: az utoljára kiküldött renderelt szövegek (villogás- és csomag-spórolás). */
    private final Map<UUID, String> lastHeaderFooter = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastTabName = new ConcurrentHashMap<>();
    /** True after native output has been produced; used to cleanly hand off on config disable. */
    private volatile boolean nativeOutputActive;

    public TablistManager(final JavaPlugin plugin, final ConfigManager configManager,
                          final FactionManager factionManager, final TextAnimator animator,
                          final AfkManager afkManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.animator = animator;
        this.afkManager = afkManager;
    }

    public boolean isEnabled() {
        return configManager.getBoolean("tablist.enabled", true);
    }

    /**
     * Periodikus frissítés (global schedulerről, {@code tablist.refresh-ticks} ütemben):
     * minden online játékosra a saját régió-szálán fut le a snapshot-publikálás + a saját
     * nézetének (header/footer, tab-név, teamek, ping) szinkronja.
     */
    /** A takarító-söprések üteme (minden N. frissítésen fut a team/score-cleanup). */
    private int sweepCounter;

    public void tick() {
        if (!isEnabled()) {
            if (nativeOutputActive) {
                nativeOutputActive = false;
                releaseNativeOutput();
            }
            return;
        }
        nativeOutputActive = true;
        // A kilépő/átrendeződő bejegyzések söprése O(n²) nézőnként — nem futhat minden
        // fél másodpercben. Az érvényes team/név-készlet EGYSZER épül fel (az előző kör
        // snapshotjaiból — a söpréshez ez a laza konzisztencia elég), és minden néző ezt
        // kapja; a per-viewer belső ciklusok így O(n)-re esnek.
        final int sweepEvery = Math.max(1, configManager.getInt("tablist.sweep-every-refresh", 10));
        final boolean sweep = (sweepCounter = (sweepCounter + 1) % sweepEvery) == 0;
        final java.util.Set<String> validTeams = new java.util.HashSet<>();
        final java.util.Set<String> onlineNames = new java.util.HashSet<>();
        if (sweep) {
            for (final TabInfo info : snapshots.values()) {
                validTeams.add(teamName(info));
                onlineNames.add(info.name());
            }
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                publishSnapshot(player);
                updateHeaderFooter(player);
                updateTabName(player);
                syncViewerBoard(player, sweep, validTeams, onlineNames);
            }, null);
        }
    }

    /** A kilépő játékos map-állapotának takarítása. */
    public void cleanup(final UUID playerId) {
        snapshots.remove(playerId);
        lastHeaderFooter.remove(playerId);
        lastTabName.remove(playerId);
        // A többi néző boardján maradt team-et a következő tick szedi le (a snapshot már nincs meg).
    }

    /**
     * Removes every native tablist artifact from a live player. The caller must own the player's
     * region thread; config-disable uses the entity scheduler and plugin shutdown uses its existing
     * live-player cleanup phase.
     */
    public void cleanup(final Player player) {
        if (player == null) {
            return;
        }
        cleanup(player.getUniqueId());
        player.playerListName(null);
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());

        final Scoreboard board = player.getScoreboard();
        for (final Team team : List.copyOf(board.getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
        final Objective ping = board.getObjective(PING_OBJECTIVE);
        if (ping != null) {
            ping.unregister();
        }
    }

    /** One-shot cleanup when the native tablist is disabled at runtime. */
    private void releaseNativeOutput() {
        snapshots.clear();
        lastHeaderFooter.clear();
        lastTabName.clear();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> cleanup(player), null);
        }
    }

    // ==================== 1. fázis: snapshot a játékos saját szálán ====================

    private void publishSnapshot(final Player player) {
        final UUID id = player.getUniqueId();
        final FactionType faction = factionManager.getChosenFaction(id).orElse(null);
        final String group = LuckPermsBridge.primaryGroup(id);
        // AFK-jelzés a tab-név (és a fej fölötti nametag) végén — a diff-cache miatt a
        // váltás csak egyszer megy ki csomagként.
        final boolean afk = afkManager != null && afkManager.isAfk(id);
        final String afkSuffix = afk ? " &7⌚ ᴀꜰᴋ" : "";
        snapshots.put(id, new TabInfo(
                id, player.getName(),
                sortKey(group, player.getName(), afk),
                LuckPermsBridge.prefix(id),
                LuckPermsBridge.suffix(id) + afkSuffix,
                factionColor(faction),
                faction,
                Math.max(0, player.getPing())));
    }

    /**
     * Rendezési kulcs = rang-karakter + kisbetűs név: a tablist a team-nevek ABC-sorrendjében
     * rendez, így a {@code tablist.sorting.group-order} listában előrébb álló LP-csoport
     * kisebb karaktert (előrébb sorolást) kap; ismeretlen csoport a lista mögé kerül.
     */
    private String sortKey(final String group, final String name, final boolean afk) {
        final List<String> order = configManager.getConfiguration() == null ? List.of()
                : configManager.getConfiguration().getStringList("tablist.sorting.group-order");
        int index = order.indexOf(group);
        if (index < 0) {
            index = order.size();
        }
        return TablistOrdering.key(index, name, afk);
    }

    // ==================== 2. fázis: a néző saját nézete ====================

    private void updateHeaderFooter(final Player player) {
        if (!configManager.getBoolean("tablist.header-footer.enabled", true)) {
            return;
        }
        final String header = renderLines("tablist.header-footer.header", player);
        final String footer = renderLines("tablist.header-footer.footer", player);
        final String combined = header + " " + footer;
        if (combined.equals(lastHeaderFooter.get(player.getUniqueId()))) {
            return;
        }
        lastHeaderFooter.put(player.getUniqueId(), combined);
        player.sendPlayerListHeaderAndFooter(legacyComponent(header), legacyComponent(footer));
    }

    private String renderLines(final String path, final Player viewer) {
        final List<String> lines = configManager.getConfiguration() == null ? List.of()
                : configManager.getConfiguration().getStringList(path);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(applyTokens(lines.get(i), viewer));
        }
        return out.toString();
    }

    /** {anim:<név>} / {player} / {ping} / {online} / {max} tokenek feloldása. */
    private String applyTokens(final String line, final Player viewer) {
        String result = line;
        int guard = 0;
        while (result.contains("{anim:") && guard++ < 8) {
            final int start = result.indexOf("{anim:");
            final int end = result.indexOf('}', start);
            if (end < 0) {
                break;
            }
            final String name = result.substring(start + 6, end);
            result = result.substring(0, start) + animator.frame(name) + result.substring(end + 1);
        }
        if (result.contains("{")) {
            result = result.replace("{player}", viewer.getName())
                    .replace("{ping}", coloredPing(Math.max(0, viewer.getPing())))
                    .replace("{online}", String.valueOf(visibleOnlineCount()))
                    .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
            // Az aktív világesemények egy sorban (a HUD-snapshot §-kódolt sora).
            if (result.contains("{event}")) {
                final HudManager hud = hudManager;
                final HudManager.HudSnapshot snapshot = hud == null ? null : hud.snapshot(viewer.getUniqueId());
                result = result.replace("{event}", snapshot == null ? "" : snapshot.event());
            }
        }
        return result;
    }

    /** A {ping} token szín-kódolva (küszöbök: tablist.ping-colors.good/ok, ms-ben). */
    private String coloredPing(final int ping) {
        final int good = configManager.getInt("tablist.ping-colors.good", 80);
        final int ok = configManager.getInt("tablist.ping-colors.ok", 150);
        final String color = ping < good ? "&a" : ping < ok ? "&e" : "&c";
        return color + ping;
    }

    private void updateTabName(final Player player) {
        if (!configManager.getBoolean("tablist.tab-names.enabled", true)) {
            return;
        }
        final TabInfo info = snapshots.get(player.getUniqueId());
        if (info == null) {
            return;
        }
        final String rendered = info.prefixLegacy() + " " + info.nameColor() + " " + info.suffixLegacy();
        if (rendered.equals(lastTabName.get(player.getUniqueId()))) {
            return;
        }
        lastTabName.put(player.getUniqueId(), rendered);
        player.playerListName(legacyComponent(info.prefixLegacy())
                .append(Component.text(player.getName(), info.nameColor()))
                .append(legacyComponent(info.suffixLegacy())));
    }

    /**
     * A néző saját scoreboardján szinkronizálja a nametag-teameket (rendezés + fej fölötti
     * prefix/suffix + név-szín + ütközés) és a ping-oszlopot. Minden írás diff-elt.
     */
    private void syncViewerBoard(final Player viewer, final boolean sweep,
                                 final java.util.Set<String> validTeams, final java.util.Set<String> onlineNames) {
        final boolean nametags = configManager.getBoolean("tablist.nametags.enabled", true);
        final boolean pingColumn = configManager.getBoolean("tablist.playerlist-ping.enabled", true);
        if (!nametags && !pingColumn) {
            return;
        }
        final Scoreboard board = ownBoard(viewer);
        if (board == null) {
            return;
        }

        if (nametags) {
            // Eltűnt játékosok teamjeinek leszedése (kilépett vagy más a rendezési kulcsa) —
            // csak a ritkított söprő-körben, az előre felépített érvényes-készlet ellen (O(n)).
            // Az üres team is lejárt: az entry-t az új teamje magához vette.
            if (sweep) {
                for (final Team team : List.copyOf(board.getTeams())) {
                    if (!team.getName().startsWith(TEAM_PREFIX)) {
                        continue;
                    }
                    if (!validTeams.contains(team.getName()) || team.getEntries().isEmpty()) {
                        team.unregister();
                    }
                }
            }
            // Relációs szín — a néző frakciója + az aktív raid dönti el, hogy egy
            // célpont ellenségként (piros) jelenjen-e meg ENNEK a nézőnek. Per-viewer boardon
            // ez legálisan nézőnként más — a rendezési kulcsot nem érinti (nincs sorrend-ugrálás).
            final FactionType viewerFaction = factionManager.getChosenFaction(viewer.getUniqueId()).orElse(null);
            final RaidManager raids = raidManager;
            final RaidManager.ActiveRaid raid = raids == null ? null : raids.getActiveRaid();
            for (final TabInfo info : snapshots.values()) {
                if (hiddenForViewer(viewer, info)) {
                    final Team existing = board.getTeam(teamName(info));
                    if (existing != null) {
                        existing.removeEntry(info.name());
                        if (existing.getEntries().isEmpty()) {
                            existing.unregister();
                        }
                    }
                    board.resetScores(info.name());
                    continue;
                }
                applyTeam(board, info, displayColor(viewerFaction, info, raid));
            }
        }

        if (pingColumn) {
            Objective ping = board.getObjective(PING_OBJECTIVE);
            if (ping == null) {
                ping = board.registerNewObjective(PING_OBJECTIVE, Criteria.DUMMY, Component.text("ms"));
                ping.setRenderType(RenderType.INTEGER);
                ping.setDisplaySlot(DisplaySlot.PLAYER_LIST);
            }
            for (final TabInfo info : snapshots.values()) {
                if (hiddenForViewer(viewer, info)) {
                    board.resetScores(info.name());
                    continue;
                }
                final org.bukkit.scoreboard.Score score = ping.getScore(info.name());
                if (!score.isScoreSet() || score.getScore() != info.ping()) {
                    score.setScore(info.ping());
                }
            }
            // Kilépett játékos score-bejegyzésének takarítása (a sidebar §-entry-jei nem
            // játékosnevek) — csak a söprő-körben, készlet-ellenőrzéssel (O(n)).
            if (sweep) {
                for (final String entry : List.copyOf(board.getEntries())) {
                    if (!entry.startsWith("§") && !onlineNames.contains(entry)) {
                        board.resetScores(entry);
                    }
                }
            }
        }
    }

    private boolean hiddenForViewer(final Player viewer, final TabInfo info) {
        final VanishManager vanish = vanishManager;
        return vanish != null && !viewer.getUniqueId().equals(info.id())
                && vanish.isVanished(info.id())
                && !viewer.hasPermission(hu.taliann.icesmp.core.Permissions.MODERATION_VANISH_SEE);
    }

    private int visibleOnlineCount() {
        final VanishManager vanish = vanishManager;
        return vanish == null ? Bukkit.getOnlinePlayers().size() : vanish.visibleOnlineCount();
    }

    private String teamName(final TabInfo info) {
        return TEAM_PREFIX + info.sortKey();
    }

    private void applyTeam(final Scoreboard board, final TabInfo info, final NamedTextColor color) {
        final String name = teamName(info);
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
            team.setCanSeeFriendlyInvisibles(false);
        }
        if (!team.hasEntry(info.name())) {
            team.addEntry(info.name());
        }
        final Component prefix = legacyComponent(info.prefixLegacy());
        if (!prefix.equals(team.prefix())) {
            team.prefix(prefix);
        }
        final Component suffix = legacyComponent(info.suffixLegacy());
        if (!suffix.equals(team.suffix())) {
            team.suffix(suffix);
        }
        syncTeamColor(team, color);
    }

    /**
     * Paper/Folia 1.21.11 throws from {@link Team#color()} while a newly created team is still
     * uncoloured. The short-circuited {@link Team#hasColor()} check is therefore part of the
     * correctness contract, not merely an optimization.
     */
    static void syncTeamColor(final Team team, final NamedTextColor color) {
        if (!team.hasColor() || !color.equals(team.color())) {
            team.color(color);
        }
    }

    /**
     * A célpont megjelenítési színe ENNEK a nézőnek — aktív raidben a szemben
     * álló hadviselő fél tagjai PIROSAK ({@code tablist.nametags.war-colors}), egyébként
     * (és a raidben nem érintett nézőknek/célpontoknak) a frakció-szín marad.
     */
    private NamedTextColor displayColor(final FactionType viewerFaction, final TabInfo info,
                                        final RaidManager.ActiveRaid raid) {
        if (raid != null && viewerFaction != null && info.faction() != null
                && configManager.getBoolean("tablist.nametags.war-colors", true)) {
            final boolean viewerInWar = viewerFaction == raid.attacker() || viewerFaction == raid.defender();
            final boolean targetInWar = info.faction() == raid.attacker() || info.faction() == raid.defender();
            if (viewerInWar && targetInWar && viewerFaction != info.faction()) {
                final NamedTextColor war = NamedTextColor.NAMES.value(configManager
                        .getString("tablist.nametags.war-color", "red")
                        .trim().toLowerCase(java.util.Locale.ROOT));
                return war == null ? NamedTextColor.RED : war;
            }
        }
        return info.nameColor();
    }

    /**
     * A néző SAJÁT scoreboardja — ha még a közös main boardon áll (pl. a sidebar ki van
     * kapcsolva), kap egy sajátot, mert a nametag/ping réteg nézőnként egyedi. A hívás a
     * néző saját régió-szálán történik, így a setScoreboard biztonságos.
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

    // ==================== segéd ====================

    /** Legacy '&' + '&#RRGGBB' kódok Component-té — a közös TextAnimator-segédre delegál. */
    private static Component legacyComponent(final String text) {
        return TextAnimator.legacy(text);
    }

    private NamedTextColor factionColor(final FactionType faction) {
        return factionColor(configManager, faction);
    }

    /**
     * Frakciónkénti név-szín a tablistához és a fej fölötti nametaghez
     * ({@code tablist.faction-colors.*}, élő-config). A NEUTRAL default szándékosan NEM
     * szürke: a Menedék-polgár és a Kitaszított (dark_gray) különben összetéveszthető.
     */
    public static NamedTextColor factionColor(final ConfigManager configManager, final FactionType faction) {
        final String key;
        final NamedTextColor fallback;
        if (faction == null) {
            key = "guest";
            fallback = NamedTextColor.WHITE;
        } else {
            key = faction.name().toLowerCase(java.util.Locale.ROOT);
            fallback = switch (faction) {
                case RED -> NamedTextColor.RED;
                case BLUE -> NamedTextColor.BLUE;
                case NEUTRAL -> NamedTextColor.GREEN;
                case DARK -> NamedTextColor.DARK_GRAY;
            };
        }
        final String configured = configManager.getString(
                "tablist.faction-colors." + key, fallback.toString());
        final NamedTextColor resolved = NamedTextColor.NAMES.value(
                configured.trim().toLowerCase(java.util.Locale.ROOT));
        return resolved == null ? fallback : resolved;
    }
}
