package hu.taliann.icesmp.prologue;

/** A kaszt-XP plafon és catch-up tiszta számításai. */
public final class PrologueProgression {
    private PrologueProgression() { }

    public static int experienceAtLevelStart(final int level, final int baseXp, final int increment) {
        final int target = Math.max(1, level);
        long total = 0L;
        for (int current = 1; current < target; current++) {
            final long cost = Math.addExact(Math.max(1, baseXp),
                    Math.multiplyExact((long) current - 1L, Math.max(0, increment)));
            total = Math.addExact(total, cost);
            if (total > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) total;
    }

    public static int clampExperienceToLevelCap(final int experience, final int levelCap,
                                                final int baseXp, final int increment) {
        return Math.max(0, Math.min(experience,
                experienceAtLevelStart(Math.max(1, levelCap), baseXp, increment)));
    }

    public static int applyMultiplier(final int value, final double multiplier) {
        if (value <= 0) return value;
        final double safe = Double.isFinite(multiplier) ? Math.max(1.0D, multiplier) : 1.0D;
        final long scaled = Math.round(value * safe);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(value, scaled));
    }
}
