package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CrateKeyFactory;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileQuestStore;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Config-driven quest definitions with PlayerProfile-backed player lifecycle state. */
public final class QuestManager implements PersistentStore, PlayerStateCleanup {

    public static final Set<String> OBJECTIVE_TYPES = Set.of(
            "KILL_MOBS", "BREAK_BLOCKS", "CRAFT_ITEMS", "CATCH_FISH",
            "VISIT_TERRITORY", "REACH_LEVEL", "TALK_TO_NPC", "PARKOUR_TRIAL",
            "PLACE_BLOCKS", "COLLECT_ITEMS", "KILL_PLAYERS", "DELIVER_ITEMS",
            "BREED_ANIMALS", "ENCHANT_ITEMS", "CONSUME_ITEMS", "SMELT_ITEMS",
            "TAME_ANIMALS", "TRADE_WITH_VILLAGER", "EXPLORE_BIOME", "WIN_RAID",
            "KILL_WORLDBOSS");

    public static final List<String> EDITABLE_FIELDS = List.of(
            "display-name", "description", "giver-npc", "next", "repeatable",
            "cooldown-hours", "seasonal", "auto-start-territory", "objectives-mode",
            "rotation-group", "rotation-daily-count", "requires-job", "requires-faction",
            "requires-level", "requires-quest", "chapter", "riddle", "min-season-day",
            "max-season-day", "objective.type", "objective.count", "objective.entity-type",
            "objective.min-mob-level", "objective.materials", "objective.territory",
            "objective.level", "objective.npc", "objective.course", "objective.biome",
            "objective.description", "rewards.class-xp", "rewards.currency.type",
            "rewards.currency.amount", "rewards.items", "rewards.unlock-spell",
            "rewards.cleanse-sins", "dialogue.speaker", "dialogue.give",
            "dialogue.complete", "dialogue.choices.1.text", "dialogue.choices.1.quest");

    private static final Set<String> OBJECTIVE_SUBFIELDS = Set.of(
            "type", "count", "entity-type", "min-mob-level", "materials",
            "territory", "level", "npc", "course", "biome", "description");
    private static final ThreadLocal<Integer> CHAIN_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_CHAIN_DEPTH = 16;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final JobManager jobManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final SeasonManager seasonManager;
    private final File customQuestsFile;
    private final PlayerProfileQuestStore questStore = new PlayerProfileQuestStore();

    /** Rebuildable online projection; durable truth remains QuestSection. */
    private final ConcurrentMap<UUID, QuestMirror> mirrors = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<Void>> mutationTails =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private volatile YamlConfiguration customQuests = new YamlConfiguration();
    private volatile GuildManager guildManager;
    private volatile StatsManager statsManager;
    private volatile CrateKeyFactory crateKeyFactory;
    private volatile SpecializationManager specializationManagerRef;
    private volatile boolean warnedMissingCrateKeyFactory;
    private volatile boolean npcBridgeActive;

    private record QuestMirror(Map<String, Map<String, Long>> active,
                               Set<String> completed,
                               Map<String, Long> localDoneAt,
                               Map<String, Long> localSeason) {
        private QuestMirror {
            final LinkedHashMap<String, Map<String, Long>> activeCopy = new LinkedHashMap<>();
            active.forEach((key, value) -> activeCopy.put(key, Map.copyOf(value)));
            active = Map.copyOf(activeCopy);
            completed = Set.copyOf(completed);
            localDoneAt = Map.copyOf(localDoneAt);
            localSeason = Map.copyOf(localSeason);
        }

        private static QuestMirror from(final PlayerProfileQuestStore.State state) {
            return new QuestMirror(state.progress(), state.completed(), Map.of(), Map.of());
        }
    }

