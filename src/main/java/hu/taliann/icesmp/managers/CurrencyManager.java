package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Wallet;
import hu.taliann.icesmp.items.CurrencyItemFactory;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.TransactionJournal;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class CurrencyManager implements PlayerStateCleanup, PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyItemFactory itemFactory;
    private final File storageFile;
    private final Map<UUID, EnumMap<CurrencyType, Double>> balances = new ConcurrentHashMap<>();
    /** Restart-durable exact-once wallet witnesses used by Profile v2 cross-store operations. */
    private final Map<String, DurableWalletOperation> durableWalletOperations = new ConcurrentHashMap<>();
    private static final int MAX_DURABLE_WALLET_OPERATIONS = 4096;
    private static final Set<String> DURABLE_OPERATION_KEYS = Set.of("operation-id", "player-id",
            "currency", "amount", "created-at", "status", "previous-present", "previous", "expected");
    /** Serializes every wallet mutation with the durable snapshot writer. */
    private final Object saveLock = new Object();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private CurrencyType defaultCurrencyType = CurrencyType.NEUTRAL;

    /** Exact before/after wallet snapshot used by crate compensation. */
    public record DurableMutation(UUID playerId, boolean previousPresent,
                                  Map<CurrencyType, Double> previous,
                                  Map<CurrencyType, Double> expected) {
        public DurableMutation {
            previous = Map.copyOf(previous);
            expected = Map.copyOf(expected);
        }
    }

    public enum DurableWalletOperationStatus { DEBITED, COMMITTED, ROLLED_BACK }

    public record DurableWalletOperation(String operationId, UUID playerId, CurrencyType currency,
                                         double amount, long createdAtEpochMillis,
                                         DurableWalletOperationStatus status,
                                         boolean previousPresent,
                                         Map<CurrencyType, Double> previous,
                                         Map<CurrencyType, Double> expected) {
        public DurableWalletOperation {
            operationId = requireDurableOperationId(operationId);
            java.util.Objects.requireNonNull(playerId, "playerId");
            java.util.Objects.requireNonNull(currency, "currency");
            java.util.Objects.requireNonNull(status, "status");
            if (!Double.isFinite(amount) || amount <= 0.0D || createdAtEpochMillis <= 0L) {
                throw new IllegalArgumentException("Invalid durable wallet operation");
            }
            previous = Map.copyOf(previous);
            expected = Map.copyOf(expected);
            validateWalletSnapshot(previous, "previous");
            validateWalletSnapshot(expected, "expected");
        }
        DurableWalletOperation withStatus(final DurableWalletOperationStatus next) {
            return new DurableWalletOperation(operationId, playerId, currency, amount,
                    createdAtEpochMillis, next, previousPresent, previous, expected);
        }
    }

    public CurrencyManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemFactory = new CurrencyItemFactory(plugin, configManager);
        this.storageFile = new File(plugin.getDataFolder(), "currency-balances.yml");
        YamlStore.registerCriticalWrite(storageFile);
        plugin.getDataFolder().mkdirs();
    }

    /** Parses into a temporary candidate and swaps live state only after full validation. */
    public void load() {
        final CurrencyType loadedDefault = resolveDefaultCurrencyType();
        final YamlConfiguration configuration = YamlStore.loadTracked(storageFile, plugin.getLogger());
        final Map<UUID, EnumMap<CurrencyType, Double>> loaded = new HashMap<>();
        final ConfigurationSection playersSection = configuration.getConfigurationSection("players");
        if (playersSection != null) {
            for (final String uuidKey : playersSection.getKeys(false)) {
                final UUID uuid;
                try {
                    uuid = UUID.fromString(uuidKey);
                } catch (final IllegalArgumentException invalidUuid) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen wallet UUID: " + uuidKey);
                    return;
                }
                final ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidKey);
                if (playerSection == null) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Hiányzó wallet szakasz: players." + uuidKey);
                    return;
                }
                final EnumMap<CurrencyType, Double> wallet = createEmptyBalanceMap();
                for (final CurrencyType currency : CurrencyType.values()) {
                    final Object raw = playerSection.get(currency.name());
                    if (raw == null) {
                        continue;
                    }
                    if (!(raw instanceof Number number)) {
                        YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                "Nem numerikus wallet érték: players." + uuidKey + "." + currency.name());
                        return;
                    }
                    final double amount = number.doubleValue();
                    if (!Double.isFinite(amount) || amount < 0.0D) {
                        YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                "Érvénytelen wallet érték: players." + uuidKey + "." + currency.name());
                        return;
                    }
                    wallet.put(currency, amount);
                }
                loaded.put(uuid, wallet);
            }
        }
        final Map<String, DurableWalletOperation> loadedOperations = new HashMap<>();
        final Object rawOperations = configuration.get("operations");
        final ConfigurationSection operationsSection = configuration.getConfigurationSection("operations");
        if (rawOperations != null && operationsSection == null) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "The durable wallet operations root must be a section");
            return;
        }
        if (operationsSection != null) {
            for (final String storageKey : operationsSection.getKeys(false)) {
                final ConfigurationSection section = operationsSection.getConfigurationSection(storageKey);
                if (section == null || !section.getKeys(false).equals(DURABLE_OPERATION_KEYS)) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Invalid durable wallet operation fields: " + storageKey);
                    return;
                }
                try {
                    final String operationId = requireDurableOperationId(requireString(section,
                            "operation-id", 192));
                    if (!storageKey.equals(durableOperationKey(operationId))) {
                        throw new IllegalArgumentException("operation storage key mismatch");
                    }
                    final UUID playerId = UUID.fromString(requireString(section, "player-id", 64));
                    final CurrencyType currency = CurrencyType.valueOf(requireString(section,
                            "currency", 64));
                    final double amount = requireFinitePositive(section.get("amount"), "amount");
                    final long createdAt = requireIntegralLong(section.get("created-at"), 1L,
                            Long.MAX_VALUE, "created-at");
                    final DurableWalletOperationStatus status = DurableWalletOperationStatus.valueOf(
                            requireString(section, "status", 32));
                    final Object rawPreviousPresent = section.get("previous-present");
                    if (!(rawPreviousPresent instanceof Boolean previousPresent)) {
                        throw new IllegalArgumentException("previous-present must be boolean");
                    }
                    final EnumMap<CurrencyType, Double> previous = readWalletSnapshot(
                            section.getConfigurationSection("previous"), "previous");
                    final EnumMap<CurrencyType, Double> expected = readWalletSnapshot(
                            section.getConfigurationSection("expected"), "expected");
                    final DurableWalletOperation operation = new DurableWalletOperation(operationId,
                            playerId, currency, amount, createdAt, status, previousPresent,
                            previous, expected);
                    if (loadedOperations.putIfAbsent(operationId, operation) != null) {
                        throw new IllegalArgumentException("duplicate durable operation id");
                    }
                } catch (final RuntimeException invalidOperation) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Invalid durable wallet operation " + storageKey + ": "
                                    + invalidOperation.getMessage());
                    return;
                }
            }
        }
        synchronized (saveLock) {
            defaultCurrencyType = loadedDefault;
            balances.clear();
            balances.putAll(loaded);
            durableWalletOperations.clear();
            durableWalletOperations.putAll(loadedOperations);
        }
    }

    /** Synchronous durable flush. A failure propagates to the transaction caller. */
    public void save() {
        if (!flushToDisk()) {
            throw unavailable("A wallet mentése piaci tranzakció közben zárolva van.");
        }
    }

    /**
     * Debounced save during ordinary gameplay. During journal recovery the current thread owns the
     * wallet gate, therefore the repair MUST be written synchronously before the journal entry can
     * be removed. This closes the crash window where recovery mutated memory, deleted the WAL entry,
     * and died before the delayed wallet flush.
     */
    public void requestSave() {
        if (YamlStore.hasCriticalWriteFailure()) {
            return;
        }
        if (TransactionJournal.isRecoveryOwnerThread()) {
            save();
            return;
        }
        if (saveScheduled.compareAndSet(false, true)) {
            scheduleFlushAttempt(2L);
        }
    }

    private void scheduleFlushAttempt(final long delaySeconds) {
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
            if (YamlStore.hasCriticalWriteFailure()) {
                saveScheduled.set(false);
                return;
            }
            if (!flushToDisk()) {
                scheduleFlushAttempt(1L);
                return;
            }
            saveScheduled.set(false);
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /** @return false only when another thread owns the market transaction gate. */
    private boolean flushToDisk() {
        return TransactionJournal.withCurrencyMutationPermit(() -> {
            if (!isStorageHealthy()) {
                throw unavailable("A wallet store hibás vagy írási hiba után letiltott.");
            }
            synchronized (saveLock) {
                if (!isStorageHealthy()) {
                    throw unavailable("A wallet store hibás vagy írási hiba után letiltott.");
                }
                writeBalancesLocked();
            }
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }

    /** Caller owns {@link #saveLock} and the currency mutation permit. */
    private void writeBalancesLocked() {
        final YamlConfiguration configuration = new YamlConfiguration();
        for (final Map.Entry<UUID, EnumMap<CurrencyType, Double>> entry : balances.entrySet()) {
            final String basePath = "players." + entry.getKey();
            final EnumMap<CurrencyType, Double> playerBalances = entry.getValue();
            for (final CurrencyType currencyType : CurrencyType.values()) {
                final double value = playerBalances.getOrDefault(currencyType, 0.0D);
                if (!Double.isFinite(value) || value < 0.0D) {
                    throw unavailable("Nem menthető wallet érték: " + entry.getKey()
                            + "/" + currencyType.name());
                }
                configuration.set(basePath + "." + currencyType.name(), value);
            }
        }
        for (final DurableWalletOperation operation : durableWalletOperations.values().stream()
                .sorted(java.util.Comparator.comparing(DurableWalletOperation::operationId)).toList()) {
            final String basePath = "operations." + durableOperationKey(operation.operationId());
            configuration.set(basePath + ".operation-id", operation.operationId());
            configuration.set(basePath + ".player-id", operation.playerId().toString());
            configuration.set(basePath + ".currency", operation.currency().name());
            configuration.set(basePath + ".amount", operation.amount());
            configuration.set(basePath + ".created-at", operation.createdAtEpochMillis());
            configuration.set(basePath + ".status", operation.status().name());
            configuration.set(basePath + ".previous-present", operation.previousPresent());
            for (final CurrencyType type : CurrencyType.values()) {
                configuration.set(basePath + ".previous." + type.name(),
                        operation.previous().getOrDefault(type, 0.0D));
                configuration.set(basePath + ".expected." + type.name(),
                        operation.expected().getOrDefault(type, 0.0D));
            }
        }
        try {
            YamlStore.saveAtomic(storageFile, configuration);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save currency balances: "
                    + exception.getMessage());
            throw unavailable("A wallet tartós mentése sikertelen.");
        }
    }

    public boolean isStorageHealthy() {
        return isOwnStorageHealthy() && !YamlStore.hasCriticalWriteFailure();
    }

    private boolean isOwnStorageHealthy() {
        return !YamlStore.isLoadFailed(storageFile) && !YamlStore.hasWriteFailure(storageFile);
    }

    public FactionType getDefaultCurrencyType() {
        return defaultCurrencyType.toFactionType();
    }

    public CurrencyType getDefaultCurrency() {
        return defaultCurrencyType;
    }

    public ItemStack createCurrencyItem(final FactionType currencyType, final long amount) {
        return itemFactory.create(currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType), amount);
    }

    public ItemStack createCurrencyItem(final CurrencyType currencyType, final long amount) {
        return itemFactory.create(currencyType == null ? defaultCurrencyType : currencyType, amount);
    }

    public boolean isCurrencyItem(final ItemStack itemStack) {
        return itemFactory.isCurrencyItem(itemStack);
    }

    /** Physical token payout; does not mutate the bank ledger. */
    public void payOutTokens(final Player player, final CurrencyType currencyType, final long amount) {
        final CurrencyType currency = currencyType == null ? defaultCurrencyType : currencyType;
        long left = Math.max(0L, amount);
        while (left > 0L) {
            final long batch = Math.min(64L, left);
            left -= batch;
            for (final ItemStack overflow : player.getInventory()
                    .addItem(itemFactory.create(currency, batch)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
    }

    public CurrencyType getCurrencyType(final ItemStack itemStack) {
        return itemFactory.getCurrencyType(itemStack);
    }

    public Wallet getWallet(final Player player) {
        return new Wallet(player.getUniqueId(), getBalances(player));
    }

    public double getBalance(final Player player) {
        return getBalance(player, defaultCurrencyType);
    }

    public double getBalance(final Player player, final FactionType currencyType) {
        return getBalance(player, currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType));
    }

    public double getBalance(final Player player, final CurrencyType currencyType) {
        final CurrencyType resolvedType = currencyType == null ? defaultCurrencyType : currencyType;
        return getStoredBalance(player.getUniqueId(), resolvedType);
    }

    public double getTotalBalance(final Player player) {
        if (player == null) {
            return 0.0D;
        }
        double total = 0.0D;
        for (final CurrencyType currencyType : CurrencyType.values()) {
            total += getBalance(player, currencyType);
        }
        return total;
    }

    public Map<FactionType, Double> getBalances(final Player player) {
        final EnumMap<FactionType, Double> balancesByFaction = new EnumMap<>(FactionType.class);
        for (final CurrencyType currencyType : CurrencyType.values()) {
            balancesByFaction.put(currencyType.toFactionType(), getBalance(player, currencyType));
        }
        return Map.copyOf(balancesByFaction);
    }

    public String describeBalances(final Player player) {
        final Map<FactionType, Double> currentBalances = getBalances(player);
        final StringJoiner joiner = new StringJoiner(", ");
        for (final FactionType factionType : FactionType.values()) {
            joiner.add(factionType.getDisplayName() + ": "
                    + formatBalance(currentBalances.getOrDefault(factionType, 0.0D)));
        }
        return joiner.toString();
    }

    public String formatBalance(final double amount) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(amount);
    }

    public double getBalance(final UUID playerId, final CurrencyType currencyType) {
        if (playerId == null || currencyType == null) {
            return 0.0D;
        }
        return getStoredBalance(playerId, currencyType);
    }

    public boolean deductFromBalance(final UUID playerId, final CurrencyType currencyType,
                                     final double amount) {
        if (playerId == null || currencyType == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return false;
        }
        final boolean deducted = withMutation(() -> tryDeductUnsafe(playerId, currencyType, amount),
                false);
        if (deducted) {
            requestSave();
        }
        return deducted;
    }

    public void addToBalance(final UUID playerId, final CurrencyType currencyType,
                             final double amount) {
        if (playerId == null || currencyType == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return;
        }
        addBalances(playerId, Map.of(currencyType, amount));
    }

    /** Applies one reward batch under a single wallet mutation permit, without partial currency types. */
    public void addBalances(final UUID playerId, final Map<CurrencyType, Double> additions) {
        if (playerId == null || additions == null || additions.isEmpty()) {
            return;
        }
        final EnumMap<CurrencyType, Double> validated = new EnumMap<>(CurrencyType.class);
        for (final Map.Entry<CurrencyType, Double> entry : additions.entrySet()) {
            final CurrencyType type = entry.getKey();
            final Double amount = entry.getValue();
            if (type == null || amount == null || !Double.isFinite(amount) || amount <= 0.0D) {
                throw new IllegalArgumentException("A wallet batch csak véges, pozitív összegeket fogad.");
            }
            validated.merge(type, amount, Double::sum);
            if (!Double.isFinite(validated.get(type))) {
                throw new IllegalArgumentException("A wallet batch összege nem véges.");
            }
        }
        requireMutation(() -> balances.compute(playerId, (key, existing) -> {
            final EnumMap<CurrencyType, Double> updated = existing == null
                    ? createEmptyBalanceMap() : new EnumMap<>(existing);
            for (final Map.Entry<CurrencyType, Double> entry : validated.entrySet()) {
                final double next = updated.getOrDefault(entry.getKey(), 0.0D) + entry.getValue();
                if (!Double.isFinite(next) || next < 0.0D) {
                    throw new IllegalArgumentException("A wallet batch túlcsordulna.");
                }
                updated.put(entry.getKey(), next);
            }
            return updated;
        }));
        requestSave();
    }

    /**
     * Applies one currency reward batch and synchronously persists it before returning. On write
     * failure the in-memory wallet is restored before the exception escapes.
     */
    public DurableMutation addBalancesDurably(final UUID playerId,
                                               final Map<CurrencyType, Double> additions) {
        if (playerId == null || additions == null || additions.isEmpty()) {
            throw new IllegalArgumentException("A tartós wallet batch nem lehet üres.");
        }
        final EnumMap<CurrencyType, Double> validated = validatePositiveBatch(additions);
        final DurableMutation mutation = TransactionJournal.withCurrencyMutationPermit(() -> {
            if (!isStorageHealthy()) {
                throw unavailable("A wallet store jelenleg nem írható.");
            }
            synchronized (saveLock) {
                if (!isStorageHealthy()) {
                    throw unavailable("A wallet store jelenleg nem írható.");
                }
                final boolean previousPresent = balances.containsKey(playerId);
                final EnumMap<CurrencyType, Double> previous = previousPresent
                        ? new EnumMap<>(balances.get(playerId)) : createEmptyBalanceMap();
                final EnumMap<CurrencyType, Double> expected = new EnumMap<>(previous);
                for (final Map.Entry<CurrencyType, Double> entry : validated.entrySet()) {
                    final double next = expected.getOrDefault(entry.getKey(), 0.0D) + entry.getValue();
                    if (!Double.isFinite(next) || next < 0.0D) {
                        throw new IllegalArgumentException("A wallet batch túlcsordulna.");
                    }
                    expected.put(entry.getKey(), next);
                }
                balances.put(playerId, expected);
                try {
                    writeBalancesLocked();
                } catch (final RuntimeException | Error failure) {
                    restoreWalletUnsafe(playerId, previousPresent, previous);
                    throw failure;
                }
                return new DurableMutation(playerId, previousPresent, previous, expected);
            }
        }, null);
        if (mutation == null) {
            throw unavailable("Piaci tranzakció alatt a tartós wallet-módosítás nem indítható.");
        }
        return mutation;
    }

    /** Plans an exact durable deduction without publishing or writing it. */
    public DurableMutation planDurableDeduction(final UUID playerId,
                                                final CurrencyType currencyType,
                                                final double amount) {
        if (playerId == null || currencyType == null || !Double.isFinite(amount) || amount <= 0.0D) {
            throw new IllegalArgumentException("Érvénytelen tartós wallet levonás.");
        }
        synchronized (saveLock) {
            if (!isStorageHealthy()) {
                throw unavailable("A wallet store jelenleg nem írható.");
            }
            final boolean previousPresent = balances.containsKey(playerId);
            final EnumMap<CurrencyType, Double> previous = previousPresent
                    ? new EnumMap<>(balances.get(playerId)) : createEmptyBalanceMap();
            final double current = previous.getOrDefault(currencyType, 0.0D);
            if (!Double.isFinite(current) || current < amount) {
                return null;
            }
            final EnumMap<CurrencyType, Double> expected = new EnumMap<>(previous);
            expected.put(currencyType, current - amount);
            return new DurableMutation(playerId, previousPresent, previous, expected);
        }
    }

    /** Applies a previously journaled deduction only if its exact before-snapshot is still current. */
    public void applyDurably(final DurableMutation mutation) {
        if (mutation == null) {
            throw new IllegalArgumentException("Hiányzó wallet mutation token.");
        }
        final boolean success = TransactionJournal.runCurrencyMutation(() -> {
            if (!isStorageHealthy()) {
                throw unavailable("A wallet store jelenleg nem írható.");
            }
            synchronized (saveLock) {
                final EnumMap<CurrencyType, Double> current = balances.get(mutation.playerId());
                final Map<CurrencyType, Double> actual = current == null
                        ? createEmptyBalanceMap() : current;
                if (balances.containsKey(mutation.playerId()) != mutation.previousPresent()
                        || !actual.equals(mutation.previous())) {
                    throw new IllegalStateException(
                            "A wallet mutation terv elavult; a tranzakciót újra kell kezdeni.");
                }
                balances.put(mutation.playerId(), new EnumMap<>(mutation.expected()));
                try {
                    writeBalancesLocked();
                } catch (final RuntimeException | Error failure) {
                    restoreWalletUnsafe(mutation.playerId(), mutation.previousPresent(),
                            new EnumMap<>(mutation.previous()));
                    throw failure;
                }
            }
        });
        if (!success) {
            throw unavailable("Piaci tranzakció alatt a tartós wallet-módosítás nem indítható.");
        }
    }

    public Map<CurrencyType, Double> walletSnapshot(final UUID playerId) {
        synchronized (saveLock) {
            final EnumMap<CurrencyType, Double> current = balances.get(playerId);
            return current == null ? Map.copyOf(createEmptyBalanceMap()) : Map.copyOf(current);
        }
    }

    public boolean walletMatches(final DurableMutation mutation, final boolean expectedAfter) {
        if (mutation == null) {
            return false;
        }
        synchronized (saveLock) {
            final EnumMap<CurrencyType, Double> current = balances.get(mutation.playerId());
            final Map<CurrencyType, Double> actual = current == null
                    ? createEmptyBalanceMap() : current;
            final boolean present = balances.containsKey(mutation.playerId());
            if (expectedAfter ? !present : present != mutation.previousPresent()) {
                return false;
            }
            return actual.equals(expectedAfter ? mutation.expected() : mutation.previous());
        }
    }

    /** Durable purchase deduction. Null means insufficient balance; storage failures throw. */
    public DurableMutation deductDurably(final UUID playerId, final CurrencyType currencyType,
                                         final double amount) {
        final DurableMutation mutation = planDurableDeduction(playerId, currencyType, amount);
        if (mutation == null) {
            return null;
        }
        applyDurably(mutation);
        return mutation;
    }

    /** Restores a durable mutation only while its exact expected snapshot is still current. */
    public void rollbackDurably(final DurableMutation mutation) {
        if (mutation == null) {
            return;
        }
        final boolean success = TransactionJournal.runCompensatingCurrencyMutation(() -> {
            if (!isOwnStorageHealthy()) {
                throw unavailable("A wallet store saját állapota nem írható vissza.");
            }
            synchronized (saveLock) {
                final EnumMap<CurrencyType, Double> current = balances.get(mutation.playerId());
                if (current == null || !current.equals(mutation.expected())) {
                    throw new IllegalStateException("A wallet rollback token elavult; kézi audit szükséges.");
                }
                final EnumMap<CurrencyType, Double> expected = new EnumMap<>(current);
                restoreWalletUnsafe(mutation.playerId(), mutation.previousPresent(),
                        new EnumMap<>(mutation.previous()));
                try {
                    writeBalancesLocked();
                } catch (final RuntimeException | Error failure) {
                    balances.put(mutation.playerId(), expected);
                    throw failure;
                }
            }
        });
        if (!success) {
            throw unavailable("Piaci tranzakció alatt a wallet rollback nem indítható.");
        }
    }

    /** Creates or replays an exact restart-durable wallet debit. */
    public DurableWalletOperation debitOperation(final UUID playerId, final CurrencyType currency,
                                                  final double amount, final String operationId) {
        if (playerId == null || currency == null || !Double.isFinite(amount) || amount <= 0.0D) {
            throw new IllegalArgumentException("Invalid durable wallet debit");
        }
        final String id = requireDurableOperationId(operationId);
        final Object result = TransactionJournal.withCurrencyMutationPermit(() -> {
            synchronized (saveLock) {
                final DurableWalletOperation existing = durableWalletOperations.get(id);
                if (existing != null) {
                    if (!existing.playerId().equals(playerId) || existing.currency() != currency
                            || Double.compare(existing.amount(), amount) != 0) {
                        throw new IllegalStateException("Durable wallet operation identity conflict");
                    }
                    return existing;
                }
                if (durableWalletOperations.size() >= MAX_DURABLE_WALLET_OPERATIONS) {
                    pruneDurableOperationsLocked();
                }
                if (durableWalletOperations.size() >= MAX_DURABLE_WALLET_OPERATIONS) {
                    throw unavailable("A durable wallet operation ledger megtelt.");
                }
                final boolean previousPresent = balances.containsKey(playerId);
                final EnumMap<CurrencyType, Double> previous = previousPresent
                        ? new EnumMap<>(balances.get(playerId)) : createEmptyBalanceMap();
                final double current = previous.getOrDefault(currency, 0.0D);
                if (!Double.isFinite(current) || current < amount) return Boolean.FALSE;
                final EnumMap<CurrencyType, Double> expected = new EnumMap<>(previous);
                expected.put(currency, current - amount);
                final DurableWalletOperation created = new DurableWalletOperation(id, playerId,
                        currency, amount, System.currentTimeMillis(), DurableWalletOperationStatus.DEBITED,
                        previousPresent, previous, expected);
                balances.put(playerId, expected);
                durableWalletOperations.put(id, created);
                try {
                    writeBalancesLocked();
                } catch (final RuntimeException failure) {
                    durableWalletOperations.remove(id);
                    restoreWalletUnsafe(playerId, previousPresent, previous);
                    throw failure;
                }
                return created;
            }
        }, null);
        if (result == null) throw unavailable("A durable wallet debit nem indítható.");
        return result instanceof DurableWalletOperation operation ? operation : null;
    }

    public Optional<DurableWalletOperation> durableOperation(final String operationId) {
        if (operationId == null || operationId.isBlank()) return Optional.empty();
        synchronized (saveLock) { return Optional.ofNullable(durableWalletOperations.get(operationId.trim())); }
    }

    public DurableWalletOperation commitOperation(final String operationId) {
        return transitionOperation(operationId, DurableWalletOperationStatus.COMMITTED, false);
    }

    public DurableWalletOperation rollbackOperation(final String operationId) {
        return transitionOperation(operationId, DurableWalletOperationStatus.ROLLED_BACK, true);
    }

    private DurableWalletOperation transitionOperation(final String operationId,
                                                        final DurableWalletOperationStatus target,
                                                        final boolean restoreWallet) {
        final String id = requireDurableOperationId(operationId);
        final Object result = TransactionJournal.withCurrencyMutationPermit(() -> {
            synchronized (saveLock) {
                final DurableWalletOperation current = durableWalletOperations.get(id);
                if (current == null) throw new IllegalStateException("Unknown durable wallet operation");
                if (current.status() == target) return current;
                if (current.status() != DurableWalletOperationStatus.DEBITED) {
                    throw new IllegalStateException("Durable wallet operation is already terminal");
                }
                final EnumMap<CurrencyType, Double> live = balances.get(current.playerId());
                if (live == null || !live.equals(current.expected())) {
                    throw new IllegalStateException("Durable wallet witness no longer matches live wallet");
                }
                if (restoreWallet) {
                    restoreWalletUnsafe(current.playerId(), current.previousPresent(),
                            new EnumMap<>(current.previous()));
                }
                final DurableWalletOperation updated = current.withStatus(target);
                durableWalletOperations.put(id, updated);
                try {
                    writeBalancesLocked();
                } catch (final RuntimeException failure) {
                    durableWalletOperations.put(id, current);
                    if (restoreWallet) balances.put(current.playerId(), new EnumMap<>(current.expected()));
                    throw failure;
                }
                return updated;
            }
        }, null);
        if (result == null) throw unavailable("A durable wallet operation transition nem indítható.");
        return (DurableWalletOperation) result;
    }

    private void pruneDurableOperationsLocked() {
        durableWalletOperations.values().stream()
                .filter(operation -> operation.status() != DurableWalletOperationStatus.DEBITED)
                .sorted(java.util.Comparator.comparingLong(DurableWalletOperation::createdAtEpochMillis))
                .limit(Math.max(0, durableWalletOperations.size() - MAX_DURABLE_WALLET_OPERATIONS + 64L))
                .map(DurableWalletOperation::operationId).toList()
                .forEach(durableWalletOperations::remove);
    }

    private EnumMap<CurrencyType, Double> validatePositiveBatch(
            final Map<CurrencyType, Double> additions) {
        final EnumMap<CurrencyType, Double> validated = new EnumMap<>(CurrencyType.class);
        for (final Map.Entry<CurrencyType, Double> entry : additions.entrySet()) {
            final CurrencyType type = entry.getKey();
            final Double amount = entry.getValue();
            if (type == null || amount == null || !Double.isFinite(amount) || amount <= 0.0D) {
                throw new IllegalArgumentException("A wallet batch csak véges, pozitív összegeket fogad.");
            }
            validated.merge(type, amount, Double::sum);
            if (!Double.isFinite(validated.get(type))) {
                throw new IllegalArgumentException("A wallet batch összege nem véges.");
            }
        }
        return validated;
    }

    private void restoreWalletUnsafe(final UUID playerId, final boolean previousPresent,
                                     final EnumMap<CurrencyType, Double> previous) {
        if (previousPresent) {
            balances.put(playerId, new EnumMap<>(previous));
        } else {
            balances.remove(playerId);
        }
    }

    public double getTotalSupply(final CurrencyType currencyType) {
        if (currencyType == null) {
            return 0.0D;
        }
        synchronized (saveLock) {
            double total = 0.0D;
            for (final EnumMap<CurrencyType, Double> playerBalances : balances.values()) {
                total += playerBalances.getOrDefault(currencyType, 0.0D);
            }
            return total;
        }
    }

    public void setBalance(final Player player, final long amount) {
        setBalance(player, defaultCurrencyType, amount);
    }

    public void setBalance(final Player player, final FactionType currencyType, final long amount) {
        setBalance(player, currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType), (double) amount);
    }

    public void setBalance(final Player player, final FactionType currencyType, final double amount) {
        setBalance(player, currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType), amount);
    }

    public void setBalance(final Player player, final CurrencyType currencyType, final double amount) {
        if (player == null || !Double.isFinite(amount)) {
            return;
        }
        final CurrencyType resolvedType = currencyType == null ? defaultCurrencyType : currencyType;
        final double clamped = Math.max(0.0D, amount);
        requireMutation(() -> balances.compute(player.getUniqueId(), (key, existing) -> {
            final EnumMap<CurrencyType, Double> map = existing != null
                    ? existing : createEmptyBalanceMap();
            map.put(resolvedType, clamped);
            return map;
        }));
        requestSave();
    }

    public double deposit(final Player player) {
        if (player == null) {
            return 0.0D;
        }
        final double deposited = withMutation(() -> {
            final PlayerInventory inventory = player.getInventory();
            final ItemStack[] contents = inventory.getContents();
            double total = 0.0D;
            final Map<CurrencyType, Double> pending = new EnumMap<>(CurrencyType.class);
            for (int slot = 0; slot < contents.length; slot++) {
                final ItemStack itemStack = contents[slot];
                if (!itemFactory.isCurrencyItem(itemStack)) {
                    continue;
                }
                final CurrencyType currencyType = itemFactory.getCurrencyType(itemStack);
                if (currencyType == null) {
                    continue;
                }
                total += itemStack.getAmount();
                pending.merge(currencyType, (double) itemStack.getAmount(), Double::sum);
                contents[slot] = null;
            }
            if (total > 0.0D) {
                for (final Map.Entry<CurrencyType, Double> entry : pending.entrySet()) {
                    adjustBalanceUnsafe(player.getUniqueId(), entry.getKey(), entry.getValue());
                }
                inventory.setContents(contents);
            }
            return total;
        }, 0.0D);
        if (deposited > 0.0D) {
            requestSave();
        }
        return deposited;
    }

    public void refreshPlayerCurrencyItems(final Player player) {
        if (player == null) {
            return;
        }
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack itemStack = contents[slot];
            if (!itemFactory.isCurrencyItem(itemStack)) {
                continue;
            }
            contents[slot] = itemFactory.refresh(itemStack);
            changed = true;
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }

    public boolean withdraw(final Player player, final FactionType currencyType, final int amount) {
        final CurrencyType resolvedType = currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType);
        return withdraw(player, resolvedType, amount);
    }

    public boolean withdraw(final Player player, final CurrencyType currencyType, final int amount) {
        if (player == null || amount <= 0) {
            return false;
        }
        final CurrencyType resolvedType = currencyType == null ? defaultCurrencyType : currencyType;
        final boolean success = withMutation(() -> {
            if (!tryDeductUnsafe(player.getUniqueId(), resolvedType, amount)) {
                return false;
            }
            giveCurrency(player, resolvedType, amount);
            return true;
        }, false);
        if (success) {
            requestSave();
        }
        return success;
    }

    public boolean transfer(final Player from, final Player to, final long amount) {
        return transfer(from, to, defaultCurrencyType.toFactionType(), amount);
    }

    public boolean transfer(final Player from, final Player to, final FactionType currencyType,
                            final long amount) {
        if (from == null || to == null || amount <= 0L) {
            return false;
        }
        final CurrencyType resolvedType = currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType);
        final boolean success = withMutation(() -> {
            if (!tryDeductUnsafe(from.getUniqueId(), resolvedType, amount)) {
                return false;
            }
            adjustBalanceUnsafe(to.getUniqueId(), resolvedType, amount);
            return true;
        }, false);
        if (success) {
            requestSave();
        }
        return success;
    }

    public long exchange(final Player player, final FactionType from, final FactionType to,
                         final long amount, final double rate, final double feePercent) {
        if (player == null || from == null || to == null || amount <= 0L
                || !Double.isFinite(rate) || rate <= 0.0D
                || !Double.isFinite(feePercent) || feePercent < 0.0D) {
            return -1L;
        }
        final CurrencyType fromType = CurrencyType.fromFactionType(from);
        final CurrencyType toType = CurrencyType.fromFactionType(to);
        if (fromType == toType) {
            return -1L;
        }
        final double grossTargetAmount = amount * rate;
        final double fee = grossTargetAmount * feePercent / 100.0D;
        if (!Double.isFinite(grossTargetAmount) || !Double.isFinite(fee)) {
            return -1L;
        }
        final long netTargetAmount = Math.round(Math.max(0.0D, grossTargetAmount - fee));
        if (netTargetAmount <= 0L) {
            return -1L;
        }
        final long result = withMutation(() -> {
            if (!tryDeductUnsafe(player.getUniqueId(), fromType, amount)) {
                return -1L;
            }
            adjustBalanceUnsafe(player.getUniqueId(), toType, netTargetAmount);
            return netTargetAmount;
        }, -1L);
        if (result > 0L) {
            requestSave();
        }
        return result;
    }

    private void giveCurrency(final Player player, final CurrencyType currencyType,
                              final long amount) {
        long remaining = amount;
        while (remaining > 0L) {
            final int stackAmount = (int) Math.min(64L, remaining);
            final ItemStack currencyItem = createCurrencyItem(currencyType, stackAmount);
            final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(currencyItem);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(item -> player.getWorld()
                        .dropItemNaturally(player.getLocation(), item));
            }
            remaining -= stackAmount;
        }
    }

    private <T> T withMutation(final Supplier<T> mutation, final T deniedValue) {
        return TransactionJournal.withCurrencyMutationPermit(() -> {
            if (!isStorageHealthy()) {
                return deniedValue;
            }
            synchronized (saveLock) {
                if (!isStorageHealthy()) {
                    return deniedValue;
                }
                return mutation.get();
            }
        }, deniedValue);
    }

    private void requireMutation(final Runnable mutation) {
        final boolean success = TransactionJournal.runCurrencyMutation(() -> {
            if (!isStorageHealthy()) {
                throw unavailable("A wallet store jelenleg nem írható.");
            }
            synchronized (saveLock) {
                if (!isStorageHealthy()) {
                    throw unavailable("A wallet store jelenleg nem írható.");
                }
                mutation.run();
            }
        });
        if (!success) {
            throw unavailable("Piaci tranzakció alatt egy másik wallet-módosítás nem indítható.");
        }
    }

    private void adjustBalanceUnsafe(final UUID uuid, final CurrencyType currencyType,
                                     final double delta) {
        balances.compute(uuid, (key, existing) -> {
            final EnumMap<CurrencyType, Double> map = existing != null
                    ? existing : createEmptyBalanceMap();
            final double current = map.getOrDefault(currencyType, 0.0D);
            final double updated = current + delta;
            if (!Double.isFinite(updated)) {
                throw new IllegalArgumentException("A wallet művelet nem véges eredményt adna.");
            }
            map.put(currencyType, Math.max(0.0D, updated));
            return map;
        });
    }

    private boolean tryDeductUnsafe(final UUID uuid, final CurrencyType currencyType,
                                    final double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) {
            return false;
        }
        final boolean[] deducted = {false};
        balances.compute(uuid, (key, existing) -> {
            final EnumMap<CurrencyType, Double> map = existing != null
                    ? existing : createEmptyBalanceMap();
            final double current = map.getOrDefault(currencyType, 0.0D);
            if (Double.isFinite(current) && current >= amount) {
                map.put(currencyType, current - amount);
                deducted[0] = true;
            }
            return map;
        });
        return deducted[0];
    }

    private double getStoredBalance(final UUID uuid, final CurrencyType currencyType) {
        synchronized (saveLock) {
            final EnumMap<CurrencyType, Double> currentBalances = balances.get(uuid);
            if (currentBalances == null) {
                return 0.0D;
            }
            return currentBalances.getOrDefault(currencyType, 0.0D);
        }
    }

    private EnumMap<CurrencyType, Double> createEmptyBalanceMap() {
        final EnumMap<CurrencyType, Double> map = new EnumMap<>(CurrencyType.class);
        for (final CurrencyType currencyType : CurrencyType.values()) {
            map.put(currencyType, 0.0D);
        }
        return map;
    }

    private static String requireDurableOperationId(final String operationId) {
        if (operationId == null || operationId.isBlank() || operationId.trim().length() > 192) {
            throw new IllegalArgumentException("Durable operation id must be non-blank and bounded");
        }
        return operationId.trim();
    }

    private static String durableOperationKey(final String operationId) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requireDurableOperationId(operationId).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String requireString(final ConfigurationSection section, final String key,
                                        final int maximum) {
        final Object raw = section.get(key);
        if (!(raw instanceof String value) || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(key + " must be a bounded string");
        }
        return value;
    }

    private static long requireIntegralLong(final Object raw, final long minimum,
                                            final long maximum, final String key) {
        if (!(raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long)) {
            throw new IllegalArgumentException(key + " must be an integral scalar");
        }
        final long value = ((Number) raw).longValue();
        if (value < minimum || value > maximum) throw new IllegalArgumentException(key + " out of range");
        return value;
    }

    private static double requireFinitePositive(final Object raw, final String key) {
        if (!(raw instanceof Number number)) throw new IllegalArgumentException(key + " must be numeric");
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value <= 0.0D) throw new IllegalArgumentException(key + " invalid");
        return value;
    }

    private static EnumMap<CurrencyType, Double> readWalletSnapshot(
            final ConfigurationSection section, final String label) {
        if (section == null || !section.getKeys(false).equals(java.util.Arrays.stream(CurrencyType.values())
                .map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()))) {
            throw new IllegalArgumentException(label + " wallet snapshot fields differ");
        }
        final EnumMap<CurrencyType, Double> result = new EnumMap<>(CurrencyType.class);
        for (final CurrencyType type : CurrencyType.values()) {
            final Object raw = section.get(type.name());
            if (!(raw instanceof Number number)) throw new IllegalArgumentException(label + " value not numeric");
            final double value = number.doubleValue();
            if (!Double.isFinite(value) || value < 0.0D) throw new IllegalArgumentException(label + " value invalid");
            result.put(type, value);
        }
        return result;
    }

    private static void validateWalletSnapshot(final Map<CurrencyType, Double> snapshot,
                                               final String label) {
        for (final CurrencyType type : CurrencyType.values()) {
            final Double value = snapshot.get(type);
            if (value == null || !Double.isFinite(value) || value < 0.0D) {
                throw new IllegalArgumentException(label + " wallet snapshot is incomplete or invalid");
            }
        }
        if (snapshot.size() != CurrencyType.values().length) {
            throw new IllegalArgumentException(label + " wallet snapshot has unknown keys");
        }
    }

    private CurrencyType resolveDefaultCurrencyType() {
        final String configuredType = configManager.getString("currency.default-type",
                CurrencyType.NEUTRAL.name());
        final CurrencyType parsedType = CurrencyType.fromInput(configuredType);
        return parsedType == null ? CurrencyType.NEUTRAL : parsedType;
    }

    private CurrencyStorageUnavailableException unavailable(final String message) {
        return new CurrencyStorageUnavailableException(message);
    }

    public void cleanup(final UUID playerId) {
        // No volatile per-session caches currently stored for currency operations.
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }
}
