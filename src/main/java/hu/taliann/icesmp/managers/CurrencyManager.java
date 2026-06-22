package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Wallet;
import hu.taliann.icesmp.items.CurrencyItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
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
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
public final class CurrencyManager implements PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyItemFactory itemFactory;
    private final File storageFile;
    private final Map<UUID, EnumMap<CurrencyType, Double>> balances = new ConcurrentHashMap<>();
    /** Serializes disk writes so the global tax task and region-thread commands never write concurrently. */
    private final Object saveLock = new Object();
    /** Debounce flag: at most one pending async flush is scheduled at a time. */
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private CurrencyType defaultCurrencyType = CurrencyType.NEUTRAL;

    public CurrencyManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemFactory = new CurrencyItemFactory(plugin, configManager);
        this.storageFile = new File(plugin.getDataFolder(), "currency-balances.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        defaultCurrencyType = resolveDefaultCurrencyType();
        balances.clear();

        if (!storageFile.exists()) {
            return;
        }

        final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(storageFile);
        final ConfigurationSection playersSection = configuration.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (final String uuidKey : playersSection.getKeys(false)) {
            final UUID uuid = parseUuid(uuidKey);
            if (uuid == null) {
                continue;
            }

            final ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidKey);
            if (playerSection == null) {
                continue;
            }

            final EnumMap<CurrencyType, Double> loadedBalances = createEmptyBalanceMap();
            for (final CurrencyType currencyType : CurrencyType.values()) {
                loadedBalances.put(currencyType, Math.max(0.0D, playerSection.getDouble(currencyType.name(), 0.0D)));
            }

            balances.put(uuid, loadedBalances);
        }
    }

    /**
     * Synchronously flushes balances to disk. Used on shutdown; gameplay paths
     * should call {@link #requestSave()} to debounce the write off the region thread.
     */
    public void save() {
        flushToDisk();
    }

    /**
     * Schedules a debounced asynchronous flush. Many balance edits in a short
     * window coalesce into a single atomic write, avoiding an I/O storm on the
     * region threads while still persisting promptly.
     */
    public void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                flushToDisk();
            }, 2L, TimeUnit.SECONDS);
        }
    }

    private void flushToDisk() {
        synchronized (saveLock) {
            final YamlConfiguration configuration = new YamlConfiguration();
            for (final Map.Entry<UUID, EnumMap<CurrencyType, Double>> entry : balances.entrySet()) {
                final String basePath = "players." + entry.getKey();
                final EnumMap<CurrencyType, Double> playerBalances = entry.getValue();
                for (final CurrencyType currencyType : CurrencyType.values()) {
                    configuration.set(basePath + "." + currencyType.name(), playerBalances.getOrDefault(currencyType, 0.0D));
                }
            }

            try {
                YamlStore.saveAtomic(storageFile, configuration);
            } catch (final IOException exception) {
                plugin.getLogger().severe("Failed to save currency balances: " + exception.getMessage());
            }
        }
    }

    /**
     * Gets the default faction's currency type.
     *
     * @return the default faction type
     */
    public FactionType getDefaultCurrencyType() {
        return defaultCurrencyType.toFactionType();
    }

    /**
     * Gets the default currency type.
     *
     * @return the default currency type
     */
    public CurrencyType getDefaultCurrency() {
        return defaultCurrencyType;
    }

    public ItemStack createCurrencyItem(final FactionType currencyType, final long amount) {
        return itemFactory.create(currencyType == null ? defaultCurrencyType : CurrencyType.fromFactionType(currencyType), amount);
    }

    public ItemStack createCurrencyItem(final CurrencyType currencyType, final long amount) {
        return itemFactory.create(currencyType == null ? defaultCurrencyType : currencyType, amount);
    }

    public boolean isCurrencyItem(final ItemStack itemStack) {
        return itemFactory.isCurrencyItem(itemStack);
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
        return getBalance(player, currencyType == null ? defaultCurrencyType : CurrencyType.fromFactionType(currencyType));
    }

    public double getBalance(final Player player, final CurrencyType currencyType) {
        final CurrencyType resolvedType = currencyType == null ? defaultCurrencyType : currencyType;
        return getStoredBalance(player.getUniqueId(), resolvedType);
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
            joiner.add(factionType.getDisplayName() + ": " + formatBalance(currentBalances.getOrDefault(factionType, 0.0D)));
        }
        return joiner.toString();
    }

    public String formatBalance(final double amount) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(amount);
    }

    /**
     * Gets the stored bank balance of a (possibly offline) player by UUID.
     *
     * @param playerId the player UUID
     * @param currencyType the currency
     * @return the stored balance
     */
    public double getBalance(final UUID playerId, final CurrencyType currencyType) {
        if (playerId == null || currencyType == null) {
            return 0.0D;
        }

        return getStoredBalance(playerId, currencyType);
    }

    /**
     * Deducts an amount from a (possibly offline) player's bank balance if covered.
     * Used by the faction tax and other server-side sinks.
     *
     * @param playerId the player UUID
     * @param currencyType the currency
     * @param amount the amount to deduct
     * @return true if the balance covered the amount and it was deducted
     */
    public boolean deductFromBalance(final UUID playerId, final CurrencyType currencyType, final double amount) {
        if (playerId == null || currencyType == null || amount <= 0.0D) {
            return false;
        }

        if (!tryDeduct(playerId, currencyType, amount)) {
            return false;
        }
        requestSave();
        return true;
    }

    /**
     * Adds an amount to a (possibly offline) player's bank balance.
     *
     * @param playerId the player UUID
     * @param currencyType the currency
     * @param amount the amount to add
     */
    public void addToBalance(final UUID playerId, final CurrencyType currencyType, final double amount) {
        if (playerId == null || currencyType == null || amount <= 0.0D) {
            return;
        }

        adjustBalance(playerId, currencyType, amount);
        requestSave();
    }

    /**
     * Gets the total circulating supply of a currency across every stored wallet.
     * Used by the dynamic exchange rate model: scarcer currencies become more valuable.
     *
     * @param currencyType the currency to sum
     * @return the total banked amount of the currency on the server
     */
    public double getTotalSupply(final CurrencyType currencyType) {
        if (currencyType == null) {
            return 0.0D;
        }

        double total = 0.0D;
        for (final EnumMap<CurrencyType, Double> playerBalances : balances.values()) {
            total += playerBalances.getOrDefault(currencyType, 0.0D);
        }
        return total;
    }

    public void setBalance(final Player player, final long amount) {
        setBalance(player, defaultCurrencyType, amount);
    }

    public void setBalance(final Player player, final FactionType currencyType, final long amount) {
        setBalance(player, currencyType == null ? defaultCurrencyType : CurrencyType.fromFactionType(currencyType), (double) amount);
    }

    public void setBalance(final Player player, final FactionType currencyType, final double amount) {
        setBalance(player, currencyType == null ? defaultCurrencyType : CurrencyType.fromFactionType(currencyType), amount);
    }

    public void setBalance(final Player player, final CurrencyType currencyType, final double amount) {
        final CurrencyType resolvedType = currencyType == null ? defaultCurrencyType : currencyType;
        final double clamped = Math.max(0.0D, amount);
        balances.compute(player.getUniqueId(), (key, existing) -> {
            final EnumMap<CurrencyType, Double> map = existing != null ? existing : createEmptyBalanceMap();
            map.put(resolvedType, clamped);
            return map;
        });
        requestSave();
    }

    public double deposit(final Player player) {
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] contents = inventory.getContents();
        double deposited = 0.0D;

        final Map<CurrencyType, Double> pendingDeposits = new java.util.EnumMap<>(CurrencyType.class);

        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack itemStack = contents[slot];
            if (!itemFactory.isCurrencyItem(itemStack)) {
                continue;
            }

            final CurrencyType currencyType = itemFactory.getCurrencyType(itemStack);
            if (currencyType == null) {
                continue;
            }

            deposited += itemStack.getAmount();
            pendingDeposits.merge(currencyType, (double) itemStack.getAmount(), Double::sum);
            contents[slot] = null;
        }

        if (deposited > 0.0D) {
            for (final Map.Entry<CurrencyType, Double> entry : pendingDeposits.entrySet()) {
                adjustBalance(player.getUniqueId(), entry.getKey(), entry.getValue());
            }
            inventory.setContents(contents);
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
        final CurrencyType resolvedType = currencyType == null ? defaultCurrencyType : CurrencyType.fromFactionType(currencyType);
        return withdraw(player, resolvedType, amount);
    }

    public boolean withdraw(final Player player, final CurrencyType currencyType, final int amount) {
        if (amount <= 0) {
            return false;
        }

        final CurrencyType resolvedType = currencyType == null ? defaultCurrencyType : currencyType;
        if (!tryDeduct(player.getUniqueId(), resolvedType, amount)) {
            return false;
        }

        giveCurrency(player, resolvedType, amount);
        requestSave();
        return true;
    }

    public boolean transfer(final Player from, final Player to, final long amount) {
        return transfer(from, to, defaultCurrencyType.toFactionType(), amount);
    }

    public boolean transfer(final Player from, final Player to, final FactionType currencyType, final long amount) {
        if (amount <= 0L) {
            return false;
        }

        final CurrencyType resolvedType = currencyType == null ? defaultCurrencyType : CurrencyType.fromFactionType(currencyType);
        if (!tryDeduct(from.getUniqueId(), resolvedType, amount)) {
            return false;
        }

        adjustBalance(to.getUniqueId(), resolvedType, amount);
        requestSave();
        return true;
    }

    public long exchange(final Player player, final FactionType from, final FactionType to, final long amount, final double rate, final double feePercent) {
        if (from == null || to == null || amount <= 0L || rate <= 0.0D || feePercent < 0.0D) {
            return -1L;
        }

        final CurrencyType fromType = CurrencyType.fromFactionType(from);
        final CurrencyType toType = CurrencyType.fromFactionType(to);
        if (fromType == toType) {
            return -1L;
        }

        final double grossTargetAmount = Math.max(0.0D, amount * rate);
        final double fee = Math.max(0.0D, grossTargetAmount * feePercent / 100.0D);
        // Credit a whole unit so the stored balance matches what the player is told (no hidden fractional dust).
        final long netTargetAmount = Math.round(grossTargetAmount - fee);
        if (netTargetAmount <= 0L) {
            return -1L;
        }

        if (!tryDeduct(player.getUniqueId(), fromType, amount)) {
            return -1L;
        }
        adjustBalance(player.getUniqueId(), toType, netTargetAmount);
        requestSave();
        return netTargetAmount;
    }

    private void giveCurrency(final Player player, final CurrencyType currencyType, final long amount) {
        long remaining = amount;
        while (remaining > 0L) {
            final int stackAmount = (int) Math.min(64L, remaining);
            final ItemStack currencyItem = createCurrencyItem(currencyType, stackAmount);
            final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(currencyItem);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            remaining -= stackAmount;
        }
    }

    /**
     * Atomically applies a delta to a player's balance. The whole read-modify-write
     * runs inside {@link ConcurrentHashMap#compute}, so concurrent edits from
     * different region threads are serialized per player and never lose an update.
     */
    private void adjustBalance(final UUID uuid, final CurrencyType currencyType, final double delta) {
        balances.compute(uuid, (key, existing) -> {
            final EnumMap<CurrencyType, Double> map = existing != null ? existing : createEmptyBalanceMap();
            final double current = map.getOrDefault(currencyType, 0.0D);
            map.put(currencyType, Math.max(0.0D, current + delta));
            return map;
        });
    }

    /**
     * Atomically deducts {@code amount} only if the player's balance covers it,
     * eliminating the check-then-act race that let concurrent withdrawals/transfers
     * double-spend.
     *
     * @return true if the amount was fully deducted
     */
    private boolean tryDeduct(final UUID uuid, final CurrencyType currencyType, final double amount) {
        if (amount <= 0.0D) {
            return false;
        }
        final boolean[] deducted = {false};
        balances.compute(uuid, (key, existing) -> {
            final EnumMap<CurrencyType, Double> map = existing != null ? existing : createEmptyBalanceMap();
            final double current = map.getOrDefault(currencyType, 0.0D);
            if (current >= amount) {
                map.put(currencyType, current - amount);
                deducted[0] = true;
            }
            return map;
        });
        return deducted[0];
    }

    private double getStoredBalance(final UUID uuid, final CurrencyType currencyType) {
        final EnumMap<CurrencyType, Double> currentBalances = balances.get(uuid);
        if (currentBalances == null) {
            return 0.0D;
        }

        return currentBalances.getOrDefault(currencyType, 0.0D);
    }

    private EnumMap<CurrencyType, Double> createEmptyBalanceMap() {
        final EnumMap<CurrencyType, Double> map = new EnumMap<>(CurrencyType.class);
        for (final CurrencyType currencyType : CurrencyType.values()) {
            map.put(currencyType, 0.0D);
        }
        return map;
    }

    private UUID parseUuid(final String value) {
        try {
            return UUID.fromString(value);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private CurrencyType resolveDefaultCurrencyType() {
        final String configuredType = configManager.getString("currency.default-type", CurrencyType.NEUTRAL.name());
        final CurrencyType parsedType = CurrencyType.fromInput(configuredType);
        return parsedType == null ? CurrencyType.NEUTRAL : parsedType;
    }

    public void cleanup(final UUID playerId) {
        // No volatile per-session caches currently stored for currency operations.
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }
}

