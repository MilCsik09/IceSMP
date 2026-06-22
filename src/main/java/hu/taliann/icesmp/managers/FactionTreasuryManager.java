package hu.taliann.icesmp.managers;

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
public final class FactionTreasuryManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final Map<FactionType, Double> balances = new ConcurrentHashMap<>();
    private final Map<FactionType, Double> taxRates = new ConcurrentHashMap<>();

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
     * Collects the periodic citizen tax: every member of a non-exempt faction
     * pays rate-percent of their own-faction bank balance into the faction
     * treasury. Offline citizens are taxed too (balances live in memory for
     * every stored wallet); online citizens get a chat notice.
     * Scheduled by IceSMPCore on the global region scheduler (Folia-safe).
     */
    public void collectTaxes() {
        if (!configManager.getBoolean("factions.tax.enabled", true)) {
            return;
        }

        final List<String> exempt = configManager.getStringList("factions.tax.exempt").stream()
                .map(name -> name.toUpperCase(Locale.ROOT))
                .toList();

        final Map<FactionType, Double> collected = new EnumMap<>(FactionType.class);
        for (final Map.Entry<UUID, FactionType> entry : factionManager.getFactionAssignments().entrySet()) {
            final FactionType faction = entry.getValue();
            if (faction == null || exempt.contains(faction.name())) {
                continue;
            }

            final double ratePercent = getTaxRate(faction);
            if (ratePercent <= 0.0D) {
                continue;
            }

            final CurrencyType currency = CurrencyType.fromFactionType(faction);
            final double balance = currencyManager.getBalance(entry.getKey(), currency);
            final double tax = Math.floor(balance * ratePercent) / 100.0D;
            if (tax <= 0.0D || !currencyManager.deductFromBalance(entry.getKey(), currency, tax)) {
                continue;
            }

            collected.merge(faction, tax, Double::sum);

            final Player citizen = Bukkit.getPlayer(entry.getKey());
            if (citizen != null) {
                citizen.sendMessage(messageManager.getMessage(
                        "faction-tax-notice",
                        "&6Állampolgári adó levonva: &f{amount} {currency} &7(a frakciókasszába került).",
                        Map.of("amount", currencyManager.formatBalance(tax), "currency", currency.getDisplayName())
                ));
            }
        }

        if (collected.isEmpty()) {
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
