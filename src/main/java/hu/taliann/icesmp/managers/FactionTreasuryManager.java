package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Faction treasury (hadi kassza): a per-faction currency pool persisted to treasury.yml.
 */
public final class FactionTreasuryManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final File storageFile;
    /** Minden store-mutáció, snapshot, tartós írás és rollback közös monitora. */
    private final Object stateLock = new Object();
    private final Map<FactionType, Double> balances = new ConcurrentHashMap<>();
    /** Már alkalmazott, azonosítóhoz kötött jóváírások. */
    private final Map<String, Long> appliedGrants = new ConcurrentHashMap<>();
    // Grant-nyugtát nem dobunk ki pusztán darabszám alapján: a másik cél-store tartós
    // hibája miatt hosszan nyitva maradó outbox replaye különben duplán jóváírhatna.
    private final Map<FactionType, Double> taxRates = new ConcurrentHashMap<>();
    /** Unpaid citizen tax per player. */
    private final Map<UUID, Double> taxArrears = new ConcurrentHashMap<>();
    /** Tax-evasion strikes. */
    private final Map<UUID, Integer> evasionStrikes = new ConcurrentHashMap<>();
    private final SinManager sinManager;
    private final java.util.concurrent.atomic.AtomicBoolean saveScheduled =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public FactionTreasuryManager(final JavaPlugin plugin, final ConfigManager configManager,
                                  final CurrencyManager currencyManager,
                                  final FactionManager factionManager,
                                  final SinManager sinManager,
                                  final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "treasury.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        synchronized (stateLock) {
            balances.clear();
            appliedGrants.clear();

            if (!storageFile.exists()) {
                return;
            }

            try {
                final YamlConfiguration yaml = YamlStore.loadTracked(
                        storageFile, plugin.getLogger());
                final ConfigurationSection section = yaml.getConfigurationSection("treasury");
                if (section == null) {
                    return;
                }

                for (final String factionKey : section.getKeys(false)) {
                    final FactionType faction = FactionType.fromInput(factionKey);
                    if (faction == null) {
                        plugin.getLogger().warning(
                                "Unknown faction in treasury.yml: " + factionKey);
                        continue;
                    }
                    balances.put(faction,
                            Math.max(0.0D, section.getDouble(factionKey, 0.0D)));
                }

                final ConfigurationSection rateSection = yaml.getConfigurationSection("tax-rates");
                if (rateSection != null) {
                    for (final String factionKey : rateSection.getKeys(false)) {
                        final FactionType faction = FactionType.fromInput(factionKey);
                        if (faction != null) {
                            taxRates.put(faction, Math.max(0.0D,
                                    rateSection.getDouble(factionKey, 0.0D)));
                        }
                    }
                }

                taxArrears.clear();
                final ConfigurationSection arrearsSection =
                        yaml.getConfigurationSection("tax-arrears");
                if (arrearsSection != null) {
                    for (final String key : arrearsSection.getKeys(false)) {
                        try {
                            final double owed = arrearsSection.getDouble(key, 0.0D);
                            if (owed > 0.0D) {
                                taxArrears.put(UUID.fromString(key), owed);
                            }
                        } catch (final IllegalArgumentException ignored) {
                            plugin.getLogger().warning(
                                    "Invalid UUID in treasury.yml tax-arrears: " + key);
                        }
                    }
                }

                evasionStrikes.clear();
                final ConfigurationSection strikesSection =
                        yaml.getConfigurationSection("tax-evasion-strikes");
                if (strikesSection != null) {
                    for (final String key : strikesSection.getKeys(false)) {
                        try {
                            final int strikes = strikesSection.getInt(key, 0);
                            if (strikes > 0) {
                                evasionStrikes.put(UUID.fromString(key), strikes);
                            }
                        } catch (final IllegalArgumentException ignored) {
                            plugin.getLogger().warning(
                                    "Invalid UUID in treasury.yml tax-evasion-strikes: " + key);
                        }
                    }
                }

                final ConfigurationSection grants =
                        yaml.getConfigurationSection("applied-grants");
                if (grants != null) {
                    for (final String key : grants.getKeys(false)) {
                        appliedGrants.put(key,
                                grants.getLong(key, System.currentTimeMillis()));
                    }
                }
                plugin.getLogger().info("Loaded faction treasury balances.");
            } catch (final Exception exception) {
                plugin.getLogger().severe(
                        "Failed to load faction treasury: " + exception.getMessage());
            }
        }
    }

    /** Debounced asynchronous save request. */
    public void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public void save() {
        synchronized (stateLock) {
            writeStateLocked();
        }
    }

    /** A hívónak tartania kell a stateLock monitort. */
    private boolean writeStateLocked() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<FactionType, Double> entry : balances.entrySet()) {
            yaml.set("treasury." + entry.getKey().name(), entry.getValue());
        }
        for (final Map.Entry<FactionType, Double> entry : taxRates.entrySet()) {
            yaml.set("tax-rates." + entry.getKey().name(), entry.getValue());
        }
        for (final Map.Entry<UUID, Double> entry : taxArrears.entrySet()) {
            if (entry.getValue() > 0.0D) {
                yaml.set("tax-arrears." + entry.getKey(), entry.getValue());
            }
        }
        for (final Map.Entry<UUID, Integer> entry : evasionStrikes.entrySet()) {
            if (entry.getValue() > 0) {
                yaml.set("tax-evasion-strikes." + entry.getKey(), entry.getValue());
            }
        }
        for (final Map.Entry<String, Long> entry : appliedGrants.entrySet()) {
            yaml.set("applied-grants." + entry.getKey(), entry.getValue());
        }

        try {
            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().severe(
                    "Failed to save faction treasury: " + exception.getMessage());
            return false;
        }
    }

    public double getBalance(final FactionType faction) {
        synchronized (stateLock) {
            return faction == null ? 0.0D : balances.getOrDefault(faction, 0.0D);
        }
    }

    /** Gets a faction's effective citizen-tax rate. */
    public double getTaxRate(final FactionType faction) {
        synchronized (stateLock) {
            final double configDefault = Math.max(0.0D,
                    configManager.getDouble("factions.tax.rate-percent", 2.0D));
            return faction == null
                    ? configDefault : taxRates.getOrDefault(faction, configDefault);
        }
    }

    /** Sets a faction's citizen-tax rate. */
    public double setTaxRate(final FactionType faction, final double ratePercent) {
        final double max = Math.max(0.0D,
                configManager.getDouble("factions.tax.max-rate-percent", 10.0D));
        final double safeRate = Double.isFinite(ratePercent) ? ratePercent : 0.0D;
        final double applied = Math.max(0.0D, Math.min(max, safeRate));
        if (faction != null) {
            synchronized (stateLock) {
                taxRates.put(faction, applied);
            }
            requestSave();
        }
        return applied;
    }

    /**
     * Idempotens jóváírás. A candidate balance, grant-nyugta, snapshot, írás és sikertelen
     * írás utáni rollback ugyanazon store-lock alatt fut, ezért egy közben érkező ordinary
     * deposit/withdraw módosítást a rollback nem írhat felül.
     */
    public boolean depositOnce(final String grantId, final FactionType faction,
                               final double amount) {
        synchronized (stateLock) {
            if (grantId == null || grantId.isBlank()) {
                return false;
            }
            if (appliedGrants.containsKey(grantId)) {
                return true;
            }
            if (faction == null || !Double.isFinite(amount) || amount <= 0.0D) {
                return false;
            }

            final Double previous = balances.get(faction);
            balances.merge(faction, amount, Double::sum);
            appliedGrants.put(grantId, System.currentTimeMillis());
            if (!YamlStore.isLoadFailed(storageFile) && writeStateLocked()) {
                return true;
            }

            appliedGrants.remove(grantId);
            if (previous == null) {
                balances.remove(faction);
            } else {
                balances.put(faction, previous);
            }
            return false;
        }
    }

    public void deposit(final FactionType faction, final double amount) {
        if (faction == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return;
        }
        synchronized (stateLock) {
            balances.merge(faction, amount, Double::sum);
        }
        requestSave();
    }

    /** Withdraws from a faction treasury if it has sufficient funds. */
    public boolean withdraw(final FactionType faction, final double amount) {
        if (faction == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return false;
        }

        synchronized (stateLock) {
            final double balance = balances.getOrDefault(faction, 0.0D);
            if (balance < amount) {
                return false;
            }
            balances.put(faction, balance - amount);
        }
        requestSave();
        return true;
    }

    /** A player's outstanding tax arrears. */
    public double getArrears(final UUID playerId) {
        synchronized (stateLock) {
            return playerId == null ? 0.0D : taxArrears.getOrDefault(playerId, 0.0D);
        }
    }

    /** Collects the periodic citizen tax. */
    public void collectTaxes() {
        if (!configManager.getBoolean("factions.tax.enabled", true)) {
            return;
        }

        final List<String> exempt = configManager.getStringList("factions.tax.exempt").stream()
                .map(name -> name.toUpperCase(Locale.ROOT))
                .toList();
        final double minimum = Math.max(0.0D,
                configManager.getDouble("factions.tax.minimum-amount", 2.0D));
        final double maxArrears = Math.max(0.0D,
                configManager.getDouble("factions.tax.max-arrears", 50.0D));

        final Map<FactionType, Double> collected = new EnumMap<>(FactionType.class);
        boolean arrearsChanged = false;
        for (final Map.Entry<UUID, FactionType> entry
                : factionManager.getFactionAssignments().entrySet()) {
            final FactionType faction = entry.getValue();
            if (faction == null || exempt.contains(faction.name())) {
                continue;
            }

            final double ratePercent = getTaxRate(faction);
            final UUID citizenId = entry.getKey();
            final CurrencyType currency = CurrencyType.fromFactionType(faction);
            final double balance = currencyManager.getBalance(citizenId, currency);

            final double percentDue = ratePercent <= 0.0D
                    ? 0.0D : Math.floor(balance * ratePercent) / 100.0D;
            final double owedBefore;
            synchronized (stateLock) {
                owedBefore = taxArrears.getOrDefault(citizenId, 0.0D);
            }
            final double due = Math.max(percentDue, minimum) + owedBefore;
            if (due <= 0.0D) {
                continue;
            }

            double payable = 0.0D;
            double paid = 0.0D;
            for (int attempt = 0; attempt < 2 && paid <= 0.0D; attempt++) {
                final double fresh = currencyManager.getBalance(citizenId, currency);
                payable = Math.floor(Math.min(due, fresh) * 100.0D) / 100.0D;
                if (payable <= 0.0D) {
                    break;
                }
                paid = currencyManager.deductFromBalance(citizenId, currency, payable)
                        ? payable : 0.0D;
            }
            final double owedAfter = Math.min(maxArrears,
                    Math.round((due - paid) * 100.0D) / 100.0D);
            synchronized (stateLock) {
                if (owedAfter > 0.0D) {
                    taxArrears.put(citizenId, owedAfter);
                } else {
                    taxArrears.remove(citizenId);
                }
            }
            arrearsChanged |= owedAfter != owedBefore;

            if (paid > 0.0D) {
                collected.merge(faction, paid, Double::sum);
            }

            final int evasionThreshold = Math.max(0,
                    configManager.getInt("factions.tax.evasion-strikes", 3));
            if (evasionThreshold > 0 && maxArrears > 0.0D
                    && paid <= 0.0D && owedAfter >= maxArrears) {
                final int strikes;
                synchronized (stateLock) {
                    strikes = evasionStrikes.merge(citizenId, 1,
                            (current, one) -> Math.min(evasionThreshold, current + one));
                }
                arrearsChanged = true;
                if (strikes >= evasionThreshold) {
                    final Player evader = Bukkit.getPlayer(citizenId);
                    if (evader != null) {
                        synchronized (stateLock) {
                            evasionStrikes.remove(citizenId);
                        }
                        evader.getScheduler().run(plugin, task -> {
                            sinManager.addSin(evader, 1);
                            evader.sendMessage(messageManager.getMessage(
                                    "faction-tax-evasion",
                                    "&4⚖ Adócsalás! &cA Számvevők feljelentettek — bűnt róttak fel neked. Törleszd a hátralékod, mielőtt a bűneid súlya a Kitaszítottak közé taszít!"));
                        }, null);
                    }
                }
            } else if (owedAfter < maxArrears) {
                synchronized (stateLock) {
                    if (evasionStrikes.remove(citizenId) != null) {
                        arrearsChanged = true;
                    }
                }
            }

            final Player citizen = Bukkit.getPlayer(citizenId);
            if (citizen != null && (paid > 0.0D || owedAfter > owedBefore)) {
                final net.kyori.adventure.text.Component notice = owedAfter > 0.0D
                        ? messageManager.getMessage(
                                "faction-tax-arrears",
                                "&6Állampolgári adó: &f{amount} {currency}&6 levonva, hátralékod: &c{arrears} {currency}&7 — a Számvevők a következő beszedéskor is behajtják.",
                                Map.of("amount", currencyManager.formatBalance(paid),
                                        "arrears", currencyManager.formatBalance(owedAfter),
                                        "currency", currency.getDisplayName()))
                        : messageManager.getMessage(
                                "faction-tax-notice",
                                "&6Állampolgári adó levonva: &f{amount} {currency} &7(a frakciókasszába került).",
                                Map.of("amount", currencyManager.formatBalance(paid),
                                        "currency", currency.getDisplayName()));
                citizen.getScheduler().run(plugin,
                        task -> citizen.sendMessage(notice), null);
            }
        }

        final java.util.Set<UUID> known = factionManager.getFactionAssignments().keySet();
        synchronized (stateLock) {
            arrearsChanged |= taxArrears.keySet().removeIf(id -> !known.contains(id));
            arrearsChanged |= evasionStrikes.keySet().removeIf(id -> !known.contains(id));
        }

        if (collected.isEmpty() && !arrearsChanged) {
            return;
        }

        synchronized (stateLock) {
            for (final Map.Entry<FactionType, Double> entry : collected.entrySet()) {
                balances.merge(entry.getKey(), entry.getValue(), Double::sum);
                plugin.getLogger().info("Faction tax collected: "
                        + currencyManager.formatBalance(entry.getValue())
                        + " -> " + entry.getKey().name() + " treasury");
            }
            writeStateLocked();
        }
    }
}
