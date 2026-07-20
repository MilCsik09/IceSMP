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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-wide community goals (quest-package E): a shared counter every member
 * of a faction (or the whole server) contributes to — e.g. "the Red faction
 * together gathers 1000 iron". Goals are config-driven under
 * {@code community-goals.<id>} and their accumulated progress persists to
 * community-goals.yml. On completion the goal pays its {@code reward-treasury}
 * into the owning faction's vault (all four for an ALL goal), grants a short
 * buff to the online members it belongs to, broadcasts, and resets so the
 * community can chase it again.
 *
 * <p>Fed by the {@link hu.taliann.icesmp.listeners.QuestProgressListener} from
 * the same gameplay events as personal quests. Progress lives in a concurrent
 * map (contributions arrive from many region threads); completion effects hop
 * to each rewarded player's own scheduler (Folia).</p>
 */
public final class CommunityGoalManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final FactionTreasuryManager treasuryManager;
    private final MessageManager messageManager;
    private final SeasonManager seasonManager;
    private final File storageFile;
    private final Map<String, Long> progress = new ConcurrentHashMap<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    public CommunityGoalManager(final JavaPlugin plugin, final ConfigManager configManager,
                                final FactionManager factionManager, final FactionTreasuryManager treasuryManager,
                                final MessageManager messageManager, final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.treasuryManager = treasuryManager;
        this.messageManager = messageManager;
        this.seasonManager = seasonManager;
        this.storageFile = new File(plugin.getDataFolder(), "community-goals.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        progress.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        final ConfigurationSection section = yaml.getConfigurationSection("progress");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                progress.put(key.toLowerCase(Locale.ROOT), section.getLong(key));
            }
        }
    }

    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<String, Long> entry : progress.entrySet()) {
            yaml.set("progress." + entry.getKey(), entry.getValue());
        }
        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save community-goals.yml: " + exception.getMessage());
        }
    }

    /**
     * Debounced async flush for the hot contribution path: every mob kill / block break on an
     * active goal used to rewrite community-goals.yml synchronously on the region thread —
     * many contributions in a short window now coalesce into one write (CurrencyManager pattern).
     */
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

    public long getProgress(final String goalId) {
        return progress.getOrDefault(goalId.toLowerCase(Locale.ROOT), 0L);
    }

    /**
     * Records a contribution towards every matching community goal. Called from
     * the quest progress listener with the same event data as personal quests.
     *
     * @param player the contributing player
     * @param objectiveType the event's objective type (e.g. KILL_MOBS, COLLECT_ITEMS)
     * @param materialOrEntity the block/item/entity name for filtered goals, or null
     * @param amount how much to add (stack size for gathers, 1 for kills)
     */
    public void contribute(final Player player, final String objectiveType,
                           final String materialOrEntity, final int amount) {
        final ConfigurationSection goals = goalsSection();
        if (goals == null || !configManager.getBoolean("community-goals.enabled", true)) {
            return;
        }

        final FactionType playerFaction = factionManager.getFaction(player.getUniqueId());
        for (final String goalId : goals.getKeys(false)) {
            if ("enabled".equalsIgnoreCase(goalId)) {
                continue;
            }
            final ConfigurationSection goal = goals.getConfigurationSection(goalId);
            if (goal == null || !objectiveType.equalsIgnoreCase(goal.getString("objective.type", ""))) {
                continue;
            }

            // Faction filter: "ALL" (or blank) = server-wide, otherwise members only.
            final String goalFactionName = goal.getString("faction", "ALL");
            final boolean serverWide = goalFactionName.isBlank() || "ALL".equalsIgnoreCase(goalFactionName);
            final FactionType goalFaction = serverWide ? null : FactionType.fromInput(goalFactionName);
            if (!serverWide && playerFaction != goalFaction) {
                continue;
            }

            // Material/entity filter (for gather/break/kill-type goals).
            final var materials = goal.getStringList("objective.materials");
            final String entityFilter = goal.getString("objective.entity-type", "");
            if (!materials.isEmpty()) {
                if (materialOrEntity == null || materials.stream().noneMatch(name -> name.equalsIgnoreCase(materialOrEntity))) {
                    continue;
                }
            } else if (!entityFilter.isBlank()
                    && (materialOrEntity == null || !entityFilter.equalsIgnoreCase(materialOrEntity))) {
                continue;
            }

            recordContribution(goalId.toLowerCase(Locale.ROOT), goal, serverWide, goalFaction, Math.max(1, amount));
        }
    }

    private synchronized void recordContribution(final String goalId, final ConfigurationSection goal,
                                                 final boolean serverWide, final FactionType goalFaction,
                                                 final int amount) {
        final long target = Math.max(1L, goal.getLong("objective.count", 1L));
        final long current = getProgress(goalId) + amount;
        if (current < target) {
            progress.put(goalId, current);
            requestSave();
            return;
        }

        // Completed: reset the shared counter and pay out (the payout is worth an immediate flush).
        progress.put(goalId, 0L);
        save();
        completeGoal(goal, serverWide, goalFaction);
    }

    private void completeGoal(final ConfigurationSection goal, final boolean serverWide, final FactionType goalFaction) {
        final String displayName = goal.getString("display-name", "Közösségi cél");
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "community-goal-completed",
                "<gold>🏛 Közösségi cél teljesítve: <white>{goal}</white>! {who}</gold>",
                Map.of(
                        "goal", displayName,
                        "who", serverWide ? "Az egész szerver összefogott!" : "A(z) " + goalFaction.getDisplayName() + " frakció diadala!"
                )
        ));

        final double treasuryReward = Math.max(0.0D, goal.getDouble("reward-treasury", 0.0D));
        if (treasuryReward > 0.0D) {
            if (serverWide) {
                for (final FactionType faction : FactionType.values()) {
                    treasuryManager.deposit(faction, treasuryReward);
                }
            } else {
                treasuryManager.deposit(goalFaction, treasuryReward);
            }
        }

        // Aszimmetrikus liga: a közösségi cél liga-pontot is ér ("community" forrás —
        // a NEUTRAL identitás-útja, a súlymátrix szerint). Szerver-célnál mindenki kap.
        final int seasonPoints = Math.max(0, configManager.getInt("community-goals.season-points", 8));
        if (seasonPoints > 0) {
            if (serverWide) {
                // Szerver-cél: MINDENKI kap, de SÚLYOZATLANUL ("community-server" forrás,
                // nincs súly-sor → 1.0) — a NEUTRAL 1.5-ös bónusza csak a SAJÁT frakció-
                // céljaira jár, ne kapjon prémiumot mások közös munkájáért.
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
                if (!serverWide && factionManager.getFaction(online.getUniqueId()) != goalFaction) {
                    continue;
                }
                // Folia: apply the buff on each player's own region thread.
                online.getScheduler().run(plugin, task -> {
                    online.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, 0, false, true, true));
                    online.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, durationTicks, 0, false, true, true));
                }, null);
            }
        }
    }
}
