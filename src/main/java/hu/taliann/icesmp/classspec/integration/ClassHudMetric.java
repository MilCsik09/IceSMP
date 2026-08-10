package hu.taliann.icesmp.classspec.integration;

/** Immutable numeric/text mechanic channel shared by every class HUD adapter. */
public record ClassHudMetric(String id, String label, String text,
                             double value, double maximum, String state) {
    public ClassHudMetric {
        id = safe(id);
        label = safe(label);
        text = safe(text);
        value = finite(value);
        maximum = Math.max(0.0D, finite(maximum));
        state = safe(state);
    }

    public int percent() {
        if (maximum <= 0.0D) return 0;
        return (int) Math.round(Math.max(0.0D, Math.min(100.0D, value * 100.0D / maximum)));
    }

    public static ClassHudMetric text(final String id, final String label, final String text,
                                      final String state) {
        return new ClassHudMetric(id, label, text, 0.0D, 0.0D, state);
    }

    public static ClassHudMetric value(final String id, final String label, final String text,
                                       final double value, final double maximum, final String state) {
        return new ClassHudMetric(id, label, text, value, maximum, state);
    }

    private static String safe(final String value) {
        return value == null ? "" : value.trim();
    }

    private static double finite(final double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
