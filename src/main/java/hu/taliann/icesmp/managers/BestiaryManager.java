package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** PlayerProfile-backed bestiary and milestone receipt authority. */
public final class BestiaryManager {

    public enum Category { MOBS, RECIPES, TERRITORIES, BOSSES }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final PlayerProfileAchievementStore store = new PlayerProfileAchievementStore();

    public BestiaryManager(final JavaPlugin plugin, final ConfigManager configManager,
                           final CurrencyManager currencyManager,
                           final FactionManager factionManager,
                           final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
    }

    public JavaPlugin plugin() { return plugin; }
    public boolean isEnabled() { return configManager.getBoolean("bestiary.enabled", true); }

    public Set<String> entries(final Player player, final Category category) {
        if (player == null || category == null) return Set.of();
        return store.bestiaryEntries(player.getUniqueId(), category.name().toLowerCase(Locale.ROOT));
    }

    public int count(final Player player, final Category category) {
        return entries(player, category).size();
    }

    /**
     * Records a first entry with section CAS. Callers may ignore the returned stage; milestone
     * reward and UI effects are scheduled only after the durable commit succeeds.
     */
    public CompletionStage<Boolean> record(final Player player, final Category category,
                                           final String id) {
        if (!isEnabled() || player == null || category == null || id == null || id.isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        return store.recordBestiary(player.getUniqueId(),
                        category.name().toLowerCase(Locale.ROOT),
                        id.toLowerCase(Locale.ROOT))
                .thenApply(result -> {
                    if (result.created()) {
                        player.getScheduler().run(plugin,
                                task -> checkMilestone(player, category, result.categoryCount()), null);
                    }
                    return result.created();
                }).exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile bestiary commit failed for "
                            + player.getUniqueId() + "/" + category + "/" + id + ": "
                            + failure.getMessage());
                    return false;
                });
    }

    /** Milestone payout is guarded by a durable per-threshold receipt. */
    private void checkMilestone(final Player player, final Category category, final int size) {
        final String base = "bestiary.milestones." + category.name().toLowerCase(Locale.ROOT);
        for (final String row : configManager.getStringList(base)) {
            final String[] parts = row.split(":");
            final int threshold;
            final long reward;
            try {
                if (parts.length < 2) continue;
                threshold = Integer.parseInt(parts[0].trim());
                reward = Long.parseLong(parts[1].trim());
            } catch (final NumberFormatException ignored) {
                continue;
            }
            if (threshold != size) continue;
            final String receipt = "bestiary:" + category.name().toLowerCase(Locale.ROOT)
                    + ':' + threshold;
            store.reserveReward(player.getUniqueId(), receipt)
                    .whenComplete((reserved, failure) -> {
                        if (failure != null) {
                            plugin.getLogger().severe("PlayerProfile bestiary reward receipt failed for "
                                    + player.getUniqueId() + '/' + receipt + ": "
                                    + failure.getMessage());
                            return;
                        }
                        if (!Boolean.TRUE.equals(reserved)) return;
                        player.getScheduler().run(plugin, task ->
                                deliverMilestone(player, category, size, reward, parts), null);
                    });
        }
    }

    private void deliverMilestone(final Player player, final Category category,
                                  final int size, final long reward,
                                  final String[] parts) {
        if (!player.isOnline()) {
            plugin.getLogger().warning("Bestiary reward reserved while player went offline: "
                    + player.getUniqueId() + '/' + category + '/' + size);
            return;
        }
        if (reward > 0) {
            currencyManager.payOutTokens(player,
                    CurrencyType.fromFactionType(
                            factionManager.getEconomyFaction(player.getUniqueId())), reward);
        }
        player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.1F);
        player.sendMessage(messageManager.getMessage("bestiary-milestone",
                "<gold>📜 Bestiárium-mérföldkő: <white>{count}</white> bejegyzés a(z) <white>{category}</white> lajstromban! Jutalom: <white>{reward} veret</white> a kezedbe.</gold>",
                Map.of("count", String.valueOf(size), "category", categoryName(category),
                        "reward", String.valueOf(reward))));
        if (parts.length >= 3 && "broadcast".equalsIgnoreCase(parts[2].trim())) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "bestiary-milestone-broadcast",
                    "<gold>📜 <white>{player}</white> lajstroma <white>{count}</white> bejegyzésre hízott a(z) <white>{category}</white> fejezetben — a krónikások főt hajtanak!</gold>",
                    Map.of("player", player.getName(), "count", String.valueOf(size),
                            "category", categoryName(category))));
        }
    }

    public static String categoryName(final Category category) {
        return switch (category) {
            case MOBS -> "Szörnyek";
            case RECIPES -> "Receptek";
            case TERRITORIES -> "Territóriumok";
            case BOSSES -> "Világbossok";
        };
    }
}
