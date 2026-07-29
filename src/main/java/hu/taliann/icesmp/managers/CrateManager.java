package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.crates.CrateLedger;
import hu.taliann.icesmp.crates.CrateRules;
import hu.taliann.icesmp.crates.KeyConsumption;
import hu.taliann.icesmp.crates.WeightedSelector;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.gui.CrateBrowserGUI;
import hu.taliann.icesmp.gui.CrateSpinGUI;
import hu.taliann.icesmp.items.BlueprintItemFactory;
import hu.taliann.icesmp.items.CrateKeyFactory;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;

/** IceSMP-native physical crate, reward, cooldown and statistics owner. */
public final class CrateManager implements PersistentStore, PlayerStateCleanup {

    private static final int SCHEMA = 1;
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

    private enum PendingStatus {
        RESERVED,
        PERSISTED,
        COMPLETED,
        CANCELLED
    }

    private static final class PendingOpen {
        private final UUID playerId;
        private final String playerName;
        private final String crateId;
        private final long configGeneration;
        private final int opens;
        private final int keysRequired;
        private final StoredLocation source;
        private final List<RewardEntry> rewards;
        private PendingStatus status = PendingStatus.RESERVED;
        private CrateLedger.Mutation ledgerMutation;

        private PendingOpen(final Player player, final String crateId, final long configGeneration,
                            final int opens, final int keysRequired, final StoredLocation source,
                            final List<RewardEntry> rewards) {
            this.playerId = player.getUniqueId();
            this.playerName = player.getName();
            this.crateId = crateId;
            this.configGeneration = configGeneration;
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
    private final Map<UUID, PendingOpen> pendingOpens = new HashMap<>();
    private final AtomicLong configGeneration = new AtomicLong();
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
        YamlStore.registerCriticalWrite(storageFile);
        crateKeyFactory.bind(this);
    }

    // ==================== config snapshot ====================

