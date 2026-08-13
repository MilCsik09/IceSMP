package hu.taliann.icesmp.prologue;

public enum BreachSeverity {
    MINOR(1, 1.0D, 1.0D),
    MAJOR(2, 1.45D, 1.35D),
    CRITICAL(3, 2.0D, 1.75D);

    private final int waves;
    private final double mobMultiplier;
    private final double eliteMultiplier;

    BreachSeverity(final int waves, final double mobMultiplier, final double eliteMultiplier) {
        this.waves = waves;
        this.mobMultiplier = mobMultiplier;
        this.eliteMultiplier = eliteMultiplier;
    }

    public int waves() { return waves; }
    public double mobMultiplier() { return mobMultiplier; }
    public double eliteMultiplier() { return eliteMultiplier; }
}
