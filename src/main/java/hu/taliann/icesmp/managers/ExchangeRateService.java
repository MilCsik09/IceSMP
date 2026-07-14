package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supply-driven dynamic exchange rates between faction currencies.
 *
 * Each currency has a configured base value. Its effective value scales with scarcity:
 * value = base-value * clamp((reference-supply / max(supply, 1)) ^ elasticity, min, max).
 * The rate from currency A to currency B is value(A) / value(B), so flooding the server
 * with one currency devalues it against the others — emulating a real economy.
 */
public final class ExchangeRateService {

    /** Supply-scan cache TTL: getTotalSupply walks every stored balance (O(players)). */
    private static final long SUPPLY_CACHE_TTL_MILLIS = 5_000L;

    private record CachedSupply(double supply, long expiresAt) { }

    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final EconomyEventManager economyEventManager;
    private final Map<CurrencyType, CachedSupply> supplyCache = new ConcurrentHashMap<>();

    public ExchangeRateService(final ConfigManager configManager, final CurrencyManager currencyManager,
                               final EconomyEventManager economyEventManager) {
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.economyEventManager = economyEventManager;
    }

    public boolean isEnabled() {
        return configManager.getBoolean("currency.dynamic-exchange.enabled", true);
    }

    /**
     * Gets the current effective value index of a currency based on its circulating supply.
     *
     * @param currencyType the currency
     * @return the scarcity-adjusted value of one unit
     */
    public double getValue(final CurrencyType currencyType) {
        // Demand-shock events temporarily inflate one currency's base value.
        final double baseValue = Math.max(0.0001D, configManager.getDouble(
                "currency.dynamic-exchange.base-values." + currencyType.name(), 1.0D))
                * economyEventManager.getMultiplier(currencyType);
        if (!isEnabled()) {
            return baseValue;
        }

        final double referenceSupply = Math.max(1.0D, configManager.getDouble(
                "currency.dynamic-exchange.reference-supply", 10000.0D));
        final double elasticity = Math.max(0.0D, configManager.getDouble(
                "currency.dynamic-exchange.elasticity", 0.5D));
        final double minMultiplier = Math.max(0.01D, configManager.getDouble(
                "currency.dynamic-exchange.min-multiplier", 0.25D));
        final double maxMultiplier = Math.max(minMultiplier, configManager.getDouble(
                "currency.dynamic-exchange.max-multiplier", 4.0D));

        final double supply = Math.max(1.0D, cachedTotalSupply(currencyType));
        final double rawMultiplier = Math.pow(referenceSupply / supply, elasticity);
        final double multiplier = Math.min(maxMultiplier, Math.max(minMultiplier, rawMultiplier));
        return baseValue * multiplier;
    }

    /**
     * Total supply with a short TTL cache: the exchange-board tick queries all four currencies
     * every refresh, and each uncached query walks the full balance store. A few seconds of
     * staleness is invisible in the rates (supply moves slowly) but removes the repeated scans.
     */
    private double cachedTotalSupply(final CurrencyType currencyType) {
        final long now = System.currentTimeMillis();
        final CachedSupply cached = supplyCache.get(currencyType);
        if (cached != null && cached.expiresAt() > now) {
            return cached.supply();
        }
        final double supply = currencyManager.getTotalSupply(currencyType);
        supplyCache.put(currencyType, new CachedSupply(supply, now + SUPPLY_CACHE_TTL_MILLIS));
        return supply;
    }

    /**
     * Gets the current exchange rate between two currencies:
     * how many units of 'to' one unit of 'from' is worth right now.
     *
     * @param from the source currency
     * @param to the target currency
     * @return the dynamic exchange rate
     */
    public double getRate(final CurrencyType from, final CurrencyType to) {
        if (from == null || to == null || from == to) {
            return 1.0D;
        }

        return getValue(from) / getValue(to);
    }

    public double getFeePercent() {
        return Math.max(0.0D, configManager.getDouble("currency.exchange-fee-percent", 0.0D));
    }
}
