package hu.taliann.icesmp.spells;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** Pure final gate for proposed active kits and selected-spell reconciliation. */
public final class ActiveKitReconciler {

    private ActiveKitReconciler() {
    }

    /**
     * Filters a class runtime's proposed kit against the current grant set and registry,
     * removes duplicates and enforces the configured maximum while preserving order.
     */
    public static List<String> reconcile(final List<String> unlocked,
                                         final List<String> proposed,
                                         final int maximum,
                                         final Predicate<String> registered) {
        if (unlocked == null || unlocked.isEmpty() || proposed == null || proposed.isEmpty()
                || maximum <= 0 || registered == null) {
            return List.of();
        }
        final Set<String> granted = new HashSet<>();
        for (final String raw : unlocked) {
            final String id = normalize(raw);
            if (!id.isEmpty()) granted.add(id);
        }
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final String raw : proposed) {
            final String id = normalize(raw);
            if (id.isEmpty() || !granted.contains(id) || !registered.test(id)) continue;
            result.add(id);
            if (result.size() >= maximum) break;
        }
        return List.copyOf(new ArrayList<>(result));
    }

    /** Returns the current valid selection, deterministic first fallback, or empty for a sealed/empty kit. */
    public static String reconcileSelected(final String selected, final List<String> active) {
        if (active == null || active.isEmpty()) return "";
        final String normalized = normalize(selected);
        return active.contains(normalized) ? normalized : active.getFirst();
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
