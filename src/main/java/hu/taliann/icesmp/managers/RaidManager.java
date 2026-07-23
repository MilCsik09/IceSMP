package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raid system:
 * a faction king declares a raid against another faction for an entry fee paid
 * from the treasury, optionally bound to one of the defender's territory claims
 * (default: their capital).
 *
 * <p>The raid opens with a WARM-UP phase where fighters of both factions sign
 * up (<code>/faction raid join</code>, capped per side). Once COMBAT starts,
 * kills BETWEEN REGISTERED FIGHTERS are sanctioned (no sin) and score
 * kill-points — on a territory-bound raid only when the victim falls inside
 * the raid zone. The territory's center is a capture point: every capture
 * interval each fighter standing within the capture radius earns hold-points
 * for their side. When the timer ends, the side with more points wins the
 * configured percentage of the loser's treasury; if the ATTACKER wins a
 * territory-bound raid, the claim itself changes hands (losing its capital
 * status). Runtime-only state; all timers run on the global region scheduler
 * (Paper + Folia), hopping to each player's own thread for entity access.</p>
 */
public final class RaidManager {

    /** Snapshot of the running raid ({@code territoryId} null = unbound raid). */
    public record ActiveRaid(FactionType attacker, FactionType defender, String territoryId,
                             long combatStartsAtMillis, long endsAtMillis) {

        public boolean inWarmup() {
            return System.currentTimeMillis() < combatStartsAtMillis;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final FactionTreasuryManager treasuryManager;
    private final SeasonManager seasonManager;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;
    private final Map<FactionType, Integer> points = new ConcurrentHashMap<>();
    private final Map<UUID, FactionType> participants = new ConcurrentHashMap<>();

    private volatile ActiveRaid activeRaid;
    /** Az utolsó raid lezárásának bélyege — a lánc-raid cooldown alapja (memóriában él). */
    private volatile long lastRaidEndedAt;
    private ScheduledTask combatStartTask;
    private ScheduledTask captureTask;
    private ScheduledTask endTask;
    private java.util.function.Consumer<Player> winHook;

    /** Sets the callback fired for every ONLINE winning-side fighter when a raid is won (quest bridge). */
    public void setWinHook(final java.util.function.Consumer<Player> hook) {
        this.winHook = hook;
    }

    public RaidManager(final JavaPlugin plugin, final ConfigManager configManager,
                       final FactionManager factionManager, final FactionTreasuryManager treasuryManager,
                       final SeasonManager seasonManager, final TerritoryManager territoryManager,
                       final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.treasuryManager = treasuryManager;
        this.seasonManager = seasonManager;
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
    }

    public ActiveRaid getActiveRaid() {
        return activeRaid;
    }

    public boolean isRaidActive() {
        return activeRaid != null;
    }

    /**
     * Checks whether two factions are currently at war with each other. Used
     * for faction-level consequences of the war state (reputation pricing);
     * the sin exemptions are participant-based — see
     * {@link #isSanctionedKill(UUID, UUID)}.
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

    public boolean isParticipant(final UUID playerId) {
        return playerId != null && participants.containsKey(playerId);
    }

    /**
     * Whether a kill is sanctioned war PvP: the raid is in the COMBAT phase and
     * both players are registered fighters on opposite sides. Sanctioned kills
     * carry no sin; anyone NOT signed up murders as in peacetime.
     */
    public boolean isSanctionedKill(final UUID killerId, final UUID victimId) {
        final ActiveRaid raid = activeRaid;
        if (raid == null || raid.inWarmup() || killerId == null || victimId == null) {
            return false;
        }

        final FactionType killerSide = participants.get(killerId);
        final FactionType victimSide = participants.get(victimId);
        return killerSide != null && victimSide != null && killerSide != victimSide;
    }

    /**
     * Whether container looting is sanctioned war plunder: the looter is a
     * registered fighter in the COMBAT phase and the territory belongs to the
     * enemy side. Used by the TheftListener.
     */
    public boolean isSanctionedLooting(final UUID looterId, final FactionType territoryOwner) {
        final ActiveRaid raid = activeRaid;
        if (raid == null || raid.inWarmup() || looterId == null || territoryOwner == null) {
            return false;
        }

        final FactionType looterSide = participants.get(looterId);
        if (looterSide == null || looterSide == territoryOwner) {
            return false;
        }

        return territoryOwner == raid.attacker() || territoryOwner == raid.defender();
    }

    /**
     * Declares a raid. Validation errors are returned as message keys so the
     * command layer can localize them.
     *
     * @param attacker the declaring faction (the king's faction)
     * @param defender the raid target
     * @param requestedTerritoryId the defender claim to bind the raid to, or
     *        null for the default (the defender's capital, then any claim,
     *        then an unbound kills-only raid)
     * @return null on success, otherwise the error message key
     */
    public synchronized String startRaid(final FactionType attacker, final FactionType defender,
                                         final String requestedTerritoryId) {
        if (isRaidActive()) {
            return "faction-raid-already-active";
        }

        // Lánc-raid fék: két hirdetés közt kötelező szünet (élő kulcs, 0 = kikapcsolva).
        final long cooldownMillis = Math.max(0L,
                configManager.getLong("factions.raid.cooldown-minutes", 60L)) * 60_000L;
        if (cooldownMillis > 0L && System.currentTimeMillis() - lastRaidEndedAt < cooldownMillis) {
            return "faction-raid-cooldown";
        }

        if (attacker == null || defender == null || attacker == defender) {
            return "faction-raid-invalid-target";
        }

        final List<String> protectedFactions = configManager.getStringList("factions.raid.protected");
        final List<String> effectiveProtected = protectedFactions.isEmpty() ? List.of("NEUTRAL") : protectedFactions;
        if (effectiveProtected.stream().anyMatch(name -> name.equalsIgnoreCase(defender.name()))) {
            return "faction-raid-protected-target";
        }

        // Territory binding: an explicit id must exist and belong to the defender;
        // without one, prefer the defender's capital, then any of their claims.
        Territory territory = null;
        if (requestedTerritoryId != null && !requestedTerritoryId.isBlank()) {
            territory = territoryManager.getById(requestedTerritoryId);
            if (territory == null || territory.faction() != defender) {
                return "faction-raid-unknown-territory";
            }
        } else {
            territory = territoryManager.getCapital(defender);
            if (territory == null) {
                territory = territoryManager.all().stream()
                        .filter(claim -> claim.faction() == defender)
                        .findFirst()
                        .orElse(null);
            }
        }

        final double entryCost = Math.max(0.0D, configManager.getDouble("factions.raid.entry-cost", 200.0D));
        if (entryCost > 0.0D && !treasuryManager.withdraw(attacker, entryCost)) {
            return "faction-raid-no-funds";
        }

        final long warmupMinutes = Math.max(0L, configManager.getLong("factions.raid.warmup-minutes", 2L));
        final long durationMinutes = Math.max(1L, configManager.getLong("factions.raid.duration-minutes", 15L));
        final long now = System.currentTimeMillis();
        points.clear();
        participants.clear();
        activeRaid = new ActiveRaid(attacker, defender,
                territory == null ? null : territory.id(),
                now + (warmupMinutes * 60_000L),
                now + ((warmupMinutes + durationMinutes) * 60_000L));

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "faction-raid-started",
                "<red>⚔ RAID! <white>{attacker}</white> hadat üzent <white>{defender}</white> ellen{zone}! "
                        + "<white>{warmup}</white> perc felkészülés — harcosok, jelentkezzetek: "
                        + "<white>/faction raid join</white> (max <white>{cap}</white>/oldal)</red>",
                Map.of(
                        "attacker", attacker.getDisplayName(),
                        "defender", defender.getDisplayName(),
                        "zone", territory == null ? "" : " a(z) " + territory.name() + " területért",
                        "warmup", String.valueOf(warmupMinutes),
                        "cap", String.valueOf(maxPerSide())
                )
        ));

        if (warmupMinutes > 0L) {
            combatStartTask = plugin.getServer().getGlobalRegionScheduler().runDelayed(
                    plugin, task -> announceCombatStart(), warmupMinutes * 60L * 20L);
        } else {
            announceCombatStart();
        }
        endTask = plugin.getServer().getGlobalRegionScheduler().runDelayed(
                plugin,
                task -> endRaid(),
                (warmupMinutes + durationMinutes) * 60L * 20L
        );
        return null;
    }

