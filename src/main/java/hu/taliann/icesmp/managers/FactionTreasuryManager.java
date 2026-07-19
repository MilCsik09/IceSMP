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
 * Faction treasury (hadi kassza): a per-faction currency pool persisted to
 * treasury.yml. It is filled by player donations and the periodic citizen tax
 * — the tax doubles as the economy's money sink, feeding the dynamic exchange
 * rate model by draining circulating supply.
 */
public final class FactionTreasuryManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final Map<FactionType, Double> balances = new ConcurrentHashMap<>();
    private final Map<FactionType, Double> taxRates = new ConcurrentHashMap<>();
    /**
     * Unpaid citizen tax per player (hátralék): when a wallet can't cover the tax due
     * (e.g. a zero balance kept empty on purpose), the shortfall is recorded here and
     * settled automatically at the next collections, capped so it can't grow unboundedly.
     */
    private final Map<UUID, Double> taxArrears = new ConcurrentHashMap<>();

    public FactionTreasuryManager(final JavaPlugin plugin, final ConfigManager configManager,
                                  final CurrencyManager currencyManager, final FactionManager factionManager,
                                  final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "treasury.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        balances.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
            final ConfigurationSection section = yaml.getConfigurationSection("treasury");
            if (section == null) {
                return;
            }

            for (final String factionKey : section.getKeys(false)) {
                final FactionType faction = FactionType.fromInput(factionKey);
                if (faction == null) {
                    plugin.getLogger().warning("Unknown faction in treasury.yml: " + factionKey);
                    continue;
                }

                balances.put(faction, Math.max(0.0D, section.getDouble(factionKey, 0.0D)));
            }

            final ConfigurationSection rateSection = yaml.getConfigurationSection("tax-rates");
            if (rateSection != null) {
                for (final String factionKey : rateSection.getKeys(false)) {
                    final FactionType faction = FactionType.fromInput(factionKey);
                    if (faction != null) {
                        taxRates.put(faction, Math.max(0.0D, rateSection.getDouble(factionKey, 0.0D)));
                    }
                }
            }

            taxArrears.clear();
            final ConfigurationSection arrearsSection = yaml.getConfigurationSection("tax-arrears");
            if (arrearsSection != null) {
                for (final String key : arrearsSection.getKeys(false)) {
                    try {
                        final double owed = arrearsSection.getDouble(key, 0.0D);
                        if (owed > 0.0D) {
                            taxArrears.put(UUID.fromString(key), owed);
                        }
                    } catch (final IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Invalid UUID in treasury.yml tax-arrears: " + key);
                    }
                }
            }

            plugin.getLogger().info("Loaded faction treasury balances.");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load faction treasury: " + exception.getMessage());
        }
    }

    public synchronized void save() {
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

        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save faction treasury: " + exception.getMessage());
        }
    }

    public double getBalance(final FactionType faction) {
        return faction == null ? 0.0D : balances.getOrDefault(faction, 0.0D);
    }

    /**
     * Gets a faction's effective citizen-tax rate: the king's override if set,
     * otherwise the server default from config.
     *
     * @param faction the faction
     * @return the tax rate in percent
     */
    public double getTaxRate(final FactionType faction) {
        final double configDefault = Math.max(0.0D, configManager.getDouble("factions.tax.rate-percent", 2.0D));
        return faction == null ? configDefault : taxRates.getOrDefault(faction, configDefault);
    }

    /**
     * Sets a faction's citizen-tax rate (the king's prerogative), clamped to a
     * configured maximum to prevent abuse.
     *
     * @param faction the faction
     * @param ratePercent the new rate in percent
     * @return the applied (possibly clamped) rate
     */
    public double setTaxRate(final FactionType faction, final double ratePercent) {
        final double max = Math.max(0.0D, configManager.getDouble("factions.tax.max-rate-percent", 10.0D));
        final double safeRate = Double.isFinite(ratePercent) ? ratePercent : 0.0D;
        final double applied = Math.max(0.0D, Math.min(max, safeRate));
        if (faction != null) {
            taxRates.put(faction, applied);
            save();
        }
        return applied;
    }

    public void deposit(final FactionType faction, final double amount) {
        if (faction == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return;
        }

        balances.merge(faction, amount, Double::sum);
        save();
    }

    /**
     * Withdraws from a faction treasury if it has sufficient funds.
     *
     * @param faction the faction
     * @param amount the amount to withdraw
     * @return true if the withdrawal succeeded
     */
    public boolean withdraw(final FactionType faction, final double amount) {
        if (faction == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return false;
        }

        // Atomic check-and-deduct so concurrent withdrawals can't overdraw the treasury.
        final boolean[] withdrawn = {false};
        balances.compute(faction, (key, current) -> {
            final double balance = current == null ? 0.0D : current;
            if (balance >= amount) {
                withdrawn[0] = true;
                return balance - amount;
            }
            return balance;
        });
        if (!withdrawn[0]) {
            return false;
        }
        save();
        return true;
    }

    /**
     * A player's outstanding tax arrears (hátralék) — 0 when fully paid up.
     *
     * @param playerId the player
     * @return the owed amount
     */
    public double getArrears(final UUID playerId) {
        return playerId == null ? 0.0D : taxArrears.getOrDefault(playerId, 0.0D);
    }

    /**
     * Collects the periodic citizen tax: every member of a non-exempt faction owes
     * rate-percent of their own-faction bank balance, but at least the configured
     * minimum head tax (fejadó) — so an emptied wallet is no longer a tax dodge.
     * Whatever the wallet can't cover accrues as arrears (capped) and is settled
     * automatically at later collections. Offline citizens are taxed too (balances
     * live in memory for every stored wallet); online citizens get a chat notice
     * on their own entity scheduler.
     * Scheduled by IceSMPCore on the global region scheduler (Folia-safe).
     */
    public void collectTaxes() {
        if (!configManager.getBoolean("factions.tax.enabled", true)) {
            return;
        }

        final List<String> exempt = configManager.getStringList("factions.tax.exempt").stream()
                .map(name -> name.toUpperCase(Locale.ROOT))
                .toList();
        final double minimum = Math.max(0.0D, configManager.getDouble("factions.tax.minimum-amount", 2.0D));
        final double maxArrears = Math.max(0.0D, configManager.getDouble("factions.tax.max-arrears", 50.0D));

        final Map<FactionType, Double> collected = new EnumMap<>(FactionType.class);
        boolean arrearsChanged = false;
        for (final Map.Entry<UUID, FactionType> entry : factionManager.getFactionAssignments().entrySet()) {
            final FactionType faction = entry.getValue();
            if (faction == null || exempt.contains(faction.name())) {
                continue;
            }

            final double ratePercent = getTaxRate(faction);
            final UUID citizenId = entry.getKey();
            final CurrencyType currency = CurrencyType.fromFactionType(faction);
            final double balance = currencyManager.getBalance(citizenId, currency);

            // Due = max(percent of balance, minimum head tax) + any unpaid arrears.
            final double percentDue = ratePercent <= 0.0D ? 0.0D : Math.floor(balance * ratePercent) / 100.0D;
            final double owedBefore = taxArrears.getOrDefault(citizenId, 0.0D);
            final double due = Math.max(percentDue, minimum) + owedBefore;
            if (due <= 0.0D) {
                continue;
            }

            // Pay what the wallet covers; the shortfall becomes (capped) arrears.
            final double payable = Math.floor(Math.min(due, balance) * 100.0D) / 100.0D;
            final double paid = payable > 0.0D && currencyManager.deductFromBalance(citizenId, currency, payable)
                    ? payable : 0.0D;
            final double owedAfter = Math.min(maxArrears, Math.round((due - paid) * 100.0D) / 100.0D);
            if (owedAfter > 0.0D) {
                taxArrears.put(citizenId, owedAfter);
            } else {
                taxArrears.remove(citizenId);
            }
            arrearsChanged |= owedAfter != owedBefore;

            if (paid > 0.0D) {
                collected.merge(faction, paid, Double::sum);
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
                citizen.getScheduler().run(plugin, task -> citizen.sendMessage(notice), null);
            }
        }

        if (collected.isEmpty() && !arrearsChanged) {
            return;
        }

        for (final Map.Entry<FactionType, Double> entry : collected.entrySet()) {
            balances.merge(entry.getKey(), entry.getValue(), Double::sum);
            plugin.getLogger().info("Faction tax collected: " + currencyManager.formatBalance(entry.getValue())
                    + " -> " + entry.getKey().name() + " treasury");
        }

        save();
    }
}