    public QuestManager(final JavaPlugin plugin, final ConfigManager configManager,
                        final MessageManager messageManager, final JobManager jobManager,
                        final CurrencyManager currencyManager, final FactionManager factionManager,
                        final SinManager sinManager, final SeasonManager seasonManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.configManager = Objects.requireNonNull(configManager);
        this.messageManager = Objects.requireNonNull(messageManager);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.currencyManager = Objects.requireNonNull(currencyManager);
        this.factionManager = Objects.requireNonNull(factionManager);
        this.sinManager = Objects.requireNonNull(sinManager);
        this.seasonManager = seasonManager;
        this.customQuestsFile = new File(plugin.getDataFolder(), "custom-quests.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void setGuildManager(final GuildManager guildManager) { this.guildManager = guildManager; }
    public void setStatsManager(final StatsManager statsManager) { this.statsManager = statsManager; }
    public void setCrateKeyFactory(final CrateKeyFactory crateKeyFactory) { this.crateKeyFactory = crateKeyFactory; }
    public void setSpecializationManager(final SpecializationManager manager) { this.specializationManagerRef = manager; }

    @Override
    public void load() {
        if (!customQuestsFile.exists()) {
            customQuests = new YamlConfiguration();
            return;
        }
        try {
            customQuests = YamlStore.loadTracked(customQuestsFile, plugin.getLogger());
            plugin.getLogger().info("Loaded " + getCustomQuestIds().size()
                    + " admin-created quest(s).");
        } catch (final Exception failure) {
            plugin.getLogger().severe("Failed to load custom-quests.yml: " + failure.getMessage());
        }
    }

    @Override
    public synchronized void save() {
        try { YamlStore.saveAtomic(customQuestsFile, customQuests); }
        catch (final IOException failure) {
            throw new java.io.UncheckedIOException("Failed to save custom-quests.yml", failure);
        }
    }

    private static YamlConfiguration copyOf(final YamlConfiguration source) {
        final YamlConfiguration copy = new YamlConfiguration();
        try { copy.loadFromString(source.saveToString()); }
        catch (final InvalidConfigurationException impossible) {
            throw new IllegalStateException("custom quest snapshot failed", impossible);
        }
        return copy;
    }

    public Set<String> getCustomQuestIds() {
        final ConfigurationSection section = customQuests.getConfigurationSection("quests");
        return section == null ? Set.of() : Set.copyOf(section.getKeys(false));
    }

    public boolean isCustomQuest(final String questId) {
        return questId != null && customQuests.isConfigurationSection(
                "quests." + normalizeQuestId(questId));
    }

    private boolean isConfigQuest(final String questId) {
        return configManager.getConfiguration() != null
                && configManager.getConfiguration().isConfigurationSection(
                "quests." + normalizeQuestId(questId));
    }

    public synchronized String createCustomQuest(final String id, final String objectiveType,
                                                 final int count, final String displayName) {
        if (id == null || id.isBlank() || !id.toLowerCase(Locale.ROOT).matches("[a-z0-9_]+")) {
            return "quest-admin-bad-id";
        }
        final String normalized = normalizeQuestId(id);
        if (isConfigQuest(normalized) || isCustomQuest(normalized)) return "quest-admin-exists";
        if (objectiveType == null || !OBJECTIVE_TYPES.contains(objectiveType.toUpperCase(Locale.ROOT))) {
            return "quest-admin-bad-objective";
        }
        if (count < 1) return "quest-admin-bad-count";
        final YamlConfiguration draft = copyOf(customQuests);
        final String base = "quests." + normalized;
        draft.set(base + ".display-name",
                displayName == null || displayName.isBlank() ? normalized : displayName);
        draft.set(base + ".objective.type", objectiveType.toUpperCase(Locale.ROOT));
        draft.set(base + ".objective.count", count);
        customQuests = draft;
        save();
        return null;
    }

    public synchronized String setCustomQuestField(final String questId, final String field,
                                                   final String rawValue) {
        if (!isCustomQuest(questId)) return "quest-admin-not-custom";
        final String normalizedField = field == null ? "" : field.toLowerCase(Locale.ROOT);
        String parseKey = normalizedField;
        final var indexed = java.util.regex.Pattern.compile(
                "objectives\\.(\\d+)\\.([a-z-]+)").matcher(normalizedField);
        final var choice = java.util.regex.Pattern.compile(
                "dialogue\\.choices\\.(\\d+)\\.(text|quest)").matcher(normalizedField);
        if (indexed.matches()) {
            if (!OBJECTIVE_SUBFIELDS.contains(indexed.group(2))) return "quest-admin-bad-field";
            parseKey = "objective." + indexed.group(2);
        } else if (choice.matches()) {
            parseKey = "dialogue.choice-" + choice.group(2);
        } else if (!EDITABLE_FIELDS.contains(normalizedField)
                && !"objectives-mode".equals(normalizedField)) {
            return "quest-admin-bad-field";
        }
        if (rawValue == null || rawValue.isBlank()) return "quest-admin-bad-value";

        final Object parsed;
        try {
            parsed = switch (parseKey) {
                case "objectives-mode" -> {
                    final String mode = rawValue.trim().toUpperCase(Locale.ROOT);
                    if (!Set.of("ALL", "SEQUENCE").contains(mode))
                        throw new IllegalArgumentException();
                    yield mode;
                }
                case "requires-level", "objective.count", "objective.min-mob-level",
                     "objective.level", "rewards.class-xp", "rotation-daily-count" ->
                        Math.max(0, Integer.parseInt(rawValue.trim()));
                case "rewards.currency.amount", "cooldown-hours" ->
                        Math.max(0.0D, Double.parseDouble(rawValue.trim()));
                case "rewards.cleanse-sins", "repeatable", "seasonal" ->
                        Boolean.parseBoolean(rawValue.trim());
                case "dialogue.choice-quest" -> normalizeQuestId(rawValue);
                case "rewards.items" -> parseItems(rawValue);
                case "objective.materials" -> parseMaterials(rawValue);
                case "dialogue.give", "dialogue.complete" -> parseLines(rawValue);
                case "rewards.currency.type" -> {
                    final String type = rawValue.trim();
                    if (!isOwnFactionCurrency(type) && CurrencyType.fromInput(type) == null)
                        throw new IllegalArgumentException();
                    yield isOwnFactionCurrency(type) ? "OWN" : type.toUpperCase(Locale.ROOT);
                }
                case "objective.type" -> {
                    final String type = rawValue.trim().toUpperCase(Locale.ROOT);
                    if (!OBJECTIVE_TYPES.contains(type)) throw new IllegalArgumentException();
                    yield type;
                }
                default -> rawValue.trim();
            };
        } catch (final RuntimeException invalid) {
            return "objective.type".equals(parseKey)
                    ? "quest-admin-bad-objective" : "quest-admin-bad-value";
        }
        final YamlConfiguration draft = copyOf(customQuests);
        draft.set("quests." + normalizeQuestId(questId) + "." + normalizedField, parsed);
        customQuests = draft;
        save();
        return null;
    }

    public synchronized String addObjective(final String questId, final String objectiveType,
                                            final int count, final String description,
                                            final int[] index) {
        if (!isCustomQuest(questId)) return "quest-admin-not-custom";
        if (objectiveType == null || !OBJECTIVE_TYPES.contains(objectiveType.toUpperCase(Locale.ROOT)))
            return "quest-admin-bad-objective";
        if (count < 1) return "quest-admin-bad-count";
        final YamlConfiguration draft = copyOf(customQuests);
        final ConfigurationSection quest = draft.getConfigurationSection(
                "quests." + normalizeQuestId(questId));
        if (quest == null) return "quest-admin-not-custom";
        ConfigurationSection multi = quest.getConfigurationSection("objectives");
        if (multi == null) {
            multi = quest.createSection("objectives");
            final ConfigurationSection single = quest.getConfigurationSection("objective");
            if (single != null) {
                final ConfigurationSection first = multi.createSection("1");
                single.getKeys(false).forEach(key -> first.set(key, single.get(key)));
                quest.set("objective", null);
            }
        }
        int next = 1;
        while (multi.contains(Integer.toString(next))) next++;
        multi.set(next + ".type", objectiveType.toUpperCase(Locale.ROOT));
        multi.set(next + ".count", count);
        if (description != null && !description.isBlank())
            multi.set(next + ".description", description.trim());
        customQuests = draft;
        save();
        if (index != null && index.length > 0) index[0] = next;
        return null;
    }

    public synchronized boolean deleteCustomQuest(final String questId) {
        if (!isCustomQuest(questId)) return false;
        final YamlConfiguration draft = copyOf(customQuests);
        draft.set("quests." + normalizeQuestId(questId), null);
        customQuests = draft;
        save();
        return true;
    }

    public Set<String> getQuestIds() {
        final LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (configManager.getConfiguration() != null) {
            final ConfigurationSection root =
                    configManager.getConfiguration().getConfigurationSection("quests");
            if (root != null) ids.addAll(root.getKeys(false));
        }
        ids.addAll(getCustomQuestIds());
        return Set.copyOf(ids);
    }

    public ConfigurationSection getQuestSection(final String questId) {
        if (questId == null) return null;
        final String path = "quests." + normalizeQuestId(questId);
        if (configManager.getConfiguration() != null) {
            final ConfigurationSection packaged =
                    configManager.getConfiguration().getConfigurationSection(path);
            if (packaged != null) return packaged;
        }
        return customQuests.getConfigurationSection(path);
    }

    public String getDisplayName(final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        return quest == null ? questId : quest.getString("display-name", questId);
    }

    public int getObjectiveCount(final String questId) {
        final List<ConfigurationSection> objectives = getObjectiveSections(getQuestSection(questId));
        return objectives.isEmpty() ? 1 : Math.max(1, objectives.get(0).getInt("count", 1));
    }

    public List<String> getActiveQuests(final Player player) {
        return player == null ? List.of() : List.copyOf(mirror(player.getUniqueId()).active().keySet());
    }

    public List<String> getCompletedQuests(final Player player) {
        return player == null ? List.of() : List.copyOf(mirror(player.getUniqueId()).completed());
    }

    public boolean isActive(final Player player, final String questId) {
        return player != null && questId != null
                && mirror(player.getUniqueId()).active().containsKey(normalizeQuestId(questId));
    }

    public boolean hasCompleted(final Player player, final String questId) {
        return player != null && questId != null
                && mirror(player.getUniqueId()).completed().contains(normalizeQuestId(questId));
    }

    public int getProgress(final Player player, final String questId) {
        return getObjectiveProgress(player, questId, 0);
    }

    public String getAcceptBlocker(final Player player, final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest == null) return "quest-unknown";
        if (isActive(player, questId)) return "quest-already-active";
        if (!isOfferedToday(questId)) return "quest-not-offered-today";
        if (hasCompleted(player, questId)) {
            final boolean repeatable = quest.getBoolean("repeatable", false);
            final boolean seasonal = quest.getBoolean("seasonal", false);
            if (!repeatable && !seasonal) return "quest-already-completed";
            if (seasonal && getCompletedSeason(player, questId) == currentSeasonId())
                return "quest-season-locked";
            if (repeatable) {
                final long cooldown = (long) (Math.max(0.0D,
                        quest.getDouble("cooldown-hours", 0.0D)) * 3_600_000.0D);
                if (cooldown > 0L && System.currentTimeMillis()
                        - getLastCompletedAt(player, questId) < cooldown)
                    return "quest-on-cooldown";
            }
        }
        final int chapter = quest.getInt("chapter", 0);
        if (chapter > 0 && seasonManager != null && seasonManager.getSeasonNumber() > 0
                && seasonManager.getSeasonNumber() != chapter) {
            return seasonManager.getSeasonNumber() > chapter
                    ? "quest-chapter-closed" : "quest-chapter-future";
        }
        final int minDay = quest.getInt("min-season-day", 0);
        final int maxDay = quest.getInt("max-season-day", 0);
        if ((minDay > 0 || maxDay > 0) && seasonManager != null
                && seasonManager.getSeasonDay() > 0) {
            if (minDay > 0 && seasonManager.getSeasonDay() < minDay)
                return "quest-season-window-future";
            if (maxDay > 0 && seasonManager.getSeasonDay() > maxDay)
                return "quest-season-window-closed";
        }
        final String requiredJob = quest.getString("requires-job");
        if (requiredJob != null && !requiredJob.isBlank()) {
            final JobType type = JobType.fromId(requiredJob);
            if (type == null || jobManager.getPrimaryJob(player) != type)
                return "quest-requires-job";
        }
        final String requiredFaction = quest.getString("requires-faction");
        if (requiredFaction != null && !requiredFaction.isBlank()
                && !factionManager.isMember(player.getUniqueId(),
                FactionType.fromInput(requiredFaction))) return "quest-requires-faction";
        final int requiredLevel = quest.getInt("requires-level", 0);
        if (requiredLevel > 0 && jobManager.getPrimaryLevel(player) < requiredLevel)
            return "quest-requires-level";
        final String requiredQuest = quest.getString("requires-quest");
        if (requiredQuest != null && !requiredQuest.isBlank()
                && !hasCompleted(player, requiredQuest)) return "quest-requires-quest";
        return null;
    }

    public void setNpcBridgeActive(final boolean active) { npcBridgeActive = active; }
    public boolean isNpcBridgeActive() { return npcBridgeActive; }

    public void playGiveDialogue(final Player player, final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest != null) sendDialogue(player, questId, "give", dialogueSpeakerFallback(quest));
    }

