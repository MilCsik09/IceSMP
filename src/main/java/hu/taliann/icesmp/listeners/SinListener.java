package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Records sins for player murder and betrayal: the killer gains sin points,
 * and the SinManager exiles repeat offenders to the Dark faction once
 * the configured threshold is reached. Killing a member of your OWN faction
 * is betrayal and weighs more than plain murder (Neutrals are a loose
 * association, so a Neutral-on-Neutral kill stays plain murder). Sanctioned
 * raid kills (between the two warring factions) carry no sin and score for
 * the killer's side instead.
 */
public final class SinListener implements Listener {

    private final JavaPlugin plugin;
    private final SinManager sinManager;
    private final RaidManager raidManager;
    private final FactionManager factionManager;
    private final StatsManager statsManager;
    private final CurrencyManager currencyManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public SinListener(final JavaPlugin plugin, final SinManager sinManager, final RaidManager raidManager,
                       final FactionManager factionManager, final StatsManager statsManager,
                       final CurrencyManager currencyManager, final ConfigManager configManager,
                       final MessageManager messageManager) {
        this.plugin = plugin;
        this.sinManager = sinManager;
        this.raidManager = raidManager;
        this.factionManager = factionManager;
        this.statsManager = statsManager;
        this.currencyManager = currencyManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player victim = event.getEntity();
        final Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        // Raid rule: only kills between REGISTERED fighters on
        // opposite sides are sanctioned (no sin). On a territory-bound raid points are only
        // scored when the victim falls inside the raid zone — the death location is read
        // here on the victim's own region thread. Killer-side mutations (PDC sin/stats,
        // messages) hop onto the killer's own region thread, because PlayerDeathEvent runs
        // on the VICTIM's region and the killer may be elsewhere.
        final FactionType killerFaction = factionManager.getFaction(killer.getUniqueId());
        final FactionType victimFaction = factionManager.getFaction(victim.getUniqueId());
        if (raidManager.isSanctionedKill(killer.getUniqueId(), victim.getUniqueId())) {
            final boolean scored = raidManager.recordKill(killerFaction, victim.getLocation());
            killer.getScheduler().run(plugin, task -> {
                statsManager.recordRaidKill(killer);
                killer.sendMessage(messageManager.getMessage(
                        scored ? "faction-raid-kill" : "faction-raid-kill-outside-zone",
                        scored ? "<gold>⚔ Raid-ölés jóváírva a(z) {faction} oldalán!</gold>"
                                : "<gray>⚔ Szentesített raid-ölés, de a raid-zónán kívül — nem ér pontot.</gray>",
                        Map.of("faction", killerFaction.getDisplayName())
                ));
            }, null);
            return;
        }

        // Bounty hunter rule: a high-sin victim carries a
        // bounty. Executing them is a justified kill — the hunter is PAID and gains NO sin,
        // and the criminal's bounty resets (they paid with their life). The sin count is
        // read on the victim's own region thread (this event runs there); the currency
        // payout is UUID-keyed (safe off-thread) and the broadcast names the reward.
        if (configManager.getBoolean("factions.sins.bounty.enabled", true)) {
            final int victimSins = sinManager.getSinCount(victim);
            final int minSins = Math.max(1, configManager.getInt("factions.sins.bounty.min-sins", 3));
            if (victimSins >= minSins) {
                final double reward = victimSins
                        * Math.max(0.0D, configManager.getDouble("factions.sins.bounty.reward-per-sin", 25.0D));
                final CurrencyType currency = resolveBountyCurrency();
                if (configManager.getBoolean("factions.sins.bounty.clear-sins-on-death", true)) {
                    sinManager.resetSinCount(victim);
                }
                if (reward > 0.0D && currency != null) {
                    currencyManager.addToBalance(killer.getUniqueId(), currency, reward);
                }
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "bounty-claimed",
                        "<gold>💰 {hunter} beváltotta a fejpénzt {target} fejére: {reward} {currency}!</gold>",
                        Map.of(
                                "hunter", killer.getName(),
                                "target", victim.getName(),
                                "reward", currencyManager.formatBalance(reward),
                                "currency", currency == null ? "?" : currency.getDisplayName()
                        )
                ));
                return;
            }
        }

        final boolean betrayal = killerFaction == victimFaction && killerFaction != FactionType.NEUTRAL;
        final int weight;
        final String messageKey;
        final String messageDefault;
        if (betrayal) {
            if (!configManager.getBoolean("factions.sins.betrayal-counts", true)) {
                return;
            }
            weight = Math.max(1, configManager.getInt("factions.sins.betrayal-weight", 2));
            messageKey = "sinner.betrayal-recorded";
            messageDefault = "<dark_purple>Bűnt követtél el: árulás — a saját frakciótársadat ölted meg. "
                    + "<gray>Bűneid: <white>{count}</white>/<white>{threshold}</white></gray></dark_purple>";
        } else {
            if (!configManager.getBoolean("factions.sins.murder-counts", true)) {
                return;
            }
            weight = 1;
            messageKey = "sinner.sin-recorded";
            messageDefault = "<dark_purple>Bűnt követtél el: gyilkosság. "
                    + "<gray>Bűneid: <white>{count}</white>/<white>{threshold}</white></gray></dark_purple>";
        }

        final int threshold = Math.max(0, configManager.getInt("factions.sins.exile-threshold", 4));
        killer.getScheduler().run(plugin, task -> {
            final int sinCount = sinManager.addSin(killer, weight);
            killer.sendMessage(messageManager.getMessage(
                    messageKey,
                    messageDefault,
                    Map.of("count", String.valueOf(sinCount), "threshold", String.valueOf(threshold))
            ));
        }, null);
    }

    /** The currency bounties are paid in (config; defaults to Neutral tokens). */
    private CurrencyType resolveBountyCurrency() {
        final CurrencyType configured = CurrencyType.fromInput(
                configManager.getString("factions.sins.bounty.currency", "NEUTRAL"));
        return configured == null ? CurrencyType.fromFactionType(FactionType.NEUTRAL) : configured;
    }
}
