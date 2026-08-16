package hu.taliann.icesmp.hud;

/** Immutable display projection of total class XP into the current level interval. */
public record ClassXpProgress(int total, int intoLevel, int levelCost,
                              int remaining, int percent, boolean maxed) {

    public ClassXpProgress {
        total = Math.max(0, total);
        intoLevel = Math.max(0, intoLevel);
        levelCost = Math.max(1, levelCost);
        remaining = Math.max(0, remaining);
        percent = Math.max(0, Math.min(100, percent));
    }

    public static ClassXpProgress calculate(final int totalXp, final int level,
                                            final int baseXp, final int increment,
                                            final int maximumLevel) {
        final int safeTotal = Math.max(0, totalXp);
        final int safeLevel = Math.max(1, level);
        final int safeBase = Math.max(1, baseXp);
        final int safeIncrement = Math.max(0, increment);
        if (safeLevel >= Math.max(1, maximumLevel)) {
            return new ClassXpProgress(safeTotal, 1, 1, 0, 100, true);
        }
        final long completed = safeLevel - 1L;
        final long start = completed * safeBase
                + safeIncrement * completed * Math.max(0L, completed - 1L) / 2L;
        final long cost = (long) safeBase + completed * safeIncrement;
        final long progress = Math.max(0L, Math.min(cost, (long) safeTotal - start));
        final long remaining = Math.max(0L, cost - progress);
        final int percent = (int) Math.round(progress * 100.0D / Math.max(1L, cost));
        return new ClassXpProgress(safeTotal, bounded(progress), bounded(cost),
                bounded(remaining), percent, false);
    }

    public static ClassXpProgress empty() {
        return new ClassXpProgress(0, 0, 1, 1, 0, false);
    }

    private static int bounded(final long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }
}
