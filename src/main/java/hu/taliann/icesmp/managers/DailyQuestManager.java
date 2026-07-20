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

    /**
     * A daily objective. {@code type} is one of the action keys fed by
     * {@code DailyQuestListener}: KILL_MOBS / CATCH_FISH / BREAK_BLOCKS /
     * MINE_ORE / HARVEST_CROPS / KILL_ANIMALS.
     */
    public record Daily(String id, String name, String type, int amount, long reward) { }


    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final NamespacedKey dayKey;
    private final NamespacedKey progressKey;
    private final NamespacedKey doneKey;
    private final NamespacedKey streakKey;
    private final NamespacedKey lastDoneKey;
    private final NamespacedKey weekKey;
    private final NamespacedKey weekProgressKey;
    private final NamespacedKey weekDoneKey;
    private final List<Daily> pool;
    private final List<Daily> weeklyPool;

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
        this.streakKey = new NamespacedKey(plugin, "daily_streak");
        this.lastDoneKey = new NamespacedKey(plugin, "daily_last_done");
        this.weekKey = new NamespacedKey(plugin, "weekly_week");
        this.weekProgressKey = new NamespacedKey(plugin, "weekly_progress");
        this.weekDoneKey = new NamespacedKey(plugin, "weekly_done");
        this.weeklyPool = List.of(
                weekly("weekly_hunt", "Heti hadjárat", "KILL_MOBS", 150, 500),
                weekly("weekly_fish", "Heti nagy fogás", "CATCH_FISH", 60, 400),
                weekly("weekly_mine", "Heti bányászság", "MINE_ORE", 96, 600),
                weekly("weekly_harvest", "Heti betakarítás", "HARVEST_CROPS", 200, 450),
                weekly("weekly_beasts", "Heti vadbefogás", "KILL_ANIMALS", 80, 400),
                weekly("weekly_excavate", "Heti nagy ásatás", "BREAK_BLOCKS", 384, 500),
                weekly("weekly_slay", "Heti nagy mészárlás", "KILL_MOBS", 300, 800),
                weekly("weekly_prospect", "Heti nagy bányászat", "MINE_ORE", 256, 700)
        );
        this.pool = List.of(
                daily("daily_hunt", "Napi vadászat", "KILL_MOBS", 20, 60),
                daily("daily_fish", "Napi horgászat", "CATCH_FISH", 10, 50),
                daily("daily_gather", "Napi gyűjtögetés", "BREAK_BLOCKS", 64, 40),
                daily("daily_mine", "Napi bányászat", "MINE_ORE", 16, 70),
                daily("daily_harvest", "Napi aratás", "HARVEST_CROPS", 32, 55),
                daily("daily_beasts", "Napi vadbefogás", "KILL_ANIMALS", 15, 45),
                daily("daily_slay", "Napi nagy hadjárat", "KILL_MOBS", 40, 110),
                daily("daily_excavate", "Napi nagy ásatás", "BREAK_BLOCKS", 128, 75),
                daily("daily_angler", "Napi pecás", "CATCH_FISH", 20, 70),
                daily("daily_butcher", "Napi mészáros", "KILL_ANIMALS", 30, 70),
                daily("daily_prospector", "Napi kutató", "MINE_ORE", 32, 110),
                daily("daily_forager", "Napi gyűjtő", "HARVEST_CROPS", 48, 65),
                daily("daily_ranger", "Napi vadőr", "KILL_ANIMALS", 25, 60)
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

    private Daily weekly(final String id, final String name, final String type, final int amount, final long reward) {
        return new Daily(id, name, type,
                Math.max(1, configManager.getInt("daily-quests." + id + ".amount", amount)),
                Math.max(0L, configManager.getLong("daily-quests." + id + ".reward", reward)));
    }

    private long thisWeek() {
        // Bucket weeks by epoch-day / 7, on the server's local date — a new weekly rotates every 7 days.
        return today() / 7L;
    }

    /** The active weekly quest for everyone this week. */
    public Daily getActiveWeekly() {
        return weeklyPool.get((int) Math.floorMod(thisWeek(), weeklyPool.size()));
    }

    public int getWeeklyProgress(final Player player) {
        ensureFreshWeekly(player);
        return player.getPersistentDataContainer().getOrDefault(weekProgressKey, PersistentDataType.INTEGER, 0);
    }

    public boolean isWeeklyDone(final Player player) {
        ensureFreshWeekly(player);
        return player.getPersistentDataContainer().getOrDefault(weekDoneKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private long today() {
        // Bucket by the server's LOCAL date (epoch-day), so the daily rotates at local midnight
        // for everyone — not at UTC midnight, which falls mid-day for non-UTC servers and would
        // wipe in-progress dailies.
        return java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toEpochDay();
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

    /** Feeds both the daily and the weekly quest for the given action type. */
    public void handle(final Player player, final String type) {
        if (!isEnabled()) {
            return;
        }
        handleDaily(player, type);
        handleWeekly(player, type);
    }

    /** Records daily progress for an action type; rewards (with streak bonus) + notifies on completion. */
    private void handleDaily(final Player player, final String type) {
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

        // Streak: consecutive days completed. Finishing on the day right after the last
        // completion extends the streak; any gap resets it to 1. The bonus scales with the
        // streak length up to a configurable cap.
        final long today = today();
        final long lastDone = player.getPersistentDataContainer().getOrDefault(lastDoneKey, PersistentDataType.LONG, -1L);
        final int previousStreak = player.getPersistentDataContainer().getOrDefault(streakKey, PersistentDataType.INTEGER, 0);
        final int streak = (lastDone == today - 1) ? previousStreak + 1 : 1;
        player.getPersistentDataContainer().set(streakKey, PersistentDataType.INTEGER, streak);
        player.getPersistentDataContainer().set(lastDoneKey, PersistentDataType.LONG, today);

        final int bonusPerDay = Math.max(0, configManager.getInt("daily-quests.streak-bonus-per-day", 5));
        final int bonusCapDays = Math.max(0, configManager.getInt("daily-quests.streak-bonus-cap-days", 7));
        final long streakBonus = (long) Math.min(streak, bonusCapDays) * bonusPerDay;
        final long totalReward = daily.reward() + streakBonus;

        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        if (totalReward > 0) {
            currencyManager.payOutTokens(player, CurrencyType.fromFactionType(faction), totalReward);
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);
        player.sendMessage(messageManager.getMessage(
                "daily-completed",
                "<gold>📅 Napi küldetés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} valuta, sorozat: {streak} nap)</gray></gold>",
                Map.of("name", daily.name(), "reward", String.valueOf(totalReward), "streak", String.valueOf(streak))));
    }

    /** The player's current daily-completion streak (consecutive days). */
    public int getStreak(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(streakKey, PersistentDataType.INTEGER, 0);
    }

    private void ensureFresh(final Player player) {
        final long today = today();
        if (player.getPersistentDataContainer().getOrDefault(dayKey, PersistentDataType.LONG, -1L) != today) {
            player.getPersistentDataContainer().set(dayKey, PersistentDataType.LONG, today);
            player.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER, 0);
            player.getPersistentDataContainer().set(doneKey, PersistentDataType.BYTE, (byte) 0);
        }
    }

    /** Records weekly progress for an action type; pays the (larger) reward on completion. */
    private void handleWeekly(final Player player, final String type) {
        ensureFreshWeekly(player);
        final Daily weekly = getActiveWeekly();
        if (!weekly.type().equalsIgnoreCase(type) || isWeeklyDone(player)) {
            return;
        }

        final int progress = getWeeklyProgress(player) + 1;
        player.getPersistentDataContainer().set(weekProgressKey, PersistentDataType.INTEGER, progress);
        if (progress < weekly.amount()) {
            return;
        }

        player.getPersistentDataContainer().set(weekDoneKey, PersistentDataType.BYTE, (byte) 1);
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        if (weekly.reward() > 0) {
            currencyManager.payOutTokens(player, CurrencyType.fromFactionType(faction), weekly.reward());
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F);
        player.sendMessage(messageManager.getMessage(
                "weekly-completed",
                "<gold>🗓 Heti küldetés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} valuta)</gray></gold>",
                Map.of("name", weekly.name(), "reward", String.valueOf(weekly.reward()))));
    }

    private void ensureFreshWeekly(final Player player) {
        final long week = thisWeek();
        if (player.getPersistentDataContainer().getOrDefault(weekKey, PersistentDataType.LONG, -1L) != week) {
            player.getPersistentDataContainer().set(weekKey, PersistentDataType.LONG, week);
            player.getPersistentDataContainer().set(weekProgressKey, PersistentDataType.INTEGER, 0);
            player.getPersistentDataContainer().set(weekDoneKey, PersistentDataType.BYTE, (byte) 0);
        }
    }
}
