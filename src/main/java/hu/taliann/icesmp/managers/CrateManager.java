package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.crates.CrateAuditWriter;
import hu.taliann.icesmp.crates.CrateCommandBatch;
import hu.taliann.icesmp.crates.CrateFormatting;
import hu.taliann.icesmp.crates.CrateLedger;
import hu.taliann.icesmp.crates.CrateOpeningLifecycle;
import hu.taliann.icesmp.crates.CrateRecoveryLedger;
import hu.taliann.icesmp.crates.CrateRewardProgress;
import hu.taliann.icesmp.crates.CrateTaskSubmission;
import hu.taliann.icesmp.crates.CrateSoundResolver;
import hu.taliann.icesmp.crates.CrateRules;
import hu.taliann.icesmp.crates.KeyConsumption;
import hu.taliann.icesmp.crates.WeightedSelector;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.gui.CrateBrowserGUI;
import hu.taliann.icesmp.gui.CrateSpinGUI;
import hu.taliann.icesmp.items.BlueprintItemFactory;
import hu.taliann.icesmp.items.CrateKeyFactory;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileCrateStore;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.StatisticsSection;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.listeners.ProfessionRecipeBookListener;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.CriticalPersistenceWriteError;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

/** IceSMP-native physical crate, reward, cooldown and statistics owner. */
public final class CrateManager implements PersistentStore, PlayerStateCleanup {

    private static final int SCHEMA = 2;
    private static final int LEGACY_SCHEMA = 1;
    private static final long AUDIT_ROTATE_BYTES = 5L * 1024L * 1024L;
    private static final double MAX_KEY_PRICE = 1_000_000_000.0D;
    private static final int MAX_CRATES = 128;

    public enum RewardType {
        ITEM,
        COMMAND,
        CURRENCY,
        UNIQUE_ITEM,
        RECIPE_ITEM,
        BLUEPRINT,
        CRATE_KEY
    }

    /** Fully validated immutable reward entry. */
    public record RewardEntry(double weight, RewardType type, String value, int amount,
                              CurrencyType currency, double currencyAmount, String command,
                              String description, Material iconMaterial) {
        public RewardEntry {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(iconMaterial, "iconMaterial");
        }
    }

    public record RewardOdds(RewardEntry reward, String description, double percent) {
    }

    /** Fully validated immutable per-crate config snapshot. */
    public record CrateDefinition(String id, boolean enabled, String displayName,
                                  Material keyMaterial, String keyName, String keyItemModel,
                                  CurrencyType keyPriceCurrency, double keyPriceAmount,
                                  String permission, Set<String> worlds, int requiredKeys,
                                  long cooldownMillis, boolean massOpenEnabled, int massOpenMaximum,
                                  Sound openingSound, float soundVolume, float soundPitch,
                                  boolean broadcastEnabled, String broadcastMessage,
                                  List<RewardEntry> rewards) {
        public CrateDefinition {
            worlds = Set.copyOf(worlds);
            rewards = List.copyOf(rewards);
        }

        public boolean allowsWorld(final String worldName) {
            return worlds.isEmpty() || (worldName != null && worlds.contains(worldName.toLowerCase(Locale.ROOT)));
        }

        public boolean hasCurrencyReward() {
            return rewards.stream().anyMatch(reward -> reward.type() == RewardType.CURRENCY);
        }
    }

    public record StatsView(UUID playerId, String lastKnownName, long total, Map<String, Long> perCrate) {
        public StatsView {
            perCrate = Map.copyOf(perCrate);
        }
    }

    public record MutationResult(boolean success, String errorKey) {
        public static MutationResult ok() {
            return new MutationResult(true, null);
        }

        public static MutationResult fail(final String key) {
            return new MutationResult(false, key);
        }
    }

    public record PurchaseResult(boolean success, String errorKey, String crateId, String displayName,
                                 int amount, double totalPrice, CurrencyType currency) {
        private static PurchaseResult fail(final String key, final String crateId) {
            return new PurchaseResult(false, key, crateId, crateId, 0, 0.0D, CurrencyType.NEUTRAL);
        }
    }

    public record AccessDecision(boolean allowed, String errorKey) {
        private static AccessDecision allow() {
            return new AccessDecision(true, null);
        }

        private static AccessDecision deny(final String key) {
            return new AccessDecision(false, key);
        }
    }

    private static final class PendingOpen {
        private final UUID openingId = UUID.randomUUID();
        private final UUID playerId;
        private final String playerName;
        private final String crateId;
        private final ConfigSnapshot snapshot;
        private final CrateDefinition definition;
        private final int opens;
        private final int keysRequired;
        private final StoredLocation source;
        private final List<RewardEntry> rewards;
        private final CrateOpeningLifecycle lifecycle = new CrateOpeningLifecycle();
        private CrateLedger.Mutation ledgerMutation;
        private List<List<ItemStack>> resolvedItems = List.of();
        private Map<CurrencyType, Double> currencyRewards = Map.of();
        private List<String> commands = List.of();
        private CurrencyManager.DurableMutation currencyMutation;
        private boolean keysConsumed;
        private boolean irreversibleFence;
        private boolean commandTaskClaimed;
        private boolean compensationStarted;
        private boolean grantCounted;
        private int successfulCommands;
        private int successfulItems;

        private PendingOpen(final Player player, final String crateId, final ConfigSnapshot snapshot,
                            final CrateDefinition definition, final int opens, final int keysRequired,
                            final StoredLocation source, final List<RewardEntry> rewards) {
            this.playerId = player.getUniqueId();
            this.playerName = player.getName();
            this.crateId = crateId;
            this.snapshot = snapshot;
            this.definition = definition;
            this.opens = opens;
            this.keysRequired = keysRequired;
            this.source = source;
            this.rewards = List.copyOf(rewards);
        }
    }

    private record StoredLocation(UUID worldId, String worldName, int x, int y, int z) {
        private static StoredLocation from(final Location location) {
            final World world = Objects.requireNonNull(location.getWorld(), "world");
            return new StoredLocation(world.getUID(), world.getName(), location.getBlockX(),
                    location.getBlockY(), location.getBlockZ());
        }