    public boolean accept(final Player player, final String questId) {
        if (player == null || getAcceptBlocker(player, questId) != null) return false;
        final String id = normalizeQuestId(questId);
        final UUID playerId = player.getUniqueId();
        synchronized (lock(playerId)) {
            final QuestMirror before = mirror(playerId);
            if (before.active().containsKey(id)) return false;
            final LinkedHashMap<String, Map<String, Long>> active =
                    new LinkedHashMap<>(before.active());
            active.put(id, Map.of());
            mirrors.put(playerId, new QuestMirror(active, before.completed(),
                    before.localDoneAt(), before.localSeason()));
            enqueue(playerId, () -> questStore.accept(playerId, id).thenApply(ignored -> null));
        }
        handleLevelChange(player);
        return true;
    }

    public boolean abandon(final Player player, final String questId) {
        if (player == null || !isActive(player, questId)) return false;
        final String id = normalizeQuestId(questId);
        final UUID playerId = player.getUniqueId();
        synchronized (lock(playerId)) {
            final QuestMirror before = mirror(playerId);
            final LinkedHashMap<String, Map<String, Long>> active =
                    new LinkedHashMap<>(before.active());
            active.remove(id);
            mirrors.put(playerId, new QuestMirror(active, before.completed(),
                    before.localDoneAt(), before.localSeason()));
            enqueue(playerId, () -> questStore.abandon(playerId, id).thenApply(ignored -> null));
        }
        return true;
    }

    public void handleKill(final Player player, final EntityType type, final int level) {
        forEachActive(player, "KILL_MOBS", (id, objective) -> {
            final String required = objective.getString("entity-type");
            return (required == null || required.isBlank() || required.equalsIgnoreCase(type.name()))
                    && level >= objective.getInt("min-mob-level", 0);
        });
    }
    public void handleBlockBreak(final Player player, final Material material) { forEachActive(player, "BREAK_BLOCKS", (id, obj) -> materialMatches(obj, material)); }
    public void handleCraft(final Player player, final Material material, final int amount) { forEachActive(player, "CRAFT_ITEMS", Math.max(1, amount), (id, obj) -> materialMatches(obj, material)); }
    public void handleFish(final Player player) { forEachActive(player, "CATCH_FISH", (id, obj) -> true); }
    public void handlePlaceBlock(final Player player, final Material material) { forEachActive(player, "PLACE_BLOCKS", (id, obj) -> materialMatches(obj, material)); }
    public void handleCollect(final Player player, final Material material, final int amount) { forEachActive(player, "COLLECT_ITEMS", Math.max(1, amount), (id, obj) -> materialMatches(obj, material)); }
    public void handlePlayerKill(final Player player) { forEachActive(player, "KILL_PLAYERS", (id, obj) -> true); }
    public void handleBreed(final Player player, final EntityType type) { forEachActive(player, "BREED_ANIMALS", (id, obj) -> entityMatches(obj, type)); }
    public void handleEnchant(final Player player) { forEachActive(player, "ENCHANT_ITEMS", (id, obj) -> true); }
    public void handleConsume(final Player player, final Material material) { forEachActive(player, "CONSUME_ITEMS", (id, obj) -> materialMatches(obj, material)); }
    public void handleSmelt(final Player player, final Material material, final int amount) { forEachActive(player, "SMELT_ITEMS", Math.max(1, amount), (id, obj) -> materialMatches(obj, material)); }
    public void handleTame(final Player player, final EntityType type) { forEachActive(player, "TAME_ANIMALS", (id, obj) -> entityMatches(obj, type)); }
    public void handleVillagerTrade(final Player player) { forEachActive(player, "TRADE_WITH_VILLAGER", (id, obj) -> true); }
    public void handleRaidWin(final Player player) { forEachActive(player, "WIN_RAID", (id, obj) -> true); }
    public void handleBossKill(final Player player) { forEachActive(player, "KILL_WORLDBOSS", (id, obj) -> true); }
    public void handleParkourFinish(final Player player, final String courseId) { forEachActive(player, "PARKOUR_TRIAL", (id, obj) -> courseId != null && courseId.equalsIgnoreCase(obj.getString("course", ""))); }

