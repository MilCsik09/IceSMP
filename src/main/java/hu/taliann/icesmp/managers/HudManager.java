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

    /** Sidebar row budget: the 7 base rows + up to 7 party-frame rows (separator, header, 5 members). */
    private static final int LINES = 14;
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
    private final PartyManager partyManager;
    private final CaravanManager caravanManager;
    private final EscortManager escortManager;
    private final AbundanceManager abundanceManager;
    private final ServerChallengeManager serverChallengeManager;
    private final MeteorEventManager meteorEventManager;
    private final GatheringBuffManager gatheringBuffManager;

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
                      final MeteorEventManager meteorEventManager, final GatheringBuffManager gatheringBuffManager) {
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
            final Team team = board.registerNewTeam("hud_" + i);
            team.addEntry(entry(i));
            teams[i] = team;
        }
        // Row scores are set lazily in update(), so unused rows (e.g. the party
        // frames while not in a party) don't render as blank sidebar lines.
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
                final Scoreboard board = player.getScoreboard();
                final Objective objective = board.getObjective(OBJECTIVE);
                for (int i = 0; i < LINES; i++) {
                    final String entry = entry(i);
                    if (i < lines.size()) {
                        teams[i].prefix(lines.get(i));
                        if (objective != null && !objective.getScore(entry).isScoreSet()) {
                            objective.getScore(entry).setScore(LINES - i);
                        }
                    } else {
                        // Hide the unused row entirely (no empty sidebar line).
                        teams[i].prefix(Component.empty());
                        board.resetScores(entry);
                    }
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
            final String label = raid.inWarmup()
                    ? "⚔ RAID (felkészülés — /faction raid join): "
                            + raid.attacker().getDisplayName() + " ⚔ " + raid.defender().getDisplayName()
                    : "⚔ RAID: " + raid.attacker().getDisplayName() + " " + raidManager.getPoints(raid.attacker())
                            + " ⚔ " + raidManager.getPoints(raid.defender()) + " " + raid.defender().getDisplayName();
            raidBar.name(Component.text(label, NamedTextColor.RED));
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

        // WoW-style party frames: one row per member with a colour-coded health bar,
        // shown only while the viewer is in a party (otherwise these rows are hidden).
        final PartyManager.Party party = partyManager.getParty(player.getUniqueId());
        if (party != null && configManager.getBoolean("party.hud-enabled", true)) {
            lines.add(Component.text("  ", NamedTextColor.DARK_GRAY));
            lines.add(Component.text("— Csapat —", NamedTextColor.LIGHT_PURPLE));
            for (final UUID memberId : party.getMembers()) {
                lines.add(partyMemberLine(party, memberId));
            }
        }

        lines.add(Component.text("play.icesmp", NamedTextColor.DARK_GRAY));
        return lines;
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
