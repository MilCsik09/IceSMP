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
 * Achievements: milestone goals checked against tracked player
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
    /** Reloadra build-then-swap cserélődik; a tick több régió-szálról olvassa. */
    private volatile List<Achievement> achievements = List.of();

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
        reload();
    }

    /**
     * Az elérés-lista újraépítése a configból ({@code achievements.definitions.*}).
     * A tick játékosonként fut, ezért a listát NEM olvassuk minden hívásnál — a
     * {@code /icesmp reload} hívja újra (a Relic/MobScaling minta szerint).
     */
    public void reload() {
        final org.bukkit.configuration.ConfigurationSection section =
                configManager.getConfiguration() == null ? null
                        : configManager.getConfiguration().getConfigurationSection("achievements.definitions");
        if (section == null) {
            plugin.getLogger().warning("achievements.definitions hianyzik a configbol - nincs elereny.");
            this.achievements = List.of();
            return;
        }
        final List<Achievement> parsed = new java.util.ArrayList<>();
        for (final String rawId : section.getKeys(false)) {
            final org.bukkit.configuration.ConfigurationSection entry = section.getConfigurationSection(rawId);
            if (entry == null) {
                continue;
            }
            // KANONIKUS id. A megszerzett-lista PDC-ből visszaolvasva kisbetűsít, a tárolás viszont
            // a config eredeti casingjét használta: egy „RichOne" id mentés után „richone" lett, a
            // contains("RichOne") hamis maradt, és a periodikus tick MINDEN körben újra kifizette a
            // jutalmat. A vessző pedig szét is hasította volna a CSV-t.
            final String id = rawId.toLowerCase(java.util.Locale.ROOT);
            if (!id.matches("[a-z0-9_]+")) {
                plugin.getLogger().warning("achievements." + rawId + ": az azonosito csak [a-z0-9_] "
                        + "karaktereket tartalmazhat - a sor kimarad (kulonben ismetelt jutalmat adna).");
                continue;
            }
            final String metricName = entry.getString("metric", "");
            final Metric metric;
            try {
                metric = Metric.valueOf(metricName.toUpperCase(java.util.Locale.ROOT));
            } catch (final IllegalArgumentException exception) {
                plugin.getLogger().warning("achievements." + id + ": ismeretlen metric \"" + metricName
                        + "\" - a sor kimarad. Ervenyes: CLASS_LEVEL, WEALTH, RAID_KILLS, PROFESSION_LEVEL, DAILY_STREAK.");
                continue;
            }
            final double threshold = entry.getDouble("threshold", -1.0D);
            if (threshold <= 0.0D) {
                plugin.getLogger().warning("achievements." + id + ": a threshold hianyzik vagy nem pozitiv - a sor kimarad.");
                continue;
            }
            parsed.add(new Achievement(id,
                    entry.getString("name", id),
                    entry.getString("description", ""),
                    metric,
                    threshold,
                    Math.max(0L, entry.getLong("reward", 0L))));
        }
        // Küszöb szerint növekvő: a HUD/GUI így a következő mérföldkövet mutatja elöl.
        parsed.sort(java.util.Comparator.comparingDouble(Achievement::threshold));
        this.achievements = List.copyOf(parsed);
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
            // Vagyon = az ÖSSZES valuta-egyenleg összege (a default-valutás olvasás a
            // RED/BLUE/DARK játékosokat kizárta volna a vagyon-elérésekből).
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
        // A VAGYON-elérések kaszt-XP-t fizetnek, NEM veretet: az egyenleg-küszöb
        // kölcsönkért tokenekkel (alt-számláról) átléphető, és pénz-jutalommal ez
        // ingyen-pénz-nyomda lenne (befizet → jutalom → visszaadja). Az XP nem
        // átruházható, így a kör értelmetlen; a többi metrika veretben fizet tovább.
        final boolean xpReward = achievement.metric() == Metric.WEALTH;
        if (achievement.reward() > 0) {
            if (xpReward) {
                jobManager.addXpToJob(player, (int) Math.min(Integer.MAX_VALUE, achievement.reward()));
            } else {
                final FactionType faction = factionManager.getEconomyFaction(player.getUniqueId());
                currencyManager.payOutTokens(player, CurrencyType.fromFactionType(faction), achievement.reward());
            }
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        player.sendMessage(messageManager.getMessage(
                xpReward ? "achievement-earned-xp" : "achievement-earned",
                xpReward
                        ? "<gold>🏆 Elérés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} kaszt-XP)</gray></gold>"
                        : "<gold>🏆 Elérés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} valuta)</gray></gold>",
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
