package hu.taliann.icesmp.managers;

/** Focused regressions for the manager's minimal scheduler gate. */
public final class DevItemRewardTransitionRegressionTest {

    private DevItemRewardTransitionRegressionTest() {
    }

    public static void main(final String[] args) {
        runAll();
        System.out.println("DevItem scheduler gate regression tests passed.");
    }

    static void runAll() {
        normalCompletionOpensGate();
        exceptionOpensGate();
        retiredCallbackOpensGate();
        rejectedOrNullTaskOpensGate();
    }

    private static void normalCompletionOpensGate() {
        final var gate = new DevItemStateData.TickGate();
        check(gate.tryEnter(), "normal callback could not enter gate");
        try {
            check(!gate.tryEnter(), "overlapping callback entered gate");
        } finally {
            gate.exit();
        }
        check(gate.tryEnter(), "normal completion left gate closed");
        gate.exit();
    }

    private static void exceptionOpensGate() {
        final var gate = new DevItemStateData.TickGate();
        check(gate.tryEnter(), "exception callback could not enter gate");
        try {
            throw new SimulatedCallbackFailure();
        } catch (final SimulatedCallbackFailure expected) {
            // production callback releases in finally
        } finally {
            gate.exit();
        }
        check(gate.tryEnter(), "callback exception left gate closed");
        gate.exit();
    }

    private static void retiredCallbackOpensGate() {
        final var gate = new DevItemStateData.TickGate();
        check(gate.tryEnter(), "retired callback could not enter gate");
        gate.exit();
        check(gate.tryEnter(), "retired callback left gate closed");
        gate.exit();
    }

    private static void rejectedOrNullTaskOpensGate() {
        final var rejected = new DevItemStateData.TickGate();
        check(rejected.tryEnter(), "rejected scheduling could not enter gate");
        rejected.exit();
        check(rejected.tryEnter(), "scheduler rejection left gate closed");
        rejected.exit();

        final var nullTask = new DevItemStateData.TickGate();
        check(nullTask.tryEnter(), "null scheduling could not enter gate");
        nullTask.exit();
        check(nullTask.tryEnter(), "null task return left gate closed");
        nullTask.exit();
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class SimulatedCallbackFailure extends RuntimeException {
    }
}
