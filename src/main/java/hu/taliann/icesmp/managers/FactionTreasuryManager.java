package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.factions.FactionTaxDebtLedger;
import hu.taliann.icesmp.factions.DurableRecoveryPolicy;
import hu.taliann.icesmp.factions.DurableTransactionProtocol;
import hu.taliann.icesmp.factions.FactionTaxEvasionPolicy;
import hu.taliann.icesmp.factions.FactionTaxJournal;
import hu.taliann.icesmp.factions.FactionTreasuryAmountPolicy;
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
    private final FactionTaxJournal taxJournal;
    /** Minden store-mutáció, snapshot, tartós írás és rollback közös monitora. */
    private final Object stateLock = new Object();
    private final Map<FactionType, Double> balances = new ConcurrentHashMap<>();
    /** Már alkalmazott, azonosítóhoz kötött jóváírások. */
    private final Map<String, Long> appliedGrants = new ConcurrentHashMap<>();
    // Grant-nyugtát nem dobunk ki pusztán darabszám alapján: a másik cél-store tartós
    // hibája miatt hosszan nyitva maradó outbox replaye különben duplán jóváírhatna.
    private final Map<FactionType, Double> taxRates = new ConcurrentHashMap<>();
    /** Unpaid tax and evasion strikes, retained per assessing faction/currency. */
    private FactionTaxDebtLedger taxDebts = new FactionTaxDebtLedger();
    private final SinManager sinManager;
    /** Serializes owner-thread tax-evasion delivery across collection runs. */
    private final Set<UUID> taxSinDeliveriesInFlight = ConcurrentHashMap.newKeySet();
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
        this.taxJournal = new FactionTaxJournal(
                new File(plugin.getDataFolder(), "faction-tax-journal.yml"), plugin.getLogger());
        YamlStore.registerCriticalWrite(storageFile);
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        synchronized (stateLock) {
            final Map<FactionType, Double> previousBalances = Map.copyOf(balances);
            final Map<FactionType, Double> previousRates = Map.copyOf(taxRates);
            final Map<String, Long> previousGrants = Map.copyOf(appliedGrants);
            final FactionTaxDebtLedger previousDebts = taxDebts.copy();
            try {
                balances.clear();
                taxRates.clear();
                appliedGrants.clear();
                taxDebts = new FactionTaxDebtLedger();

                if (storageFile.exists()) {
                    final YamlConfiguration yaml = YamlStore.loadTracked(
                            storageFile, plugin.getLogger());
                    final ConfigurationSection section = yaml.getConfigurationSection("treasury");
                    if (section != null) {
                        for (final String factionKey : section.getKeys(false)) {
                            final FactionType faction = FactionType.fromInput(factionKey);
                            if (faction == null) {
                                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                        "Unknown faction in treasury.yml: " + factionKey);
                                return;
                            }
                            balances.put(faction, readStoredNonNegative(
                                    section, factionKey, "treasury." + factionKey));
                        }
                    }

                    final ConfigurationSection rateSection = yaml.getConfigurationSection("tax-rates");
                    if (rateSection != null) {
                        for (final String factionKey : rateSection.getKeys(false)) {
                            final FactionType faction = FactionType.fromInput(factionKey);
                            if (faction == null) {
                                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                        "Unknown faction in treasury.yml tax-rates: " + factionKey);
                                return;
                            }
                            taxRates.put(faction, readStoredNonNegative(
                                    rateSection, factionKey, "tax-rates." + factionKey));
                        }
                    }

                    final boolean migratedLegacyTaxDebt = loadTaxDebtState(yaml);
                    final ConfigurationSection grants =
                            yaml.getConfigurationSection("applied-grants");
                    if (grants != null) {
                        for (final String key : grants.getKeys(false)) {
                            appliedGrants.put(key, readStoredTimestamp(
                                    grants, key, "applied-grants." + key));
                        }
                    }
                    if (migratedLegacyTaxDebt) {
                        if (YamlStore.isLoadFailed(storageFile) || !writeStateLocked()) {
                            throw new IllegalStateException(
                                    "Legacy faction tax debt migration could not be persisted");
                        }
                        plugin.getLogger().info(
                                "Quarantined legacy scalar faction tax debt without inferring an origin.");
                    }
                }

                taxJournal.load();
                recoverPendingTaxTransaction();
                if (!taxDebts.quarantinedLegacyPlayerIds().isEmpty()) {
                    plugin.getLogger().warning("Quarantined unknown-origin legacy tax debt for "
                            + taxDebts.quarantinedLegacyPlayerIds().size()
                            + " player(s); no runtime collection or automatic faction binding occurs.");
                }
                plugin.getLogger().info("Loaded faction treasury balances.");
            } catch (final RuntimeException | Error failure) {
                balances.clear();
                balances.putAll(previousBalances);
                taxRates.clear();
                taxRates.putAll(previousRates);
                appliedGrants.clear();
                appliedGrants.putAll(previousGrants);
                taxDebts = previousDebts;
                throw failure;
            }
        }
    }

    /**
     * Loads the origin-aware schema, or quarantines the pre-rework scalar maps once. A mixed file
     * prefers the new schema so a stale legacy section cannot duplicate an already preserved debt.
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
            // The scalar legacy schema never recorded the assessing faction/currency. Even an
            // active or last-chosen membership is only a guess after a historical switch, so the
            // greenfield runtime never auto-converts it. Preserve it byte-for-value in quarantine
            // until an explicit offline admin migration supplies a proven origin.
            taxDebts.putUnresolvedLegacy(playerId, amount, evasionStrikes);
            plugin.getLogger().warning(
                    "Legacy tax debt for " + playerId + " has no proven origin. "
                            + "It is quarantined and excluded from runtime collection until an "
                            + "explicit offline admin migration supplies the original faction/currency.");
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
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Invalid non-section tax-debts record for " + playerKey);
                }
                continue;
            }
            for (final String factionKey : playerSection.getKeys(false)) {
                final FactionType origin = FactionType.fromInput(factionKey);
                final ConfigurationSection debtSection =
                        playerSection.getConfigurationSection(factionKey);
                if (origin == null || debtSection == null) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
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
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
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
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Invalid UUID in treasury.yml " + section + ": " + raw);
            return null;
        }
    }

    private double readStoredNonNegative(final ConfigurationSection section,
                                                   final String key, final String path) {
        final Object raw = section.get(key);
        if (!(raw instanceof Number number)) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Invalid non-numeric persisted amount at " + path);
            return 0.0D;
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0D) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Invalid non-finite/negative persisted amount at " + path);
            return 0.0D;
        }
        return value;
    }

    private double readDebtAmount(final ConfigurationSection section, final String key,
                                  final String path) {
        final Object raw = section.get(key);
        if (raw == null) {
            return 0.0D;
        }
        if (!(raw instanceof Number number)) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Invalid non-numeric tax debt at " + path);
            return 0.0D;
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0D) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Invalid non-finite/negative tax debt at " + path);
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
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Invalid non-numeric tax-evasion strike count at " + path);
            return 0;
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0D || value > Integer.MAX_VALUE
                || value != Math.rint(value)) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Invalid non-integral/out-of-range tax-evasion strike count at " + path);
            return 0;
        }
        return (int) value;
    }


    private long readStoredTimestamp(final ConfigurationSection section, final String key,
                                     final String path) {
        final Object raw = section.get(key);
        if (!(raw instanceof Number number)) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Invalid non-numeric persisted timestamp at " + path);
            return 0L;
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0D || value > Long.MAX_VALUE
                || value != Math.rint(value)) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Invalid non-integral/out-of-range persisted timestamp at " + path);
            return 0L;
        }
        return number.longValue();
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

    private double nonNegativeFiniteConfig(final String path, final double defaultValue) {
        return nonNegativeFiniteConfig(configManager.snapshot(), path, defaultValue);
    }

    private double nonNegativeFiniteConfig(final ConfigManager.ConfigSnapshot snapshot,
                                           final String path, final double defaultValue) {
        final Object raw = snapshot.isSet(path) && snapshot.configuration() != null
                ? snapshot.configuration().get(path) : defaultValue;
        if (raw instanceof Number number) {
            final double value = number.doubleValue();
            if (Double.isFinite(value) && value >= 0.0D) {
                return value;
            }
        }
        plugin.getLogger().warning("Config: invalid '" + path + "' value (" + raw
                + "); expected a finite non-negative number — this branch is disabled at 0.0, "
                + "not silently clamped.");
        return 0.0D;
    }

    private int nonNegativeIntConfig(final String path, final int defaultValue) {
        return nonNegativeIntConfig(configManager.snapshot(), path, defaultValue);
    }

    private int nonNegativeIntConfig(final ConfigManager.ConfigSnapshot snapshot,
                                     final String path, final int defaultValue) {
        final Object raw = snapshot.isSet(path) && snapshot.configuration() != null
                ? snapshot.configuration().get(path) : defaultValue;
        if (raw instanceof Number number) {
            final double value = number.doubleValue();
            if (Double.isFinite(value) && value >= 0.0D && value <= Integer.MAX_VALUE
                    && value == Math.rint(value)) {
                return (int) value;
            }
        }
        plugin.getLogger().warning("Config: invalid '" + path + "' value (" + raw
                + "); expected a non-negative integer — this branch is disabled at 0, "
                + "not silently clamped.");
        return 0;
    }

    /** Gets a faction's effective citizen-tax rate. */
    public double getTaxRate(final FactionType faction) {
        return getTaxRate(faction, configManager.snapshot());
    }

    private double getTaxRate(final FactionType faction,
                              final ConfigManager.ConfigSnapshot snapshot) {
        synchronized (stateLock) {
            final double configDefault = nonNegativeFiniteConfig(
                    snapshot, "factions.tax.rate-percent", 2.0D);
            final double stored = faction == null
                    ? configDefault : taxRates.getOrDefault(faction, configDefault);
            if (Double.isFinite(stored) && stored >= 0.0D) {
                return stored;
            }
            plugin.getLogger().warning("Invalid persisted tax rate for " + faction
                    + "; using the validated config default.");
            return configDefault;
        }
    }

    /** Sets a faction's citizen-tax rate. The configured maximum is explicit, not hidden. */
    public double setTaxRate(final FactionType faction, final double ratePercent) {
        if (faction == null || !Double.isFinite(ratePercent) || ratePercent < 0.0D) {
            plugin.getLogger().warning("Rejected invalid faction tax rate: " + ratePercent);
            return faction == null ? 0.0D : getTaxRate(faction);
        }
        final double max = nonNegativeFiniteConfig(
                "factions.tax.max-rate-percent", 10.0D);
        final double applied = Math.min(max, ratePercent);
        if (ratePercent > max) {
            plugin.getLogger().warning("Faction tax rate " + ratePercent
                    + " exceeds configured factions.tax.max-rate-percent=" + max
                    + "; applying the documented maximum.");
        }
        synchronized (stateLock) {
            taxRates.put(faction, applied);
        }
        requestSave();
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
            final double next = FactionTreasuryAmountPolicy.checkedAdd(
                    previous == null ? 0.0D : previous, amount);
            if (!Double.isFinite(next)) {
                plugin.getLogger().warning("Rejected overflowing faction treasury grant '"
                        + grantId + "' for " + faction + ".");
                return false;
            }
            balances.put(faction, next);
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
            final double next = FactionTreasuryAmountPolicy.checkedAdd(
                    balances.getOrDefault(faction, 0.0D), amount);
            if (!Double.isFinite(next)) {
                plugin.getLogger().warning("Rejected overflowing faction treasury deposit for "
                        + faction + ".");
                return;
            }
            balances.put(faction, next);
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
            if (!Double.isFinite(balance) || balance < amount) {
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
     * original currency and credited back to its original treasury. Unassigned guests receive no
     * new assessment, but a previously assessed origin-aware debt remains collectible after reset.
     */
    public void collectTaxes() {
        final ConfigManager.ConfigSnapshot config = configManager.snapshot();
        final org.bukkit.configuration.file.FileConfiguration live = config.configuration();
        if ((live == null || !live.getBoolean("factions.tax.enabled", true))
                || YamlStore.hasCriticalWriteFailure()) {
            return;
        }

        final List<String> exempt = live == null ? List.of() : live
                .getStringList("factions.tax.exempt").stream()
                .map(name -> name.toUpperCase(Locale.ROOT))
                .toList();
        final double minimum = nonNegativeFiniteConfig(
                config, "factions.tax.minimum-amount", 2.0D);
        final double maxArrears = nonNegativeFiniteConfig(
                config, "factions.tax.max-arrears", 50.0D);
        final int evasionThreshold = nonNegativeIntConfig(
                config, "factions.tax.evasion-strikes", 3);

        final Map<UUID, FactionType> assignments = factionManager.getFactionAssignments();
        final Set<UUID> participants = new HashSet<>(assignments.keySet());
        synchronized (stateLock) {
            participants.addAll(taxDebts.playerIdsWithDebt());
        }
        final Set<UUID> evasionReportedThisRun = new HashSet<>();

        for (final UUID citizenId : participants) {
            final FactionType currentFaction = assignments.get(citizenId);
            final double newAssessment;
            if (currentFaction == null || exempt.contains(currentFaction.name())) {
                newAssessment = 0.0D;
            } else {
                final CurrencyType currentCurrency = CurrencyType.fromFactionType(currentFaction);
                final double ratePercent = getTaxRate(currentFaction, config);
                final double balance = currencyManager.getBalance(citizenId, currentCurrency);
                final double percentDue = ratePercent <= 0.0D
                        ? 0.0D : Math.floor(balance * ratePercent) / 100.0D;
                newAssessment = Math.max(percentDue, minimum);
            }

            final EnumSet<FactionType> origins = EnumSet.noneOf(FactionType.class);
            synchronized (stateLock) {
                for (final FactionTaxDebtLedger.Debt debt : taxDebts.debtsFor(citizenId)) {
                    origins.add(debt.faction());
                }
            }
            if (newAssessment > 0.0D) {
                origins.add(currentFaction);
            }

            for (final FactionType origin : origins) {
                final double assessed = currentFaction != null && origin == currentFaction
                        ? newAssessment : 0.0D;
                final Player online = Bukkit.getPlayer(citizenId);
                final boolean mayReportSin = online != null
                        && !evasionReportedThisRun.contains(citizenId);
                final TaxCommit commit = commitTaxTransaction(citizenId, origin, assessed,
                        maxArrears, evasionThreshold, mayReportSin);
                if (commit.reportSin() && online != null
                        && !evasionReportedThisRun.contains(citizenId)
                        && taxSinDeliveriesInFlight.add(citizenId)) {
                    evasionReportedThisRun.add(citizenId);
                    try {
                        online.getScheduler().run(plugin, task -> {
                            try {
                                if (!online.isOnline()) {
                                    return;
                                }
                                sinManager.addSin(online, 1);
                                online.sendMessage(messageManager.getMessage(
                                        "faction-tax-evasion",
                                        "&4⚖ Adócsalás! &cA Számvevők feljelentettek — bűnt róttak fel neked. Törleszd a hátralékod, mielőtt a bűneid súlya a Kitaszítottak közé taszít!"));
                                if (!acknowledgeTaxEvasionSin(citizenId, origin)) {
                                    plugin.getLogger().severe(
                                            "Tax-evasion sin was applied, but its durable pending strike "
                                                    + "could not be acknowledged for " + citizenId
                                                    + " / " + origin + ".");
                                }
                            } finally {
                                taxSinDeliveriesInFlight.remove(citizenId);
                            }
                        }, () -> {
                            taxSinDeliveriesInFlight.remove(citizenId);
                            plugin.getLogger().fine(
                                    "Deferred tax-evasion sin because the player scheduler retired: "
                                            + citizenId);
                        });
                    } catch (final RuntimeException scheduleFailure) {
                        taxSinDeliveriesInFlight.remove(citizenId);
                        plugin.getLogger().warning(
                                "Could not schedule tax-evasion sin for " + citizenId
                                        + "; the durable strike remains pending: "
                                        + scheduleFailure.getMessage());
                    }
                }
                if (!commit.changed()) {
                    continue;
                }
                if (online != null && (commit.paid() > 0.0D
                        || commit.owedAfter() > commit.owedBefore())) {
                    final CurrencyType currency = CurrencyType.fromFactionType(origin);
                    final net.kyori.adventure.text.Component notice = commit.owedAfter() > 0.0D
                            ? messageManager.getMessage(
                            "faction-tax-arrears",
                            "&6Állampolgári adó: &f{amount} {currency}&6 levonva, hátralékod: &c{arrears} {currency}&7 — a Számvevők a következő beszedéskor is behajtják.",
                            Map.of("amount", currencyManager.formatBalance(commit.paid()),
                                    "arrears", currencyManager.formatBalance(commit.owedAfter()),
                                    "currency", currency.getDisplayName()))
                            : messageManager.getMessage(
                            "faction-tax-notice",
                            "&6Állampolgári adó levonva: &f{amount} {currency} &7(a frakciókasszába került).",
                            Map.of("amount", currencyManager.formatBalance(commit.paid()),
                                    "currency", currency.getDisplayName()));
                    online.getScheduler().run(plugin, task -> {
                        if (online.isOnline()) {
                            online.sendMessage(notice);
                        }
                    }, null);
                }
            }
        }
    }

    private record TaxCommit(boolean changed, double paid, double owedBefore,
                             double owedAfter, boolean reportSin) {
    }

    private TaxCommit commitTaxTransaction(final UUID playerId, final FactionType origin,
                                           final double assessed, final double maxArrears,
                                           final int evasionThreshold,
                                           final boolean mayReportSin) {
        synchronized (stateLock) {
            final FactionTaxJournal.DomainState before = domainState(playerId, origin);
            final double due = before.debtAmount() + assessed;
            if (due <= 0.0D && before.evasionStrikes() == 0) {
                return new TaxCommit(false, 0.0D, before.debtAmount(),
                        before.debtAmount(), false);
            }

            final CurrencyType currency = CurrencyType.fromFactionType(origin);
            final double fresh = currencyManager.getBalance(playerId, currency);
            final double plannedPayment = Math.floor(Math.min(Math.max(0.0D, due), fresh)
                    * 100.0D) / 100.0D;
            CurrencyManager.DurableMutation wallet = plannedPayment > 0.0D
                    ? currencyManager.planDurableDeduction(playerId, currency, plannedPayment)
                    : null;
            final double paid = wallet == null ? 0.0D : plannedPayment;
            final double owedAfter = Math.min(maxArrears,
                    Math.max(0.0D, Math.round((due - paid) * 100.0D) / 100.0D));

            final FactionTaxEvasionPolicy.Decision evasion =
                    FactionTaxEvasionPolicy.afterCollection(
                            before.evasionStrikes(), paid, owedAfter, maxArrears,
                            evasionThreshold, mayReportSin);
            final int strikesAfter = evasion.strikesAfter();
            final boolean reportSin = evasion.reportSin();
            final double treasuryAfter = paid <= 0.0D
                    ? before.treasuryBalance()
                    : FactionTreasuryAmountPolicy.checkedAdd(
                    before.treasuryBalance(), paid);
            if (!Double.isFinite(treasuryAfter)) {
                throw new IllegalStateException(
                        "Faction treasury credit would overflow for " + origin);
            }

            final FactionTaxJournal.DomainState after = new FactionTaxJournal.DomainState(
                    treasuryAfter, owedAfter, strikesAfter);
            if (before.equals(after) && wallet == null) {
                return new TaxCommit(false, 0.0D, before.debtAmount(), owedAfter, reportSin);
            }

            final FactionTaxJournal.Entry[] journalEntry = new FactionTaxJournal.Entry[1];
            final CurrencyManager.DurableMutation walletMutation = wallet;
            final DurableTransactionProtocol.ExecutionResult transactionResult =
                    DurableTransactionProtocol.execute(new DurableTransactionProtocol.Steps() {
                @Override
                public void prepare() {
                    journalEntry[0] = taxJournal.prepare(
                            playerId, origin, before, after, walletMutation);
                }

                @Override
                public boolean hasWalletMutation() {
                    return walletMutation != null;
                }

                @Override
                public void applyWallet() {
                    currencyManager.applyDurably(walletMutation);
                }

                @Override
                public void commitDomain() {
                    applyDomainState(playerId, origin, after);
                    if (!writeStateLocked()) {
                        applyDomainState(playerId, origin, before);
                        throw new IllegalStateException("Faction tax state could not be persisted");
                    }
                }

                @Override
                public void rollbackWallet() {
                    currencyManager.rollbackDurably(walletMutation);
                }

                @Override
                public void completeJournal() {
                    taxJournal.complete(journalEntry[0]);
                }
            });
            if (transactionResult.recoveryPending()) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Tax transaction committed, but WAL cleanup failed; startup recovery will finalize it",
                        transactionResult.cleanupFailure());
            }
            return new TaxCommit(true, paid, before.debtAmount(), owedAfter, reportSin);
        }
    }

    private boolean acknowledgeTaxEvasionSin(final UUID playerId, final FactionType origin) {
        synchronized (stateLock) {
            final int previousStrikes = taxDebts.getEvasionStrikes(playerId, origin);
            if (previousStrikes <= 0) {
                return true;
            }
            final double previousAmount = taxDebts.getArrears(playerId, origin);
            taxDebts.put(playerId, origin, previousAmount, 0);
            if (writeStateLocked()) {
                return true;
            }
            taxDebts.put(playerId, origin, previousAmount, previousStrikes);
            return false;
        }
    }

    private FactionTaxJournal.DomainState domainState(final UUID playerId,
                                                       final FactionType origin) {
        return new FactionTaxJournal.DomainState(
                balances.getOrDefault(origin, 0.0D),
                taxDebts.getArrears(playerId, origin),
                taxDebts.getEvasionStrikes(playerId, origin));
    }

    private void applyDomainState(final UUID playerId, final FactionType origin,
                                  final FactionTaxJournal.DomainState state) {
        if (state.treasuryBalance() == 0.0D) {
            balances.remove(origin);
        } else {
            balances.put(origin, state.treasuryBalance());
        }
        taxDebts.put(playerId, origin, state.debtAmount(), state.evasionStrikes());
    }

    private void recoverPendingTaxTransaction() {
        final FactionTaxJournal.Entry entry = taxJournal.pending();
        if (entry == null) {
            return;
        }
        final FactionTaxJournal.DomainState current = domainState(
                entry.playerId(), entry.origin());
        final boolean domainBefore = current.equals(entry.before());
        final boolean domainAfter = current.equals(entry.after());
        final boolean walletBefore = entry.walletMutation() == null
                || currencyManager.walletMatches(entry.walletMutation(), false);
        final boolean walletAfter = entry.walletMutation() == null
                || currencyManager.walletMatches(entry.walletMutation(), true);

        switch (DurableRecoveryPolicy.decide(domainBefore, domainAfter, walletBefore,
                walletAfter, entry.walletMutation() != null)) {
            case COMPLETE_COMMITTED -> {
                taxJournal.complete(entry);
                plugin.getLogger().warning(
                        "Recovered committed tax transaction " + entry.id());
            }
            case DISCARD_UNAPPLIED -> {
                taxJournal.complete(entry);
                plugin.getLogger().warning(
                        "Discarded unapplied tax transaction " + entry.id());
            }
            case ROLLBACK_WALLET -> {
                currencyManager.rollbackDurably(entry.walletMutation());
                taxJournal.complete(entry);
                plugin.getLogger().warning(
                        "Rolled back interrupted tax transaction " + entry.id());
            }
            case ROLLBACK_DOMAIN -> {
                applyDomainState(entry.playerId(), entry.origin(), entry.before());
                if (!writeStateLocked()) {
                    applyDomainState(entry.playerId(), entry.origin(), entry.after());
                    throw new IllegalStateException(
                            "Could not roll back unpaid tax transaction");
                }
                taxJournal.complete(entry);
                plugin.getLogger().warning(
                        "Rolled back unpaid tax transaction " + entry.id());
            }
            case AMBIGUOUS -> taxJournal.failCorrupt(
                    "Ambiguous tax transaction recovery state for " + entry.id());
        }
    }

}
