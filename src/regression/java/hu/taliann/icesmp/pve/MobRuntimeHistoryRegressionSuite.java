package hu.taliann.icesmp.pve;

import java.util.*;

/** Registry churn cannot exhaust memory, reset an active cooldown or rearm consumed threshold content. */
public final class MobRuntimeHistoryRegressionSuite {
    private static int assertions;
    public static void main(String[] args) {
        final var history = new MobRuntimeHistory();
        check(history.begin("guard", 0, 100), "first admission");
        check(!history.begin("guard", 1, 200) && history.readyAt("guard") == 100, "duplicate cannot extend or replace cooldown");
        final var detached = history.view();
        check(history.consume("phase") && !history.consume("phase"), "threshold exactly once");
        history.advance(100);
        check(history.readyAt("guard") == 0 && history.consumed("phase"), "expiry releases only cooldown history");
        check(detached.cooldowns().get("guard") == 100 && detached.consumedThresholdCount() == 0, "view is detached");
        rejected(() -> detached.cooldowns().put("forged", 200L));
        rejected(() -> history.advance(99));
        rejected(() -> history.begin("guard", 100, 100));
        check(history.begin("GUARD", 100, 200) && history.readyAt("guard") == 200, "canonical content ID normalization");
        history.advance(200);
        for (int index = 0; index < MobRuntimeHistory.LIMIT; index++)
            check(history.begin("reload_" + index, 200, 400), "distinct reload history accepted below cap");
        final var full = history.view();
        check(full.cooldowns().size() == 24 && full.omittedCooldowns() == 488 && full.cooldownCount() == 512, "bounded honest inspection");
        check(!history.begin("new_content", 200, 500), "full ledger refuses instead of evicting live cooldowns");
        check(!history.begin("reload_0", 399, 500) && history.readyAt("reload_0") == 400, "churn cannot reset hidden cooldown");
        history.advance(400);
        check(history.begin("new_content", 400, 500), "expired history reopens capacity without a registry reset");
        check(full.cooldownCount() == 512 && full.cooldowns().values().stream().allMatch(value -> value == 400), "retained view unaffected by pruning");
        for (int index = 1; index < MobRuntimeHistory.LIMIT; index++) check(history.consume("phase_" + index), "threshold capacity");
        check(!history.consume("overflow_phase") && !history.consume("phase") && history.consumed("phase"), "threshold overflow cannot rearm history");
        history.advance(Long.MAX_VALUE);
        check(history.view().cooldownCount() == 0 && history.view().consumedThresholdCount() == 512, "thresholds survive all cooldown expiry");
        final var longNames = new MobRuntimeHistory();
        for (int i = 0; i < 128; i++) check(longNames.begin("x".repeat(92) + String.format("%04d", i), 0, Long.MAX_VALUE), "maximum-sized ID");
        check(longNames.view().cooldowns().toString().length() < 3000, "native diagnostic leaves room under the text codec limit");
        rejected(() -> new MobRuntimeHistory.View(Map.of("bad", -1L), 1, 0));
        rejected(() -> new MobRuntimeHistory.View(Map.of(), 513, 0));
        System.out.println("Mob runtime history passed: " + assertions + " assertions; content churn, live cooldown preservation, bounded immutable inspection, expiry and threshold retention.");
    }
    private static void rejected(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | UnsupportedOperationException expected) { rejected = true; }
        check(rejected, "invalid or mutable history accepted");
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
