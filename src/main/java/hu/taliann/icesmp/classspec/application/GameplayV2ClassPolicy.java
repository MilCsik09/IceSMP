package hu.taliann.icesmp.classspec.application;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Explicit allowlist of classes whose gameplay-v2 vertical slice is complete.
 *
 * <p>Two-loadout gameplay (SECOND slot learning, loadout switching, doctrine choice,
 * class mastery) stays fail-closed for every class until its own slice ships and is
 * added here. This is deliberately a plain list, not a capability framework.</p>
 */
public final class GameplayV2ClassPolicy {

    private static final Set<String> ENABLED = Set.of("warrior", "evoker", "archer", "shaman", "monk", "paladin");

    private GameplayV2ClassPolicy() {
    }

    public static boolean isEnabled(final String classId) {
        return classId != null && ENABLED.contains(classId.toLowerCase(Locale.ROOT));
    }

    /** Stable, human-readable list for rejection details. */
    public static String enabledList() {
        return String.join(", ", new TreeSet<>(ENABLED));
    }
}
