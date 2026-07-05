package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Config-driven quest framework (ideas.md "Quest-keretrendszer"): quest
 * definitions live under 'quests.<id>' in config.yml, player progress lives in
 * PDC. Quests gate content (the necromancer initiation), reward progression
 * (class trials) and offer the only way back from the dark pact (the penance
 * chain, whose final reward may cleanse all sins).
 *
 * Objective types: KILL_MOBS (count, optional entity-type + min-mob-level),
 * BREAK_BLOCKS (materials + count), CRAFT_ITEMS (materials + count),
 * CATCH_FISH (count), VISIT_TERRITORY (territory id), REACH_LEVEL (class level),
 * TALK_TO_NPC (npc name, via the FancyNpcs bridge), PARKOUR_TRIAL (course id,
 * via the ParkourManager finish hook).
 *
 * Accept requirements: requires-job, requires-faction, requires-level,
 * requires-quest (chains). Rewards: class-xp, currency (type + amount),
 * unlock-spell, cleanse-sins.
 */
public final class QuestManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final JobManager jobManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MetelytepoManager metelytepoManager;
    private final NamespacedKey activeQuestsKey;
    private final NamespacedKey completedQuestsKey;

    public QuestManager(final JavaPlugin plugin, final ConfigManager configManager,
                        final MessageManager messageManager, final JobManager jobManager,
                        final CurrencyManager currencyManager, final FactionManager factionManager,
                        final MetelytepoManager metelytepoManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.jobManager = jobManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.metelytepoManager = metelytepoManager;
        this.activeQuestsKey = new NamespacedKey(plugin, "quests_active");
        this.completedQuestsKey = new NamespacedKey(plugin, "quests_completed");
    }

    // ===== Definíciók =====

    public Set<String> getQuestIds() {
        if (configManager.getConfiguration() == null) {
            return Set.of();
        }

        final ConfigurationSection questsSection = configManager.getConfiguration().getConfigurationSection("quests");
        return questsSection == null ? Set.of() : questsSection.getKeys(false);
    }

    public ConfigurationSection getQuestSection(final String questId) {
        if (questId == null || configManager.getConfiguration() == null) {
            return null;
        }

        return configManager.getConfiguration().getConfigurationSection("quests." + questId.toLowerCase(Locale.ROOT));
    }

    public String getDisplayName(final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        return quest == null ? questId : quest.getString("display-name", questId);
    }

    public int getObjectiveCount(final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        return quest == null ? 1 : Math.max(1, quest.getInt("objective.count", 1));
    }

    // ===== Állapot (PDC) =====

    public List<String> getActiveQuests(final Player player) {
        return readCsv(player, activeQuestsKey);
    }

    public List<String> getCompletedQuests(final Player player) {
        return readCsv(player, completedQuestsKey);
    }

    public boolean isActive(final Player player, final String questId) {
        return questId != null && getActiveQuests(player).contains(questId.toLowerCase(Locale.ROOT));
    }

    public boolean hasCompleted(final Player player, final String questId) {
        return questId != null && getCompletedQuests(player).contains(questId.toLowerCase(Locale.ROOT));
    }

    public int getProgress(final Player player, final String questId) {
        return player.getPersistentDataContainer().getOrDefault(progressKey(questId), PersistentDataType.INTEGER, 0);
    }

    // ===== Felvétel / eldobás =====

    /**
     * Checks whether the player may accept a quest; returns the rejection
     * message key, or null when acceptance is allowed.
     *
     * @param player the player
     * @param questId the quest
     * @return null if acceptable, otherwise a message key suffix
     */
    public String getAcceptBlocker(final Player player, final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest == null) {
            return "quest-unknown";
        }

        if (isActive(player, questId)) {
            return "quest-already-active";
        }

        if (hasCompleted(player, questId)) {
            return "quest-already-completed";
        }

        final String requiredJobId = quest.getString("requires-job");
        if (requiredJobId != null && !requiredJobId.isBlank()) {
            final JobType requiredJob = JobType.fromId(requiredJobId);
            if (requiredJob == null || jobManager.getPrimaryJob(player) != requiredJob) {
                return "quest-requires-job";
            }
        }

        final String requiredFactionName = quest.getString("requires-faction");
        if (requiredFactionName != null && !requiredFactionName.isBlank()
                && factionManager.getFaction(player.getUniqueId()) != FactionType.fromInput(requiredFactionName)) {
            return "quest-requires-faction";
        }

        final int requiredLevel = quest.getInt("requires-level", 0);
        if (requiredLevel > 0 && jobManager.getPrimaryLevel(player) < requiredLevel) {
            return "quest-requires-level";
        }

        final String requiredQuest = quest.getString("requires-quest");
        if (requiredQuest != null && !requiredQuest.isBlank() && !hasCompleted(player, requiredQuest)) {
            return "quest-requires-quest";
        }

        return null;
    }

    public boolean accept(final Player player, final String questId) {
        if (getAcceptBlocker(player, questId) != null) {
            return false;
        }

        final List<String> active = new ArrayList<>(getActiveQuests(player));
        active.add(questId.toLowerCase(Locale.ROOT));
        writeCsv(player, activeQuestsKey, active);
        player.getPersistentDataContainer().set(progressKey(questId), PersistentDataType.INTEGER, 0);

        // REACH_LEVEL quests may already be satisfied at acceptance.
        handleLevelChange(player);
        return true;
    }

    public boolean abandon(final Player player, final String questId) {
        if (!isActive(player, questId)) {
            return false;
        }

        final List<String> active = new ArrayList<>(getActiveQuests(player));
        active.remove(questId.toLowerCase(Locale.ROOT));
        writeCsv(player, activeQuestsKey, active);
        player.getPersistentDataContainer().remove(progressKey(questId));
        return true;
    }

    // ===== Haladás-útvonalak (a listenerek hívják) =====

    public void handleKill(final Player player, final EntityType entityType, final int mobLevel) {
        forEachActive(player, "KILL_MOBS", (questId, quest) -> {
            final String requiredEntity = quest.getString("objective.entity-type");
            if (requiredEntity != null && !requiredEntity.isBlank()
                    && !requiredEntity.equalsIgnoreCase(entityType.name())) {
                return false;
            }

            final int minMobLevel = quest.getInt("objective.min-mob-level", 0);
            return mobLevel >= minMobLevel;
        });
    }

    public void handleBlockBreak(final Player player, final Material material) {
        forEachActive(player, "BREAK_BLOCKS", (questId, quest) ->
                quest.getStringList("objective.materials").stream()
                        .anyMatch(name -> name.equalsIgnoreCase(material.name())));
    }

    public void handleCraft(final Player player, final Material material) {
        forEachActive(player, "CRAFT_ITEMS", (questId, quest) ->
                quest.getStringList("objective.materials").stream()
                        .anyMatch(name -> name.equalsIgnoreCase(material.name())));
    }

    public void handleFish(final Player player) {
        forEachActive(player, "CATCH_FISH", (questId, quest) -> true);
    }

    public void handleTerritoryEnter(final Player player, final String territoryId) {
        forEachActive(player, "VISIT_TERRITORY", (questId, quest) ->
                territoryId != null && territoryId.equalsIgnoreCase(quest.getString("objective.territory", "")));
    }

    /**
     * Progresses TALK_TO_NPC quests when the player interacts with a named NPC.
     * Fired by the reflective FancyNpcs bridge on the player's own region thread.
     *
     * @param player the interacting player
     * @param npcName the NPC's internal (FancyNpcs) name
     */
    public void handleNpcInteract(final Player player, final String npcName) {
        forEachActive(player, "TALK_TO_NPC", (questId, quest) ->
                npcName != null && npcName.equalsIgnoreCase(quest.getString("objective.npc", "")));
    }

    /**
     * Progresses PARKOUR_TRIAL quests when the player finishes a timed parkour
     * course. Wired into the ParkourManager finish hook.
     *
     * @param player the finishing player
     * @param courseId the completed course id
     */
    public void handleParkourFinish(final Player player, final String courseId) {
        forEachActive(player, "PARKOUR_TRIAL", (questId, quest) ->
                courseId != null && courseId.equalsIgnoreCase(quest.getString("objective.course", "")));
    }

    /**
     * Re-checks every active REACH_LEVEL quest against the player's primary
     * class level. Wired into the JobManager XP-change hook.
     *
     * @param player the player whose level changed
     */
    public void handleLevelChange(final Player player) {
        for (final String questId : List.copyOf(getActiveQuests(player))) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null || !"REACH_LEVEL".equalsIgnoreCase(quest.getString("objective.type", ""))) {
                continue;
            }

            final int targetLevel = Math.max(1, quest.getInt("objective.level", 1));
            if (jobManager.getPrimaryLevel(player) >= targetLevel) {
                complete(player, questId);
            }
        }
    }

    private interface ObjectiveMatcher {
        boolean matches(String questId, ConfigurationSection quest);
    }

    private void forEachActive(final Player player, final String objectiveType, final ObjectiveMatcher matcher) {
        for (final String questId : List.copyOf(getActiveQuests(player))) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null || !objectiveType.equalsIgnoreCase(quest.getString("objective.type", ""))) {
                continue;
            }

            if (!matcher.matches(questId, quest)) {
                continue;
            }

            final int progress = getProgress(player, questId) + 1;
            final int target = Math.max(1, quest.getInt("objective.count", 1));
            if (progress >= target) {
                complete(player, questId);
                continue;
            }

            player.getPersistentDataContainer().set(progressKey(questId), PersistentDataType.INTEGER, progress);
            player.sendActionBar(messageManager.getMessage(
                    "quest.progress",
                    "<gray>{quest}: <gold>{progress}</gold>/<gold>{target}</gold></gray>",
                    Map.of(
                            "quest", getDisplayName(questId),
                            "progress", String.valueOf(progress),
                            "target", String.valueOf(target)
                    )
            ));
        }
    }

    // ===== Teljesítés és jutalmak =====

    /**
     * Completes a quest: moves it to the completed list, wipes its progress
     * and pays out every configured reward.
     *
     * @param player the player
     * @param questId the quest to complete
     */
    public void complete(final Player player, final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest == null) {
            return;
        }

        final String normalizedId = questId.toLowerCase(Locale.ROOT);
        final List<String> active = new ArrayList<>(getActiveQuests(player));
        active.remove(normalizedId);
        writeCsv(player, activeQuestsKey, active);

        final List<String> completed = new ArrayList<>(getCompletedQuests(player));
        if (!completed.contains(normalizedId)) {
            completed.add(normalizedId);
        }
        writeCsv(player, completedQuestsKey, completed);
        player.getPersistentDataContainer().remove(progressKey(questId));

        applyRewards(player, quest);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        player.sendMessage(messageManager.getMessage(
                "quest.completed",
                "<gold>✔ Küldetés teljesítve: <white>{quest}</white>!</gold>",
                Map.of("quest", getDisplayName(questId))
        ));
    }

    private void applyRewards(final Player player, final ConfigurationSection quest) {
        final int classXp = quest.getInt("rewards.class-xp", 0);
        if (classXp > 0 && jobManager.hasPrimaryJob(player)) {
            jobManager.addXpToJob(player, classXp);
        }

        final ConfigurationSection currencyReward = quest.getConfigurationSection("rewards.currency");
        if (currencyReward != null) {
            final CurrencyType currencyType = CurrencyType.fromInput(currencyReward.getString("type", ""));
            final double amount = currencyReward.getDouble("amount", 0.0D);
            if (currencyType != null && amount > 0.0D) {
                currencyManager.addToBalance(player.getUniqueId(), currencyType, amount);
            }
        }

        final String unlockSpell = quest.getString("rewards.unlock-spell");
        if (unlockSpell != null && !unlockSpell.isBlank()) {
            jobManager.unlockSpell(player, unlockSpell);
        }

        // The penance chain's final mercy: even the dark pact can be broken.
        if (quest.getBoolean("rewards.cleanse-sins", false)) {
            metelytepoManager.breakDarkPact(player);
        }
    }

    // ===== PDC segédek =====

    private NamespacedKey progressKey(final String questId) {
        final String sanitized = questId == null
                ? "unknown"
                : questId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return new NamespacedKey(plugin, "quest_progress_" + sanitized);
    }

    private List<String> readCsv(final Player player, final NamespacedKey key) {
        final String raw = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        final Set<String> unique = new LinkedHashSet<>();
        for (final String token : raw.split(",")) {
            final String id = token.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) {
                unique.add(id);
            }
        }
        return List.copyOf(unique);
    }

    private void writeCsv(final Player player, final NamespacedKey key, final List<String> values) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (values == null || values.isEmpty()) {
            pdc.remove(key);
            return;
        }

        pdc.set(key, PersistentDataType.STRING, String.join(",", new LinkedHashSet<>(values)));
    }
}
