package hu.taliann.icesmp.playerprofile.application;

import java.util.Objects;

/**
 * Pure decision boundary for crash-safe physical quest rewards.
 *
 * <p>A physical side effect is always preceded by a durable PREPARED component receipt. If a
 * crash happens after the item entered the inventory but before the component becomes DELIVERED,
 * the stamped inventory item is the recovery witness: recovery acknowledges it instead of
 * creating a second copy. Once DELIVERED is durable, recovery must never mint the component again.</p>
 */
public final class QuestRewardDeliveryProtocol {

    public enum Decision {
        DELIVER,
        ACKNOWLEDGE_WITNESS,
        SKIP_DELIVERED
    }

    private QuestRewardDeliveryProtocol() { }

    public static Decision decide(final PlayerProfileQuestStore.RewardComponentState state,
                                  final boolean physicalWitnessPresent) {
        Objects.requireNonNull(state, "state");
        if (state == PlayerProfileQuestStore.RewardComponentState.DELIVERED) {
            return Decision.SKIP_DELIVERED;
        }
        return physicalWitnessPresent ? Decision.ACKNOWLEDGE_WITNESS : Decision.DELIVER;
    }
}
