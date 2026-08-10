package hu.taliann.icesmp.classspec.integration;

import java.util.List;

/** Immutable, class-agnostic projection of transient class gameplay state for HUD consumers. */
public record ClassHudState(String classId, String specId, String specName,
                            String mechanicPrimary, String mechanicSecondary,
                            String state, String proc, int charges, int chargesMax,
                            List<String> mechanics, List<ClassHudMetric> metrics,
                            List<ClassHudSlot> slots) {
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
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    public ClassHudState(final String classId, final String specId, final String specName,
                         final String mechanicPrimary, final String mechanicSecondary,
                         final String state, final String proc, final int charges,
                         final int chargesMax, final List<String> mechanics) {
        this(classId, specId, specName, mechanicPrimary, mechanicSecondary, state, proc,
                charges, chargesMax, mechanics, List.of(), List.of());
    }

    public static ClassHudState empty() {
        return new ClassHudState("", "", "", "", "", "", "", 0, 0,
                List.of(), List.of(), List.of());
    }

    private static String safe(final String value) {
        return value == null ? "" : value.trim();
    }
}
