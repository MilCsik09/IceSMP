package hu.taliann.icesmp.managers;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/** Bukkit-free validation and replay decisions for durable season rewards. */
public final class SeasonRewardStateData {

    public enum DeliveryDecision {
        DELIVER,
        ACKNOWLEDGE,
        WRONG_RECIPIENT
    }

    private SeasonRewardStateData() {
    }

    public static void validateBatchGeneration(final int currentSeason,
                                               final int closingSeason,
                                               final int openedSeason) {
        if (closingSeason < 1 || openedSeason != closingSeason + 1) {
            throw new IllegalArgumentException("Invalid season reward generation transition");
        }
        if (currentSeason != openedSeason) {
            throw new IllegalArgumentException("Season reward batch does not belong to the active generation");
        }
    }

    public static DeliveryDecision deliveryDecision(final UUID claimRecipient,
                                                     final UUID currentPlayer,
                                                     final UUID grantId,
                                                     final Collection<UUID> durableReceipts) {
        Objects.requireNonNull(claimRecipient, "claimRecipient");
        Objects.requireNonNull(currentPlayer, "currentPlayer");
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(durableReceipts, "durableReceipts");
        if (!claimRecipient.equals(currentPlayer)) {
            return DeliveryDecision.WRONG_RECIPIENT;
        }
        return durableReceipts.contains(grantId)
                ? DeliveryDecision.ACKNOWLEDGE
                : DeliveryDecision.DELIVER;
    }

    public static int safeBuffTicks(final long minutes) {
        if (minutes < 0L) {
            throw new IllegalArgumentException("Season buff duration cannot be negative");
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.multiplyExact(minutes, 60L * 20L));
    }
}