    /** Rebuilds the immutable crate snapshot; one bad crate is disabled without stopping IceSMP. */
    public void reloadConfig() {
        final long generation = configGeneration.incrementAndGet();
        final List<String> errors = new ArrayList<>();
        final Map<String, CrateDefinition> definitions = new LinkedHashMap<>();
        final ConfigurationSection root = configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("crates");
        final boolean globallyEnabled = configManager.getBoolean("crates-settings.enabled", true);
        final boolean spin = configManager.getBoolean("crates-settings.spin-animation", true);

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
        final boolean enabled = section.getBoolean("enabled", true);
        final String displayName = requiredText(section.getString("display-name"), "display-name");
        final Material keyMaterial = Material.matchMaterial(requiredText(section.getString("key-material"), "key-material"));
        if (keyMaterial == null || keyMaterial.isAir()) {
            throw new IllegalArgumentException("ismeretlen vagy AIR key-material");
        }
        final String keyName = requiredText(section.getString("key-name"), "key-name");
        final String keyItemModel = blankToNull(section.getString("key-item-model"));

        final CurrencyType priceCurrency = CurrencyType.fromInput(section.getString("key-price.currency", "NEUTRAL"));
        final double priceAmount = section.getDouble("key-price.amount", 0.0D);
        if (priceCurrency == null || !Double.isFinite(priceAmount) || priceAmount < 0.0D
                || priceAmount > MAX_KEY_PRICE) {
            throw new IllegalArgumentException("érvénytelen key-price currency/amount");
        }

        final String permission = blankToNull(section.getString("permission"));
        if (permission != null && (!permission.startsWith("icesmp.") || permission.contains(" ")
                || permission.length() > 96)) {
            throw new IllegalArgumentException("a permission kötelezően icesmp.* névtérben legyen");
        }

        final Set<String> worlds = new LinkedHashSet<>();
        for (final String configuredWorld : section.getStringList("worlds")) {
            final World world = Bukkit.getWorld(configuredWorld);
            if (world == null) {
                throw new IllegalArgumentException("ismeretlen világ a worlds listában: " + configuredWorld);
            }
            worlds.add(world.getName().toLowerCase(Locale.ROOT));
        }

        final int requiredKeys = CrateRules.boundedPositiveInt(section.get("required-key-count"), 1,
                CrateRules.MAX_REQUIRED_KEYS, "required-key-count");
        final long cooldownMillis = CrateRules.cooldownMillis(section.get("cooldown-seconds"));
        final boolean massEnabled = section.getBoolean("mass-open.enabled", false);
        final int massMaximum = CrateRules.boundedPositiveInt(section.get("mass-open.max-openings"), 10,
                CrateRules.MAX_MASS_OPEN, "mass-open.max-openings");

        final String soundName = requiredText(section.getString("opening-sound.sound",
                "ENTITY_PLAYER_LEVELUP"), "opening-sound.sound");
        final NamespacedKey soundKey = NamespacedKey.fromString(soundName.contains(":")
                ? soundName.toLowerCase(Locale.ROOT)
                : "minecraft:" + soundName.toLowerCase(Locale.ROOT));
        final Sound openingSound = soundKey == null ? null : Registry.SOUNDS.get(soundKey);
        if (openingSound == null) {
            throw new IllegalArgumentException("ismeretlen opening-sound: " + soundName);
        }
        final double volumeRaw = section.getDouble("opening-sound.volume", 1.0D);
        final double pitchRaw = section.getDouble("opening-sound.pitch", 1.2D);
        if (!Double.isFinite(volumeRaw) || volumeRaw < 0.0D || volumeRaw > 10.0D
                || !Double.isFinite(pitchRaw) || pitchRaw < 0.0D || pitchRaw > 2.0D) {
            throw new IllegalArgumentException("az opening-sound volume 0..10, pitch 0..2 lehet");
        }

        final boolean broadcast = section.getBoolean("broadcast.enabled", false);
        final String broadcastMessage = requiredText(section.getString("broadcast.message",
                "&6[Láda] &f{player} &ekinyitotta: &f{crate} &7({amount}×)"), "broadcast.message");

        final List<Map<?, ?>> rawRewards = section.getMapList("rewards");
        if (rawRewards.isEmpty() || rawRewards.size() > CrateRules.MAX_REWARDS) {
            throw new IllegalArgumentException("a rewards lista 1.." + CrateRules.MAX_REWARDS + " elemű lehet");
        }
        final List<RewardEntry> rewards = new ArrayList<>();
        for (int index = 0; index < rawRewards.size(); index++) {
            try {
                rewards.add(parseReward(id, rawRewards.get(index)));
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

    public boolean canAccess(final Player player, final CrateDefinition definition) {
        if (!player.hasPermission(hu.taliann.icesmp.core.Permissions.CRATE_USE)) {
            return false;
        }
        return definition.permission() == null || player.hasPermission(definition.permission());
    }

    public void openBrowser(final Player player) {
        CrateBrowserGUI.openList(player, this, currencyManager);
    }

    public void openPreview(final Player player, final String crateId) {
        CrateBrowserGUI.openPreview(player, this, crateId);
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
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
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
        });
    }

    public void removeCrateAsync(final Location location, final Consumer<MutationResult> callback) {
        if (location == null || location.getWorld() == null) {
            callback.accept(MutationResult.fail("crate-not-a-crate"));
            return;
        }
        final StoredLocation stored = StoredLocation.from(location);
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
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
        });
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

    public String buyKey(final Player player, final String crateId, final int amount) {
        final CrateDefinition definition = definition(crateId);
        if (definition == null || !definition.enabled()) {
            return "crate-unknown";
        }
        if (!canAccess(player, definition)) {
            return "crate-no-permission";
        }
        if (amount <= 0 || amount > CrateRules.MAX_KEY_AMOUNT) {
            return "crate-invalid-amount";
        }
        final double totalPrice = definition.keyPriceAmount() * amount;
        if (!Double.isFinite(totalPrice) || totalPrice > MAX_KEY_PRICE * CrateRules.MAX_KEY_AMOUNT) {
            return "crate-invalid-amount";
        }
        if (totalPrice > 0.0D && !currencyManager.isStorageHealthy()) {
            return "crate-storage-unavailable";
        }
        if (totalPrice > 0.0D && !currencyManager.deductFromBalance(player.getUniqueId(),
                definition.keyPriceCurrency(), totalPrice)) {
            return currencyManager.isStorageHealthy()
                    ? "crate-insufficient-funds" : "crate-storage-unavailable";
        }
        giveKeys(player, crateId, amount);
        return null;
    }

