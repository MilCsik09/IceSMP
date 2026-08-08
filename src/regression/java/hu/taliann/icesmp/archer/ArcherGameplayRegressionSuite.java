package hu.taliann.icesmp.archer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Dependency-free behavior regression for the concrete Íjász runtime state. */
public final class ArcherGameplayRegressionSuite {

    private static int assertions;

    private ArcherGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        windReadPacingAndSingleUse();
        windReadDistanceIsShotAnchored();
        precisionChainPreyAndWeakPoint();
        precisionChainWindowAndRetention();
        bondBuildSpendAndCollapse();
        cleanupLifecycle();
        stableAndAllowlistSourceContracts();
        System.out.println("Archer gameplay regression suite passed. assertions=" + assertions);
    }

    private static void windReadPacingAndSingleUse() {
        final ArcherCombatState state = new ArcherCombatState();
        final long t0 = 10_000L;
        check(state.recordShot(t0, true, 900L, 0.0D, 64.0D, 0.0D),
                "first full-draw shot is paced");
        check(!state.recordShot(t0 + 500L, true, 900L, 0.0D, 64.0D, 0.0D),
                "spam inside the pacing window is not disciplined");
        check(!state.recordShot(t0 + 1_500L, false, 900L, 0.0D, 64.0D, 0.0D),
                "a half-drawn shot is never disciplined");

        state.armWindRead(t0 + 2_000L, 5_000L);
        check(state.isWindReadArmed(t0 + 6_999L), "read stays armed inside its window");
        check(state.consumeWindRead(t0 + 3_000L), "the next shot consumes the read");
        check(!state.consumeWindRead(t0 + 3_001L), "the read is strictly single-use");

        state.armWindRead(t0 + 10_000L, 5_000L);
        check(!state.consumeWindRead(t0 + 15_001L), "an expired read never fires");

        state.armWindRead(t0 + 20_000L, 5_000L);
        check(state.recordShot(t0 + 20_050L, true, 900L, 0.0D, 64.0D, 0.0D),
                "a paced shot leaves the armed read intact for consumption");
        check(state.isWindReadArmed(t0 + 20_060L),
                "discipline keeps the read armed");
        state.recordShot(t0 + 20_400L, true, 900L, 0.0D, 64.0D, 0.0D);
        check(!state.isWindReadArmed(t0 + 20_500L),
                "an unpaced follow-up breaks the armed read");
    }

    private static void windReadDistanceIsShotAnchored() {
        final ArcherCombatState state = new ArcherCombatState();
        check(state.distanceFromLastShot(30.0D, 64.0D, 40.0D) == 0.0D,
                "no recorded shot means no distance");
        state.recordShot(1_000L, true, 900L, 10.0D, 64.0D, 10.0D);
        check(Math.abs(state.distanceFromLastShot(10.0D, 64.0D, 25.0D) - 15.0D) < 1.0E-9,
                "distance is measured from the recorded shot origin");
    }

    private static void precisionChainPreyAndWeakPoint() {
        final ArcherCombatState state = new ArcherCombatState();
        final UUID prey = UUID.randomUUID();
        final UUID other = UUID.randomUUID();
        final long t0 = 50_000L;
        check(state.recordPreyHit(prey, t0, 6_000L, 5) == 1, "first hit selects the prey");
        check(state.recordPreyHit(prey, t0 + 1_000L, 6_000L, 5) == 2,
                "consecutive prey hits build the chain");
        check(state.recordPreyHit(other, t0 + 2_000L, 6_000L, 5) == 1,
                "switching targets restarts the chain on the new prey");
        check(state.preyTargetId().orElseThrow().equals(other),
                "the prey follows the last chained target");

        state.recordPreyHit(other, t0 + 3_000L, 6_000L, 5);
        state.recordPreyHit(other, t0 + 4_000L, 6_000L, 5);
        state.recordPreyHit(other, t0 + 5_000L, 6_000L, 5);
        check(!state.consumeWeakPoint(5, 0), "below-threshold chain cannot finish");
        state.recordPreyHit(other, t0 + 6_000L, 6_000L, 5);
        check(state.consumeWeakPoint(5, 0), "threshold chain opens the weak point");
        check(state.precisionChain(t0 + 6_100L, 6_000L) == 0, "spent chain vents fully");
        check(state.preyTargetId().isEmpty(), "spent chain releases the prey");
    }

    private static void precisionChainWindowAndRetention() {
        final ArcherCombatState state = new ArcherCombatState();
        final UUID prey = UUID.randomUUID();
        final long t0 = 100_000L;
        state.recordPreyHit(prey, t0, 6_000L, 5);
        state.recordPreyHit(prey, t0 + 1_000L, 6_000L, 5);
        check(state.recordPreyHit(prey, t0 + 8_000L, 6_000L, 5) == 1,
                "a stale window restarts the chain");
        check(state.precisionChain(t0 + 15_000L, 6_000L) == 0,
                "an idle chain reads back as zero after the window");

        state.recordPreyHit(prey, t0 + 20_000L, 6_000L, 3);
        state.recordPreyHit(prey, t0 + 21_000L, 6_000L, 3);
        state.recordPreyHit(prey, t0 + 22_000L, 6_000L, 3);
        check(state.recordPreyHit(prey, t0 + 23_000L, 6_000L, 3) == 3,
                "the chain is bounded at its maximum");
        check(state.consumeWeakPoint(3, 2), "doctrine retention still finishes");
        check(state.precisionChain(t0 + 23_100L, 6_000L) == 2,
                "retention doctrine keeps partial chain");
        check(state.preyTargetId().isPresent(),
                "retained chain keeps the prey context");
    }

    private static void bondBuildSpendAndCollapse() {
        final ArcherCombatState state = new ArcherCombatState();
        check(state.addBond(40) == 40, "coordination builds the bond");
        check(state.addBond(1000) == 100, "bond is bounded at 100");
        check(state.spendBond(80), "bond funds the pack spells");
        check(state.bond() == 20, "bond spend is exact");
        check(!state.spendBond(21), "bond cannot spend more than available");
        check(state.bond() == 20, "failed bond spend is side-effect free");

        state.addBond(60);
        state.collapseBond(0);
        check(state.bond() == 0, "pet death collapses the bond");
        state.addBond(80);
        state.collapseBond(50);
        check(state.bond() == 50, "the level-50 doctrine retains part of the bond");
    }

    private static void cleanupLifecycle() {
        final ArcherCombatState state = new ArcherCombatState();
        final long t0 = 200_000L;
        state.recordShot(t0, true, 900L, 0.0D, 64.0D, 0.0D);
        state.armWindRead(t0, 5_000L);
        state.recordPreyHit(UUID.randomUUID(), t0, 6_000L, 5);
        state.addBond(70);
        state.clearSpecializationState();
        check(!state.isWindReadArmed(t0), "spec switch clears the wind read");
        check(state.precisionChain(t0, 6_000L) == 0, "spec switch clears the chain");
        check(state.preyTargetId().isEmpty(), "spec switch clears the prey");
        check(state.bond() == 0, "spec switch clears the bond");

        state.addBond(30);
        state.clearAll();
        check(state.bond() == 0, "death/logout cleanup clears everything");
    }

    private static void stableAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("Set.of(\"warrior\", \"evoker\", \"archer\")"),
                "gameplay-v2 allowlist is exactly the completed slices");

        final String pets = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/PetManager.java"));
        check(pets.contains("pet-stable-full"),
                "the Vadmester stable is capacity-gated at capture");
        check(pets.contains("Kind.REMOVE") && pets.contains("pet-release:"),
                "stable release is a durable-first companion REMOVE");
        final int removeCommit = pets.indexOf("pet-release:");
        final int removeEffect = pets.indexOf("removeActive(player)", removeCommit);
        check(removeCommit >= 0 && removeEffect > removeCommit,
                "release removes the live entity only after the durable commit");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/archer/ArcherGameplayService.java"));
        check(service.contains("distanceFromLastShot"),
                "wind-read distance uses shot-anchored coordinates, not cross-region entity reads");
        check(service.contains("pvp-max-bonus-percent") && service.contains("pve-max-bonus-percent"),
                "arrow bonuses carry explicit PvE/PvP caps");
        check(!service.contains("getNearbyEntities") && !service.contains("runAtFixedRate"),
                "no proximity scans or repeating tasks in the archer runtime");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
