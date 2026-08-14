package hu.taliann.icesmp.classspec.integration;

import java.util.ArrayList;
import java.util.List;

/** One generic discrete mechanic slot for charge rows and class-specific wheels. */
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

    /** Builds a bounded visual pip row without giving the HUD ownership of the underlying count. */
    public static List<ClassHudSlot> charges(final String id, final String kind, final String label,
                                             final int current, final int maximum) {
        if (maximum <= 0) return List.of();
        final int visibleMaximum = Math.min(9, maximum);
        final int available = Math.max(0, Math.min(current, visibleMaximum));
        final ArrayList<ClassHudSlot> slots = new ArrayList<>(visibleMaximum);
        for (int index = 0; index < visibleMaximum; index++) {
            slots.add(new ClassHudSlot(safe(id) + "_" + (index + 1), safe(kind),
                    index < available ? "ready" : "spent", index < available ? 100 : 0,
                    safe(label)));
        }
        return List.copyOf(slots);
    }
}