        private String describe() {
            return worldName + " (" + x + ", " + y + ", " + z + ")";
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof StoredLocation location
                    && worldId.equals(location.worldId) && x == location.x && y == location.y && z == location.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldId, x, y, z);
        }
    }

    private record ConfigSnapshot(long generation, boolean enabled, boolean spinAnimation,
                                  Map<String, CrateDefinition> definitions, List<String> errors) {
        private ConfigSnapshot {
            definitions = Map.copyOf(definitions);
            errors = List.copyOf(errors);
        }

        private static ConfigSnapshot disabled() {
            return new ConfigSnapshot(0L, false, false, Map.of(), List.of("A crate config még nincs betöltve."));
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final CrateKeyFactory crateKeyFactory;
    private final UniqueMaterialFactory uniqueMaterialFactory;
    private final ProfessionRecipeCatalog recipeCatalog;
    private final ProfessionRecipeBookListener recipeBuilder;
    private final BlueprintItemFactory blueprintFactory;
    private final MessageManager messageManager;
    private final File storageFile;
    private final File auditFile;

    private final Object stateLock = new Object();
    private final Map<StoredLocation, String> crateBlocks = new ConcurrentHashMap<>();
    private final CrateLedger ledger = new CrateLedger();
    private final PlayerProfileCrateStore profileCrateStore = new PlayerProfileCrateStore();
    private final CrateRecoveryLedger recoveryLedger = new CrateRecoveryLedger();
    private final CrateAuditWriter auditWriter;
    private final Map<UUID, PendingOpen> pendingOpens = new HashMap<>();
    private final AtomicLong configGeneration = new AtomicLong();
    private final AtomicInteger inFlightGrants = new AtomicInteger();
    private final ConcurrentLinkedQueue<CurrencyManager.DurableMutation> deferredCurrencyRollbacks =
            new ConcurrentLinkedQueue<>();
    private volatile ConfigSnapshot configSnapshot = ConfigSnapshot.disabled();
    private volatile boolean acceptingOpens = true;

    public CrateManager(final JavaPlugin plugin, final ConfigManager configManager,
                        final CurrencyManager currencyManager, final CrateKeyFactory crateKeyFactory,
                        final UniqueMaterialFactory uniqueMaterialFactory,
                        final ProfessionRecipeCatalog recipeCatalog,
                        final ProfessionRecipeBookListener recipeBuilder,
                        final BlueprintItemFactory blueprintFactory,
                        final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.crateKeyFactory = crateKeyFactory;
        this.uniqueMaterialFactory = uniqueMaterialFactory;
        this.recipeCatalog = recipeCatalog;
        this.recipeBuilder = recipeBuilder;
        this.blueprintFactory = blueprintFactory;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "crates-data.yml");
        this.auditFile = new File(new File(plugin.getDataFolder(), "logs"), "crate-openings.log");
        this.auditWriter = new CrateAuditWriter(auditFile.toPath(), AUDIT_ROTATE_BYTES);
        YamlStore.registerCriticalWrite(storageFile);
        crateKeyFactory.bind(this);
    }

    // ==================== config snapshot ====================

    /** Rebuilds the immutable crate snapshot; one bad crate is disabled without stopping IceSMP. */
    public void reloadConfig() {
        final long generation = configGeneration.incrementAndGet();
        final List<String> errors = new ArrayList<>();
        final Map<String, CrateDefinition> definitions = new LinkedHashMap<>();
        final ConfigurationSection configuration = configManager.getConfiguration();
        final ConfigurationSection root = configuration == null ? null
                : configuration.getConfigurationSection("crates");
        final boolean globallyEnabled;
        final boolean spin;
        try {
            globallyEnabled = CrateRules.strictBoolean(configuration == null ? null
                    : configuration.get("crates-settings.enabled"), true, "crates-settings.enabled");
            spin = CrateRules.strictBoolean(configuration == null ? null
                    : configuration.get("crates-settings.spin-animation"), true,
                    "crates-settings.spin-animation");
        } catch (final IllegalArgumentException invalidGlobal) {
            errors.add(invalidGlobal.getMessage());
            configSnapshot = new ConfigSnapshot(generation, false, false, Map.of(), errors);
            plugin.getLogger().warning("Crate config: " + invalidGlobal.getMessage());
            return;
        }

        if (root == null || root.getKeys(false).isEmpty()) {
            errors.add("config/crates.yml: a crates szekció üres vagy hiányzik");
        } else if (root.getKeys(false).size() > MAX_CRATES) {
            errors.add("config/crates.yml: legfeljebb " + MAX_CRATES + " crate definiálható");
        } else {
            for (final String rawId : root.getKeys(false)) {
                final String id = CrateRules.normalizeId(rawId);
                if (id == null || !id.equals(rawId)) {
                    errors.add("crates." + rawId + ": az id csak kisbetűs [a-z0-9_-] lehet");
                    continue;
                }
                final ConfigurationSection section = root.getConfigurationSection(rawId);
                if (section == null) {
                    errors.add("crates." + rawId + ": nem objektum");
                    continue;
                }
                try {
                    definitions.put(id, parseDefinition(id, section));
                } catch (final IllegalArgumentException invalid) {
                    errors.add("crates." + id + ": " + invalid.getMessage());
                }
            }
        }

        boolean changed;
        do {
            changed = false;
            for (final CrateDefinition definition : List.copyOf(definitions.values())) {
                for (final RewardEntry reward : definition.rewards()) {
                    if (reward.type() == RewardType.CRATE_KEY && !definitions.containsKey(reward.value())) {
                        errors.add("crates." + definition.id() + ": ismeretlen crate-key cél: " + reward.value());
                        definitions.remove(definition.id());
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        final ConfigSnapshot next = new ConfigSnapshot(generation,
                globallyEnabled && !definitions.isEmpty(), spin, definitions, errors);
        configSnapshot = next;
        hu.taliann.icesmp.core.Permissions.registerCratePermissions(definitions.values().stream()
                .map(CrateDefinition::permission).filter(permission -> permission != null && !permission.isBlank()).toList());
        if (errors.isEmpty()) {
            plugin.getLogger().info("Natív crate config betöltve: " + definitions.size() + " láda.");
        } else {
            errors.forEach(error -> plugin.getLogger().warning("Crate config: " + error));
            plugin.getLogger().warning("Érvényes crate-ek: " + definitions.size()
                    + "; a hibás crate-ek fail-safe letiltva.");
        }
    }

    private CrateDefinition parseDefinition(final String id, final ConfigurationSection section) {
        final boolean enabled = CrateRules.strictBoolean(section.get("enabled"), true, "enabled");
        final String displayName = requiredText(section.getString("display-name"), "display-name");
        final Material keyMaterial = Material.matchMaterial(requiredText(section.getString("key-material"), "key-material"));
        if (keyMaterial == null || keyMaterial.isAir()) {
            throw new IllegalArgumentException("ismeretlen vagy AIR key-material");
        }
        final String keyName = requiredText(section.getString("key-name"), "key-name");
        final String keyItemModel = blankToNull(section.getString("key-item-model"));

        final CurrencyType priceCurrency = CurrencyType.fromInput(section.getString("key-price.currency", "NEUTRAL"));
        final double priceAmount = CrateRules.finiteDecimal(section.get("key-price.amount"), 0.0D,
                0.0D, MAX_KEY_PRICE, "key-price.amount");
        if (priceCurrency == null) {
            throw new IllegalArgumentException("érvénytelen key-price currency/amount");
        }

        final String permission = blankToNull(section.getString("permission"));
        if (permission != null && (!permission.startsWith("icesmp.") || permission.contains(" ")
                || permission.length() > 96)) {
            throw new IllegalArgumentException("a permission kötelezően icesmp.* névtérben legyen");
        }

        final Set<String> worlds = new LinkedHashSet<>();
        for (final String configuredWorld : CrateRules.strictStringList(section.get("worlds"), "worlds")) {
            final World world = Bukkit.getWorld(configuredWorld);
            if (world == null) {
                throw new IllegalArgumentException("ismeretlen világ a worlds listában: " + configuredWorld);
            }
            worlds.add(world.getName().toLowerCase(Locale.ROOT));
        }

        final int requiredKeys = CrateRules.boundedPositiveInt(section.get("required-key-count"), 1,
                CrateRules.MAX_REQUIRED_KEYS, "required-key-count");
        final long cooldownMillis = CrateRules.cooldownMillis(section.get("cooldown-seconds"));
        final boolean massEnabled = CrateRules.strictBoolean(section.get("mass-open.enabled"), false, "mass-open.enabled");
        final int massMaximum = CrateRules.boundedPositiveInt(section.get("mass-open.max-openings"), 10,
                CrateRules.MAX_MASS_OPEN, "mass-open.max-openings");

        final String soundName = requiredText(section.getString("opening-sound.sound",
                "ENTITY_PLAYER_LEVELUP"), "opening-sound.sound");
        final Sound openingSound = CrateSoundResolver.resolve(soundName);
        if (openingSound == null) {
            throw new IllegalArgumentException("ismeretlen opening-sound: " + soundName);
        }
        final double volumeRaw = CrateRules.finiteDecimal(section.get("opening-sound.volume"),
                1.0D, 0.0D, 10.0D, "opening-sound.volume");
        final double pitchRaw = CrateRules.finiteDecimal(section.get("opening-sound.pitch"),
                1.2D, 0.0D, 2.0D, "opening-sound.pitch");

        final boolean broadcast = CrateRules.strictBoolean(section.get("broadcast.enabled"),
                false, "broadcast.enabled");
        final String broadcastMessage = requiredText(section.getString("broadcast.message",
                "&6[Láda] &f{player} &ekinyitotta: &f{crate} &7({amount}×)"), "broadcast.message");

        final Object rewardsNode = section.get("rewards");
        if (!(rewardsNode instanceof List<?> rawRewards) || rawRewards.isEmpty()
                || rawRewards.size() > CrateRules.MAX_REWARDS) {
            throw new IllegalArgumentException("a rewards csak 1.." + CrateRules.MAX_REWARDS
                    + " elemű objektumlista lehet");
        }
        final List<RewardEntry> rewards = new ArrayList<>();
        for (int index = 0; index < rawRewards.size(); index++) {
            try {
                if (!(rawRewards.get(index) instanceof Map<?, ?> rewardMap)) {
                    throw new IllegalArgumentException("nem objektum");
                }
                rewards.add(parseReward(id, rewardMap));
            } catch (final IllegalArgumentException invalid) {
                throw new IllegalArgumentException("rewards[" + index + "]: " + invalid.getMessage());
            }
        }
        return new CrateDefinition(id, enabled, displayName, keyMaterial, keyName, keyItemModel,
                priceCurrency, priceAmount, permission, worlds, requiredKeys, cooldownMillis,
                massEnabled, massMaximum, openingSound, (float) volumeRaw, (float) pitchRaw,
                broadcast, broadcastMessage, rewards);
    }

    private RewardEntry parseReward(final String crateId, final Map<?, ?> raw) {
        final double weight = CrateRules.positiveWeight(raw.get("weight"));
        final String typeRaw = requiredText(stringValue(raw.get("type")), "type").toUpperCase(Locale.ROOT)
                .replace('-', '_');
        final RewardType type;
        try {
            type = RewardType.valueOf(typeRaw);
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("ismeretlen reward type: " + typeRaw);
        }
        final String description = blankToNull(stringValue(raw.get("description")));
        return switch (type) {
            case ITEM -> {
                final Material material = Material.matchMaterial(requiredText(stringValue(raw.get("material")), "material"));
                if (material == null || material.isAir()) {
                    throw new IllegalArgumentException("ismeretlen vagy AIR material");
                }
                final int amount = CrateRules.itemAmount(raw.get("amount"), 1);
                yield new RewardEntry(weight, type, material.name(), amount, null, 0.0D,
                        null, description, material);
            }
            case COMMAND -> {
                final String command = CrateRules.validateCommand(stringValue(raw.get("command")));
                yield new RewardEntry(weight, type, null, 1, null, 0.0D,
                        command, description, Material.COMMAND_BLOCK);
            }
            case CURRENCY -> {
                final CurrencyType currency = CurrencyType.fromInput(stringValue(raw.get("currency")));
                if (currency == null) {
                    throw new IllegalArgumentException("ismeretlen currency");
                }
                final double amount = CrateRules.currencyAmount(raw.get("amount"));
                yield new RewardEntry(weight, type, currency.name(), 1, currency, amount,
                        null, description, Material.EMERALD);
            }
            case UNIQUE_ITEM -> {
                final String id = normalizedReference(raw.get("id"), "id");
                if (!uniqueMaterialFactory.isDefined(id)) {
                    throw new IllegalArgumentException("ismeretlen unique item: " + id);
                }
                final int amount = CrateRules.itemAmount(raw.get("amount"), 1);
                final ItemStack icon = uniqueMaterialFactory.create(id, 1);
                if (icon == null || icon.getType().isAir()) {
                    throw new IllegalArgumentException("a unique item nem építhető: " + id);
                }
                yield new RewardEntry(weight, type, id, amount, null, 0.0D,
                        null, description, icon.getType());
            }
            case RECIPE_ITEM -> {
                final String id = normalizedReference(raw.get("id"), "id");
                final ProfessionRecipeCatalog.Recipe recipe = recipeCatalog.get(id);
                if (recipe == null) {
                    throw new IllegalArgumentException("ismeretlen profession recipe: " + id);
                }
                final int amount = CrateRules.boundedPositiveInt(raw.get("amount"), 1,
                        CrateRules.MAX_RECIPE_REWARD_AMOUNT, "amount");
                yield new RewardEntry(weight, type, id, amount, null, 0.0D,
                        null, description, recipe.result());
            }
            case BLUEPRINT -> {
                final String id = normalizedReference(raw.get("id"), "id");
                final ProfessionRecipeCatalog.Recipe recipe = recipeCatalog.get(id);
                if (recipe == null || !recipe.blueprint()) {
                    throw new IllegalArgumentException("ismeretlen vagy nem tervrajzos recept: " + id);
                }
                final int amount = CrateRules.itemAmount(raw.get("amount"), 1);
                yield new RewardEntry(weight, type, id, amount, null, 0.0D,
                        null, description, Material.KNOWLEDGE_BOOK);
            }
            case CRATE_KEY -> {
                final String id = normalizedReference(raw.get("id"), "id");
                final int amount = CrateRules.itemAmount(raw.get("amount"), 1);
                yield new RewardEntry(weight, type, id, amount, null, 0.0D,
                        null, description, Material.TRIPWIRE_HOOK);
            }
        };
    }

    private static String normalizedReference(final Object raw, final String field) {
        final String value = CrateRules.normalizeId(stringValue(raw));
        if (value == null) {
            throw new IllegalArgumentException("érvénytelen " + field);
        }
        return value;
    }

    private static String requiredText(final String raw, final String field) {
        if (raw == null || raw.isBlank() || raw.length() > 512) {
            throw new IllegalArgumentException("hiányzó vagy túl hosszú " + field);
        }
        return raw.strip();
    }

    private static String blankToNull(final String raw) {
        return raw == null || raw.isBlank() ? null : raw.strip();
    }

    private static String stringValue(final Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    // ==================== metadata and GUI ====================

    public List<String> crateIds() {
        return List.copyOf(configSnapshot.definitions().keySet());
    }

    public CrateDefinition definition(final String crateId) {
        return crateId == null ? null : configSnapshot.definitions().get(crateId.toLowerCase(Locale.ROOT));
    }

    public List<String> configErrors() {
        return configSnapshot.errors();
    }

    public String displayName(final String crateId) {
        final CrateDefinition definition = definition(crateId);
        return definition == null ? crateId : definition.displayName();
    }

    public Material keyMaterial(final String crateId) {
        final CrateDefinition definition = definition(crateId);
        return definition == null ? Material.AIR : definition.keyMaterial();
    }

    public String keyName(final String crateId) {
        final CrateDefinition definition = definition(crateId);
        return definition == null ? "&fLáda Kulcs" : definition.keyName();
    }

    public String keyItemModel(final String crateId) {
        final CrateDefinition definition = definition(crateId);
        return definition == null ? null : definition.keyItemModel();
    }

    public CurrencyType keyPriceCurrency(final String crateId) {
        final CrateDefinition definition = definition(crateId);
        return definition == null ? CurrencyType.NEUTRAL : definition.keyPriceCurrency();
    }

    public double keyPriceAmount(final String crateId) {
        final CrateDefinition definition = definition(crateId);
        return definition == null ? 0.0D : definition.keyPriceAmount();
    }

    public List<RewardOdds> rewardOdds(final String crateId) {
        final CrateDefinition definition = definition(crateId);
        if (definition == null) {
            return List.of();
        }
        final double total = definition.rewards().stream().mapToDouble(RewardEntry::weight).sum();
        return definition.rewards().stream()
                .map(reward -> new RewardOdds(reward, describeReward(reward), reward.weight() / total * 100.0D))
                .toList();
    }

    public long cooldownRemaining(final UUID playerId, final String crateId) {
        synchronized (stateLock) {
            return ledger.remainingCooldown(playerId, crateId, System.currentTimeMillis());
        }
    }

    public int keyCount(final Player player, final String crateId) {
        int total = 0;
        for (final ItemStack stack : player.getInventory().getStorageContents()) {
            if (crateId.equals(crateKeyFactory.keyCrateId(stack))) {
                total = Math.addExact(total, stack.getAmount());
            }
        }
        return total;
    }

    public AccessDecision accessDecision(final Player player, final CrateDefinition definition) {
        if (player == null || !configSnapshot.enabled() || definition == null || !definition.enabled()) {
            return AccessDecision.deny("crate-unknown");
        }
        if (!player.hasPermission(hu.taliann.icesmp.core.Permissions.CRATE_USE)
                || (definition.permission() != null && !player.hasPermission(definition.permission()))) {
            return AccessDecision.deny("crate-no-permission");
        }
        if (!definition.allowsWorld(player.getWorld().getName())) {
            return AccessDecision.deny("crate-world-disabled");
        }
        return AccessDecision.allow();
    }

    public boolean canAccess(final Player player, final CrateDefinition definition) {
        return accessDecision(player, definition).allowed();
    }

    public List<String> accessibleCrateIds(final Player player) {
        return configSnapshot.definitions().values().stream()
                .filter(definition -> accessDecision(player, definition).allowed())
                .map(CrateDefinition::id).toList();
    }

    public void openBrowser(final Player player) {
        CrateBrowserGUI.openList(player, this, currencyManager);
    }

    public boolean openPreview(final Player player, final String crateId) {
        final CrateDefinition definition = definition(crateId);
        if (!accessDecision(player, definition).allowed()) {
            return false;
        }
        CrateBrowserGUI.openPreview(player, this, definition.id());
        return true;
    }

    // ==================== persistent crate registry ====================

    public void setCrateAsync(final Location location, final String crateId,
                              final Consumer<MutationResult> callback) {
        final CrateDefinition definition = definition(crateId);
        if (location == null || location.getWorld() == null || definition == null) {
            callback.accept(MutationResult.fail("crate-unknown"));
            return;
        }
        final StoredLocation stored = StoredLocation.from(location);
        submitAsync(() -> {
            synchronized (stateLock) {
                final String previous = crateBlocks.put(stored, definition.id());
                if (!writeStateLocked()) {
                    if (previous == null) {
                        crateBlocks.remove(stored);
                    } else {
                        crateBlocks.put(stored, previous);
                    }
                    callback.accept(MutationResult.fail("crate-storage-unavailable"));
                    return;
                }
            }
            callback.accept(MutationResult.ok());
        }, () -> callback.accept(MutationResult.fail("crate-storage-unavailable")));
    }

    public void removeCrateAsync(final Location location, final Consumer<MutationResult> callback) {
        if (location == null || location.getWorld() == null) {
            callback.accept(MutationResult.fail("crate-not-a-crate"));
            return;
        }
        final StoredLocation stored = StoredLocation.from(location);
        submitAsync(() -> {
            synchronized (stateLock) {
                final String previous = crateBlocks.remove(stored);
                if (previous == null) {
                    callback.accept(MutationResult.fail("crate-not-a-crate"));
                    return;
                }
                if (!writeStateLocked()) {
                    crateBlocks.put(stored, previous);
                    callback.accept(MutationResult.fail("crate-storage-unavailable"));
                    return;
                }
            }
            callback.accept(MutationResult.ok());
        }, () -> callback.accept(MutationResult.fail("crate-storage-unavailable")));
    }

    public String crateAt(final Location location) {
        return location == null || location.getWorld() == null ? null : crateBlocks.get(StoredLocation.from(location));
    }

    public List<String> listCrates() {
        return crateBlocks.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().describe()))
                .map(entry -> entry.getValue() + " @ " + entry.getKey().describe())
                .toList();
    }

    // ==================== keys and opening ====================

    public void buyKeyAsync(final Player player, final String crateId, final int amount,
                            final Consumer<PurchaseResult> callback) {
        final ConfigSnapshot snapshot = configSnapshot;
        final String normalized = CrateRules.normalizeId(crateId);
        final CrateDefinition definition = normalized == null ? null : snapshot.definitions().get(normalized);
        final AccessDecision access = accessDecision(player, definition);
        if (!access.allowed()) {
            callback.accept(PurchaseResult.fail(access.errorKey(), normalized));
            return;
        }
        if (amount <= 0 || amount > CrateRules.MAX_KEY_AMOUNT) {
            callback.accept(PurchaseResult.fail("crate-invalid-amount", normalized));
            return;
        }
        final double totalPrice = definition.keyPriceAmount() * amount;
        if (!Double.isFinite(totalPrice) || totalPrice > MAX_KEY_PRICE * CrateRules.MAX_KEY_AMOUNT) {
            callback.accept(PurchaseResult.fail("crate-invalid-amount", normalized));
            return;
        }
        final List<ItemStack> keys = buildKeyStacks(definition, amount);
        if (keys.isEmpty()) {
            callback.accept(PurchaseResult.fail("crate-broken", normalized));
            return;
        }

        final Runnable purchase = () -> {
            if (configSnapshot.generation() != snapshot.generation()) {
                submitPlayer(player, () -> callback.accept(PurchaseResult.fail(
                        "crate-opening-changed", normalized)), () -> { });
                return;
            }
            final CurrencyManager.DurableMutation currencyMutation;
            try {
                currencyMutation = totalPrice <= 0.0D ? null
                        : currencyManager.deductDurably(player.getUniqueId(),
                        definition.keyPriceCurrency(), totalPrice);
            } catch (final RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Crate key purchase wallet write failed", failure);
                submitPlayer(player, () -> callback.accept(PurchaseResult.fail(
                        "crate-storage-unavailable", normalized)), () -> { });
                return;
            }
            if (totalPrice > 0.0D && currencyMutation == null) {
                submitPlayer(player, () -> callback.accept(PurchaseResult.fail(
                        "crate-insufficient-funds", normalized)), () -> { });
                return;
            }
            submitPlayer(player, () -> {
                if (configSnapshot.generation() != snapshot.generation()
                        || !accessDecision(player, definition).allowed()) {
                    rollbackCurrencyAsync(currencyMutation, "key purchase generation changed");
                    callback.accept(PurchaseResult.fail("crate-opening-changed", normalized));
                    return;
                }
                try {
                    keys.forEach(item -> giveItemSafely(player, item.clone()));
                    callback.accept(new PurchaseResult(true, null, normalized, definition.displayName(),
                            amount, totalPrice, definition.keyPriceCurrency()));
                } catch (final RuntimeException failure) {
                    rollbackCurrencyAsync(currencyMutation, "key delivery failed");
                    plugin.getLogger().log(Level.SEVERE, "Crate key delivery failed", failure);
                    callback.accept(PurchaseResult.fail("crate-storage-unavailable", normalized));
                }
            }, () -> rollbackCurrencyAsync(currencyMutation, "key purchase player scheduler retired"));
        };
        if (!submitAsync(purchase, () -> submitPlayer(player, () -> callback.accept(
                PurchaseResult.fail("crate-storage-unavailable", normalized)), () -> { }))) {
            // Rejection callback already delivered.
        }
    }

    public boolean giveKeys(final Player player, final String crateId, final int amount) {
        final CrateDefinition definition = definition(crateId);
        if (definition == null || amount <= 0 || amount > CrateRules.MAX_KEY_AMOUNT) {
            return false;
        }
        final List<ItemStack> keys = buildKeyStacks(definition, amount);
        if (keys.isEmpty()) {
            return false;
        }
        keys.forEach(item -> giveItemSafely(player, item));
        return true;
    }

    private List<ItemStack> buildKeyStacks(final CrateDefinition definition, final int amount) {
        int remaining = amount;
        final List<ItemStack> keys = new ArrayList<>();
        while (remaining > 0) {
            final int chunk = Math.min(64, remaining);
            final ItemStack key = crateKeyFactory.createKey(definition, chunk);
            if (key == null || key.getType().isAir() || key.getAmount() != chunk) {
                return List.of();
            }
            keys.add(key);
            remaining -= chunk;
        }
        return List.copyOf(keys);
    }

    /** Begins a persist-before-side-effect opening transaction from a physical crate interaction. */
    public void requestOpen(final Player player, final String crateId, final Location crateLocation,
                            final int requestedOpenings) {
        final ConfigSnapshot snapshot = configSnapshot;
        final CrateDefinition definition = snapshot.definitions().get(crateId);
        final AccessDecision access = accessDecision(player, definition);
        if (!access.allowed()) {
            player.sendMessage(messageManager.get(access.errorKey(), switch (access.errorKey()) {
                case "crate-no-permission" -> "&cEhhez a ládához nincs jogosultságod.";
                case "crate-world-disabled" -> "&cEz a láda ebben a világban nem nyitható.";
                default -> "&cEz a láda jelenleg le van tiltva vagy hibás.";
            }));
            return;
        }
        if (!acceptingOpens || YamlStore.hasWriteFailure(storageFile)
                || (definition.hasCurrencyReward() && !currencyManager.isStorageHealthy())) {
            player.sendMessage(messageManager.get("crate-storage-unavailable",
                    "&cA ládarendszer vagy a valuta-state nem írható; a nyitás biztonsági okból leállt."));
            return;
        }
        final StoredLocation source = crateLocation == null || crateLocation.getWorld() == null
                ? null : StoredLocation.from(crateLocation);
        if (source != null && !crateId.equals(crateBlocks.get(source))) {
            player.sendMessage(messageManager.get("crate-opening-changed",
                    "&eA láda regisztrációja közben megváltozott; a nyitás nem indult el."));
            return;
        }
        synchronized (stateLock) {
            if (pendingOpens.containsKey(player.getUniqueId())
                    || recoveryLedger.containsPlayer(player.getUniqueId())) {
                player.sendMessage(messageManager.get("crate-opening-busy",
                        "&eEgy korábbi ládanyitásod vagy kompenzációd még folyamatban van."));
                return;
            }
            final long remainingCooldown = ledger.remainingCooldown(player.getUniqueId(), crateId,
                    System.currentTimeMillis());
            if (remainingCooldown > 0L) {
                player.sendMessage(messageManager.get("crate-cooldown", "&eMég %s másodpercet várnod kell.",
                        Math.max(1L, (remainingCooldown + 999L) / 1000L)));
                return;
            }
        }

        final int availableKeys = keyCount(player, crateId);
        final int openings = CrateRules.maxOpenable(availableKeys, definition.requiredKeys(),
                Math.max(1, requestedOpenings), definition.massOpenEnabled(), definition.massOpenMaximum());
        if (openings <= 0) {
            player.sendMessage(messageManager.get("crate-not-enough-keys",
                    "&cEhhez %s kulcs kell nyitásonként; nálad %s van.",
                    definition.requiredKeys(), availableKeys));
            return;
        }
        final List<RewardEntry> rewards = new ArrayList<>(openings);
        final List<WeightedSelector.Weighted<RewardEntry>> weighted = definition.rewards().stream()
                .map(reward -> new WeightedSelector.Weighted<>(reward.weight(), reward)).toList();
        for (int index = 0; index < openings; index++) {
            rewards.add(WeightedSelector.select(weighted, ThreadLocalRandom.current().nextDouble()));
        }
        final PendingOpen pending = new PendingOpen(player, crateId, snapshot, definition, openings,
                Math.multiplyExact(openings, definition.requiredKeys()), source, rewards);
        synchronized (stateLock) {
            if (pendingOpens.putIfAbsent(player.getUniqueId(), pending) != null
                    || recoveryLedger.containsPlayer(player.getUniqueId())) {
                pendingOpens.remove(player.getUniqueId(), pending);
                player.sendMessage(messageManager.get("crate-opening-busy",
                        "&eEgy korábbi ládanyitásod még folyamatban van."));
                return;
            }
        }
        submitAsync(() -> prepareOpen(player, pending), () -> {
            synchronized (stateLock) {
                if (pendingOpens.remove(pending.playerId, pending)) {
                    pending.lifecycle.rollbackBeforeGrant();
                }
            }
            submitPlayer(player, () -> player.sendMessage(messageManager.get("crate-storage-unavailable",
                    "&cA ládanyitás előkészítése nem ütemezhető; a kulcsok nem fogytak el.")), () -> { });
        });
    }

    /** Persists stats/cooldown and a refundable key record in one atomic store write. */
    private void prepareOpen(final Player player, final PendingOpen pending) {
        String failureKey = null;
        synchronized (stateLock) {
            if (!isCurrent(pending, CrateOpeningLifecycle.State.RESERVED) || !acceptingOpens
                    || YamlStore.hasWriteFailure(storageFile)
                    || configSnapshot.generation() != pending.snapshot.generation()) {
                failureKey = "crate-opening-changed";
            } else if (pending.definition.hasCurrencyReward() && !currencyManager.isStorageHealthy()) {
                failureKey = "crate-storage-unavailable";
            } else if (ledger.remainingCooldown(pending.playerId, pending.crateId,
                    System.currentTimeMillis()) != 0L) {
                failureKey = "crate-opening-changed";
            } else {
                try {
                    pending.ledgerMutation = ledger.prepare(pending.playerId, pending.playerName,
                            pending.crateId, pending.opens, System.currentTimeMillis(),
                            pending.definition.cooldownMillis());
                    if (!pending.lifecycle.markPersisted()) {
                        throw new IllegalStateException("crate opening lifecycle lost before persist");
                    }
                    final CrateRecoveryLedger.Recovery recovery = new CrateRecoveryLedger.Recovery(
                            pending.openingId, pending.playerId, pending.playerName, pending.crateId,
                            pending.keysRequired, crateKeyFactory.recoverySpec(pending.definition),
                            pending.ledgerMutation, CrateRecoveryLedger.Disposition.ROLLBACK_ONLY,
                            "opening-prepared-without-key-or-ledger-side-effect");
                    recoveryLedger.add(recovery);
                    if (!writeStateLocked()) {
                        recoveryLedger.remove(pending.openingId);
                        pending.lifecycle.rollbackBeforeGrant();
                        pendingOpens.remove(pending.playerId, pending);
                        failureKey = "crate-storage-unavailable";
                    }
                } catch (final ArithmeticException | IllegalArgumentException | IllegalStateException invalid) {
                    recoveryLedger.remove(pending.openingId);
                    pending.lifecycle.rollbackBeforeGrant();
                    pendingOpens.remove(pending.playerId, pending);
                    plugin.getLogger().log(Level.SEVERE,
                            "Crate persist preparation rejected for " + pending.playerId + "/" + pending.crateId,
                            invalid);
                    failureKey = "crate-broken";
                }
            }
            if (failureKey != null && pendingOpens.get(pending.playerId) == pending
                    && pending.lifecycle.state() == CrateOpeningLifecycle.State.RESERVED) {
                pending.lifecycle.rollbackBeforeGrant();
                pendingOpens.remove(pending.playerId, pending);
            }
        }
        if (failureKey != null) {
            final String finalFailure = failureKey;
            submitPlayer(player, () -> player.sendMessage(messageManager.get(finalFailure, switch (finalFailure) {
                case "crate-broken" -> "&cA láda állapota nem bővíthető biztonságosan; a kulcsok nem fogytak el.";
                case "crate-opening-changed" -> "&eA ládanyitás feltételei közben megváltoztak; a kulcsok nem fogytak el.";
                default -> "&cA ládanyitás nem menthető biztonságosan; a kulcsok nem fogytak el.";
            })), () -> { });
            return;
        }
        submitPlayer(player, () -> beginGrant(player, pending),
                () -> rollbackBeforeKeyConsumptionAsync(pending, player, "player scheduler retired before grant"));
    }

    /** Claims PERSISTED -> GRANTING once, then prepares all player-owned side effects. */
    private void beginGrant(final Player player, final PendingOpen pending) {
        synchronized (stateLock) {
            if (!isCurrent(pending, CrateOpeningLifecycle.State.PERSISTED)
                    || !pending.lifecycle.claimGrant()) {
                return;
            }
            pending.grantCounted = true;
            inFlightGrants.incrementAndGet();
        }
        if (!validateOpening(player, pending)) {
            rollbackBeforeKeyConsumptionAsync(pending, player,
                    "opening definition/location changed before key use");
            player.sendMessage(messageManager.get("crate-opening-changed",
                    "&eA ládanyitás feltételei közben megváltoztak; a kulcsok nem fogytak el."));
            return;
        }

        final List<List<ItemStack>> resolvedItems = new ArrayList<>(pending.rewards.size());
        final EnumMap<CurrencyType, Double> currencyRewards = new EnumMap<>(CurrencyType.class);
        final List<String> commands = new ArrayList<>();
        for (final RewardEntry reward : pending.rewards) {
            final List<ItemStack> items = resolveItems(player, pending, reward);
            if (isItemType(reward.type()) && items.isEmpty()) {
                rollbackBeforeKeyConsumptionAsync(pending, player,
                        "selected reward could not be built");
                player.sendMessage(messageManager.get("crate-broken",
                        "&cA kiválasztott jutalom nem építhető; a kulcsok nem fogytak el."));
                return;
            }
            resolvedItems.add(items);
            if (reward.type() == RewardType.CURRENCY) {
                currencyRewards.merge(reward.currency(), reward.currencyAmount(), Double::sum);
                if (!Double.isFinite(currencyRewards.get(reward.currency()))) {
                    rollbackBeforeKeyConsumptionAsync(pending, player,
                            "currency reward batch overflow");
                    player.sendMessage(messageManager.get("crate-broken",
                            "&cA valutajutalom összege túlcsordulna; a kulcsok nem fogytak el."));
                    return;
                }
            } else if (reward.type() == RewardType.COMMAND) {
                commands.add(CrateRules.renderCommand(reward.command(), pending.playerName,
                        pending.playerId.toString(), pending.crateId, pending.opens));
            }
        }
        synchronized (stateLock) {
            if (!isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)) {
                return;
            }
            pending.resolvedItems = List.copyOf(resolvedItems);
            pending.currencyRewards = Map.copyOf(currencyRewards);
            pending.commands = List.copyOf(commands);
        }
        persistKeyConsumptionFence(pending, player);
    }

    /** Persists a refundable key fence before the player inventory is touched. */
    private void persistKeyConsumptionFence(final PendingOpen pending, final Player player) {
        submitAsync(() -> {
            boolean armed = false;
            synchronized (stateLock) {
                if (isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)
                        && !pending.keysConsumed && !pending.compensationStarted) {
                    final CrateRecoveryLedger.Recovery updated = recoveryLedger.transition(
                            pending.openingId, CrateRecoveryLedger.Disposition.ROLLBACK_ONLY,
                            CrateRecoveryLedger.Disposition.REFUND_KEYS,
                            "key-consumption-fence");
                    if (updated != null && writeStateLocked()) {
                        armed = true;
                    } else if (updated != null) {
                        recoveryLedger.transition(pending.openingId,
                                CrateRecoveryLedger.Disposition.REFUND_KEYS,
                                CrateRecoveryLedger.Disposition.ROLLBACK_ONLY,
                                "key-consumption-fence-write-failed");
                    }
                }
            }
            if (!armed) {
                rollbackBeforeKeyConsumptionAsync(pending, player,
                        "key-consumption fence could not persist");
                return;
            }
            submitPlayer(player, () -> consumeKeysAndContinue(player, pending), () ->
                    revertUnconsumedFenceAndRollback(pending, player,
                            "player scheduler retired before key consumption"));
        }, () -> rollbackBeforeKeyConsumptionAsync(pending, player,
                "key-consumption fence scheduler rejected"));
    }

    private void consumeKeysAndContinue(final Player player, final PendingOpen pending) {
        if (!validateOpening(player, pending)) {
            revertUnconsumedFenceAndRollback(pending, player,
                    "opening changed after key fence");
            return;
        }
        final ItemStack[] storage = player.getInventory().getStorageContents();
        final List<Integer> slots = new ArrayList<>();
        final List<Integer> amounts = new ArrayList<>();
        for (int slot = 0; slot < storage.length; slot++) {
            if (pending.crateId.equals(crateKeyFactory.keyCrateId(storage[slot]))) {
                slots.add(slot);
                amounts.add(storage[slot].getAmount());
            }
        }
        final List<KeyConsumption.Take> takes = KeyConsumption.plan(amounts, pending.keysRequired);
        if (takes.isEmpty()) {
            revertUnconsumedFenceAndRollback(pending, player,
                    "required keys disappeared before grant");
            player.sendMessage(messageManager.get("crate-not-enough-keys",
                    "&cA szükséges kulcsok közben már nincsenek nálad; a nyitás visszavonva."));
            return;
        }
        RuntimeException consumptionFailure = null;
        boolean consumed = false;
        synchronized (stateLock) {
            // Shutdown/quit cleanup classifies the recovery under the same lock. Holding it across
            // the owner-thread inventory write prevents a consumed key from being misclassified as
            // ROLLBACK_ONLY in the tiny window before keysConsumed becomes visible.
            if (!isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)
                    || pending.compensationStarted || pending.keysConsumed) {
                return;
            }
            try {
                for (final KeyConsumption.Take take : takes) {
                    final int slot = slots.get(take.index());
                    final ItemStack stack = storage[slot];
                    final int left = stack.getAmount() - take.amount();
                    storage[slot] = left <= 0 ? null : stack.asQuantity(left);
                }
                player.getInventory().setStorageContents(storage);
                pending.keysConsumed = true;
                consumed = true;
            } catch (final RuntimeException failure) {
                consumptionFailure = failure;
            }
        }
        if (!consumed) {
            revertUnconsumedFenceAndRollback(pending, player,
                    "key inventory mutation failed");
            if (consumptionFailure != null) {
                plugin.getLogger().log(Level.SEVERE, "Crate key consumption failed", consumptionFailure);
            }
            player.sendMessage(messageManager.get("crate-broken",
                    "&cA kulcsok nem fogyaszthatók biztonságosan; a nyitás visszavonva."));
            return;
        }
        armIrreversibleFence(pending, player);
    }

    private void revertUnconsumedFenceAndRollback(final PendingOpen pending, final Player player,
                                                  final String reason) {
        submitAsync(() -> {
            synchronized (stateLock) {
                final CrateRecoveryLedger.Recovery recovery = recoveryLedger.get(pending.openingId);
                if (recovery != null && !pending.keysConsumed
                        && recovery.disposition() == CrateRecoveryLedger.Disposition.REFUND_KEYS) {
                    recoveryLedger.transition(pending.openingId,
                            CrateRecoveryLedger.Disposition.REFUND_KEYS,
                            CrateRecoveryLedger.Disposition.ROLLBACK_ONLY, reason);
                    writeStateLocked();
                }
            }
            rollbackBeforeKeyConsumptionAsync(pending, player, reason);
        }, () -> plugin.getLogger().severe(
                "Crate key-fence rollback scheduler rejected: " + reason));
    }

    /**
     * Before any reward side effect, persist MANUAL_REVIEW. This prevents a crash/reload during
     * command/currency/item delivery from auto-refunding a key after a possibly irreversible reward.
     */
    private void armIrreversibleFence(final PendingOpen pending, final Player player) {
        submitAsync(() -> {
            boolean ready = false;
            synchronized (stateLock) {
                if (isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)
                        && pending.keysConsumed && !pending.compensationStarted) {
                    final CrateRecoveryLedger.Recovery before = recoveryLedger.get(pending.openingId);
                    if (before != null) {
                        recoveryLedger.transition(pending.openingId,
                                CrateRecoveryLedger.Disposition.MANUAL_REVIEW,
                                "reward-side-effect-fence");
                        if (writeStateLocked()) {
                            pending.irreversibleFence = true;
                            ready = true;
                        } else {
                            recoveryLedger.transition(pending.openingId,
                                    CrateRecoveryLedger.Disposition.REFUND_KEYS,
                                    "reward-side-effect-fence-write-failed");
                        }
                    }
                }
            }
            if (ready) {
                applyCurrencyOrContinue(pending, player);
            } else {
                beginCompensation(pending, player, "reward side-effect fence could not persist");
            }
        }, () -> beginCompensation(pending, player, "async fence scheduler rejected"));
    }

    private void dispatchCommandsOrContinue(final PendingOpen pending, final Player player) {
        if (pending.commands.isEmpty()) {
            submitPlayer(player, () -> grantItems(player, pending), () ->
                    beginCompensation(pending, player, "player scheduler retired before item grant"));
            return;
        }
        submitGlobal(() -> {
            synchronized (stateLock) {
                if (!isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)
                        || pending.compensationStarted || !pending.irreversibleFence) {
                    return;
                }
                pending.commandTaskClaimed = true;
            }
            int successful = 0;
            for (final String command : pending.commands) {
                final boolean dispatched;
                try {
                    dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (final RuntimeException failure) {
                    plugin.getLogger().log(Level.SEVERE, "Crate command reward failed: " + command, failure);
                    handleCommandBatchFailure(pending, player, successful,
                            "command dispatch exception");
                    return;
                }
                if (!dispatched) {
                    handleCommandBatchFailure(pending, player, successful,
                            "dispatchCommand returned false");
                    return;
                }
                successful++;
                synchronized (stateLock) {
                    pending.successfulCommands = successful;
                }
            }
            submitPlayer(player, () -> grantItems(player, pending), () ->
                    failPartial(pending, player,
                            "player scheduler retired after command reward"));
        }, () -> beginCompensation(pending, player,
                "global command scheduler rejected"));
    }

    private void handleCommandBatchFailure(final PendingOpen pending, final Player player,
                                           final int successful, final String reason) {
        final CrateCommandBatch.Outcome outcome = CrateCommandBatch.classify(
                pending.commands.size(), successful, false);
        if (outcome == CrateCommandBatch.Outcome.COMPENSATABLE_FAILURE) {
            beginCompensation(pending, player, reason + " before any command success");
        } else {
            failPartial(pending, player, reason + " after " + successful + " success(es)");
        }
    }

    private void applyCurrencyOrContinue(final PendingOpen pending, final Player player) {
        if (pending.currencyRewards.isEmpty()) {
            dispatchCommandsOrContinue(pending, player);
            return;
        }
        submitAsync(() -> {
            final CurrencyManager.DurableMutation mutation;
            try {
                mutation = currencyManager.addBalancesDurably(pending.playerId,
                        pending.currencyRewards);
            } catch (final RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE,
                        "Crate durable currency reward failed", failure);
                beginCompensation(pending, player,
                        "currency persistence failed before other reward");
                return;
            }
            synchronized (stateLock) {
                if (!isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)) {
                    rollbackCurrencyAsync(mutation,
                            "opening left GRANTING after durable currency mutation");
                    return;
                }
                pending.currencyMutation = mutation;
            }
            dispatchCommandsOrContinue(pending, player);
        }, () -> beginCompensation(pending, player,
                "async currency scheduler rejected"));
    }

    private void grantItems(final Player player, final PendingOpen pending) {
        synchronized (stateLock) {
            if (!isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)
                    || pending.compensationStarted) {
                return;
            }
        }
        try {
            for (final List<ItemStack> items : pending.resolvedItems) {
                for (final ItemStack item : items) {
                    giveItemSafely(player, item.clone());
                    synchronized (stateLock) {
                        pending.successfulItems++;
                    }
                }
            }
        } catch (final RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Crate item reward delivery failed", failure);
            final CrateRewardProgress.Recovery recovery;
            synchronized (stateLock) {
                recovery = CrateRewardProgress.recoveryFor(pending.successfulCommands,
                        pending.successfulItems, pending.currencyMutation != null);
            }
            switch (recovery) {
                case REFUND_KEY -> beginCompensation(pending, player,
                        "item reward delivery failed before irreversible reward");
                case ROLLBACK_CURRENCY_THEN_REFUND_KEY -> rollbackCurrencyThenCompensate(
                        pending, player, "item delivery failed after currency reward");
                case MANUAL_REVIEW -> failPartial(pending, player,
                        "item delivery failed after an item or command side effect");
            }
            return;
        }
        commitSuccessfulOpening(pending, player);
    }

    /**
     * Settles the durable player-side statistics/cooldown in PlayerProfile first, then removes the
     * manual-recovery fence. The opening-id receipt keeps a crash between the two writes
     * exact-once: an orphaned fence finalizes against the receipt at the next load.
     */
    private void commitSuccessfulOpening(final PendingOpen pending, final Player player) {
        profileCrateStore.applyMutation(pending.playerId, pending.ledgerMutation, pending.openingId)
                .whenComplete((status, profileFailure) -> {
                    if (profileFailure != null
                            || status == PlayerProfileCrateStore.ApplyStatus.STALE) {
                        if (profileFailure != null) {
                            plugin.getLogger().log(Level.SEVERE,
                                    "Crate settlement PlayerProfile commit failed", profileFailure);
                        }
                        submitAsync(() -> {
                            synchronized (stateLock) {
                                if (isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)) {
                                    recoveryLedger.transition(pending.openingId,
                                            CrateRecoveryLedger.Disposition.MANUAL_REVIEW,
                                            profileFailure != null
                                                    ? "settlement-profile-commit-failed"
                                                    : "settlement-profile-token-stale");
                                    writeStateLocked();
                                }
                            }
                            failPartial(pending, player,
                                    "completion persistence failed after reward delivery");
                        }, () -> failPartial(pending, player,
                                "completion async scheduler rejected after reward delivery"));
                        return;
                    }
                    finalizeSettledOpening(pending, player);
                });
    }

    /** The profile commit is durable at this point; the projection must not roll back anymore. */
    private void finalizeSettledOpening(final PendingOpen pending, final Player player) {
        submitAsync(() -> {
            boolean committed = false;
            synchronized (stateLock) {
                if (isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)) {
                    final CrateRecoveryLedger.Recovery recovery = recoveryLedger.remove(pending.openingId);
                    if (recovery != null) {
                        if (ledger.canApply(pending.ledgerMutation)) {
                            ledger.apply(pending.ledgerMutation);
                        }
                        if (!writeStateLocked()) {
                            recoveryLedger.add(recovery);
                            plugin.getLogger().severe("Crate fence persistence failed after durable "
                                    + "profile settlement; the receipt finalizes it at next load: "
                                    + pending.openingId);
                        }
                        committed = pending.lifecycle.complete();
                        finishPendingLocked(pending);
                    }
                }
            }
            if (!committed) {
                failPartial(pending, player,
                        "completion persistence failed after reward delivery");
                return;
            }
            appendAudit("SUCCESS", pending, rewardSummary(pending), "");
            submitPlayer(player, () -> finishOpening(player, pending.definition, pending), () -> { });
        }, () -> failPartial(pending, player,
                "completion async scheduler rejected after reward delivery"));
    }

    /** Compensable path: persist a refund-in-progress marker before returning any key. */
    private void beginCompensation(final PendingOpen pending, final Player player, final String reason) {
        synchronized (stateLock) {
            if (!isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)
                    || pending.compensationStarted || pending.successfulCommands > 0) {
                if (pending.successfulCommands > 0) {
                    failPartial(pending, player, reason + " after command success");
                }
                return;
            }
            pending.compensationStarted = true;
        }
        if (pending.currencyMutation != null) {
            rollbackCurrencyThenCompensate(pending, player, reason);
            return;
        }
        persistRefundInProgress(pending, player, reason);
    }

    private void rollbackCurrencyThenCompensate(final PendingOpen pending, final Player player,
                                                final String reason) {
        final CurrencyManager.DurableMutation mutation;
        synchronized (stateLock) {
            mutation = pending.currencyMutation;
            pending.currencyMutation = null;
            pending.compensationStarted = true;
        }
        submitAsync(() -> {
            try {
                currencyManager.rollbackDurably(mutation);
            } catch (final RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, "Crate currency compensation failed", failure);
                failPartial(pending, player, reason + "; currency rollback failed");
                return;
            }
            persistRefundInProgress(pending, player, reason);
        }, () -> failPartial(pending, player, reason + "; currency rollback scheduler rejected"));
    }

    private void persistRefundInProgress(final PendingOpen pending, final Player player, final String reason) {
        submitAsync(() -> {
            boolean persisted = false;
            synchronized (stateLock) {
                if (isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)) {
                    final CrateRecoveryLedger.Recovery current = recoveryLedger.get(pending.openingId);
                    if (current != null) {
                        recoveryLedger.transition(pending.openingId,
                                CrateRecoveryLedger.Disposition.MANUAL_REVIEW,
                                "refund-in-progress: " + reason);
                        persisted = writeStateLocked();
                        if (!persisted) {
                            recoveryLedger.transition(pending.openingId,
                                    CrateRecoveryLedger.Disposition.MANUAL_REVIEW,
                                    "refund-write-failed: " + reason);
                        }
                    }
                }
            }
            if (!persisted) {
                failPartial(pending, player, reason + "; refund marker persistence failed");
                return;
            }
            if (!pending.keysConsumed) {
                finishCompensatedOpening(pending, player, reason);
                return;
            }
            submitPlayer(player, () -> {
                final CrateRecoveryLedger.Recovery recovery;
                synchronized (stateLock) {
                    recovery = recoveryLedger.get(pending.openingId);
                    if (!isCurrent(pending, CrateOpeningLifecycle.State.GRANTING) || recovery == null) {
                        return;
                    }
                }
                giveRecoveryKeys(player, recovery);
                finishCompensatedOpening(pending, player, reason);
            }, () -> makeRecoveryAutoRefundable(pending, reason + "; player retired during refund"));
        }, () -> failPartial(pending, player, reason + "; refund persistence scheduler rejected"));
    }

    private void makeRecoveryAutoRefundable(final PendingOpen pending, final String reason) {
        submitAsync(() -> {
            synchronized (stateLock) {
                final CrateRecoveryLedger.Recovery recovery = recoveryLedger.get(pending.openingId);
                if (recovery != null) {
                    recoveryLedger.transition(pending.openingId,
                            CrateRecoveryLedger.Disposition.REFUND_KEYS, reason);
                    writeStateLocked();
                }
                finishPendingLocked(pending);
            }
        }, () -> plugin.getLogger().severe("Crate recovery could not be made refundable: " + reason));
    }

    private void finishCompensatedOpening(final PendingOpen pending, final Player player, final String reason) {
        submitAsync(() -> {
            boolean done = false;
            synchronized (stateLock) {
                if (isCurrent(pending, CrateOpeningLifecycle.State.GRANTING)) {
                    final CrateRecoveryLedger.Recovery recovery = recoveryLedger.remove(pending.openingId);
                    if (recovery != null && writeStateLocked()) {
                        pending.lifecycle.finishCompensatedRollback();
                        finishPendingLocked(pending);
                        done = true;
                    } else if (recovery != null) {
                        recoveryLedger.add(recovery.withDisposition(
                                CrateRecoveryLedger.Disposition.MANUAL_REVIEW,
                                "refund-applied-but-persistence-failed: " + reason));
                    }
                }
            }
            if (done) {
                appendAudit("FAILED_COMPENSATED", pending, "", reason);
                submitPlayer(player, () -> player.sendMessage(messageManager.get(
                        "crate-opening-changed",
                        "&eA ládanyitás nem teljesült; a kulcsok visszaálltak, stat/cooldown nem keletkezett.")),
                        () -> { });
            } else {
                failPartial(pending, player,
                        reason + "; compensated rollback persistence failed");
            }
        }, () -> failPartial(pending, player,
                reason + "; compensated rollback scheduler rejected"));
    }

    private void rollbackBeforeKeyConsumptionAsync(final PendingOpen pending, final Player player,
                                                    final String reason) {
        submitAsync(() -> {
            boolean rolledBack = false;
            synchronized (stateLock) {
                if (pendingOpens.get(pending.playerId) != pending) {
                    return;
                }
                if (pending.keysConsumed) {
                    pending.compensationStarted = false;
                } else {
                    final CrateRecoveryLedger.Recovery recovery = recoveryLedger.remove(pending.openingId);
                    if (recovery != null && writeStateLocked()) {
                        if (pending.lifecycle.state() == CrateOpeningLifecycle.State.GRANTING) {
                            pending.lifecycle.finishCompensatedRollback();
                        } else {
                            pending.lifecycle.rollbackBeforeGrant();
                        }
                        finishPendingLocked(pending);
                        rolledBack = true;
                    } else if (recovery != null) {
                        recoveryLedger.add(recovery.withDisposition(
                                CrateRecoveryLedger.Disposition.ROLLBACK_ONLY,
                                "pre-key rollback persistence failed: " + reason));
                    }
                }
            }
            if (!rolledBack && pending.keysConsumed) {
                beginCompensation(pending, player, reason);
            } else if (rolledBack) {
                appendAudit("FAILED_ROLLED_BACK", pending, "", reason);
            }
        }, () -> plugin.getLogger().severe(
                "Crate rollback scheduler rejected: " + reason));
    }

    private void failPartial(final PendingOpen pending, final Player player, final String reason) {
        boolean changed = false;
        synchronized (stateLock) {
            if (pendingOpens.get(pending.playerId) == pending
                    && pending.lifecycle.state() == CrateOpeningLifecycle.State.GRANTING) {
                pending.lifecycle.failPartial();
                final CrateRecoveryLedger.Recovery recovery = recoveryLedger.get(pending.openingId);
                if (recovery != null) {
                    recoveryLedger.transition(pending.openingId,
                            CrateRecoveryLedger.Disposition.MANUAL_REVIEW, reason);
                }
                finishPendingLocked(pending);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        appendAudit("FAILED_PARTIAL_MANUAL_REVIEW", pending, rewardSummary(pending), reason);
        if (player != null) {
            submitPlayer(player, () -> player.sendMessage(messageManager.get("crate-storage-unavailable",
                    "&cA ládanyitás részben teljesült; automatikus kompenzáció nem biztonságos. Az admin auditrekordot kapott.")),
                    () -> { });
        }
        // The MANUAL_REVIEW recovery remains durable and blocks another opening for this player.
        submitAsync(() -> {
            synchronized (stateLock) {
                writeStateLocked();
            }
        }, () -> plugin.getLogger().severe("Crate partial-failure state could not be flushed: " + reason));
    }

    private boolean validateOpening(final Player player, final PendingOpen pending) {
        if (!acceptingOpens || configSnapshot.generation() != pending.snapshot.generation()
                || !pending.definition.equals(configSnapshot.definitions().get(pending.crateId))
                || !accessDecision(player, pending.definition).allowed()
                || (pending.definition.hasCurrencyReward() && !currencyManager.isStorageHealthy())
                || !nearSource(player, pending.source)) {
            return false;
        }
        if (pending.source == null) {
            return true;
        }
        final World world = Bukkit.getWorld(pending.source.worldId());
        return world != null && world.getName().equals(pending.source.worldName())
                && pending.crateId.equals(crateBlocks.get(pending.source));
    }

    private boolean nearSource(final Player player, final StoredLocation source) {
        if (source == null) {
            return true;
        }
        if (!player.getWorld().getUID().equals(source.worldId())) {
            return false;
        }
        final Location location = player.getLocation();
        final double dx = location.getX() - (source.x() + 0.5D);
        final double dy = location.getY() - (source.y() + 0.5D);
        final double dz = location.getZ() - (source.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= 64.0D;
    }

    private List<ItemStack> resolveItems(final Player player, final PendingOpen pending,
                                              final RewardEntry reward) {
        return switch (reward.type()) {
            case ITEM -> split(new ItemStack(Material.valueOf(reward.value())), reward.amount());
            case UNIQUE_ITEM -> {
                final ItemStack base = uniqueMaterialFactory.create(reward.value(), 1);
                yield base == null ? List.of() : split(base, reward.amount());
            }
            case RECIPE_ITEM -> {
                final ProfessionRecipeCatalog.Recipe recipe = recipeCatalog.get(reward.value());
                if (recipe == null) {
                    yield List.of();
                }
                final List<ItemStack> built = new ArrayList<>();
                for (int index = 0; index < reward.amount(); index++) {
                    final ItemStack item = recipeBuilder.buildResult(player, recipe);
                    if (item == null || item.getType().isAir()) {
                        built.clear();
                        break;
                    }
                    built.add(item);
                }
                yield List.copyOf(built);
            }
            case BLUEPRINT -> {
                final ItemStack base = blueprintFactory.create(reward.value());
                yield base == null ? List.of() : split(base, reward.amount());
            }
            case CRATE_KEY -> {
                final CrateDefinition target = pending.snapshot.definitions().get(reward.value());
                final ItemStack key = target == null ? null : crateKeyFactory.createKey(target, 1);
                yield key == null ? List.of() : split(key, reward.amount());
            }
            case COMMAND, CURRENCY -> List.of();
        };
    }

    private static boolean isItemType(final RewardType type) {
        return type != RewardType.COMMAND && type != RewardType.CURRENCY;
    }

    private static List<ItemStack> split(final ItemStack template, final int amount) {
        if (template == null || template.getType().isAir() || amount <= 0) {
            return List.of();
        }
        final int perStack = Math.max(1, template.getMaxStackSize());
        int remaining = amount;
        final List<ItemStack> stacks = new ArrayList<>();
        while (remaining > 0) {
            final ItemStack copy = template.clone();
            final int chunk = Math.min(perStack, remaining);
            copy.setAmount(chunk);
            stacks.add(copy);
            remaining -= chunk;
        }
        return List.copyOf(stacks);
    }

    private void giveItemSafely(final Player player, final ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return;
        }
        player.getInventory().addItem(item).values().forEach(left ->
                player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void giveRecoveryKeys(final Player player, final CrateRecoveryLedger.Recovery recovery) {
        int remaining = recovery.keyCount();
        while (remaining > 0) {
            final int amount = Math.min(64, remaining);
            final ItemStack key = crateKeyFactory.createRecoveryKey(recovery.crateId(),
                    recovery.keySpec(), amount);
            if (key == null || key.getType().isAir()) {
                throw new IllegalStateException("A tartós crate key refund nem építhető.");
            }
            giveItemSafely(player, key);
            remaining -= amount;
        }
    }

    private void finishOpening(final Player player, final CrateDefinition definition,
                               final PendingOpen pending) {
        final String rewardSummary = rewardSummary(pending);
        final Runnable feedback = () -> {
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    player.getLocation().add(0.0D, 1.0D, 0.0D), 16, 0.4D, 0.5D, 0.4D, 0.1D);
            player.playSound(player.getLocation(), definition.openingSound(),
                    definition.soundVolume(), definition.soundPitch());
            player.sendMessage(messageManager.get("crate-opened",
                    "&6[Láda] &eKinyitottad: &f%s &7(%s×) &e— nyeremény: &a%s",
                    definition.displayName(), pending.opens, rewardSummary));
        };

        final World sourceWorld = pending.source == null ? null : Bukkit.getWorld(pending.source.worldId());
        final Location sourceLocation = sourceWorld == null ? null
                : new Location(sourceWorld, pending.source.x(), pending.source.y(), pending.source.z());
        if (sourceLocation != null) {
            spawnCrateReveal(sourceLocation, definition.rewards(),
                    pending.rewards.get(pending.rewards.size() - 1));
        }
        if (pending.snapshot.spinAnimation() && pending.opens == 1) {
            CrateSpinGUI.open(plugin, player, definition.displayName(), definition.rewards(),
                    pending.rewards.getFirst(), feedback);
        } else {
            feedback.run();
        }
        if (definition.broadcastEnabled()) {
            broadcast(definition.broadcastMessage()
                    .replace("{player}", pending.playerName)
                    .replace("{crate}", definition.displayName())
                    .replace("{amount}", Integer.toString(pending.opens)));
        }
    }

    private void broadcast(final String message) {
        submitGlobal(() -> {
            final List<Player> viewers = List.copyOf(Bukkit.getOnlinePlayers());
            for (final Player viewer : viewers) {
                submitPlayer(viewer, () -> viewer.sendMessage(
                        hu.taliann.icesmp.utils.TextUtil.color(message)), () -> { });
            }
        }, () -> plugin.getLogger().warning("Crate broadcast scheduler rejected."));
    }

    private String rewardSummary(final PendingOpen pending) {
        return pending.rewards.stream().map(CrateManager::describeReward)
                .reduce((left, right) -> left + ", " + right).orElse("?");
    }

    private void appendAudit(final String outcome, final PendingOpen pending,
                             final String rewards, final String detail) {
        final String line = Instant.now() + " outcome=" + sanitize(outcome)
                + " opening=" + pending.openingId + " player=" + pending.playerId
                + " name=" + sanitize(pending.playerName) + " crate=" + pending.crateId
                + " opens=" + pending.opens + " keys=" + pending.keysRequired
                + " rewards=" + sanitize(rewards) + " detail=" + sanitize(detail)
                + System.lineSeparator();
        submitAsync(() -> {
            try {
                auditWriter.append(line);
            } catch (final IOException failure) {
                plugin.getLogger().warning("A crate-openings.log írása nem sikerült: " + failure.getMessage());
            }
        }, () -> plugin.getLogger().warning("A crate audit scheduler visszautasította a bejegyzést: " + outcome));
    }

    private static String sanitize(final String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private boolean isCurrent(final PendingOpen pending, final CrateOpeningLifecycle.State state) {
        return pendingOpens.get(pending.playerId) == pending && pending.lifecycle.state() == state;
    }

    private void finishPendingLocked(final PendingOpen pending) {
        pendingOpens.remove(pending.playerId, pending);
        if (pending.grantCounted) {
            pending.grantCounted = false;
            inFlightGrants.decrementAndGet();
        }
    }

    private boolean submitPlayer(final Player player, final Runnable task, final Runnable rejected) {
        if (player == null) {
            rejected.run();
            return false;
        }
        return CrateTaskSubmission.entity(plugin, player.getScheduler(), task, rejected);
    }

    private boolean submitGlobal(final Runnable task, final Runnable rejected) {
        return CrateTaskSubmission.global(plugin, plugin.getServer().getGlobalRegionScheduler(), task, rejected);
    }

    private boolean submitAsync(final Runnable task, final Runnable rejected) {
        return CrateTaskSubmission.async(plugin, plugin.getServer().getAsyncScheduler(), task, rejected);
    }

    private void rollbackCurrencyAsync(final CurrencyManager.DurableMutation mutation,
                                       final String reason) {
        if (mutation == null) {
            return;
        }
        deferredCurrencyRollbacks.add(mutation);
        submitAsync(() -> performDeferredCurrencyRollback(mutation, reason), () ->
                plugin.getLogger().severe("Crate currency rollback scheduler rejected; orderly shutdown will retry: "
                        + reason));
    }

    private void performDeferredCurrencyRollback(final CurrencyManager.DurableMutation mutation,
                                                  final String reason) {
        if (!deferredCurrencyRollbacks.remove(mutation)) {
            return;
        }
        try {
            currencyManager.rollbackDurably(mutation);
        } catch (final RuntimeException failure) {
            deferredCurrencyRollbacks.add(mutation);
            plugin.getLogger().log(Level.SEVERE,
                    "Crate durable currency rollback failed; retry retained for orderly shutdown: " + reason,
                    failure);
        }
    }

    /** One bounded pass; failed or stale tokens remain explicit for the final shutdown audit. */
    private void drainDeferredCurrencyRollbacks() {
        final int attempts = deferredCurrencyRollbacks.size();
        for (int index = 0; index < attempts; index++) {
            final CurrencyManager.DurableMutation mutation = deferredCurrencyRollbacks.poll();
            if (mutation == null) {
                return;
            }
            try {
                currencyManager.rollbackDurably(mutation);
            } catch (final RuntimeException failure) {
                deferredCurrencyRollbacks.add(mutation);
                plugin.getLogger().log(Level.SEVERE,
                        "Crate deferred currency rollback requires manual audit for " + mutation.playerId(),
                        failure);
            }
        }
    }

    /** Join-time recovery for a safely refundable opening left by quit/reload/disable. */
    public void restorePendingRecovery(final Player player) {
        final CrateRecoveryLedger.Recovery recovery;
        synchronized (stateLock) {
            recovery = recoveryLedger.forPlayer(player.getUniqueId());
            if (recovery == null) {
                return;
            }
            if (recovery.disposition() != CrateRecoveryLedger.Disposition.REFUND_KEYS) {
                plugin.getLogger().severe("Crate opening kézi auditot igényel: " + recovery.openingId()
                        + " player=" + recovery.playerId() + " reason=" + recovery.reason());
                player.sendMessage(messageManager.get("crate-storage-unavailable",
                        "&cEgy korábbi ládanyitásod kézi admin auditot igényel; addig új láda nem nyitható."));
                return;
            }
        }
        submitAsync(() -> {
            boolean armed = false;
            synchronized (stateLock) {
                final CrateRecoveryLedger.Recovery current = recoveryLedger.get(recovery.openingId());
                if (current != null && current.disposition() == CrateRecoveryLedger.Disposition.REFUND_KEYS) {
                    recoveryLedger.transition(recovery.openingId(),
                            CrateRecoveryLedger.Disposition.REFUND_KEYS,
                            CrateRecoveryLedger.Disposition.REFUND_CLAIMED,
                            "join-refund-in-progress");
                    armed = writeStateLocked();
                    if (!armed) {
                        recoveryLedger.transition(recovery.openingId(),
                                CrateRecoveryLedger.Disposition.REFUND_CLAIMED,
                                CrateRecoveryLedger.Disposition.REFUND_KEYS,
                                "join-refund-marker-write-failed");
                    }
                }
            }
            if (!armed) {
                return;
            }
            submitPlayer(player, () -> {
                try {
                    giveRecoveryKeys(player, recovery);
                } catch (final RuntimeException failure) {
                    plugin.getLogger().log(Level.SEVERE, "Join crate recovery key delivery failed", failure);
                    failJoinRecovery(recovery, "join-refund-key-build-or-delivery-failed");
                    return;
                }
                submitAsync(() -> completeJoinRecovery(recovery), () ->
                        failJoinRecovery(recovery, "join-refund-commit-scheduler-rejected"));
            }, () -> makeJoinRecoveryRefundable(recovery));
        }, () -> plugin.getLogger().warning("Join crate recovery scheduler rejected: " + recovery.openingId()));
    }

    private void completeJoinRecovery(final CrateRecoveryLedger.Recovery recovery) {
        boolean completed = false;
        synchronized (stateLock) {
            final CrateRecoveryLedger.Recovery current = recoveryLedger.get(recovery.openingId());
            if (current != null
                    && current.disposition() == CrateRecoveryLedger.Disposition.REFUND_CLAIMED) {
                // Stats/cooldown are applied only at successful settlement. A refundable recovery
                // therefore owns only the consumed key; removing it after delivery is the complete
                // restart action and must not try to rollback a ledger mutation that never ran.
                recoveryLedger.remove(recovery.openingId());
                if (writeStateLocked()) {
                    completed = true;
                } else {
                    recoveryLedger.add(current.withDisposition(
                            CrateRecoveryLedger.Disposition.MANUAL_REVIEW,
                            "join-refund-applied-but-persistence-failed"));
                }
            }
        }
        if (completed) {
            plugin.getLogger().info("Crate kulcskompenzáció helyreállítva stat/cooldown mellékhatás nélkül: "
                    + recovery.openingId());
        }
    }

    private void failJoinRecovery(final CrateRecoveryLedger.Recovery recovery, final String reason) {
        submitAsync(() -> {
            synchronized (stateLock) {
                final CrateRecoveryLedger.Recovery current = recoveryLedger.get(recovery.openingId());
                if (current != null) {
                    recoveryLedger.transition(recovery.openingId(),
                            CrateRecoveryLedger.Disposition.MANUAL_REVIEW, reason);
                    writeStateLocked();
                }
            }
        }, () -> plugin.getLogger().severe("Join crate recovery failure could not be persisted: " + reason));
    }

    private void makeJoinRecoveryRefundable(final CrateRecoveryLedger.Recovery recovery) {
        submitAsync(() -> {
            synchronized (stateLock) {
                if (recoveryLedger.get(recovery.openingId()) != null) {
                    recoveryLedger.transition(recovery.openingId(),
                            CrateRecoveryLedger.Disposition.REFUND_CLAIMED,
                            CrateRecoveryLedger.Disposition.REFUND_KEYS,
                            "join player scheduler retired before refund");
                    writeStateLocked();
                }
            }
        }, () -> plugin.getLogger().severe("Join recovery could not be made refundable: "
                + recovery.openingId()));
    }

    public int manualRecoveryCount() {
        synchronized (stateLock) {
            return (int) recoveryLedger.snapshot().values().stream()
                    .filter(recovery -> recovery.disposition()
                            == CrateRecoveryLedger.Disposition.MANUAL_REVIEW).count();
        }
    }

    // ==================== statistics ====================

    public StatsView stats(final UUID playerId) {
        synchronized (stateLock) {
            final CrateLedger.PlayerSnapshot snapshot = ledger.snapshot().get(playerId);
            if (snapshot == null) {
                return new StatsView(playerId, null, 0L, Map.of());
            }
            return new StatsView(playerId, snapshot.lastKnownName(), ledger.total(playerId), snapshot.counts());
        }
    }

    public UUID findStatsPlayer(final String input) {
        try {
            return UUID.fromString(input);
        } catch (final IllegalArgumentException ignored) {
            synchronized (stateLock) {
                return ledger.findByName(input);
            }
        }
    }

    public void resetStatsAsync(final UUID playerId, final String crateId,
                                final Consumer<MutationResult> callback) {
        submitAsync(() -> {
            synchronized (stateLock) {
                if (pendingOpens.containsKey(playerId) || recoveryLedger.containsPlayer(playerId)) {
                    callback.accept(MutationResult.fail("crate-opening-busy"));
                    return;
                }
            }
            profileCrateStore.reset(playerId, crateId).whenComplete((changed, failure) -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Crate stat reset PlayerProfile commit failed", failure);
                    callback.accept(MutationResult.fail("crate-storage-unavailable"));
                    return;
                }
                submitAsync(() -> {
                    // The profile commit is durable; the projection follows it unconditionally.
                    synchronized (stateLock) {
                        ledger.reset(playerId, crateId);
                    }
                    callback.accept(MutationResult.ok());
                }, () -> callback.accept(MutationResult.fail("crate-storage-unavailable")));
            });
        }, () -> callback.accept(MutationResult.fail("crate-storage-unavailable")));
    }

    // ==================== reveal and labels ====================

    private void spawnCrateReveal(final Location crateLocation, final List<RewardEntry> rewards,
                                  final RewardEntry picked) {
        if (crateLocation.getWorld() == null || !configManager.getBoolean("display-fx.crate-reveal.enabled", true)) {
            return;
        }
        final int cycles = 12;
        final int cycleTicks = 3;
        final int despawn = cycles * cycleTicks + 45;
        final Location at = crateLocation.clone().add(0.5D, 1.4D, 0.5D);
        hu.taliann.icesmp.utils.DisplayFxUtil.spawnItemDisplay(plugin, at,
                new ItemStack(rewards.getFirst().iconMaterial()), despawn, display -> {
            display.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
            display.setTransformation(hu.taliann.icesmp.utils.DisplayFxUtil.scale(0.5F, 0.5F, 0.5F));
            display.setViewRange(3.0F);
            final int[] step = {0};
            final hu.taliann.icesmp.crates.CrateTaskLease lease =
                    new hu.taliann.icesmp.crates.CrateTaskLease();
            final Runnable retired = () -> {
                lease.retire();
                hu.taliann.icesmp.utils.TransientEntities.markGone(display.getUniqueId());
            };
            try {
                final io.papermc.paper.threadedregions.scheduler.ScheduledTask task =
                        display.getScheduler().runAtFixedRate(plugin, scheduled -> {
                            if (!display.isValid()) {
                                scheduled.cancel();
                                lease.retire();
                                hu.taliann.icesmp.utils.TransientEntities.markGone(display.getUniqueId());
                                return;
                            }
                            if (step[0] < cycles) {
                                display.setItemStack(new ItemStack(rewards.get(
                                        ThreadLocalRandom.current().nextInt(rewards.size())).iconMaterial()));
                                step[0]++;
                            } else {
                                scheduled.cancel();
                                lease.retire();
                                display.setItemStack(new ItemStack(picked.iconMaterial()));
                                display.setGlowing(true);
                                display.setGlowColorOverride(org.bukkit.Color.fromRGB(0xFFD24A));
                                display.setInterpolationDelay(0);
                                display.setInterpolationDuration(8);
                                display.setTransformation(hu.taliann.icesmp.utils.DisplayFxUtil.scale(
                                        0.95F, 0.95F, 0.95F));
                            }
                        }, retired, cycleTicks, cycleTicks);
                if (!lease.publish(task)) {
                    if (display.isValid()) {
                        display.remove();
                    }
                    retired.run();
                }
            } catch (final RuntimeException failure) {
                if (display.isValid()) {
                    display.remove();
                }
                retired.run();
                plugin.getLogger().log(Level.FINE, "Crate reveal scheduler rejected", failure);
            }
        });
    }

    public static String describeReward(final RewardEntry reward) {
        if (reward.description() != null && !reward.description().isBlank()) {
            return reward.description();
        }
        return switch (reward.type()) {
            case ITEM -> humanize(Material.valueOf(reward.value())) + " ×" + reward.amount();
            case COMMAND -> "&dParancs-jutalom";
            case CURRENCY -> reward.currencyAmount() + " " + reward.currency().getDisplayName();
            case UNIQUE_ITEM -> "&bEgyedi tárgy: " + reward.value() + " ×" + reward.amount();
            case RECIPE_ITEM -> "&6Recepttárgy: " + reward.value() + " ×" + reward.amount();
            case BLUEPRINT -> "&bTervrajz: " + reward.value() + " ×" + reward.amount();
            case CRATE_KEY -> "&eLádakulcs: " + reward.value() + " ×" + reward.amount();
        };
    }

    private static String humanize(final Material material) {
        final StringBuilder builder = new StringBuilder();
        for (final String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!part.isEmpty()) {
                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return builder.toString().strip();
    }

    // ==================== persistence and lifecycle ====================

    @Override
    public void load() {
        synchronized (stateLock) {
            crateBlocks.clear();
            ledger.replace(Map.of());
            recoveryLedger.replace(Map.of());
            pendingOpens.clear();
            inFlightGrants.set(0);
            acceptingOpens = true;
            if (!storageFile.exists()) {
                return;
            }
            final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
            final Set<String> allowedRoot = Set.of("schema", "blocks", "recoveries");
            for (final String key : yaml.getKeys(false)) {
                if (!allowedRoot.contains(key)) {
                    corrupt("ismeretlen root kulcs: " + key);
                }
            }
            final int schema;
            try {
                schema = CrateRules.exactInt(yaml.get("schema"), -1,
                        LEGACY_SCHEMA, SCHEMA, "schema");
            } catch (final IllegalArgumentException invalid) {
                corrupt("hiányzó vagy ismeretlen schema");
                return;
            }
            final Map<StoredLocation, String> loadedBlocks = new LinkedHashMap<>();
            final Object blocksNode = yaml.get("blocks");
            if (blocksNode != null && !(blocksNode instanceof List<?>)) {
                corrupt("blocks csak lista lehet");
            }
            final List<Map<?, ?>> rawBlocks = yaml.getMapList("blocks");
            for (int index = 0; index < rawBlocks.size(); index++) {
                final Map<?, ?> raw = rawBlocks.get(index);
                try {
                    final UUID worldId = UUID.fromString(requiredStoredText(raw.get("world-uuid"), "world-uuid"));
                    final String storedWorldName = requiredStoredText(raw.get("world-name"), "world-name");
                    final World resolvedWorld = Bukkit.getWorld(worldId);
                    if (resolvedWorld == null || !resolvedWorld.getName().equals(storedWorldName)) {
                        throw new IllegalArgumentException("a világ UUID/név jelenleg nem egyezik: "
                                + storedWorldName);
                    }
                    final int x = stateCoordinate(raw.get("x"), "x", -30_000_000, 30_000_000);
                    final int y = stateCoordinate(raw.get("y"), "y", -2048, 2048);
                    final int z = stateCoordinate(raw.get("z"), "z", -30_000_000, 30_000_000);
                    final String crateId = CrateRules.normalizeId(requiredStoredText(raw.get("crate-id"), "crate-id"));
                    if (crateId == null || !configSnapshot.definitions().containsKey(crateId)) {
                        throw new IllegalArgumentException("ismeretlen vagy törölt crate-id: " + raw.get("crate-id"));
                    }
                    final StoredLocation location = new StoredLocation(worldId,
                            resolvedWorld.getName(), x, y, z);
                    if (loadedBlocks.putIfAbsent(location, crateId) != null) {
                        throw new IllegalArgumentException("duplikált location");
                    }
                } catch (final IllegalArgumentException invalid) {
                    corrupt("blocks[" + index + "]: " + invalid.getMessage());
                }
            }

            crateBlocks.putAll(loadedBlocks);
            // The projection must be seeded before recovery validation, because the recovery
            // tokens' previous values are compared against the durable per-player crate state.
            if (PlayerProfileAuthority.installed().isEmpty()) {
                throw new IllegalStateException(
                        "PlayerProfile authority is required before CrateManager.load");
            }
            final Map<UUID, CrateLedger.PlayerSnapshot> seededStats = new LinkedHashMap<>();
            final Map<UUID, List<String>> settlementReceipts = new LinkedHashMap<>();
            final Set<UUID> ownerIds = PlayerProfileAuthority.current().repository()
                    .listPlayerIds().toCompletableFuture().join();
            for (final UUID ownerId : ownerIds) {
                final var profile = PlayerProfileAuthority.current().repository()
                        .find(ownerId).toCompletableFuture().join();
                if (profile.isEmpty()) {
                    continue;
                }
                final var section = profile.orElseThrow().section(ProfileSectionId.STATISTICS);
                if (section.isEmpty() || !section.orElseThrow().health().usable()
                        || !(section.orElseThrow().value() instanceof StatisticsSection stats)) {
                    continue;
                }
                final PlayerProfileCrateStore.PlayerCrateState state =
                        PlayerProfileCrateStore.read(stats);
                if (!state.isEmpty()) {
                    seededStats.put(ownerId, state.toLedgerSnapshot());
                }
                if (!state.recentOps().isEmpty()) {
                    settlementReceipts.put(ownerId, state.recentOps());
                }
            }
            ledger.replace(seededStats);

            if (schema >= SCHEMA) {
                final Object recoveriesNode = yaml.get("recoveries");
                if (recoveriesNode != null && !(recoveriesNode instanceof List<?>)) {
                    corrupt("recoveries csak lista lehet");
                }
                final List<Map<?, ?>> rawRecoveries = yaml.getMapList("recoveries");
                if (rawRecoveries.size() > 10_000) {
                    corrupt("túl sok recovery rekord");
                }
                final Map<UUID, CrateRecoveryLedger.Recovery> loadedRecoveries = new LinkedHashMap<>();
                for (int index = 0; index < rawRecoveries.size(); index++) {
                    try {
                        final CrateRecoveryLedger.Recovery recovery = decodeRecovery(rawRecoveries.get(index));
                        if (!ledger.canApply(recovery.ledgerMutation())) {
                            if (settlementReceipts.getOrDefault(recovery.playerId(), List.of())
                                    .contains(recovery.openingId().toString())) {
                                // A settlement profil-commitja tartós, csak a fence maradt árván —
                                // a receipt igazolja, ezért a fence csendben véglegesíthető.
                                continue;
                            }
                            throw new IllegalArgumentException("a ledger mutation token elavult vagy ellentmondó");
                        }
                        if (loadedRecoveries.putIfAbsent(recovery.openingId(), recovery) != null) {
                            throw new IllegalArgumentException("duplikált opening-id");
                        }
                    } catch (final IllegalArgumentException invalid) {
                        corrupt("recoveries[" + index + "]: " + invalid.getMessage());
                    }
                }
                recoveryLedger.replace(loadedRecoveries);
                for (final CrateRecoveryLedger.Recovery recovery :
                        List.copyOf(recoveryLedger.snapshot().values())) {
                    if (recovery.disposition() == CrateRecoveryLedger.Disposition.ROLLBACK_ONLY) {
                        recoveryLedger.remove(recovery.openingId());
                        plugin.getLogger().warning("Félbemaradt crate-előkészítés stat/cooldown és kulcsvesztés nélkül "
                                + "eldobva: " + recovery.openingId());
                    } else if (recovery.disposition() == CrateRecoveryLedger.Disposition.REFUND_CLAIMED
                            || recovery.disposition() == CrateRecoveryLedger.Disposition.MANUAL_REVIEW) {
                        plugin.getLogger().severe("Crate recovery kézi auditot igényel: "
                                + recovery.openingId() + " player=" + recovery.playerId()
                                + " reason=" + recovery.reason());
                    }
                }
            }
        }
    }

    private static int stateCoordinate(final Object raw, final String field, final int minimum,
                                       final int maximum) {
        return CrateRules.exactInt(raw, 0, minimum, maximum, field);
    }

    private CrateRecoveryLedger.Recovery decodeRecovery(final Map<?, ?> raw) {
        final UUID openingId = UUID.fromString(requiredStoredText(raw.get("opening-id"), "opening-id"));
        final UUID playerId = UUID.fromString(requiredStoredText(raw.get("player-id"), "player-id"));
        final String playerName = optionalStoredText(raw.get("player-name"), "player-name", 64);
        final String crateId = CrateRules.normalizeId(requiredStoredText(raw.get("crate-id"), "crate-id"));
        if (crateId == null) {
            throw new IllegalArgumentException("hibás crate-id");
        }
        final int keyCount = CrateRules.exactInt(raw.get("key-count"), 0, 1,
                CrateRules.MAX_KEY_AMOUNT, "key-count");
        final String keyMaterial = requiredStoredText(raw.get("key-material"), "key-material");
        final Material material = Material.matchMaterial(keyMaterial);
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("hibás key-material");
        }
        final String keyName = requiredStoredText(raw.get("key-name"), "key-name");
        final String keyItemModel = optionalStoredText(raw.get("key-item-model"),
                "key-item-model", 256);
        final CrateRecoveryLedger.Disposition disposition;
        try {
            disposition = CrateRecoveryLedger.Disposition.valueOf(
                    requiredStoredText(raw.get("disposition"), "disposition"));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("hibás disposition");
        }
        final String reason = optionalStoredText(raw.get("reason"), "reason", 1024);
        final UUID mutationPlayer = UUID.fromString(requiredStoredText(
                raw.get("mutation-player-id"), "mutation-player-id"));
        final String mutationCrate = CrateRules.normalizeId(requiredStoredText(
                raw.get("mutation-crate-id"), "mutation-crate-id"));
        if (!playerId.equals(mutationPlayer) || !crateId.equals(mutationCrate)) {
            throw new IllegalArgumentException("a recovery és mutation identity eltér");
        }
        final long previousCount = CrateRules.exactLong(raw.get("previous-count"), 0L,
                0L, Long.MAX_VALUE, "previous-count");
        final long previousCooldown = CrateRules.exactLong(raw.get("previous-cooldown"), 0L,
                0L, Long.MAX_VALUE, "previous-cooldown");
        final String previousName = optionalStoredText(raw.get("previous-name"),
                "previous-name", 64);
        final String newName = optionalStoredText(raw.get("new-name"), "new-name", 64);
        final long newCount = CrateRules.exactLong(raw.get("new-count"), 0L,
                1L, Long.MAX_VALUE, "new-count");
        final long newCooldown = CrateRules.exactLong(raw.get("new-cooldown"), 0L,
                0L, Long.MAX_VALUE, "new-cooldown");
        if (newCount <= previousCount) {
            throw new IllegalArgumentException("a new-count nem növeli a korábbi értéket");
        }
        final CrateLedger.Mutation mutation = new CrateLedger.Mutation(playerId, crateId,
                previousCount, previousCooldown, previousName, newName, newCount, newCooldown);
        return new CrateRecoveryLedger.Recovery(openingId, playerId, playerName, crateId,
                keyCount, new CrateRecoveryLedger.KeySpec(material.name(), keyName, keyItemModel),
                mutation, disposition, reason);
    }

    private static String requiredStoredText(final Object raw, final String field) {
        final String value = optionalStoredText(raw, field, 512);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("hiányzó " + field);
        }
        return value;
    }

    private static String optionalStoredText(final Object raw, final String field,
                                             final int maximumLength) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String text) || text.length() > maximumLength) {
            throw new IllegalArgumentException("hibás vagy túl hosszú " + field);
        }
        return text.isBlank() ? null : text;
    }

    private void corrupt(final String reason) {
        YamlStore.failCorrupt(storageFile, plugin.getLogger(), "Érvénytelen crates-data.yml: " + reason);
        throw new IllegalStateException(reason);
    }

    @Override
    public void save() {
        synchronized (stateLock) {
            if (!writeStateLocked()) {
                throw new IllegalStateException("crates-data.yml mentése sikertelen");
            }
        }
    }

    private boolean writeStateLocked() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", SCHEMA);
        final List<Map<String, Object>> blocks = new ArrayList<>();
        for (final Map.Entry<StoredLocation, String> entry : crateBlocks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(StoredLocation::describe))).toList()) {
            final Map<String, Object> block = new LinkedHashMap<>();
            block.put("world-uuid", entry.getKey().worldId().toString());
            block.put("world-name", entry.getKey().worldName());
            block.put("x", entry.getKey().x());
            block.put("y", entry.getKey().y());
            block.put("z", entry.getKey().z());
            block.put("crate-id", entry.getValue());
            blocks.add(block);
        }
        yaml.set("blocks", blocks);
        final List<Map<String, Object>> recoveries = new ArrayList<>();
        for (final CrateRecoveryLedger.Recovery recovery : recoveryLedger.snapshot().values().stream()
                .sorted(Comparator.comparing(value -> value.openingId().toString())).toList()) {
            final CrateLedger.Mutation mutation = recovery.ledgerMutation();
            final Map<String, Object> encoded = new LinkedHashMap<>();
            encoded.put("opening-id", recovery.openingId().toString());
            encoded.put("player-id", recovery.playerId().toString());
            encoded.put("player-name", recovery.playerName());
            encoded.put("crate-id", recovery.crateId());
            encoded.put("key-count", recovery.keyCount());
            encoded.put("key-material", recovery.keySpec().material());
            encoded.put("key-name", recovery.keySpec().displayName());
            encoded.put("key-item-model", recovery.keySpec().itemModel());
            encoded.put("disposition", recovery.disposition().name());
            encoded.put("reason", recovery.reason());
            encoded.put("mutation-player-id", mutation.playerId().toString());
            encoded.put("mutation-crate-id", mutation.crateId());
            encoded.put("previous-count", mutation.previousCount());
            encoded.put("previous-cooldown", mutation.previousCooldown());
            encoded.put("previous-name", mutation.previousName());
            encoded.put("new-name", mutation.newName());
            encoded.put("new-count", mutation.newCount());
            encoded.put("new-cooldown", mutation.newCooldown());
            recoveries.add(encoded);
        }
        yaml.set("recoveries", recoveries);
        try {
            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException failure) {
            plugin.getLogger().log(Level.SEVERE,
                    "A crates-data.yml mentése nem sikerült", failure);
            return false;
        } catch (final CriticalPersistenceWriteError fatal) {
            plugin.getLogger().severe(fatal.getMessage() == null
                    ? fatal.toString() : fatal.getMessage());
            return false;
        }
    }

    /**
     * Stops new openings. Work that has not consumed a key is rolled back; work that might have
     * crossed an irreversible reward boundary is retained as a durable recovery/manual-review
     * record. The core's immediately following final store save persists this classification.
     */
    public void shutdown() {
        acceptingOpens = false;
        CrateSpinGUI.cancelAll();
        final long deadline = System.nanoTime() + 500_000_000L;
        while (inFlightGrants.get() > 0 && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        drainDeferredCurrencyRollbacks();
        synchronized (stateLock) {
            for (final PendingOpen pending : List.copyOf(pendingOpens.values())) {
                final CrateRecoveryLedger.Recovery recovery = recoveryLedger.get(pending.openingId);
                final CrateOpeningLifecycle.State state = pending.lifecycle.state();
                if (recovery != null) {
                    if (state == CrateOpeningLifecycle.State.RESERVED
                            || state == CrateOpeningLifecycle.State.PERSISTED
                            || (!pending.keysConsumed && !pending.irreversibleFence)) {
                        recoveryLedger.transition(pending.openingId,
                                CrateRecoveryLedger.Disposition.ROLLBACK_ONLY,
                                "orderly-shutdown-before-key-consumption");
                    } else if (pending.keysConsumed && !pending.irreversibleFence) {
                        recoveryLedger.transition(pending.openingId,
                                CrateRecoveryLedger.Disposition.REFUND_KEYS,
                                "orderly-shutdown-after-key-consumption-before-reward");
                    } else {
                        recoveryLedger.transition(pending.openingId,
                                CrateRecoveryLedger.Disposition.MANUAL_REVIEW,
                                "orderly-shutdown-during-possible-reward-side-effect");
                    }
                }
                if (state == CrateOpeningLifecycle.State.RESERVED
                        || state == CrateOpeningLifecycle.State.PERSISTED) {
                    pending.lifecycle.rollbackBeforeGrant();
                } else if (state == CrateOpeningLifecycle.State.GRANTING
                        && !pending.keysConsumed && !pending.irreversibleFence) {
                    pending.lifecycle.finishCompensatedRollback();
                } else if (state == CrateOpeningLifecycle.State.GRANTING) {
                    pending.lifecycle.failPartial();
                }
                finishPendingLocked(pending);
            }
            pendingOpens.clear();
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        CrateSpinGUI.cancel(playerId);
        final PendingOpen pending;
        synchronized (stateLock) {
            pending = pendingOpens.get(playerId);
        }
        if (pending == null) {
            return;
        }
        if (!pending.keysConsumed) {
            rollbackBeforeKeyConsumptionAsync(pending, null,
                    "player quit/kick before key consumption");
        } else if (!pending.irreversibleFence) {
            makeRecoveryAutoRefundable(pending,
                    "player quit/kick after key consumption before reward");
        } else {
            failPartial(pending, null,
                    "player quit/kick during possible reward side effect");
        }
    }
}
