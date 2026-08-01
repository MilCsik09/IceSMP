package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.factions.FactionTaxDebtLedger;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    /** Unpaid tax and evasion strikes, retained per assessing faction/currency. */
    private final FactionTaxDebtLedger taxDebts = new FactionTaxDebtLedger();
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
            taxRates.clear();
            appliedGrants.clear();
            taxDebts.clear();

            if (!storageFile.exists()) {
                return;
            }

            try {
                final YamlConfiguration yaml = YamlStore.loadTracked(
                        storageFile, plugin.getLogger());
                final ConfigurationSection section = yaml.getConfigurationSection("treasury");
                if (section != null) {
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

                final boolean migratedLegacyTaxDebt = loadTaxDebtState(yaml);

                final ConfigurationSection grants =
                        yaml.getConfigurationSection("applied-grants");
                if (grants != null) {
                    for (final String key : grants.getKeys(false)) {
                        appliedGrants.put(key,
                                grants.getLong(key, System.currentTimeMillis()));
                    }
                }
                if (migratedLegacyTaxDebt) {
                    if (YamlStore.isLoadFailed(storageFile) || !writeStateLocked()) {
                        plugin.getLogger().severe(
                                "Legacy faction tax debt migration could not be persisted; "
                                        + "the in-memory state remains available for this session.");
                    } else {
                        plugin.getLogger().info(
                                "Migrated legacy scalar faction tax debt to origin-faction buckets.");
                    }
                }
                plugin.getLogger().info("Loaded faction treasury balances.");
            } catch (final Exception exception) {
                plugin.getLogger().severe(
                        "Failed to load faction treasury: " + exception.getMessage());
            }
        }
    }

    /**
     * Loads the origin-aware schema, or migrates the pre-rework scalar maps once. A mixed file
     * prefers the new schema so a stale legacy section cannot duplicate an already migrated debt.
     */
    private boolean loadTaxDebtState(final YamlConfiguration yaml) {
        final ConfigurationSection debtRoot = yaml.getConfigurationSection("tax-debts");
        final ConfigurationSection unresolvedRoot =
                yaml.getConfigurationSection("legacy-tax-debts-unresolved");
        if (debtRoot != null || unresolvedRoot != null) {
            loadOriginTaxDebts(debtRoot);
            loadUnresolvedLegacyTaxDebts(unresolvedRoot);
            if (yaml.isSet("tax-arrears") || yaml.isSet("tax-evasion-strikes")) {
                plugin.getLogger().warning(
                        "treasury.yml contains both new and legacy tax-debt sections; "
                                + "the legacy scalar sections were ignored to prevent duplication.");
            }
            return false;
        }

        final ConfigurationSection arrears = yaml.getConfigurationSection("tax-arrears");
        final ConfigurationSection strikes =
                yaml.getConfigurationSection("tax-evasion-strikes");
        if (arrears == null && strikes == null) {
            return false;
        }

        final Set<String> playerKeys = new HashSet<>();
        if (arrears != null) {
            playerKeys.addAll(arrears.getKeys(false));
        }
        if (strikes != null) {
            playerKeys.addAll(strikes.getKeys(false));
        }
        for (final String playerKey : playerKeys) {
            final UUID playerId = parseDebtPlayerId(playerKey, "legacy tax debt");
            if (playerId == null) {
                continue;
            }
            final double amount = arrears == null ? 0.0D : readDebtAmount(
                    arrears, playerKey, "legacy tax-arrears." + playerKey);
            final int evasionStrikes = strikes == null ? 0 : readDebtStrikes(
                    strikes, playerKey, "legacy tax-evasion-strikes." + playerKey);
            if (amount == 0.0D && evasionStrikes == 0) {
                continue;
            }
            final Optional<FactionType> activeOrigin =
                    factionManager.getChosenFaction(playerId);
            final FactionType inferredOrigin = FactionTaxDebtLedger.resolveLegacyOrigin(
                    activeOrigin, factionManager.getLastChosenFaction(playerId)).orElse(null);
            if (inferredOrigin == null) {
                taxDebts.putUnresolvedLegacy(playerId, amount, evasionStrikes);
                plugin.getLogger().warning(
                        "Legacy tax debt for " + playerId + " has no explicit faction. "
                                + "It is preserved without an origin and will bind only after "
                                + "the player's next explicit faction membership; the old scalar "
                                + "schema cannot reconstruct its original currency.");
            } else {
                taxDebts.put(playerId, inferredOrigin, amount, evasionStrikes);
                if (activeOrigin.isEmpty()) {
                    plugin.getLogger().info(
                            "Recovered legacy tax-debt origin for " + playerId
                                    + " from durable membership history: "
                                    + inferredOrigin.name());
                }
            }
        }
        return true;
    }

    private void loadOriginTaxDebts(final ConfigurationSection root) {
        if (root == null) {
            return;
        }
        for (final String playerKey : root.getKeys(false)) {
            final UUID playerId = parseDebtPlayerId(playerKey, "tax-debts");
            final ConfigurationSection playerSection = root.getConfigurationSection(playerKey);
            if (playerId == null || playerSection == null) {
                if (playerSection == null) {
                    plugin.getLogger().warning(
                            "Invalid non-section tax-debts record for " + playerKey);
                }
                continue;
            }
            for (final String factionKey : playerSection.getKeys(false)) {
                final FactionType origin = FactionType.fromInput(factionKey);
                final ConfigurationSection debtSection =
                        playerSection.getConfigurationSection(factionKey);
                if (origin == null || debtSection == null) {
                    plugin.getLogger().warning(
                            "Invalid tax-debts origin record: " + playerKey + "." + factionKey);
                    continue;
                }
                final String path = "tax-debts." + playerKey + "." + factionKey;
                final double amount = readDebtAmount(
                        debtSection, "amount", path + ".amount");
                final int evasionStrikes = readDebtStrikes(
                        debtSection, "evasion-strikes",
                        path + ".evasion-strikes");
                if (amount > 0.0D || evasionStrikes > 0) {
                    taxDebts.put(playerId, origin, amount, evasionStrikes);
                }
            }
        }
    }

    private void loadUnresolvedLegacyTaxDebts(final ConfigurationSection root) {
        if (root == null) {
            return;
        }
        for (final String playerKey : root.getKeys(false)) {
            final UUID playerId = parseDebtPlayerId(playerKey, "legacy-tax-debts-unresolved");
            final ConfigurationSection debtSection = root.getConfigurationSection(playerKey);
            if (playerId == null || debtSection == null) {
                if (debtSection == null) {
                    plugin.getLogger().warning(
                            "Invalid unresolved legacy tax-debt record for " + playerKey);
                }
                continue;
            }
            final String path = "legacy-tax-debts-unresolved." + playerKey;
            final double amount = readDebtAmount(
                    debtSection, "amount", path + ".amount");
            final int evasionStrikes = readDebtStrikes(
                    debtSection, "evasion-strikes",
                    path + ".evasion-strikes");
            if (amount > 0.0D || evasionStrikes > 0) {
                taxDebts.putUnresolvedLegacy(playerId, amount, evasionStrikes);
            }
        }
    }

    private UUID parseDebtPlayerId(final String raw, final String section) {
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException ignored) {
            plugin.getLogger().warning(
                    "Invalid UUID in treasury.yml " + section + ": " + raw);
            return null;
        }
    }

    private double readDebtAmount(final ConfigurationSection section, final String key,
                                  final String path) {
        final Object raw = section.get(key);
        if (raw == null) {
            return 0.0D;
        }
        if (!(raw instanceof Number number)) {
            plugin.getLogger().warning(
                    "Invalid non-numeric tax debt at " + path + "; amount disabled.");
            return 0.0D;
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0D) {
            plugin.getLogger().warning(
                    "Invalid non-finite/negative tax debt at " + path + "; amount disabled.");
            return 0.0D;
        }
        return value;
    }

    private int readDebtStrikes(final ConfigurationSection section, final String key,
                                final String path) {
        final Object raw = section.get(key);
        if (raw == null) {
            return 0;
        }
        if (!(raw instanceof Number number)) {
            plugin.getLogger().warning(
                    "Invalid non-numeric tax-evasion strike count at " + path
                            + "; strikes disabled.");
            return 0;
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0D || value > Integer.MAX_VALUE
                || value != Math.rint(value)) {
            plugin.getLogger().warning(
                    "Invalid non-integral/out-of-range tax-evasion strike count at " + path
                            + "; strikes disabled.");
            return 0;
        }
        return (int) value;
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
            if (!writeStateLocked()) {
                // A koordinátor hibagyűjtése csak dobásból lát.
                throw new IllegalStateException("faction-treasury mentése sikertelen — részletek a logban");
            }
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
        for (final FactionTaxDebtLedger.Debt debt : taxDebts.snapshot()) {
            final String base = "tax-debts." + debt.playerId() + "."
                    + debt.faction().name() + ".";
            if (debt.amount() > 0.0D) {
                yaml.set(base + "amount", debt.amount());
            }
            if (debt.evasionStrikes() > 0) {
                yaml.set(base + "evasion-strikes", debt.evasionStrikes());
            }
        }
        for (final FactionTaxDebtLedger.UnresolvedLegacyDebt debt
                : taxDebts.unresolvedLegacySnapshot()) {
            final String base = "legacy-tax-debts-unresolved." + debt.playerId() + ".";
            if (debt.amount() > 0.0D) {
                yaml.set(base + "amount", debt.amount());
            }
            if (debt.evasionStrikes() > 0) {
                yaml.set(base + "evasion-strikes", debt.evasionStrikes());
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
        } catch (final hu.taliann.icesmp.storage.CriticalPersistenceWriteError fatal) {
            // A kritikus write-circuit már beállt (minden további írás tiltva) — itt false-t
            // adunk, hogy a hívó rollback-ága lefusson; a koordinátort a void save() wrapper
            // dobása értesíti. A fatal elnyelése nélkül a rollback kimaradna (Error != IOException).
            plugin.getLogger().severe(fatal.getMessage() == null ? fatal.toString() : fatal.getMessage());
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

    /** A player's total outstanding tax arrears across every assessing faction. */
    public double getArrears(final UUID playerId) {
        synchronized (stateLock) {
            return playerId == null ? 0.0D : taxDebts.getTotalArrears(playerId);
        }
    }

    /** Outstanding arrears that must be paid in the given faction's currency. */
    public double getArrears(final UUID playerId, final FactionType originFaction) {
        synchronized (stateLock) {
            return playerId == null || originFaction == null
                    ? 0.0D : taxDebts.getArrears(playerId, originFaction);
        }
    }

    /**
     * Collects the periodic citizen tax. A current explicit citizen receives one new assessment
     * in their current faction; every retained debt bucket is settled independently in its
     * original currency and credited back to its original treasury. Unassigned guests are absent
     * from the assignment snapshot, so they receive neither a new assessment nor debt collection.
     */
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
        final int evasionThreshold = Math.max(0,
                configManager.getInt("factions.tax.evasion-strikes", 3));

        final Map<FactionType, Double> collected = new EnumMap<>(FactionType.class);
        final Set<UUID> evasionReportedThisRun = new HashSet<>();
        boolean arrearsChanged = false;
        for (final Map.Entry<UUID, FactionType> entry
                : factionManager.getFactionAssignments().entrySet()) {
            final FactionType currentFaction = entry.getValue();
            if (currentFaction == null) {
                continue;
            }

            final UUID citizenId = entry.getKey();
            final CurrencyType currentCurrency = CurrencyType.fromFactionType(currentFaction);
            final double newAssessment;
            if (exempt.contains(currentFaction.name())) {
                newAssessment = 0.0D;
            } else {
                final double ratePercent = getTaxRate(currentFaction);
                final double balance = currencyManager.getBalance(citizenId, currentCurrency);
                final double percentDue = ratePercent <= 0.0D
                        ? 0.0D : Math.floor(balance * ratePercent) / 100.0D;
                newAssessment = Math.max(percentDue, minimum);
            }

            final EnumSet<FactionType> origins = EnumSet.noneOf(FactionType.class);
            synchronized (stateLock) {
                if (taxDebts.bindUnresolvedLegacy(citizenId, currentFaction)) {
                    arrearsChanged = true;
                    plugin.getLogger().warning(
                            "Bound origin-less legacy tax debt for " + citizenId + " to "
                                    + currentFaction.name() + " after explicit membership. "
                                    + "This is the first origin recoverable after the legacy schema.");
                }
                for (final FactionTaxDebtLedger.Debt debt : taxDebts.debtsFor(citizenId)) {
                    origins.add(debt.faction());
                }
            }
            if (newAssessment > 0.0D) {
                origins.add(currentFaction);
            }

            for (final FactionType originFaction : origins) {
                final double owedBefore;
                final int strikesBefore;
                synchronized (stateLock) {
                    owedBefore = taxDebts.getArrears(citizenId, originFaction);
                    strikesBefore = taxDebts.getEvasionStrikes(citizenId, originFaction);
                }
                final double assessed = originFaction == currentFaction ? newAssessment : 0.0D;
                final double due = owedBefore + assessed;
                if (due <= 0.0D) {
                    synchronized (stateLock) {
                        arrearsChanged |= taxDebts.clearEvasionStrikes(
                                citizenId, originFaction);
                    }
                    continue;
                }

                final CurrencyType originCurrency =
                        CurrencyType.fromFactionType(originFaction);
                final double paid = collectDebt(citizenId, originCurrency, due);
                final double owedAfter = Math.min(maxArrears,
                        Math.max(0.0D, Math.round((due - paid) * 100.0D) / 100.0D));
                synchronized (stateLock) {
                    arrearsChanged |= taxDebts.setArrears(
                            citizenId, originFaction, owedAfter);
                }

                if (paid > 0.0D) {
                    collected.merge(originFaction, paid, Double::sum);
                }

                if (evasionThreshold > 0 && maxArrears > 0.0D
                        && paid <= 0.0D && owedAfter >= maxArrears) {
                    final int strikes;
                    synchronized (stateLock) {
                        strikes = taxDebts.incrementEvasionStrikes(
                                citizenId, originFaction, evasionThreshold);
                    }
                    arrearsChanged |= strikes != strikesBefore;
                    if (strikes >= evasionThreshold) {
                        final Player evader = Bukkit.getPlayer(citizenId);
                        if (evader != null && evasionReportedThisRun.add(citizenId)) {
                            synchronized (stateLock) {
                                arrearsChanged |= taxDebts.clearEvasionStrikes(
                                        citizenId, originFaction);
                            }
                            evader.getScheduler().run(plugin, task -> {
                                sinManager.addSin(evader, 1);
                                evader.sendMessage(messageManager.getMessage(
                                        "faction-tax-evasion",
                                        "&4⚖ Adócsalás! &cA Számvevők feljelentettek — bűnt róttak fel neked. Törleszd a hátralékod, mielőtt a bűneid súlya a Kitaszítottak közé taszít!"));
                            }, null);
                        }
                    }
                } else if (owedAfter <= 0.0D || owedAfter < maxArrears) {
                    synchronized (stateLock) {
                        arrearsChanged |= taxDebts.clearEvasionStrikes(
                                citizenId, originFaction);
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
                                            "currency", originCurrency.getDisplayName()))
                            : messageManager.getMessage(
                                    "faction-tax-notice",
                                    "&6Állampolgári adó levonva: &f{amount} {currency} &7(a frakciókasszába került).",
                                    Map.of("amount", currencyManager.formatBalance(paid),
                                            "currency", originCurrency.getDisplayName()));
                    citizen.getScheduler().run(plugin,
                            task -> citizen.sendMessage(notice), null);
                }
            }
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

    /** Atomic balance deduction with one retry against a concurrently changed account. */
    private double collectDebt(final UUID citizenId, final CurrencyType currency,
                               final double due) {
        for (int attempt = 0; attempt < 2; attempt++) {
            final double fresh = currencyManager.getBalance(citizenId, currency);
            final double payable = Math.floor(Math.min(due, fresh) * 100.0D) / 100.0D;
            if (payable <= 0.0D) {
                return 0.0D;
            }
            if (currencyManager.deductFromBalance(citizenId, currency, payable)) {
                return payable;
            }
        }
        return 0.0D;
    }
}
