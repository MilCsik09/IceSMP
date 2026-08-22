package hu.taliann.icesmp.pve;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** One typed, bounded step in a composable mob technique. */
public record MobTechniqueAction(Type type, Target target, Map<String, Double> parameters) {
    public enum Type { DAMAGE, KNOCKBACK, DASH, RETREAT, GUARD }
    public enum Target { SELF, CURRENT_TARGET, PROVOKER, NEARBY_PLAYERS }

    public MobTechniqueAction {
        type = Objects.requireNonNull(type, "action type");
        target = Objects.requireNonNullElse(target, Target.CURRENT_TARGET);
        final LinkedHashMap<String, Double> safe = new LinkedHashMap<>();
        if (parameters != null) parameters.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("invalid technique action parameter");
            }
            safe.put(key.trim().toLowerCase(Locale.ROOT).replace('-', '_'), value);
        });
        if (safe.size() > 16) throw new IllegalArgumentException("too many technique action parameters");
        parameters = Map.copyOf(safe);
    }

    public double parameter(final String name, final double fallback) {
        return parameters.getOrDefault(name, fallback);
    }
}