    /** Backwards-compatible entry point: raid bound to the defender's default claim. */
    public String startRaid(final FactionType attacker, final FactionType defender) {
        return startRaid(attacker, defender, null);
    }

    /**
     * Signs a player up as a raid fighter for their faction's side.
     *
     * @param player the volunteering player
     * @return null on success, otherwise an error message key
     */
    public synchronized String joinRaid(final Player player) {
        final ActiveRaid raid = activeRaid;
        if (raid == null) {
            return "faction-raid-none";
        }

        final FactionType side = factionManager.getFaction(player.getUniqueId());
        if (side != raid.attacker() && side != raid.defender()) {
            return "faction-raid-not-party";
        }

        if (participants.containsKey(player.getUniqueId())) {
            return "faction-raid-already-joined";
        }

        if (countParticipants(side) >= maxPerSide()) {
            return "faction-raid-side-full";
        }

        participants.put(player.getUniqueId(), side);
        return null;
    }

    public long countParticipants(final FactionType side) {
        return participants.values().stream().filter(faction -> faction == side).count();
    }

    public int getPoints(final FactionType side) {
        return points.getOrDefault(side, 0);
    }

    public int maxPerSide() {
        return Math.max(1, configManager.getInt("factions.raid.max-participants-per-side", 10));
    }

