package hu.taliann.icesmp.pve;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Spawn-fixed elite modifiers. Combination validation is deliberately narrow and bounded. */
public enum EliteAffix {
    VOLATILE,
    VAMPIRIC,
    SHIELDED,
    FRENZIED,
    FROSTBOUND,
    ARCANE,
    SUMMONER;

    private static final Set<Set<EliteAffix>> FORBIDDEN = Set.of(
            Set.of(SUMMONER, ARCANE),
            Set.of(VAMPIRIC, FRENZIED));

    public static EliteAffix parse(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("elite affix required");
        return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public static List<EliteAffix> validate(final List<EliteAffix> requested) {
        if (requested == null || requested.isEmpty()) return List.of();
        final LinkedHashSet<EliteAffix> unique = new LinkedHashSet<>(requested);
        if (unique.size() != requested.size() || unique.size() > 2) {
            throw new IllegalArgumentException("elite mobs support at most two distinct affixes");
        }
        if (unique.size() == 2 && FORBIDDEN.contains(Set.copyOf(unique))) {
            throw new IllegalArgumentException("forbidden elite affix combination: " + unique);
        }
        return List.copyOf(unique);
    }
}
