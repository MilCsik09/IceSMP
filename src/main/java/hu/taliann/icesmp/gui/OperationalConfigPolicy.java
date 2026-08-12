package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;

import java.util.function.Function;

/** Cross-key safety rules that cannot be represented by one icon's numeric min/max range. */
public final class OperationalConfigPolicy {

    private OperationalConfigPolicy() { }

    public static String validate(final String key, final Object proposed,
                                  final ConfigManager configManager) {
        return validate(key, proposed, configManager, ignored -> null);
    }

    /** @return null when accepted, otherwise a player-facing Hungarian explanation. */
    public static String validate(final String key, final Object proposed,
                                  final ConfigManager configManager,
                                  final ConfigEditSession session) {
        return validate(key, proposed, configManager,
                session == null ? ignored -> null : session::value);
    }

    public static String validate(final String key, final Object proposed,
                                  final ConfigManager configManager,
                                  final ConfigEditSession.Snapshot snapshot) {
        return validate(key, proposed, configManager,
                snapshot == null ? ignored -> null : snapshot::resolvedValue);
    }

    private static String validate(final String key, final Object proposed,
                                   final ConfigManager configManager,
                                   final Function<String, Object> stagedValue) {
        if (key == null || proposed == null || configManager == null) return null;
        if (!(proposed instanceof Number number)) return null;
        final double value = number.doubleValue();
        if (!Double.isFinite(value)) return "Az értéknek véges számnak kell lennie.";

        return switch (key) {
            case "currency.exchange-rate" -> value > 0.0D ? null
                    : "A fix árfolyamnak nullánál nagyobbnak kell lennie.";
            case "currency.dynamic-exchange.min-multiplier" -> value >= 0.01D
                    ? atMost(value, stagedDouble("currency.dynamic-exchange.max-multiplier", 4.0D,
                            configManager, stagedValue), "Az árfolyam alsó korlátja nem lehet nagyobb a felső korlátnál.")
                    : "A dinamikus árfolyam alsó korlátja legalább 0,01 legyen.";
            case "currency.dynamic-exchange.max-multiplier" -> atLeast(value,
                    Math.max(0.01D, stagedDouble("currency.dynamic-exchange.min-multiplier", 0.25D,
                            configManager, stagedValue)), "Az árfolyam felső korlátja nem lehet kisebb az alsó korlátnál.");
            case "currency.economy-event.min-multiplier" -> atMost(value,
                    stagedDouble("currency.economy-event.max-multiplier", 1.6D, configManager, stagedValue),
                    "A pozitív sokk minimum szorzója nem lehet nagyobb a maximumnál.");
            case "currency.economy-event.max-multiplier" -> atLeast(value,
                    stagedDouble("currency.economy-event.min-multiplier", 1.2D, configManager, stagedValue),
                    "A pozitív sokk maximum szorzója nem lehet kisebb a minimumnál.");
            case "currency.economy-event.panic-min-multiplier" -> atMost(value,
                    stagedDouble("currency.economy-event.panic-max-multiplier", 0.8D, configManager, stagedValue),
                    "A piaci pánik minimum szorzója nem lehet nagyobb a maximumnál.");
            case "currency.economy-event.panic-max-multiplier" -> atLeast(value,
                    stagedDouble("currency.economy-event.panic-min-multiplier", 0.6D, configManager, stagedValue),
                    "A piaci pánik maximum szorzója nem lehet kisebb a minimumnál.");
            case "market.auction.default-duration-hours" -> atMost(value,
                    stagedDouble("market.auction.max-duration-hours", 72.0D, configManager, stagedValue),
                    "Az alap aukcióidő nem lehet hosszabb a maximális aukcióidőnél.");
            case "market.auction.max-duration-hours" -> atLeast(value,
                    stagedDouble("market.auction.default-duration-hours", 24.0D, configManager, stagedValue),
                    "A maximális aukcióidő nem lehet rövidebb az alap aukcióidőnél.");
            case "tablist.ping-colors.good" -> atMost(value,
                    stagedDouble("tablist.ping-colors.ok", 150.0D, configManager, stagedValue),
                    "A zöld pingküszöb nem lehet nagyobb a sárga küszöbnél.");
            case "tablist.ping-colors.ok" -> atLeast(value,
                    stagedDouble("tablist.ping-colors.good", 80.0D, configManager, stagedValue),
                    "A sárga pingküszöb nem lehet kisebb a zöld küszöbnél.");
            case "pets.summon.tier2-level" -> atMost(value,
                    stagedDouble("pets.summon.tier3-level", 25.0D, configManager, stagedValue),
                    "A második társforma szintje nem lehet magasabb a harmadik formáénál.");
            case "pets.summon.tier3-level" -> atLeast(value,
                    stagedDouble("pets.summon.tier2-level", 15.0D, configManager, stagedValue),
                    "A harmadik társforma szintje nem lehet alacsonyabb a második formáénál.");
            case "pets.companion.follow-start-distance" -> atMost(value,
                    stagedDouble("pets.companion.follow-distance", 16.0D, configManager, stagedValue),
                    "A gyalogos követés kezdőtávja nem lehet nagyobb a visszateleportálási távnál.");
            case "pets.companion.follow-distance" -> atLeast(value,
                    stagedDouble("pets.companion.follow-start-distance", 5.0D, configManager, stagedValue),
                    "A visszateleportálási táv nem lehet kisebb a követés kezdőtávjánál.");
            case "pets.companion.aggro-range" -> atMost(value,
                    stagedDouble("pets.companion.leash-range", 24.0D, configManager, stagedValue),
                    "Az automatikus aggro-táv nem lehet nagyobb a célpont elengedési távjánál.");
            case "pets.companion.leash-range" -> atLeast(value,
                    stagedDouble("pets.companion.aggro-range", 10.0D, configManager, stagedValue),
                    "A célpont elengedési távja nem lehet kisebb az automatikus aggro-távnál.");
            default -> null;
        };
    }

    private static double stagedDouble(final String key, final double fallback,
                                       final ConfigManager configManager,
                                       final Function<String, Object> stagedValue) {
        final Object value = stagedValue.apply(key);
        return value instanceof Number number ? number.doubleValue()
                : configManager.getDouble(key, fallback);
    }
    private static String atMost(final double value, final double upper, final String message) {
        return value <= upper ? null : message;
    }
    private static String atLeast(final double value, final double lower, final String message) {
        return value >= lower ? null : message;
    }
}
