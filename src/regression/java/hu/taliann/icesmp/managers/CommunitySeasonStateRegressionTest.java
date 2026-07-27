package hu.taliann.icesmp.managers;

public final class CommunitySeasonStateRegressionTest {

    private CommunitySeasonStateRegressionTest() {
    }

    public static void main(final String[] args) {
        assertEquals(CommunitySeasonState.LoadAction.INITIALISE_CURRENT,
                CommunitySeasonState.reconcileOnLoad(null, 7, true),
                "legacy marker migration must preserve current state");
        assertEquals(CommunitySeasonState.LoadAction.KEEP_CURRENT,
                CommunitySeasonState.reconcileOnLoad(7, 7, true),
                "matching stores must remain untouched");
        assertEquals(CommunitySeasonState.LoadAction.RESET_TO_CURRENT,
                CommunitySeasonState.reconcileOnLoad(7, 8, false),
                "a crash after season commit must replay the community reset");

        expectFailure(() -> CommunitySeasonState.reconcileOnLoad(8, 7, false),
                "community marker ahead of season store");
        expectFailure(() -> CommunitySeasonState.reconcileOnLoad(6, 8, false),
                "multi-season drift");
        expectFailure(() -> CommunitySeasonState.reconcileOnLoad(7, 8, true),
                "old payout crossing into a new season");

        CommunitySeasonState.validateTransition(7, 7, 8, false);
        expectFailure(() -> CommunitySeasonState.validateTransition(6, 7, 8, false),
                "transition from the wrong community generation");
        expectFailure(() -> CommunitySeasonState.validateTransition(7, 7, 9, false),
                "skipped target season");
        expectFailure(() -> CommunitySeasonState.validateTransition(7, 7, 8, true),
                "transition with unsettled payouts");
    }

    private static void assertEquals(final Object expected, final Object actual,
                                     final String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void expectFailure(final Runnable action, final String message) {
        try {
            action.run();
        } catch (final IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }
}
