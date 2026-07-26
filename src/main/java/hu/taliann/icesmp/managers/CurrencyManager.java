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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
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
    /** Serializes every wallet mutation with the durable snapshot writer. */
    private final Object saveLock = new Object();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private CurrencyType defaultCurrencyType = CurrencyType.NEUTRAL;

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
        synchronized (saveLock) {
            defaultCurrencyType = loadedDefault;
            balances.clear();
            balances.putAll(loaded);
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
                try {
                    YamlStore.saveAtomic(storageFile, configuration);
                } catch (final IOException exception) {
                    plugin.getLogger().severe("Failed to save currency balances: "
                            + exception.getMessage());
                    throw unavailable("A wallet tartós mentése sikertelen.");
                }
            }
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }

    public boolean isStorageHealthy() {
        return !YamlStore.isLoadFailed(storageFile) && !YamlStore.hasCriticalWriteFailure();
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
        requireMutation(() -> adjustBalanceUnsafe(playerId, currencyType, amount));
        requestSave();
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
