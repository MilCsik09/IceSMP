package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileDailyQuestStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/** Daily and weekly quests backed exclusively by PlayerProfile QuestSection CAS. */
public final class DailyQuestManager {

    public record Daily(String id, String name, String type, int amount, long reward) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final PlayerProfileDailyQuestStore store = new PlayerProfileDailyQuestStore();
    private final List<Daily> pool;
    private final List<Daily> weeklyPool;

    public DailyQuestManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final CurrencyManager currencyManager,
                             final FactionManager factionManager,
                             final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        this.weeklyPool = List.of(
                weekly("weekly_hunt", "Heti hadjárat", "KILL_MOBS", 150, 500),
                weekly("weekly_fish", "Heti nagy fogás", "CATCH_FISH", 60, 400),
                weekly("weekly_mine", "Heti bányászság", "MINE_ORE", 96, 600),
                weekly("weekly_harvest", "Heti betakarítás", "HARVEST_CROPS", 200, 450),
                weekly("weekly_beasts", "Heti vadbefogás", "KILL_ANIMALS", 80, 400),
                weekly("weekly_excavate", "Heti nagy ásatás", "BREAK_BLOCKS", 384, 500),
                weekly("weekly_slay", "Heti nagy mészárlás", "KILL_MOBS", 300, 800),
                weekly("weekly_prospect", "Heti nagy bányászat", "MINE_ORE", 256, 700));
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
                daily("daily_ranger", "Napi vadőr", "KILL_ANIMALS", 25, 60));
    }

    public boolean isEnabled() { return configManager.getBoolean("daily-quests.enabled", true); }

    private Daily daily(final String id, final String name, final String type,
                        final int amount, final long reward) {
        return new Daily(id, name, type,
                Math.max(1, configManager.getInt("daily-quests." + id + ".amount", amount)),
                Math.max(0L, configManager.getLong("daily-quests." + id + ".reward", reward)));
    }

    private Daily weekly(final String id, final String name, final String type,
                         final int amount, final long reward) {
        return new Daily(id, name, type,
                Math.max(1, configManager.getInt("daily-quests." + id + ".amount", amount)),
                Math.max(0L, configManager.getLong("daily-quests." + id + ".reward", reward)));
    }

    private long today() {
        return java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toEpochDay();
    }

    private long thisWeek() { return today() / 7L; }

    public Daily getActive() {
        return pool.get((int) Math.floorMod(today(), pool.size()));
    }

    public Daily getActiveWeekly() {
        return weeklyPool.get((int) Math.floorMod(thisWeek(), weeklyPool.size()));
    }

    public int getProgress(final Player player) {
        return store.state(player.getUniqueId(), false, today()).progress();
    }

    public boolean isDone(final Player player) {
        return store.state(player.getUniqueId(), false, today()).done();
    }

    public int getWeeklyProgress(final Player player) {
        return store.state(player.getUniqueId(), true, thisWeek()).progress();
    }

    public boolean isWeeklyDone(final Player player) {
        return store.state(player.getUniqueId(), true, thisWeek()).done();
    }

    public int getStreak(final Player player) {
        return store.streak(player.getUniqueId());
    }

    public void handle(final Player player, final String type) {
        if (!isEnabled() || player == null || type == null) return;
        handleDaily(player, type);
        handleWeekly(player, type);
    }

    private void handleDaily(final Player player, final String type) {
        final Daily daily = getActive();
        if (!daily.type().equalsIgnoreCase(type)) return;
        store.advanceDaily(player.getUniqueId(), today(), daily.amount())
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        logFailure("daily", player, failure);
                        return;
                    }
                    if (!result.completedNow()) return;
                    player.getScheduler().run(plugin,
                            task -> deliverDaily(player, daily, result.streak()), null);
                });
    }

    private void handleWeekly(final Player player, final String type) {
        final Daily weekly = getActiveWeekly();
        if (!weekly.type().equalsIgnoreCase(type)) return;
        store.advanceWeekly(player.getUniqueId(), thisWeek(), weekly.amount())
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        logFailure("weekly", player, failure);
                        return;
                    }
                    if (!result.completedNow()) return;
                    player.getScheduler().run(plugin,
                            task -> deliverWeekly(player, weekly), null);
                });
    }

    private void deliverDaily(final Player player, final Daily daily, final int streak) {
        if (!player.isOnline()) {
            plugin.getLogger().warning("Daily reward receipt committed while player went offline: "
                    + player.getUniqueId() + '/' + daily.id());
            return;
        }
        final int bonusPerDay = Math.max(0,
                configManager.getInt("daily-quests.streak-bonus-per-day", 5));
        final int bonusCapDays = Math.max(0,
                configManager.getInt("daily-quests.streak-bonus-cap-days", 7));
        final long streakBonus = (long) Math.min(streak, bonusCapDays) * bonusPerDay;
        final long totalReward = daily.reward() + streakBonus;
        final FactionType faction = factionManager.getEconomyFaction(player.getUniqueId());
        if (totalReward > 0L) {
            currencyManager.payOutTokens(player,
                    CurrencyType.fromFactionType(faction), totalReward);
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0F, 1.2F);
        player.sendMessage(messageManager.getMessage("daily-completed",
                "<gold>📅 Napi küldetés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} valuta, sorozat: {streak} nap)</gray></gold>",
                Map.of("name", daily.name(), "reward", String.valueOf(totalReward),
                        "streak", String.valueOf(streak))));
    }

    private void deliverWeekly(final Player player, final Daily weekly) {
        if (!player.isOnline()) {
            plugin.getLogger().warning("Weekly reward receipt committed while player went offline: "
                    + player.getUniqueId() + '/' + weekly.id());
            return;
        }
        final FactionType faction = factionManager.getEconomyFaction(player.getUniqueId());
        if (weekly.reward() > 0L) {
            currencyManager.payOutTokens(player,
                    CurrencyType.fromFactionType(faction), weekly.reward());
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0F, 0.8F);
        player.sendMessage(messageManager.getMessage("weekly-completed",
                "<gold>🗓 Heti küldetés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} valuta)</gray></gold>",
                Map.of("name", weekly.name(),
                        "reward", String.valueOf(weekly.reward()))));
    }

    private void logFailure(final String kind, final Player player,
                            final Throwable failure) {
        plugin.getLogger().severe("PlayerProfile " + kind + " quest commit failed for "
                + player.getUniqueId() + ": " + failure.getMessage());
    }
}
