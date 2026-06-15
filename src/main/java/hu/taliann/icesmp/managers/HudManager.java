package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

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

    private static final int LINES = 6;
    private static final String OBJECTIVE = "icesmp_hud";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final CurrencyManager currencyManager;
    private final JobManager jobManager;
    private final RaidManager raidManager;
    private final BloodMoonManager bloodMoonManager;
    private final WorldBossManager worldBossManager;

    private final BossBar raidBar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
    private final BossBar bloodMoonBar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private final BossBar worldBossBar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_6);

    private final ConcurrentHashMap<UUID, Team[]> playerTeams = new ConcurrentHashMap<>();

    public HudManager(final JavaPlugin plugin, final ConfigManager configManager, final FactionManager factionManager,
                      final CurrencyManager currencyManager, final JobManager jobManager, final RaidManager raidManager,
                      final BloodMoonManager bloodMoonManager, final WorldBossManager worldBossManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.currencyManager = currencyManager;
        this.jobManager = jobManager;
        this.raidManager = raidManager;
        this.bloodMoonManager = bloodMoonManager;
        this.worldBossManager = worldBossManager;
    }

    public boolean isEnabled() {
        return configManager.getBoolean("hud.enabled", true);
    }

    /** Builds the player's sidebar (called on join, on that player's region thread). */
    public void init(final Player player) {
        if (!isEnabled()) {
            return;
        }

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
        update(player);
    }

    /** Refreshes the sidebar text and tab-list name (on the player's region thread). */
    public void update(final Player player) {
        final Team[] teams = playerTeams.get(player.getUniqueId());
        if (teams == null) {
            init(player);
            return;
        }

        final List<Component> lines = buildLines(player);
        for (int i = 0; i < LINES; i++) {
            teams[i].prefix(i < lines.size() ? lines.get(i) : Component.empty());
        }
        player.playerListName(tabName(player));
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
            }, null);
        }
    }

    public void cleanup(final Player player) {
        if (player == null) {
            return;
        }
        playerTeams.remove(player.getUniqueId());
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

        return List.of(
                label("Frakció", faction == null
                        ? Component.text("nincs", NamedTextColor.GRAY)
                        : Component.text(faction.getDisplayName(), factionColor(faction))),
                label("Valuta", Component.text(currencyManager.formatBalance(balance), NamedTextColor.GOLD)),
                label("Kaszt", job == null
                        ? Component.text("nincs", NamedTextColor.GRAY)
                        : job.getDisplayName().append(Component.text(" Lvl " + jobManager.getPrimaryLevel(player), NamedTextColor.WHITE))),
                Component.text(" ", NamedTextColor.DARK_GRAY),
                label("Esemény", eventLabel()),
                Component.text("play.icesmp", NamedTextColor.DARK_GRAY)
        );
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
