package hu.taliann.icesmp.prologue;

/** Determinisztikus, lineáris és felülről korlátozott event-skálázás. */
public final class PrologueScaling {
    private PrologueScaling() { }

    public static int effectivePlayers(final int participants, final int minimum, final int maximum) {
        final int lo = Math.max(1, minimum);
        final int hi = Math.max(lo, maximum);
        return Math.max(lo, Math.min(hi, participants));
    }

    public static int mobCount(final int base, final int participants,
                               final int minimumPlayers, final int maximumPlayers,
                               final double perPlayer, final int minimum, final int maximum) {
        final int effective = effectivePlayers(participants, minimumPlayers, maximumPlayers);
        final int extraPlayers = Math.max(0, effective - Math.max(1, minimumPlayers));
        final long scaled = Math.round(Math.max(0, base) + extraPlayers * Math.max(0.0D, perPlayer));
        final int lo = Math.max(0, minimum);
        final int hi = Math.max(lo, maximum);
        return (int) Math.max(lo, Math.min(hi, scaled));
    }

    public static double bossHealth(final double baseHealth, final int participants,
                                    final int minimumPlayers, final int maximumPlayers,
                                    final double healthPerExtraPlayer, final double maximumMultiplier) {
        final int effective = effectivePlayers(participants, minimumPlayers, maximumPlayers);
        final int extraPlayers = Math.max(0, effective - Math.max(1, minimumPlayers));
        final double multiplier = Math.min(Math.max(1.0D, maximumMultiplier),
                1.0D + extraPlayers * Math.max(0.0D, healthPerExtraPlayer));
        return Math.max(1.0D, baseHealth) * multiplier;
    }
}
