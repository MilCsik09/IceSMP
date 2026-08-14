package hu.taliann.icesmp.lifecycle;

import hu.taliann.icesmp.prologue.PrologueRegressionSuite;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source-contract regressions for Folia-safe, idempotent disable cleanup. */
public final class LifecycleShutdownRegressionSuite {

    private LifecycleShutdownRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        transientRemovalNeverSchedulesForDisabledPlugin();
        escortShutdownClearsStateBeforeCleanup();
        bossBarCleanupDoesNotScheduleAfterDisable();
        PrologueRegressionSuite.main(args);
        System.out.println("Lifecycle shutdown regression suite passed.");
    }

    private static void transientRemovalNeverSchedulesForDisabledPlugin() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/utils/TransientEntities.java"));
        final String method = between(source,
                "public static void removeById", "public static void markGone");
        final int enabledGuard = method.indexOf("if (!owner.isEnabled())");
        final int schedulerRun = method.indexOf("handle.scheduler.run");
        check(enabledGuard >= 0 && schedulerRun > enabledGuard,
                "transient cleanup may still register an entity task after plugin disable");
        check(method.contains("catch (final IllegalPluginAccessException")
                        && method.contains("retire(handle.id, handle.generation)"),
                "disable race is not retired without an illegal scheduler retry");
    }

    private static void escortShutdownClearsStateBeforeCleanup() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/EscortManager.java"));
        final String method = between(source, "public void shutdown()", "private boolean start(");
        final int clearId = method.indexOf("convoyId = null");
        final int remove = method.indexOf("TransientEntities.removeById");
        check(clearId >= 0 && remove > clearId,
                "escort shutdown is not idempotent before entity cleanup begins");
        check(method.contains("destination = null")
                        && method.contains("expiresAt = 0L")
                        && method.contains("schedule.release()"),
                "escort shutdown leaves live event state behind");
    }

    private static void bossBarCleanupDoesNotScheduleAfterDisable() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/EscortManager.java"));
        final String method = between(source, "private void hideBarFromAll()", "private void clearWaves()");
        check(method.indexOf("if (!plugin.isEnabled())") >= 0
                        && method.indexOf("player.getScheduler().run") > method.indexOf("if (!plugin.isEnabled())"),
                "escort bossbar cleanup may schedule after disable");
        check(method.contains("catch (final IllegalPluginAccessException"),
                "escort bossbar cleanup does not handle the disable race");
    }

    private static String between(final String source, final String start, final String end) {
        final int from = source.indexOf(start);
        final int to = from < 0 ? -1 : source.indexOf(end, from + start.length());
        check(from >= 0 && to > from, "missing source section: " + start);
        return source.substring(from, to);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
