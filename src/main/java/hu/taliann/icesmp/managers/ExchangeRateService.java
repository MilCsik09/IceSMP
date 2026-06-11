package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;

/**
 * Supply-driven dynamic exchange rates between faction currencies.
 *
 * Each currency has a configured base value. Its effective value scales with scarcity:
 * value = base-value * clamp((reference-supply / max(supply, 1)) ^ elasticity, min, max).
 * The rate from currency A to currency B is value(A) / value(B), so flooding the server
 * with one currency devalues it against the others — emulating a real economy.
 */
public final class ExchangeRateService {

    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;

    public ExchangeRateService(final ConfigManager configManager, final CurrencyManager currencyManager) {
        this.configManager = configManager;
        this.currencyManager = currencyManager;
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
        final double baseValue = Math.max(0.0001D, configManager.getDouble(
                "currency.dynamic-exchange.base-values." + currencyType.name(), 1.0D));
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

        final double supply = Math.max(1.0D, currencyManager.getTotalSupply(currencyType));
        final double rawMultiplier = Math.pow(referenceSupply / supply, elasticity);
        final double multiplier = Math.min(maxMultiplier, Math.max(minMultiplier, rawMultiplier));
        return baseValue * multiplier;
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
