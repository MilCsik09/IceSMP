package hu.taliann.icesmp.client.projection;

import hu.taliann.icesmp.client.protocol.ClientProtocol;
import hu.taliann.icesmp.client.protocol.FactionStatePayload;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.FactionTreasuryManager;
import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.SeasonManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.managers.WarWindowManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FACTION_STATE-projekció a vanilla frakció-felületekkel (/menu frakció-fejléc,
 * /faction king|treasury|raid status|war, /events szezon-állás) azonos
 * manager-hívásokból. Minden forrás lock-mentes vagy synchronized olvasás, a néző
 * region-szálán hívható; a király-/jelölt-neveket a menü-úttal azonos módon oldja
 * (OfflinePlayer névcache, védőhálóval). Menedék-vendégnél a frakció-blokk üres, a
 * szezon-állás akkor is utazik (publikus broadcast-adat).
 */
public final class ClientFactionProjector {

    private ClientFactionProjector() {
    }

    public static FactionStatePayload project(final Player player,
                                              final FactionManager factions,
                                              final FactionTreasuryManager treasury,
                                              final CurrencyManager currency,
                                              final KingManager kings,
                                              final SeasonManager seasons,
                                              final RaidManager raids,
                                              final WarWindowManager warWindows,
                                              final TerritoryManager territories) {
        final FactionType faction = factions.getChosenFaction(player.getUniqueId()).orElse(null);

        final List<FactionStatePayload.SeasonRow> seasonRows = new ArrayList<>(FactionType.values().length);
        for (final FactionType candidate : FactionType.values()) {
            seasonRows.add(new FactionStatePayload.SeasonRow(candidate.name(),
                    candidate.getDisplayName(), seasons.getPoints(candidate)));
        }
        final int remainingDays = (int) Math.max(0L,
                (seasons.getSeasonEndMillis() - System.currentTimeMillis()) / 86_400_000L);

        boolean kingExcluded = false;
        String kingName = "";
        final List<FactionStatePayload.Tally> tally = new ArrayList<>();
        String treasuryBalance = "";
        final double taxRate = 0.0D;
        if (faction != null) {
            treasuryBalance = currency.formatBalance(treasury.getBalance(faction));
            kingExcluded = kings.isFactionExcluded(faction);
            if (!kingExcluded) {
                kingName = nameOf(kings.getKing(faction));
                for (final Map.Entry<UUID, Integer> entry : kings.getTally(faction).entrySet()) {
                    if (tally.size() >= ClientProtocol.MAX_LIST_ELEMENTS) {
                        break;
                    }
                    tally.add(new FactionStatePayload.Tally(nameOf(entry.getKey()), entry.getValue()));
                }
            }
        }

        boolean raidActive = false;
        boolean raidWarmup = false;
        String raidTerritoryName = "";
        String raidAttackerLabel = "";
        String raidDefenderLabel = "";
        int raidAttackerPoints = 0;
        int raidDefenderPoints = 0;
        int raidAttackerFighters = 0;
        int raidDefenderFighters = 0;
        int raidRemainingMinutes = 0;
        final RaidManager.ActiveRaid raid = raids.getActiveRaid();
        if (raid != null && raids.isRaidActive()) {
            raidActive = true;
            raidWarmup = raid.inWarmup();
            final Territory territory = territories.getById(raid.territoryId());
            raidTerritoryName = territory == null ? raid.territoryId() : territory.name();
            raidAttackerLabel = raid.attacker().getDisplayName();
            raidDefenderLabel = raid.defender().getDisplayName();
            raidAttackerPoints = raids.getPoints(raid.attacker());
            raidDefenderPoints = raids.getPoints(raid.defender());
            raidAttackerFighters = Math.toIntExact(raids.countParticipants(raid.attacker()));
            raidDefenderFighters = Math.toIntExact(raids.countParticipants(raid.defender()));
            raidRemainingMinutes = (int) Math.max(0L,
                    (raid.endsAtMillis() - System.currentTimeMillis()) / 60_000L);
        }

        return new FactionStatePayload(
                faction != null,
                faction == null ? "" : faction.name(),
                faction == null ? "" : faction.getDisplayName(),
                faction == null ? "" : faction.getFullName(),
                treasuryBalance, taxRate,
                kingExcluded, kingName, tally,
                seasons.getSeasonNumber(), seasons.getSeasonDay(), remainingDays,
                seasons.isGrandFinaleWindow(), seasonRows,
                raidActive, raidWarmup, raidTerritoryName,
                raidAttackerLabel, raidDefenderLabel,
                raidAttackerPoints, raidDefenderPoints,
                raidAttackerFighters, raidDefenderFighters,
                raids.maxPerSide(), raidRemainingMinutes,
                warWindows.isActive(),
                (int) Math.min(Integer.MAX_VALUE, Math.max(0L, warWindows.minutesUntilNextWindow())));
    }

    private static String nameOf(final UUID playerId) {
        if (playerId == null) {
            return "";
        }
        try {
            final String name = Bukkit.getOfflinePlayer(playerId).getName();
            return name == null ? "" : name;
        } catch (final Exception ignored) {
            return "";
        }
    }
}