    public void giveKeys(final Player player, final String crateId, final int amount) {
        int remaining = Math.max(1, Math.min(CrateRules.MAX_KEY_AMOUNT, amount));
        while (remaining > 0) {
            final int chunk = Math.min(64, remaining);
            giveItemSafely(player, crateKeyFactory.createKey(crateId, chunk));
            remaining -= chunk;
        }
    }

    /** Begins a persist-before-side-effect opening transaction from a physical crate interaction. */
    public void requestOpen(final Player player, final String crateId, final Location crateLocation,
                            final int requestedOpenings) {
        final ConfigSnapshot snapshot = configSnapshot;
        final CrateDefinition definition = snapshot.definitions().get(crateId);
        if (!snapshot.enabled() || definition == null || !definition.enabled()) {
            player.sendMessage(messageManager.get("crate-broken", "&cEz a láda jelenleg le van tiltva vagy hibás."));
            return;
        }
        if (!acceptingOpens || YamlStore.hasWriteFailure(storageFile)
                || (definition.hasCurrencyReward() && !currencyManager.isStorageHealthy())) {
            player.sendMessage(messageManager.get("crate-storage-unavailable",
                    "&cA ládarendszer vagy a valuta-state nem írható; a nyitás biztonsági okból leállt."));
            return;
        }
        if (!canAccess(player, definition)) {
            player.sendMessage(messageManager.get("crate-no-permission", "&cEhhez a ládához nincs jogosultságod."));
            return;
        }
        final String sourceWorld = crateLocation == null || crateLocation.getWorld() == null
                ? player.getWorld().getName() : crateLocation.getWorld().getName();
        if (!definition.allowsWorld(sourceWorld)) {
            player.sendMessage(messageManager.get("crate-world-disabled", "&cEz a láda ebben a világban nem nyitható."));
            return;
        }
        synchronized (stateLock) {
            if (pendingOpens.containsKey(player.getUniqueId())) {
                player.sendMessage(messageManager.get("crate-opening-busy", "&eEgy korábbi ládanyitásod még folyamatban van."));
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
        final PendingOpen pending = new PendingOpen(player, crateId, snapshot.generation(), openings,
                Math.multiplyExact(openings, definition.requiredKeys()),
                crateLocation == null ? null : StoredLocation.from(crateLocation), rewards);
        synchronized (stateLock) {
            if (pendingOpens.putIfAbsent(player.getUniqueId(), pending) != null) {
                player.sendMessage(messageManager.get("crate-opening-busy", "&eEgy korábbi ládanyitásod még folyamatban van."));
                return;
            }
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> prepareOpen(player, pending));
    }

    private void prepareOpen(final Player player, final PendingOpen pending) {
        boolean committed = false;
        String failureKey = "crate-opening-changed";
        synchronized (stateLock) {
            if (isCurrent(pending, PendingStatus.RESERVED) && acceptingOpens
                    && !YamlStore.hasWriteFailure(storageFile)) {
                final CrateDefinition definition = configSnapshot.definitions().get(pending.crateId);
                if (definition != null && configSnapshot.generation() == pending.configGeneration
                        && (!definition.hasCurrencyReward() || currencyManager.isStorageHealthy())
                        && ledger.remainingCooldown(pending.playerId, pending.crateId,
                        System.currentTimeMillis()) == 0L) {
                    try {
                        pending.ledgerMutation = ledger.record(pending.playerId, pending.playerName,
                                pending.crateId, pending.opens, System.currentTimeMillis(), definition.cooldownMillis());
                        pending.status = PendingStatus.PERSISTED;
                        if (writeStateLocked()) {
                            committed = true;
                        } else {
                            ledger.rollback(pending.ledgerMutation);
                            pending.status = PendingStatus.CANCELLED;
                            pendingOpens.remove(pending.playerId, pending);
                            failureKey = "crate-storage-unavailable";
                        }
                    } catch (final ArithmeticException | IllegalArgumentException invalidState) {
                        pending.status = PendingStatus.CANCELLED;
                        pendingOpens.remove(pending.playerId, pending);
                        plugin.getLogger().log(Level.SEVERE,
                                "Crate ledger mutation rejected for " + pending.playerId + "/" + pending.crateId,
                                invalidState);
                        failureKey = "crate-broken";
                    }
                } else {
                    pending.status = PendingStatus.CANCELLED;
                    pendingOpens.remove(pending.playerId, pending);
                    failureKey = definition != null && definition.hasCurrencyReward()
                            && !currencyManager.isStorageHealthy()
                            ? "crate-storage-unavailable" : "crate-opening-changed";
                }
            }
        }
        if (!committed) {
            notifyPlayer(player, failureKey, switch (failureKey) {
                case "crate-broken" -> "&cA láda állapota nem bővíthető biztonságosan; a kulcsok nem fogytak el.";
                case "crate-opening-changed" -> "&eA ládanyitás feltételei közben megváltoztak; a kulcsok nem fogytak el.";
                default -> "&cA ládanyitás nem menthető biztonságosan; a kulcsok nem fogytak el.";
            });
            return;
        }
        player.getScheduler().run(plugin, task -> finalizeOpen(player, pending),
                () -> rollbackPending(pending, true));
    }

    private void finalizeOpen(final Player player, final PendingOpen pending) {
        final CrateDefinition definition = configSnapshot.definitions().get(pending.crateId);
        if (definition == null || configSnapshot.generation() != pending.configGeneration
                || !acceptingOpens || !canAccess(player, definition)
                || (definition.hasCurrencyReward() && !currencyManager.isStorageHealthy())
                || !definition.allowsWorld(player.getWorld().getName())
                || !nearSource(player, pending.source)) {
            rollbackPending(pending, true);
            player.sendMessage(messageManager.get("crate-opening-changed",
                    "&eA ládanyitás feltételei közben megváltoztak; a kulcsok nem fogytak el."));
            return;
        }

        final List<Integer> slots = new ArrayList<>();
        final List<Integer> amounts = new ArrayList<>();
        final ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (pending.crateId.equals(crateKeyFactory.keyCrateId(storage[slot]))) {
                slots.add(slot);
                amounts.add(storage[slot].getAmount());
            }
        }
        final List<KeyConsumption.Take> takes = KeyConsumption.plan(amounts, pending.keysRequired);
        if (takes.isEmpty()) {
            rollbackPending(pending, true);
            player.sendMessage(messageManager.get("crate-not-enough-keys",
                    "&cA szükséges kulcsok közben már nincsenek nálad; a nyitás visszavonva."));
            return;
        }

        final List<List<ItemStack>> resolvedItems = new ArrayList<>(pending.rewards.size());
        for (final RewardEntry reward : pending.rewards) {
            final List<ItemStack> items = resolveItems(player, reward);
            if (isItemType(reward.type()) && items.isEmpty()) {
                rollbackPending(pending, true);
                player.sendMessage(messageManager.get("crate-broken",
                        "&cA kiválasztott jutalom nem építhető; a kulcsok nem fogytak el."));
                return;
            }
            resolvedItems.add(items);
        }

        final ItemStack[] originalStorage = Arrays.stream(storage)
                .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        for (final KeyConsumption.Take take : takes) {
            final int slot = slots.get(take.index());
            final ItemStack stack = storage[slot];
            final int left = stack.getAmount() - take.amount();
            storage[slot] = left <= 0 ? null : stack.asQuantity(left);
        }
        player.getInventory().setStorageContents(storage);

        final Map<CurrencyType, Double> currencyRewards = new EnumMap<>(CurrencyType.class);
        for (final RewardEntry reward : pending.rewards) {
            if (reward.type() == RewardType.CURRENCY) {
                currencyRewards.merge(reward.currency(), reward.currencyAmount(), Double::sum);
            }
        }
        try {
            if (!currencyRewards.isEmpty()) {
                currencyManager.addBalances(player.getUniqueId(), currencyRewards);
            }
        } catch (final RuntimeException currencyFailure) {
            player.getInventory().setStorageContents(originalStorage);
            rollbackPending(pending, true);
            plugin.getLogger().log(Level.SEVERE,
                    "Crate currency reward failed before item/command grant for " + pending.playerId,
                    currencyFailure);
            player.sendMessage(messageManager.get("crate-storage-unavailable",
                    "&cA valutajutalom nem írható biztonságosan; a kulcsok visszaálltak."));
            return;
        }

        for (int index = 0; index < pending.rewards.size(); index++) {
            grantReward(player, pending, pending.rewards.get(index), resolvedItems.get(index));
        }
        synchronized (stateLock) {
            if (isCurrent(pending, PendingStatus.PERSISTED)) {
                pending.status = PendingStatus.COMPLETED;
                pendingOpens.remove(pending.playerId, pending);
            }
        }
        finishOpening(player, definition, pending);
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

    private void grantReward(final Player player, final PendingOpen pending, final RewardEntry reward,
                             final List<ItemStack> resolvedItems) {
        switch (reward.type()) {
            case ITEM, UNIQUE_ITEM, RECIPE_ITEM, BLUEPRINT, CRATE_KEY ->
                    resolvedItems.forEach(item -> giveItemSafely(player, item));
            case CURRENCY -> {
                // Aggregated and granted atomically in-memory before item/command side effects.
            }
            case COMMAND -> {
                final String command = CrateRules.renderCommand(reward.command(), player.getName(),
                        player.getUniqueId().toString(), pending.crateId, pending.opens);
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    } catch (final RuntimeException failure) {
                        plugin.getLogger().log(Level.SEVERE, "Crate command reward failed: " + command, failure);
                    }
                });
            }
        }
    }

