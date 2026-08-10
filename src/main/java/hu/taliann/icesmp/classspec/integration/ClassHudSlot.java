package hu.taliann.icesmp.classspec.integration;

/** One generic discrete mechanic slot, used first by the Death Knight rune wheel. */
public record ClassHudSlot(String id, String kind, String state, int progress, String label) {
    public ClassHudSlot {
        id = safe(id);
        kind = safe(kind);
        state = safe(state);
        progress = Math.max(0, Math.min(100, progress));
        label = safe(label);
    }

    private static String safe(final String value) {
        return value == null ? "" : value.trim();
    }
}
