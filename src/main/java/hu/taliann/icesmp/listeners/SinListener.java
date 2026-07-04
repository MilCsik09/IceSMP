package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Records sins for player murder and betrayal: the killer gains sin points,
 * and the MetelytepoManager exiles repeat offenders to the Dark faction once
 * the configured threshold is reached. Killing a member of your OWN faction
 * is betrayal and weighs more than plain murder (Neutrals are a loose
 * association, so a Neutral-on-Neutral kill stays plain murder). Sanctioned
 * raid kills (between the two warring factions) carry no sin and score for
 * the killer's side instead.
 */
public final class SinListener implements Listener {

    private final JavaPlugin plugin;
    private final MetelytepoManager metelytepoManager;
    private final RaidManager raidManager;
    private final FactionManager factionManager;
    private final StatsManager statsManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public SinListener(final JavaPlugin plugin, final MetelytepoManager metelytepoManager, final RaidManager raidManager,
                       final FactionManager factionManager, final StatsManager statsManager,
                       final ConfigManager configManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.metelytepoManager = metelytepoManager;
        this.raidManager = raidManager;
        this.factionManager = factionManager;
        this.statsManager = statsManager;
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

        // Raid rule (ideas.md): sanctioned war kills carry no sin — they score for the raid.
        // Faction lookups and raid scoring are in-memory data (safe here); the killer-side
        // mutations (PDC sin/stats, messages) are hopped onto the killer's own region thread,
        // because PlayerDeathEvent runs on the VICTIM's region and the killer may be elsewhere.
        final FactionType killerFaction = factionManager.getFaction(killer.getUniqueId());
        final FactionType victimFaction = factionManager.getFaction(victim.getUniqueId());
        if (raidManager.isAtWar(killerFaction, victimFaction)) {
            raidManager.recordKill(killerFaction);
            killer.getScheduler().run(plugin, task -> {
                statsManager.recordRaidKill(killer);
                killer.sendMessage(messageManager.getMessage(
                        "faction-raid-kill",
                        "<gold>⚔ Raid-ölés jóváírva a(z) {faction} oldalán!</gold>",
                        Map.of("faction", killerFaction.getDisplayName())
                ));
            }, null);
            return;
        }

        // Betrayal (ROADMAP "Bűn-rendszer bővítés"): killing your own faction member weighs
        // more than plain murder. Neutrals are a loose association, not an allegiance.
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
            final int sinCount = metelytepoManager.addSin(killer, weight);
            killer.sendMessage(messageManager.getMessage(
                    messageKey,
                    messageDefault,
                    Map.of("count", String.valueOf(sinCount), "threshold", String.valueOf(threshold))
            ));
        }, null);
    }
}
