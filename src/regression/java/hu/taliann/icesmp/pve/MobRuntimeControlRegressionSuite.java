package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.pve.MobRuntimeControlLedger.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class MobRuntimeControlRegressionSuite {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        assertions++;
        try { action.run(); throw new AssertionError("Expected refusal"); } catch (IllegalArgumentException | IllegalStateException expected) { }
    }
    public static void main(String[] args) {
        final var ledger = new MobRuntimeControlLedger(); final var epoch = new AtomicLong(); final var calls = new AtomicInteger();
        final var before = ledger.view(epoch.get()); final var request = new Request(UUID.randomUUID(), Kind.FORCE_ABILITY, "gatebreaker_charge", before.stamp());
        final var accepted = ledger.execute(request, epoch::get, () -> { calls.incrementAndGet(); epoch.incrementAndGet(); return true; }, () -> 10);
        check(accepted.request().equals(request) && !accepted.afterStamp().equals(before.stamp()) && ledger.view(epoch.get()).accepted().size() == 1, "accepted native operation evidence missing");
        epoch.incrementAndGet();
        check(ledger.execute(request, epoch::get, () -> { throw new AssertionError("duplicate cast"); }, () -> 20).equals(accepted) && calls.get() == 1, "duplicate cast replayed after natural runtime drift");
        rejects(() -> ledger.execute(new Request(request.operationId(), Kind.REFRESH, "", request.expectedStamp()), epoch::get, () -> true, () -> 20));
        rejects(() -> ledger.execute(new Request(UUID.randomUUID(), Kind.REFRESH, "", before.stamp()), epoch::get, () -> { throw new AssertionError("stale callback entered"); }, () -> 20));
        final var denied = new Request(UUID.randomUUID(), Kind.REFRESH, "", ledger.view(epoch.get()).stamp());
        rejects(() -> ledger.execute(denied, epoch::get, () -> false, () -> 20));
        check(!ledger.view(epoch.get()).stamp().equals(denied.expectedStamp()) && !ledger.view(epoch.get()).accepted().containsKey(denied.operationId()), "entered refusal falsely proves untouched runtime");
        final var failed = new Request(UUID.randomUUID(), Kind.REFRESH, "", ledger.view(epoch.get()).stamp());
        rejects(() -> ledger.execute(failed, epoch::get, () -> { calls.incrementAndGet(); throw new IllegalStateException("after native effect"); }, () -> 20));
        check(!ledger.view(epoch.get()).stamp().equals(failed.expectedStamp()) && !ledger.view(epoch.get()).accepted().containsKey(failed.operationId()), "failed callback falsely proves BEFORE or accepted outcome");
        final var reentrant = new Request(UUID.randomUUID(), Kind.REFRESH, "", ledger.view(epoch.get()).stamp());
        ledger.execute(reentrant, epoch::get, () -> {
            rejects(() -> ledger.execute(new Request(UUID.randomUUID(), Kind.REFRESH, "", ledger.view(epoch.get()).stamp()), epoch::get, () -> { throw new AssertionError("nested control"); }, () -> 20));
            return true;
        }, () -> 20);
        for (int i = 0; i < 40; i++) ledger.execute(new Request(UUID.randomUUID(), Kind.REFRESH, "", ledger.view(epoch.get()).stamp()), epoch::get, () -> true, () -> 30);
        final var bounded = ledger.view(epoch.get()); check(bounded.accepted().size() == 32 && !bounded.accepted().containsKey(request.operationId()), "native receipt retention unbounded");
        rejects(() -> ledger.execute(request, epoch::get, () -> { throw new AssertionError("evicted operation replay"); }, () -> 40));
        check(before.accepted().isEmpty(), "detached view changed with runtime");
        try { bounded.accepted().clear(); throw new AssertionError("mutable view"); } catch (UnsupportedOperationException expected) { assertions++; }
        final var restarted = new MobRuntimeControlLedger();
        check(!restarted.view(0).stamp().equals(before.stamp()), "runtime generation survived detach/restart without evidence");
        rejects(() -> restarted.execute(request, () -> 0, () -> { throw new AssertionError("restart replay"); }, () -> 50));
        rejects(() -> new Accepted(request, restarted.view(0).stamp(), 10));
        rejects(() -> new View(restarted.view(0).stamp(), Map.of(request.operationId(), accepted)));
        for (String invalid : List.of("", "detached", UUID.randomUUID() + "/-1/0", UUID.randomUUID() + "/01/0", UUID.randomUUID() + "/9223372036854775808/0")) rejects(() -> MobRuntimeControlLedger.stamp(invalid));
        System.out.println("Mob runtime control passed: " + assertions + " assertions; exact correlation, drift, failed-entry uncertainty, reentrancy, bounded immutable evidence and no eviction/restart replay.");
    }
}
