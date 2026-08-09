package hu.taliann.icesmp.paladin;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free behavior regression for the concrete Paplovag runtime state. */
public final class PaladinGameplayRegressionSuite {

    private static int assertions;

    private PaladinGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        oathChoiceAndConviction();
        judgmentMarksAndVerdict();
        shieldChargeBoundsAndSpend();
        cleanupLifecycle();
        oathAndAllowlistSourceContracts();
        System.out.println("Paladin gameplay regression suite passed. assertions=" + assertions);
    }

    private static void oathChoiceAndConviction() {
        final PaladinCombatState state = new PaladinCombatState();
        check(state.oathOrDefault(PaladinCombatState.Oath.IRGALOM)
                        == PaladinCombatState.Oath.IRGALOM,
                "without a choice the Eskü falls back to the spec role");
        state.chooseOath(PaladinCombatState.Oath.ITELET);
        check(state.oathOrDefault(PaladinCombatState.Oath.IRGALOM)
                        == PaladinCombatState.Oath.ITELET,
                "an explicit Eskü overrides the spec default");
        state.clearSpecializationState();
        check(state.oathOrDefault(PaladinCombatState.Oath.IRGALOM)
                        == PaladinCombatState.Oath.ITELET,
                "the session Eskü survives a spec switch — class-level identity");
        state.clearAll();
        check(state.oathOrDefault(PaladinCombatState.Oath.OLTALMAZAS)
                        == PaladinCombatState.Oath.OLTALMAZAS,
                "death/logout resets the Eskü to the role default");

        final long t0 = 10_000L;
        check(state.addConviction(8, t0, 6_000L, 5.0D) == 8,
                "an in-role deed builds conviction");
        check(state.addConviction(1000, t0, 6_000L, 5.0D) == 100,
                "conviction is bounded at 100");
        check(state.conviction(t0 + 5_999L, 6_000L, 5.0D) == 100,
                "conviction holds inside the grace window");
        check(state.conviction(t0 + 10_000L, 6_000L, 5.0D) == 80,
                "idle conviction decays lazily");
    }

    private static void judgmentMarksAndVerdict() {
        final PaladinCombatState state = new PaladinCombatState();
        final long t0 = 50_000L;
        check(state.lightMark(PaladinCombatState.JudgmentMark.BUN, t0, 8_000L) == 1,
                "the first mark lights");
        check(state.lightMark(PaladinCombatState.JudgmentMark.BUN, t0 + 1_000L, 8_000L) == 1,
                "the same mark does not stack");
        state.lightMark(PaladinCombatState.JudgmentMark.DAC, t0 + 2_000L, 8_000L);
        check(!state.isVerdictArmed(t0 + 3_000L, 8_000L),
                "two marks do not arm the Verdict");
        state.lightMark(PaladinCombatState.JudgmentMark.KARHOZAT, t0 + 3_000L, 8_000L);
        check(state.isVerdictArmed(t0 + 4_000L, 8_000L),
                "Bűn + Dac + Kárhozat arms the Verdict");
        check(state.consumeVerdict(t0 + 4_000L, 8_000L), "the finisher consumes the Verdict");
        check(state.markCount(t0 + 4_100L, 8_000L) == 0, "the Verdict clears all marks");
        check(!state.consumeVerdict(t0 + 4_200L, 8_000L), "the Verdict is strictly single-use");

        state.lightMark(PaladinCombatState.JudgmentMark.BUN, t0 + 10_000L, 8_000L);
        check(state.markCount(t0 + 19_000L, 8_000L) == 0,
                "a stale window clears the marks");
    }

    private static void shieldChargeBoundsAndSpend() {
        final PaladinCombatState state = new PaladinCombatState();
        check(state.addShieldCharge(40) == 40, "defensive play builds the charge");
        check(state.addShieldCharge(1000) == 100, "the charge is bounded at 100");
        check(state.spendShieldCharge(80), "the charge funds the Megszentelt Föld");
        check(state.shieldCharge() == 20, "the spend is exact");
        check(!state.spendShieldCharge(21), "the charge cannot overspend");
        check(state.shieldCharge() == 20, "a failed spend is side-effect free");
    }

    private static void cleanupLifecycle() {
        final PaladinCombatState state = new PaladinCombatState();
        final long t0 = 100_000L;
        state.addConviction(60, t0, 6_000L, 5.0D);
        state.lightMark(PaladinCombatState.JudgmentMark.BUN, t0, 8_000L);
        state.addShieldCharge(50);
        state.clearSpecializationState();
        check(state.conviction(t0, 6_000L, 5.0D) == 0, "spec switch clears conviction");
        check(state.markCount(t0, 8_000L) == 0, "spec switch clears the marks");
        check(state.shieldCharge() == 0, "spec switch clears the charge");
    }

    private static void oathAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"paladin\""),
                "the gameplay-v2 allowlist still admits this completed slice");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/paladin/PaladinGameplayService.java"));
        check(service.contains("beaconTargets.put(paladinId,")
                        && !service.contains("List<BeaconTarget>"),
                "the Fényjelző is a single beacon target — no raid-wide passive heal");
        check(!service.contains("runAtFixedRate"),
                "no repeating tasks in the paladin runtime");
        check(service.contains("member.getScheduler().run(plugin,"),
                "Megszentelt Föld ally protection hops to each ally's scheduler");
        check(!service.contains("guardiansByTarget"),
                "no Warrior-Guardian-style target-bound reverse index");

        final String command = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/SpecCommand.java"));
        check(command.contains("\"esku\"") && command.contains("choosePaladinOath"),
                "the Eskü is chosen through /spec esku with tab-complete");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