    private List<ItemStack> resolveItems(final Player player, final RewardEntry reward) {
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
            case CRATE_KEY -> split(crateKeyFactory.createKey(reward.value(), 1), reward.amount());
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

    private void finishOpening(final Player player, final CrateDefinition definition,
                               final PendingOpen pending) {
        final String rewardSummary = pending.rewards.stream().map(CrateManager::describeReward)
                .reduce((left, right) -> left + ", " + right).orElse("?");
        final Runnable feedback = () -> {
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    player.getLocation().add(0.0D, 1.0D, 0.0D), 16, 0.4D, 0.5D, 0.4D, 0.1D);
            player.playSound(player.getLocation(), definition.openingSound(),
                    definition.soundVolume(), definition.soundPitch());
            player.sendMessage(messageManager.get("crate-opened",
                    "&6[Láda] &eKinyitottad: &f%s &7(%s×) &e— nyeremény: &a%s",
                    definition.displayName(), pending.opens, rewardSummary));
        };

        final Location sourceLocation = pending.source == null ? null
                : new Location(player.getWorld(), pending.source.x(), pending.source.y(), pending.source.z());
        if (sourceLocation != null) {
            spawnCrateReveal(sourceLocation, definition.rewards(),
                    pending.rewards.get(pending.rewards.size() - 1));
        }
        if (configSnapshot.spinAnimation() && pending.opens == 1) {
            CrateSpinGUI.open(plugin, player, definition.displayName(), definition.rewards(),
                    pending.rewards.getFirst(), feedback);
        } else {
            feedback.run();
        }
        if (definition.broadcastEnabled()) {
            broadcast(definition.broadcastMessage()
                    .replace("{player}", player.getName())
                    .replace("{crate}", definition.displayName())
                    .replace("{amount}", Integer.toString(pending.opens)));
        }
        appendAuditAsync(player, pending, rewardSummary);
    }

