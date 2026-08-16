package hu.taliann.icesmp.hud;

/** Immutable, display-only projection of the four vanilla survival HUD resources. */
public record SurvivalHudState(double health, double maximumHealth, double absorption,
                               double armor, double maximumArmor,
                               int food, int maximumFood,
                               int air, int maximumAir) {

    public SurvivalHudState {
        maximumHealth = positive(maximumHealth, 20.0D);
        health = clamp(finite(health, 0.0D), 0.0D, maximumHealth);
        absorption = Math.max(0.0D, finite(absorption, 0.0D));
        maximumArmor = positive(maximumArmor, 20.0D);
        armor = Math.max(0.0D, finite(armor, 0.0D));
        maximumFood = Math.max(1, maximumFood);
        food = Math.max(0, Math.min(maximumFood, food));
        maximumAir = Math.max(1, maximumAir);
        air = Math.max(0, Math.min(maximumAir, air));
    }

    public int healthPercent() {
        return percent(health, maximumHealth);
    }

    public int armorPercent() {
        return percent(armor, maximumArmor);
    }

    public int foodPercent() {
        return percent(food, maximumFood);
    }

    public int airPercent() {
        return percent(air, maximumAir);
    }

    private static int percent(final double value, final double maximum) {
        return (int) Math.round(clamp(value / Math.max(1.0D, maximum), 0.0D, 1.0D) * 100.0D);
    }

    private static double positive(final double value, final double fallback) {
        final double safe = finite(value, fallback);
        return safe > 0.0D ? safe : fallback;
    }

    private static double finite(final double value, final double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(final double value, final double minimum, final double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
