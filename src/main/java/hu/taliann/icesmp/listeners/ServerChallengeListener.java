package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ServerChallengeManager;
import hu.taliann.icesmp.managers.ServerChallengeManager.ChallengeType;
import org.bukkit.GameMode;
import hu.taliann.icesmp.integrity.*;
import java.util.LinkedHashSet;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Feeds the {@link ServerChallengeManager} counter: hostile kills, ore mines and
 * crop harvests each count toward the active goal. Handlers run on the acting
 * region thread; the manager admits immutable provenance under its transition lock. Joining
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
        if (!(event.getEntity() instanceof Monster)) return;
        try {
            final var kill = hu.taliann.icesmp.utils.MobKillUtil.eligibleTrackingKill(event.getEntity());
            if (kill != null) serverChallengeManager.record(ChallengeType.SLAY, kill.rewardContext(RewardChannel.SERVER_CHALLENGE));
        } catch (final RuntimeException | LinkageError unavailable) { return; }
    }

    /** Ore mined / mature crop harvested by a survival player → MINE / HARVEST progress. */
    // MONITOR: a védelmi réteg HIGH/HIGHEST prioritáson cancel-el, ezért NORMAL-on a
    // progresszt még a visszavonás ELŐTT könyveltük volna — a tiltott törés/lerakás így
    // XP-t és quest-haladást adott. MONITOR-on az event végleges állapota már ismert,
    // és az ignoreCancelled valóban kizárja a visszavont akciót. Itt NEM módosítunk eventet.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (!isSurvival(player) || !event.isDropItems()) {
            return;
        }
        final Block block = event.getBlock();
        final RewardContext contribution;
        try {
            final var sources = new LinkedHashSet<>(BukkitRewardSources.causal(player));
            sources.addAll(BukkitRewardSources.block(block));
            contribution = new RewardContext(RewardChannel.SERVER_CHALLENGE, player.getUniqueId(), List.copyOf(sources));
        } catch (final RuntimeException | LinkageError unavailable) { return; }
        if (block.getType().name().endsWith("_ORE")) {
            serverChallengeManager.record(ChallengeType.MINE, contribution);
        } else if (isMatureCrop(block)) {
            serverChallengeManager.record(ChallengeType.HARVEST, contribution);
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
