package hu.taliann.icesmp.managers;

/**
 * Bukkit-free validator for the durable season marker stored by the community-goal store.
 *
 * <p>The season store is committed first. Therefore the community marker may be exactly one
 * season behind after a crash, but it may never be ahead, skip multiple seasons, or carry a
 * pending old-season payout across that boundary.</p>
 */
record CommunitySeasonState(int seasonNumber) {

    enum LoadAction {
        /** Legacy state without a marker: preserve its progress and bind it to the active season. */
        INITIALISE_CURRENT,
        /** Both stores already describe the same open season. */
        KEEP_CURRENT,
        /** Season committed, community reset did not: clear old progress and advance the marker. */
        RESET_TO_CURRENT
    }

    CommunitySeasonState {
        if (seasonNumber < 1) {
            throw new IllegalArgumentException("community season number must be positive");
        }
    }

    static LoadAction reconcileOnLoad(final Integer storedSeason,
                                      final int activeSeason,
                                      final boolean hasPendingPayouts) {
        if (activeSeason < 1) {
            throw new IllegalArgumentException("active season number must be positive");
        }
        if (storedSeason == null) {
            return LoadAction.INITIALISE_CURRENT;
        }
        if (storedSeason < 1) {
            throw new IllegalStateException("community season marker is not positive");
        }
        if (storedSeason > activeSeason) {
            throw new IllegalStateException("community season marker is ahead of season.yml");
        }
        if (storedSeason == activeSeason) {
            return LoadAction.KEEP_CURRENT;
        }
        if (storedSeason + 1 != activeSeason) {
            throw new IllegalStateException("community season marker skipped more than one season");
        }
        if (hasPendingPayouts) {
            throw new IllegalStateException(
                    "old-season community payouts remain after the season commit");
        }
        return LoadAction.RESET_TO_CURRENT;
    }

    static void validateTransition(final int storedSeason,
                                   final int closingSeason,
                                   final int openedSeason,
                                   final boolean hasPendingPayouts) {
        if (closingSeason < 1 || openedSeason != closingSeason + 1) {
            throw new IllegalArgumentException("season transition must open the next season");
        }
        if (storedSeason != closingSeason) {
            throw new IllegalStateException(
                    "community progress does not belong to the season being closed");
        }
        if (hasPendingPayouts) {
            throw new IllegalStateException(
                    "pending community payouts must settle before season commit");
        }
    }
}
