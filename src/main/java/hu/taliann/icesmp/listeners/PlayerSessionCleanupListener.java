package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.JobGUIHolder;
import hu.taliann.icesmp.gui.ProfileHolder;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.spells.ArmamentSpell;
import hu.taliann.icesmp.spells.DoubleJumpSpell;
import hu.taliann.icesmp.spells.HideSpell;
import hu.taliann.icesmp.spells.InnerFocusSpell;
import hu.taliann.icesmp.spells.LuckyStarSpell;
import hu.taliann.icesmp.spells.RootSpell;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class PlayerSessionCleanupListener implements Listener {

    private final SpellbookListener spellbookListener;
    private final JobManager jobManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MetelytepoManager metelytepoManager;
    private final RelicManager relicManager;

    public PlayerSessionCleanupListener(final SpellbookListener spellbookListener,
                                        final JobManager jobManager,
                                        final CurrencyManager currencyManager,
                                        final FactionManager factionManager,
                                        final MetelytepoManager metelytepoManager,
                                        final RelicManager relicManager) {
        this.spellbookListener = spellbookListener;
        this.jobManager = jobManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.metelytepoManager = metelytepoManager;
        this.relicManager = relicManager;
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        cleanupPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(final PlayerKickEvent event) {
        cleanupPlayerState(event.getPlayer().getUniqueId());
    }

    public void cleanupPlayerState(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);

        spellbookListener.clearPlayerState(playerId);
        jobManager.clearPlayerState(playerId);
        currencyManager.clearPlayerState(playerId);
        factionManager.clearPlayerState(playerId);
        metelytepoManager.clearPlayerState(playerId);
        relicManager.clearPlayerState(playerId);

        HideSpell.clearPlayerState(playerId);
        LuckyStarSpell.clearPlayerState(playerId);
        ArmamentSpell.clearPlayerState(playerId);
        InnerFocusSpell.clearPlayerState(playerId);
        RootSpell.clearPlayerState(playerId);
        DoubleJumpSpell.clearPlayerState(playerId);

        if (player != null && (player.getOpenInventory().getTopInventory().getHolder() instanceof JobGUIHolder
                || player.getOpenInventory().getTopInventory().getHolder() instanceof ProfileHolder)) {
            player.closeInventory();
        }
    }
}


