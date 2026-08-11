package hu.taliann.icesmp.classspec.domain;

/** Per-specialization ten-rank mastery progress. */
public record MasteryProgress(int rank, long experience) {

    public static final int MAX_RANK = 10;

    public MasteryProgress {
        if (rank < 0 || rank > MAX_RANK) {
            throw new IllegalArgumentException("Mastery rank must be between 0 and " + MAX_RANK);
        }
        if (experience < 0L) {
            throw new IllegalArgumentException("Mastery experience must be non-negative");
        }
    }

    public static MasteryProgress empty() {
        return new MasteryProgress(0, 0L);
    }
}
