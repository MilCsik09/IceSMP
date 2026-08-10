package hu.taliann.icesmp.classspec.application;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Explicit allowlist of classes whose gameplay-v2 vertical slice is complete.
 *
 * <p>The canonical staging base intentionally enables none. Each stacked class PR extends this
 * list only when that class's complete vertical slice is present, so Profile v2 infrastructure can
 * be hardened independently without activating gameplay that has not landed yet.</p>
 */
public final class GameplayV2ClassPolicy {

    private static final Set<String> ENABLED = Set.of();

    private GameplayV2ClassPolicy() {
    }

    public static boolean isEnabled(final String classId) {
        return classId != null && ENABLED.contains(classId.toLowerCase(Locale.ROOT));
    }

    public static String enabledList() {
        return String.join(", ", new TreeSet<>(ENABLED));
    }
}
