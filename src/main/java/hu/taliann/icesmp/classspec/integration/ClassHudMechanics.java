package hu.taliann.icesmp.classspec.integration;

import java.util.List;

/** Class-owned transient projection before common class/spec identity is attached. */
public record ClassHudMechanics(ClassHudMetric primary, ClassHudMetric secondary,
                                String state, String proc, int charges, int chargesMax,
                                List<ClassHudMetric> metrics, List<ClassHudSlot> slots) {
    public ClassHudMechanics {
        primary = primary == null ? ClassHudMetric.text("", "", "", "") : primary;
        secondary = secondary == null ? ClassHudMetric.text("", "", "", "") : secondary;
        state = state == null ? "" : state.trim();
        proc = proc == null ? "" : proc.trim();
        charges = Math.max(0, charges);
        chargesMax = Math.max(charges, chargesMax);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    public static ClassHudMechanics of(final ClassHudMetric primary, final ClassHudMetric secondary,
                                       final String state, final String proc,
                                       final int charges, final int chargesMax) {
        final ClassHudMetric safePrimary = primary == null
                ? ClassHudMetric.text("", "", "", "") : primary;
        final ClassHudMetric safeSecondary = secondary == null
                ? ClassHudMetric.text("", "", "", "") : secondary;
        return new ClassHudMechanics(safePrimary, safeSecondary, state, proc, charges, chargesMax,
                List.of(safePrimary, safeSecondary), List.of());
    }

    public static ClassHudMechanics empty() {
        return of(null, null, "", "", 0, 0);
    }
}
