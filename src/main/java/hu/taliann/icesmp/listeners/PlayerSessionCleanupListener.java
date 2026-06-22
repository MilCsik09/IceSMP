package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.JobGUIHolder;
import hu.taliann.icesmp.gui.ProfileHolder;
import hu.taliann.icesmp.managers.CraftingRestrictionManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.spells.ArmamentSpell;
import hu.taliann.icesmp.spells.DoubleJumpSpell;
import hu.taliann.icesmp.spells.HideSpell;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.InnerFocusSpell;
import hu.taliann.icesmp.spells.LuckyStarSpell;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;

public final class PlayerSessionCleanupListener implements Listener {

    /** Every component holding per-player session state — cleaned in one uniform pass. */
    private final List<PlayerStateCleanup> stateOwners;

    public PlayerSessionCleanupListener(final AbilityCatalystListener abilityCatalystListener,
                                        final JobManager jobManager,
                                        final CurrencyManager currencyManager,
                                        final FactionManager factionManager,
                                        final MetelytepoManager metelytepoManager,
                                        final RelicManager relicManager,
                                        final CraftingRestrictionManager craftingRestrictionManager) {
        // Register every stateful component here; adding a new one needs only this line + the interface.
        this.stateOwners = List.of(abilityCatalystListener, jobManager, currencyManager, factionManager,
                metelytepoManager, relicManager, craftingRestrictionManager);
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

        for (final PlayerStateCleanup owner : stateOwners) {
            owner.clearPlayerState(playerId);
        }

        // Spells still hold static per-player state (no registered instance yet — see REORGANIZATION #6).
        HideSpell.clearPlayerState(playerId);
        LuckyStarSpell.clearPlayerState(playerId);
        ArmamentSpell.clearPlayerState(playerId);
        InnerFocusSpell.clearPlayerState(playerId);
        DoubleJumpSpell.clearPlayerState(playerId);

        if (player != null && (player.getOpenInventory().getTopInventory().getHolder() instanceof JobGUIHolder
                || player.getOpenInventory().getTopInventory().getHolder() instanceof ProfileHolder)) {
            player.closeInventory();
        }
    }
}


