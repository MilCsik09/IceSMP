package hu.taliann.icesmp.pve;

/**
 * Bounded authored movement/positioning projection layered over vanilla mob AI.
 * It deliberately models only combat spacing; it is not a general behaviour tree.
 */
public record MobBehaviorProfile(double preferredRange, double minimumComfortRange,
                                 double maximumPursuitRange, double retreatTendency,
                                 double repositionTendency, double strafeTendency,
                                 double aggressionCadence, double chasePressure) {
    public MobBehaviorProfile {
        if (!bounded(preferredRange, 0.5D, 24.0D)
                || !bounded(minimumComfortRange, 0.0D, preferredRange)
                || !bounded(maximumPursuitRange, preferredRange, 32.0D)
                || !bounded(retreatTendency, 0.0D, 1.0D)
                || !bounded(repositionTendency, 0.0D, 1.0D)
                || !bounded(strafeTendency, 0.0D, 1.0D)
                || !bounded(aggressionCadence, 0.5D, 1.75D)
                || !bounded(chasePressure, 0.0D, 1.0D)) {
            throw new IllegalArgumentException("invalid mob behavior profile");
        }
    }

    public static MobBehaviorProfile defaults(final MobArchetype archetype) {
        return switch (archetype) {
            case BRUISER -> new MobBehaviorProfile(2.2D, 0.0D, 24.0D, 0.0D, 0.08D, 0.02D, 1.15D, 0.95D);
            case CHARGER -> new MobBehaviorProfile(7.0D, 2.5D, 26.0D, 0.12D, 0.45D, 0.08D, 0.90D, 0.85D);
            case SKIRMISHER -> new MobBehaviorProfile(5.0D, 2.4D, 22.0D, 0.48D, 0.80D, 0.72D, 1.05D, 0.70D);
            case RANGED -> new MobBehaviorProfile(11.0D, 5.0D, 26.0D, 0.72D, 0.62D, 0.55D, 0.90D, 0.45D);
            case ARTILLERY -> new MobBehaviorProfile(15.0D, 7.0D, 28.0D, 0.82D, 0.42D, 0.20D, 0.72D, 0.30D);
            case DEFENDER -> new MobBehaviorProfile(3.0D, 0.0D, 14.0D, 0.05D, 0.12D, 0.05D, 0.82D, 0.35D);
            case SUPPORT, HEALER -> new MobBehaviorProfile(9.0D, 4.5D, 22.0D, 0.68D, 0.72D, 0.48D, 0.76D, 0.35D);
            case SUMMONER -> new MobBehaviorProfile(12.0D, 6.0D, 25.0D, 0.78D, 0.70D, 0.35D, 0.78D, 0.35D);
            case ASSASSIN -> new MobBehaviorProfile(4.0D, 1.5D, 26.0D, 0.58D, 0.88D, 0.62D, 1.22D, 0.82D);
            case CONTROLLER -> new MobBehaviorProfile(9.0D, 4.0D, 24.0D, 0.62D, 0.76D, 0.58D, 0.82D, 0.42D);
            case FLYING -> new MobBehaviorProfile(10.0D, 4.0D, 28.0D, 0.42D, 0.75D, 0.65D, 1.00D, 0.62D);
        };
    }

    public double techniqueWeight(final MobAbilityDefinition.Kind kind,
                                  final double distance, final double healthFraction) {
        double score = 0.0D;
        if (distance < minimumComfortRange) {
            score += switch (kind) {
                case RETREAT, SHIELD, HEAL_PULSE -> 1.4D + retreatTendency;
                case GROUND_SLAM, CLEAVE, POISON_CLOUD -> 0.7D;
                default -> -0.7D;
            };
        } else if (distance > preferredRange + 2.0D) {
            score += switch (kind) {
                case LUNGE, PROJECTILE_BURST, DELAYED_RUNE -> 1.0D + chasePressure * 0.5D;
                case RETREAT -> -1.0D;
                default -> 0.0D;
            };
        }
        if (healthFraction < 0.45D && (kind == MobAbilityDefinition.Kind.SHIELD
                || kind == MobAbilityDefinition.Kind.HEAL_PULSE)) score += 1.0D;
        return score;
    }

    private static boolean bounded(final double value, final double minimum, final double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }
}
