package hu.taliann.icesmp.classspec.application;

import java.util.Locale;
import java.util.Set;

/** Canonical DARK specialization classification used by lifecycle policy. */
public final class DarkSpecializationPolicy {

    public static final Set<String> IDS = Set.of(
            "necromancer",
            "plaguebringer",
            "unholy",
            "bone_priest",
            "demonologist");

    private DarkSpecializationPolicy() {
    }

    public static boolean isDark(final String specializationId) {
        return specializationId != null
                && IDS.contains(specializationId.trim().toLowerCase(Locale.ROOT));
    }
}
