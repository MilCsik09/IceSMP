package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.ExperienceUtil;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class LuckyStarSpell extends BaseSpell {

    private static final int XP_DRAIN_PER_SECOND = 20;
    private static final Map<UUID, Long> ACTIVE_PLAYERS = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> DRAIN_TASKS = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;

    public LuckyStarSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "lucky_star", "Lucky Star", 0, SpellCostType.XP, 0);
        this.plugin = plugin;
    }

    @Override
    public boolean canCast(final Player player) {
        return isActive(player) || ExperienceUtil.getTotalExperience(player) >= XP_DRAIN_PER_SECOND;
    }

    @Override
    public void execute(final Player player) {
        if (isActive(player)) {
            deactivate(player, true);
            return;
        }

        ACTIVE_PLAYERS.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage(resolveMessage("spell.lucky_star.on", "<gold>Lucky Star aktiv.</gold>"));
        startDrainTask(player.getUniqueId());
    }

    private void startDrainTask(final UUID playerId) {
        final BukkitTask existing = DRAIN_TASKS.remove(playerId);
        if (existing != null) {
            existing.cancel();
        }

        final BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            final Player player = Bukkit.getPlayer(playerId);
            if (player == null || !isActive(player)) {
                DRAIN_TASKS.remove(playerId);
                return;
            }

            if (ExperienceUtil.getTotalExperience(player) < XP_DRAIN_PER_SECOND) {
                deactivate(player, false);
                return;
            }

            ExperienceUtil.setTotalExperience(player, ExperienceUtil.getTotalExperience(player) - XP_DRAIN_PER_SECOND);
            startDrainTask(playerId);
        }, 20L);
        DRAIN_TASKS.put(playerId, task);
    }

    public static boolean shouldDodge(final Player player) {
        return isActive(player) && ThreadLocalRandom.current().nextDouble() < 0.40D;
    }

    public static boolean isActive(final Player player) {
        return ACTIVE_PLAYERS.containsKey(player.getUniqueId());
    }

    public static void cleanup(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        ACTIVE_PLAYERS.remove(playerId);
        final BukkitTask task = DRAIN_TASKS.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public static void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }

    private void deactivate(final Player player, final boolean manual) {
        cleanup(player.getUniqueId());
        if (manual) {
            player.sendMessage(resolveMessage("spell.lucky_star.off", "<gray>Lucky Star kikapcsolva.</gray>"));
        } else {
            player.sendMessage(resolveMessage("spell.lucky_star.off_no_xp", "<red>Lucky Star kikapcsolt: nincs eleg XP.</red>"));
        }
    }
}

