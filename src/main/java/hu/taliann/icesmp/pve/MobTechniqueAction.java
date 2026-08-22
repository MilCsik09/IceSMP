package hu.taliann.icesmp.pve;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** One typed, bounded step in a composable mob technique. */
public record MobTechniqueAction(Type type, Target target, Map<String, Double> parameters,
                                 String reference) {
    public enum Type { DAMAGE, KNOCKBACK, DASH, RETREAT, GUARD, APPLY_EFFECT, SUMMON_TEMPLATE }
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
        reference = reference == null ? "" : reference.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._:-]", "_");
        if ((type == Type.APPLY_EFFECT || type == Type.SUMMON_TEMPLATE) && reference.isBlank()) {
            throw new IllegalArgumentException("technique action reference required");
        }
    }

    public MobTechniqueAction(final Type type, final Target target,
                              final Map<String, Double> parameters) {
        this(type, target, parameters, "");
    }

    public double parameter(final String name, final double fallback) {
        return parameters.getOrDefault(name, fallback);
    }
}
