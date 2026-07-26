package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-wide community goals with durable one-shot contribution receipts. */
public final class CommunityGoalManager implements PersistentStore {

    private record Completion(ConfigurationSection goal, boolean serverWide,
                              FactionType goalFaction, int count) {
    }

    private record AppliedContribution(boolean changed, List<Completion> completions) {
    }

    private static final int MAX_COMPLETIONS_PER_CONTRIBUTION = 3;
    private static final long RECEIPT_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(7L);
    private static final int MAX_RECEIPTS = 50_000;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final FactionTreasuryManager treasuryManager;
    private final MessageManager messageManager;
    private final SeasonManager seasonManager;
    private final File storageFile;
    private final Map<String, Long> progress = new ConcurrentHashMap<>();
    /** Source-event UUID -> durable claim time. */
    private final Map<String, Long> contributionReceipts = new ConcurrentHashMap<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    public CommunityGoalManager(final JavaPlugin plugin, final ConfigManager configManager,
                                final FactionManager factionManager,
                                final FactionTreasuryManager treasuryManager,
                                final MessageManager messageManager,
                                final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.treasuryManager = treasuryManager;
        this.messageManager = messageManager;
        this.seasonManager = seasonManager;
        this.storageFile = new File(plugin.getDataFolder(), "community-goals.yml");
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public synchronized void load() {
        progress.clear();
        contributionReceipts.clear();
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        final ConfigurationSection section = yaml.getConfigurationSection("progress");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final Object raw = section.get(key);
                if (!(raw instanceof Number number) || number.longValue() < 0L) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen community progress: " + key);
                }
                progress.put(key.toLowerCase(Locale.ROOT), number.longValue());
            }
        }
        final ConfigurationSection receipts = yaml.getConfigurationSection("contribution-receipts");
        if (receipts != null) {
            for (final String key : receipts.getKeys(false)) {
                try {
                    UUID.fromString(key);
                } catch (final IllegalArgumentException invalid) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen contribution receipt UUID: " + key);
                }
                final long claimedAt = receipts.getLong(key, 0L);
                if (claimedAt <= 0L) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen contribution receipt idő: " + key);
                }
                contributionReceipts.put(key, claimedAt);
            }
        }
        pruneReceipts(System.currentTimeMillis());
    }

    @Override
    public synchronized void save() {
        saveStrict();
    }

    /** Writes receipts and counters in the same atomic file image. */
    private boolean saveStrict() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<String, Long> entry : progress.entrySet()) {
            yaml.set("progress." + entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<String, Long> entry : contributionReceipts.entrySet()) {
            yaml.set("contribution-receipts." + entry.getKey(), entry.getValue());
        }
        try {
            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save community-goals.yml: "
                    + exception.getMessage());
            return false;
        }
    }

    private void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, TimeUnit.SECONDS);
        }
    }

    private ConfigurationSection goalsSection() {
        return configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("community-goals");
    }

    public synchronized void resetForNewSeason() {
        final Map<String, Long> oldProgress = new HashMap<>(progress);
        final Map<String, Long> oldReceipts = new HashMap<>(contributionReceipts);
        progress.clear();
        contributionReceipts.clear();
        if (!saveStrict()) {
            progress.putAll(oldProgress);
            contributionReceipts.putAll(oldReceipts);
        }
    }

    public long getProgress(final String goalId) {
        return progress.getOrDefault(goalId.toLowerCase(Locale.ROOT), 0L);
    }

    /** Ordinary non-economic event contribution; completion is durable before rewards run. */
    public synchronized void contribute(final Player player, final String objectiveType,
                                        final String materialOrEntity, final int amount) {
        final Map<String, Long> before = new HashMap<>(progress);
        final AppliedContribution applied = applyContribution(
                player, objectiveType, materialOrEntity, amount);
        if (!applied.changed()) {
            return;
        }
        if (applied.completions().isEmpty()) {
            requestSave();
            return;
        }
        if (!saveStrict()) {
            progress.clear();
            progress.putAll(before);
            return;
        }
        payCompletions(applied.completions());
    }

    /**
     * Claims one source event and applies every matching community counter in the same durable file
     * image. Returning false means the event was already claimed or persistence failed; callers must
     * not advance personal progress either.
     */
    public synchronized boolean contributeOnce(final Player player, final String objectiveType,
                                                final String materialOrEntity, final int amount,
                                                final UUID contributionId) {
        if (player == null || contributionId == null || amount <= 0) {
            return false;
        }
        final long now = System.currentTimeMillis();
        pruneReceipts(now);
        final String receipt = contributionId.toString();
        if (contributionReceipts.containsKey(receipt)) {
            return false;
        }

        final Map<String, Long> before = new HashMap<>(progress);
        contributionReceipts.put(receipt, now);
        final AppliedContribution applied = applyContribution(
                player, objectiveType, materialOrEntity, amount);
        if (!saveStrict()) {
            progress.clear();
            progress.putAll(before);
            contributionReceipts.remove(receipt);
            return false;
        }
        payCompletions(applied.completions());
        return true;
    }

    private AppliedContribution applyContribution(final Player player, final String objectiveType,
                                                  final String materialOrEntity, final int rawAmount) {
        final ConfigurationSection goals = goalsSection();
        if (goals == null || !configManager.getBoolean("community-goals.enabled", true)) {
            return new AppliedContribution(false, List.of());
        }
        final int amount = Math.max(1, rawAmount);
        final FactionType playerFaction = factionManager.getFaction(player.getUniqueId());
        final List<Completion> completions = new ArrayList<>();
        boolean changed = false;

        for (final String goalIdRaw : goals.getKeys(false)) {
            if ("enabled".equalsIgnoreCase(goalIdRaw)
                    || "season-points".equalsIgnoreCase(goalIdRaw)) {
                continue;
            }
            final ConfigurationSection goal = goals.getConfigurationSection(goalIdRaw);
            if (goal == null || !objectiveType.equalsIgnoreCase(
                    goal.getString("objective.type", ""))) {
                continue;
            }
            final String goalFactionName = goal.getString("faction", "ALL");
            final boolean serverWide = goalFactionName.isBlank()
                    || "ALL".equalsIgnoreCase(goalFactionName);
            final FactionType goalFaction = serverWide
                    ? null : FactionType.fromInput(goalFactionName);
            if (!serverWide && (goalFaction == null || playerFaction != goalFaction)) {
                continue;
            }
            final var materials = goal.getStringList("objective.materials");
            final String entityFilter = goal.getString("objective.entity-type", "");
            if (!materials.isEmpty()) {
                if (materialOrEntity == null || materials.stream().noneMatch(
                        name -> name.equalsIgnoreCase(materialOrEntity))) {
                    continue;
                }
            } else if (!entityFilter.isBlank() && (materialOrEntity == null
                    || !entityFilter.equalsIgnoreCase(materialOrEntity))) {
                continue;
            }

            changed = true;
            final String goalId = goalIdRaw.toLowerCase(Locale.ROOT);
            final long target = Math.max(1L, goal.getLong("objective.count", 1L));
            final long old = getProgress(goalId);
            long remaining = old > Long.MAX_VALUE - amount
                    ? Long.MAX_VALUE : old + amount;
            int completed = 0;
            while (remaining >= target && completed < MAX_COMPLETIONS_PER_CONTRIBUTION) {
                remaining -= target;
                completed++;
            }
            progress.put(goalId, remaining);
            if (completed > 0) {
                completions.add(new Completion(goal, serverWide, goalFaction, completed));
            }
        }
        return new AppliedContribution(changed, List.copyOf(completions));
    }

    private void payCompletions(final List<Completion> completions) {
        for (final Completion completion : completions) {
            for (int index = 0; index < completion.count(); index++) {
                completeGoal(completion.goal(), completion.serverWide(), completion.goalFaction());
            }
        }
    }

    private void pruneReceipts(final long now) {
        final long cutoff = now - RECEIPT_RETENTION_MILLIS;
        contributionReceipts.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        if (contributionReceipts.size() <= MAX_RECEIPTS) {
            return;
        }
        final int remove = contributionReceipts.size() - MAX_RECEIPTS;
        contributionReceipts.entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry::getValue))
                .limit(remove)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(contributionReceipts::remove);
    }

    private void completeGoal(final ConfigurationSection goal, final boolean serverWide,
                              final FactionType goalFaction) {
        final String displayName = goal.getString("display-name", "Közösségi cél");
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "community-goal-completed",
                "<gold>🏛 Közösségi cél teljesítve: <white>{goal}</white>! {who}</gold>",
                Map.of(
                        "goal", displayName,
                        "who", serverWide ? "Az egész szerver összefogott!"
                                : "A(z) " + goalFaction.getDisplayName() + " frakció diadala!"
                )
        ));

        final double treasuryReward = Math.max(0.0D,
                goal.getDouble("reward-treasury", 0.0D));
        if (treasuryReward > 0.0D) {
            if (serverWide) {
                for (final FactionType faction : FactionType.values()) {
                    treasuryManager.deposit(faction, treasuryReward);
                }
            } else {
                treasuryManager.deposit(goalFaction, treasuryReward);
            }
        }

        final int seasonPoints = Math.max(0,
                configManager.getInt("community-goals.season-points", 8));
        if (seasonPoints > 0) {
            if (serverWide) {
                for (final FactionType faction : FactionType.values()) {
                    seasonManager.addPoints(faction, seasonPoints, "community-server");
                }
            } else {
                seasonManager.addPoints(goalFaction, seasonPoints, "community");
            }
        }

        final int buffMinutes = Math.max(0, goal.getInt("reward-buff-minutes", 0));
        if (buffMinutes > 0) {
            final int durationTicks = buffMinutes * 60 * 20;
            for (final Player online : Bukkit.getOnlinePlayers()) {
                if (!serverWide && factionManager.getFaction(
                        online.getUniqueId()) != goalFaction) {
                    continue;
                }
                online.getScheduler().run(plugin, task -> {
                    online.addPotionEffect(new PotionEffect(
                            PotionEffectType.STRENGTH, durationTicks, 0, false, true, true));
                    online.addPotionEffect(new PotionEffect(
                            PotionEffectType.HERO_OF_THE_VILLAGE,
                            durationTicks, 0, false, true, true));
                }, null);
            }
        }
    }
}
