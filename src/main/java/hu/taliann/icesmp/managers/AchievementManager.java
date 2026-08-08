package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Achievement milestones backed exclusively by PlayerProfile. */
public final class AchievementManager {

    public enum Metric { CLASS_LEVEL, WEALTH, RAID_KILLS, PROFESSION_LEVEL, DAILY_STREAK }

    public record Achievement(String id, String name, String description, Metric metric,
                              double threshold, long reward) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final CurrencyManager currencyManager;
    private final ProfessionManager professionManager;
    private final FactionManager factionManager;
    private final StatsManager statsManager;
    private final DailyQuestManager dailyQuestManager;
    private final MessageManager messageManager;
    private final PlayerProfileAchievementStore store = new PlayerProfileAchievementStore();
    /** Reloadra build-then-swap cserélődik; a tick több régió-szálról olvassa. */
    private volatile List<Achievement> achievements = List.of();

    public AchievementManager(final JavaPlugin plugin, final ConfigManager configManager,
                              final JobManager jobManager, final CurrencyManager currencyManager,
                              final ProfessionManager professionManager,
                              final FactionManager factionManager, final StatsManager statsManager,
                              final DailyQuestManager dailyQuestManager,
                              final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.currencyManager = currencyManager;
        this.professionManager = professionManager;
        this.factionManager = factionManager;
        this.statsManager = statsManager;
        this.dailyQuestManager = dailyQuestManager;
        this.messageManager = messageManager;
        reload();
    }

    public void reload() {
        final org.bukkit.configuration.ConfigurationSection section =
                configManager.getConfiguration() == null ? null
                        : configManager.getConfiguration().getConfigurationSection(
                                "achievements.definitions");
        if (section == null) {
            plugin.getLogger().warning(
                    "achievements.definitions hianyzik a configbol - nincs elereny.");
            achievements = List.of();
            return;
        }
        final List<Achievement> parsed = new java.util.ArrayList<>();
        for (final String rawId : section.getKeys(false)) {
            final org.bukkit.configuration.ConfigurationSection entry =
                    section.getConfigurationSection(rawId);
            if (entry == null) continue;
            final String id = rawId.toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_]+")) {
                plugin.getLogger().warning("achievements." + rawId
                        + ": az azonosito csak [a-z0-9_] karaktereket tartalmazhat - a sor kimarad.");
                continue;
            }
            final String metricName = entry.getString("metric", "");
            final Metric metric;
            try {
                metric = Metric.valueOf(metricName.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException exception) {
                plugin.getLogger().warning("achievements." + id + ": ismeretlen metric \""
                        + metricName + "\" - a sor kimarad.");
                continue;
            }
            final double threshold = entry.getDouble("threshold", -1.0D);
            if (threshold <= 0.0D) {
                plugin.getLogger().warning("achievements." + id
                        + ": a threshold hianyzik vagy nem pozitiv - a sor kimarad.");
                continue;
            }
            parsed.add(new Achievement(id, entry.getString("name", id),
                    entry.getString("description", ""), metric, threshold,
                    Math.max(0L, entry.getLong("reward", 0L))));
        }
        parsed.sort(java.util.Comparator.comparingDouble(Achievement::threshold));
        achievements = List.copyOf(parsed);
    }

    public boolean isEnabled() { return configManager.getBoolean("achievements.enabled", true); }
    public List<Achievement> getAchievements() { return achievements; }

    public void tick() {
        if (!isEnabled()) return;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> evaluate(player), null);
        }
    }

    /**
     * Evaluation is optimistic and non-blocking. Section-CAS makes unlock and reward receipt
     * reservation idempotent even when multiple metric events race the periodic tick.
     */
    public void evaluate(final Player player) {
        if (!isEnabled() || player == null) return;
        for (final Achievement achievement : achievements) {
            if (metricValue(player, achievement.metric()) < achievement.threshold()) continue;
            store.unlock(player.getUniqueId(), achievement.id())
                    .thenCompose(unlocked -> reserveReward(player, achievement))
                    .whenComplete((reserved, failure) -> {
                        if (failure != null) {
                            plugin.getLogger().severe("PlayerProfile achievement commit failed for "
                                    + player.getUniqueId() + '/' + achievement.id() + ": "
                                    + failure.getMessage());
                            return;
                        }
                        if (!Boolean.TRUE.equals(reserved)) return;
                        player.getScheduler().run(plugin,
                                task -> award(player, achievement), null);
                    });
        }
    }

    private java.util.concurrent.CompletionStage<Boolean> reserveReward(
            final Player player, final Achievement achievement) {
        // A zero reward still gets a durable receipt so announcements cannot repeat.
        return store.reserveReward(player.getUniqueId(),
                "achievement:" + achievement.id());
    }

    public boolean isEarned(final Player player, final String id) {
        return player != null && store.isUnlocked(player.getUniqueId(),
                id.toLowerCase(Locale.ROOT));
    }

    public double metricValue(final Player player, final Metric metric) {
        return switch (metric) {
            case CLASS_LEVEL -> jobManager.getPrimaryLevel(player);
            case WEALTH -> currencyManager.getTotalBalance(player);
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
        if (!player.isOnline()) {
            plugin.getLogger().warning("Achievement reward reserved while player went offline: "
                    + player.getUniqueId() + '/' + achievement.id());
            return;
        }
        final boolean xpReward = achievement.metric() == Metric.WEALTH;
        if (achievement.reward() > 0) {
            if (xpReward) {
                jobManager.addXpToJobV2(player,
                                (int) Math.min(Integer.MAX_VALUE, achievement.reward()),
                                "achievement:" + player.getUniqueId() + ':' + achievement.id())
                        .exceptionally(failure -> {
                            plugin.getLogger().warning("Achievement class XP failed: "
                                    + failure.getMessage());
                            return false;
                        });
            } else {
                final FactionType faction = factionManager.getEconomyFaction(player.getUniqueId());
                currencyManager.payOutTokens(player, CurrencyType.fromFactionType(faction),
                        achievement.reward());
            }
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0F, 1.0F);
        player.sendMessage(messageManager.getMessage(
                xpReward ? "achievement-earned-xp" : "achievement-earned",
                xpReward
                        ? "<gold>🏆 Elérés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} kaszt-XP)</gray></gold>"
                        : "<gold>🏆 Elérés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} valuta)</gray></gold>",
                Map.of("name", achievement.name(),
                        "reward", String.valueOf(achievement.reward()))));
    }
}
