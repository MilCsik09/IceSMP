package hu.taliann.icesmp.managers;

import java.util.Set;
import java.util.UUID;

public final class SeasonRewardStateDataRegressionTest {

    private SeasonRewardStateDataRegressionTest() {
    }

    public static void main(final String[] args) {
        generationValidation();
        receiptDecisions();
        durationValidation();
        System.out.println("SeasonRewardStateDataRegressionTest: PASS");
    }

    private static void generationValidation() {
        SeasonRewardStateData.validateBatchGeneration(8, 7, 8);
        expectFailure(() -> SeasonRewardStateData.validateBatchGeneration(7, 7, 8));
        expectFailure(() -> SeasonRewardStateData.validateBatchGeneration(9, 7, 8));
        expectFailure(() -> SeasonRewardStateData.validateBatchGeneration(8, 7, 9));
    }

    private static void receiptDecisions() {
        final UUID recipient = UUID.randomUUID();
        final UUID grant = UUID.randomUUID();
        check(SeasonRewardStateData.deliveryDecision(recipient, recipient, grant, Set.of())
                == SeasonRewardStateData.DeliveryDecision.DELIVER, "missing receipt must deliver");
        check(SeasonRewardStateData.deliveryDecision(recipient, recipient, grant, Set.of(grant))
                == SeasonRewardStateData.DeliveryDecision.ACKNOWLEDGE, "matching receipt must acknowledge");
        check(SeasonRewardStateData.deliveryDecision(recipient, UUID.randomUUID(), grant, Set.of())
                == SeasonRewardStateData.DeliveryDecision.WRONG_RECIPIENT, "wrong player must not receive");
    }

    private static void durationValidation() {
        check(SeasonRewardStateData.safeBuffTicks(30) == 36_000, "30 minutes must be exact");
        check(SeasonRewardStateData.safeBuffTicks(Long.MAX_VALUE / 1200L) == Integer.MAX_VALUE,
                "huge finite duration must saturate");
        expectFailure(() -> SeasonRewardStateData.safeBuffTicks(-1));
        expectFailure(() -> SeasonRewardStateData.safeBuffTicks(Long.MAX_VALUE));
    }

    private static void expectFailure(final Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected failure");
        } catch (final IllegalArgumentException | ArithmeticException expected) {
            // expected
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
