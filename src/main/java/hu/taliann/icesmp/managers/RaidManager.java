package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raid system (ideas.md "Raid eventek"): a faction king declares a raid
 * against another faction for an entry fee paid from the treasury. While the
 * raid runs, kills between the two warring factions do not count as sins
 * (sanctioned PvP), and every kill scores for the killer's side. When the
 * timer ends, the side with more kills wins the configured percentage of the
 * loser's treasury as war spoils. Runtime-only state; the end timer runs on
 * the global region scheduler (Paper + Folia).
 */
public final class RaidManager {

    /** Snapshot of the running raid. */
    public record ActiveRaid(FactionType attacker, FactionType defender, long endsAtMillis) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionTreasuryManager treasuryManager;
    private final SeasonManager seasonManager;
    private final MessageManager messageManager;
    private final Map<FactionType, Integer> kills = new ConcurrentHashMap<>();

    private volatile ActiveRaid activeRaid;
    private ScheduledTask endTask;

    public RaidManager(final JavaPlugin plugin, final ConfigManager configManager,
                       final FactionTreasuryManager treasuryManager, final SeasonManager seasonManager,
                       final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.treasuryManager = treasuryManager;
        this.seasonManager = seasonManager;
        this.messageManager = messageManager;
    }

    public ActiveRaid getActiveRaid() {
        return activeRaid;
    }

    public boolean isRaidActive() {
        return activeRaid != null;
    }

    /**
     * Checks whether two factions are currently at war with each other.
     * Used by the SinListener: sanctioned raid kills carry no sin.
     *
     * @param first one faction
     * @param second the other faction
     * @return true if the running raid is between exactly these two factions
     */
    public boolean isAtWar(final FactionType first, final FactionType second) {
        final ActiveRaid raid = activeRaid;
        if (raid == null || first == null || second == null || first == second) {
            return false;
        }

        return (raid.attacker() == first && raid.defender() == second)
                || (raid.attacker() == second && raid.defender() == first);
    }

    /**
     * Declares a raid. Validation errors are returned as message keys so the
     * command layer can localize them.
     *
     * @param attacker the declaring faction (the king's faction)
     * @param defender the raid target
     * @return null on success, otherwise the error message key
     */
    public synchronized String startRaid(final FactionType attacker, final FactionType defender) {
        if (isRaidActive()) {
            return "faction-raid-already-active";
        }

        if (attacker == null || defender == null || attacker == defender) {
            return "faction-raid-invalid-target";
        }

        final List<String> protectedFactions = configManager.getStringList("factions.raid.protected");
        final List<String> effectiveProtected = protectedFactions.isEmpty() ? List.of("NEUTRAL") : protectedFactions;
        if (effectiveProtected.stream().anyMatch(name -> name.equalsIgnoreCase(defender.name()))) {
            return "faction-raid-protected-target";
        }

        final double entryCost = Math.max(0.0D, configManager.getDouble("factions.raid.entry-cost", 200.0D));
        if (entryCost > 0.0D && !treasuryManager.withdraw(attacker, entryCost)) {
            return "faction-raid-no-funds";
        }

        final long durationMinutes = Math.max(1L, configManager.getLong("factions.raid.duration-minutes", 15L));
        kills.clear();
        activeRaid = new ActiveRaid(attacker, defender, System.currentTimeMillis() + (durationMinutes * 60_000L));

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "faction-raid-started",
                "<red>⚔ RAID! <white>{attacker}</white> hadat üzent <white>{defender}</white> ellen — <white>{minutes}</white> percig a két frakció közti PvP nem számít bűnnek!</red>",
                Map.of(
                        "attacker", attacker.getDisplayName(),
                        "defender", defender.getDisplayName(),
                        "minutes", String.valueOf(durationMinutes)
                )
        ));

        endTask = plugin.getServer().getGlobalRegionScheduler().runDelayed(
                plugin,
                task -> endRaid(),
                durationMinutes * 60L * 20L
        );
        return null;
    }

    /**
     * Records a raid kill for the killer's side.
     *
     * @param killerFaction the faction scoring the kill
     */
    public void recordKill(final FactionType killerFaction) {
        final ActiveRaid raid = activeRaid;
        if (raid == null || killerFaction == null) {
            return;
        }

        if (killerFaction == raid.attacker() || killerFaction == raid.defender()) {
            kills.merge(killerFaction, 1, Integer::sum);
        }
    }

    /**
     * Ends the raid: the side with more kills loots the configured percentage
     * of the loser's treasury; a tie pays out nothing.
     */
    public synchronized void endRaid() {
        final ActiveRaid raid = activeRaid;
        if (raid == null) {
            return;
        }

        activeRaid = null;
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }

        final int attackerKills = kills.getOrDefault(raid.attacker(), 0);
        final int defenderKills = kills.getOrDefault(raid.defender(), 0);

        if (attackerKills == defenderKills) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "faction-raid-tie",
                    "<gold>⚔ A raid döntetlennel zárult ({attacker} {attackerKills} - {defenderKills} {defender}) — nincs hadizsákmány.</gold>",
                    Map.of(
                            "attacker", raid.attacker().getDisplayName(),
                            "defender", raid.defender().getDisplayName(),
                            "attackerKills", String.valueOf(attackerKills),
                            "defenderKills", String.valueOf(defenderKills)
                    )
            ));
            return;
        }

        final FactionType winner = attackerKills > defenderKills ? raid.attacker() : raid.defender();
        final FactionType loser = winner == raid.attacker() ? raid.defender() : raid.attacker();
        final double spoilsPercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("factions.raid.spoils-percent", 20.0D)));
        final double spoils = Math.floor(treasuryManager.getBalance(loser) * spoilsPercent) / 100.0D;
        if (spoils > 0.0D && treasuryManager.withdraw(loser, spoils)) {
            treasuryManager.deposit(winner, spoils);
        }

        // Raid victory feeds the seasonal league standings.
        seasonManager.addPoints(winner, Math.max(0, configManager.getInt("factions.raid.season-points", 5)));

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "faction-raid-ended",
                "<gold>⚔ A raid véget ért! Győztes: <white>{winner}</white> ({attackerKills} - {defenderKills}). Hadizsákmány: <white>{spoils}</white> a(z) {loser} kasszájából.</gold>",
                Map.of(
                        "winner", winner.getDisplayName(),
                        "loser", loser.getDisplayName(),
                        "attackerKills", String.valueOf(attackerKills),
                        "defenderKills", String.valueOf(defenderKills),
                        "spoils", String.valueOf(spoils)
                )
        ));
    }

    /** Cancels the pending end-timer on plugin disable. */
    public void shutdown() {
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        activeRaid = null;
    }
}
