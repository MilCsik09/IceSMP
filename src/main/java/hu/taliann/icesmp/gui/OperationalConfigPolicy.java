package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;

/** Cross-key safety rules that cannot be represented by one icon's numeric min/max range. */
public final class OperationalConfigPolicy {

    private OperationalConfigPolicy() {
    }

    /** @return null when accepted, otherwise a player-facing Hungarian explanation. */
    public static String validate(final String key, final Object proposed,
                                  final ConfigManager configManager) {
        if (key == null || proposed == null || configManager == null) {
            return null;
        }
        if (!(proposed instanceof Number number)) {
            return null;
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            return "Az értéknek véges számnak kell lennie.";
        }

        return switch (key) {
            case "currency.exchange-rate" -> value > 0.0D
                    ? null : "A fix árfolyamnak nullánál nagyobbnak kell lennie.";
            case "currency.dynamic-exchange.min-multiplier" -> value >= 0.01D
                    ? compareAtMost(value,
                            configManager.getDouble("currency.dynamic-exchange.max-multiplier", 4.0D),
                            "Az árfolyam alsó korlátja nem lehet nagyobb a felső korlátnál.")
                    : "A dinamikus árfolyam alsó korlátja legalább 0,01 legyen.";
            case "currency.dynamic-exchange.max-multiplier" -> compareAtLeast(value,
                    Math.max(0.01D, configManager.getDouble(
                            "currency.dynamic-exchange.min-multiplier", 0.25D)),
                    "Az árfolyam felső korlátja nem lehet kisebb az alsó korlátnál.");
            case "currency.economy-event.min-multiplier" -> compareAtMost(value,
                    configManager.getDouble("currency.economy-event.max-multiplier", 1.6D),
                    "A pozitív sokk minimum szorzója nem lehet nagyobb a maximumnál.");
            case "currency.economy-event.max-multiplier" -> compareAtLeast(value,
                    configManager.getDouble("currency.economy-event.min-multiplier", 1.2D),
                    "A pozitív sokk maximum szorzója nem lehet kisebb a minimumnál.");
            case "currency.economy-event.panic-min-multiplier" -> compareAtMost(value,
                    configManager.getDouble("currency.economy-event.panic-max-multiplier", 0.8D),
                    "A piaci pánik minimum szorzója nem lehet nagyobb a maximumnál.");
            case "currency.economy-event.panic-max-multiplier" -> compareAtLeast(value,
                    configManager.getDouble("currency.economy-event.panic-min-multiplier", 0.6D),
                    "A piaci pánik maximum szorzója nem lehet kisebb a minimumnál.");
            case "market.auction.default-duration-hours" -> compareAtMost(value,
                    configManager.getDouble("market.auction.max-duration-hours", 72.0D),
                    "Az alap aukcióidő nem lehet hosszabb a maximális aukcióidőnél.");
            case "market.auction.max-duration-hours" -> compareAtLeast(value,
                    configManager.getDouble("market.auction.default-duration-hours", 24.0D),
                    "A maximális aukcióidő nem lehet rövidebb az alap aukcióidőnél.");
            case "tablist.ping-colors.good" -> compareAtMost(value,
                    configManager.getDouble("tablist.ping-colors.ok", 150.0D),
                    "A zöld pingküszöb nem lehet nagyobb a sárga küszöbnél.");
            case "tablist.ping-colors.ok" -> compareAtLeast(value,
                    configManager.getDouble("tablist.ping-colors.good", 80.0D),
                    "A sárga pingküszöb nem lehet kisebb a zöld küszöbnél.");
            case "pets.summon.tier2-level" -> compareAtMost(value,
                    configManager.getDouble("pets.summon.tier3-level", 25.0D),
                    "A második társforma szintje nem lehet magasabb a harmadik formáénál.");
            case "pets.summon.tier3-level" -> compareAtLeast(value,
                    configManager.getDouble("pets.summon.tier2-level", 15.0D),
                    "A harmadik társforma szintje nem lehet alacsonyabb a második formáénál.");
            case "pets.companion.follow-start-distance" -> compareAtMost(value,
                    configManager.getDouble("pets.companion.follow-distance", 16.0D),
                    "A gyalogos követés kezdőtávja nem lehet nagyobb a visszateleportálási távnál.");
            case "pets.companion.follow-distance" -> compareAtLeast(value,
                    configManager.getDouble("pets.companion.follow-start-distance", 5.0D),
                    "A visszateleportálási táv nem lehet kisebb a követés kezdőtávjánál.");
            case "pets.companion.aggro-range" -> compareAtMost(value,
                    configManager.getDouble("pets.companion.leash-range", 24.0D),
                    "Az automatikus aggro-táv nem lehet nagyobb a célpont elengedési távjánál.");
            case "pets.companion.leash-range" -> compareAtLeast(value,
                    configManager.getDouble("pets.companion.aggro-range", 10.0D),
                    "A célpont elengedési távja nem lehet kisebb az automatikus aggro-távnál.");
            default -> null;
        };
    }

    private static String compareAtMost(final double value, final double upper,
                                        final String message) {
        return value <= upper ? null : message;
    }

    private static String compareAtLeast(final double value, final double lower,
                                         final String message) {
        return value >= lower ? null : message;
    }
}
