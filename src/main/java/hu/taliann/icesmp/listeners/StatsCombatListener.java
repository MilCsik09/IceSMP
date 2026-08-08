package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.StatsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Feeds the {@code /stats} profile counters from combat: player
 * kills/deaths ({@link PlayerDeathEvent}) and mob kills ({@link EntityDeathEvent}
 * for non-player victims).
 *
 * <p>Folia note: both events run on the DYING entity's own region thread. We
 * only ever read the killer's {@link java.util.UUID} off {@code getKiller()} —
 * the killer entity itself is never touched (no PDC/inventory read, no
 * {@code sendMessage}). {@link StatsManager}'s record* methods are pure
 * concurrent (atomic) map operations, so calling them here needs no scheduler
 * hop and is safe from any region thread.
 *
 * <p>Registered at {@link EventPriority#MONITOR}: purely observational, never
 * affects death/combat resolution.
 */
public final class StatsCombatListener implements Listener {

    private final StatsManager statsManager;

    public StatsCombatListener(final StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        statsManager.recordDeath(event.getEntity().getUniqueId());
        final Player killer = event.getEntity().getKiller();
        if (killer != null) {
            statsManager.recordKill(killer.getUniqueId());
        }
    }

    /**
     * Non-player deaths only. {@link PlayerDeathEvent} keeps its own handler
     * list (it does not fall through to this handler), but we still guard
     * against a {@link Player} victim here — same defensive pattern used by
     * {@code QuestProgressListener#onEntityDeath} — so a mob kill is never
     * double-counted as a player kill.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(final EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        final Player killer = hu.taliann.icesmp.utils.MobKillUtil.eligibleTrackingKiller(event.getEntity());
        if (killer != null) {
            statsManager.recordMobKill(killer.getUniqueId(),
                    event.getEntity() instanceof org.bukkit.entity.Monster
                            ? hu.taliann.icesmp.managers.BestiaryManager.entryId(event.getEntity())
                            : null);
        }
    }
}
