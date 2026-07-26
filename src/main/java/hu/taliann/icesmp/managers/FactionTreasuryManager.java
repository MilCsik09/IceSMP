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
    /**
     * Már alkalmazott, azonosítóhoz kötött jóváírások. A bejegyzés az egyenleggel EGY atomi
     * fájl-képbe kerül, ezért a hívó (pl. a közösségi cél kifizetés-outboxa) pontosan egyszeri
     * alkalmazást kap: a napló-nyugtázás és az egyenleg-írás közti crash nem duplázhat.
     */
    private final Map<String, Long> appliedGrants = new ConcurrentHashMap<>();
    /** A nyugta-halmaz felső korlátja (a régi bejegyzések kiesnek). */
    private static final int MAX_APPLIED_GRANTS = 20_000;
    private final Map<FactionType, Double> taxRates = new ConcurrentHashMap<>();
    /**
     * Unpaid citizen tax per player (hátralék): when a wallet can't cover the tax due
     * (e.g. a zero balance kept empty on purpose), the shortfall is recorded here and
     * settled automatically at the next collections, capped so it can't grow unboundedly.
     */
    private final Map<UUID, Double> taxArrears = new ConcurrentHashMap<>();
    /**
     * Tax-evasion strikes (adócsalás): consecutive collections where the arrears sat at
     * the cap and NOTHING could be deducted. At the configured threshold the Reckoners
     * report the evader — +1 sin through SinManager (whose own threshold exiles to the
     * Outcasts), then the counter resets. Cleared as soon as the debt drops below the cap.
     */
    private final Map<UUID, Integer> evasionStrikes = new ConcurrentHashMap<>();
    /** Sin hook for the evasion report (SinManager is built earlier in the DI order). */
    private final SinManager sinManager;
    /** Debounce-kapu a lemezíráshoz (CurrencyManager.requestSave mintája). */
    private final java.util.concurrent.atomic.AtomicBoolean saveScheduled =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public FactionTreasuryManager(final JavaPlugin plugin, final ConfigManager configManager,
                                  final CurrencyManager currencyManager, final FactionManager factionManager,
                                  final SinManager sinManager, final MessageManager messageManager) {
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
        balances.clear();
        appliedGrants.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
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

            evasionStrikes.clear();
            final ConfigurationSection strikesSection = yaml.getConfigurationSection("tax-evasion-strikes");
            if (strikesSection != null) {
                for (final String key : strikesSection.getKeys(false)) {
                    try {
                        final int strikes = strikesSection.getInt(key, 0);
                        if (strikes > 0) {
                            evasionStrikes.put(UUID.fromString(key), strikes);
                        }
                    } catch (final IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Invalid UUID in treasury.yml tax-evasion-strikes: " + key);
                    }
                }
            }

            final ConfigurationSection grants = yaml.getConfigurationSection("applied-grants");
            if (grants != null) {
                for (final String key : grants.getKeys(false)) {
                    appliedGrants.put(key, grants.getLong(key, System.currentTimeMillis()));
                }
            }
            plugin.getLogger().info("Loaded faction treasury balances.");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load faction treasury: " + exception.getMessage());
        }
    }

    /**
     * Debounce-olt aszinkron mentés a játékos-szálas hívóknak (donate/withdraw/tax-rate):
     * a rövid ablakon belüli sok módosítás egyetlen atomi írássá olvad össze — a
     * régió-szálakon nincs blokkoló I/O (CurrencyManager.requestSave mintája). A
     * disable-kori PersistentStore.save() továbbra is szinkron zár le.
     */
    public void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public synchronized void save() {
        writeState();
    }

    /** @return true, ha az atomi írás sikerült (a jóváírás commit-pontja ezt kéri) */
    private synchronized boolean writeState() {
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
            plugin.getLogger().severe("Failed to save faction treasury: " + exception.getMessage());
            return false;
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
            requestSave();
        }
        return applied;
    }

    /**
     * Idempotens jóváírás: ugyanazzal a {@code grantId}-val többször meghívva PONTOSAN egyszer
     * változtatja az egyenleget, és a nyugtát az egyenleggel egyetlen atomi írásban rögzíti.
     *
     * @param grantId a hívó stabil, egyszeri művelet-azonosítója
     * @return true, ha az állapot tartósan alkalmazva van (vagy már korábban az volt)
     */
    public synchronized boolean depositOnce(final String grantId, final FactionType faction,
                                            final double amount) {
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
        pruneAppliedGrants();
        if (saveNow()) {
            return true;
        }
        // A tartós írás nem sikerült: visszaállunk, hogy a hívó újrapróbálhassa.
        appliedGrants.remove(grantId);
        if (previous == null) {
            balances.remove(faction);
        } else {
            balances.put(faction, previous);
        }
        return false;
    }

    private void pruneAppliedGrants() {
        if (appliedGrants.size() <= MAX_APPLIED_GRANTS) {
            return;
        }
        appliedGrants.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(appliedGrants.size() - MAX_APPLIED_GRANTS)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(appliedGrants::remove);
    }

    /** Szinkron mentés a jóváírás commit-pontjához (a sérült fájlt a YamlStore amúgy sem írja). */
    private boolean saveNow() {
        return !YamlStore.isLoadFailed(storageFile) && writeState();
    }

    public void deposit(final FactionType faction, final double amount) {
        if (faction == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return;
        }

        balances.merge(faction, amount, Double::sum);
        requestSave();
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
        requestSave();
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
            // A levonás előtt FRISS egyenleget olvasunk (a játékos a saját szálán közben
            // kereshetett/költhetett): ha nőtt, a teljes esedékest szedjük be; ha csökkent és
            // az atomi tryDeduct emiatt elutasít, egyszer újrapróbáljuk a még frissebb értékkel.
            double payable = 0.0D;
            double paid = 0.0D;
            for (int attempt = 0; attempt < 2 && paid <= 0.0D; attempt++) {
                final double fresh = currencyManager.getBalance(citizenId, currency);
                payable = Math.floor(Math.min(due, fresh) * 100.0D) / 100.0D;
                if (payable <= 0.0D) {
                    break;
                }
                paid = currencyManager.deductFromBalance(citizenId, currency, payable) ? payable : 0.0D;
            }
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

            // Adócsalás: strike, ha a hátralék a plafonon ragadt ÉS semmit sem sikerült levonni;
            // törlődik, amint a tartozás a plafon alá kerül. A küszöbnél a Számvevők feljelentik
            // az adócsalót (+1 bűn — a bűn-küszöb a meglévő száműzetés-mechanikát indítja).
            final int evasionThreshold = Math.max(0, configManager.getInt("factions.tax.evasion-strikes", 3));
            if (evasionThreshold > 0 && maxArrears > 0.0D && paid <= 0.0D && owedAfter >= maxArrears) {
                // Plafon a küszöbnél: tartósan offline adócsalónál a számláló ne nőjön korlátlanul.
                final int strikes = evasionStrikes.merge(citizenId, 1,
                        (current, one) -> Math.min(evasionThreshold, current + one));
                arrearsChanged = true;
                if (strikes >= evasionThreshold) {
                    final Player evader = Bukkit.getPlayer(citizenId);
                    // A bűn PDC-be íródik és world-effektet játszik → csak online bűnösnél, a saját
                    // régió-szálán (Folia). Offline adócsalónál a strike a küszöbön marad, és az
                    // első online beszedéskor csapódik le.
                    if (evader != null) {
                        evasionStrikes.remove(citizenId);
                        evader.getScheduler().run(plugin, task -> {
                            sinManager.addSin(evader, 1);
                            evader.sendMessage(messageManager.getMessage(
                                    "faction-tax-evasion",
                                    "&4⚖ Adócsalás! &cA Számvevők feljelentettek — bűnt róttak fel neked. Törleszd a hátralékod, mielőtt a bűneid súlya a Kitaszítottak közé taszít!"));
                        }, null);
                    }
                }
            } else if (owedAfter < maxArrears && evasionStrikes.remove(citizenId) != null) {
                arrearsChanged = true;
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

        // Takarítás: a frakció-nyilvántartásból eltűnt (törölt/ismeretlen) játékosok
        // hátralék/strike-bejegyzései ne hizlalják örökre a treasury.yml-t. (Exempt frakcióba
        // váltónál a hátralék MEGMARAD — különben a váltás adósság-törlő kiskapu lenne.)
        final java.util.Set<UUID> known = factionManager.getFactionAssignments().keySet();
        arrearsChanged |= taxArrears.keySet().removeIf(id -> !known.contains(id));
        arrearsChanged |= evasionStrikes.keySet().removeIf(id -> !known.contains(id));

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
