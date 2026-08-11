package hu.taliann.icesmp.priest;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free behavior regression for the concrete Pap runtime state. */
public final class PriestGameplayRegressionSuite {

    private static int assertions;

    private PriestGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        litanyVersesReciteAndReset();
        atonementConversionGuardIsNonReentrant();
        shieldWebAbsorbsWhatItHas();
        marrowCondensesIntoOssuary();
        madnessThresholdAndVent();
        cleanupLifecycle();
        guardAndAllowlistSourceContracts();
        System.out.println("Priest gameplay regression suite passed. assertions=" + assertions);
    }

    private static void litanyVersesReciteAndReset() {
        final PriestCombatState state = new PriestCombatState();
        final long t0 = 10_000L;
        check(state.litanyOrDefault(PriestCombatState.Litany.VIGASZ)
                        == PriestCombatState.Litany.VIGASZ,
                "without a choice the Litánia falls back to the spec role");
        state.chooseLitany(PriestCombatState.Litany.OSTOR);
        check(state.litanyOrDefault(PriestCombatState.Litany.VIGASZ)
                        == PriestCombatState.Litany.OSTOR,
                "an explicit prayer overrides the spec default");

        check(state.addVerse(3) == 1, "a matching deed speaks one verse");
        check(!state.recite(3, t0, 6_000L), "an unfinished prayer cannot be recited");
        check(state.verses() == 1, "a refused recitation is side-effect free");
        state.addVerse(3);
        check(state.addVerse(3) == 3, "the verse count stops at the required count");
        check(state.addVerse(3) == 3, "extra deeds cannot overfill the prayer");
        check(state.recite(3, t0, 6_000L), "the full verse count recites the prayer");
        check(state.verses() == 0,
                "reciting restarts the counter — the payoff is re-earned, never held");
        check(state.isRecited(t0 + 5_999L), "the recitation window stays open");
        check(!state.isRecited(t0 + 6_001L), "the recitation window closes on its own");

        state.chooseLitany(PriestCombatState.Litany.CSEND);
        check(state.verses() == 0, "switching prayer starts the new one from the first verse");
    }

    private static void atonementConversionGuardIsNonReentrant() {
        final PriestCombatState state = new PriestCombatState();
        final long t0 = 50_000L;
        check(!state.isAtonementActive(t0), "Engesztelés is not permanently on");
        state.armAtonement(t0, 8_000L);
        check(state.isAtonementActive(t0 + 7_999L), "Engesztelés holds for its window");
        check(!state.isAtonementActive(t0 + 8_001L), "Engesztelés expires on its own");

        check(state.beginConversion(), "the first conversion may start");
        check(state.isConverting(), "the guard reports the conversion in flight");
        check(!state.beginConversion(),
                "a nested conversion is refused — a heal can never feed itself");
        state.endConversion();
        check(!state.isConverting(), "the guard is released");
        check(state.beginConversion(), "a later, non-nested conversion may start again");
        state.endConversion();
    }

    private static void shieldWebAbsorbsWhatItHas() {
        final PriestCombatState state = new PriestCombatState();
        check(state.addShield(8, 20) == 8, "the conversion feeds the shield web");
        check(state.addShield(1000, 20) == 20, "the shield web is bounded by its cap");
        check(state.absorb(6) == 6, "the web absorbs an incoming hit");
        check(state.shield() == 14, "the absorbed amount leaves the pool");
        check(state.absorb(1000) == 14, "the web can never absorb more than it holds");
        check(state.shield() == 0, "a drained web is empty, never negative");
        check(state.absorb(5) == 0, "an empty web absorbs nothing");
    }

    private static void marrowCondensesIntoOssuary() {
        final PriestCombatState state = new PriestCombatState();
        check(state.addMarrow(20, 100) == 20, "a controlled sacrifice yields Velő");
        check(!state.condenseOssuary(40, 2), "too little Velő condenses nothing");
        check(state.marrow() == 20, "a failed condensation is side-effect free");
        state.addMarrow(20, 100);
        check(state.condenseOssuary(40, 2), "enough Velő condenses an Osszárium charge");
        check(state.marrow() == 0 && state.ossuary() == 1, "the charge is paid for in Velő");

        state.addMarrow(1000, 100);
        check(state.marrow() == 100, "Velő is bounded at its maximum");
        check(state.condenseOssuary(40, 2), "a second charge fits under the maximum");
        check(!state.condenseOssuary(40, 2), "the Osszárium cannot exceed its maximum");
        check(state.marrow() == 60, "a refused condensation does not spend the Velő");
        check(state.consumeOssuary() && state.consumeOssuary(), "dark mending burns the charges");
        check(!state.consumeOssuary(), "an empty Osszárium has nothing to burn");
    }

    private static void madnessThresholdAndVent() {
        final PriestCombatState state = new PriestCombatState();
        final long t0 = 100_000L;
        check(state.addMadness(12, t0, 5_000L, 5.0D) == 12, "a shadow cast builds Őrület");
        check(!state.isBeyondThreshold(60, t0, 5_000L, 5.0D),
                "below the Küszöb nothing is owed");
        state.addMadness(48, t0, 5_000L, 5.0D);
        check(state.isBeyondThreshold(60, t0, 5_000L, 5.0D),
                "reaching the Küszöb enters the dangerous, empowered state");
        check(state.addMadness(1000, t0, 5_000L, 5.0D) == 100, "Őrület is bounded at 100");
        check(state.ventMadness(40) == 40, "dispersion is a deliberate vent");
        check(state.madness(t0, 5_000L, 5.0D) == 60, "the vent is exact");
        check(state.ventMadness(1000) == 60, "a vent can never take more than is there");
        check(state.madness(t0, 5_000L, 5.0D) == 0, "a full vent empties the meter");

        state.addMadness(80, t0, 5_000L, 5.0D);
        check(state.madness(t0 + 4_999L, 5_000L, 5.0D) == 80,
                "Őrület holds inside the grace window");
        check(state.madness(t0 + 10_000L, 5_000L, 5.0D) == 55,
                "idle Őrület decays lazily back under the Küszöb");
    }

    private static void cleanupLifecycle() {
        final PriestCombatState state = new PriestCombatState();
        final long t0 = 200_000L;
        state.chooseLitany(PriestCombatState.Litany.OSTOR);
        state.addVerse(3);
        state.armAtonement(t0, 8_000L);
        state.addShield(10, 20);
        state.beginConversion();
        state.addMarrow(40, 100);
        state.condenseOssuary(40, 2);
        state.addMadness(70, t0, 5_000L, 5.0D);
        state.clearSpecializationState();
        check(state.verses() == 0, "spec switch clears the verses");
        check(!state.isAtonementActive(t0) && state.shield() == 0,
                "spec switch clears Engesztelés and the shield web");
        check(!state.isConverting(), "spec switch releases the conversion guard");
        check(state.marrow() == 0 && state.ossuary() == 0,
                "spec switch clears the Velő and the Osszárium");
        check(state.madness(t0, 5_000L, 5.0D) == 0, "spec switch clears the Őrület");
        check(state.litanyOrDefault(PriestCombatState.Litany.VIGASZ)
                        == PriestCombatState.Litany.OSTOR,
                "the chosen prayer survives a spec switch — class-level identity");
        state.clearAll();
        check(state.litanyOrDefault(PriestCombatState.Litany.CSEND)
                        == PriestCombatState.Litany.CSEND,
                "death/logout resets the prayer to the role default");
    }

    private static void guardAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"priest\""),
                "the gameplay-v2 allowlist still admits this completed slice");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/priest/PriestGameplayService.java"));
        check(service.contains("if (!state.beginConversion()) return;")
                        && service.contains("} finally {")
                        && service.contains("state.endConversion();"),
                "the Engesztelés conversion runs behind the explicit guard and always releases it");
        check(!service.contains("runAtFixedRate") && !service.contains("getNearbyEntities"),
                "no repeating tasks or proximity scans in the priest runtime");
        check(service.contains("Math.max(floor, player.getHealth() - strain)"),
                "the Küszöb strain is floored — it can never be the killing blow");
        check(!service.contains("Math.random") && !service.contains("ThreadLocalRandom"),
                "no random self-harm: the Árnyék risk is entirely deterministic");

        final String gameplayConfig = Files.readString(Path.of(
                "src/main/resources/config/class-gameplay.yml"));
        final int atonementStart = gameplayConfig.indexOf("      atonement-spells:");
        check(atonementStart > 0, "the Engesztelés source list is declared in live config");
        final String atonement = gameplayConfig.substring(atonementStart,
                Math.min(gameplayConfig.length(), atonementStart + 200));
        for (final String heal : new String[]{"heal", "renew", "power_word_radiance",
                "evangelism", "rapture"}) {
            check(!atonement.contains("- " + heal + "\n"),
                    "the Engesztelés source list carries no pure heal (" + heal + ")");
        }
        check(gameplayConfig.contains("min-health-ratio: 0.35")
                        && gameplayConfig.contains("min-health-ratio: 0.25"),
                "both health floors (sacrifice, Küszöb) are admin-tunable live config");

        final String manager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/SpecializationManager.java"));
        for (final String trial : new String[]{"priest_discipline_trial",
                "priest_bone_priest_trial", "priest_shadow_trial"}) {
            check(manager.contains(trial), "the capstone trial contract " + trial + " is registered");
        }

        final String dark = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/DarkSpecializationPolicy.java"));
        check(dark.contains("\"bone_priest\""),
                "Csontpap stays on the EXISTING DARK seal/gate system — no second gate mechanism");

        final String command = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/SpecCommand.java"));
        check(command.contains("\"ima\"") && command.contains("choosePriestLitany"),
                "the Litánia is chosen through /spec ima with tab-complete");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
