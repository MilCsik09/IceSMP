package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * Daily quests (ROADMAP phase 8): a small pool of objectives that rotates once
 * per day. Everyone shares the same daily (deterministic by day index); progress
 * and completion are tracked per player in PDC and reset when the day changes.
 * Completing it auto-pays a currency reward.
 */
public final class DailyQuestManager {

    /** A daily objective: {@code type} is one of KILL_MOBS / CATCH_FISH / BREAK_BLOCKS. */
    public record Daily(String id, String name, String type, int amount, long reward) { }

    private static final long DAY_MS = 86_400_000L;

    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final NamespacedKey dayKey;
    private final NamespacedKey progressKey;
    private final NamespacedKey doneKey;
    private final List<Daily> pool;

    public DailyQuestManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final CurrencyManager currencyManager, final FactionManager factionManager,
                             final MessageManager messageManager) {
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        this.dayKey = new NamespacedKey(plugin, "daily_day");
        this.progressKey = new NamespacedKey(plugin, "daily_progress");
        this.doneKey = new NamespacedKey(plugin, "daily_done");
        this.pool = List.of(
                daily("daily_hunt", "Napi vadászat", "KILL_MOBS", 20, 60),
                daily("daily_fish", "Napi horgászat", "CATCH_FISH", 10, 50),
                daily("daily_gather", "Napi gyűjtögetés", "BREAK_BLOCKS", 64, 40)
        );
    }

    public boolean isEnabled() {
        return configManager.getBoolean("daily-quests.enabled", true);
    }

    private Daily daily(final String id, final String name, final String type, final int amount, final long reward) {
        return new Daily(id, name, type,
                Math.max(1, configManager.getInt("daily-quests." + id + ".amount", amount)),
                Math.max(0L, configManager.getLong("daily-quests." + id + ".reward", reward)));
    }

    private long today() {
        return System.currentTimeMillis() / DAY_MS;
    }

    /** The active daily for everyone today. */
    public Daily getActive() {
        return pool.get((int) Math.floorMod(today(), pool.size()));
    }

    public int getProgress(final Player player) {
        ensureFresh(player);
        return player.getPersistentDataContainer().getOrDefault(progressKey, PersistentDataType.INTEGER, 0);
    }

    public boolean isDone(final Player player) {
        ensureFresh(player);
        return player.getPersistentDataContainer().getOrDefault(doneKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /** Records progress for an action type; rewards + notifies on completion. */
    public void handle(final Player player, final String type) {
        if (!isEnabled()) {
            return;
        }
        ensureFresh(player);
        final Daily daily = getActive();
        if (!daily.type().equalsIgnoreCase(type) || isDone(player)) {
            return;
        }

        final int progress = getProgress(player) + 1;
        player.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER, progress);
        if (progress < daily.amount()) {
            return;
        }

        player.getPersistentDataContainer().set(doneKey, PersistentDataType.BYTE, (byte) 1);
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        if (daily.reward() > 0) {
            currencyManager.addToBalance(player.getUniqueId(), CurrencyType.fromFactionType(faction), daily.reward());
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);
        player.sendMessage(messageManager.getMessage(
                "daily-completed",
                "<gold>📅 Napi küldetés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} valuta)</gray></gold>",
                Map.of("name", daily.name(), "reward", String.valueOf(daily.reward()))));
    }

    private void ensureFresh(final Player player) {
        final long today = today();
        if (player.getPersistentDataContainer().getOrDefault(dayKey, PersistentDataType.LONG, -1L) != today) {
            player.getPersistentDataContainer().set(dayKey, PersistentDataType.LONG, today);
            player.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER, 0);
            player.getPersistentDataContainer().set(doneKey, PersistentDataType.BYTE, (byte) 0);
        }
    }
}
