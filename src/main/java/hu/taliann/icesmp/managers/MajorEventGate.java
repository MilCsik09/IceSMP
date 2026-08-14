package hu.taliann.icesmp.managers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Bounded orchestration gate for large PvE events. */
public final class MajorEventGate {

    private static final List<String> DEFAULT_MAJORS =
            List.of("world-boss", "invasion", "wild-hunt", "escort", "cultists", "prologue");
    private static volatile MajorEventGate active;

    private final ConfigManager configManager;
    private final Map<String, BooleanSupplier> activeChecks = new ConcurrentHashMap<>();
    /** First instant at which the supplier continuously reported active. */
    private final Map<String, Long> activeSince = new ConcurrentHashMap<>();

    public MajorEventGate(final ConfigManager configManager) {
        this.configManager = configManager;
        active = this;
    }

    /** Runtime bridge for late-installed orchestration clients. */
    public static MajorEventGate current() {
        return active;
    }

    public void register(final String eventKey, final BooleanSupplier activeCheck) {
        activeChecks.put(eventKey, activeCheck);
        activeSince.remove(eventKey);
    }

    /**
     * A lost lifecycle callback may leave one manager's volatile flag stuck. Such a stale supplier
     * must not deadlock every other event forever: after the configurable watchdog limit it is
     * ignored until it reports inactive once. Normal event lifetimes are well below the 60-minute
     * default.
     */
    public boolean mayStartNaturally(final String eventKey) {
        if (!configManager.getBoolean("world-events.orchestration.enabled", true)) {
            return true;
        }
        final List<String> configured = configManager.getStringList(
                "world-events.orchestration.major-events");
        final List<String> majors = configured.isEmpty() ? DEFAULT_MAJORS : configured;
        if (!majors.contains(eventKey)) {
            return true;
        }
        final long now = System.currentTimeMillis();
        final long watchdogMillis = Math.max(5L, configManager.getLong(
                "world-events.orchestration.max-active-minutes", 60L)) * 60_000L;
        for (final String other : majors) {
            if (other.equals(eventKey)) {
                continue;
            }
            final BooleanSupplier check = activeChecks.get(other);
            if (check == null) {
                activeSince.remove(other);
                continue;
            }
            final boolean activeNow;
            try {
                activeNow = check.getAsBoolean();
            } catch (final RuntimeException failure) {
                activeSince.remove(other);
                continue;
            }
            if (!activeNow) {
                activeSince.remove(other);
                continue;
            }
            final long since = activeSince.computeIfAbsent(other, ignored -> now);
            if (now - since <= watchdogMillis) {
                return false;
            }
        }
        return true;
    }
}
