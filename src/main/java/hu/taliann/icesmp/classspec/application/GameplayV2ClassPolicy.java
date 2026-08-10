package hu.taliann.icesmp.classspec.application;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Explicit allowlist of classes whose gameplay-v2 vertical slice is complete. */
public final class GameplayV2ClassPolicy {
    private static final Set<String> ENABLED = Set.of("warrior");
    private GameplayV2ClassPolicy() { }
    public static boolean isEnabled(final String classId) {
        return classId != null && ENABLED.contains(classId.toLowerCase(Locale.ROOT));
    }
    public static String enabledList() {
        return String.join(", ", new TreeSet<>(ENABLED));
    }
}
