package hu.taliann.icesmp.quest;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;

import java.util.Optional;

/** One canonical resolver for configured quest currency payout and presentation. */
public final class QuestCurrencyResolver {

    private QuestCurrencyResolver() {
    }

    public static CurrencyType resolve(final String configuredType,
                                       final Optional<FactionType> chosenFaction) {
        if (isOwn(configuredType)) {
            return chosenFaction.map(CurrencyType::fromFactionType)
                    .orElse(CurrencyType.NEUTRAL);
        }
        return CurrencyType.fromInput(configuredType);
    }

    public static boolean isOwn(final String raw) {
        return "OWN".equalsIgnoreCase(raw) || "FACTION".equalsIgnoreCase(raw)
                || "SAJAT".equalsIgnoreCase(raw) || "SAJÁT".equalsIgnoreCase(raw);
    }
}
