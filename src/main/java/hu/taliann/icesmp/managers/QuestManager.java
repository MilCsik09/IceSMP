package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
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
 * BREAK_BLOCKS / PLACE_BLOCKS / CRAFT_ITEMS / COLLECT_ITEMS / CONSUME_ITEMS
 * (materials + count), CATCH_FISH / ENCHANT_ITEMS / KILL_PLAYERS (count),
 * BREED_ANIMALS (count, optional entity-type), VISIT_TERRITORY (territory id),
 * REACH_LEVEL (class level), TALK_TO_NPC (npc name, via the FancyNpcs bridge),
 * DELIVER_ITEMS (npc + materials + count — the NPC takes the goods),
 * PARKOUR_TRIAL (course id, via the ParkourManager finish hook).
 * Optional dialogue block: dialogue.speaker + dialogue.give / dialogue.complete
 * lines are spoken by the giver / target NPC.
 *
 * Accept requirements: requires-job, requires-faction, requires-level,
 * requires-quest (chains). Rewards: class-xp, currency (type + amount),
 * unlock-spell, cleanse-sins.
 *
 * <p>Besides the config-shipped quests, admins can build quests in-game
 * without touching code or files (<code>/quest admin create|set|delete</code>);
 * those live in custom-quests.yml under the plugin data folder (same schema)
 * and are merged into every lookup. On an id collision the config quest wins,
 * so shipped content can't be shadowed.</p>
 */
public final class QuestManager implements PersistentStore {

    /** Objective types the framework understands (admin create validates against this). */
    public static final Set<String> OBJECTIVE_TYPES = Set.of(
            "KILL_MOBS", "BREAK_BLOCKS", "CRAFT_ITEMS", "CATCH_FISH",
            "VISIT_TERRITORY", "REACH_LEVEL", "TALK_TO_NPC", "PARKOUR_TRIAL",
            "PLACE_BLOCKS", "COLLECT_ITEMS", "KILL_PLAYERS", "DELIVER_ITEMS",
            "BREED_ANIMALS", "ENCHANT_ITEMS", "CONSUME_ITEMS",
            "SMELT_ITEMS", "TAME_ANIMALS", "TRADE_WITH_VILLAGER",
            "EXPLORE_BIOME", "WIN_RAID", "KILL_WORLDBOSS");

