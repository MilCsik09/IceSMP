package hu.taliann.icesmp.druid;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Dependency-free behavior regression for the concrete Druida runtime state. */
public final class DruidGameplayRegressionSuite {

    private static final UUID PREY = UUID.fromString("00000000-0000-0000-0000-0000000005a1");
    private static final UUID OTHER_PREY = UUID.fromString("00000000-0000-0000-0000-0000000005a2");

    private static int assertions;

    private DruidGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        harmonyBuildsReleasesAndDecays();
        feralComboAndScentTrail();
        lunarBalanceSweepsIntoEclipse();
        ironbarkLayersAndRoots();
        restorationSeedsMustRipen();
        cleanupLifecycle();
        seasonAndAllowlistSourceContracts();
        System.out.println("Druid gameplay regression suite passed. assertions=" + assertions);
    }

    private static void harmonyBuildsReleasesAndDecays() {
        final DruidCombatState state = new DruidCombatState();
        final long t0 = 10_000L;
        check(state.addHarmony(8, t0, 6_000L, 4.0D) == 8, "a nature cast builds harmony");
        check(state.addHarmony(1000, t0, 6_000L, 4.0D) == 100, "harmony is bounded at 100");
        check(state.harmony(t0 + 5_999L, 6_000L, 4.0D) == 100,
                "harmony holds inside the grace window");
        check(state.harmony(t0 + 10_000L, 6_000L, 4.0D) == 84,
                "idle harmony decays lazily");

        final DruidCombatState low = new DruidCombatState();
        low.addHarmony(20, t0, 6_000L, 4.0D);
        check(low.releaseHarmony(30) == 0,
                "below the threshold the shapeshift releases no season");
        check(low.harmony(t0, 6_000L, 4.0D) == 20,
                "a withheld release is side-effect free — the form itself still works");
        low.addHarmony(20, t0, 6_000L, 4.0D);
        check(low.releaseHarmony(30) == 40, "the shapeshift releases the whole pool at once");
        check(low.harmony(t0, 6_000L, 4.0D) == 0, "the release empties the pool");

        check(!low.isAutumnWindowArmed(t0), "no season window without an Ősz shapeshift");
        low.armAutumnWindow(t0, 6_000L);
        check(low.isAutumnWindowArmed(t0 + 5_999L), "the Ősz window stays armed");
        check(!low.isAutumnWindowArmed(t0 + 6_001L), "the Ősz window expires on its own");
    }

    private static void feralComboAndScentTrail() {
        final DruidCombatState state = new DruidCombatState();
        final long t0 = 50_000L;
        check(state.addCombo(1, 5) == 1, "a claw cast builds a combo point");
        state.addCombo(2, 5);
        check(state.addCombo(9, 5) == 5, "combo points are bounded at the maximum");
        check(state.spendAllCombo() == 5, "the finisher spends every point at once");
        check(state.combo() == 0, "the finisher empties the combo bar");
        check(state.spendAllCombo() == 0, "an empty bar spends nothing");

        check(state.scentTarget(t0) == null, "no trail before the first hit");
        state.markScent(PREY, t0, 8_000L);
        check(state.isScentLive(t0 + 7_999L), "the trail stays live inside its window");
        check(PREY.equals(state.scentTarget(t0 + 1_000L)), "the trail names the prey");
        state.markScent(OTHER_PREY, t0 + 2_000L, 8_000L);
        check(OTHER_PREY.equals(state.scentTarget(t0 + 2_100L)),
                "switching prey starts a new trail — staying on one target is what pays");
        check(state.scentTarget(t0 + 11_000L) == null, "a stale trail goes cold");
    }

    private static void lunarBalanceSweepsIntoEclipse() {
        final DruidCombatState state = new DruidCombatState();
        final long t0 = 100_000L;
        check(state.shiftBalance(25) == 25, "a solar cast leans toward Nap");
        check(state.shiftBalance(-50) == -25, "a lunar cast leans toward Hold");
        check(state.shiftBalance(-1000) == -100, "the balance is bounded at the Hold end");
        check(state.shiftBalance(1000) == 100, "the balance is bounded at the Nap end");

        check(!state.isEclipseArmed(t0), "no Eclipse before the sweep completes");
        state.armEclipse(t0, 6_000L);
        state.resetBalance(0);
        check(state.isEclipseArmed(t0 + 5_999L), "the Eclipse window stays open");
        check(state.balance() == 0,
                "the sweep restarts — an Eclipse is earned by swinging, never by camping");
        check(!state.isEclipseArmed(t0 + 6_001L), "the Eclipse closes on its own");
    }

    private static void ironbarkLayersAndRoots() {
        final DruidCombatState state = new DruidCombatState();
        final long t0 = 150_000L;
        check(state.addBarkLayer(3) == 1, "a defensive cast stacks a bark layer");
        state.addBarkLayer(3);
        check(state.addBarkLayer(3) == 3, "layers stop at the maximum");
        check(state.addBarkLayer(3) == 3, "an extra cast cannot exceed the maximum");
        check(state.crackBarkLayer() && state.barkLayers() == 2, "a hit cracks one layer");
        check(state.crackBarkLayer() && state.crackBarkLayer(), "each hit cracks exactly one");
        check(!state.crackBarkLayer(), "with no layers left the bark no longer blunts");

        check(!state.isRootsArmed(t0), "the root net is not permanently on");
        state.armRoots(t0, 6_000L);
        check(state.isRootsArmed(t0 + 5_999L), "the root net holds for its window");
        check(!state.isRootsArmed(t0 + 6_001L), "the root net expires — no permanent aura");
    }

    private static void restorationSeedsMustRipen() {
        final DruidCombatState state = new DruidCombatState();
        final long t0 = 200_000L;
        check(state.plantSeed(t0, 3, 20_000L), "a heal cast plants a seed");
        check(state.seedCount(t0, 20_000L) == 1, "the planted seed is counted");
        check(state.ripeSeedCount(t0, 4_000L, 20_000L) == 0, "a fresh seed is not ripe yet");
        check(state.collectRipeSeeds(t0 + 1_000L, 4_000L, 20_000L) == 0,
                "an early bloom harvests nothing — the heal is preparation-based");
        check(state.seedCount(t0 + 1_000L, 20_000L) == 1,
                "a failed harvest leaves the seed maturing");
        check(state.ripeSeedCount(t0 + 4_000L, 4_000L, 20_000L) == 1, "the seed ripens in time");

        state.plantSeed(t0 + 5_000L, 3, 20_000L);
        state.plantSeed(t0 + 5_000L, 3, 20_000L);
        check(!state.plantSeed(t0 + 5_000L, 3, 20_000L),
                "seeds are bounded — never unlimited world growth");
        check(state.collectRipeSeeds(t0 + 6_000L, 4_000L, 20_000L) == 1,
                "the bloom harvests only what is ripe");
        check(state.seedCount(t0 + 6_000L, 20_000L) == 2,
                "the unripe seeds keep maturing after the bloom");
        check(state.seedCount(t0 + 30_000L, 20_000L) == 0, "forgotten seeds wither");
        check(state.plantSeed(t0 + 30_000L, 3, 20_000L),
                "withered seeds free their slot for a new planting");
    }

    private static void cleanupLifecycle() {
        final DruidCombatState state = new DruidCombatState();
        final long t0 = 300_000L;
        state.addHarmony(60, t0, 6_000L, 4.0D);
        state.armAutumnWindow(t0, 6_000L);
        state.addCombo(3, 5);
        state.markScent(PREY, t0, 8_000L);
        state.shiftBalance(50);
        state.armEclipse(t0, 6_000L);
        state.addBarkLayer(3);
        state.armRoots(t0, 6_000L);
        state.plantSeed(t0, 3, 20_000L);
        state.clearSpecializationState();
        check(state.harmony(t0, 6_000L, 4.0D) == 0, "spec switch clears the harmony");
        check(!state.isAutumnWindowArmed(t0), "spec switch clears the season window");
        check(state.combo() == 0 && state.scentTarget(t0) == null,
                "spec switch clears the combo and the trail");
        check(state.balance() == 0 && !state.isEclipseArmed(t0),
                "spec switch clears the balance and the Eclipse");
        check(state.barkLayers() == 0 && !state.isRootsArmed(t0),
                "spec switch clears the bark and the roots");
        check(state.seedCount(t0, 20_000L) == 0, "spec switch clears the seeds");
    }

    private static void seasonAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"druid\""),
                "the gameplay-v2 allowlist still admits this completed slice");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/druid/DruidGameplayService.java"));
        check(service.contains("DruidFormSpell.activeForm")
                        && !service.contains("class DruidForm"),
                "the Évszak rides the existing shapeshift system — no new form engine");
        check(!service.contains("spawnEntity") && !service.contains("dropItem"),
                "Mag is a pure counter — never a persistent world plant entity");
        check(!service.contains("getNearbyEntities") && !service.contains("runAtFixedRate"),
                "no proximity scans or repeating tasks in the druid runtime");
        check(!service.contains("guardiansByTarget") && !service.contains("beaconTargets"),
                "Védelmező is self-only — no Warrior-Guardian-style target-bound index");
        check(service.contains("attacker.getScheduler().run(plugin,"),
                "the Gyökérháló slow hops to the attacker's own region thread");

        final String gameplayConfig = Files.readString(Path.of(
                "src/main/resources/config/class-gameplay.yml"));
        check(gameplayConfig.contains("classes: []"),
                "the melee-catalyst compatibility list stays empty once every class casts through its Lélekkapocs");
        check(gameplayConfig.contains("ripen-millis") && gameplayConfig.contains("expiry-millis"),
                "the seed ripening and withering windows are admin-tunable live config");

        final String manager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/SpecializationManager.java"));
        for (final String trial : new String[]{"druid_feral_trial", "druid_lunar_trial",
                "druid_ironbark_trial", "druid_restoration_trial"}) {
            check(manager.contains(trial), "the capstone trial contract " + trial + " is registered");
        }
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
