package hu.taliann.icesmp.pve;

/** Internal encounter telemetry estimate. It is intentionally not a public gear score. */
public final class CombatPowerEstimator {
    public record Input(int level, double maximumHealth, double attackDamage, double armor,
                        double healingPerSecond, double mitigation,
                        double signatureSynergy, int setTiers, int runes) { }

    private CombatPowerEstimator() { }

    public static double estimate(final Input input) {
        if (input == null) return 1.0D;
        final double health = bounded(input.maximumHealth(), 1.0D, 10_000.0D);
        final double damage = bounded(input.attackDamage(), 0.0D, 2_000.0D);
        final double armor = bounded(input.armor(), 0.0D, 100.0D);
        final double healing = bounded(input.healingPerSecond(), 0.0D, 1_000.0D);
        final double mitigation = bounded(input.mitigation(), 0.0D, 0.9D);
        final double synergy = bounded(input.signatureSynergy(), 0.0D, 1.0D);
        final double raw = Math.max(1, input.level()) * 2.0D
                + Math.sqrt(health) * 2.2D + damage * 5.0D + armor * 1.5D
                + healing * 2.0D + mitigation * 180.0D + synergy * 80.0D
                + Math.max(0, Math.min(8, input.setTiers())) * 12.0D
                + Math.max(0, Math.min(2, input.runes())) * 15.0D;
        return Math.min(10_000.0D, raw);
    }

    private static double bounded(final double value, final double minimum, final double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
