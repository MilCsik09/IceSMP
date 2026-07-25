package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.JobGUIHolder;
import hu.taliann.icesmp.gui.ProfileHolder;
import hu.taliann.icesmp.managers.CraftingRestrictionManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;

public final class PlayerSessionCleanupListener implements Listener {

    /** Every component holding per-player session state — cleaned in one uniform pass. */
    private final List<PlayerStateCleanup> stateOwners;
    /** Spells are cleaned via the registry, so a new stateful spell needs no change here. */
    private final SpellRegistry spellRegistry;

    public PlayerSessionCleanupListener(final AbilityCatalystListener abilityCatalystListener,
                                        final JobManager jobManager,
                                        final CurrencyManager currencyManager,
                                        final FactionManager factionManager,
                                        final MetelytepoManager metelytepoManager,
                                        final RelicManager relicManager,
                                        final CraftingRestrictionManager craftingRestrictionManager,
                                        final hu.taliann.icesmp.managers.ResourceManager resourceManager,
                                        final hu.taliann.icesmp.managers.PartyManager partyManager,
                                        final hu.taliann.icesmp.managers.ClaimManager claimManager,
                                        final hu.taliann.icesmp.managers.TerritoryManager territoryManager,
                                        final hu.taliann.icesmp.managers.PetManager petManager,
                                        final hu.taliann.icesmp.managers.RitualManager ritualManager,
                                        final hu.taliann.icesmp.managers.ProfessionManager professionManager,
                                        final hu.taliann.icesmp.managers.AfkManager afkManager,
                                        final hu.taliann.icesmp.managers.SitManager sitManager,
                                        final hu.taliann.icesmp.managers.ModerationManager moderationManager,
                                        final hu.taliann.icesmp.managers.WhisperManager whisperManager,
                                        final hu.taliann.icesmp.managers.GuildManager guildManager,
                                        final hu.taliann.icesmp.managers.HonorDuelManager honorDuelManager,
                                        final hu.taliann.icesmp.managers.SpyManager spyManager,
                                        final hu.taliann.icesmp.managers.CombatTagManager combatTagManager,
                                        final hu.taliann.icesmp.managers.ClassHealthService classHealthService,
                                        final hu.taliann.icesmp.listeners.LowHealthBorderListener lowHealthBorderListener,
                                        final SpellRegistry spellRegistry) {
        // Register every stateful component here; adding a new one needs only this line + the interface.
        this.stateOwners = List.of(abilityCatalystListener, jobManager, currencyManager, factionManager,
                metelytepoManager, relicManager, craftingRestrictionManager, resourceManager, partyManager,
                claimManager, territoryManager, petManager, ritualManager, professionManager,
                afkManager, sitManager, moderationManager, whisperManager, guildManager,
                honorDuelManager, spyManager, combatTagManager, classHealthService, lowHealthBorderListener);
        this.spellRegistry = spellRegistry;
    }

    /**
     * A játékmód-tükör feltöltése: a join a játékos SAJÁT régió-szálán fut, tehát a
     * {@code getGameMode()} itt biztonságosan olvasható. Innentől a jutalom-előszűrő
     * (az áldozat szálán) a tükörből olvas, nem a másik entitásból.
     */
    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        hu.taliann.icesmp.utils.GameModeCache.update(event.getPlayer());
    }

    /** A váltás az ÚJ értékkel érkezik, ezért nem a játékosból olvassuk, hanem az eventből. */
    @EventHandler
    public void onGameModeChange(final PlayerGameModeChangeEvent event) {
        hu.taliann.icesmp.utils.GameModeCache.update(event.getPlayer().getUniqueId(), event.getNewGameMode());
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

        hu.taliann.icesmp.utils.GameModeCache.remove(playerId);

        for (final PlayerStateCleanup owner : stateOwners) {
            owner.clearPlayerState(playerId);
        }

        for (final Spell spell : spellRegistry.getAll()) {
            spell.clearPlayerState(playerId);
        }

        if (player != null && (player.getOpenInventory().getTopInventory().getHolder() instanceof JobGUIHolder
                || player.getOpenInventory().getTopInventory().getHolder() instanceof ProfileHolder)) {
            player.closeInventory();
        }
    }
}


