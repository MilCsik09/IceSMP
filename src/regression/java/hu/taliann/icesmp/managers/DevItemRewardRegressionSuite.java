package hu.taliann.icesmp.managers;

/** Single Gradle/Python entry point for all dependency-free DEV reward regressions. */
public final class DevItemRewardRegressionSuite {

    private DevItemRewardRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        DevItemStateDataRegressionTest.runAll();
        DevItemRewardTransitionRegressionTest.runAll();
        System.out.println("DEV-item reward regression suite passed.");
    }
}
