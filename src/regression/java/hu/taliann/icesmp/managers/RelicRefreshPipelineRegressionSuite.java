package hu.taliann.icesmp.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Regression checks for per-slot relic refresh failure isolation and diagnostics. */
public final class RelicRefreshPipelineRegressionSuite {

    private RelicRefreshPipelineRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        repeatedEntriesRefreshNormally();
        brokenEntryDoesNotAbortFollowingEntries();
        identificationFailureIsAlsoIsolated();
        managerLogsRequiredNonSensitiveContext();
        System.out.println("Relic refresh pipeline regression suite passed.");
    }

    private static void repeatedEntriesRefreshNormally() {
        final String[] entries = {"metelytepo", "metelytepo", "other"};
        final List<String> refreshed = new ArrayList<>();
        final int changed = RelicRefreshPipeline.refresh(
                entries,
                value -> "other".equals(value) ? null : value,
                (value, definition) -> refreshed.add(value),
                (index, value, definition, exception) -> {
                    throw new AssertionError("valid entry failed", exception);
                }
        );
        check(changed == 2 && refreshed.equals(List.of("metelytepo", "metelytepo")),
                "valid inventory entries were not refreshed exactly once each");
    }

    private static void brokenEntryDoesNotAbortFollowingEntries() {
        final String[] entries = {"ok-1", "broken", "ok-2"};
        final List<String> refreshed = new ArrayList<>();
        final List<Integer> failedSlots = new ArrayList<>();
        final int changed = RelicRefreshPipeline.refresh(
                entries,
                value -> value,
                (value, definition) -> {
                    if ("broken".equals(value)) {
                        throw new IllegalArgumentException("malformed relic");
                    }
                    refreshed.add(value);
                },
                (index, value, definition, exception) -> failedSlots.add(index)
        );
        check(changed == 2 && refreshed.equals(List.of("ok-1", "ok-2")),
                "one malformed item aborted the remaining inventory refresh");
        check(failedSlots.equals(List.of(1)),
                "malformed item failure was not attributed to its exact slot");
    }

    private static void identificationFailureIsAlsoIsolated() {
        final String[] entries = {"bad-meta", "ok"};
        final List<String> refreshed = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final int changed = RelicRefreshPipeline.refresh(
                entries,
                value -> {
                    if ("bad-meta".equals(value)) {
                        throw new IllegalStateException("corrupt PDC");
                    }
                    return value;
                },
                (value, definition) -> refreshed.add(value),
                (index, value, definition, exception) ->
                        failures.add(index + ":" + definition + ":" + exception.getClass().getSimpleName())
        );
        check(changed == 1 && refreshed.equals(List.of("ok")),
                "identification failure aborted a later valid item");
        check(failures.equals(List.of("0:null:IllegalStateException")),
                "identification failure context was lost");
    }

    private static void managerLogsRequiredNonSensitiveContext() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/RelicManager.java"));
        check(source.contains("playerUuid=")
                        && source.contains("itemType=")
                        && source.contains("relicId=")
                        && source.contains("slot="),
                "relic refresh log no longer carries the required context");
        check(source.contains("REFRESH_FAILURE_LOG_INTERVAL_MILLIS")
                        && source.contains("refreshFailureLogTimes"),
                "persistent malformed items would flood every join with the same stack trace");
        check(source.contains("REFRESH_FAILURE_LOG_CACHE_LIMIT")
                        && source.contains("Map.Entry.comparingByValue()"),
                "relic refresh failure throttling cache is no longer bounded");
        check(!source.contains("player.getName()")
                        || source.indexOf("player.getName()") < source.indexOf("refreshPlayerRelicItems"),
                "relic refresh diagnostics should use UUID, not player name");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