    public void handleBiomeVisit(final Player player, final String biomeKey) {
        forEachActive(player, "EXPLORE_BIOME", (id, objective) -> {
            final String required = objective.getString("biome", "");
            if (required.isBlank() || biomeKey == null) return false;
            final String shortKey = biomeKey.contains(":")
                    ? biomeKey.substring(biomeKey.indexOf(':') + 1) : biomeKey;
            return required.equalsIgnoreCase(biomeKey) || required.equalsIgnoreCase(shortKey);
        });
    }

    public void handleTerritoryEnter(final Player player, final String territoryId) {
        forEachActive(player, "VISIT_TERRITORY", (id, objective) ->
                territoryId != null && territoryId.equalsIgnoreCase(
                        objective.getString("territory", "")));
        if (territoryId == null) return;
        for (final String questId : getQuestIds()) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null || !territoryId.equalsIgnoreCase(
                    quest.getString("auto-start-territory", ""))
                    || getAcceptBlocker(player, questId) != null || !accept(player, questId)) continue;
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1.0F, 1.2F);
            player.sendMessage(messageManager.getMessage("quest.auto-started",
                    "<gold>❕ Új küldetés indult: <white>{quest}</white> <gray>— {description}</gray></gold>",
                    Map.of("quest", getDisplayName(questId),
                            "description", quest.getString("description", ""))));
            sendDialogue(player, questId, "give", dialogueSpeakerFallback(quest));
        }
    }

    public void handleNpcInteract(final Player player, final String npcName) {
        if (player == null || npcName == null) return;
        for (final String questId : List.copyOf(getActiveQuests(player))) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null || !isStillFactionEligible(player, quest)) continue;
            final List<ConfigurationSection> objectives = getObjectiveSections(quest);
            final boolean sequence = isSequenceMode(quest);
            boolean changed = false;
            for (int index = 0; index < objectives.size(); index++) {
                final ConfigurationSection objective = objectives.get(index);
                if (isObjectiveComplete(player, questId, index, objective)
                        || !npcName.equalsIgnoreCase(objective.getString("npc", ""))
                        || sequence && !isCurrentStep(player, questId, objectives, index)) continue;
                final String type = objective.getString("type", "");
                if ("TALK_TO_NPC".equalsIgnoreCase(type)) {
                    setObjectiveProgress(player, questId, index,
                            Math.max(1, objective.getInt("count", 1)));
                    changed = true;
                } else if ("DELIVER_ITEMS".equalsIgnoreCase(type)
                        && tryDeliver(player, questId, objective, index)) changed = true;
            }
            if (changed && allObjectivesComplete(player, questId, objectives)) complete(player, questId);
        }
    }

    private boolean tryDeliver(final Player player, final String questId,
                               final ConfigurationSection objective, final int index) {
        final List<String> materials = objective.getStringList("materials");
        if (materials.isEmpty()) return false;
        final int target = Math.max(1, objective.getInt("count", 1));
        int carried = 0;
        for (final var item : player.getInventory().getContents()) {
            if (item != null && materials.stream().anyMatch(name ->
                    name.equalsIgnoreCase(item.getType().name()))) carried += item.getAmount();
        }
        if (carried < target) {
            player.sendActionBar(messageManager.getMessage("quest.deliver-progress",
                    "<gray>{quest}: <gold>{carried}</gold>/<gold>{target}</gold> nálad — hozd el mindet!</gray>",
                    Map.of("quest", getDisplayName(questId), "carried", Integer.toString(carried),
                            "target", Integer.toString(target))));
            return false;
        }
        int remaining = target;
        for (final var item : player.getInventory().getContents()) {
            if (remaining <= 0) break;
            if (item == null || materials.stream().noneMatch(name ->
                    name.equalsIgnoreCase(item.getType().name()))) continue;
            final int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
        }
        setObjectiveProgress(player, questId, index, target);
        return true;
    }

    public void handleLevelChange(final Player player) {
        if (player == null) return;
        for (final String questId : List.copyOf(getActiveQuests(player))) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null || !isStillFactionEligible(player, quest)) continue;
            final List<ConfigurationSection> objectives = getObjectiveSections(quest);
            final boolean sequence = isSequenceMode(quest);
            boolean changed = false;
            for (int index = 0; index < objectives.size(); index++) {
                final ConfigurationSection objective = objectives.get(index);
                if (!"REACH_LEVEL".equalsIgnoreCase(objective.getString("type", ""))
                        || isObjectiveComplete(player, questId, index, objective)
                        || sequence && !isCurrentStep(player, questId, objectives, index)) continue;
                if (jobManager.getPrimaryLevel(player) >= Math.max(1, objective.getInt("level", 1))) {
                    setObjectiveProgress(player, questId, index,
                            Math.max(1, objective.getInt("count", 1)));
                    changed = true;
                }
            }
            if (changed && allObjectivesComplete(player, questId, objectives)) complete(player, questId);
        }
    }

    private interface ObjectiveMatcher { boolean matches(String questId, ConfigurationSection objective); }
    private void forEachActive(final Player player, final String type, final ObjectiveMatcher matcher) { forEachActive(player, type, 1, matcher); }

    private void forEachActive(final Player player, final String objectiveType, final int amount,
                               final ObjectiveMatcher matcher) {
        if (player == null) return;
        for (final String questId : List.copyOf(getActiveQuests(player))) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null || !isStillFactionEligible(player, quest)) continue;
            final List<ConfigurationSection> objectives = getObjectiveSections(quest);
            final boolean sequence = isSequenceMode(quest);
            boolean changed = false;
            for (int index = 0; index < objectives.size(); index++) {
                final ConfigurationSection objective = objectives.get(index);
                if (!objectiveType.equalsIgnoreCase(objective.getString("type", ""))
                        || isObjectiveComplete(player, questId, index, objective)
                        || sequence && !isCurrentStep(player, questId, objectives, index)
                        || !matcher.matches(questId, objective)) continue;
                final int target = Math.max(1, objective.getInt("count", 1));
                final int old = getObjectiveProgress(player, questId, index);
                final int next = incrementObjectiveProgress(player, questId, index, amount, target);
                if (next > old) {
                    changed = true;
                    announceObjective(player, questId, objectives, index, objective, next, target);
                }
                if (sequence) break;
            }
            if (changed && allObjectivesComplete(player, questId, objectives)) complete(player, questId);
        }
    }

    private void announceObjective(final Player player, final String questId,
                                   final List<ConfigurationSection> objectives, final int index,
                                   final ConfigurationSection objective, final int progress,
                                   final int target) {
        if (progress >= target && allObjectivesComplete(player, questId, objectives)) return;
        if (objectives.size() == 1) {
            player.sendActionBar(messageManager.getMessage("quest.progress",
                    "<gray>{quest}: <gold>{progress}</gold>/<gold>{target}</gold></gray>",
                    Map.of("quest", getDisplayName(questId), "progress", Integer.toString(progress),
                            "target", Integer.toString(target))));
        } else {
            player.sendActionBar(messageManager.getMessage("quest.progress-multi",
                    "<gray>{quest} — {objective}: <gold>{progress}</gold>/<gold>{target}</gold> <dark_gray>[{step}/{steps}]</dark_gray></gray>",
                    Map.of("quest", getDisplayName(questId), "objective", objectiveLabel(objective),
                            "progress", Integer.toString(progress), "target", Integer.toString(target),
                            "step", Integer.toString(index + 1), "steps", Integer.toString(objectives.size()))));
        }
    }

    private List<ConfigurationSection> getObjectiveSections(final ConfigurationSection quest) {
        if (quest == null) return List.of();
        final ConfigurationSection multi = quest.getConfigurationSection("objectives");
        if (multi != null) {
            final List<ConfigurationSection> sections = multi.getKeys(false).stream()
                    .sorted(Comparator.comparingInt(QuestManager::objectiveOrder))
                    .map(multi::getConfigurationSection).filter(Objects::nonNull).toList();
            if (!sections.isEmpty()) return sections;
        }
        final ConfigurationSection single = quest.getConfigurationSection("objective");
        return single == null ? List.of() : List.of(single);
    }

    private static int objectiveOrder(final String key) {
        try { return Integer.parseInt(key.trim()); }
        catch (final NumberFormatException ignored) { return Integer.MAX_VALUE; }
    }

    public int getObjectiveTotal(final String questId) { return getObjectiveSections(getQuestSection(questId)).size(); }
    private boolean isSequenceMode(final ConfigurationSection quest) { return "SEQUENCE".equalsIgnoreCase(quest.getString("objectives-mode", "ALL")); }

    private boolean isCurrentStep(final Player player, final String questId,
                                  final List<ConfigurationSection> objectives, final int index) {
        for (int earlier = 0; earlier < index; earlier++)
            if (!isObjectiveComplete(player, questId, earlier, objectives.get(earlier))) return false;
        return true;
    }

    private boolean allObjectivesComplete(final Player player, final String questId,
                                          final List<ConfigurationSection> objectives) {
        if (objectives.isEmpty()) return false;
        for (int index = 0; index < objectives.size(); index++)
            if (!isObjectiveComplete(player, questId, index, objectives.get(index))) return false;
        return true;
    }

    private boolean isObjectiveComplete(final Player player, final String questId, final int index,
                                        final ConfigurationSection objective) {
        return getObjectiveProgress(player, questId, index)
                >= Math.max(1, objective.getInt("count", 1));
    }

    public int getObjectiveProgress(final Player player, final String questId, final int index) {
        if (player == null || index < 0) return 0;
        return Math.toIntExact(mirror(player.getUniqueId()).active()
                .getOrDefault(normalizeQuestId(questId), Map.of())
                .getOrDefault(objectiveKey(index), 0L));
    }

    private void setObjectiveProgress(final Player player, final String questId,
                                      final int index, final int value) {
        if (player == null || index < 0 || value < 0) return;
        final UUID playerId = player.getUniqueId();
        final String id = normalizeQuestId(questId);
        synchronized (lock(playerId)) {
            final QuestMirror before = mirror(playerId);
            final Map<String, Long> current = before.active().get(id);
            if (current == null) return;
            final LinkedHashMap<String, Long> progress = new LinkedHashMap<>(current);
            if (value == 0) progress.remove(objectiveKey(index));
            else progress.put(objectiveKey(index), (long) value);
            final LinkedHashMap<String, Map<String, Long>> active = new LinkedHashMap<>(before.active());
            active.put(id, Map.copyOf(progress));
            mirrors.put(playerId, new QuestMirror(active, before.completed(),
                    before.localDoneAt(), before.localSeason()));
            enqueue(playerId, () -> questStore.setProgress(playerId, id, index, value)
                    .thenApply(ignored -> null));
        }
    }

    private int incrementObjectiveProgress(final Player player, final String questId,
                                           final int index, final int amount, final int target) {
        final UUID playerId = player.getUniqueId();
        final String id = normalizeQuestId(questId);
        synchronized (lock(playerId)) {
            final QuestMirror before = mirror(playerId);
            final Map<String, Long> current = before.active().get(id);
            if (current == null) return 0;
            final long old = current.getOrDefault(objectiveKey(index), 0L);
            final long next = Math.min(target, Math.addExact(old, amount));
            if (next == old) return Math.toIntExact(next);
            final LinkedHashMap<String, Long> progress = new LinkedHashMap<>(current);
            progress.put(objectiveKey(index), next);
            final LinkedHashMap<String, Map<String, Long>> active = new LinkedHashMap<>(before.active());
            active.put(id, Map.copyOf(progress));
            mirrors.put(playerId, new QuestMirror(active, before.completed(),
                    before.localDoneAt(), before.localSeason()));
            enqueue(playerId, () -> questStore.incrementProgress(playerId, id, index, amount, target)
                    .thenApply(ignored -> null));
            return Math.toIntExact(next);
        }
    }

    public String describeProgress(final Player player, final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest != null && quest.getBoolean("riddle", false))
            return "??? — a nyomot a leírás rejti";
        final List<ConfigurationSection> objectives = getObjectiveSections(quest);
        if (objectives.isEmpty()) return getProgress(player, questId) + "/" + getObjectiveCount(questId);
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < objectives.size(); i++) {
            if (i > 0) result.append(" • ");
            final ConfigurationSection objective = objectives.get(i);
            final int target = Math.max(1, objective.getInt("count", 1));
            result.append(objectiveLabel(objective)).append(' ')
                    .append(Math.min(getObjectiveProgress(player, questId, i), target))
                    .append('/').append(target);
        }
        return result.toString();
    }

    public void complete(final Player player, final String questId) {
        if (player == null) return;
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest == null || !isStillFactionEligible(player, quest)) return;
        final UUID playerId = player.getUniqueId();
        final String id = normalizeQuestId(questId);
        final long completedAt = System.currentTimeMillis();
        final long seasonId = currentSeasonId();
        synchronized (lock(playerId)) {
            final QuestMirror before = mirror(playerId);
            if (!before.active().containsKey(id)) return;
            final LinkedHashMap<String, Map<String, Long>> active = new LinkedHashMap<>(before.active());
            active.remove(id);
            final LinkedHashSet<String> completed = new LinkedHashSet<>(before.completed());
            completed.add(id);
            final LinkedHashMap<String, Long> done = new LinkedHashMap<>(before.localDoneAt());
            final LinkedHashMap<String, Long> seasons = new LinkedHashMap<>(before.localSeason());
            done.put(id, completedAt);
            seasons.put(id, seasonId);
            mirrors.put(playerId, new QuestMirror(active, completed, done, seasons));
            enqueue(playerId, () -> questStore.complete(playerId, id, completedAt, seasonId)
                    .thenCompose(receipt -> {
                        if (!receipt.committed()) return CompletableFuture.completedFuture(null);
                        final CompletableFuture<Void> result = new CompletableFuture<>();
                        player.getScheduler().run(plugin, task -> finishCompletion(
                                player, quest, receipt, false, result),
                                () -> result.completeExceptionally(new IllegalStateException(
                                        "player scheduler rejected quest completion")));
                        return result;
                    }));
        }
    }

    /** Replays unacknowledged reward receipts after a reconnect/restart. */
    public void recoverPendingRewards(final Player player) {
        if (player == null) return;
        final Set<String> pending;
        try { pending = questStore.pendingRewards(player.getUniqueId()); }
        catch (final RuntimeException notReady) { return; }
        for (final String receiptId : pending) {
            final String questId;
            try { questId = questStore.questFromReceipt(receiptId); }
            catch (final RuntimeException malformed) {
                plugin.getLogger().severe("Invalid PlayerProfile quest reward receipt: " + receiptId);
                continue;
            }
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null) {
                plugin.getLogger().severe("Pending quest reward references missing quest: " + questId);
                continue;
            }
            final var receipt = new PlayerProfileQuestStore.CompletionReceipt(
                    true, receiptId, questId, 0L, 0L);
            final CompletableFuture<Void> result = new CompletableFuture<>();
            player.getScheduler().run(plugin,
                    task -> finishCompletion(player, quest, receipt, true, result),
                    () -> result.completeExceptionally(new IllegalStateException(
                            "player scheduler rejected quest reward recovery")));
        }
    }

    private void finishCompletion(final Player player, final ConfigurationSection quest,
                                  final PlayerProfileQuestStore.CompletionReceipt receipt,
                                  final boolean recovery, final CompletableFuture<Void> result) {
        if (!recovery) sendDialogue(player, receipt.questId(), "complete", dialogueSpeakerFallback(quest));
        applyRewards(player, quest, receipt.receiptId()).whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().severe("Quest reward remains pending for " + player.getUniqueId()
                        + "/" + receipt.receiptId() + ": " + rootMessage(failure));
                result.completeExceptionally(unwrap(failure));
                return;
            }
            questStore.settleReward(player.getUniqueId(), receipt.receiptId())
                    .whenComplete((settled, settleFailure) -> player.getScheduler().run(plugin, task -> {
                        if (settleFailure != null) {
                            result.completeExceptionally(unwrap(settleFailure));
                            return;
                        }
                        if (!recovery) {
                            if (statsManager != null) statsManager.recordQuestComplete(player.getUniqueId());
                            if (guildManager != null) guildManager.addActivityXp(player,
                                    Math.max(0, configManager.getInt("guilds.xp-per-quest", 10)));
                            player.playSound(player.getLocation(),
                                    Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                            player.sendMessage(messageManager.getMessage("quest.completed",
                                    "<gold>✔ Küldetés teljesítve: <white>{quest}</white>!</gold>",
                                    Map.of("quest", getDisplayName(receipt.questId()))));
                            if (configManager.getBoolean("quest-toast.enabled", true))
                                hu.taliann.icesmp.utils.ToastUtil.show(plugin, player,
                                        hu.taliann.icesmp.utils.ToastUtil.Kind.QUEST);
                            advanceChain(player, quest);
                        } else {
                            player.sendMessage(messageManager.getMessage("quest.reward-recovered",
                                    "<gold>A korábban függő küldetésjutalmad helyreállt: <white>{quest}</white>.</gold>",
                                    Map.of("quest", getDisplayName(receipt.questId()))));
                        }
                        result.complete(null);
                    }, null));
        });
    }

    private CompletionStage<Void> applyRewards(final Player player,
                                               final ConfigurationSection quest,
                                               final String receiptId) {
        final List<CompletionStage<?>> stages = new ArrayList<>();
        final int classXp = quest.getInt("rewards.class-xp", 0);
        if (classXp > 0 && jobManager.hasPrimaryJob(player)) {
            stages.add(jobManager.addXpToJobV2(player, classXp,
                    "quest-xp:" + receiptId));
        }
        final ConfigurationSection currency = quest.getConfigurationSection("rewards.currency");
        if (currency != null) {
            final String raw = currency.getString("type", "");
            final CurrencyType type = isOwnFactionCurrency(raw)
                    ? factionManager.getChosenFaction(player.getUniqueId())
                    .map(CurrencyType::fromFactionType).orElse(null)
                    : CurrencyType.fromInput(raw);
            final double amount = currency.getDouble("amount", 0.0D);
            if (type != null && amount > 0.0D) currencyManager.payOutTokens(player, type, Math.round(amount));
        }
        for (final String entry : quest.getStringList("rewards.items")) {
            final String[] parts = entry.split(":");
            final Material material = Material.matchMaterial(parts[0].trim());
            if (material == null || material.isAir()) continue;
            int amount = 1;
            if (parts.length >= 2) {
                try { amount = Math.max(1, Integer.parseInt(parts[1].trim())); }
                catch (final NumberFormatException ignored) { amount = 1; }
            }
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, amount))
                    .values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        final String unlockSpell = quest.getString("rewards.unlock-spell");
        if (unlockSpell != null && !unlockSpell.isBlank()) {
            stages.add(jobManager.unlockSpellV2(player, unlockSpell,
                    JobManager.SOURCE_QUEST_PREFIX + normalizeQuestId(quest.getName())));
        }
        final String crateReward = quest.getString("rewards.crate-key");
        if (crateReward != null && !crateReward.isBlank()) grantCrateKeyReward(player, crateReward);
        if (quest.getBoolean("rewards.cleanse-sins", false)) {
            factionManager.setFaction(player.getUniqueId(), FactionType.NEUTRAL);
            sinManager.breakDarkPact(player);
        }
        final SpecializationManager specs = specializationManagerRef;
        if (specs != null) stages.add(specs.reconcileDarkGates(player));
        return CompletableFuture.allOf(stages.stream()
                .map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new));
    }

    private void advanceChain(final Player player, final ConfigurationSection completedQuest) {
        final String next = completedQuest.getString("next");
        if (next == null || next.isBlank()) return;
        final int depth = CHAIN_DEPTH.get();
        if (depth >= MAX_CHAIN_DEPTH) {
            plugin.getLogger().severe("Quest chain depth exceeded at " + next);
            return;
        }
        CHAIN_DEPTH.set(depth + 1);
        try {
            if (getAcceptBlocker(player, next) == null && accept(player, next)) {
                final ConfigurationSection nextQuest = getQuestSection(next);
                player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1.0F, 1.2F);
                player.sendMessage(messageManager.getMessage("quest.auto-started",
                        "<gold>❕ Új küldetés indult: <white>{quest}</white> <gray>— {description}</gray></gold>",
                        Map.of("quest", getDisplayName(next), "description",
                                nextQuest == null ? "" : nextQuest.getString("description", ""))));
                sendDialogue(player, next, "give", dialogueSpeakerFallback(nextQuest));
            }
        } finally {
            if (depth == 0) CHAIN_DEPTH.remove(); else CHAIN_DEPTH.set(depth);
        }
    }

    public String getGiverNpc(final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        final String npc = quest == null ? null : quest.getString("giver-npc");
        return npc == null || npc.isBlank() ? null : npc;
    }

    public boolean isOfferedToday(final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest == null) return false;
        final String group = quest.getString("rotation-group", "");
        return group.isBlank() || todaysRotation(group).contains(normalizeQuestId(questId));
    }

    private List<String> todaysRotation(final String group) {
        final List<String> pool = new ArrayList<>();
        int dailyCount = 2;
        for (final String id : getQuestIds()) {
            final ConfigurationSection quest = getQuestSection(id);
            if (quest != null && group.equalsIgnoreCase(quest.getString("rotation-group", ""))) {
                pool.add(normalizeQuestId(id));
                dailyCount = Math.max(1, quest.getInt("rotation-daily-count", dailyCount));
            }
        }
        if (pool.size() <= dailyCount) return pool;
        pool.sort(Comparator.naturalOrder());
        final long day = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toEpochDay();
        Collections.shuffle(pool, new Random(day * 31L + group.toLowerCase(Locale.ROOT).hashCode()));
        return new ArrayList<>(pool.subList(0, dailyCount));
    }

    public Set<String> getQuestNpcNames() {
        final LinkedHashSet<String> names = new LinkedHashSet<>();
        for (final String id : getQuestIds()) {
            final ConfigurationSection quest = getQuestSection(id);
            if (quest == null) continue;
            final String giver = quest.getString("giver-npc");
            if (giver != null && !giver.isBlank()) names.add(giver);
            for (final ConfigurationSection objective : getObjectiveSections(quest)) {
                final String npc = objective.getString("npc");
                if (npc != null && !npc.isBlank()) names.add(npc);
            }
        }
        return Set.copyOf(names);
    }

    public boolean hasAcceptableQuestFrom(final Player player, final String npcName) {
        if (npcName == null) return false;
        for (final String id : getQuestIds())
            if (npcName.equalsIgnoreCase(getGiverNpc(id)) && getAcceptBlocker(player, id) == null)
                return true;
        return false;
    }

    public boolean hasTalkObjectiveAt(final Player player, final String npcName) {
        if (npcName == null) return false;
        for (final String id : getActiveQuests(player)) {
            final ConfigurationSection quest = getQuestSection(id);
            if (quest == null || !isStillFactionEligible(player, quest)) continue;
            final List<ConfigurationSection> objectives = getObjectiveSections(quest);
            final boolean sequence = isSequenceMode(quest);
            for (int i = 0; i < objectives.size(); i++) {
                final ConfigurationSection objective = objectives.get(i);
                if (npcName.equalsIgnoreCase(objective.getString("npc", ""))
                        && !isObjectiveComplete(player, id, i, objective)
                        && (!sequence || isCurrentStep(player, id, objectives, i))) return true;
            }
        }
        return false;
    }

    public String acceptFromNpc(final Player player, final String npcName) {
        if (npcName == null) return null;
        for (final String id : getQuestIds()) {
            if (npcName.equalsIgnoreCase(getGiverNpc(id)) && getAcceptBlocker(player, id) == null) {
                final String accepted = tryAcceptAndAnnounce(player, id, npcName);
                if (accepted != null) return accepted;
            }
        }
        return null;
    }

    public String acceptBoundQuest(final Player player, final String questId, final String npcName) {
        return questId == null || getAcceptBlocker(player, questId) != null
                ? null : tryAcceptAndAnnounce(player, questId, npcName);
    }

    private String tryAcceptAndAnnounce(final Player player, final String questId,
                                        final String npcName) {
        if (!accept(player, questId)) return null;
        final ConfigurationSection quest = getQuestSection(questId);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0F, 1.1F);
        player.sendMessage(messageManager.getMessage("quest.accepted-from-npc",
                "<gold>❕ Új küldetés: <white>{quest}</white> <gray>— {description}</gray></gold>",
                Map.of("quest", getDisplayName(questId), "description",
                        quest == null ? "" : quest.getString("description", ""))));
        sendDialogue(player, questId, "give", npcName);
        return normalizeQuestId(questId);
    }

    private String dialogueSpeakerFallback(final ConfigurationSection quest) {
        if (quest == null) return "???";
        for (final ConfigurationSection objective : getObjectiveSections(quest)) {
            final String npc = objective.getString("npc", "");
            if (!npc.isBlank()) return npc;
        }
        final String giver = quest.getString("giver-npc", "");
        return giver.isBlank() ? "???" : giver;
    }

    private void sendDialogue(final Player player, final String questId, final String phase,
                              final String fallbackSpeaker) {
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest == null) return;
        final List<String> lines = quest.getStringList("dialogue." + phase);
        if (lines.isEmpty()) return;
        final String speaker = quest.getString("dialogue.speaker",
                fallbackSpeaker == null ? "???" : fallbackSpeaker);
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final Runnable send = () -> player.sendMessage(messageManager.getMessage(
                    "quest.dialogue-line", "<gold>{speaker}:</gold> <white>{line}</white>",
                    Map.of("speaker", speaker, "line", line)));
            if (i == 0) send.run();
            else player.getScheduler().runDelayed(plugin, task -> send.run(), null, 30L * i);
        }
        if ("give".equalsIgnoreCase(phase)) {
            final ConfigurationSection choices = quest.getConfigurationSection("dialogue.choices");
            if (choices != null && !choices.getKeys(false).isEmpty())
                player.getScheduler().runDelayed(plugin,
                        task -> sendChoices(player, choices), null, 30L * Math.max(1, lines.size()));
        }
    }

    private void sendChoices(final Player player, final ConfigurationSection choices) {
        player.sendMessage(messageManager.getMessage("quest.choose-prompt", "<gray>Válassz:</gray>"));
        for (final String key : choices.getKeys(false).stream()
                .sorted(Comparator.comparingInt(QuestManager::objectiveOrder)).toList()) {
            final ConfigurationSection choice = choices.getConfigurationSection(key);
            if (choice == null) continue;
            final String target = choice.getString("quest", "");
            if (target.isBlank()) continue;
            player.sendMessage(net.kyori.adventure.text.Component.text(
                            "  ▸ " + choice.getString("text", "..."),
                            net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/quest accept " + target))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            net.kyori.adventure.text.Component.text("Kattints a választáshoz",
                                    net.kyori.adventure.text.format.NamedTextColor.GRAY))));
        }
    }

    private boolean isStillFactionEligible(final Player player, final ConfigurationSection quest) {
        final String required = quest.getString("requires-faction");
        return required == null || required.isBlank()
                || factionManager.isMember(player.getUniqueId(), FactionType.fromInput(required));
    }

    private void grantCrateKeyReward(final Player player, final String raw) {
        final CrateKeyFactory factory = crateKeyFactory;
        if (factory == null) {
            if (!warnedMissingCrateKeyFactory) {
                warnedMissingCrateKeyFactory = true;
                plugin.getLogger().warning("Quest crate reward skipped: CrateKeyFactory is not bound.");
            }
            return;
        }
        final String[] parts = raw.split(":");
        int amount = 1;
        if (parts.length >= 2) {
            try { amount = Math.max(1, Integer.parseInt(parts[1].trim())); }
            catch (final NumberFormatException ignored) { amount = 1; }
        }
        final var key = factory.createKey(parts[0].trim(), amount);
        if (!key.getType().isAir()) player.getInventory().addItem(key).values()
                .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    public long getLastCompletedAt(final Player player, final String questId) {
        if (player == null) return 0L;
        final QuestMirror mirror = mirror(player.getUniqueId());
        final Long local = mirror.localDoneAt().get(normalizeQuestId(questId));
        if (local != null) return local;
        try { return questStore.lastCompletedAt(player.getUniqueId(), questId); }
        catch (final RuntimeException notReady) { return 0L; }
    }

    private long getCompletedSeason(final Player player, final String questId) {
        if (player == null) return -1L;
        final QuestMirror mirror = mirror(player.getUniqueId());
        final Long local = mirror.localSeason().get(normalizeQuestId(questId));
        if (local != null) return local;
        try { return questStore.completedSeason(player.getUniqueId(), questId); }
        catch (final RuntimeException notReady) { return -1L; }
    }

    private long currentSeasonId() { return seasonManager == null ? 0L : seasonManager.getSeasonStart(); }

    private QuestMirror mirror(final UUID playerId) {
        return mirrors.computeIfAbsent(playerId, ignored -> {
            try { return QuestMirror.from(questStore.read(playerId)); }
            catch (final RuntimeException notReady) {
                return new QuestMirror(Map.of(), Set.of(), Map.of(), Map.of());
            }
        });
    }

    private Object lock(final UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, ignored -> new Object());
    }

    private void enqueue(final UUID playerId, final Supplier<CompletionStage<Void>> work) {
        mutationTails.compute(playerId, (ignored, previous) -> {
            final CompletableFuture<Void> start = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((value, failure) -> null);
            final CompletableFuture<Void> next = start.thenCompose(nothing -> {
                try { return Objects.requireNonNull(work.get()).toCompletableFuture(); }
                catch (final Throwable failure) { return CompletableFuture.failedFuture(failure); }
            });
            next.whenComplete((value, failure) -> {
                mutationTails.remove(playerId, next);
                if (failure != null) {
                    mirrors.remove(playerId);
                    plugin.getLogger().severe("PlayerProfile quest mutation failed for "
                            + playerId + ": " + rootMessage(failure));
                }
            });
            return next;
        });
    }

    private static boolean materialMatches(final ConfigurationSection objective,
                                           final Material material) {
        return material != null && objective.getStringList("materials").stream()
                .anyMatch(name -> name.equalsIgnoreCase(material.name()));
    }

    private static boolean entityMatches(final ConfigurationSection objective,
                                         final EntityType type) {
        final String required = objective.getString("entity-type");
        return required == null || required.isBlank() || required.equalsIgnoreCase(type.name());
    }

    private static String objectiveLabel(final ConfigurationSection objective) {
        final String described = objective.getString("description", "");
        if (!described.isBlank()) return described;
        return switch (objective.getString("type", "").toUpperCase(Locale.ROOT)) {
            case "KILL_MOBS" -> "Szörnyek";
            case "KILL_PLAYERS" -> "Játékosok";
            case "KILL_WORLDBOSS" -> "Világboss";
            case "BREAK_BLOCKS" -> "Bányászás";
            case "PLACE_BLOCKS" -> "Építés";
            case "CRAFT_ITEMS" -> "Craftolás";
            case "COLLECT_ITEMS" -> "Gyűjtés";
            case "DELIVER_ITEMS" -> "Beszállítás";
            case "CONSUME_ITEMS" -> "Fogyasztás";
            case "SMELT_ITEMS" -> "Olvasztás";
            case "CATCH_FISH" -> "Horgászat";
            case "ENCHANT_ITEMS" -> "Bűbáj";
            case "BREED_ANIMALS" -> "Tenyésztés";
            case "TAME_ANIMALS" -> "Szelídítés";
            case "TRADE_WITH_VILLAGER" -> "Kereskedés";
            case "EXPLORE_BIOME" -> "Felfedezés";
            case "VISIT_TERRITORY" -> "Utazás";
            case "REACH_LEVEL" -> "Szint";
            case "TALK_TO_NPC" -> "Beszélgetés";
            case "PARKOUR_TRIAL" -> "Parkour";
            case "WIN_RAID" -> "Raid";
            default -> "Feladat";
        };
    }

    private static String objectiveKey(final int index) { return "objective." + index; }
    private static String normalizeQuestId(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("quest id required");
        return raw.trim().toLowerCase(Locale.ROOT);
    }
    private static boolean isOwnFactionCurrency(final String raw) {
        return "OWN".equalsIgnoreCase(raw) || "FACTION".equalsIgnoreCase(raw)
                || "SAJAT".equalsIgnoreCase(raw) || "SAJÁT".equalsIgnoreCase(raw);
    }
    private static List<String> parseItems(final String raw) {
        final List<String> result = new ArrayList<>();
        for (final String token : raw.split(",")) {
            if (token.isBlank()) continue;
            final String[] parts = token.trim().split(":");
            if (Material.matchMaterial(parts[0].trim()) == null) throw new IllegalArgumentException();
            result.add(token.trim().toUpperCase(Locale.ROOT));
        }
        if (result.isEmpty()) throw new IllegalArgumentException();
        return result;
    }
    private static List<String> parseMaterials(final String raw) {
        final List<String> result = new ArrayList<>();
        for (final String token : raw.split(",")) if (!token.isBlank())
            result.add(token.trim().toUpperCase(Locale.ROOT));
        if (result.isEmpty()) throw new IllegalArgumentException();
        return result;
    }
    private static List<String> parseLines(final String raw) {
        final List<String> result = new ArrayList<>();
        for (final String token : raw.split("\\|")) if (!token.isBlank()) result.add(token.trim());
        if (result.isEmpty()) throw new IllegalArgumentException();
        return result;
    }
    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }
    private static String rootMessage(final Throwable failure) {
        final Throwable root = unwrap(failure);
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final CompletableFuture<Void> tail = mutationTails.get(playerId);
        if (tail == null) {
            mirrors.remove(playerId);
            playerLocks.remove(playerId);
        } else {
            tail.whenComplete((ignored, failure) -> {
                mirrors.remove(playerId);
                playerLocks.remove(playerId);
            });
        }
    }
}
