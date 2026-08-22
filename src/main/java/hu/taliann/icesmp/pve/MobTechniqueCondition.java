package hu.taliann.icesmp.pve;

import java.util.Locale;

/** Minimal typed condition vocabulary used by currently authored IceSMP techniques. */
public record MobTechniqueCondition(Type type, double value) {
    public enum Type { COMBAT_ACTIVE, ADULT, UNTAMED, HEALTH_BELOW, DISTANCE_WITHIN }

    public MobTechniqueCondition {
        if (type == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException("invalid mob technique condition");
        }
        if ((type == Type.HEALTH_BELOW && (value <= 0.0D || value > 1.0D))
                || (type == Type.DISTANCE_WITHIN && (value < 0.5D || value > 32.0D))) {
            throw new IllegalArgumentException("mob technique condition value out of bounds");
        }
    }

    public static Type parseType(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("condition type required");
        return Type.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
