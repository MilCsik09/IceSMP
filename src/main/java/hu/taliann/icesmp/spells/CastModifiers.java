package hu.taliann.icesmp.spells;

/**
 * Immutable, semantically separated cast modifiers.
 *
 * <p>The shared spell-power sources (mastery, class level, talents, gear,
 * generic combos and class mechanics) intentionally affect magnitude outputs
 * only. They must not lengthen hard crowd control, harmful/beneficial effect
 * durations, cooldowns or costs unless a dedicated mechanic explicitly opts in
 * by constructing a different modifier set.</p>
 */
public record CastModifiers(
        double damageMultiplier,
        double healingMultiplier,
        double shieldingMultiplier,
        double beneficialDurationMultiplier,
        double harmfulDurationMultiplier,
        double ccDurationMultiplier,
        double cooldownMultiplier,
        double costMultiplier,
        double resourceGenerationMultiplier
) {

    public static final CastModifiers IDENTITY = new CastModifiers(
            1.0D, 1.0D, 1.0D,
            1.0D, 1.0D, 1.0D,
            1.0D, 1.0D, 1.0D);

    public CastModifiers {
        damageMultiplier = validMultiplier("damageMultiplier", damageMultiplier);
        healingMultiplier = validMultiplier("healingMultiplier", healingMultiplier);
        shieldingMultiplier = validMultiplier("shieldingMultiplier", shieldingMultiplier);
        beneficialDurationMultiplier = validMultiplier("beneficialDurationMultiplier", beneficialDurationMultiplier);
        harmfulDurationMultiplier = validMultiplier("harmfulDurationMultiplier", harmfulDurationMultiplier);
        ccDurationMultiplier = validMultiplier("ccDurationMultiplier", ccDurationMultiplier);
        cooldownMultiplier = validMultiplier("cooldownMultiplier", cooldownMultiplier);
        costMultiplier = validMultiplier("costMultiplier", costMultiplier);
        resourceGenerationMultiplier = validMultiplier("resourceGenerationMultiplier", resourceGenerationMultiplier);
    }

    /**
     * Standard IceSMP spell power. Only damage, healing and shielding scale.
     * Durations — especially hard CC — remain exactly at their designed base.
     */
    public static CastModifiers standardPower(final double powerMultiplier) {
        final double power = validMultiplier("powerMultiplier", powerMultiplier);
        return new CastModifiers(
                power, power, power,
                1.0D, 1.0D, 1.0D,
                1.0D, 1.0D, 1.0D);
    }

    /** Combines independent modifier sources multiplicatively. */
    public CastModifiers combine(final CastModifiers other) {
        if (other == null) {
            return this;
        }
        return new CastModifiers(
                damageMultiplier * other.damageMultiplier,
                healingMultiplier * other.healingMultiplier,
                shieldingMultiplier * other.shieldingMultiplier,
                beneficialDurationMultiplier * other.beneficialDurationMultiplier,
                harmfulDurationMultiplier * other.harmfulDurationMultiplier,
                ccDurationMultiplier * other.ccDurationMultiplier,
                cooldownMultiplier * other.cooldownMultiplier,
                costMultiplier * other.costMultiplier,
                resourceGenerationMultiplier * other.resourceGenerationMultiplier);
    }

    private static double validMultiplier(final String name, final double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and >= 0.0, got " + value);
        }
        return value;
    }
}
