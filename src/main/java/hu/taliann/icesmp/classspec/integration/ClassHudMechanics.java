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
        final ClassHudMetric chargeMetric = matchingChargeMetric(
                safePrimary, safeSecondary, charges, chargesMax);
        final String slotId = chargeMetric == null ? "charge" : chargeMetric.id();
        final String slotLabel = chargeMetric == null ? "" : chargeMetric.label();
        return of(safePrimary, safeSecondary, state, proc, charges, chargesMax, slotId, slotLabel);
    }

    /** Explicit typed slot projection for counters whose resource metric has a different id. */
    public static ClassHudMechanics of(final ClassHudMetric primary, final ClassHudMetric secondary,
                                       final String state, final String proc,
                                       final int charges, final int chargesMax,
                                       final String slotKind, final String slotLabel) {
        final ClassHudMetric safePrimary = primary == null
                ? ClassHudMetric.text("", "", "", "") : primary;
        final ClassHudMetric safeSecondary = secondary == null
                ? ClassHudMetric.text("", "", "", "") : secondary;
        final String safeKind = slotKind == null || slotKind.isBlank() ? "charge" : slotKind;
        return new ClassHudMechanics(safePrimary, safeSecondary, state, proc, charges, chargesMax,
                List.of(safePrimary, safeSecondary),
                ClassHudSlot.charges(safeKind, safeKind, slotLabel, charges, chargesMax));
    }

    public static ClassHudMechanics empty() {
        return of(null, null, "", "", 0, 0);
    }

    private static ClassHudMetric matchingChargeMetric(final ClassHudMetric primary,
                                                        final ClassHudMetric secondary,
                                                        final int charges, final int chargesMax) {
        if (matches(secondary, charges, chargesMax)) return secondary;
        if (matches(primary, charges, chargesMax)) return primary;
        return null;
    }

    private static boolean matches(final ClassHudMetric metric, final int charges, final int chargesMax) {
        return metric != null && !metric.id().isBlank() && chargesMax > 0
                && Math.round(metric.value()) == charges
                && Math.round(metric.maximum()) == chargesMax;
    }
}
