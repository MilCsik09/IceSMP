package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Achievements (ROADMAP phase 8): milestone goals checked against tracked player
 * data on the periodic stats tick. Newly-earned achievements are stored in the
 * player's PDC, announced, and pay a currency reward. Read-only helpers back the
 * achievements GUI.
 */
public final class AchievementManager {

    public enum Metric { CLASS_LEVEL, WEALTH, RAID_KILLS, PROFESSION_LEVEL, DAILY_STREAK }

    public record Achievement(String id, String name, String description, Metric metric, double threshold, long reward) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final CurrencyManager currencyManager;
    private final ProfessionManager professionManager;
    private final FactionManager factionManager;
    private final StatsManager statsManager;
    private final DailyQuestManager dailyQuestManager;
    private final MessageManager messageManager;
    private final NamespacedKey earnedKey;
    private final List<Achievement> achievements;

    public AchievementManager(final JavaPlugin plugin, final ConfigManager configManager, final JobManager jobManager,
                              final CurrencyManager currencyManager, final ProfessionManager professionManager,
                              final FactionManager factionManager, final StatsManager statsManager,
                              final DailyQuestManager dailyQuestManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.currencyManager = currencyManager;
        this.professionManager = professionManager;
        this.factionManager = factionManager;
        this.statsManager = statsManager;
        this.dailyQuestManager = dailyQuestManager;
        this.messageManager = messageManager;
        this.earnedKey = new NamespacedKey(plugin, "achievements");
        this.achievements = List.of(
                // Raid-killek
                new Achievement("first_blood", "Első Vér", "Szerezz 1 raid-killt.", Metric.RAID_KILLS, 1, 50),
                new Achievement("skirmisher", "Csatadöntő", "Szerezz 10 raid-killt.", Metric.RAID_KILLS, 10, 200),
                new Achievement("warlord", "Hadúr", "Szerezz 25 raid-killt.", Metric.RAID_KILLS, 25, 500),
                new Achievement("war_hero", "Háborús Hős", "Szerezz 100 raid-killt.", Metric.RAID_KILLS, 100, 2000),
                // Kaszt-szint
                new Achievement("apprentice", "Tanonc", "Érd el a 10. kaszt-szintet.", Metric.CLASS_LEVEL, 10, 75),
                new Achievement("veteran", "Veterán", "Érd el a 25. kaszt-szintet.", Metric.CLASS_LEVEL, 25, 200),
                new Achievement("champion", "Bajnok", "Érd el a 40. kaszt-szintet.", Metric.CLASS_LEVEL, 40, 600),
                new Achievement("legend", "Legenda", "Érd el az 50. (max) kaszt-szintet.", Metric.CLASS_LEVEL, 50, 1000),
                // Vagyon
                new Achievement("saver", "Megtakarító", "Gyűjts össze 250 valutát.", Metric.WEALTH, 250, 50),
                new Achievement("well_off", "Tehetős", "Gyűjts össze 1000 valutát.", Metric.WEALTH, 1000, 100),
                new Achievement("magnate", "Mágnás", "Gyűjts össze 10000 valutát.", Metric.WEALTH, 10000, 750),
                new Achievement("croesus", "Krőzus", "Gyűjts össze 50000 valutát.", Metric.WEALTH, 50000, 2500),
                // Szakma
                new Achievement("tradesman", "Szakmunkás", "Érj el 25 össz-szakmaszintet.", Metric.PROFESSION_LEVEL, 25, 150),
                new Achievement("craftsman", "Iparos", "Érj el 50 össz-szakmaszintet.", Metric.PROFESSION_LEVEL, 50, 350),
                new Achievement("artisan", "Mesterember", "Érj el 100 össz-szakmaszintet.", Metric.PROFESSION_LEVEL, 100, 800),
                new Achievement("grandmaster", "Nagymester", "Érj el 200 össz-szakmaszintet.", Metric.PROFESSION_LEVEL, 200, 2000),
                // Magasabb fokozatok
                new Achievement("warmaster", "Hadvezér", "Szerezz 50 raid-killt.", Metric.RAID_KILLS, 50, 1000),
                new Achievement("gold_mountain", "Aranyhegy", "Gyűjts össze 100000 valutát.", Metric.WEALTH, 100000, 5000),
                // Napi sorozat (streak)
                new Achievement("persistent", "Kitartó", "Érj el 3 napos napi-sorozatot.", Metric.DAILY_STREAK, 3, 100),
                new Achievement("dedicated", "Elszánt", "Érj el 7 napos napi-sorozatot.", Metric.DAILY_STREAK, 7, 300),
                new Achievement("obsessed", "Megszállott", "Érj el 30 napos napi-sorozatot.", Metric.DAILY_STREAK, 30, 1500)
        );
    }

    public boolean isEnabled() {
        return configManager.getBoolean("achievements.enabled", true);
    }

    public List<Achievement> getAchievements() {
        return achievements;
    }

    /** Periodic per-player evaluation (each on its own region thread; Folia-safe). */
    public void tick() {
        if (!isEnabled()) {
            return;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> evaluate(player), null);
        }
    }

    /** Checks every achievement for the player and awards any newly-completed ones. */
    public void evaluate(final Player player) {
        if (!isEnabled() || player == null) {
            return;
        }
        final Set<String> earned = getEarned(player);
        boolean changed = false;
        for (final Achievement achievement : achievements) {
            if (earned.contains(achievement.id())) {
                continue;
            }
            if (metricValue(player, achievement.metric()) >= achievement.threshold()) {
                earned.add(achievement.id());
                changed = true;
                award(player, achievement);
            }
        }
        if (changed) {
            saveEarned(player, earned);
        }
    }

    public boolean isEarned(final Player player, final String id) {
        return getEarned(player).contains(id.toLowerCase(Locale.ROOT));
    }

    public double metricValue(final Player player, final Metric metric) {
        return switch (metric) {
            case CLASS_LEVEL -> jobManager.getPrimaryLevel(player);
            case WEALTH -> currencyManager.getBalance(player);
            case RAID_KILLS -> statsManager.getRaidKills(player.getUniqueId());
            case PROFESSION_LEVEL -> totalProfessionLevel(player);
            case DAILY_STREAK -> dailyQuestManager.getStreak(player);
        };
    }

    private double totalProfessionLevel(final Player player) {
        int total = 0;
        for (final ProfessionType profession : ProfessionType.values()) {
            total += professionManager.getLevel(player, profession);
        }
        return total;
    }

    private void award(final Player player, final Achievement achievement) {
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        final CurrencyType currency = CurrencyType.fromFactionType(faction);
        if (achievement.reward() > 0) {
            currencyManager.addToBalance(player.getUniqueId(), currency, achievement.reward());
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        player.sendMessage(messageManager.getMessage(
                "achievement-earned",
                "<gold>🏆 Elérés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} valuta)</gray></gold>",
                Map.of("name", achievement.name(), "reward", String.valueOf(achievement.reward()))));
    }

    private Set<String> getEarned(final Player player) {
        final Set<String> earned = new LinkedHashSet<>();
        final String raw = player.getPersistentDataContainer().get(earnedKey, PersistentDataType.STRING);
        if (raw != null && !raw.isBlank()) {
            for (final String id : raw.split(",")) {
                if (!id.isBlank()) {
                    earned.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return earned;
    }

    private void saveEarned(final Player player, final Set<String> earned) {
        player.getPersistentDataContainer().set(earnedKey, PersistentDataType.STRING, String.join(",", earned));
    }
}