    private void broadcast(final String message) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            final List<Player> viewers = List.copyOf(Bukkit.getOnlinePlayers());
            for (final Player viewer : viewers) {
                viewer.getScheduler().run(plugin,
                        playerTask -> viewer.sendMessage(hu.taliann.icesmp.utils.TextUtil.color(message)), null);
            }
        });
    }

    private void appendAuditAsync(final Player player, final PendingOpen pending, final String rewards) {
        final String line = Instant.now() + " player=" + player.getUniqueId() + " name="
                + sanitize(player.getName()) + " crate=" + pending.crateId + " opens=" + pending.opens
                + " keys=" + pending.keysRequired + " rewards=" + sanitize(rewards) + System.lineSeparator();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                final File parent = auditFile.getParentFile();
                if (parent != null) {
                    Files.createDirectories(parent.toPath());
                }
                if (auditFile.exists() && auditFile.length() >= AUDIT_ROTATE_BYTES) {
                    final File rotated = new File(parent, auditFile.getName() + ".1");
                    Files.deleteIfExists(rotated.toPath());
                    Files.move(auditFile.toPath(), rotated.toPath());
                }
                Files.writeString(auditFile.toPath(), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (final IOException failure) {
                plugin.getLogger().warning("A crate-openings.log írása nem sikerült: " + failure.getMessage());
            }
        });
    }

    private static String sanitize(final String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private void rollbackPending(final PendingOpen pending, final boolean persistAsync) {
        boolean changed = false;
        synchronized (stateLock) {
            if (pendingOpens.get(pending.playerId) != pending || pending.status == PendingStatus.CANCELLED
                    || pending.status == PendingStatus.COMPLETED) {
                return;
            }
            if (pending.status == PendingStatus.PERSISTED && pending.ledgerMutation != null) {
                ledger.rollback(pending.ledgerMutation);
                changed = true;
            }
            pending.status = PendingStatus.CANCELLED;
            pendingOpens.remove(pending.playerId, pending);
        }
        if (changed && persistAsync) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                synchronized (stateLock) {
                    if (!writeStateLocked()) {
                        plugin.getLogger().severe("Crate rollback mentése sikertelen; a write circuit lezárt.");
                    }
                }
            });
        }
    }

    private boolean isCurrent(final PendingOpen pending, final PendingStatus status) {
        return pendingOpens.get(pending.playerId) == pending && pending.status == status;
    }

    private void notifyPlayer(final Player player, final String key, final String fallback) {
        player.getScheduler().run(plugin, task -> player.sendMessage(messageManager.get(key, fallback)), null);
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
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            synchronized (stateLock) {
                if (pendingOpens.containsKey(playerId)) {
                    callback.accept(MutationResult.fail("crate-opening-busy"));
                    return;
                }
                final CrateLedger.ResetToken token = ledger.reset(playerId, crateId);
                if (!writeStateLocked()) {
                    ledger.rollbackReset(token);
                    callback.accept(MutationResult.fail("crate-storage-unavailable"));
                    return;
                }
            }
            callback.accept(MutationResult.ok());
        });
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
            display.getScheduler().runAtFixedRate(plugin, task -> {
                if (!display.isValid()) {
                    task.cancel();
                    return;
                }
                if (step[0] < cycles) {
                    display.setItemStack(new ItemStack(rewards.get(
                            ThreadLocalRandom.current().nextInt(rewards.size())).iconMaterial()));
                    step[0]++;
                } else {
                    task.cancel();
                    display.setItemStack(new ItemStack(picked.iconMaterial()));
                    display.setGlowing(true);
                    display.setGlowColorOverride(org.bukkit.Color.fromRGB(0xFFD24A));
                    hu.taliann.icesmp.utils.DisplayFxUtil.animateTo(plugin, display,
                            hu.taliann.icesmp.utils.DisplayFxUtil.scale(0.95F, 0.95F, 0.95F), 8);
                }
            }, null, cycleTicks, cycleTicks);
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
            pendingOpens.clear();
            acceptingOpens = true;
            if (!storageFile.exists()) {
                return;
            }
            final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
            if (yaml.getInt("schema", -1) != SCHEMA) {
                corrupt("hiányzó vagy ismeretlen schema; legacy migration nem támogatott");
            }
            final Map<StoredLocation, String> loadedBlocks = new LinkedHashMap<>();
            final List<Map<?, ?>> rawBlocks = yaml.getMapList("blocks");
            for (int index = 0; index < rawBlocks.size(); index++) {
                final Map<?, ?> raw = rawBlocks.get(index);
                try {
                    final UUID worldId = UUID.fromString(requiredText(stringValue(raw.get("world-uuid")), "world-uuid"));
                    final String storedWorldName = requiredText(stringValue(raw.get("world-name")), "world-name");
                    final World resolvedWorld = Bukkit.getWorld(worldId);
                    if (resolvedWorld == null) {
                        throw new IllegalArgumentException("a világ UUID jelenleg nem tölthető be: " + storedWorldName);
                    }
                    final String worldName = resolvedWorld.getName();
                    final int x = stateCoordinate(raw.get("x"), "x", -30_000_000, 30_000_000);
                    final int y = stateCoordinate(raw.get("y"), "y", -2048, 2048);
                    final int z = stateCoordinate(raw.get("z"), "z", -30_000_000, 30_000_000);
                    final String crateId = CrateRules.normalizeId(stringValue(raw.get("crate-id")));
                    if (crateId == null) {
                        throw new IllegalArgumentException("hibás crate-id");
                    }
                    final StoredLocation location = new StoredLocation(worldId, worldName, x, y, z);
                    if (loadedBlocks.putIfAbsent(location, crateId) != null) {
                        throw new IllegalArgumentException("duplikált location");
                    }
                } catch (final IllegalArgumentException invalid) {
                    corrupt("blocks[" + index + "]: " + invalid.getMessage());
                }
            }

            final Map<UUID, CrateLedger.PlayerSnapshot> loadedStats = new LinkedHashMap<>();
            final ConfigurationSection stats = yaml.getConfigurationSection("players");
            if (stats != null) {
                for (final String uuidRaw : stats.getKeys(false)) {
                    try {
                        final UUID uuid = UUID.fromString(uuidRaw);
                        final ConfigurationSection player = stats.getConfigurationSection(uuidRaw);
                        if (player == null) {
                            throw new IllegalArgumentException("nem objektum");
                        }
                        final String name = blankToNull(player.getString("last-known-name"));
                        if (name != null && name.length() > 64) {
                            throw new IllegalArgumentException("túl hosszú last-known-name");
                        }
                        final Map<String, Long> counts = readNonNegativeLongMap(player.getConfigurationSection("counts"), true);
                        long total = 0L;
                        for (final long count : counts.values()) {
                            total = Math.addExact(total, count);
                        }
                        final Map<String, Long> cooldowns = readNonNegativeLongMap(player.getConfigurationSection("cooldowns"), false);
                        loadedStats.put(uuid, new CrateLedger.PlayerSnapshot(name, counts, cooldowns));
                    } catch (final IllegalArgumentException invalid) {
                        corrupt("players." + uuidRaw + ": " + invalid.getMessage());
                    }
                }
            }
            crateBlocks.putAll(loadedBlocks);
            ledger.replace(loadedStats);
        }
    }

    private Map<String, Long> readNonNegativeLongMap(final ConfigurationSection section,
                                                      final boolean strictlyPositive) {
        if (section == null) {
            return Map.of();
        }
        final Map<String, Long> result = new LinkedHashMap<>();
        for (final String rawId : section.getKeys(false)) {
            final String id = CrateRules.normalizeId(rawId);
            final Object raw = section.get(rawId);
            if (id == null || !id.equals(rawId) || !(raw instanceof Number number)) {
                throw new IllegalArgumentException("hibás crate state: " + rawId);
            }
            final double exact = number.doubleValue();
            if (!Double.isFinite(exact) || exact != Math.rint(exact) || exact < 0.0D
                    || exact > Long.MAX_VALUE || (strictlyPositive && exact <= 0.0D)) {
                throw new IllegalArgumentException("hibás state érték: " + rawId);
            }
            result.put(id, number.longValue());
        }
        return result;
    }

    private static int stateCoordinate(final Object raw, final String field, final int minimum,
                                       final int maximum) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("hiányzó " + field);
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("hibás " + field);
        }
        return (int) value;
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
        for (final Map.Entry<UUID, CrateLedger.PlayerSnapshot> entry : ledger.snapshot().entrySet()) {
            final String path = "players." + entry.getKey();
            yaml.set(path + ".last-known-name", entry.getValue().lastKnownName());
            entry.getValue().counts().forEach((crate, count) -> yaml.set(path + ".counts." + crate, count));
            entry.getValue().cooldowns().forEach((crate, until) -> yaml.set(path + ".cooldowns." + crate, until));
        }
        try {
            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException failure) {
            plugin.getLogger().log(Level.SEVERE, "A crates-data.yml mentése nem sikerült", failure);
            return false;
        } catch (final CriticalPersistenceWriteError fatal) {
            plugin.getLogger().severe(fatal.getMessage() == null ? fatal.toString() : fatal.getMessage());
            return false;
        }
    }

    /** Stops new openings and rolls back every persist-before-grant transaction before final save. */
    public void shutdown() {
        acceptingOpens = false;
        CrateSpinGUI.cancelAll();
        synchronized (stateLock) {
            for (final PendingOpen pending : List.copyOf(pendingOpens.values())) {
                if (pending.status == PendingStatus.PERSISTED && pending.ledgerMutation != null) {
                    ledger.rollback(pending.ledgerMutation);
                }
                pending.status = PendingStatus.CANCELLED;
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
        if (pending != null) {
            rollbackPending(pending, true);
        }
    }
}
