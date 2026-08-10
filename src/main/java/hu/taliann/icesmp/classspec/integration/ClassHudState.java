package hu.taliann.icesmp.classspec.integration;

import java.util.List;

/** Immutable, class-agnostic projection of transient class gameplay state for HUD consumers. */
public record ClassHudState(String classId, String specId, String specName,
                            String mechanicPrimary, String mechanicSecondary,
                            String state, String proc, int charges, int chargesMax,
                            List<String> mechanics) {
    public ClassHudState {
        classId = safe(classId);
        specId = safe(specId);
        specName = safe(specName);
        mechanicPrimary = safe(mechanicPrimary);
        mechanicSecondary = safe(mechanicSecondary);
        state = safe(state);
        proc = safe(proc);
        charges = Math.max(0, charges);
        chargesMax = Math.max(charges, chargesMax);
        mechanics = mechanics == null ? List.of() : List.copyOf(mechanics);
    }

    public static ClassHudState empty() {
        return new ClassHudState("", "", "", "", "", "", "", 0, 0, List.of());
    }

    private static String safe(final String value) {
        return value == null ? "" : value.trim();
    }
}
