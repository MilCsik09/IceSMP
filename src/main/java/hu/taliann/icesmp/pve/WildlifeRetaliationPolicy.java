package hu.taliann.icesmp.pve;

import java.util.UUID;

/** Pure stable-temperament and retaliation decision model for passive wildlife. */
public final class WildlifeRetaliationPolicy {
    public enum Temperament { TIMID, DEFENSIVE, AGGRESSIVE }

    public record Weights(int timid, int defensive, int aggressive) {
        public Weights {
            if (timid < 0 || defensive < 0 || aggressive < 0
                    || timid + defensive + aggressive < 1
                    || timid + defensive + aggressive > 10_000) {
                throw new IllegalArgumentException("invalid wildlife temperament weights");
            }
        }

        int total() { return timid + defensive + aggressive; }
    }

    public record Chances(double timid, double defensive, double aggressive) {
        public Chances {
            if (!chance(timid) || !chance(defensive) || !chance(aggressive)) {
                throw new IllegalArgumentException("invalid wildlife retaliation chances");
            }
        }

        private static boolean chance(final double value) {
            return Double.isFinite(value) && value >= 0.0D && value <= 100.0D;
        }
    }

    private WildlifeRetaliationPolicy() { }

    public static Temperament stableTemperament(final UUID entityId, final Weights weights) {
        if (entityId == null) throw new IllegalArgumentException("wildlife entity id required");
        final long mixed = entityId.getMostSignificantBits()
                ^ Long.rotateLeft(entityId.getLeastSignificantBits(), 23);
        final int bucket = Math.floorMod(Long.hashCode(mixed), weights.total());
        if (bucket < weights.timid()) return Temperament.TIMID;
        if (bucket < weights.timid() + weights.defensive()) return Temperament.DEFENSIVE;
        return Temperament.AGGRESSIVE;
    }

    /** roll is a deterministic/testable [0,1) value; runtime supplies ThreadLocalRandom. */
    public static boolean retaliates(final Temperament temperament, final Chances chances,
                                     final double roll) {
        if (temperament == null || !Double.isFinite(roll) || roll < 0.0D || roll >= 1.0D) {
            return false;
        }
        final double percent = switch (temperament) {
            case TIMID -> chances.timid();
            case DEFENSIVE -> chances.defensive();
            case AGGRESSIVE -> chances.aggressive();
        };
        return roll * 100.0D < percent;
    }
}
