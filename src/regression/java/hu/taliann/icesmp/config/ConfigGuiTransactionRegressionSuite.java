package hu.taliann.icesmp.config;

import hu.taliann.icesmp.gui.ConfigEditSession;
import java.util.Map;

public final class ConfigGuiTransactionRegressionSuite {
    private ConfigGuiTransactionRegressionSuite() { }
    public static void main(final String[] args) {
        final Map<String, Object> opening = Map.of("a", true, "n", 10, "mode", "A");
        final Map<String, Object> defaults = Map.of("a", false, "n", 5, "mode", "B");
        final ConfigEditSession session = new ConfigEditSession(7L, "abc", opening, defaults);
        check(!session.dirty() && session.pendingChanges().isEmpty(), "open/cancel has no writes");
        session.stage("a", false);
        check(session.dirty() && Boolean.FALSE.equals(session.value("a")), "toggle staged only");
        session.reset("n");
        check(Integer.valueOf(5).equals(session.value("n")), "reset shows documented base default");
        check(session.pendingChanges().containsKey("n") && session.pendingChanges().get("n") == null,
                "reset persists as override removal");
        boolean immutable = false;
        try { session.pendingChanges().put("x", 1); } catch (final UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "save batch immutable");
        check(session.expectedGeneration() == 7L && session.expectedFingerprint().equals("abc"),
                "session retains optimistic concurrency token");
        System.out.println("Config GUI transaction regression suite passed.");
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
