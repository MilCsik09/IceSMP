package hu.taliann.icesmp.demonhunter;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free behavior regression for the concrete Démonvadász runtime state. */
public final class DemonHunterGameplayRegressionSuite {

    private static int assertions;

    private DemonHunterGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        loadBandsVentAndDecay();
        fragmentsCollectAndMomentum();
        painPoolAndSigilPair();
        cleanupLifecycle();
        loadAndAllowlistSourceContracts();
        System.out.println("Demon Hunter gameplay regression suite passed. assertions=" + assertions);
    }

    private static void loadBandsVentAndDecay() {
        final DemonHunterCombatState state = new DemonHunterCombatState();
        final long t0 = 10_000L;
        check(state.loadBand(t0, 40, 80, 5_000L, 6.0D)
                        == DemonHunterCombatState.LoadBand.STABIL,
                "empty load is stable");
        state.addLoad(39, t0, 5_000L, 6.0D);
        check(state.loadBand(t0, 40, 80, 5_000L, 6.0D)
                        == DemonHunterCombatState.LoadBand.STABIL,
                "39 remains stable");
        state.addLoad(1, t0, 5_000L, 6.0D);
        check(state.loadBand(t0, 40, 80, 5_000L, 6.0D)
                        == DemonHunterCombatState.LoadBand.ATHEVULT,
                "40 enters the heated band");
        state.addLoad(40, t0, 5_000L, 6.0D);
        check(state.loadBand(t0, 40, 80, 5_000L, 6.0D)
                        == DemonHunterCombatState.LoadBand.TULTERHELT,
                "80 enters the overloaded band");
        check(state.addLoad(500, t0, 5_000L, 6.0D) == 100, "load is bounded at 100");

        check(state.ventLoad(40) == 40, "consume_magic vents a bounded chunk");
        check(state.load(t0, 5_000L, 6.0D) == 60, "the vent is exact");
        check(state.load(t0 + 5_000L, 5_000L, 6.0D) == 60,
                "load holds inside the grace window");
        check(state.load(t0 + 10_000L, 5_000L, 6.0D) == 30,
                "idle load decays lazily — the risk is player-controlled");
    }

    private static void fragmentsCollectAndMomentum() {
        final DemonHunterCombatState state = new DemonHunterCombatState();
        final long t0 = 50_000L;
        check(state.addFragments(1, 5) == 1, "damaging casts produce a lightweight fragment");
        state.addFragments(2, 5);
        check(state.addFragments(9, 5) == 5, "fragments are bounded at the maximum");
        check(state.collectFragments() == 5, "a mobility cast collects everything at once");
        check(state.fragments() == 0, "the collect empties the counter");
        check(state.collectFragments() == 0, "an empty counter collects nothing");

        state.armMomentum(1, t0, 4_000L);
        check(state.isMomentumArmed(t0 + 3_999L), "momentum stays armed inside its window");
        check(state.consumeMomentum(t0 + 1_000L), "an empowered cast consumes a charge");
        check(!state.isMomentumArmed(t0 + 1_001L), "a single charge is spent by one cast");

        state.armMomentum(2, t0 + 10_000L, 4_000L);
        check(state.consumeMomentum(t0 + 11_000L) && state.consumeMomentum(t0 + 12_000L),
                "the level-50 doctrine grants two charges");
        check(!state.consumeMomentum(t0 + 12_500L), "the third cast finds nothing");
        state.armMomentum(1, t0 + 20_000L, 4_000L);
        check(!state.consumeMomentum(t0 + 24_001L), "an expired momentum never fires");
    }

    private static void painPoolAndSigilPair() {
        final DemonHunterCombatState state = new DemonHunterCombatState();
        final long t0 = 100_000L;
        check(state.addPain(50) == 50, "taken damage builds the pain");
        check(state.addPain(1000) == 100, "pain is bounded at 100");
        check(state.spendPain(60), "pain funds the sigils");
        check(!state.spendPain(41), "pain cannot overspend");
        check(state.pain() == 40, "a failed spend is side-effect free");

        check(state.armSigil(t0, 8_000L), "the first Sigil arms");
        check(state.armSigil(t0 + 1_000L, 8_000L), "the second Sigil arms");
        check(!state.armSigil(t0 + 2_000L, 8_000L),
                "a third concurrent Sigil is rejected — at most two stand");
        check(state.armedSigils(t0 + 3_000L) == 2, "both Sigils count while live");
        check(state.armSigil(t0 + 9_500L, 8_000L),
                "an expired slot is reusable for a new Sigil");
    }

    private static void cleanupLifecycle() {
        final DemonHunterCombatState state = new DemonHunterCombatState();
        final long t0 = 200_000L;
        state.addLoad(70, t0, 5_000L, 6.0D);
        state.addFragments(4, 5);
        state.armMomentum(1, t0, 4_000L);
        state.addPain(60);
        state.armSigil(t0, 8_000L);
        state.clearSpecializationState();
        check(state.load(t0, 5_000L, 6.0D) == 0, "spec switch clears the load");
        check(state.fragments() == 0, "spec switch clears the fragments");
        check(!state.isMomentumArmed(t0), "spec switch clears the momentum");
        check(state.pain() == 0, "spec switch clears the pain");
        check(state.armedSigils(t0) == 0, "spec switch clears the sigils");
    }

    private static void loadAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"demon_hunter\""),
                "the gameplay-v2 allowlist still admits this completed slice");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/demonhunter/DemonHunterGameplayService.java"));
        check(service.contains("overload-taken-penalty-percent"),
                "the overload band trades power for readable incoming-damage fragility");
        check(service.contains("consume_magic") && service.contains("ventLoad"),
                "the load is player-ventable — controlled risk, not random punishment");
        check(!service.contains("dropItem") && !service.contains("spawnEntity"),
                "Lélektöredék is a lightweight counter — never item/entity spam");
        check(!service.contains("getNearbyEntities") && !service.contains("runAtFixedRate"),
                "no proximity scans or repeating tasks in the demon hunter runtime");

        final String gameplayConfig = Files.readString(Path.of(
                "src/main/resources/config/class-gameplay.yml"));
        check(gameplayConfig.contains("classes: []"),
                "the melee-catalyst compatibility list stays empty once every class casts through its Lélekkapocs");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
