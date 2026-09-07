package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.integrity.*;
import java.util.UUID;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore.PendingReward;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore.RewardKind;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore.RewardReservation;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
    private record DeliveryKey(UUID player, String receipt) { }
    /** Bounded work admission only; canonical pending state and receipts remain in PlayerProfile. */
    private final java.util.Set<DeliveryKey> delivering = new java.util.HashSet<>();

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
            runOnOwner(player.getUniqueId(), this::evaluate, () -> { });
        }
    }

    /**
     * Unlock is durable first. Existing pending rewards are replayed independently of the current
     * metric (important for WEALTH after spending money) and independently of later config changes.
     */
    public void evaluate(final Player player) {
        if (!isEnabled() || player == null || !Bukkit.isOwnedByCurrentRegion(player) || !player.isOnline()) return;
        final UUID playerId = player.getUniqueId();
        try {
            for (final PendingReward pending : store.pendingRewards(player.getUniqueId())) {
                deliverPending(player, pending, achievementForReceipt(pending.receiptId()));
            }
        } catch (final RuntimeException notReady) {
            return; // PlayerProfile/class session has not reached a readable state yet.
        }

        final RewardContext reward;
        try { reward = BukkitRewardSources.entity(RewardChannel.ACHIEVEMENT, player).forRecipient(playerId); }
        catch (RuntimeException | LinkageError unavailable) { return; }
        for (final Achievement achievement : achievements) {
            final boolean alreadyUnlocked;
            try {
                alreadyUnlocked = store.isUnlocked(player.getUniqueId(), achievement.id());
            } catch (final RuntimeException notReady) {
                return;
            }
            if (!alreadyUnlocked && metricValue(player, achievement.metric()) < achievement.threshold()) {
                continue;
            }
            final PendingReward payload = rewardPayload(player, achievement);
            final CompletionStage<Boolean> unlock = alreadyUnlocked
                    ? CompletableFuture.completedFuture(true)
                    : store.unlock(playerId, achievement.id(), reward);
            unlock.thenCompose(ignored -> ensureReservation(playerId, payload, reward))
                    .thenAccept(reservation -> {
                        if (reservation != null && reservation.pending()) {
                            runOnOwner(playerId, owned -> deliverPending(owned, reservation.reward(), Optional.of(achievement)), () -> { });
                        }
                    }).exceptionally(failure -> {
                        if (rewardDenied(failure)) return null;
                        plugin.getLogger().severe("PlayerProfile achievement reservation failed for "
                                + playerId + '/' + achievement.id() + ": "
                                + rootMessage(failure));
                        return null;
                    });
        }
    }

    private CompletionStage<RewardReservation> ensureReservation(
            final UUID playerId, final PendingReward payload, final RewardContext reward) {
        final String receiptId = payload.receiptId();
        final Optional<PendingReward> existing = store.pendingReward(playerId, receiptId);
        if (existing.isPresent()) {
            return CompletableFuture.completedFuture(new RewardReservation(
                    PlayerProfileAchievementStore.RewardState.PENDING,
                    existing.orElseThrow(), false));
        }
        if (store.rewardSettled(playerId, receiptId)) {
            return CompletableFuture.completedFuture(null);
        }
        return store.reserveReward(playerId, payload, reward);
    }

    private PendingReward rewardPayload(final Player player, final Achievement achievement) {
        final String receipt = receiptId(achievement.id());
        if (achievement.reward() <= 0L) {
            return new PendingReward(receipt, RewardKind.NONE, 0L, "");
        }
        if (achievement.metric() == Metric.WEALTH) {
            return new PendingReward(receipt, RewardKind.CLASS_XP,
                    Math.min(Integer.MAX_VALUE, achievement.reward()), "");
        }
        final FactionType faction = factionManager.getEconomyFaction(player.getUniqueId());
        final CurrencyType currency = CurrencyType.fromFactionType(faction);
        return new PendingReward(receipt, RewardKind.CURRENCY,
                achievement.reward(), currency.name().toLowerCase(Locale.ROOT));
    }

    /**
     * External delivery is idempotent and receipt-bound. XP STALE_SESSION/RUNTIME_EFFECT_FAILED
     * deliberately remains PENDING; reconnect replays the same Profile operation ID, observes
     * NO_CHANGE after an already-durable commit, then settles the achievement receipt exactly once.
     */
    private void deliverPending(final Player player, final PendingReward pending,
                                final Optional<Achievement> achievement) {
        final UUID playerId = player.getUniqueId();
        settlePendingReward(playerId, pending).whenComplete((settled, failure) -> {
            if (failure != null) {
                plugin.getLogger().warning("Achievement reward remains pending for "
                        + playerId + '/' + pending.receiptId() + ": " + rootMessage(failure));
                return;
            }
            if (Boolean.TRUE.equals(settled)) runOnOwner(playerId, owned -> announce(owned, pending, achievement), () -> { });
        });
    }

    /** Shared Bestiary/reconnect route. No caller can substitute a different durable reward payload. */
    public CompletionStage<Boolean> settlePendingReward(final UUID playerId, final PendingReward expected) {
        java.util.Objects.requireNonNull(playerId); java.util.Objects.requireNonNull(expected);
        final DeliveryKey key = new DeliveryKey(playerId, expected.receiptId());
        synchronized (delivering) {
            if (delivering.size() >= 128 || !delivering.add(key)) return CompletableFuture.completedFuture(false);
        }
        final CompletionStage<Boolean> delivery;
        try {
            delivery = store.deliverReward(playerId, expected, admitted -> switch (admitted.kind()) {
                case NONE -> CompletableFuture.completedFuture(true);
                case CURRENCY -> deliverCurrency(playerId, admitted);
                case CLASS_XP -> {
                    final var result = new CompletableFuture<Boolean>();
                    runOnOwner(playerId, owned -> {
                        try {
                            deliverClassXp(owned, admitted).whenComplete((done, failure) -> {
                                if (failure != null) result.completeExceptionally(failure); else result.complete(done);
                            });
                        } catch (RuntimeException | LinkageError unavailable) { result.completeExceptionally(unavailable); }
                    }, () -> result.complete(false));
                    yield result;
                }
            });
        } catch (RuntimeException | LinkageError unavailable) {
            synchronized (delivering) { delivering.remove(key); }
            return CompletableFuture.failedFuture(unavailable);
        }
        return delivery.whenComplete((ignored, failure) -> { synchronized (delivering) { delivering.remove(key); } });
    }

    private void runOnOwner(final UUID playerId, final java.util.function.Consumer<Player> action, final Runnable unavailable) {
        final Player handle = Bukkit.getPlayer(playerId);
        if (handle == null) { unavailable.run(); return; }
        if (handle.getScheduler().run(plugin, task -> {
            final Player owned = Bukkit.getPlayer(playerId);
            if (owned == null || !Bukkit.isOwnedByCurrentRegion(owned) || !owned.isOnline()) { unavailable.run(); return; }
            action.accept(owned);
        }, unavailable) == null) unavailable.run();
    }

    private static boolean rewardDenied(Throwable failure) {
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure instanceof RewardEligibilityDeniedException;
    }

    private CompletionStage<Boolean> deliverClassXp(final Player player,
                                                     final PendingReward pending) {
        if (!jobManager.hasPrimaryJob(player)) {
            return CompletableFuture.completedFuture(false);
        }
        return jobManager.addXpToJobResultV2(player, (int) pending.amount(),
                        "achievement-xp:" + pending.receiptId())
                .thenApply(result -> result.status() == ProfileMutationResult.Status.COMMITTED
                        || result.status() == ProfileMutationResult.Status.NO_CHANGE);
    }

    private CompletionStage<Boolean> deliverCurrency(final UUID playerId,
                                                      final PendingReward pending) {
        final CurrencyType currency = CurrencyType.fromInput(pending.currencyId());
        if (currency == null || pending.amount() <= 0L) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("invalid pending achievement currency reward"));
        }
        // PlayerProfileEconomyStore credits balance + operation receipt in one ECONOMY CAS.
        // Run the synchronous durability boundary off the region thread; replay is a no-op.
        return CompletableFuture.supplyAsync(() -> {
            currencyManager.creditOnceDurably(playerId, currency,
                    pending.amount(), "achievement-currency:" + pending.receiptId());
            return true;
        });
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

    private Optional<Achievement> achievementForReceipt(final String receipt) {
        final String prefix = "achievement:";
        if (receipt == null || !receipt.startsWith(prefix)) return Optional.empty();
        final String id = receipt.substring(prefix.length());
        return achievements.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    private void announce(final Player player, final PendingReward pending,
                          final Optional<Achievement> achievement) {
        final String name = achievement.map(Achievement::name)
                .orElseGet(() -> pending.receiptId().replaceFirst("^achievement:", ""));
        final boolean xpReward = pending.kind() == RewardKind.CLASS_XP;
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0F, 1.0F);
        player.sendMessage(messageManager.getMessage(
                xpReward ? "achievement-earned-xp" : "achievement-earned",
                xpReward
                        ? "<gold>🏆 Elérés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} kaszt-XP)</gray></gold>"
                        : "<gold>🏆 Elérés teljesítve: <yellow>{name}</yellow> <gray>(+{reward} valuta)</gray></gold>",
                Map.of("name", name, "reward", String.valueOf(pending.amount()))));
    }

    private static String receiptId(final String achievementId) {
        return "achievement:" + achievementId.toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
