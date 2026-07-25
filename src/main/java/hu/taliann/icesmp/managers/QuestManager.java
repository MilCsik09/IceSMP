package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CrateKeyFactory;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Config-driven quest framework: quest
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
 * <p>Linear auto-chains (no NPC/territory step needed between links, e.g. the
 * first-join onboarding sequence): a quest's optional {@code next} field names
 * the follow-up quest id, auto-accepted for the player the moment this quest
 * completes (see {@link #complete}) — same accept + announce + dialogue flow
 * as an NPC hand-out, just fired from completion instead of an interaction.</p>
 *
 * <p>Besides the config-shipped quests, admins can build quests in-game
 * without touching code or files (<code>/quest admin create|set|delete</code>);
 * those live in custom-quests.yml under the plugin data folder (same schema)
 * and are merged into every lookup. On an id collision the config quest wins,
 * so shipped content can't be shadowed.</p>
 */
public final class QuestManager implements PersistentStore {

    /** B35 — setter-injektált (konstruktor-sorrend): quest-teljesítés céh-XP-je. */
    private volatile GuildManager guildManager;

    public void setGuildManager(final GuildManager guildManager) {
        this.guildManager = guildManager;
    }

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
            "display-name", "description", "giver-npc", "next",
            "repeatable", "cooldown-hours", "seasonal", "auto-start-territory", "objectives-mode",
            "rotation-group", "rotation-daily-count",
            "requires-job", "requires-faction", "requires-level", "requires-quest", "chapter", "riddle",
            "min-season-day", "max-season-day",
            "objective.type", "objective.count", "objective.entity-type",
            "objective.min-mob-level", "objective.materials", "objective.territory",
            "objective.level", "objective.npc", "objective.course", "objective.biome",
            "objective.description",
            "rewards.class-xp", "rewards.currency.type", "rewards.currency.amount",
            "rewards.items", "rewards.unlock-spell", "rewards.cleanse-sins",
            "dialogue.speaker", "dialogue.give", "dialogue.complete",
            // Kattintható párbeszéd-válaszok: bármely index mehet (dialogue.choices.<N>.text|quest),
            // az 1-es példaként szerepel itt a tab-complete kedvéért.
            "dialogue.choices.1.text", "dialogue.choices.1.quest");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final JobManager jobManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final SeasonManager seasonManager;
    private final NamespacedKey activeQuestsKey;
    private final NamespacedKey completedQuestsKey;
    private final File customQuestsFile;
    private volatile YamlConfiguration customQuests = new YamlConfiguration();
    // Bound after construction (manual-DI ordering) — see IceSMPCore#setStatsManager wiring.
    private volatile StatsManager statsManager;
    // Bound after construction (manual-DI ordering) — see IceSMPCore#setCrateKeyFactory wiring.
    private volatile CrateKeyFactory crateKeyFactory;
    private volatile boolean warnedMissingCrateKeyFactory;

    public QuestManager(final JavaPlugin plugin, final ConfigManager configManager,
                        final MessageManager messageManager, final JobManager jobManager,
                        final CurrencyManager currencyManager, final FactionManager factionManager,
                        final SinManager sinManager, final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.jobManager = jobManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.seasonManager = seasonManager;
        this.activeQuestsKey = new NamespacedKey(plugin, "quests_active");
        this.completedQuestsKey = new NamespacedKey(plugin, "quests_completed");
        this.customQuestsFile = new File(plugin.getDataFolder(), "custom-quests.yml");
        plugin.getDataFolder().mkdirs();
    }

    /**
     * Binds the {@link StatsManager} used by {@code /stats} to count completed
     * quests. Set after construction because of the manual-DI
     * ordering in {@code IceSMPCore} (StatsManager is built after QuestManager).
     */
    public void setStatsManager(final StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    /**
     * Binds the {@link CrateKeyFactory} used by the {@code rewards.crate-key} quest-reward
     * field. Set after construction because of the manual-DI ordering in
     * {@code IceSMPCore} (CrateKeyFactory is built after QuestManager).
     */
    public void setCrateKeyFactory(final CrateKeyFactory crateKeyFactory) {
        this.crateKeyFactory = crateKeyFactory;
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

    /**
     * Copy-on-write snapshot of the custom-quest tree. The admin editor mutates a
     * private copy and swaps the volatile {@code customQuests} reference, so reader
     * threads (every region thread resolves quest sections on each progress event)
     * never observe a YamlConfiguration whose backing maps are mid-mutation — on
     * Folia the admin edit and player progress genuinely run in parallel.
     */
    private static YamlConfiguration copyOf(final YamlConfiguration source) {
        final YamlConfiguration copy = new YamlConfiguration();
        try {
            copy.loadFromString(source.saveToString());
        } catch (final InvalidConfigurationException exception) {
            // Round-tripping our own serialized config cannot produce invalid YAML.
            throw new IllegalStateException("custom-quests snapshot failed", exception);
        }
        return copy;
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
        final YamlConfiguration draft = copyOf(customQuests);
        draft.set(base + ".display-name", displayName == null || displayName.isBlank() ? normalizedId : displayName);
        draft.set(base + ".objective.type", objectiveType.toUpperCase(Locale.ROOT));
        draft.set(base + ".objective.count", count);
        customQuests = draft;
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
    /** Objective sub-fields the admin editor may set under objective / objectives.N. */
    private static final Set<String> OBJECTIVE_SUBFIELDS = Set.of(
            "type", "count", "entity-type", "min-mob-level", "materials",
            "territory", "level", "npc", "course", "biome", "description");

    private volatile SpecializationManager specializationManagerRef;

    public void setSpecializationManager(final SpecializationManager specializationManager) {
        this.specializationManagerRef = specializationManager;
    }

    public synchronized String setCustomQuestField(final String questId, final String field, final String rawValue) {
        if (!isCustomQuest(questId)) {
            return "quest-admin-not-custom";
        }

        final String normalizedField = field == null ? "" : field.toLowerCase(Locale.ROOT);

        // Accept whitelisted fields, the objectives-mode switch, and any objectives.<N>.<subfield>
        // path (multi-objective). For parsing we canonicalize objectives.N.X to objective.X so the
        // existing type-aware cases apply.
        String parseKey = normalizedField;
        final java.util.regex.Matcher indexed =
                java.util.regex.Pattern.compile("objectives\\.(\\d+)\\.([a-z-]+)").matcher(normalizedField);
        // Kattintható párbeszéd-válaszok tetszőleges indexszel: dialogue.choices.<N>.text|quest.
        final java.util.regex.Matcher choice =
                java.util.regex.Pattern.compile("dialogue\\.choices\\.(\\d+)\\.(text|quest)").matcher(normalizedField);
        if (indexed.matches()) {
            if (!OBJECTIVE_SUBFIELDS.contains(indexed.group(2))) {
                return "quest-admin-bad-field";
            }
            parseKey = "objective." + indexed.group(2);
        } else if (choice.matches()) {
            parseKey = "dialogue.choice-" + choice.group(2);
        } else if (!EDITABLE_FIELDS.contains(normalizedField) && !"objectives-mode".equals(normalizedField)) {
            return "quest-admin-bad-field";
        }

        if (rawValue == null || rawValue.isBlank()) {
            return "quest-admin-bad-value";
        }

        final Object parsed;
        switch (parseKey) {
            case "objectives-mode" -> {
                final String mode = rawValue.trim().toUpperCase(Locale.ROOT);
                if (!"ALL".equals(mode) && !"SEQUENCE".equals(mode)) {
                    return "quest-admin-bad-value";
                }
                parsed = mode;
            }
            case "requires-level", "objective.count", "objective.min-mob-level",
                 "objective.level", "rewards.class-xp", "rotation-daily-count" -> {
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
            case "rewards.cleanse-sins", "repeatable", "seasonal" -> parsed = Boolean.parseBoolean(rawValue.trim());
            // A választás cél-questje: kisbetűs id-ként tárolódik (mint minden quest-kulcs).
            case "dialogue.choice-quest" -> parsed = rawValue.trim().toLowerCase(Locale.ROOT);
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

        final YamlConfiguration draft = copyOf(customQuests);
        draft.set("quests." + questId.toLowerCase(Locale.ROOT) + "." + normalizedField, parsed);
        customQuests = draft;
        save();
        return null;
    }

    /**
     * Appends an objective to an admin-authored quest, turning it multi-objective.
     * The first call migrates the quest's single {@code objective:} block into
     * {@code objectives.1}, so "kill X mobs AND fetch Y items" is built by
     * creating the quest, then adding a second objective. Returns the new
     * objective's 1-based index in {@code [index]}, or an error message key.
     *
     * @param questId the custom quest id
     * @param objectiveType one of {@link #OBJECTIVE_TYPES}
     * @param count the objective count
     * @param description an optional short label for the step
     * @param index a one-element holder receiving the new objective index on success
     * @return null on success, otherwise an error message key
     */
    public synchronized String addObjective(final String questId, final String objectiveType, final int count,
                                            final String description, final int[] index) {
        if (!isCustomQuest(questId)) {
            return "quest-admin-not-custom";
        }
        if (objectiveType == null || !OBJECTIVE_TYPES.contains(objectiveType.toUpperCase(Locale.ROOT))) {
            return "quest-admin-bad-objective";
        }
        if (count < 1) {
            return "quest-admin-bad-count";
        }

        final String base = "quests." + questId.toLowerCase(Locale.ROOT);
        final YamlConfiguration draft = copyOf(customQuests);
        final ConfigurationSection quest = draft.getConfigurationSection(base);
        if (quest == null) {
            return "quest-admin-not-custom";
        }

        // Migrate a legacy single objective into objectives.1 on the first add.
        ConfigurationSection multi = quest.getConfigurationSection("objectives");
        if (multi == null) {
            multi = quest.createSection("objectives");
            final ConfigurationSection single = quest.getConfigurationSection("objective");
            if (single != null) {
                final ConfigurationSection first = multi.createSection("1");
                for (final String key : single.getKeys(false)) {
                    first.set(key, single.get(key));
                }
                quest.set("objective", null);
            }
        }

        int next = 1;
        while (multi.contains(String.valueOf(next))) {
            next++;
        }
        multi.set(next + ".type", objectiveType.toUpperCase(Locale.ROOT));
        multi.set(next + ".count", count);
        if (description != null && !description.isBlank()) {
            multi.set(next + ".description", description.trim());
        }
        customQuests = draft;
        save();
        if (index != null && index.length > 0) {
            index[0] = next;
        }
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

        final YamlConfiguration draft = copyOf(customQuests);
        draft.set("quests." + questId.toLowerCase(Locale.ROOT), null);
        customQuests = draft;
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

    /** The first objective's target count (kept for the compact single-objective displays). */
    public int getObjectiveCount(final String questId) {
        final List<ConfigurationSection> objectives = getObjectiveSections(getQuestSection(questId));
        return objectives.isEmpty() ? 1 : Math.max(1, objectives.get(0).getInt("count", 1));
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

        // Rotation: a grouped quest is only acceptable on the days it is on offer.
        if (!isOfferedToday(questId)) {
            return "quest-not-offered-today";
        }

        // Repeatable quests come back after their cooldown; seasonal quests come back
        // once each new season; others complete once, forever.
        if (hasCompleted(player, questId)) {
            final boolean repeatable = quest.getBoolean("repeatable", false);
            final boolean seasonal = quest.getBoolean("seasonal", false);
            if (!repeatable && !seasonal) {
                return "quest-already-completed";
            }

            if (seasonal && getCompletedSeason(player, questId) == currentSeasonId()) {
                return "quest-season-locked";
            }

            if (repeatable) {
                final long cooldownMillis = (long) (Math.max(0.0D, quest.getDouble("cooldown-hours", 0.0D)) * 3_600_000.0D);
                if (cooldownMillis > 0L
                        && System.currentTimeMillis() - getLastCompletedAt(player, questId) < cooldownMillis) {
                    return "quest-on-cooldown";
                }
            }
        }

        // Fejezet-szűrő: a `chapter: N` quest csak az N. szezon-fejezet alatt vehető
        // fel. A már FELVETT fejezet-quest szezonváltás után is befejezhető (kegyelmi
        // szabály), de új felvétel és a next-lánc folytatása már nem nyílik meg.
        final int chapter = quest.getInt("chapter", 0);
        if (chapter > 0) {
            final SeasonManager seasonRef = seasonManager;
            final int current = seasonRef == null ? 0 : seasonRef.getSeasonNumber();
            if (current > 0 && current != chapter) {
                return current > chapter ? "quest-chapter-closed" : "quest-chapter-future";
            }
        }

        // Szezon-közepi ablak: a min/max-season-day questek csak
        // a szezon adott nap-sávjában vehetők fel — így a szezon KÖZEPÉNEK is van dátum-kapus
        // tartalma. A már felvett quest az ablak zárta után is befejezhető (kegyelmi szabály).
        final int minSeasonDay = quest.getInt("min-season-day", 0);
        final int maxSeasonDay = quest.getInt("max-season-day", 0);
        if (minSeasonDay > 0 || maxSeasonDay > 0) {
            final SeasonManager seasonRef = seasonManager;
            final int day = seasonRef == null ? 0 : seasonRef.getSeasonDay();
            if (day > 0) {
                if (minSeasonDay > 0 && day < minSeasonDay) {
                    return "quest-season-window-future";
                }
                if (maxSeasonDay > 0 && day > maxSeasonDay) {
                    return "quest-season-window-closed";
                }
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

    /** A FancyNpcs quest-bridge állapota — a /quest talk tartalék-út ebből tudja, hogy kell-e. */
    private volatile boolean npcBridgeActive;

    public void setNpcBridgeActive(final boolean active) {
        this.npcBridgeActive = active;
    }

    public boolean isNpcBridgeActive() {
        return npcBridgeActive;
    }

    /** A give-dialógus lejátszása parancsos felvételkor (az NPC-út a saját folyamában játssza). */
    public void playGiveDialogue(final Player player, final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest != null) {
            sendDialogue(player, questId, "give", dialogueSpeakerFallback(quest));
        }
    }

    public boolean accept(final Player player, final String questId) {
        if (getAcceptBlocker(player, questId) != null) {
            return false;
        }

        final List<String> active = new ArrayList<>(getActiveQuests(player));
        active.add(questId.toLowerCase(Locale.ROOT));
        writeCsv(player, activeQuestsKey, active);
        // Clear any stale counters (e.g. from a previous run of a repeatable quest).
        clearAllProgress(player, questId);

        // REACH_LEVEL objectives may already be satisfied at acceptance.
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
        clearAllProgress(player, questId);
        return true;
    }

    // ===== Haladás-útvonalak (a listenerek hívják) =====

    public void handleKill(final Player player, final EntityType entityType, final int mobLevel) {
        forEachActive(player, "KILL_MOBS", (questId, objective) -> {
            final String requiredEntity = objective.getString("entity-type");
            if (requiredEntity != null && !requiredEntity.isBlank()
                    && !requiredEntity.equalsIgnoreCase(entityType.name())) {
                return false;
            }

            final int minMobLevel = objective.getInt("min-mob-level", 0);
            return mobLevel >= minMobLevel;
        });
    }

    public void handleBlockBreak(final Player player, final Material material) {
        forEachActive(player, "BREAK_BLOCKS", (questId, objective) -> materialMatches(objective, material));
    }

    /** Crafting progresses by the recipe's yield per craft action (e.g. 4 for planks). */
    public void handleCraft(final Player player, final Material material, final int amount) {
        forEachActive(player, "CRAFT_ITEMS", Math.max(1, amount),
                (questId, objective) -> materialMatches(objective, material));
    }

    public void handleFish(final Player player) {
        forEachActive(player, "CATCH_FISH", (questId, objective) -> true);
    }

    public void handlePlaceBlock(final Player player, final Material material) {
        forEachActive(player, "PLACE_BLOCKS", (questId, objective) -> materialMatches(objective, material));
    }

    /** Item pickups progress by the picked-up stack size, not one per event. */
    public void handleCollect(final Player player, final Material material, final int amount) {
        forEachActive(player, "COLLECT_ITEMS", Math.max(1, amount),
                (questId, objective) -> materialMatches(objective, material));
    }

    public void handlePlayerKill(final Player killer) {
        forEachActive(killer, "KILL_PLAYERS", (questId, objective) -> true);
    }

    public void handleBreed(final Player breeder, final EntityType entityType) {
        forEachActive(breeder, "BREED_ANIMALS", (questId, objective) -> entityMatches(objective, entityType));
    }

    public void handleEnchant(final Player player) {
        forEachActive(player, "ENCHANT_ITEMS", (questId, objective) -> true);
    }

    public void handleConsume(final Player player, final Material material) {
        forEachActive(player, "CONSUME_ITEMS", (questId, objective) -> materialMatches(objective, material));
    }

    /** Furnace extraction progresses by the extracted amount. */
    public void handleSmelt(final Player player, final Material material, final int amount) {
        forEachActive(player, "SMELT_ITEMS", Math.max(1, amount),
                (questId, objective) -> materialMatches(objective, material));
    }

    public void handleTame(final Player player, final EntityType entityType) {
        forEachActive(player, "TAME_ANIMALS", (questId, objective) -> entityMatches(objective, entityType));
    }

    public void handleVillagerTrade(final Player player) {
        forEachActive(player, "TRADE_WITH_VILLAGER", (questId, objective) -> true);
    }

    /** Biome keys arrive as "minecraft:plains" style; the objective may use either form. */
    public void handleBiomeVisit(final Player player, final String biomeKey) {
        forEachActive(player, "EXPLORE_BIOME", (questId, objective) -> {
            final String required = objective.getString("biome", "");
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
        forEachActive(fighter, "WIN_RAID", (questId, objective) -> true);
    }

    public void handleBossKill(final Player killer) {
        forEachActive(killer, "KILL_WORLDBOSS", (questId, objective) -> true);
    }

    /** Whether an objective's material list contains this material (empty list = matches nothing). */
    private static boolean materialMatches(final ConfigurationSection objective, final Material material) {
        return objective.getStringList("materials").stream().anyMatch(name -> name.equalsIgnoreCase(material.name()));
    }

    /** Whether an objective's optional entity-type filter matches (blank filter = any). */
    private static boolean entityMatches(final ConfigurationSection objective, final EntityType entityType) {
        final String required = objective.getString("entity-type");
        return required == null || required.isBlank() || required.equalsIgnoreCase(entityType.name());
    }

    public void handleTerritoryEnter(final Player player, final String territoryId) {
        forEachActive(player, "VISIT_TERRITORY", (questId, objective) ->
                territoryId != null && territoryId.equalsIgnoreCase(objective.getString("territory", "")));

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
            if (quest == null) {
                continue;
            }

            final List<ConfigurationSection> objectives = getObjectiveSections(quest);
            final boolean sequence = isSequenceMode(quest);
            boolean changed = false;

            for (int index = 0; index < objectives.size(); index++) {
                final ConfigurationSection objective = objectives.get(index);
                if (isObjectiveComplete(player, questId, index, objective)
                        || !npcName.equalsIgnoreCase(objective.getString("npc", ""))) {
                    continue;
                }
                if (sequence && !isCurrentStep(player, questId, objectives, index)) {
                    continue;
                }

                final String type = objective.getString("type", "");
                if ("TALK_TO_NPC".equalsIgnoreCase(type)) {
                    setObjectiveProgress(player, questId, index, Math.max(1, objective.getInt("count", 1)));
                    changed = true;
                } else if ("DELIVER_ITEMS".equalsIgnoreCase(type)
                        && tryDeliver(player, questId, objective, index)) {
                    changed = true;
                }
            }

            if (changed && allObjectivesComplete(player, questId, objectives)) {
                complete(player, questId);
            }
        }
    }

    /**
     * Settles a DELIVER_ITEMS objective: takes the goods from the inventory if
     * enough is carried and marks the objective complete.
     *
     * @return true if the delivery succeeded (objective now complete)
     */
    private boolean tryDeliver(final Player player, final String questId,
                               final ConfigurationSection objective, final int index) {
        final List<String> materials = objective.getStringList("materials");
        if (materials.isEmpty()) {
            return false;
        }

        final int target = Math.max(1, objective.getInt("count", 1));
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
            return false;
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

        setObjectiveProgress(player, questId, index, target);
        return true;
    }

    /** The best NPC name to speak as when dialogue.speaker is not set. */
    private String dialogueSpeakerFallback(final ConfigurationSection quest) {
        for (final ConfigurationSection objective : getObjectiveSections(quest)) {
            final String npc = objective.getString("npc", "");
            if (!npc.isBlank()) {
                return npc;
            }
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

        // Branching choices appear after the give dialogue: clickable options that
        // accept the follow-up quest they point to (dialogue.choices.<N>.quest).
        if ("give".equalsIgnoreCase(phase)) {
            final ConfigurationSection choices = quest.getConfigurationSection("dialogue.choices");
            if (choices != null && !choices.getKeys(false).isEmpty()) {
                player.getScheduler().runDelayed(plugin, task -> sendChoices(player, choices), null,
                        30L * Math.max(1, lines.size()));
            }
        }
    }

    /** Renders clickable dialogue choices; each runs /quest accept for its target quest. */
    private void sendChoices(final Player player, final ConfigurationSection choices) {
        player.sendMessage(messageManager.getMessage("quest.choose-prompt", "<gray>Válassz:</gray>"));
        for (final String key : choices.getKeys(false).stream().sorted(Comparator.comparingInt(QuestManager::objectiveOrder)).toList()) {
            final ConfigurationSection choice = choices.getConfigurationSection(key);
            if (choice == null) {
                continue;
            }
            final String text = choice.getString("text", "...");
            final String target = choice.getString("quest", "");
            if (target.isBlank()) {
                continue;
            }
            player.sendMessage(net.kyori.adventure.text.Component.text("  ▸ " + text, net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/quest accept " + target))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            net.kyori.adventure.text.Component.text("Kattints a választáshoz", net.kyori.adventure.text.format.NamedTextColor.GRAY))));
        }
    }

    // ===== NPC quest-adók (giver-npc) =====

    /** The NPC name configured to hand out this quest, or null. */
    public String getGiverNpc(final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        final String npc = quest == null ? null : quest.getString("giver-npc");
        return npc == null || npc.isBlank() ? null : npc;
    }

    // ===== NPC napi rotáció =====

    /**
     * Whether a quest is on offer today. Quests without a {@code rotation-group}
     * are always offered; grouped quests rotate — each day a deterministic
     * subset of the group (sized by {@code rotation-daily-count}) is on offer,
     * so a single NPC can present a fresh handful of its pool every day.
     */
    public boolean isOfferedToday(final String questId) {
        final ConfigurationSection quest = getQuestSection(questId);
        if (quest == null) {
            return false;
        }
        final String group = quest.getString("rotation-group", "");
        return group.isBlank() || todaysRotation(group).contains(questId.toLowerCase(Locale.ROOT));
    }

    /** The deterministic set of quest ids from a rotation group offered on the current day. */
    private List<String> todaysRotation(final String group) {
        final List<String> pool = new ArrayList<>();
        int dailyCount = 2;
        for (final String questId : getQuestIds()) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest != null && group.equalsIgnoreCase(quest.getString("rotation-group", ""))) {
                pool.add(questId.toLowerCase(Locale.ROOT));
                dailyCount = Math.max(1, quest.getInt("rotation-daily-count", dailyCount));
            }
        }
        if (pool.size() <= dailyCount) {
            return pool;
        }

        pool.sort(Comparator.naturalOrder());
        // Local-date bucket (same rule as DailyQuestManager.today()): the rotation flips
        // at the server's LOCAL midnight, not at UTC midnight mid-day for non-UTC servers.
        final long daySeed = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toEpochDay();
        Collections.shuffle(pool, new Random(daySeed * 31L + group.toLowerCase(Locale.ROOT).hashCode()));
        return new ArrayList<>(pool.subList(0, dailyCount));
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

            // Any TALK_TO_NPC or DELIVER_ITEMS objective (across all steps) has a target NPC.
            for (final ConfigurationSection objective : getObjectiveSections(quest)) {
                final String talkTarget = objective.getString("npc");
                if (talkTarget != null && !talkTarget.isBlank()) {
                    names.add(talkTarget);
                }
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

    /** Whether the player has an active, still-open objective (talk/deliver) at this NPC. */
    public boolean hasTalkObjectiveAt(final Player player, final String npcName) {
        if (npcName == null) {
            return false;
        }

        for (final String questId : getActiveQuests(player)) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null) {
                continue;
            }
            final List<ConfigurationSection> objectives = getObjectiveSections(quest);
            final boolean sequence = isSequenceMode(quest);
            for (int index = 0; index < objectives.size(); index++) {
                final ConfigurationSection objective = objectives.get(index);
                if (!npcName.equalsIgnoreCase(objective.getString("npc", ""))
                        || isObjectiveComplete(player, questId, index, objective)) {
                    continue;
                }
                // In a sequence, an NPC step only "glows" once it becomes the current step.
                if (!sequence || isCurrentStep(player, questId, objectives, index)) {
                    return true;
                }
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

            final String accepted = tryAcceptAndAnnounce(player, questId, npcName);
            if (accepted != null) {
                return accepted;
            }
        }
        return null;
    }

    /**
     * Hands out a specific quest regardless of its configured {@code giver-npc} —
     * used by explicit {@code /npcbind <npc> quest <questId>} bindings, where the
     * admin already decided which NPC gives this quest out-of-band. Same
     * accept + announce + dialogue flow as {@link #acceptFromNpc}, just for a
     * single target id instead of scanning every quest for a name match.
     *
     * @param player the interacting player
     * @param questId the bound quest id
     * @param npcName the NPC's internal name (used for the "give" dialogue lookup)
     * @return the accepted quest id, or null if it could not be accepted right now
     */
    public String acceptBoundQuest(final Player player, final String questId, final String npcName) {
        if (questId == null || getAcceptBlocker(player, questId) != null) {
            return null;
        }
        return tryAcceptAndAnnounce(player, questId, npcName);
    }

    /** Accepts {@code questId} and — on success — plays the sound/message/dialogue trio. */
    private String tryAcceptAndAnnounce(final Player player, final String questId, final String npcName) {
        if (!accept(player, questId)) {
            return null;
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

    /**
     * Progresses PARKOUR_TRIAL quests when the player finishes a timed parkour
     * course. Wired into the ParkourManager finish hook.
     *
     * @param player the finishing player
     * @param courseId the completed course id
     */
    public void handleParkourFinish(final Player player, final String courseId) {
        forEachActive(player, "PARKOUR_TRIAL", (questId, objective) ->
                courseId != null && courseId.equalsIgnoreCase(objective.getString("course", "")));
    }

    /**
     * Re-checks every active REACH_LEVEL objective against the player's primary
     * class level (level objectives are satisfied by state, not by an event, so
     * they can't just increment). Wired into the JobManager XP-change hook and
     * fired on accept. Marks satisfied level-objectives complete and closes the
     * quest once all objectives are done.
     */
    public void handleLevelChange(final Player player) {
        for (final String questId : List.copyOf(getActiveQuests(player))) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null) {
                continue;
            }

            final List<ConfigurationSection> objectives = getObjectiveSections(quest);
            final boolean sequence = isSequenceMode(quest);
            boolean changed = false;

            for (int index = 0; index < objectives.size(); index++) {
                final ConfigurationSection objective = objectives.get(index);
                if (!"REACH_LEVEL".equalsIgnoreCase(objective.getString("type", ""))
                        || isObjectiveComplete(player, questId, index, objective)) {
                    continue;
                }
                if (sequence && !isCurrentStep(player, questId, objectives, index)) {
                    continue;
                }
                if (jobManager.getPrimaryLevel(player) >= Math.max(1, objective.getInt("level", 1))) {
                    setObjectiveProgress(player, questId, index, Math.max(1, objective.getInt("count", 1)));
                    changed = true;
                }
            }

            if (changed && allObjectivesComplete(player, questId, objectives)) {
                complete(player, questId);
            }
        }
    }

    /** A matcher receives the single OBJECTIVE section (not the whole quest). */
    private interface ObjectiveMatcher {
        boolean matches(String questId, ConfigurationSection objective);
    }

    private void forEachActive(final Player player, final String objectiveType, final ObjectiveMatcher matcher) {
        forEachActive(player, objectiveType, 1, matcher);
    }

    /**
     * The multi-objective progress engine. A quest may declare a single
     * {@code objective:} (legacy) or a numbered {@code objectives.1..N} list;
     * this walks every objective of the given type that is still open, adds
     * {@code amount} to its own counter, and completes the quest once ALL of
     * its objectives are done. In {@code objectives-mode: SEQUENCE} only the
     * current step (the first unfinished objective) accepts progress; the
     * default {@code ALL} mode advances every matching objective in parallel
     * (so "kill X mobs AND fetch Y items" both tick from their own events).
     */
    private void forEachActive(final Player player, final String objectiveType, final int amount,
                               final ObjectiveMatcher matcher) {
        for (final String questId : List.copyOf(getActiveQuests(player))) {
            final ConfigurationSection quest = getQuestSection(questId);
            if (quest == null) {
                continue;
            }

            final List<ConfigurationSection> objectives = getObjectiveSections(quest);
            final boolean sequence = isSequenceMode(quest);
            boolean changed = false;

            for (int index = 0; index < objectives.size(); index++) {
                final ConfigurationSection objective = objectives.get(index);
                if (!objectiveType.equalsIgnoreCase(objective.getString("type", ""))
                        || isObjectiveComplete(player, questId, index, objective)) {
                    continue;
                }
                if (sequence && !isCurrentStep(player, questId, objectives, index)) {
                    continue;
                }
                if (!matcher.matches(questId, objective)) {
                    continue;
                }

                final int target = Math.max(1, objective.getInt("count", 1));
                final int newProgress = Math.min(target, getObjectiveProgress(player, questId, index) + amount);
                setObjectiveProgress(player, questId, index, newProgress);
                changed = true;
                announceObjective(player, questId, quest, objectives, index, objective, newProgress, target);

                if (sequence) {
                    break; // only the current step advances per event in a sequence
                }
            }

            if (changed && allObjectivesComplete(player, questId, objectives)) {
                complete(player, questId);
            }
        }
    }

    /** Sends the per-objective progress action bar (compact for single-objective quests). */
    private void announceObjective(final Player player, final String questId, final ConfigurationSection quest,
                                   final List<ConfigurationSection> objectives, final int index,
                                   final ConfigurationSection objective, final int progress, final int target) {
        if (progress >= target && allObjectivesComplete(player, questId, objectives)) {
            return; // the completion message will fire from forEachActive
        }

        if (objectives.size() == 1) {
            player.sendActionBar(messageManager.getMessage(
                    "quest.progress",
                    "<gray>{quest}: <gold>{progress}</gold>/<gold>{target}</gold></gray>",
                    Map.of(
                            "quest", getDisplayName(questId),
                            "progress", String.valueOf(progress),
                            "target", String.valueOf(target)
                    )
            ));
            return;
        }

        player.sendActionBar(messageManager.getMessage(
                "quest.progress-multi",
                "<gray>{quest} — {objective}: <gold>{progress}</gold>/<gold>{target}</gold> <dark_gray>[{step}/{steps}]</dark_gray></gray>",
                Map.of(
                        "quest", getDisplayName(questId),
                        "objective", objectiveLabel(objective),
                        "progress", String.valueOf(progress),
                        "target", String.valueOf(target),
                        "step", String.valueOf(index + 1),
                        "steps", String.valueOf(objectives.size())
                )
        ));
    }

    // ===== Objektíva-absztrakció (több-objektívás támogatás) =====

    /**
     * Returns a quest's objectives in order. Supports both the legacy single
     * {@code objective:} section and the numbered {@code objectives.1..N} form;
     * an empty list means the quest has no runnable objective.
     */
    private List<ConfigurationSection> getObjectiveSections(final ConfigurationSection quest) {
        if (quest == null) {
            return List.of();
        }

        final ConfigurationSection multi = quest.getConfigurationSection("objectives");
        if (multi != null) {
            final List<ConfigurationSection> sections = multi.getKeys(false).stream()
                    .sorted(Comparator.comparingInt(QuestManager::objectiveOrder))
                    .map(multi::getConfigurationSection)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!sections.isEmpty()) {
                return sections;
            }
        }

        final ConfigurationSection single = quest.getConfigurationSection("objective");
        return single == null ? List.of() : List.of(single);
    }

    private static int objectiveOrder(final String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (final NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    /** How many objectives a quest declares (at least 1 for a well-formed quest). */
    public int getObjectiveTotal(final String questId) {
        return getObjectiveSections(getQuestSection(questId)).size();
    }

    private boolean isSequenceMode(final ConfigurationSection quest) {
        return "SEQUENCE".equalsIgnoreCase(quest.getString("objectives-mode", "ALL"));
    }

    /** Whether index is the current step in a SEQUENCE quest (all earlier objectives done). */
    private boolean isCurrentStep(final Player player, final String questId,
                                  final List<ConfigurationSection> objectives, final int index) {
        for (int earlier = 0; earlier < index; earlier++) {
            if (!isObjectiveComplete(player, questId, earlier, objectives.get(earlier))) {
                return false;
            }
        }
        return true;
    }

    private boolean allObjectivesComplete(final Player player, final String questId,
                                          final List<ConfigurationSection> objectives) {
        for (int index = 0; index < objectives.size(); index++) {
            if (!isObjectiveComplete(player, questId, index, objectives.get(index))) {
                return false;
            }
        }
        return !objectives.isEmpty();
    }

    private boolean isObjectiveComplete(final Player player, final String questId, final int index,
                                        final ConfigurationSection objective) {
        return getObjectiveProgress(player, questId, index) >= Math.max(1, objective.getInt("count", 1));
    }

    public int getObjectiveProgress(final Player player, final String questId, final int index) {
        return player.getPersistentDataContainer()
                .getOrDefault(objectiveProgressKey(questId, index), PersistentDataType.INTEGER, 0);
    }

    private void setObjectiveProgress(final Player player, final String questId, final int index, final int value) {
        player.getPersistentDataContainer().set(objectiveProgressKey(questId, index), PersistentDataType.INTEGER, value);
    }

    /** Objective 0 keeps the legacy key so in-flight single-objective quests survive the upgrade. */
    private NamespacedKey objectiveProgressKey(final String questId, final int index) {
        return index == 0 ? progressKey(questId)
                : new NamespacedKey(plugin, "quest_progress_" + sanitizeId(questId) + "_" + index);
    }

    /** A short human label for an objective, from its description or a type default. */
    private String objectiveLabel(final ConfigurationSection objective) {
        final String described = objective.getString("description", "");
        if (!described.isBlank()) {
            return described;
        }
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

    /**
     * A compact multi-objective progress summary for the /quest info line and
     * the quest menu (e.g. "Szörnyek 4/10 • Gyűjtés 2/5").
     */
    public String describeProgress(final Player player, final String questId) {
        // Rejtvény-quest: a cél SOSEM jelenik meg — a nyom a leírásban van, a
        // megfejtés a játékosé (vagy a közösségé). Nincs időzített súgás.
        final ConfigurationSection riddleQuest = getQuestSection(questId);
        if (riddleQuest != null && riddleQuest.getBoolean("riddle", false)) {
            return "??? — a nyomot a leírás rejti";
        }
        final List<ConfigurationSection> objectives = getObjectiveSections(getQuestSection(questId));
        if (objectives.isEmpty()) {
            return getProgress(player, questId) + "/" + getObjectiveCount(questId);
        }
        if (objectives.size() == 1) {
            final ConfigurationSection objective = objectives.get(0);
            return getObjectiveProgress(player, questId, 0) + "/" + Math.max(1, objective.getInt("count", 1));
        }

        final StringBuilder summary = new StringBuilder();
        for (int index = 0; index < objectives.size(); index++) {
            final ConfigurationSection objective = objectives.get(index);
            if (index > 0) {
                summary.append(" • ");
            }
            summary.append(objectiveLabel(objective)).append(' ')
                    .append(Math.min(getObjectiveProgress(player, questId, index), Math.max(1, objective.getInt("count", 1))))
                    .append('/').append(Math.max(1, objective.getInt("count", 1)));
        }
        return summary.toString();
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
        clearAllProgress(player, questId);
        if (statsManager != null) {
            statsManager.recordQuestComplete(player.getUniqueId());
        }
        // Céh-XP a tag-aktivitásból: minden quest-teljesítés a céhet is építi.
        final GuildManager guildRef = guildManager;
        if (guildRef != null) {
            guildRef.addActivityXp(player, Math.max(0, configManager.getInt("guilds.xp-per-quest", 10)));
        }
        // Repeatable-cooldown anchor + seasonal anchor: when / in which season was it turned in.
        player.getPersistentDataContainer().set(doneAtKey(questId), PersistentDataType.LONG, System.currentTimeMillis());
        player.getPersistentDataContainer().set(seasonKey(questId), PersistentDataType.LONG, currentSeasonId());

        applyRewards(player, quest);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        player.sendMessage(messageManager.getMessage(
                "quest.completed",
                "<gold>✔ Küldetés teljesítve: <white>{quest}</white>!</gold>",
                Map.of("quest", getDisplayName(questId))
        ));
        // Vanília advancement-toast a jobb felső sarokban (a chat-üzenet mellett).
        if (configManager.getBoolean("quest-toast.enabled", true)) {
            hu.taliann.icesmp.utils.ToastUtil.show(plugin, player,
                    hu.taliann.icesmp.utils.ToastUtil.Kind.QUEST);
        }

        advanceChain(player, quest);
    }

    /**
     * Linear auto-chain: if the just-completed quest names a {@code next} quest
     * id, it is accepted for the player right away (subject to the normal
     * accept-blockers, so requirement mismatches or an already-active/completed
     * next link simply skip silently). Used by story sequences that shouldn't
     * need an NPC visit or territory crossing between every link — e.g. the
     * first-join onboarding chain.
     */
    private void advanceChain(final Player player, final ConfigurationSection completedQuest) {
        final String next = completedQuest.getString("next");
        if (next == null || next.isBlank() || getAcceptBlocker(player, next) != null || !accept(player, next)) {
            return;
        }

        final ConfigurationSection nextQuest = getQuestSection(next);
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1.0F, 1.2F);
        player.sendMessage(messageManager.getMessage(
                "quest.auto-started",
                "<gold>❕ Új küldetés indult: <white>{quest}</white> <gray>— {description}</gray></gold>",
                Map.of(
                        "quest", getDisplayName(next),
                        "description", nextQuest == null ? "" : nextQuest.getString("description", "")
                )
        ));
        sendDialogue(player, next, "give", dialogueSpeakerFallback(nextQuest));
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
                currencyManager.payOutTokens(player, currencyType, Math.round(amount));
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

        // Crate-key reward: "<crateId>:<darab>", pl. "koznapi:1".
        final String crateKeyReward = quest.getString("rewards.crate-key");
        if (crateKeyReward != null && !crateKeyReward.isBlank()) {
            grantCrateKeyReward(player, crateKeyReward);
        }

        // The penance chain's final mercy: even the dark pact can be broken.
        if (quest.getBoolean("rewards.cleanse-sins", false)) {
            sinManager.breakDarkPact(player);
            // A DARK-kapus spec (Nekromanta, Szentségtelen, jövőbeliek) nem élhet tovább
            // a paktum nélkül — a vezeklés a specet is elengedi (a kaszt marad).
            final SpecializationManager specs = this.specializationManagerRef;
            if (specs != null) {
                final hu.taliann.icesmp.data.SpecializationType current = specs.getClassSpecialization(player);
                if (current != null && (current.getRequiredFaction() == hu.taliann.icesmp.data.FactionType.DARK
                        || current.requiresSinner())) {
                    specs.resetClassSpecialization(player);
                    player.sendMessage(messageManager.getMessage("penance-spec-reset",
                            "<yellow>A vezekléssel a sötét út is lezárult: a specializációd elhagyott téged. Új utat választhatsz.</yellow>"));
                }
            }
        }
    }

    /**
     * Grants a {@code "<crateId>:<darab>"} quest reward via the injected
     * {@link CrateKeyFactory} — null-safe: if it was never bound (a server disabling the
     * native crate system, or a manual-DI ordering slip), this just warns once to the
     * console instead of throwing, and the rest of the quest's rewards still apply.
     */
    private void grantCrateKeyReward(final Player player, final String crateKeyReward) {
        final CrateKeyFactory factory = crateKeyFactory;
        if (factory == null) {
            if (!warnedMissingCrateKeyFactory) {
                warnedMissingCrateKeyFactory = true;
                plugin.getLogger().warning("Quest 'rewards.crate-key' mező van beállítva, de a CrateKeyFactory "
                        + "nincs bekötve (QuestManager#setCrateKeyFactory) — a kulcs-jutalom kimarad.");
            }
            return;
        }

        final String[] parts = crateKeyReward.split(":");
        final String crateId = parts[0].trim();
        int amount = 1;
        if (parts.length >= 2) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (final NumberFormatException ignored) {
                // Malformed amount: give one.
            }
        }

        final org.bukkit.inventory.ItemStack key = factory.createKey(crateId, amount);
        if (key.getType().isAir()) {
            return; // Unknown crate id — config typo, skip rather than hand out a phantom item.
        }
        final Map<Integer, org.bukkit.inventory.ItemStack> leftovers = player.getInventory().addItem(key);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
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

    /**
     * Stabil szezon-azonosító: a kezdő-bélyeg. A getSeasonEndMillis() élő configból
     * számolódik — egy length-days átírás szezon közben minden teljesített szezonális
     * questet újranyitna; a seasonStart csak tényleges szezonváltáskor mozdul.
     */
    private long currentSeasonId() {
        return seasonManager == null ? 0L : seasonManager.getSeasonStart();
    }

    private long getCompletedSeason(final Player player, final String questId) {
        return player.getPersistentDataContainer().getOrDefault(seasonKey(questId), PersistentDataType.LONG, -1L);
    }

    private NamespacedKey seasonKey(final String questId) {
        return new NamespacedKey(plugin, "quest_season_" + sanitizeId(questId));
    }

    private static String sanitizeId(final String questId) {
        return questId == null ? "unknown" : questId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private NamespacedKey doneAtKey(final String questId) {
        return new NamespacedKey(plugin, "quest_done_at_" + sanitizeId(questId));
    }

    private NamespacedKey progressKey(final String questId) {
        return new NamespacedKey(plugin, "quest_progress_" + sanitizeId(questId));
    }

    /** Wipes every objective's progress counter for a quest (all indices). */
    private void clearAllProgress(final Player player, final String questId) {
        final int total = Math.max(1, getObjectiveSections(getQuestSection(questId)).size());
        for (int index = 0; index < total; index++) {
            player.getPersistentDataContainer().remove(objectiveProgressKey(questId, index));
        }
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
