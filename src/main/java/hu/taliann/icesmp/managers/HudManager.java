package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live player HUD (ROADMAP phase 1): a sidebar scoreboard (faction, currency,
 * class + level, active event), faction-coloured tab-list names, and shared
 * boss-bars for the raid / blood moon / world boss. Refreshed on a ~1s tick.
 *
 * <p>Folia: the tick runs on the global region scheduler, but every per-player
 * scoreboard/tab/boss-bar mutation hops onto that player's own region thread.
 */
public final class HudManager {

    private static final int LINES = 7;
    private static final String OBJECTIVE = "icesmp_hud";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final CurrencyManager currencyManager;
    private final JobManager jobManager;
    private final RaidManager raidManager;
    private final BloodMoonManager bloodMoonManager;
    private final WorldBossManager worldBossManager;
    private final ResourceManager resourceManager;

    private final BossBar raidBar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
    private final BossBar bloodMoonBar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private final BossBar worldBossBar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_6);

    private final ConcurrentHashMap<UUID, Team[]> playerTeams = new ConcurrentHashMap<>();

    /**
     * A thread-safe snapshot of a player's HUD data, refreshed on the player's region thread each
     * tick. Read by the PlaceholderAPI bridge from arbitrary threads (e.g. TAB's async refresh), so it
     * must NOT touch the live player/PDC off-thread — only this immutable record.
     */
    public record HudSnapshot(String faction, String factionId, String className, int classLevel,
                              String balance, boolean hasClass, int resource, int resourceMax,
                              int resourcePercent, String resourceName, String resourceBar) {
    }

    private final ConcurrentHashMap<UUID, HudSnapshot> snapshots = new ConcurrentHashMap<>();

    public HudManager(final JavaPlugin plugin, final ConfigManager configManager, final FactionManager factionManager,
                      final CurrencyManager currencyManager, final JobManager jobManager, final RaidManager raidManager,
                      final BloodMoonManager bloodMoonManager, final WorldBossManager worldBossManager,
                      final ResourceManager resourceManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.currencyManager = currencyManager;
        this.jobManager = jobManager;
        this.raidManager = raidManager;
        this.bloodMoonManager = bloodMoonManager;
        this.worldBossManager = worldBossManager;
        this.resourceManager = resourceManager;
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

    /** Builds the player's HUD (called on join, on that player's region thread). */
    public void init(final Player player) {
        if (!isEnabled()) {
            return;
        }
        if (sidebarEnabled()) {
            buildSidebar(player);
        }
        update(player);
    }

    /** Registers the IceSMP scoreboard sidebar for the player (only when sidebar-enabled). */
    private void buildSidebar(final Player player) {
        final ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        final Scoreboard board = manager.getNewScoreboard();
        final Objective objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                Component.text("✦ IceSMP ✦", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        final Team[] teams = new Team[LINES];
        for (int i = 0; i < LINES; i++) {
            final String entry = entry(i);
            final Team team = board.registerNewTeam("hud_" + i);
            team.addEntry(entry);
            objective.getScore(entry).setScore(LINES - i);
            teams[i] = team;
        }
        playerTeams.put(player.getUniqueId(), teams);
        player.setScoreboard(board);
    }

    /** Refreshes the sidebar text and/or tab-list name (on the player's region thread). */
    public void update(final Player player) {
        if (!isEnabled()) {
            return;
        }
        if (sidebarEnabled()) {
            Team[] teams = playerTeams.get(player.getUniqueId());
            if (teams == null) {
                buildSidebar(player);
                teams = playerTeams.get(player.getUniqueId());
            }
            if (teams != null) {
                final List<Component> lines = buildLines(player);
                for (int i = 0; i < LINES; i++) {
                    teams[i].prefix(i < lines.size() ? lines.get(i) : Component.empty());
                }
            }
        } else if (playerTeams.remove(player.getUniqueId()) != null) {
            // The sidebar was disabled at runtime (e.g. handing the scoreboard over to TAB):
            // restore the main scoreboard so the stale IceSMP board doesn't linger frozen.
            final ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
        if (tablistEnabled()) {
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
                resourceManager.resourceMax(),
                showResource ? resourceManager.resourcePercent(player) : 0,
                resourceManager.resourceName(player),
                showResource ? resourceManager.resourceBarPlain(player) : "");
    }

    public void cleanup(final Player player) {
        if (player == null) {
            return;
        }
        playerTeams.remove(player.getUniqueId());
        snapshots.remove(player.getUniqueId());
        player.hideBossBar(raidBar);
        player.hideBossBar(bloodMoonBar);
        player.hideBossBar(worldBossBar);
    }

    private void refreshBossBarState() {
        final RaidManager.ActiveRaid raid = raidManager.getActiveRaid();
        if (raid != null) {
            final long totalMs = Math.max(1L, configManager.getInt("factions.raid.duration-minutes", 15) * 60L * 1000L);
            final long remaining = Math.max(0L, raid.endsAtMillis() - System.currentTimeMillis());
            raidBar.name(Component.text("⚔ RAID: " + raid.attacker().getDisplayName() + " ⚔ " + raid.defender().getDisplayName(), NamedTextColor.RED));
            raidBar.progress(clamp((float) remaining / (float) totalMs));
        }
        bloodMoonBar.name(Component.text("🌕 VÉRHOLD — a szörnyek erősebbek", NamedTextColor.DARK_RED));
        worldBossBar.name(Component.text("☠ Világboss ébredt a vidéken", NamedTextColor.LIGHT_PURPLE));
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

    private List<Component> buildLines(final Player player) {
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        final double balance = currencyManager.getBalance(player, faction == null ? FactionType.NEUTRAL : faction);
        final JobType job = jobManager.getPrimaryJob(player);

        final List<Component> lines = new ArrayList<>();
        lines.add(label("Frakció", faction == null
                ? Component.text("nincs", NamedTextColor.GRAY)
                : Component.text(faction.getDisplayName(), factionColor(faction))));
        lines.add(label("Valuta", Component.text(currencyManager.formatBalance(balance), NamedTextColor.GOLD)));
        lines.add(label("Kaszt", job == null
                ? Component.text("nincs", NamedTextColor.GRAY)
                : job.getDisplayName().append(Component.text(" Lvl " + jobManager.getPrimaryLevel(player), NamedTextColor.WHITE))));
        // Per-class power resource bar (only with a class; never a separate boss bar).
        if (job != null) {
            final Component resourceLine = resourceManager.hudLine(player);
            if (resourceLine != null) {
                lines.add(resourceLine);
            }
        }
        lines.add(Component.text(" ", NamedTextColor.DARK_GRAY));
        lines.add(label("Esemény", eventLabel()));
        lines.add(Component.text("play.icesmp", NamedTextColor.DARK_GRAY));
        return lines;
    }

    private Component eventLabel() {
        if (raidManager.isRaidActive()) {
            return Component.text("Raid!", NamedTextColor.RED);
        }
        if (worldBossManager.isBossActive()) {
            return Component.text("Világboss", NamedTextColor.LIGHT_PURPLE);
        }
        if (bloodMoonManager.isActive()) {
            return Component.text("Vérhold", NamedTextColor.DARK_RED);
        }
        return Component.text("nyugalom", NamedTextColor.GRAY);
    }

    private Component tabName(final Player player) {
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        final Component prefix = faction == null
                ? Component.empty()
                : Component.text("[" + faction.getDisplayName() + "] ", factionColor(faction));
        return prefix.append(Component.text(player.getName(), NamedTextColor.WHITE));
    }

    private NamedTextColor factionColor(final FactionType faction) {
        return switch (faction) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.BLUE;
            case NEUTRAL -> NamedTextColor.GRAY;
            case DARK -> NamedTextColor.DARK_GRAY;
        };
    }

    private Component label(final String key, final Component value) {
        return Component.text(key + ": ", NamedTextColor.GRAY).append(value);
    }

    private static float clamp(final float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static String entry(final int index) {
        return "§" + "0123456789abcdef".charAt(index % 16) + "§r";
    }
}