    private void announceCombatStart() {
        final ActiveRaid raid = activeRaid;
        if (raid == null) {
            return;
        }

        final Territory territory = raid.territoryId() == null ? null : territoryManager.getById(raid.territoryId());
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "faction-raid-combat-start",
                "<red>⚔ A raid harci szakasza megkezdődött ({attackerCount} vs {defenderCount})!{zone}</red>",
                Map.of(
                        "attackerCount", String.valueOf(countParticipants(raid.attacker())),
                        "defenderCount", String.valueOf(countParticipants(raid.defender())),
                        "zone", territory == null ? ""
                                : " A(z) " + territory.name() + " középpontját tartva pont jár!"
                )
        ));

        if (territory != null) {
            final long intervalTicks = Math.max(1L,
                    configManager.getLong("factions.raid.capture-interval-seconds", 5L)) * 20L;
            captureTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                    plugin, task -> tickCapture(), intervalTicks, intervalTicks);
        }
    }

    /**
     * Point-hold objective tick: every registered fighter standing within the
     * capture radius of the raid territory's center earns hold-points for
     * their side. Runs on the global scheduler; each fighter's location is
     * read on that player's own region thread (Folia rule).
     */
    private void tickCapture() {
        final ActiveRaid raid = activeRaid;
        if (raid == null || raid.inWarmup() || raid.territoryId() == null) {
            return;
        }

        final Territory territory = territoryManager.getById(raid.territoryId());
        if (territory == null) {
            return;
        }

        final double captureRadius = Math.max(1.0D, configManager.getDouble("factions.raid.capture-radius", 8.0D));
        final double radiusSquared = captureRadius * captureRadius;
        final int holdPoints = Math.max(1, configManager.getInt("factions.raid.capture-points", 1));

        for (final Map.Entry<UUID, FactionType> entry : participants.entrySet()) {
            final Player fighter = Bukkit.getPlayer(entry.getKey());
            if (fighter == null) {
                continue;
            }

            final FactionType side = entry.getValue();
            fighter.getScheduler().run(plugin, task -> {
                if (activeRaid == null || !fighter.isOnline() || fighter.isDead()) {
                    return;
                }

                final Location location = fighter.getLocation();
                if (!territory.world().equals(location.getWorld().getName())) {
                    return;
                }

                final double deltaX = location.getX() - territory.x();
                final double deltaZ = location.getZ() - territory.z();
                if ((deltaX * deltaX) + (deltaZ * deltaZ) > radiusSquared) {
                    return;
                }

                points.merge(side, holdPoints, Integer::sum);
                fighter.sendActionBar(messageManager.getMessage(
                        "faction-raid-holding-point",
                        "<gold>⚑ Pontot tartasz a(z) {territory} középpontján! (+{points})</gold>",
                        Map.of("territory", territory.name(), "points", String.valueOf(holdPoints))
                ));
            }, null);
        }
    }

    /**
     * Records a sanctioned raid kill for the killer's side. On a territory-bound
     * raid only deaths inside the raid zone score points.
     *
     * @param killerFaction the faction scoring the kill
     * @param deathLocation where the victim fell
     * @return true if the kill scored points (inside the zone / unbound raid)
     */
    public synchronized boolean recordKill(final FactionType killerFaction, final Location deathLocation) {
        // synchronized with endRaid(): a kill is either fully counted before the raid closes
        // or ignored after, never merged into 'points' after the payout snapshot was taken.
        final ActiveRaid raid = activeRaid;
        if (raid == null || killerFaction == null) {
            return false;
        }

        if (killerFaction != raid.attacker() && killerFaction != raid.defender()) {
            return false;
        }

        if (raid.territoryId() != null) {
            final Territory territory = territoryManager.getById(raid.territoryId());
            if (territory != null && (deathLocation == null || deathLocation.getWorld() == null
                    || !territory.contains(deathLocation.getWorld().getName(), deathLocation.getX(), deathLocation.getZ()))) {
                return false;
            }
        }

        final int killPoints = Math.max(1, configManager.getInt("factions.raid.kill-points", 5));
        points.merge(killerFaction, killPoints, Integer::sum);
        return true;
    }

    /**
     * Ends the raid: the side with more points loots the configured percentage
     * of the loser's treasury; a tie pays out nothing. If the attacker wins a
     * territory-bound raid, the claim transfers to the attacker (losing any
     * capital status).
     */
    public synchronized void endRaid() {
        final ActiveRaid raid = activeRaid;
        if (raid == null) {
            return;
        }

        lastRaidEndedAt = System.currentTimeMillis();
        activeRaid = null;
        cancelTasks();

        final int attackerPoints = points.getOrDefault(raid.attacker(), 0);
        final int defenderPoints = points.getOrDefault(raid.defender(), 0);
        final Map<UUID, FactionType> fighters = Map.copyOf(participants);
        participants.clear();

        if (attackerPoints == defenderPoints) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "faction-raid-tie",
                    "<gold>⚔ A raid döntetlennel zárult ({attacker} {attackerPoints} - {defenderPoints} {defender}) — nincs hadizsákmány.</gold>",
                    Map.of(
                            "attacker", raid.attacker().getDisplayName(),
                            "defender", raid.defender().getDisplayName(),
                            "attackerPoints", String.valueOf(attackerPoints),
                            "defenderPoints", String.valueOf(defenderPoints)
                    )
            ));
            return;
        }

        final FactionType winner = attackerPoints > defenderPoints ? raid.attacker() : raid.defender();
        final FactionType loser = winner == raid.attacker() ? raid.defender() : raid.attacker();
        final double spoilsPercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("factions.raid.spoils-percent", 20.0D)));
        final double spoils = Math.floor(treasuryManager.getBalance(loser) * spoilsPercent) / 100.0D;
        if (spoils > 0.0D && treasuryManager.withdraw(loser, spoils)) {
            treasuryManager.deposit(winner, spoils);
        }

        // Raid victory feeds the seasonal league standings.
        seasonManager.addPoints(winner, Math.max(0, configManager.getInt("factions.raid.season-points", 5)), "raid");

        // Victor's spoils buff: a temporary boon for the winning faction's online members.
        applyWinnerBuff(winner);

        // Quest bridge (WIN_RAID objectives): only registered fighters on the winning
        // side count, each on their own region thread.
        if (winHook != null) {
            for (final Map.Entry<UUID, FactionType> fighter : fighters.entrySet()) {
                if (fighter.getValue() != winner) {
                    continue;
                }
                final Player online = Bukkit.getPlayer(fighter.getKey());
                if (online != null) {
                    online.getScheduler().run(plugin, task -> winHook.accept(online), null);
                }
            }
        }

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "faction-raid-ended",
                "<gold>⚔ A raid véget ért! Győztes: <white>{winner}</white> ({attackerPoints} - {defenderPoints} pont). Hadizsákmány: <white>{spoils}</white> a(z) {loser} kasszájából.</gold>",
                Map.of(
                        "winner", winner.getDisplayName(),
                        "loser", loser.getDisplayName(),
                        "attackerPoints", String.valueOf(attackerPoints),
                        "defenderPoints", String.valueOf(defenderPoints),
                        "spoils", String.valueOf(spoils)
                )
        ));

        // Territory takeover: a successful attack captures the claim itself.
        if (winner == raid.attacker() && raid.territoryId() != null
                && configManager.getBoolean("factions.raid.territory-takeover", true)) {
            transferTerritory(raid.territoryId(), winner);
        }
    }

    private void transferTerritory(final String territoryId, final FactionType conqueror) {
        final Territory territory = territoryManager.getById(territoryId);
        if (territory == null || territory.faction() == conqueror) {
            return;
        }

        final org.bukkit.World world = Bukkit.getWorld(territory.world());
        if (world == null) {
            return;
        }

        // Captured zones never carry capital status into the conqueror's hands —
        // a faction's capital is its own seat, not a war trophy. A polygon boundary
        // (e.g. a besieged city wall) is preserved so the exact shape transfers too.
        if (territory.isPolygon()) {
            territoryManager.definePolygon(territory.id(), conqueror, territory.name(),
                    hu.taliann.icesmp.data.TerritoryType.FACTION, territory.world(), territory.polygon());
        } else {
            territoryManager.define(territory.id(), conqueror, territory.name(),
                    hu.taliann.icesmp.data.TerritoryType.FACTION,
                    new Location(world, territory.x(), 0.0D, territory.z()), territory.radius());
        }
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "faction-raid-territory-captured",
                "<dark_red>⚑ A(z) <white>{territory}</white> terület elesett — mostantól a(z) <white>{winner}</white> frakcióé!</dark_red>",
                Map.of("territory", territory.name(), "winner", conqueror.getDisplayName())
        ));
    }

    private void applyWinnerBuff(final FactionType winner) {
        final int buffMinutes = Math.max(0, configManager.getInt("factions.raid.winner-buff-minutes", 30));
        if (buffMinutes <= 0) {
            return;
        }

        final int durationTicks = buffMinutes * 60 * 20;
        final int strengthLevel = Math.max(0, configManager.getInt("factions.raid.winner-buff-strength", 0));
        final int regenLevel = Math.max(0, configManager.getInt("factions.raid.winner-buff-regen", 0));
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (factionManager.getFaction(online.getUniqueId()) != winner) {
                continue;
            }

            // Folia: endRaid() runs on the global region scheduler, so each player's
            // potion effects must be applied on that player's own region thread.
            online.getScheduler().run(plugin, task -> {
                online.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, strengthLevel, false, true, true));
                online.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, regenLevel, false, true, true));
                online.sendMessage(messageManager.getMessage(
                        "faction-raid-winner-buff",
                        "<gold>⚔ A győzelem mámora átjár — harci áldás szállt rád {minutes} percre!</gold>",
                        Map.of("minutes", String.valueOf(buffMinutes))
                ));
            }, null);
        }
    }

    private void cancelTasks() {
        if (combatStartTask != null) {
            combatStartTask.cancel();
            combatStartTask = null;
        }
        if (captureTask != null) {
            captureTask.cancel();
            captureTask = null;
        }
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
    }

    /** Cancels the pending timers on plugin disable. */
    public void shutdown() {
        final ActiveRaid raid = activeRaid;
        if (raid != null) {
            // Restart alatt a menet nem folytatható — legalább ne némán vesszen el.
            Bukkit.getServer().broadcast(messageManager.getMessage("raid-cancelled-restart",
                    "<yellow>⚔ A folyamatban lévő ostrom a szerver újraindulása miatt eredmény nélkül zárult — a krónikák nem jegyzik.</yellow>"));
        }
        cancelTasks();
        activeRaid = null;
        participants.clear();
    }
}
