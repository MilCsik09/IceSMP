package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ServerChallengeManager;
import hu.taliann.icesmp.managers.ServerChallengeManager.ChallengeType;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Feeds the {@link ServerChallengeManager} counter: hostile kills, ore mines and
 * crop harvests each count toward the active goal. Handlers run on the acting
 * region thread and the counter is atomic, so counting is Folia-safe. Joining
 * players are shown the live boss bar.
 */
public final class ServerChallengeListener implements Listener {

    private final ServerChallengeManager serverChallengeManager;

    public ServerChallengeListener(final ServerChallengeManager serverChallengeManager) {
        this.serverChallengeManager = serverChallengeManager;
    }

    /** Hostile mob slain by a player → SLAY progress. */
    @EventHandler(ignoreCancelled = true)
    public void onDeath(final EntityDeathEvent event) {
        if (event.getEntity() instanceof Monster && hu.taliann.icesmp.utils.MobKillUtil.eligibleTrackingKiller(event.getEntity()) != null) {
            serverChallengeManager.record(ChallengeType.SLAY);
        }
    }

    /** Ore mined / mature crop harvested by a survival player → MINE / HARVEST progress. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (!isSurvival(player) || !event.isDropItems()) {
            return;
        }
        final Block block = event.getBlock();
        if (block.getType().name().endsWith("_ORE")) {
            serverChallengeManager.record(ChallengeType.MINE);
        } else if (isMatureCrop(block)) {
            serverChallengeManager.record(ChallengeType.HARVEST);
        }
    }

    /** Show the live challenge boss bar to a joining player. */
    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        serverChallengeManager.showTo(event.getPlayer());
    }

    private static boolean isMatureCrop(final Block block) {
        return block.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge();
    }

    private static boolean isSurvival(final Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }
}