    /** Fields the admin editor may set, in tab-complete order. */
    public static final List<String> EDITABLE_FIELDS = List.of(
            "display-name", "description", "giver-npc",
            "repeatable", "cooldown-hours", "auto-start-territory",
            "requires-job", "requires-faction", "requires-level", "requires-quest",
            "objective.type", "objective.count", "objective.entity-type",
            "objective.min-mob-level", "objective.materials", "objective.territory",
            "objective.level", "objective.npc", "objective.course", "objective.biome",
            "rewards.class-xp", "rewards.currency.type", "rewards.currency.amount",
            "rewards.items", "rewards.unlock-spell", "rewards.cleanse-sins",
            "dialogue.speaker", "dialogue.give", "dialogue.complete");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final JobManager jobManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MetelytepoManager metelytepoManager;
    private final NamespacedKey activeQuestsKey;
    private final NamespacedKey completedQuestsKey;
    private final File customQuestsFile;
    private volatile YamlConfiguration customQuests = new YamlConfiguration();

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
        this.customQuestsFile = new File(plugin.getDataFolder(), "custom-quests.yml");
        plugin.getDataFolder().mkdirs();
    }

    // ===== Admin-készítette questek (custom-quests.yml) =====

    @Override
    public void load() {
        if (!customQuestsFile.exists()) {
            customQuests = new YamlConfiguration();
            return;
        }

        try {
            customQuests = YamlConfiguration.loadConfiguration(customQuestsFile);
            plugin.getLogger().info("Loaded " + getCustomQuestIds().size() + " admin-created quest(s).");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load custom-quests.yml: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void save() {
        try {
            YamlStore.saveAtomic(customQuestsFile, customQuests);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save custom-quests.yml: " + exception.getMessage());
        }
    }

    public Set<String> getCustomQuestIds() {
        final ConfigurationSection section = customQuests.getConfigurationSection("quests");
        return section == null ? Set.of() : section.getKeys(false);
    }

    public boolean isCustomQuest(final String questId) {
        return questId != null && customQuests.isConfigurationSection("quests." + questId.toLowerCase(Locale.ROOT));
    }

    private boolean isConfigQuest(final String questId) {
        return configManager.getConfiguration() != null
                && configManager.getConfiguration().isConfigurationSection("quests." + questId.toLowerCase(Locale.ROOT));
    }

    /**
     * Creates an admin-authored quest skeleton (id + objective + count +
     * display name); details are filled in with the set editor.
     *
     * @param id the quest id (lowercase letters, digits, underscore)
     * @param objectiveType one of {@link #OBJECTIVE_TYPES}
     * @param count the objective count
     * @param displayName the player-facing name
     * @return null on success, otherwise an error message key
     */
    public synchronized String createCustomQuest(final String id, final String objectiveType,
                                                 final int count, final String displayName) {
        if (id == null || id.isBlank() || !id.toLowerCase(Locale.ROOT).matches("[a-z0-9_]+")) {
            return "quest-admin-bad-id";
        }

        final String normalizedId = id.toLowerCase(Locale.ROOT);
        if (isConfigQuest(normalizedId) || isCustomQuest(normalizedId)) {
            return "quest-admin-exists";
        }

        if (objectiveType == null || !OBJECTIVE_TYPES.contains(objectiveType.toUpperCase(Locale.ROOT))) {
            return "quest-admin-bad-objective";
        }

        if (count < 1) {
            return "quest-admin-bad-count";
        }

        final String base = "quests." + normalizedId;
        customQuests.set(base + ".display-name", displayName == null || displayName.isBlank() ? normalizedId : displayName);
        customQuests.set(base + ".objective.type", objectiveType.toUpperCase(Locale.ROOT));
        customQuests.set(base + ".objective.count", count);
        save();
        return null;
    }

    /**
     * Sets one field of an admin-authored quest. Values are parsed by field:
     * numbers for counts/levels/XP, true/false for flags, comma-separated
     * lists for materials, plain text otherwise.
     *
     * @param questId the custom quest id
     * @param field one of {@link #EDITABLE_FIELDS}
     * @param rawValue the value as typed (already joined)
     * @return null on success, otherwise an error message key
     */
    public synchronized String setCustomQuestField(final String questId, final String field, final String rawValue) {
        if (!isCustomQuest(questId)) {
            return "quest-admin-not-custom";
        }

        final String normalizedField = field == null ? "" : field.toLowerCase(Locale.ROOT);
        if (!EDITABLE_FIELDS.contains(normalizedField)) {
            return "quest-admin-bad-field";
        }

        if (rawValue == null || rawValue.isBlank()) {
            return "quest-admin-bad-value";
        }

        final Object parsed;
        switch (normalizedField) {
            case "requires-level", "objective.count", "objective.min-mob-level",
                 "objective.level", "rewards.class-xp" -> {
                try {
                    parsed = Math.max(0, Integer.parseInt(rawValue.trim()));
                } catch (final NumberFormatException exception) {
                    return "quest-admin-bad-value";
                }
            }
            case "rewards.currency.amount" -> {
                try {
                    parsed = Math.max(0.0D, Double.parseDouble(rawValue.trim()));
                } catch (final NumberFormatException exception) {
                    return "quest-admin-bad-value";
                }
            }
            case "rewards.cleanse-sins", "repeatable" -> parsed = Boolean.parseBoolean(rawValue.trim());
            case "cooldown-hours" -> {
                try {
                    parsed = Math.max(0.0D, Double.parseDouble(rawValue.trim()));
                } catch (final NumberFormatException exception) {
                    return "quest-admin-bad-value";
                }
            }
            // Item-jutalmak: MATERIAL:DARAB bejegyzések vesszővel elválasztva.
            case "rewards.items" -> {
                final List<String> items = new ArrayList<>();
                for (final String token : rawValue.split(",")) {
                    if (token.isBlank()) {
                        continue;
                    }
                    final String[] parts = token.trim().split(":");
                    if (Material.matchMaterial(parts[0].trim()) == null) {
                        return "quest-admin-bad-value";
                    }
                    items.add(token.trim().toUpperCase(Locale.ROOT));
                }
                if (items.isEmpty()) {
                    return "quest-admin-bad-value";
                }
                parsed = items;
            }
            case "objective.materials" -> {
                final List<String> materials = new ArrayList<>();
                for (final String token : rawValue.split(",")) {
                    if (!token.isBlank()) {
                        materials.add(token.trim().toUpperCase(Locale.ROOT));
                    }
                }
                if (materials.isEmpty()) {
                    return "quest-admin-bad-value";
                }
                parsed = materials;
            }
            // Párbeszéd-sorok: | jellel elválasztva több sor adható meg.
            case "dialogue.give", "dialogue.complete" -> {
                final List<String> lines = new ArrayList<>();
                for (final String token : rawValue.split("\\|")) {
                    if (!token.isBlank()) {
                        lines.add(token.trim());
                    }
                }
                if (lines.isEmpty()) {
                    return "quest-admin-bad-value";
                }
                parsed = lines;
            }
            case "rewards.currency.type" -> {
                final String type = rawValue.trim();
                if (!isOwnFactionCurrency(type) && CurrencyType.fromInput(type) == null) {
                    return "quest-admin-bad-value";
                }
                parsed = isOwnFactionCurrency(type) ? "OWN" : type.toUpperCase(Locale.ROOT);
            }
            case "objective.type" -> {
                final String type = rawValue.trim().toUpperCase(Locale.ROOT);
                if (!OBJECTIVE_TYPES.contains(type)) {
                    return "quest-admin-bad-objective";
                }
                parsed = type;
            }
            default -> parsed = rawValue.trim();
        }

        customQuests.set("quests." + questId.toLowerCase(Locale.ROOT) + "." + normalizedField, parsed);
        save();
        return null;
    }

    /**
     * Deletes an admin-authored quest definition. Config-shipped quests cannot
     * be deleted from in-game.
     *
     * @param questId the custom quest id
     * @return true if it existed and was removed
     */
    public synchronized boolean deleteCustomQuest(final String questId) {
        if (!isCustomQuest(questId)) {
            return false;
        }

        customQuests.set("quests." + questId.toLowerCase(Locale.ROOT), null);
        save();
        return true;
    }

    // ===== Definíciók =====

    public Set<String> getQuestIds() {
        final Set<String> ids = new LinkedHashSet<>();
        if (configManager.getConfiguration() != null) {
            final ConfigurationSection questsSection = configManager.getConfiguration().getConfigurationSection("quests");
            if (questsSection != null) {
                ids.addAll(questsSection.getKeys(false));
            }
        }
        ids.addAll(getCustomQuestIds());
        return ids;
    }

    public ConfigurationSection getQuestSection(final String questId) {
        if (questId == null) {
            return null;
        }

        final String path = "quests." + questId.toLowerCase(Locale.ROOT);
        if (configManager.getConfiguration() != null) {
            final ConfigurationSection fromConfig = configManager.getConfiguration().getConfigurationSection(path);
            if (fromConfig != null) {
                return fromConfig;
            }
        }
        return customQuests.getConfigurationSection(path);
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

        // Repeatable quests come back after their cooldown; others complete once, forever.
        if (hasCompleted(player, questId)) {
            if (!quest.getBoolean("repeatable", false)) {
                return "quest-already-completed";
            }

            final long cooldownMillis = (long) (Math.max(0.0D, quest.getDouble("cooldown-hours", 0.0D)) * 3_600_000.0D);
            if (cooldownMillis > 0L
                    && System.currentTimeMillis() - getLastCompletedAt(player, questId) < cooldownMillis) {
                return "quest-on-cooldown";
            }
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

    public void handlePlaceBlock(final Player player, final Material material) {
        forEachActive(player, "PLACE_BLOCKS", (questId, quest) ->
                quest.getStringList("objective.materials").stream()
                        .anyMatch(name -> name.equalsIgnoreCase(material.name())));
    }

    /** Item pickups progress by the picked-up stack size, not one per event. */
    public void handleCollect(final Player player, final Material material, final int amount) {
        forEachActive(player, "COLLECT_ITEMS", Math.max(1, amount), (questId, quest) ->
                quest.getStringList("objective.materials").stream()
                        .anyMatch(name -> name.equalsIgnoreCase(material.name())));
    }

    public void handlePlayerKill(final Player killer) {
        forEachActive(killer, "KILL_PLAYERS", (questId, quest) -> true);
    }

    public void handleBreed(final Player breeder, final EntityType entityType) {
        forEachActive(breeder, "BREED_ANIMALS", (questId, quest) -> {
            final String requiredEntity = quest.getString("objective.entity-type");
            return requiredEntity == null || requiredEntity.isBlank()
                    || requiredEntity.equalsIgnoreCase(entityType.name());
        });
    }

    public void handleEnchant(final Player player) {
        forEachActive(player, "ENCHANT_ITEMS", (questId, quest) -> true);
    }

    public void handleConsume(final Player player, final Material material) {
        forEachActive(player, "CONSUME_ITEMS", (questId, quest) ->
                quest.getStringList("objective.materials").stream()
                        .anyMatch(name -> name.equalsIgnoreCase(material.name())));
    }

    /** Furnace extraction progresses by the extracted amount. */
    public void handleSmelt(final Player player, final Material material, final int amount) {
        forEachActive(player, "SMELT_ITEMS", Math.max(1, amount), (questId, quest) ->
                quest.getStringList("objective.materials").stream()
                        .anyMatch(name -> name.equalsIgnoreCase(material.name())));
    }

    public void handleTame(final Player player, final EntityType entityType) {
        forEachActive(player, "TAME_ANIMALS", (questId, quest) -> {
            final String requiredEntity = quest.getString("objective.entity-type");
            return requiredEntity == null || requiredEntity.isBlank()
                    || requiredEntity.equalsIgnoreCase(entityType.name());
        });
    }

    public void handleVillagerTrade(final Player player) {
        forEachActive(player, "TRADE_WITH_VILLAGER", (questId, quest) -> true);
    }

    /** Biome keys arrive as "minecraft:plains" style; the objective may use either form. */
    public void handleBiomeVisit(final Player player, final String biomeKey) {
        forEachActive(player, "EXPLORE_BIOME", (questId, quest) -> {
            final String required = quest.getString("objective.biome", "");
            if (required.isBlank() || biomeKey == null) {
                return false;
            }
            final String shortKey = biomeKey.contains(":")
                    ? biomeKey.substring(biomeKey.indexOf(':') + 1)
                    : biomeKey;
            return required.equalsIgnoreCase(biomeKey) || required.equalsIgnoreCase(shortKey);
        });
    }

    public void handleRaidWin(final Player fighter) {
        forEachActive(fighter, "WIN_RAID", (questId, quest) -> true);
    }

    public void handleBossKill(final Player killer) {
        forEachActive(killer, "KILL_WORLDBOSS", (questId, quest) -> true);
    }

    public void handleTerritoryEnter(final Player player, final String territoryId) {
        forEachActive(player, "VISIT_TERRITORY", (questId, quest) ->
                territoryId != null && territoryId.equalsIgnoreCase(quest.getString("objective.territory", "")));

        // Auto-start quests: crossing into the configured territory hands the quest over
        // by itself (if every accept requirement is met) — discovery-driven storytelling.
        if (territoryId == null) {
            return;
        }
        for (final String questId : getQuestIds()) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null
                    || !territoryId.equalsIgnoreCase(quest.getString("auto-start-territory", ""))
                    || getAcceptBlocker(player, questId) != null
                    || !accept(player, questId)) {
                continue;
            }

            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1.0F, 1.2F);
            player.sendMessage(messageManager.getMessage(
                    "quest.auto-started",
                    "<gold>❕ Új küldetés indult: <white>{quest}</white> <gray>— {description}</gray></gold>",
                    Map.of(
                            "quest", getDisplayName(questId),
                            "description", quest.getString("description", "")
                    )
            ));
            sendDialogue(player, questId, "give", dialogueSpeakerFallback(quest));
        }
    }

    /**
     * Handles an NPC interaction for quest purposes: completes TALK_TO_NPC
     * objectives targeting the NPC (a talk resolves in one click, playing the
     * quest's completion dialogue first), and settles DELIVER_ITEMS objectives
     * when the player carries enough of the requested goods. Fired by the
     * reflective FancyNpcs bridge on the player's own region thread.
     *
     * @param player the interacting player
     * @param npcName the NPC's internal (FancyNpcs) name
     */
    public void handleNpcInteract(final Player player, final String npcName) {
        if (npcName == null) {
            return;
        }

        for (final String questId : List.copyOf(getActiveQuests(player))) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null || !npcName.equalsIgnoreCase(quest.getString("objective.npc", ""))) {
                continue;
            }

            final String type = quest.getString("objective.type", "");
            if ("TALK_TO_NPC".equalsIgnoreCase(type)) {
                complete(player, questId);
            } else if ("DELIVER_ITEMS".equalsIgnoreCase(type)) {
                tryDeliver(player, questId, quest);
            }
        }
    }

    /** Settles a DELIVER_ITEMS objective: takes the goods from the inventory if enough is carried. */
    private void tryDeliver(final Player player, final String questId, final ConfigurationSection quest) {
        final List<String> materials = quest.getStringList("objective.materials");
        if (materials.isEmpty()) {
            return;
        }

        final int target = Math.max(1, quest.getInt("objective.count", 1));
        int carried = 0;
        for (final org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && materials.stream().anyMatch(name -> name.equalsIgnoreCase(item.getType().name()))) {
                carried += item.getAmount();
            }
        }

        if (carried < target) {
            player.sendActionBar(messageManager.getMessage(
                    "quest.deliver-progress",
                    "<gray>{quest}: <gold>{carried}</gold>/<gold>{target}</gold> nálad — hozd el mindet!</gray>",
                    Map.of(
                            "quest", getDisplayName(questId),
                            "carried", String.valueOf(carried),
                            "target", String.valueOf(target)
                    )
            ));
            return;
        }

        int remaining = target;
        for (final org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (remaining <= 0) {
                break;
            }
            if (item == null || materials.stream().noneMatch(name -> name.equalsIgnoreCase(item.getType().name()))) {
                continue;
            }
            final int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
        }

        complete(player, questId);
    }

    /** The best NPC name to speak as when dialogue.speaker is not set. */
    private static String dialogueSpeakerFallback(final ConfigurationSection quest) {
        final String objectiveNpc = quest.getString("objective.npc", "");
        if (!objectiveNpc.isBlank()) {
            return objectiveNpc;
        }
        final String giver = quest.getString("giver-npc", "");
        return giver.isBlank() ? "???" : giver;
    }

    /**
     * Plays a quest's configured dialogue lines ({@code dialogue.give} /
     * {@code dialogue.complete}) as NPC speech, one line per ~1.5 seconds on
     * the player's own scheduler. The speaker name defaults to the NPC's
     * internal name; {@code dialogue.speaker} overrides it.
     */
    private void sendDialogue(final Player player, final String questId, final String phase,
                              final String fallbackSpeaker) {
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest == null) {
            return;
        }

        final List<String> lines = quest.getStringList("dialogue." + phase);
        if (lines.isEmpty()) {
            return;
        }

        final String speaker = quest.getString("dialogue.speaker",
                fallbackSpeaker == null ? "???" : fallbackSpeaker);
        for (int index = 0; index < lines.size(); index++) {
            final String line = lines.get(index);
            final Runnable send = () -> player.sendMessage(messageManager.getMessage(
                    "quest.dialogue-line",
                    "<gold>{speaker}:</gold> <white>{line}</white>",
                    Map.of("speaker", speaker, "line", line)
            ));
            if (index == 0) {
                send.run();
            } else {
                player.getScheduler().runDelayed(plugin, task -> send.run(), null, 30L * index);
            }
        }
    }

    // ===== NPC quest-adók (giver-npc) =====

    /** The NPC name configured to hand out this quest, or null. */
    public String getGiverNpc(final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        final String npc = quest == null ? null : quest.getString("giver-npc");
        return npc == null || npc.isBlank() ? null : npc;
    }

    /**
     * Every NPC name the quest system cares about: quest-giver NPCs plus
     * TALK_TO_NPC objective targets. Used by the marker tick to know which
     * NPCs may need a per-player particle marker.
     */
    public Set<String> getQuestNpcNames() {
        final Set<String> names = new LinkedHashSet<>();
        for (final String questId : getQuestIds()) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null) {
                continue;
            }

            final String giver = quest.getString("giver-npc");
            if (giver != null && !giver.isBlank()) {
                names.add(giver);
            }

            final String talkTarget = quest.getString("objective.npc");
            if ("TALK_TO_NPC".equalsIgnoreCase(quest.getString("objective.type", ""))
                    && talkTarget != null && !talkTarget.isBlank()) {
                names.add(talkTarget);
            }
        }
        return names;
    }

    /** Whether this NPC has at least one quest the player could accept right now. */
    public boolean hasAcceptableQuestFrom(final Player player, final String npcName) {
        if (npcName == null) {
            return false;
        }

        for (final String questId : getQuestIds()) {
            if (npcName.equalsIgnoreCase(getGiverNpc(questId)) && getAcceptBlocker(player, questId) == null) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player has an active TALK_TO_NPC quest targeting this NPC. */
    public boolean hasTalkObjectiveAt(final Player player, final String npcName) {
        if (npcName == null) {
            return false;
        }

        for (final String questId : getActiveQuests(player)) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest != null && "TALK_TO_NPC".equalsIgnoreCase(quest.getString("objective.type", ""))
                    && npcName.equalsIgnoreCase(quest.getString("objective.npc", ""))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hands out the first acceptable quest this NPC gives (config order, so
     * chains progress naturally). Fired by the FancyNpcs bridge on the
     * player's own region thread, right after TALK_TO_NPC objectives ran —
     * so a master NPC can complete the "talk to me" step and immediately
     * hand the follow-up trial over.
     *
     * @param player the interacting player
     * @param npcName the NPC's internal name
     * @return the accepted quest id, or null if this NPC had nothing to give
     */
    public String acceptFromNpc(final Player player, final String npcName) {
        if (npcName == null) {
            return null;
        }

        for (final String questId : getQuestIds()) {
            if (!npcName.equalsIgnoreCase(getGiverNpc(questId)) || getAcceptBlocker(player, questId) != null) {
                continue;
            }

            if (!accept(player, questId)) {
                continue;
            }

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0F, 1.1F);
            player.sendMessage(messageManager.getMessage(
                    "quest.accepted-from-npc",
                    "<gold>❕ Új küldetés: <white>{quest}</white> <gray>— {description}</gray></gold>",
                    Map.of(
                            "quest", getDisplayName(questId),
                            "description", getQuestSection(questId).getString("description", "")
                    )
            ));
            sendDialogue(player, questId, "give", npcName);
            return questId;
        }
        return null;
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
        forEachActive(player, objectiveType, 1, matcher);
    }

    private void forEachActive(final Player player, final String objectiveType, final int amount,
                               final ObjectiveMatcher matcher) {
        for (final String questId : List.copyOf(getActiveQuests(player))) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null || !objectiveType.equalsIgnoreCase(quest.getString("objective.type", ""))) {
                continue;
            }

            if (!matcher.matches(questId, quest)) {
                continue;
            }

            final int progress = getProgress(player, questId) + amount;
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

        // Story first: the quest's completion dialogue plays on every completion
        // path (NPC talk, delivery, parkour finish, admin force-complete alike).
        sendDialogue(player, questId, "complete", dialogueSpeakerFallback(quest));

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
        // Repeatable-cooldown anchor: when was this quest last turned in.
        player.getPersistentDataContainer().set(doneAtKey(questId), PersistentDataType.LONG, System.currentTimeMillis());

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
            // "OWN" (vagy FACTION/SAJAT) = a játékos SAJÁT frakciójának valutája.
            final String typeRaw = currencyReward.getString("type", "");
            final CurrencyType currencyType = isOwnFactionCurrency(typeRaw)
                    ? CurrencyType.fromFactionType(factionManager.getFaction(player.getUniqueId()))
                    : CurrencyType.fromInput(typeRaw);
            final double amount = currencyReward.getDouble("amount", 0.0D);
            if (currencyType != null && amount > 0.0D) {
                currencyManager.addToBalance(player.getUniqueId(), currencyType, amount);
            }
        }

        // Item rewards: "MATERIAL:AMOUNT" entries (amount defaults to 1).
        for (final String entry : quest.getStringList("rewards.items")) {
            final String[] parts = entry.split(":");
            final Material material = Material.matchMaterial(parts[0].trim());
            if (material == null || material.isAir()) {
                continue;
            }
            int amount = 1;
            if (parts.length >= 2) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (final NumberFormatException ignored) {
                    // Malformed amount: give one.
                }
            }
            final Map<Integer, org.bukkit.inventory.ItemStack> leftovers =
                    player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, amount));
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
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

    /** Whether the configured reward-currency type means "the player's own faction currency". */
    private static boolean isOwnFactionCurrency(final String typeRaw) {
        return "OWN".equalsIgnoreCase(typeRaw) || "FACTION".equalsIgnoreCase(typeRaw)
                || "SAJAT".equalsIgnoreCase(typeRaw) || "SAJÁT".equalsIgnoreCase(typeRaw);
    }

    // ===== PDC segédek =====

    public long getLastCompletedAt(final Player player, final String questId) {
        return player.getPersistentDataContainer().getOrDefault(doneAtKey(questId), PersistentDataType.LONG, 0L);
    }

    private NamespacedKey doneAtKey(final String questId) {
        final String sanitized = questId == null
                ? "unknown"
                : questId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return new NamespacedKey(plugin, "quest_done_at_" + sanitized);
    }

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
