package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileEconomyStore;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileTaxStore;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared faction treasury. Membership-tax production is retired.
 *
 * <p>The YAML store contains only global treasury balances, tax rates and idempotent shared-grant
 * receipts. Player debt, evasion strikes, wallet deduction and settlement outboxes live solely in
 * each owner's EconomySection.</p>
 */
public final class FactionTreasuryManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final Object stateLock = new Object();
    private final Map<FactionType, Double> balances = new EnumMap<>(FactionType.class);
    private final Map<FactionType, Double> taxRates = new EnumMap<>(FactionType.class);
    private final Map<String, Long> appliedGrants = new LinkedHashMap<>();
    private final Map<UUID, Map<FactionType, Double>> arrearsProjection =
            new ConcurrentHashMap<>();
    private final PlayerProfileTaxStore taxStore = new PlayerProfileTaxStore();
    private final PlayerProfileEconomyStore economyStore = new PlayerProfileEconomyStore();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    public FactionTreasuryManager(final JavaPlugin plugin,
                                  final ConfigManager configManager,
                                  final CurrencyManager currencyManager,
                                  final FactionManager factionManager,
                                  final SinManager sinManager,
                                  final MessageManager messageManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.configManager = Objects.requireNonNull(configManager);
        this.currencyManager = Objects.requireNonNull(currencyManager);
        this.factionManager = Objects.requireNonNull(factionManager);
        this.sinManager = Objects.requireNonNull(sinManager);
        this.messageManager = Objects.requireNonNull(messageManager);
        this.storageFile = new File(plugin.getDataFolder(), "treasury.yml");
        YamlStore.registerCriticalWrite(storageFile);
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public void load() {
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        final EnumMap<FactionType, Double> loadedBalances = new EnumMap<>(FactionType.class);
        final EnumMap<FactionType, Double> loadedRates = new EnumMap<>(FactionType.class);
        for (final FactionType faction : FactionType.values()) {
            loadedBalances.put(faction, readNonNegative(yaml,
                    "treasury." + faction.name(), 0.0D));
            loadedRates.put(faction, readNonNegative(yaml,
                    "tax-rates." + faction.name(),
                    nonNegativeConfig("factions.tax.rate-percent", 2.0D)));
        }
        final LinkedHashMap<String, Long> loadedGrants = new LinkedHashMap<>();
        final ConfigurationSection grants = yaml.getConfigurationSection("applied-grants");
        if (grants != null) {
            for (final String key : grants.getKeys(false)) {
                final Object raw = grants.get(key);
                if (!(raw instanceof Number number) || number.longValue() < 0L) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Invalid treasury grant receipt: " + key);
                    return;
                }
                loadedGrants.put(key, number.longValue());
            }
        }
        synchronized (stateLock) {
            balances.clear();
            balances.putAll(loadedBalances);
            taxRates.clear();
            taxRates.putAll(loadedRates);
            appliedGrants.clear();
            appliedGrants.putAll(loadedGrants);
        }
    }

    @Override
    public void save() {
        synchronized (stateLock) {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final FactionType faction : FactionType.values()) {
                yaml.set("treasury." + faction.name(),
                        balances.getOrDefault(faction, 0.0D));
                yaml.set("tax-rates." + faction.name(),
                        taxRates.getOrDefault(faction,
                                nonNegativeConfig("factions.tax.rate-percent", 2.0D)));
            }
            appliedGrants.forEach((id, timestamp) ->
                    yaml.set("applied-grants." + id, timestamp));
            try {
                YamlStore.saveAtomic(storageFile, yaml);
            } catch (final IOException failure) {
                throw new java.io.UncheckedIOException("Failed to save faction treasury", failure);
            }
        }
    }

    public void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public double getBalance(final FactionType faction) {
        synchronized (stateLock) {
            return faction == null ? 0.0D : balances.getOrDefault(faction, 0.0D);
        }
    }

    public double getTaxRate(final FactionType faction) {
        synchronized (stateLock) {
            return faction == null
                    ? nonNegativeConfig("factions.tax.rate-percent", 2.0D)
                    : taxRates.getOrDefault(faction,
                    nonNegativeConfig("factions.tax.rate-percent", 2.0D));
        }
    }

    public double setTaxRate(final FactionType faction, final double ratePercent) {
        if (faction == null || !Double.isFinite(ratePercent) || ratePercent < 0.0D) {
            return faction == null ? 0.0D : getTaxRate(faction);
        }
        final double maximum = nonNegativeConfig("factions.tax.max-rate-percent", 10.0D);
        final double applied = Math.min(maximum, ratePercent);
        synchronized (stateLock) { taxRates.put(faction, applied); }
        requestSave();
        return applied;
    }

    public boolean depositOnce(final String grantId, final FactionType faction,
                               final double amount) {
        if (grantId == null || grantId.isBlank() || faction == null
                || !Double.isFinite(amount) || amount <= 0.0D) return false;
        synchronized (stateLock) {
            if (appliedGrants.containsKey(grantId)) return true;
            final double before = balances.getOrDefault(faction, 0.0D);
            final double after = PlayerProfileTaxStore.checkedAmountAdd(before, amount);
            if (!Double.isFinite(after)) return false;
            balances.put(faction, after);
            appliedGrants.put(grantId, System.currentTimeMillis());
            try {
                save();
                return true;
            } catch (final RuntimeException failure) {
                balances.put(faction, before);
                appliedGrants.remove(grantId);
                plugin.getLogger().severe("Treasury grant commit failed: " + rootMessage(failure));
                return false;
            }
        }
    }

    public void deposit(final FactionType faction, final double amount) {
        if (faction == null || !Double.isFinite(amount) || amount <= 0.0D) return;
        synchronized (stateLock) {
            final double next = PlayerProfileTaxStore.checkedAmountAdd(
                    balances.getOrDefault(faction, 0.0D), amount);
            if (!Double.isFinite(next)) return;
            balances.put(faction, next);
        }
        requestSave();
    }

    public boolean withdraw(final FactionType faction, final double amount) {
        if (faction == null || !Double.isFinite(amount) || amount <= 0.0D) return false;
        synchronized (stateLock) {
            final double current = balances.getOrDefault(faction, 0.0D);
            if (current < amount) return false;
            balances.put(faction, current - amount);
        }
        requestSave();
        return true;
    }

    public double getArrears(final UUID playerId) {
        if (playerId == null) return 0.0D;
        return arrearsProjection.getOrDefault(playerId, Map.of()).values().stream()
                .mapToDouble(Double::doubleValue).sum();
    }

    public double getArrears(final UUID playerId, final FactionType originFaction) {
        if (playerId == null || originFaction == null) return 0.0D;
        return arrearsProjection.getOrDefault(playerId, Map.of())
                .getOrDefault(originFaction, 0.0D);
    }

    /** Membership taxes are retired permanently; legacy config cannot restart the producer. */
    public void collectTaxes() { }

    private void processOutbox(final UUID playerId,
                               final PlayerProfileTaxStore.Outbox outbox) {
        if (outbox.paidMilli() > 0L && !depositOnce("tax-credit:" + outbox.operationId(),
                outbox.origin(), outbox.paid())) return;
        // Already deducted money may be settled, but no tax-related crime is ever generated.
        taxStore.settle(playerId, outbox.operationId()).whenComplete((settled, failure) -> {
            if (failure != null) plugin.getLogger().severe("Tax financial settlement pending for "
                    + playerId + '/' + outbox.operationId() + ": " + rootMessage(failure));
        });
    }

    private double readNonNegative(final YamlConfiguration yaml, final String path,
                                   final double fallback) {
        final Object raw = yaml.get(path);
        if (raw == null) return fallback;
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < 0.0D) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Invalid shared treasury value at " + path);
            return fallback;
        }
        return number.doubleValue();
    }

    private double nonNegativeConfig(final String path, final double fallback) {
        final Object raw = configManager.snapshot().configuration() == null ? fallback
                : configManager.snapshot().configuration().get(path);
        if (raw instanceof Number number && Double.isFinite(number.doubleValue())
                && number.doubleValue() >= 0.0D) return number.doubleValue();
        return fallback;
    }

    private int nonNegativeIntConfig(final String path, final int fallback) {
        final Object raw = configManager.snapshot().configuration() == null ? fallback
                : configManager.snapshot().configuration().get(path);
        if (raw instanceof Number number && number.doubleValue() >= 0.0D
                && number.doubleValue() <= Integer.MAX_VALUE
                && number.doubleValue() == Math.rint(number.doubleValue())) return number.intValue();
        return fallback;
    }

    private static String rootMessage(final Throwable failure) {
        if (failure == null) return "unknown failure";
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
